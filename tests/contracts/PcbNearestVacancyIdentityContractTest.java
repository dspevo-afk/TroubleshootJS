package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Raw placement-only comparison pinned to the saved13 nearest-vacancy interpretation.
 * This diagnostic compares candidate geometry and source inventory only. It
 * does not validate placement admission, route, run a solver, or claim packing
 * acceptance.
 */
public final class PcbNearestVacancyIdentityContractTest {
    private static final long[] SEEDS = {-1022L, 7L, 14L};
    private static final int[] PRESERVED_CANDIDATES = {0, 2, 3, 4, 5};
    private static final int BEFORE_VERSION = Rb56Placement.ROLE_WIDTHS_VERSION12;
    private static final int AFTER_VERSION = Rb56Placement.NEAREST_VACANCY_VERSION13;
    private static int assertions;

    public static void main(String[] args) {
        check(BEFORE_VERSION == 12 && AFTER_VERSION == 13,
            "raw vacancy oracle remains pinned to saved12 versus saved13");
        int failures = 0;
        for (int i = 0; i < SEEDS.length; i++) {
            try {
                inspectSeed(SEEDS[i]);
            } catch (AssertionError failure) {
                failures++;
                System.out.println("NEAREST_VACANCY_IDENTITY_FAILURE seed=" + SEEDS[i] +
                    " message=" + failure.getMessage());
                failure.printStackTrace(System.out);
            } catch (RuntimeException failure) {
                failures++;
                System.out.println("NEAREST_VACANCY_IDENTITY_FAILURE seed=" + SEEDS[i] +
                    " message=" + failure.getMessage());
                failure.printStackTrace(System.out);
            }
        }
        if (failures != 0)
            throw new AssertionError("nearest vacancy placement identity source/inventory failures=" + failures);
        System.out.println("PASS: nearest vacancy identity contracts seeds=" + SEEDS.length +
            " candidates=" + PRESERVED_CANDIDATES.length + " assertions=" + assertions +
            " preservedRawPlansExact=true packingAcceptance=NOT_CLAIMED " +
            "routing=NOT_RUN admission=NOT_RUN");
    }

