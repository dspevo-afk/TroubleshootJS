package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Focused contract for the generic medium inventory placement path.
 *
 * <p>The locality receipt is computed from board nets and placed pad
 * coordinates.  It deliberately does not call the planner's topology graph
 * or scoring helpers, so a regression to arbitrary region-row packing is
 * visible through the electrical result.</p>
 */
public final class MediumBoardFloorplanningContractTest {
    private static final long Q30_NET_MST_LIMIT = 28000L;
    private static final long CONNECTOR_NEAREST_PAD_LIMIT = 300L;
    private static int assertions;

    private MediumBoardFloorplanningContractTest() { }

    public static void main(String[] args) {
        Rb30Plan firstPlan = Rb30Plan.resolve(0L);
        TroubleshootBoard board = firstPlan.board();
        PcbPlacementPlanner.Plan first = place(board, firstPlan.layoutSeed, 0);
        PcbPlacementPlanner.Plan replay = place(board, firstPlan.layoutSeed, 0);
        check(first.materialize().geometryFingerprint().equals(
            replay.materialize().geometryFingerprint()),
            "same-seed placement is deterministic");
        check(first.outline.equals(replay.outline),
            "same-seed outline is deterministic");

        Rb30Plan otherPlan = Rb30Plan.resolve(1L);
        PcbPlacementPlanner.Plan other = place(board, otherPlan.layoutSeed, 0);
        check(!placementSignature(first).equals(placementSignature(other)),
            "different placement seeds produce real variation");

        checkQ30GeometryAndAccess(board, first);
        checkConnectorLocality(board, first);
        JloadRelation jload = checkJloadOutputRelationship(firstPlan, board);
        Locality locality = measureElectricalLocality(board, first);
        check(locality.totalMst <= Q30_NET_MST_LIMIT,
            "Q30 electrical net locality stays below the fixed MST bound: " +
                locality.totalMst);

        System.out.println("PASS: medium board floorplanning contracts assertions=" +
            assertions + " components=" + board.getComponentIds().size() +
            " pads=" + board.getPadIds().size() + " netMst=" + locality.totalMst +
            " netEdges=" + locality.edgeCount + " outline=" + first.outline.width +
            "x" + first.outline.height + " jloadOutput=" + jload.outputNearestTotal +
            " jloadOther=" + jload.otherNearestTotal);
    }

