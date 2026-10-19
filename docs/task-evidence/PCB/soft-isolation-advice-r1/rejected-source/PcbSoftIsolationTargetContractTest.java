package com.lushprojects.circuitjs1.client;

import java.util.TreeSet;
import java.util.Vector;

/**
 * Literal advice fixtures and finite raw1-5 preservation at three informed roots.
 * Raw equality covers the entire plan, including footprint/region sequence and
 * evaluation metadata. No route, solver, admission or metric acceptance is claimed.
 */
public final class PcbSoftIsolationTargetContractTest {
    private static final int SAVED_VERSION = 14, TRIAL_VERSION = 15;
    private static final long[] SEEDS = {-1022L, 42L, 4L};
    private static int assertions;

    private PcbSoftIsolationTargetContractTest() { }

    public static void main(String[] args) {
        literalGapFixtures();
        currentIdentityRemains14();
        int failures = 0;
        for (long seed : SEEDS) {
            try {
                inspectRawPlans(seed);
            } catch (AssertionError failure) {
                failures++;
                reportFailure(seed, failure);
            } catch (RuntimeException failure) {
                failures++;
                reportFailure(seed, failure);
            }
        }
        if (failures != 0)
            throw new AssertionError("soft isolation raw preservation failed roots=" + failures);
        System.out.println("PASS: PCB soft isolation target contracts roots=" + SEEDS.length +
            " rawCandidatesPerRoot=5 assertions=" + assertions +
            " preservedRawPlansExact=true scope=representative-informed-roots" +
            " routing=NOT_RUN admission=NOT_RUN metricAcceptance=NOT_CLAIMED");
    }

    private static void literalGapFixtures() {
        Rectangle primary = new Rectangle(20, 40, 80, 60);
        Rectangle secondary = new Rectangle(180, 20, 60, 70);
        expectGap("normal declared order", primary, secondary, 140);
        expectGap("reversed declared order", secondary, primary, 140);
        expectGap("translated courts", new Rectangle(57, -43, 80, 60),
            new Rectangle(217, -63, 60, 70), 177);
        expectGap("negative coordinates with disjoint y", new Rectangle(-220, -45, 80, 60),
            new Rectangle(-60, 900, 60, 70), -100);
        expectGap("negative odd gap floors toward spatial left", new Rectangle(-100, 0, 10, 20),
            new Rectangle(-1, 800, 1, 10), -46);
        expectGap("negative odd gap with reversed arguments", new Rectangle(-1, 800, 1, 10),
            new Rectangle(-100, 0, 10, 20), -46);
        expectGap("odd gap translation through zero", new Rectangle(1, -7, 10, 20),
            new Rectangle(100, 793, 1, 10), 55);
        expectGap("touching with disjoint y", new Rectangle(-20, -10, 20, 30),
            new Rectangle(0, 9000, 15, 10), 0);
        expectGap("touching reversed", new Rectangle(0, 9000, 15, 10),
            new Rectangle(-20, -10, 20, 30), 0);

        expectGap("horizontal overlap rejects despite disjoint y", new Rectangle(10, 0, 40, 10),
            new Rectangle(49, 5000, 30, 10), null);
        expectGap("horizontal overlap reversed", new Rectangle(49, 5000, 30, 10),
            new Rectangle(10, 0, 40, 10), null);
        expectGap("same x rejects", new Rectangle(10, 0, 1, 10),
            new Rectangle(10, 5000, 1, 10), null);
        expectGap("contained x range rejects", new Rectangle(-40, 0, 100, 10),
            new Rectangle(-10, 50, 5, 10), null);
        expectGap("missing first court", null, secondary, null);
        expectGap("missing second court", primary, null, null);
        expectGap("both courts missing", null, null, null);
        expectGap("first zero width", new Rectangle(0, 0, 0, 1), secondary, null);
        expectGap("second zero width", primary, new Rectangle(200, 0, 0, 1), null);
        expectGap("first negative width", new Rectangle(0, 0, -1, 1), secondary, null);
        expectGap("second negative width", primary, new Rectangle(200, 0, -1, 1), null);
        expectGap("first zero height", new Rectangle(0, 0, 1, 0), secondary, null);
        expectGap("second zero height", primary, new Rectangle(200, 0, 1, 0), null);
        expectGap("first negative height", new Rectangle(0, 0, 1, -1), secondary, null);
        expectGap("second negative height", primary, new Rectangle(200, 0, 1, -1), null);

        expectGap("positive midpoint sum exceeds int", new Rectangle(1500000000, 0, 100000000, 1),
            new Rectangle(2000000000, -50, 100000000, 1), 1800000000);
        expectGap("positive midpoint reversed", new Rectangle(2000000000, -50, 100000000, 1),
            new Rectangle(1500000000, 0, 100000000, 1), 1800000000);
        expectGap("negative midpoint sum exceeds int", new Rectangle(-2100000000, 0, 100000000, 1),
            new Rectangle(-1500000000, 10, 100000000, 1), -1750000000);
        expectGap("gap spans both int extremes", new Rectangle(Integer.MIN_VALUE, 0, 1, 1),
            new Rectangle(2147483646, 0, 1, 1), -1);
        expectGap("int extremes reversed", new Rectangle(2147483646, 0, 1, 1),
            new Rectangle(Integer.MIN_VALUE, 0, 1, 1), -1);
        expectGap("right edge at int maximum", new Rectangle(2147483627, 0, 10, 1),
            new Rectangle(2147483637, 0, 10, 1), 2147483637);
        expectGap("left edge at int minimum", new Rectangle(Integer.MIN_VALUE, 0, 10, 1),
            new Rectangle(-2147483638, 0, 5, 1), -2147483638);
        expectGap("overflowing first endpoint rejects", new Rectangle(2147483637, 0, 20, 1),
            new Rectangle(2147483642, 0, 1, 1), null);
        expectGap("overflowing second endpoint rejects", new Rectangle(0, 0, 1, 1),
            new Rectangle(2147483642, 0, 10, 1), null);
        expectGap("overflowing endpoint reversed rejects", new Rectangle(2147483642, 0, 10, 1),
            new Rectangle(0, 0, 1, 1), null);
    }

