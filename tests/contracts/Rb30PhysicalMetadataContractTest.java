package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Provider-owned Q30 physical metadata regression across resolved plans,
 * bounded scale fixtures, and named regression recipes. Every declared
 * resistor package must construct with the typed metadata required by its
 * renderer.
 */
public final class Rb30PhysicalMetadataContractTest {
    private static int assertions;

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        int resolvedSeedCount = 0;
        for (long seed : new long[] { 0L, 1L, 3L, 11L, Long.MIN_VALUE,
                Long.MAX_VALUE, 9007199254740993L }) {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            verifyCandidateMetadata(plan);
            resolvedSeedCount++;
        }
        check(resolvedSeedCount == 7);

        int scaleFixtureCount = 0;
        int channelCoverage = 0;
        int arrangementCoverage = 0;
        int filterCoverage = 0;
        int outputIndicatorCoverage = 0;
        boolean entryCapCovered = false;
        boolean fiveVoltIndicatorCovered = false;
        boolean twelveVoltIndicatorCovered = false;
        boolean bleederCovered = false;
        for (ScaleRecipe recipe : scaleRecipes()) {
            check(recipe.plan.physicalPackageCount() == recipe.expectedPackages,
                recipe.name + " retains its explicit scale boundary");
            verifyCandidateMetadata(recipe.plan);
            channelCoverage |= 1 << (recipe.plan.channelCount - 1);
            arrangementCoverage |= 1 << recipe.plan.referenceArrangement.ordinal();
            filterCoverage |= recipe.plan.sensorInputFilterMask;
            outputIndicatorCoverage |= recipe.plan.outputIndicatorMask;
            entryCapCovered |= recipe.plan.hasEntryCapacitor;
            fiveVoltIndicatorCovered |= recipe.plan.hasFiveVoltIndicator;
            twelveVoltIndicatorCovered |= recipe.plan.hasTwelveVoltIndicator;
            bleederCovered |= recipe.plan.hasFiveVoltBleeder;
            scaleFixtureCount++;
        }
        check(scaleFixtureCount == 4);
        check(channelCoverage == 3, "scale fixtures cover one and two channels");
        check(arrangementCoverage == 7,
            "scale fixtures cover all three sensor reference arrangements");
        check(filterCoverage == 3 && outputIndicatorCoverage == 3,
            "scale fixtures cover optional A and B filters and output indicators");
        check(entryCapCovered && fiveVoltIndicatorCovered &&
            twelveVoltIndicatorCovered && bleederCovered,
            "scale fixtures cover each optional support package family");

