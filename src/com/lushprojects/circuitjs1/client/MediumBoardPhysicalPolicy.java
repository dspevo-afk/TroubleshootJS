package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Versioned, provider-selected physical policy for structural medium-board
 * generation.
 *
 * <p>This policy is an investigation candidate.  It owns candidate breadth,
 * cheap placement ranking, and the preference order between the already
 * qualified P05 one-face router and the developer-only P07 fuller two-layer
 * router.  It does not change either router's limits, and it is not a normal
 * player admission envelope.</p>
 */
final class MediumBoardPhysicalPolicy {
    static final String ID = "MEDIUM_BOARD";
    static final int VERSION = 1;
    static final String IDENTITY = ID + "@" + VERSION;

    /** The planner already exposes six bounded outline/order alternatives. */
    static final int PLACEMENT_CANDIDATES = PcbPlacementPlanner.OUTLINE_CANDIDATES;
    /** Route only the cheapest bounded subset after all placements are ranked. */
    static final int ROUTING_CANDIDATES = 3;
    /** A P07 probe may replace P05 only with a measurable quality improvement. */
    static final int TWO_LAYER_QUALITY_IMPROVEMENT_PERCENT = 10;
    static final long TWO_LAYER_MIN_QUALITY_IMPROVEMENT = 5000L;

    static final String P05_ONE_FACE = "P05_ONE_FACE";
    static final String P07_FULLER_TWO_LAYER = "P07_FULLER_TWO_LAYER";

    private MediumBoardPhysicalPolicy() { }

    static String identity() { return IDENTITY; }

    static boolean selected(PcbPlacementConstraints constraints) {
        return constraints != null && ID.equals(constraints.physicalPolicyId) &&
            VERSION == constraints.physicalPolicyVersion;
    }

    static String canonical() {
        return IDENTITY + ";placements=" + PLACEMENT_CANDIDATES +
            ";routeSubset=" + ROUTING_CANDIDATES +
            ";routeOrder=" + P05_ONE_FACE + ">" + P07_FULLER_TWO_LAYER +
            ";p05=existing-bounded-recovery;" +
            "p07=existing-bounded-fuller-two-layer;" +
            "selection=electrical-mst-region-cohesion-connector-corridor-area;" +
            "p07Probe=1;qualityImprovement=" +
            TWO_LAYER_QUALITY_IMPROVEMENT_PERCENT + "%+" +
            TWO_LAYER_MIN_QUALITY_IMPROVEMENT + ";" +
            "normalAdmission=UNQUALIFIED";
    }

    /** Components of the deterministic placement-only score. Lower is better. */
    static final class PlacementScore {
        final long outlineArea;
        final long courtyardArea;
        final long demandArea;
        final long electricalSpan;
        final long regionCohesion;
        final long connectorDistance;
        final long corridorPenalty;
        final long total;

        PlacementScore(long outlineArea, long courtyardArea, long demandArea,
                long electricalSpan, long regionCohesion, long connectorDistance,
                long corridorPenalty) {
            if (outlineArea < 0 || courtyardArea < 0 || demandArea < 0 ||
                    electricalSpan < 0 || regionCohesion < 0 || connectorDistance < 0 ||
                    corridorPenalty < 0)
                throw new IllegalArgumentException("Invalid medium placement score");
            this.outlineArea = outlineArea;
            this.courtyardArea = courtyardArea;
            this.demandArea = demandArea;
            this.electricalSpan = electricalSpan;
            this.regionCohesion = regionCohesion;
            this.connectorDistance = connectorDistance;
            this.corridorPenalty = corridorPenalty;
            // Electrical locality is intentionally dominant.  Area terms are
            // normalized so outline area cannot erase a materially better
            // electrical/region/access arrangement.
            this.total = electricalSpan * 8L + regionCohesion * 4L +
                connectorDistance * 6L + corridorPenalty * 12L +
                outlineArea / 1000L + courtyardArea / 1000L + demandArea / 1000L;
        }

        String toCanonical() {
            return "total=" + total + ";electrical=" + electricalSpan +
                ";region=" + regionCohesion + ";connector=" + connectorDistance +
                ";corridor=" + corridorPenalty + ";outline=" + outlineArea +
                ";courtyard=" + courtyardArea + ";demand=" + demandArea;
        }
    }

    /** A planner result plus the stable attempt number used to create it. */
    static final class PlacementCandidate {
        final int attempt;
        final PcbPlacementPlanner.Plan plan;
        final PlacementScore score;
        final long cheapScore;