    private static PcbPlacementPlanner.Plan place(TroubleshootBoard board, long seed,
            int candidate) {
        return new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry())
            .plan(board, board.getPlacementConstraints(), seed, candidate);
    }

    private static void checkQ30GeometryAndAccess(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        check(board.getComponentIds().size() == 33,
            "Q30 has exactly 33 logical packages");
        check(board.getPadIds().size() == 82,
            "Q30 has exactly 82 board pads");
        check(plan.footprints.size() == 33,
            "placement has one footprint per Q30 package");

        TreeMap<String, PcbFootprint> byComponent = new TreeMap<String, PcbFootprint>();
        TreeSet<String> padIds = new TreeSet<String>();
        Vector<Rectangle> courtyards = new Vector<Rectangle>();
        for (PcbFootprint footprint : plan.footprints) {
            PcbComponentPlacement placement = footprint.getPlacement();
            String componentId = placement.getComponentId();
            check(byComponent.put(componentId, footprint) == null,
                "placement component IDs are unique: " + componentId);
            BoardComponent component = board.getComponent(componentId);
            check(component != null, "placement component exists on board: " + componentId);
            check(placement.getPhysicalPackage() == component.getPhysicalPackage(),
                "placement preserves package identity: " + componentId);
            Rectangle courtyard = placement.getRoutingCourtyard();
            check(inside(plan.outline, courtyard),
                "routing courtyard is inside outline: " + componentId);
            courtyards.add(courtyard);
            for (PcbPadPlacement pad : footprint.getPads()) {
                check(padIds.add(pad.getPadId()),
                    "placement pad IDs are unique: " + pad.getPadId());
                check(contains(pad.getPadBounds(), pad.getX(), pad.getY()),
                    "pad center is inside its pad bounds: " + pad.getPadId());
                int escapeX = pad.getX() + pad.getEscapeDx() * pad.getEscapeLength();
                int escapeY = pad.getY() + pad.getEscapeDy() * pad.getEscapeLength();
                check(inside(plan.outline, escapeX, escapeY),
                    "pad escape endpoint is inside outline: " + pad.getPadId());
                for (PcbFootprint other : plan.footprints) {
                    if (other == footprint) continue;
                    check(!contains(other.getPlacement().getRoutingCourtyard(),
                        escapeX, escapeY),
                        "pad escape endpoint remains accessible: " + pad.getPadId());
                }
            }
        }
        check(new TreeSet<String>(board.getComponentIds()).equals(
            new TreeSet<String>(byComponent.keySet())),
            "placement component IDs match board IDs");
        check(new TreeSet<String>(board.getPadIds()).equals(padIds),
            "placement pad IDs match board IDs");
        for (int first = 0; first < courtyards.size(); first++) {
            for (int second = first + 1; second < courtyards.size(); second++) {
                check(!overlaps(courtyards.get(first), courtyards.get(second)),
                    "routing courtyards do not overlap");
            }
        }
    }

    private static void checkConnectorLocality(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        TreeMap<String, PcbFootprint> byComponent = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints)
            byComponent.put(footprint.getPlacement().getComponentId(), footprint);
        for (String connectorId : new String[] { "J1", "JSA", "JSB", "JOA", "JOB" }) {
            long nearest = nearestSameNetPadDistance(board, byComponent, connectorId);
            check(nearest <= CONNECTOR_NEAREST_PAD_LIMIT,
                "connector has a nearby same-net pad: " + connectorId + " distance=" + nearest);
        }
    }

    private static long nearestSameNetPadDistance(TroubleshootBoard board,
            TreeMap<String, PcbFootprint> byComponent, String componentId) {
        PcbFootprint source = byComponent.get(componentId);
        long best = Long.MAX_VALUE;
        for (PcbPadPlacement pad : source.getPads()) {
            BoardPad boardPad = board.getPad(pad.getPadId());
            BoardNet net = board.getNet(boardPad.getNetId());
            for (String otherPadId : net.getPadIds()) {
                BoardPad otherBoardPad = board.getPad(otherPadId);
                if (componentId.equals(otherBoardPad.getComponentId())) continue;
                PcbPadPlacement other = byComponent.get(otherBoardPad.getComponentId())
                    .getPad(otherPadId);
                long distance = Math.abs((long) pad.getX() - other.getX()) +
                    Math.abs((long) pad.getY() - other.getY());
                if (distance < best) best = distance;
            }
        }
        return best;
    }

    private static JloadRelation checkJloadOutputRelationship(Rb30Plan plan,
            TroubleshootBoard board) {
        TreeMap<String, PcbPlacementConstraints.Part> demands =
            new TreeMap<String, PcbPlacementConstraints.Part>();
        for (PcbPlacementConstraints.Part part : board.getPlacementConstraints().getParts())
            demands.put(part.componentId, part);
        boolean loadHasOutputNeighbor = false;
        BoardNet loadNet = board.getNet("LOAD12");
        for (String padId : loadNet.getPadIds()) {
            BoardPad pad = board.getPad(padId);
            if (!"JLOAD".equals(pad.getComponentId()) &&
                    isOutputRegion(demands.get(pad.getComponentId())))
                loadHasOutputNeighbor = true;
        }
        check(loadHasOutputNeighbor, "JLOAD LOAD12 net reaches an output region");

        JloadRelation result = new JloadRelation();
        for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES;
                candidate++) {
            PcbPlacementPlanner.Plan placement = place(board, plan.layoutSeed, candidate);
            TreeMap<String, PcbFootprint> byComponent =
                new TreeMap<String, PcbFootprint>();
            for (PcbFootprint footprint : placement.footprints)
                byComponent.put(footprint.getPlacement().getComponentId(), footprint);
            long nearestOutput = Long.MAX_VALUE, nearestOther = Long.MAX_VALUE;
            for (Map.Entry<String, PcbFootprint> entry : byComponent.entrySet()) {
                if ("JLOAD".equals(entry.getKey())) continue;
                long distance = padDistance(byComponent.get("JLOAD"), entry.getValue());
                if (isOutputRegion(demands.get(entry.getKey())))
                    nearestOutput = Math.min(nearestOutput, distance);
                else
                    nearestOther = Math.min(nearestOther, distance);
            }
            check(nearestOutput != Long.MAX_VALUE && nearestOther != Long.MAX_VALUE,
                "JLOAD has both output and non-output placement cohorts");
            result.outputNearestTotal += nearestOutput;
            result.otherNearestTotal += nearestOther;
        }
        check(result.outputNearestTotal < result.otherNearestTotal,
            "JLOAD stays closer to output regions across candidates: output=" +
                result.outputNearestTotal + " other=" + result.otherNearestTotal);
        return result;
    }

    private static boolean isOutputRegion(PcbPlacementConstraints.Part part) {
        return part != null && part.regionLabel.startsWith("Output ");
    }

    private static long padDistance(PcbFootprint first, PcbFootprint second) {
        long best = Long.MAX_VALUE;
        for (PcbPadPlacement a : first.getPads()) {
            for (PcbPadPlacement b : second.getPads()) {
                long distance = Math.abs((long) a.getX() - b.getX()) +
                    Math.abs((long) a.getY() - b.getY());
                if (distance < best) best = distance;
            }
        }
        return best;
    }

    private static String placementSignature(PcbPlacementPlanner.Plan plan) {
        TreeMap<String, PcbFootprint> sorted = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints)
            sorted.put(footprint.getPlacement().getComponentId(), footprint);
        StringBuilder result = new StringBuilder();
        result.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append('|');
        for (Map.Entry<String, PcbFootprint> entry : sorted.entrySet()) {
            PcbComponentPlacement placement = entry.getValue().getPlacement();
            result.append(entry.getKey()).append('@').append(placement.getX()).append(',')
                .append(placement.getY()).append(',').append(placement.getWidth()).append(',')
                .append(placement.getHeight()).append(';');
        }
        return result.toString();
    }

    private static Locality measureElectricalLocality(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        TreeMap<String, PcbFootprint> footprints = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints)
            footprints.put(footprint.getPlacement().getComponentId(), footprint);
        Locality result = new Locality();
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String netId : netIds) {
            BoardNet net = board.getNet(netId);
            Vector<String> components = new Vector<String>();
            for (String padId : net.getPadIds()) {
                String componentId = board.getPad(padId).getComponentId();
                if (!components.contains(componentId)) components.add(componentId);
            }
            if (components.size() < 2) continue;
            Collections.sort(components);
            boolean[] used = new boolean[components.size()];
            long[] best = new long[components.size()];
            for (int index = 0; index < best.length; index++) best[index] = Long.MAX_VALUE;
            best[0] = 0;
            for (int edge = 0; edge < components.size(); edge++) {
                int selected = -1;
                for (int index = 0; index < components.size(); index++) {
                    if (!used[index] && (selected < 0 || best[index] < best[selected]))
                        selected = index;
                }
                check(selected >= 0 && best[selected] != Long.MAX_VALUE,
                    "net has a measurable pad connection: " + netId);
                used[selected] = true;
                result.totalMst += best[selected];
                if (edge > 0) result.edgeCount++;
                for (int index = 0; index < components.size(); index++) {
                    if (used[index]) continue;
                    long distance = netDistance(board, net, footprints,
                        components.get(selected), components.get(index));
                    if (distance < best[index]) best[index] = distance;
                }
            }
        }
        return result;
    }

    private static long netDistance(TroubleshootBoard board, BoardNet net,
            TreeMap<String, PcbFootprint> footprints, String first, String second) {
        long best = Long.MAX_VALUE;
        for (String firstPadId : net.getPadIds()) {
            BoardPad firstPad = board.getPad(firstPadId);
            if (!first.equals(firstPad.getComponentId())) continue;
            PcbPadPlacement firstPlacement = footprints.get(first).getPad(firstPadId);
            for (String secondPadId : net.getPadIds()) {
                BoardPad secondPad = board.getPad(secondPadId);
                if (!second.equals(secondPad.getComponentId())) continue;
                PcbPadPlacement secondPlacement = footprints.get(second).getPad(secondPadId);
                long distance = Math.abs((long) firstPlacement.getX() - secondPlacement.getX()) +
                    Math.abs((long) firstPlacement.getY() - secondPlacement.getY());
                if (distance < best) best = distance;
            }
        }
        return best;
    }

    private static boolean inside(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height;
    }

    private static boolean inside(Rectangle outer, int x, int y) {
        return x >= outer.x && y >= outer.y &&
            (long) x <= (long) outer.x + outer.width &&
            (long) y <= (long) outer.y + outer.height;
    }

    private static boolean contains(Rectangle outer, int x, int y) {
        return x >= outer.x && y >= outer.y &&
            (long) x <= (long) outer.x + outer.width &&
            (long) y <= (long) outer.y + outer.height;
    }

    private static boolean overlaps(Rectangle first, Rectangle second) {
        return first.x < (long) second.x + second.width &&
            second.x < (long) first.x + first.width &&
            first.y < (long) second.y + second.height &&
            second.y < (long) first.y + first.height;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Locality {
        long totalMst;
        int edgeCount;
    }

    private static final class JloadRelation {
        long outputNearestTotal;
        long otherNearestTotal;
    }
}