        int regressionRecipeCount = 0;
        long[] regressionSeeds = { 0L, 48L, 56L };
        Rb30Plan.SupportVariant[] regressionVariants = {
            Rb30Plan.SupportVariant.COMPACT_33,
            Rb30Plan.SupportVariant.STANDARD_35,
            Rb30Plan.SupportVariant.FILTERED_37
        };
        for (int index = 0; index < regressionSeeds.length; index++) {
            Rb30Plan plan = Rb30Plan.withSupport(regressionSeeds[index],
                regressionVariants[index]);
            check(plan.supportVariant == regressionVariants[index],
                "33/35/37 shapes stay named regression recipes");
            check(plan.physicalPackageCount() ==
                regressionVariants[index].packageCount(),
                "named regression package count remains explicit");
            verifyCandidateMetadata(plan);
            regressionRecipeCount++;
        }
        check(regressionRecipeCount == 3);
        verifyFullOwnerConstruction(sim, 3L);
        verifyFullOwnerConstruction(sim, 37L);
        System.out.println("PASS: Q30 physical metadata contracts " + assertions +
            " assertions, resolvedSeeds=" + resolvedSeedCount +
            " scaleFixtures=" + scaleFixtureCount +
            " regressionRecipes=33/35/37");
    }

    private static ScaleRecipe[] scaleRecipes() {
        return new ScaleRecipe[] {
            new ScaleRecipe("one-channel-minimum", 20,
                Rb30Plan.configured(20260401L, 1,
                    Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT,
                    false, false, false, 0, 0, false)),
            new ScaleRecipe("one-channel-support", 29,
                Rb30Plan.configured(20260402L, 1,
                    Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT,
                    true, true, true, 1, 1, true)),
            new ScaleRecipe("two-channel-shared-direct-max", 40,
                Rb30Plan.configured(20260403L, 2,
                    Rb30Plan.ReferenceArrangement.SHARED_DIRECT,
                    true, true, false, 3, 3, true)),
            new ScaleRecipe("two-channel-shared-hysteretic-max", 40,
                Rb30Plan.configured(20260404L, 2,
                    Rb30Plan.ReferenceArrangement.SHARED_HYSTERETIC,
                    true, false, true, 1, 3, false))
        };
    }

    private static void verifyCandidateMetadata(Rb30Plan plan) {
        TroubleshootBoard board = plan.board();
        Rb30Generator.Candidate candidate = new Rb30Generator().construct(plan);
        check(candidate.plan == plan);
        check(board.getComponentIds().size() == plan.physicalPackageCount());
        check(plan.channelCount >= 1 && plan.channelCount <= 2);
        checkPresence(board, "C12", plan.hasEntryCapacitor);
        checkPresence(board, "RBLEED5", plan.hasFiveVoltBleeder);
        checkPresence(board, "LED1", plan.hasFiveVoltIndicator);
        checkPresence(board, "RLED", plan.hasFiveVoltIndicator);
        checkPresence(board, "LED12", plan.hasTwelveVoltIndicator);
        checkPresence(board, "RLED12", plan.hasTwelveVoltIndicator);
        int outputIndicators = 0;
        int inputFilters = 0;
        for (String channel : plan.channels()) {
            for (String prefix : new String[] { "U2", "RS", "RPIN_", "RD",
                    "RPD", "Q", "D", "K", "JS", "JO" }) {
                String id = prefix + channel;
                check(board.getComponent(id) != null,
                    "active channel has its required package: " + id);
            }
            checkPresence(board, "CFLT_" + channel,
                plan.hasSensorInputFilter(channel));
            checkPresence(board, "RLEDOUT_" + channel,
                plan.hasOutputIndicator(channel));
            checkPresence(board, "LEDOUT_" + channel,
                plan.hasOutputIndicator(channel));
            if (plan.hasSensorInputFilter(channel)) inputFilters++;
            if (plan.hasOutputIndicator(channel)) outputIndicators++;
        }
        if (plan.referenceArrangement == Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT) {
            for (String channel : plan.channels()) {
                checkPresence(board, "RREF_H" + channel, true);
                checkPresence(board, "RREF_L" + channel, true);
                checkPresence(board, "RFB_" + channel, false);
            }
            checkPresence(board, "RREF_H", false);
            checkPresence(board, "RREF_L", false);
        } else {
            checkPresence(board, "RREF_H", true);
            checkPresence(board, "RREF_L", true);
            for (String channel : plan.channels()) {
                checkPresence(board, "RREF_H" + channel, false);
                checkPresence(board, "RREF_L" + channel, false);
                checkPresence(board, "RFB_" + channel,
                    plan.sharedHystereticReference());
            }
        }
        if (plan.channelCount == 1) {
            for (String id : new String[] { "U2B", "RSB", "RPIN_B", "RDB",
                    "RPDB", "QB", "DB", "KB", "JSB", "JOB", "CFLT_B",
                    "RLEDOUT_B", "LEDOUT_B", "RREF_HB", "RREF_LB", "RFB_B" })
                check(board.getComponent(id) == null,
                    "inactive channel B has no package: " + id);
        }

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
                if (id.startsWith("RPIN_")) {
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
        int expectedResistors = 1 + 4 * plan.channelCount +
            plan.referencePackageCount() + (plan.hasFiveVoltBleeder ? 1 : 0) +
            (plan.hasFiveVoltIndicator ? 1 : 0) +
            (plan.hasTwelveVoltIndicator ? 1 : 0) + outputIndicators;
        check(resistors == expectedResistors);
        check(diodes == plan.channelCount + 1);
        check(leds == (plan.hasFiveVoltIndicator ? 1 : 0) +
            (plan.hasTwelveVoltIndicator ? 1 : 0) + outputIndicators);
        check(ceramicCapacitors == 1 + (plan.hasEntryCapacitor ? 1 : 0) +
            inputFilters);
        check(electrolyticCapacitors == 1);
    }

    private static void checkPresence(TroubleshootBoard board, String id,
            boolean expected) {
        check((board.getComponent(id) != null) == expected,
            id + " presence follows its plan flag");
    }

    private static final class ScaleRecipe {
        final String name;
        final int expectedPackages;
        final Rb30Plan plan;
        ScaleRecipe(String name, int expectedPackages, Rb30Plan plan) {
            this.name = name;
            this.expectedPackages = expectedPackages;
            this.plan = plan;
        }
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

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition)
            throw new AssertionError(message);
    }
}