        PlacementCandidate(TroubleshootBoard board, int attempt,
                PcbPlacementPlanner.Plan plan) {
            if (attempt < 0 || plan == null)
                throw new IllegalArgumentException("Invalid medium placement candidate");
            this.attempt = attempt;
            this.plan = plan;
            this.score = score(board, plan);
            this.cheapScore = score.total;
        }

        /** Retained for placement-only unit fixtures; production uses board-aware scoring. */
        PlacementCandidate(int attempt, PcbPlacementPlanner.Plan plan) {
            this(null, attempt, plan);
        }

        String toCanonical() {
            return attempt + "{" + score.toCanonical() + "}";
        }
    }

    /**
     * Ranks placement-only plans without inspecting or mutating copper.  The
     * score uses only board pads/nets and planner-owned placement geometry, so
     * ranking cannot change package, pad, net, or correspondence identity.
     */
    static Vector<PlacementCandidate> rank(Vector<PlacementCandidate> input) {
        if (input == null)
            throw new IllegalArgumentException("Missing medium placement candidates");
        Vector<PlacementCandidate> result = new Vector<PlacementCandidate>(input);
        for (PlacementCandidate candidate : result)
            if (candidate == null) throw new IllegalArgumentException("Missing medium placement candidate");
        Collections.sort(result, new Comparator<PlacementCandidate>() {
            public int compare(PlacementCandidate first, PlacementCandidate second) {
                if (first.cheapScore != second.cheapScore)
                    return first.cheapScore < second.cheapScore ? -1 : 1;
                if (first.plan.candidate != second.plan.candidate)
                    return first.plan.candidate < second.plan.candidate ? -1 : 1;
                return first.attempt < second.attempt ? -1 :
                    first.attempt == second.attempt ? 0 : 1;
            }
        });
        return result;
    }

    static boolean materiallyBetterTwoLayer(double twoLayerQuality,
            double oneFaceQuality) {
        if (Double.isNaN(twoLayerQuality) || Double.isInfinite(twoLayerQuality) ||
                Double.isNaN(oneFaceQuality) || Double.isInfinite(oneFaceQuality) ||
                twoLayerQuality >= oneFaceQuality)
            return false;
        double improvement = oneFaceQuality - twoLayerQuality;
        return improvement >= TWO_LAYER_MIN_QUALITY_IMPROVEMENT &&
            improvement * 100.0 >= oneFaceQuality * TWO_LAYER_QUALITY_IMPROVEMENT_PERCENT;
    }

    private static PlacementScore score(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        long outlineArea = (long) plan.outline.width * plan.outline.height;
        if (board == null)
            return new PlacementScore(outlineArea, plan.courtyardArea,
                plan.demandArea, 0, 0, 0, 0);

        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        TreeMap<String,PcbFootprint> footprints = new TreeMap<String,PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints) {
            String componentId = footprint.getPlacement().getComponentId();
            if (footprints.put(componentId, footprint) != null)
                throw new IllegalArgumentException("Duplicate medium placement footprint: " +
                    componentId);
        }