    private static void expectGap(String name, Rectangle first, Rectangle second, Integer expected) {
        Rectangle firstBefore = first == null ? null : new Rectangle(first);
        Rectangle secondBefore = second == null ? null : new Rectangle(second);
        Integer actual = PcbPlacementPlanner.isolationGapCenterX(first, second);
        check(expected == null ? actual == null : expected.equals(actual),
            name + " literal midpoint expected=" + expected + " actual=" + actual);
        check(first == null || first.equals(firstBefore), name + " preserves first court");
        check(second == null || second.equals(secondBefore), name + " preserves second court");
    }

    private static void currentIdentityRemains14() {
        check(Rb56Placement.CURRENT_VERSION == SAVED_VERSION &&
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT == SAVED_VERSION,
            "native trial leaves both current selectors at literal14");
        check(Rb56Placement.SOFT_ISOLATION_TRIAL_VERSION == TRIAL_VERSION &&
                Rb56Placement.supportsVersion(TRIAL_VERSION) &&
                Rb56Placement.densityVersion(TRIAL_VERSION) == 14 &&
                Rb56Placement.densityVersion(SAVED_VERSION) == 13,
            "explicit trial15/density14 remains separate from saved14/density13");
        GenerationRequest current = GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID, 42L);
        PlayerSessionReplay replay = PlayerSessionReplay.forQualification(current);
        String expected = "tsj-private-session/14/" +
            GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-14/RB56_CONTROL/42";
        check(current.getRb56PlacementInterpretationVersion() == 14 &&
                expected.equals(replay.canonical()) && replay.matches(current) &&
                PlayerSessionReplay.parse(expected).rb56PlacementInterpretationVersion() == 14,
            "default qualification and exact replay retain literal14");
        boolean replay15Rejected = false;
        try {
            PlayerSessionReplay.parse("tsj-private-session/15/" +
                GenerationDependencyContext.INTERPRETATION_EPOCH + "/rb56-placement-15/RB56_CONTROL/42");
        } catch (IllegalArgumentException expectedFailure) {
            replay15Rejected = true;
        }
        check(replay15Rejected, "native-only trial15 has no supported replay namespace");
        boolean trialReplayRejected = false;
        try {
            PlayerSessionReplay.forQualification(current.withRb56PlacementInterpretationVersion(15));
        } catch (IllegalArgumentException expectedFailure) {
            trialReplayRejected = true;
        }
        check(trialReplayRejected, "explicit native trial cannot create a current player replay");
    }

    private static void inspectRawPlans(long seed) {
        Rb56Plan savedSource = Rb56Plan.resolve(seed, SAVED_VERSION);
        Rb56Plan trialSource = Rb56Plan.resolve(seed, TRIAL_VERSION);
        TroubleshootBoard savedBoard = savedSource.board(), trialBoard = trialSource.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedSource, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialSource, trialBoard);
        String savedSourceHash = PcbCanvasBoundaryContractTest.sourcePlanSha256(savedSource);
        String trialSourceHash = PcbCanvasBoundaryContractTest.sourcePlanSha256(trialSource);
        String savedGraphHash = PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard);
        String trialGraphHash = PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard);
        check(PcbSoftIsolationAdviceContractTest.savedIntent(savedSource)
                .equals(PcbSoftIsolationAdviceContractTest.savedIntent(trialSource)),
            "raw comparison preserves exact ordered source intent and streams: " + seed);
        check(savedGraphHash.equals(trialGraphHash),
            "raw comparison starts with exactly equal electrical graphs: " + seed);
        PcbPlacementConstraints savedConstraints = savedBoard.getPlacementConstraints();
        PcbPlacementConstraints trialConstraints = trialBoard.getPlacementConstraints();
        check(savedConstraints.placementDensityVersion == 13 &&
                trialConstraints.placementDensityVersion == 14,
            "raw candidates use literal saved/trial densities13/14: " + seed);
        String savedConstraintsBefore = constraintsIntent(savedConstraints);
        String trialConstraintsBefore = constraintsIntent(trialConstraints);
        check(savedConstraintsBefore.equals(trialConstraintsBefore),
            "trial changes no demand, routing, copper-profile or barrier metadata: " + seed);

        PcbPlacementPlanner savedPlanner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        PcbPlacementPlanner trialPlanner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        for (int candidate = 1; candidate <= 5; candidate++) {
            PcbPlacementPlanner.Plan saved = savedPlanner.plan(savedBoard, savedConstraints,
                savedSource.layoutSeed, candidate);
            PcbPlacementPlanner.Plan trial = trialPlanner.plan(trialBoard, trialConstraints,
                trialSource.layoutSeed, candidate);
            verifyRawInventory(savedBoard, saved, candidate, seed);
            verifyRawInventory(trialBoard, trial, candidate, seed);
            check(saved.outline.equals(trial.outline),
                "raw outline exact: " + seed + "/" + candidate);
            check(saved.evaluations == trial.evaluations,
                "raw evaluations exact: " + seed + "/" + candidate);
            check(saved.candidate == trial.candidate && saved.courtyardArea == trial.courtyardArea &&
                    saved.demandArea == trial.demandArea &&
                    regionIntent(saved.regions).equals(regionIntent(trial.regions)),
                "raw candidate/demand/courtyard/navigation metadata exact: " + seed + "/" + candidate);
            for (int index = 0; index < saved.footprints.size(); index++)
                check(saved.footprints.get(index).geometryFingerprint()
                        .equals(trial.footprints.get(index).geometryFingerprint()),
                    "full footprint geometry and canonical order exact: " +
                        seed + "/" + candidate + "/" + index);
            String savedPlan = rawPlanIntent(saved), trialPlan = rawPlanIntent(trial);
            check(savedPlan.equals(trialPlan),
                "entire raw plan remains exact saved14: " + seed + "/" + candidate);
            check(savedSourceHash.equals(PcbCanvasBoundaryContractTest.sourcePlanSha256(savedSource)) &&
                    trialSourceHash.equals(PcbCanvasBoundaryContractTest.sourcePlanSha256(trialSource)),
                "raw planner preserves both source plans: " + seed + "/" + candidate);
            check(savedGraphHash.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(savedBoard)) &&
                    trialGraphHash.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(trialBoard)),
                "raw planner preserves both live electrical graphs: " + seed + "/" + candidate);
            check(savedConstraintsBefore.equals(constraintsIntent(savedConstraints)) &&
                    trialConstraintsBefore.equals(constraintsIntent(trialConstraints)),
                "raw planner preserves declared demand metadata: " + seed + "/" + candidate);
            System.out.println("PCB_SOFT_ISOLATION_RAW seed=" + seed +
                " versions=14/15 candidate=" + candidate + " evaluations=" + saved.evaluations +
                " rawPlanSha256=" + PlayerSessionFingerprint.of(savedPlan) +
                " footprintsExact=true outlineExact=true evaluationsExact=true metadataExact=true" +
                " source=UNCHANGED graph=UNCHANGED routing=NOT_RUN admission=NOT_RUN");
        }
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(savedSource, savedBoard);
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(trialSource, trialBoard);
    }

    private static void verifyRawInventory(TroubleshootBoard board, PcbPlacementPlanner.Plan plan,
            int candidate, long seed) {
        check(plan != null && plan.candidate == candidate &&
                plan.evaluations > 0 && plan.evaluations <= 60000,
            "raw candidate identity and existing per-plan work cap: " + seed + "/" + candidate);
        check(plan.outline != null && plan.outline.width > 0 && plan.outline.height > 0 &&
                plan.outline.width <= 16000 && plan.outline.height <= 16000 &&
                (long) plan.outline.width * plan.outline.height <= 64000000L,
            "raw outline remains within existing extent and area bounds: " + seed + "/" + candidate);
        TreeSet<String> components = new TreeSet<String>(), pads = new TreeSet<String>();
        for (PcbFootprint footprint : plan.footprints) {
            String id = footprint.getPlacement().getComponentId();
            check(components.add(id) && board.getComponent(id) != null,
                "unique source-owned raw footprint: " + seed + "/" + candidate + "/" + id);
            TreeSet<String> componentPads = new TreeSet<String>();
            for (PcbPadPlacement pad : footprint.getPads()) {
                BoardPad source = board.getPad(pad.getPadId());
                check(pads.add(pad.getPadId()) && componentPads.add(pad.getPadId()) &&
                        source != null && id.equals(source.getComponentId()),
                    "unique source-owned raw pad: " + seed + "/" + candidate + "/" + pad.getPadId());
            }
            check(componentPads.equals(new TreeSet<String>(board.getComponent(id).getPadIds())),
                "exact raw component pad membership: " + seed + "/" + candidate + "/" + id);
        }
        check(components.equals(new TreeSet<String>(board.getComponentIds())) &&
                pads.equals(new TreeSet<String>(board.getPadIds())),
            "full raw component and terminal inventories match source: " + seed + "/" + candidate);
    }

    /** All placement declarations except the intentionally distinct density interpretation. */
    private static String constraintsIntent(PcbPlacementConstraints constraints) {
        StringBuilder out = new StringBuilder();
        atom(out, constraints.routingLayer.name());
        atom(out, constraints.getPhysicalPolicyIdentity());
        atom(out, Boolean.toString(constraints.requiresTwoLayerRouting));
        atom(out, constraints.getTraceWidthProfile().canonical());
        for (PcbPlacementConstraints.Part part : constraints.getParts()) {
            atom(out, part.componentId); atom(out, part.regionId); atom(out, part.regionLabel);
            atom(out, part.domainId); atom(out, part.anchor.name());
            atom(out, Integer.toString(part.accessMargin));
            out.append("terminals=").append(part.getTerminalDomains().size()).append('[');
            for (PcbPlacementConstraints.TerminalDomain terminal : part.getTerminalDomains()) {
                atom(out, terminal.terminalId); atom(out, terminal.domainId);
            }
            out.append(']');
        }
        out.append("barriers=").append(constraints.getBarriers().size()).append('[');
        for (PcbPlacementConstraints.Barrier barrier : constraints.getBarriers()) {
            atom(out, barrier.firstDomain); atom(out, barrier.secondDomain);
            atom(out, Integer.toString(barrier.clearance));
        }
        return out.append(']').toString();
    }

    private static String regionIntent(Vector<PcbLayoutRegion> regions) {
        StringBuilder out = new StringBuilder();
        out.append(regions.size()).append('[');
        for (PcbLayoutRegion region : regions) {
            atom(out, region.id); atom(out, region.label);
            Vector<String> components = region.getComponentIds();
            out.append(components.size()).append('[');
            for (String id : components) atom(out, id);
            out.append(']');
        }
        return out.append(']').toString();
    }

    private static String rawPlanIntent(PcbPlacementPlanner.Plan plan) {
        StringBuilder out = new StringBuilder();
        out.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append(';')
            .append(plan.candidate).append(';').append(plan.evaluations).append(';')
            .append(plan.courtyardArea).append(';').append(plan.demandArea).append(';');
        atom(out, regionIntent(plan.regions));
        out.append(plan.footprints.size()).append('[');
        for (PcbFootprint footprint : plan.footprints) atom(out, footprint.geometryFingerprint());
        return out.append(']').toString();
    }

    private static void atom(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value).append(';');
    }

    private static void reportFailure(long seed, Throwable failure) {
        String message = String.valueOf(failure.getMessage()).replace('\r', ' ').replace('\n', ' ');
        System.out.println("PCB_SOFT_ISOLATION_RAW_FAILURE seed=" + seed + " type=" +
            failure.getClass().getName() + " message=" + message);
        failure.printStackTrace(System.out);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