    private static void inspectSeed(long seed) {
        Rb56Plan beforePlan = Rb56Plan.resolve(seed, BEFORE_VERSION);
        Rb56Plan afterPlan = Rb56Plan.resolve(seed, AFTER_VERSION);
        String beforeSource = sourcePlanSignature(beforePlan);
        String afterSource = sourcePlanSignature(afterPlan);
        check(beforeSource.equals(afterSource), "placement versions changed the source plan");

        TroubleshootBoard beforeBoard = beforePlan.board();
        TroubleshootBoard afterBoard = afterPlan.board();
        String beforeInventory = boardInventorySignature(beforeBoard);
        String afterInventory = boardInventorySignature(afterBoard);
        check(beforeInventory.equals(afterInventory), "placement versions changed board inventory/graph");

        PcbPlacementConstraints beforeConstraints = beforeBoard.getPlacementConstraints();
        PcbPlacementConstraints afterConstraints = afterBoard.getPlacementConstraints();
        check(beforeConstraints.placementDensityVersion == Rb56Placement.densityVersion(BEFORE_VERSION),
            "saved12 planner density changed");
        check(afterConstraints.placementDensityVersion == Rb56Placement.densityVersion(AFTER_VERSION),
            "saved13 planner density changed");
        String profile = beforeConstraints.getTraceWidthProfile().canonical();
        check("ROLE_TRACE@1".equals(beforeConstraints.getTraceWidthProfile().getIdentity()),
            "diagnostic cases do not use the declared role-width profile");
        check(profile.equals(afterConstraints.getTraceWidthProfile().canonical()),
            "saved13 placement changed the saved12 copper profile");

        PcbPlacementPlanner beforePlanner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        PcbPlacementPlanner afterPlanner = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry());
        for (int candidate : PRESERVED_CANDIDATES) {
            PcbPlacementPlanner.Plan before = beforePlanner.plan(beforeBoard, beforeConstraints,
                beforePlan.layoutSeed, candidate);
            PcbPlacementPlanner.Plan after = afterPlanner.plan(afterBoard, afterConstraints,
                afterPlan.layoutSeed, candidate);

            check(sourcePlanSignature(beforePlan).equals(beforeSource),
                "candidate " + candidate + " mutated saved12 source plan");
            check(sourcePlanSignature(afterPlan).equals(afterSource),
                "candidate " + candidate + " mutated saved13 source plan");
            check(boardInventorySignature(beforeBoard).equals(beforeInventory),
                "candidate " + candidate + " mutated saved12 board graph");
            check(boardInventorySignature(afterBoard).equals(afterInventory),
                "candidate " + candidate + " mutated saved13 board graph");
            validateRawPlan(beforeBoard, before, BEFORE_VERSION, candidate);
            validateRawPlan(afterBoard, after, AFTER_VERSION, candidate);

            TreeMap<String, PcbFootprint> beforeFootprints = indexFootprints(before.footprints);
            TreeMap<String, PcbFootprint> afterFootprints = indexFootprints(after.footprints);
            boolean footprintIdsEqual = beforeFootprints.keySet().equals(afterFootprints.keySet());
            check(footprintIdsEqual, "candidate " + candidate + " changed footprint component IDs");
            boolean outlineEqual = sameRectangle(before.outline, after.outline);
            boolean evaluationsEqual = before.evaluations == after.evaluations;
            boolean rawFootprintsEqual = footprintIdsEqual;
            if (rawFootprintsEqual) {
                for (String componentId : beforeFootprints.keySet())
                    if (!beforeFootprints.get(componentId).geometryFingerprint().equals(
                            afterFootprints.get(componentId).geometryFingerprint()))
                        rawFootprintsEqual = false;
            }
            boolean rawPlanMetricsEqual = before.candidate == after.candidate &&
                before.courtyardArea == after.courtyardArea && before.demandArea == after.demandArea;

            System.out.println("NEAREST_VACANCY_IDENTITY_DIAGNOSTIC seed=" + seed +
                " versions=" + BEFORE_VERSION + "/" + AFTER_VERSION + " candidate=" + candidate +
                " routing=NOT_RUN admission=NOT_RUN packingAcceptance=NOT_CLAIMED " +
                "sourcePlan=UNCHANGED inventory=UNCHANGED outlineExact=" + outlineEqual +
                " evaluationsExact=" + evaluationsEqual + " footprintsExact=" + rawFootprintsEqual +
                " metadataExact=" + rawPlanMetricsEqual);
            reportPlan(seed, BEFORE_VERSION, before);
            reportPlan(seed, AFTER_VERSION, after);

            int changed = 0;
            int moved = 0;
            for (String componentId : beforeFootprints.keySet()) {
                PcbFootprint oldFootprint = beforeFootprints.get(componentId);
                PcbFootprint newFootprint = afterFootprints.get(componentId);
                PcbComponentPlacement oldPose = oldFootprint.getPlacement();
                PcbComponentPlacement newPose = newFootprint.getPlacement();
                boolean poseMoved = !samePose(oldPose, newPose);
                boolean geometryChanged = !oldFootprint.geometryFingerprint().equals(
                    newFootprint.geometryFingerprint());
                if (geometryChanged) {
                    changed++;
                    if (poseMoved) moved++;
                    BoardComponent component = beforeBoard.getComponent(componentId);
                    System.out.println("  CHANGED candidate=" + candidate + " component=" + componentId +
                        " type=" + component.getType() + " before=" + pose(oldPose) +
                        " after=" + pose(newPose) + " moved=" + poseMoved +
                        " geometryChanged=" + geometryChanged);
                }
            }
            long outlineAreaDelta = area(after.outline) - area(before.outline);
            System.out.println("  DELTA candidate=" + candidate + " changedFootprints=" + changed +
                " movedComponents=" + moved + " outlineArea=" + area(before.outline) + "->" +
                area(after.outline) + " outlineAreaDelta=" + outlineAreaDelta +
                " evaluations=" + before.evaluations + "->" + after.evaluations +
                " routing=NOT_RUN admission=NOT_RUN");
            check(outlineEqual, "raw candidate " + candidate +
                " outlines differ between placement interpretations: " + seed);
            check(evaluationsEqual, "raw candidate " + candidate +
                " evaluation counts differ: " + seed);
            check(rawFootprintsEqual, "raw candidate " + candidate +
                " component footprints differ: " + seed);
            check(rawPlanMetricsEqual, "raw candidate " + candidate +
                " plan metadata differs: " + seed);
        }
    }

    private static void validateRawPlan(TroubleshootBoard board, PcbPlacementPlanner.Plan plan,
            int version, int expectedCandidate) {
        check(plan != null, "planner returned null");
        check(plan.candidate == expectedCandidate,
            "planner returned a different candidate: expected " + expectedCandidate + " got " + plan.candidate);
        check(plan.evaluations > 0, "planner reported no candidate evaluations");
        check(plan.outline != null && plan.outline.width > 0 && plan.outline.height > 0,
            "planner returned an invalid outline");
        check(plan.footprints.size() == board.getComponentIds().size(),
            "planner footprint count differs from board inventory");
        TreeMap<String, PcbFootprint> footprints = indexFootprints(plan.footprints);
        TreeSet<String> components = new TreeSet<String>(board.getComponentIds());
        check(footprints.keySet().equals(components), "planner footprint inventory differs from source board");
        check(board.getPlacementConstraints().placementDensityVersion ==
            Rb56Placement.densityVersion(version), "planner case used the wrong density interpretation");
    }

    private static TreeMap<String, PcbFootprint> indexFootprints(Vector<PcbFootprint> footprints) {
        TreeMap<String, PcbFootprint> result = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : footprints) {
            String id = footprint.getPlacement().getComponentId();
            check(result.put(id, footprint) == null, "duplicate planner footprint: " + id);
        }
        return result;
    }

    private static String sourcePlanSignature(Rb56Plan plan) {
        StringBuilder result = new StringBuilder();
        result.append(plan.seed).append('|').append(plan.layoutSeed).append('|')
            .append(plan.routingSeed).append('|').append(plan.topology()).append('|');
        Vector<Rb56Plan.Part> parts = plan.parts();
        for (Rb56Plan.Part part : parts) result.append('[').append(part.canonical()).append(']');
        return result.toString();
    }

    private static String boardInventorySignature(TroubleshootBoard board) {
        StringBuilder result = new StringBuilder(board.getId()).append('|');
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String id : componentIds) {
            BoardComponent component = board.getComponent(id);
            result.append("component=").append(id).append(':').append(component.getType()).append(':')
                .append(component.getPhysicalPackage().getId()).append('|');
        }
        Vector<String> padIds = board.getPadIds();
        Collections.sort(padIds);
        for (String id : padIds) {
            BoardPad pad = board.getPad(id);
            result.append("pad=").append(id).append(':').append(pad.getComponentId()).append(':')
                .append(pad.getTerminalId()).append(':').append(pad.getNetId()).append('|');
        }
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String id : netIds) {
            BoardNet net = board.getNet(id);
            Vector<String> netPads = net.getPadIds();
            Collections.sort(netPads);
            result.append("net=").append(id).append(':').append(net.getRoutingRole().name()).append(':');
            for (String padId : netPads) result.append(padId).append(',');
            result.append('|');
        }
        Vector<String> inputIds = board.getPowerInputIds();
        Collections.sort(inputIds);
        for (String id : inputIds) {
            ExternalBoardPowerInput input = board.getPowerInput(id);
            result.append("input=").append(id).append(':').append(input.getPositivePadId()).append(':')
                .append(input.getReturnPadId()).append(':').append(input.getPositiveNetId()).append(':')
                .append(input.getReturnNetId()).append('|');
        }
        return result.toString();
    }

    private static void reportPlan(long seed, int version, PcbPlacementPlanner.Plan plan) {
        Rectangle outline = plan.outline;
        System.out.println("  PLAN seed=" + seed + " version=" + version + " candidate=" + plan.candidate +
            " evaluations=" + plan.evaluations + " outline=" + outline.x + "," + outline.y + "," +
            outline.width + "," + outline.height + " outlineArea=" + area(outline) +
            " courtyardArea=" + plan.courtyardArea + " demandArea=" + plan.demandArea +
            " footprints=" + plan.footprints.size());
    }

    private static boolean samePose(PcbComponentPlacement first, PcbComponentPlacement second) {
        return first.getX() == second.getX() && first.getY() == second.getY() &&
            first.getRotation() == second.getRotation() &&
            first.getMountingSide() == second.getMountingSide() &&
            first.getGeometryVariantKey().equals(second.getGeometryVariantKey()) &&
            first.getGeometryTransformKey().equals(second.getGeometryTransformKey());
    }

    private static String pose(PcbComponentPlacement placement) {
        return placement.getX() + "," + placement.getY() + "," + placement.getRotation() + "," +
            placement.getMountingSide() + ",variant=" + placement.getGeometryVariantKey() +
            ",transform=" + placement.getGeometryTransformKey();
    }

    private static long area(Rectangle rectangle) {
        return (long) rectangle.width * (long) rectangle.height;
    }

    private static boolean sameRectangle(Rectangle first, Rectangle second) {
        return first.x == second.x && first.y == second.y && first.width == second.width &&
            first.height == second.height;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