        long electricalSpan = 0;
        long corridorPenalty = 0;
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String netId : netIds) {
            NetGeometry net = netGeometry(board, netId, footprints);
            int roleWeight = roleWeight(board.getNet(netId).getRoutingRole());
            electricalSpan += net.mstSpan * roleWeight;
            corridorPenalty += corridorPenalty(net, footprints);
        }

        long regionCohesion = regionCohesion(board, constraints, footprints);
        long connectorDistance = connectorDistance(board, footprints);
        return new PlacementScore(outlineArea, plan.courtyardArea, plan.demandArea,
            electricalSpan, regionCohesion, connectorDistance, corridorPenalty);
    }

    private static int roleWeight(BoardNet.RoutingRole role) {
        switch (role) {
        case SUPPLY:
        case RETURN:
            return 6;
        case HIGH_CURRENT:
            return 4;
        case CONTROL:
            return 2;
        case SIGNAL:
            return 1;
        default:
            throw new IllegalArgumentException("Unknown board routing role: " + role);
        }
    }

    private static final class PadPoint {
        final String componentId;
        final String padId;
        final int x, y;

        PadPoint(String componentId, String padId, int x, int y) {
            this.componentId = componentId;
            this.padId = padId;
            this.x = x;
            this.y = y;
        }
    }

    private static final class PadEdge {
        final PadPoint first, second;

        PadEdge(PadPoint first, PadPoint second) {
            this.first = first;
            this.second = second;
        }
    }

    private static final class NetGeometry {
        final Vector<PadPoint> points;
        final Vector<PadEdge> edges;
        final TreeMap<String,Boolean> components = new TreeMap<String,Boolean>();
        long mstSpan;

        NetGeometry(Vector<PadPoint> points, Vector<PadEdge> edges) {
            this.points = points;
            this.edges = edges;
            for (PadPoint point : points) components.put(point.componentId, Boolean.TRUE);
        }
    }

    private static NetGeometry netGeometry(TroubleshootBoard board, String netId,
            TreeMap<String,PcbFootprint> footprints) {
        Vector<String> padIds = board.getNet(netId).getPadIds();
        Collections.sort(padIds);
        Vector<PadPoint> points = new Vector<PadPoint>();
        for (String padId : padIds) {
            BoardPad boardPad = board.getPad(padId);
            PcbFootprint footprint = footprints.get(boardPad.getComponentId());
            if (footprint == null)
                throw new IllegalArgumentException("Medium score missing footprint: " +
                    boardPad.getComponentId());
            PcbPadPlacement pad = footprint.getPad(padId);
            points.add(new PadPoint(boardPad.getComponentId(), padId, pad.getX(), pad.getY()));
        }
        Vector<PadEdge> edges = new Vector<PadEdge>();
        NetGeometry result = new NetGeometry(points, edges);
        if (points.size() < 2) return result;

        boolean[] connected = new boolean[points.size()];
        long[] distance = new long[points.size()];
        int[] parent = new int[points.size()];
        for (int index = 0; index < distance.length; index++) {
            distance[index] = Long.MAX_VALUE;
            parent[index] = -1;
        }
        connected[0] = true;
        for (int index = 1; index < points.size(); index++) {
            distance[index] = padDistance(points.get(0), points.get(index));
            parent[index] = 0;
        }
        for (int count = 1; count < points.size(); count++) {
            int best = -1;
            for (int index = 0; index < points.size(); index++) {
                if (connected[index] || (best >= 0 && distance[index] >= distance[best]))
                    continue;
                best = index;
            }
            if (best < 0) throw new IllegalStateException("Medium score MST disconnected");
            connected[best] = true;
            result.mstSpan += distance[best];
            edges.add(new PadEdge(points.get(parent[best]), points.get(best)));
            for (int index = 0; index < points.size(); index++) {
                if (connected[index]) continue;
                long candidate = padDistance(points.get(best), points.get(index));
                if (candidate < distance[index]) {
                    distance[index] = candidate;
                    parent[index] = best;
                }
            }
        }
        return result;
    }

    private static long padDistance(PadPoint first, PadPoint second) {
        if (first.componentId.equals(second.componentId)) return 0;
        return Math.abs((long) first.x - second.x) + Math.abs((long) first.y - second.y);
    }

    private static long regionCohesion(TroubleshootBoard board,
            PcbPlacementConstraints constraints,
            TreeMap<String,PcbFootprint> footprints) {
        TreeMap<String,RegionBounds> regions = new TreeMap<String,RegionBounds>();
        for (String componentId : board.getComponentIds()) {
            PcbFootprint footprint = footprints.get(componentId);
            if (footprint == null) continue;
            PcbPlacementConstraints.Part demand = constraints.get(componentId);
            RegionBounds bounds = regions.get(demand.regionId);
            if (bounds == null) {
                bounds = new RegionBounds();
                regions.put(demand.regionId, bounds);
            }
            PcbComponentPlacement placement = footprint.getPlacement();
            bounds.add(placement.getX() + placement.getWidth() / 2,
                placement.getY() + placement.getHeight() / 2);
        }
        long result = 0;
        for (RegionBounds bounds : regions.values())
            if (bounds.count > 1) result += bounds.maxX - bounds.minX +
                bounds.maxY - bounds.minY;
        return result;
    }

    private static final class RegionBounds {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, count;

        void add(int x, int y) {
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); count++;
        }
    }

    private static long connectorDistance(TroubleshootBoard board,
            TreeMap<String,PcbFootprint> footprints) {
        long result = 0;
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            BoardComponent component = board.getComponent(componentId);
            if (!component.getPhysicalPackage().isConnector()) continue;
            PcbFootprint connector = footprints.get(componentId);
            if (connector == null) continue;
            Vector<String> padIds = component.getPadIds();
            Collections.sort(padIds);
            for (String padId : padIds) {
                BoardPad source = board.getPad(padId);
                PcbPadPlacement sourcePlacement = connector.getPad(padId);
                long nearest = Long.MAX_VALUE;
                for (String otherPadId : board.getNet(source.getNetId()).getPadIds()) {
                    BoardPad other = board.getPad(otherPadId);
                    BoardComponent otherComponent = board.getComponent(other.getComponentId());
                    if (otherComponent.getId().equals(componentId) ||
                            otherComponent.getPhysicalPackage().isConnector()) continue;
                    PcbFootprint otherFootprint = footprints.get(other.getComponentId());
                    if (otherFootprint == null) continue;
                    PcbPadPlacement otherPlacement = otherFootprint.getPad(otherPadId);
                    nearest = Math.min(nearest, Math.abs((long) sourcePlacement.getX() -
                        otherPlacement.getX()) + Math.abs((long) sourcePlacement.getY() -
                        otherPlacement.getY()));
                }
                if (nearest != Long.MAX_VALUE) result += nearest;
            }
        }
        return result;
    }

    private static long corridorPenalty(NetGeometry net,
            TreeMap<String,PcbFootprint> footprints) {
        long result = 0;
        for (PadEdge edge : net.edges) {
            Rectangle corridor = corridor(edge.first, edge.second);
            if (corridor.width <= 0 || corridor.height <= 0) continue;
            for (PcbFootprint footprint : footprints.values()) {
                if (net.components.containsKey(footprint.getPlacement().getComponentId()))
                    continue;
                long overlap = intersectionArea(corridor,
                    footprint.getPlacement().getRoutingCourtyard());
                if (overlap > 0) result += Math.max(1L, overlap / 100L);
            }
        }
        return result;
    }

    private static Rectangle corridor(PadPoint first, PadPoint second) {
        int left = Math.min(first.x, second.x) - 20;
        int top = Math.min(first.y, second.y) - 20;
        int right = Math.max(first.x, second.x) + 20;
        int bottom = Math.max(first.y, second.y) + 20;
        return new Rectangle(left, top, right - left, bottom - top);
    }

    private static long intersectionArea(Rectangle first, Rectangle second) {
        long left = Math.max(first.x, second.x);
        long top = Math.max(first.y, second.y);
        long right = Math.min((long) first.x + first.width,
            (long) second.x + second.width);
        long bottom = Math.min((long) first.y + first.height,
            (long) second.y + second.height);
        return right <= left || bottom <= top ? 0 : (right - left) * (bottom - top);
    }

    /** Immutable route/generation receipt for a structural corpus harness. */
    static final class Statistics {
        final String policyIdentity;
        final String outcome;
        final String selectedRoutePolicy;
        final int placementCandidates;
        final int placementRejections;
        final int routeAttempts;
        final int routingOrders;
        final int routingExpansions;
        final int oneFaceAttempts;
        final int oneFaceSuccesses;
        final int twoLayerAttempts;
        final int twoLayerSuccesses;
        final int selectedPlacementAttempt;
        final Vector<Integer> rankedPlacementAttempts;
        final Vector<String> placementScores;
        final long placementEvaluations;
        final Vector<String> routeOutcomes;
        final long selectedPlacementScore;
        final long selectedRouteQualityMilli;

        Statistics(String outcome, String selectedRoutePolicy,
                int placementCandidates, int placementRejections, int routeAttempts,
                int oneFaceAttempts, int oneFaceSuccesses, int twoLayerAttempts,
                int twoLayerSuccesses, int selectedPlacementAttempt,
                Vector<Integer> rankedPlacementAttempts, Vector<String> routeOutcomes) {
            this(outcome, selectedRoutePolicy, placementCandidates, placementRejections,
                routeAttempts, oneFaceAttempts, oneFaceSuccesses, twoLayerAttempts,
                twoLayerSuccesses, selectedPlacementAttempt, rankedPlacementAttempts,
                new Vector<String>(), routeOutcomes, 0L, 0, 0, -1, -1);
        }

        Statistics(String outcome, String selectedRoutePolicy,
                int placementCandidates, int placementRejections, int routeAttempts,
                int oneFaceAttempts, int oneFaceSuccesses, int twoLayerAttempts,
                int twoLayerSuccesses, int selectedPlacementAttempt,
                Vector<Integer> rankedPlacementAttempts, Vector<String> routeOutcomes,
                int routingOrders, int routingExpansions) {
            this(outcome, selectedRoutePolicy, placementCandidates, placementRejections,
                routeAttempts, oneFaceAttempts, oneFaceSuccesses, twoLayerAttempts,
                twoLayerSuccesses, selectedPlacementAttempt, rankedPlacementAttempts,
                new Vector<String>(), routeOutcomes, 0L, routingOrders,
                routingExpansions, -1, -1);
        }

        Statistics(String outcome, String selectedRoutePolicy,
                int placementCandidates, int placementRejections, int routeAttempts,
                int oneFaceAttempts, int oneFaceSuccesses, int twoLayerAttempts,
                int twoLayerSuccesses, int selectedPlacementAttempt,
                Vector<Integer> rankedPlacementAttempts, Vector<String> placementScores,
                Vector<String> routeOutcomes, long placementEvaluations,
                int routingOrders, int routingExpansions,
                long selectedPlacementScore, long selectedRouteQualityMilli) {
            if (outcome == null || placementCandidates < 0 || placementRejections < 0 ||
                    routeAttempts < 0 || oneFaceAttempts < 0 || oneFaceSuccesses < 0 ||
                    twoLayerAttempts < 0 || twoLayerSuccesses < 0 ||
                    placementEvaluations < 0 ||
                    routingOrders < 0 || routingExpansions < 0 ||
                    selectedPlacementScore < -1 || selectedRouteQualityMilli < -1)
                throw new IllegalArgumentException("Invalid medium policy statistics");
            this.policyIdentity = IDENTITY;
            this.outcome = outcome;
            this.selectedRoutePolicy = selectedRoutePolicy;
            this.placementCandidates = placementCandidates;
            this.placementRejections = placementRejections;
            this.routeAttempts = routeAttempts;
            this.routingOrders = routingOrders;
            this.routingExpansions = routingExpansions;
            this.oneFaceAttempts = oneFaceAttempts;
            this.oneFaceSuccesses = oneFaceSuccesses;
            this.twoLayerAttempts = twoLayerAttempts;
            this.twoLayerSuccesses = twoLayerSuccesses;
            this.selectedPlacementAttempt = selectedPlacementAttempt;
            this.rankedPlacementAttempts = rankedPlacementAttempts == null ?
                new Vector<Integer>() : new Vector<Integer>(rankedPlacementAttempts);
            this.placementScores = placementScores == null ?
                new Vector<String>() : new Vector<String>(placementScores);
            this.placementEvaluations = placementEvaluations;
            this.routeOutcomes = routeOutcomes == null ?
                new Vector<String>() : new Vector<String>(routeOutcomes);
            this.selectedPlacementScore = selectedPlacementScore;
            this.selectedRouteQualityMilli = selectedRouteQualityMilli;
        }

        String toCanonical() {
            StringBuilder result = new StringBuilder(policyIdentity);
            result.append(";outcome=").append(outcome);
            result.append(";selected=").append(selectedRoutePolicy);
            result.append(";placements=").append(placementCandidates);
            result.append(";placementRejects=").append(placementRejections);
            result.append(";placementEvaluations=").append(placementEvaluations);
            result.append(";routes=").append(routeAttempts);
            result.append(";routingWork=").append(routingOrders).append('/')
                .append(routingExpansions);
            result.append(";p05=").append(oneFaceAttempts).append('/').append(oneFaceSuccesses);
            result.append(";p07=").append(twoLayerAttempts).append('/').append(twoLayerSuccesses);
            result.append(";selectedAttempt=").append(selectedPlacementAttempt);
            result.append(";ranked=").append(rankedPlacementAttempts);
            result.append(";placementScores=").append(placementScores);
            result.append(";selectedScore=").append(selectedPlacementScore);
            result.append(";selectedQualityMilli=").append(selectedRouteQualityMilli);
            result.append(";routeOutcomes=").append(routeOutcomes);
            return result.toString();
        }
    }

    /** Layout plus the bounded, deterministic policy receipt. */
    static final class Result {
        final PcbBoardLayout layout;
        final Statistics statistics;
        final String failure;

        Result(PcbBoardLayout layout, Statistics statistics, String failure) {
            if (statistics == null)
                throw new IllegalArgumentException("Missing medium policy statistics");
            this.layout = layout;
            this.statistics = statistics;
            this.failure = failure;
        }

        boolean accepted() { return layout != null; }
        PcbBoardLayout getLayout() { return layout; }
        Statistics getStatistics() { return statistics; }
        String getFailure() { return failure; }
        String toCanonical() {
            return statistics.toCanonical() + ";failure=" + String.valueOf(failure);
        }
    }
}
