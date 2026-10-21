package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/**
 * Generates only physical PCB geometry.  Stable board components, pads, and
 * nets are supplied by the logical board and are never invented here.
 */
class SeededPcbLayoutGenerator {
    /** Current corrected layout algorithm; package geometry remains contract v3. */
    static final int CURRENT_VERSION = 19;
    private static final int GRID = 10;
    private static final int MAX_ATTEMPTS = 80;
    private static final int TARGET_VIABLE_CANDIDATES = 5;
    private static final int FINAL_BOARD_X = 40;
    private static final int FINAL_BOARD_Y = 40;
    private static final int FINAL_EDGE_MARGIN = 26;
    private final PcbFootprintRegistry footprintRegistry;
    private final AttemptObserver attemptObserver;
    private final int layoutAlgorithmVersion;

    SeededPcbLayoutGenerator() {
        this(StandardPcbFootprintProviders.createRegistry(), null);
    }

    SeededPcbLayoutGenerator(PcbFootprintRegistry footprintRegistry) {
        this(footprintRegistry, null);
    }

    SeededPcbLayoutGenerator(PcbFootprintRegistry footprintRegistry,
            AttemptObserver attemptObserver) {
        if (footprintRegistry == null)
            throw new IllegalArgumentException("Missing PCB footprint registry");
        this.footprintRegistry = footprintRegistry;
        this.attemptObserver = attemptObserver == null ? DEFAULT_ATTEMPT_OBSERVER :
            attemptObserver;
        this.layoutAlgorithmVersion = CURRENT_VERSION;
    }

    int getLayoutAlgorithmVersion() { return layoutAlgorithmVersion; }

    /**
     * Receives one deterministic checkpoint immediately before each generation
     * attempt.  An observer exception propagates to the caller and is never
     * interpreted as a candidate rejection.
     */
    interface AttemptObserver {
        void check(int attempt);
    }

    private static final AttemptObserver DEFAULT_ATTEMPT_OBSERVER =
        new AttemptObserver() {
            public void check(int attempt) { GenerationWorkScope.check(); }
        };

    PcbBoardLayout generate(TroubleshootBoard board, long seed) {
        return generate(board, seed, attemptObserver);
    }
    PcbBoardLayout generate(TroubleshootBoard board,long seed,long routingSeed) {
        return generate(board,seed,attemptObserver,routingSeed);
    }

    /**
     * Generates with a per-call checkpoint owner.  The overload lets a staged
     * coordinator attach a synchronous budget without sharing mutable state
     * through this reusable layout generator.
     */
    PcbBoardLayout generate(TroubleshootBoard board, long seed,
            AttemptObserver observer) {
        return generate(board,seed,observer,seed);
    }
    private PcbBoardLayout generate(TroubleshootBoard board,long seed,AttemptObserver observer,long routingSeed) {
        Session session=new Session(board,seed,routingSeed,observer);
        while(!session.advance()) { }
        return session.result();
    }
    Session begin(TroubleshootBoard board,long seed,long routingSeed) {
        return new Session(board,seed,routingSeed,attemptObserver);
    }
    PcbBoardLayout generate(TroubleshootBoard board, long seed, long routingSeed, SupportedEnvelope envelope) {
        Session session = new Session(board,seed,routingSeed,attemptObserver,envelope);
        while (!session.advance()) { }
        return session.result();
    }
    /**
     * Runs a provider-selected medium policy and returns its bounded receipt.
     * Expected physical exhaustion is represented in the result so a
     * structural corpus can retain per-seed failures without weakening the
     * normal generator's typed rejection behavior.
     */
    MediumBoardPhysicalPolicy.Result generateWithPolicyResult(
            TroubleshootBoard board, long seed, long routingSeed) {
        return generateWithPolicyResult(board,seed,routingSeed,attemptObserver);
    }
    MediumBoardPhysicalPolicy.Result generateWithPolicyResult(
            TroubleshootBoard board, long seed, long routingSeed,
            AttemptObserver observer) {
        Session session = new Session(board,seed,routingSeed,observer);
        try {
            while (!session.advance()) { }
        } catch (PcbRoutingRejectedException expected) {
            MediumBoardPhysicalPolicy.Result result = session.mediumResult();
            if (result != null) return result;
            throw expected;
        }
        MediumBoardPhysicalPolicy.Result result = session.mediumResult();
        if (result == null)
            throw new IllegalArgumentException("Policy result requested for a non-medium board");
        return result;
    }
    Session begin(TroubleshootBoard board,long seed,long routingSeed,SupportedEnvelope envelope) {
        return new Session(board,seed,routingSeed,attemptObserver,envelope);
    }
    /** Advances one placement attempt or one retained route slice per generation work unit. */
    final class Session {
        private final TroubleshootBoard board;
        private final long seed,routingSeed;
        private final AttemptObserver observer;
        private final SupportedEnvelope envelope;
        private final GenerationStatistics statistics=new GenerationStatistics();
        private PcbRoutingRejectedException lastFailure;
        private PcbBoardLayout bestLayout;
        private PcbPlacementPlanner.Plan bestPlan, packingPlan;
        private PcbBoardLayout packingBaseline;
        private PcbPackingMetrics packingBaselineMetrics;
        private PcbLayerRoutingPrototype.Session packingRoute;
        private String packingBaselineHash, packingRoutePolicy;
        private int packingInitialOrders, packingInitialExpansions;
        private long packingInitialMillis;
        private boolean packingActive;
        private PcbPackingProbeReceipt packingProbeReceipt;
        private PcbPackingProbeReceipt.MetricEvidence packingMetricEvidence;
        private double bestScore=Double.POSITIVE_INFINITY;
        private long bestArea=Long.MAX_VALUE;
        private int attempt,viableCandidates;
        private boolean complete;
        private final boolean mediumPolicy;
        private Vector<MediumBoardPhysicalPolicy.PlacementCandidate> mediumPlacements;
        private int mediumPlacementAttempt, mediumRouteAttempt, mediumRouteStage;
        private PcbLayerRoutingPrototype.Session mediumTwoLayerRoute;
        private MediumBoardPhysicalPolicy.PlacementCandidate mediumTwoLayerCandidate;
        private long mediumTwoLayerElapsedMillis;
        private int mediumTwoLayerCheckpoint=-1;
        private int mediumPlacementRejections, mediumOneFaceAttempts,
            mediumOneFaceSuccesses, mediumTwoLayerAttempts, mediumTwoLayerSuccesses;
        private long mediumPlacementEvaluations;
        private String mediumSelectedRoute;
        private int mediumSelectedPlacementAttempt = -1;
        private final Vector<Integer> mediumRankedAttempts = new Vector<Integer>();
        private final Vector<String> mediumPlacementScores = new Vector<String>();
        private final Vector<String> mediumRouteOutcomes = new Vector<String>();
        private PcbBoardLayout mediumOneFaceLayout, mediumTwoLayerLayout;
        private PcbPlacementPlanner.Plan mediumOneFacePlan, mediumTwoLayerPlan;
        private double mediumOneFaceQuality = Double.POSITIVE_INFINITY;
        private double mediumTwoLayerQuality = Double.POSITIVE_INFINITY;
        private long mediumOneFaceArea = Long.MAX_VALUE;
        private long mediumTwoLayerArea = Long.MAX_VALUE;
        private int mediumOneFacePlacementAttempt = -1;
        private int mediumTwoLayerPlacementAttempt = -1;
        private long mediumOneFacePlacementScore = -1;
        private long mediumTwoLayerPlacementScore = -1;
        private long mediumSelectedPlacementScore = -1;
        private long mediumSelectedRouteQualityMilli = -1;
        private MediumBoardPhysicalPolicy.Result mediumPolicyResult;
        Session(TroubleshootBoard board,long seed,long routingSeed,AttemptObserver observer) {
            this(board,seed,routingSeed,observer,null);
        }
        Session(TroubleshootBoard board,long seed,long routingSeed,AttemptObserver observer,SupportedEnvelope envelope) {
            this.envelope=envelope;
            if(board==null)throw new IllegalArgumentException("Missing logical board for PCB generation");
            this.board=board;this.seed=seed;this.routingSeed=routingSeed;
            this.observer=observer==null?attemptObserver:observer;
            PcbPlacementConstraints constraints = board.getPlacementConstraints();
            if (constraints == null)
                throw new IllegalArgumentException("Missing physical placement policy");
            this.mediumPolicy=MediumBoardPhysicalPolicy.selected(constraints);
            if (!mediumPolicy && !constraints.usesDefaultPhysicalPolicy())
                throw new IllegalArgumentException("Unsupported physical placement policy: " +
                    constraints.getPhysicalPolicyIdentity());
        }
        boolean advance() {
            if(complete)throw new IllegalStateException("PCB planning is already complete");
            if (packingActive) return advancePackingProbe(this);
            if (mediumPolicy) return SeededPcbLayoutGenerator.this.advanceMedium(this);
            observer.check(attempt);
            statistics.placements++;
            try {
                int variationMode = (int) ((seed % 4 + 4) % 4);
                RoutedCandidate routed = generateAttempt(board, seed,
                    variationMode, attempt, observer,routingSeed,statistics);
                PcbBoardLayout candidate = routed.layout;
                if (envelope != null) envelope.requireBounds(board,candidate);
                double score = candidate.getRouteQualityScore(board);
                Rectangle bounds = candidate.getBoardOutline();
                long area = (long)bounds.width * bounds.height;
                if (bestLayout == null || area < bestArea || (area == bestArea && score < bestScore)) {
                    bestArea = area;
                    bestLayout = candidate;
                    bestPlan = routed.plan;
                    bestScore = score;
                }
                viableCandidates++;
            } catch (CandidateRejected failure) {
                lastFailure = PcbRoutingRejectedException.attemptRejected(
                    failure.getKind(), attempt, seed, failure.getMessage(),failure.recovery);
            } catch (SupportedEnvelope.Rejected failure) {
                lastFailure = PcbRoutingRejectedException.attemptRejected(
                    PcbRoutingRejectedException.Kind.PLACEMENT, attempt, seed, failure.getMessage());
            } catch (PcbBoardLayout.RouteQualityRejectedException failure) {
                lastFailure = PcbRoutingRejectedException.attemptRejected(
                    PcbRoutingRejectedException.Kind.ROUTING, attempt, seed,
                    failure.getMessage());
            }
            attempt++;
            boolean primaryComplete=attempt==MAX_ATTEMPTS || viableCandidates >=
                (board.getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM ? 1 : TARGET_VIABLE_CANDIDATES);
            if(primaryComplete) {
                if(bestLayout==null) {
                    if(lastFailure==null)throw new IllegalStateException("PCB generation stopped without a result or rejection");
                    throw PcbRoutingRejectedException.exhausted(seed,MAX_ATTEMPTS,lastFailure);
                }
                if (beginPackingProbe(this)) return false;
                return completeSmallSuccess(this);
            }
            return false;
        }
        PcbBoardLayout result() {
            if(!complete || bestLayout==null)throw new IllegalStateException("PCB planning has no completed result");
            return bestLayout;
        }
        MediumBoardPhysicalPolicy.Result mediumResult() { return mediumPolicyResult; }
    }

    private boolean completeSmallSuccess(Session session) {
        session.bestLayout.setGenerationStatistics(session.statistics.placements,
            session.statistics.routes, session.statistics.expansions,
            session.statistics.routeMillis, session.seed, session.routingSeed);
        session.complete = true;
        return true;
    }

    /** One optional candidate follows the unchanged, fully routed winner. */
    private boolean beginPackingProbe(Session session) {
        PcbPlacementConstraints constraints = session.board.getPlacementConstraints();
        if (constraints.placementPackingVersion !=
                PcbPlacementConstraints.CONNECTED_POCKET_PACKING_VERSION) return false;
        GenerationWorkScope.check();
        session.packingMetricEvidence = PcbPackingProbeReceipt.MetricEvidence.notMeasured();
        if (session.bestLayout == null || session.bestPlan == null)
            throw new IllegalStateException("Packing probe has no selected source");
        session.packingRoutePolicy = session.mediumPolicy ? session.mediumSelectedRoute :
            MediumBoardPhysicalPolicy.P05_ONE_FACE;
        session.packingBaseline = session.bestLayout;
        session.packingBaselineHash = PlayerSessionFingerprint.of(
            session.packingBaseline.geometryFingerprint());
        // Keep the exact post-route compaction plan in its routing coordinate
        // frame. The final display crop may translate pads by a non-grid amount.
        PcbPlacementPlanner.Plan source = session.bestPlan;
        TaskPackingSpanObservation.emit("SOURCE", session.board,
            source, session.seed, session.routingSeed);
        session.packingPlan = PcbPlacementCompactor.compact(session.board, constraints, source);
        TaskPackingSpanObservation.emit("ORDINARY", session.board,
            session.packingPlan, session.seed, session.routingSeed);
        session.packingPlan = PcbPlacementCompactor.compactPairedVacancies(
            session.board, constraints, session.packingPlan);
        TaskPackingSpanObservation.emit("PAIRED", session.board,
            session.packingPlan, session.seed, session.routingSeed);
        TaskPackingSpanObservation.emit("FINAL", session.board,
            session.packingPlan, session.seed, session.routingSeed);
        if (session.mediumPolicy)
            session.mediumPlacementEvaluations += session.packingPlan.evaluations - source.evaluations;
        if (PcbPlacementCompactor.samePlacement(source, session.packingPlan)) {
            keepWithoutPackingRoute(session, "NO_CHANGE");
            return false;
        }
        int remainingExpansions = packingExpansionLimit(session) - session.statistics.expansions;
        int requiredOrders = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
            session.packingRoutePolicy) ? PcbLayerRoutingPrototype.ORDERINGS :
                PcbRoutingWork.Limits.MAX_ORDERINGS;
        if (remainingExpansions < 1 ||
                session.statistics.routes > SupportedEnvelope.MAX_ROUTE_ATTEMPTS - requiredOrders ||
                MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(session.packingRoutePolicy) &&
                    remainingExpansions < PcbRoutingWork.Limits.MAX_EXPANSIONS) {
            keepWithoutPackingRoute(session, "NO_HEADROOM");
            return false;
        }
        try {
            session.packingBaselineMetrics = PcbPackingMetrics.measure(
                session.board, session.packingBaseline);
        } catch (PcbPackingMetrics.Unavailable unsupportedGrid) {
            session.packingMetricEvidence = PcbPackingProbeReceipt.MetricEvidence
                .baselineUnavailable(unsupportedGrid.getMessage());
            keepWithoutPackingRoute(session, "NO_HEADROOM");
            return false;
        }
        session.packingMetricEvidence = PcbPackingProbeReceipt.MetricEvidence
            .baselineOnly(session.packingBaselineMetrics);
        session.packingInitialOrders = session.statistics.routes;
        session.packingInitialExpansions = session.statistics.expansions;
        session.packingInitialMillis = session.statistics.routeMillis;
        session.packingActive = true;
        if (MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(session.packingRoutePolicy)) {
            long started = System.currentTimeMillis();
            try {
                session.packingRoute = PcbLayerRoutingPrototype.begin(session.board,
                    session.packingPlan.materialize(),
                    PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, session.observer,
                    Math.min(PcbLayerRoutingPrototype.MAX_EXPANSIONS, remainingExpansions),
                    session.packingBaselineMetrics.vias);
            } finally {
                session.statistics.routeMillis += System.currentTimeMillis() - started;
            }
        }
        return true;
    }

    private static int packingExpansionLimit(Session session) {
        return session.mediumPolicy ? MediumBoardPhysicalPolicy.ROUTING_CANDIDATES * 2 *
            PcbRoutingWork.Limits.MAX_EXPANSIONS : SupportedEnvelope.MAX_EXPANSIONS;
    }

    private void keepWithoutPackingRoute(Session session, String disposition) {
        session.packingProbeReceipt = new PcbPackingProbeReceipt(0, 0, 0, 0,
            session.packingRoutePolicy, disposition, session.packingBaselineHash,
            session.packingBaselineHash, session.packingMetricEvidence);
        session.bestLayout.setPackingProbeReceipt(session.packingProbeReceipt);
    }

    private boolean advancePackingProbe(Session session) {
        session.observer.check(session.bestPlan.candidate);
        try {
            RoutedCandidate routedCandidate;
            if (session.packingRoute != null) {
                long started = System.currentTimeMillis();
                boolean complete;
                try {
                    complete = session.packingRoute.advanceSlice();
                } finally {
                    session.statistics.routeMillis += System.currentTimeMillis() - started;
                }
                if (!complete) return false;
                PcbLayerRoutingPrototype.Result routed = session.packingRoute.result();
                session.packingRoute = null;
                session.statistics.routes += routed.orderings;
                session.statistics.expansions += routed.expansions;
                MediumBoardPhysicalPolicy.PlacementCandidate placement =
                    new MediumBoardPhysicalPolicy.PlacementCandidate(session.board,
                        session.bestPlan.candidate, session.packingPlan);
                routedCandidate = finishMediumTwoLayerRoute(session.board, session.seed,
                    placement, routed);
            } else {
                int variation = (int) ((session.seed % 4 + 4) % 4);
                routedCandidate = routePlan(session.board, session.seed, variation,
                    session.bestPlan.candidate, session.observer, session.routingSeed,
                    session.statistics, session.packingPlan, true);
            }
            PcbBoardLayout candidate = routedCandidate.layout;
            if (session.envelope != null) session.envelope.requireBounds(session.board, candidate);
            PcbPackingMetrics metrics;
            try {
                metrics = PcbPackingMetrics.measure(session.board, candidate);
            } catch (PcbPackingMetrics.Unavailable unsupportedGrid) {
                session.packingMetricEvidence = PcbPackingProbeReceipt.MetricEvidence
                    .candidateUnavailable(session.packingBaselineMetrics, unsupportedGrid.getMessage());
                return finishPackingProbe(session, "KEPT");
            }
            session.packingMetricEvidence = PcbPackingProbeReceipt.MetricEvidence
                .compared(session.packingBaselineMetrics, metrics);
            if (PcbPackingMetrics.strictlyImproves(session.packingBaselineMetrics, metrics)) {
                session.bestLayout = candidate;
                session.bestPlan = routedCandidate.plan;
                session.bestArea = metrics.area;
                session.bestScore = candidate.getRouteQualityScore(session.board);
                if (session.mediumPolicy) {
                    session.mediumSelectedPlacementScore =
                        new MediumBoardPhysicalPolicy.PlacementCandidate(session.board,
                            session.bestPlan.candidate, session.bestPlan).cheapScore;
                    session.mediumSelectedRouteQualityMilli = routeQualityMilli(session.bestScore);
                }
                return finishPackingProbe(session, "SELECTED");
            }
            return finishPackingProbe(session, "KEPT");
        } catch (CandidateRejected rejectedRoute) {
            TaskPackingObservation.emit("PACKING_CAUGHT", "PACKING_EXPECTED_CATCH",
                session.packingPlan, session.seed, session.routingSeed,
                session.packingBaselineHash, session.packingRoutePolicy,
                rejectedRoute, rejectedRoute.getKind().name(), null, null, rejectedRoute.recovery);
            return finishPackingProbe(session, "ROUTE_REJECTED");
        } catch (PcbPlacementPlanner.Rejected rejectedAccess) {
            TaskPackingObservation.emit("PACKING_CAUGHT", "PACKING_EXPECTED_CATCH",
                session.packingPlan, session.seed, session.routingSeed,
                session.packingBaselineHash, session.packingRoutePolicy,
                rejectedAccess, "PLACEMENT", rejectedAccess.reason, null, null);
            return finishPackingProbe(session, "ROUTE_REJECTED");
        } catch (SupportedEnvelope.Rejected rejectedEnvelope) {
            TaskPackingObservation.emit("PACKING_CAUGHT", "PACKING_EXPECTED_CATCH",
                session.packingPlan, session.seed, session.routingSeed,
                session.packingBaselineHash, session.packingRoutePolicy,
                rejectedEnvelope, "ENVELOPE", rejectedEnvelope.reason.name(), null, null);
            return finishPackingProbe(session, "ROUTE_REJECTED");
        } catch (PcbBoardLayout.RouteQualityRejectedException rejectedQuality) {
            TaskPackingObservation.emit("PACKING_CAUGHT", "PACKING_EXPECTED_CATCH",
                session.packingPlan, session.seed, session.routingSeed,
                session.packingBaselineHash, session.packingRoutePolicy,
                rejectedQuality, "QUALITY", null, null, null);
            return finishPackingProbe(session, "ROUTE_REJECTED");
        }
    }

    private boolean finishPackingProbe(Session session, String disposition) {
        if (!session.packingBaselineHash.equals(PlayerSessionFingerprint.of(
                session.packingBaseline.geometryFingerprint())))
            throw new IllegalStateException("Packing probe mutated its routed incumbent");
        session.packingProbeReceipt = new PcbPackingProbeReceipt(1,
            session.statistics.routes - session.packingInitialOrders,
            session.statistics.expansions - session.packingInitialExpansions,
            session.statistics.routeMillis - session.packingInitialMillis,
            session.packingRoutePolicy, disposition, session.packingBaselineHash,
            PlayerSessionFingerprint.of(session.bestLayout.geometryFingerprint()),
            session.packingMetricEvidence);
        session.bestLayout.setPackingProbeReceipt(session.packingProbeReceipt);
        session.packingActive = false;
        session.packingRoute = null;
        return session.mediumPolicy ? completeMediumSuccess(session) : completeSmallSuccess(session);
    }

    private boolean advanceMedium(Session session) {
        if (session.mediumTwoLayerRoute != null) {
            int routeCheckpoint=session.mediumTwoLayerCheckpoint;
            session.observer.check(routeCheckpoint);
            return advanceMediumTwoLayerRoute(session);
        }
        int checkpoint = session.attempt++;
        session.observer.check(checkpoint);
        if (session.mediumRouteStage == 0)
            return advanceMediumPlacement(session, checkpoint);
        if (session.mediumRouteStage == 1)
            return advanceMediumRouting(session, checkpoint, false);
        if (session.mediumRouteStage == 2)
            return advanceMediumRouting(session, checkpoint, true);
        throw new IllegalStateException("Unknown medium physical-policy stage");
    }

    private boolean advanceMediumPlacement(Session session, int checkpoint) {
        if (session.mediumPlacements == null)
            session.mediumPlacements = new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        int placementAttempt = session.mediumPlacementAttempt++;
        session.statistics.placements++;
        try {
            PcbPlacementPlanner.Plan plan = new PcbPlacementPlanner(footprintRegistry).plan(
                session.board, session.board.getPlacementConstraints(), session.seed,
                placementAttempt);
            MediumBoardPhysicalPolicy.PlacementCandidate candidate =
                new MediumBoardPhysicalPolicy.PlacementCandidate(
                    session.board, placementAttempt, plan);
            session.mediumPlacements.add(candidate);
            session.mediumPlacementEvaluations += plan.evaluations;
        } catch (PcbPlacementPlanner.Rejected failure) {
            session.mediumPlacementRejections++;
            session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                PcbRoutingRejectedException.Kind.PLACEMENT, checkpoint, session.seed,
                failure.reason);
        }
        if (session.mediumPlacementAttempt < MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES)
            return false;

        session.mediumPlacements = MediumBoardPhysicalPolicy.rank(session.mediumPlacements);
        for (MediumBoardPhysicalPolicy.PlacementCandidate candidate : session.mediumPlacements) {
            session.mediumRankedAttempts.add(Integer.valueOf(candidate.attempt));
            session.mediumPlacementScores.add(candidate.toCanonical());
        }
        if (session.board.getPlacementConstraints().placementDensityVersion == 3 ||
                session.board.getPlacementConstraints().placementDensityVersion >= 4)
            session.mediumPlacements = prioritizeGroupedPlacementRouteCandidates(session.mediumPlacements,
                session.board.getPlacementConstraints().placementDensityVersion,
                hasRepeatedControlRegions(session.board.getPlacementConstraints()));
        else if (session.board.getPlacementConstraints().placementDensityVersion == 2)
            session.mediumPlacements = prioritizeLegacyPlacementRouteCandidate(session.mediumPlacements);
        if (session.mediumPlacements.isEmpty())
            return finishMediumFailure(session);
        session.mediumRouteStage = 1;
        session.mediumRouteAttempt = 0;
        return false;
    }

    private boolean advanceMediumRouting(Session session, int checkpoint,
            boolean twoLayer) {
        int limit = Math.min(MediumBoardPhysicalPolicy.ROUTING_CANDIDATES,
            session.mediumPlacements.size());
        if (twoLayer && session.mediumOneFaceSuccesses > 0 &&
                !session.board.getPlacementConstraints().requiresTwoLayerRouting)
            limit = Math.min(1, limit);
        if (session.mediumRouteAttempt < limit) {
            int candidateIndex = session.mediumRouteAttempt;
            if (twoLayer && session.mediumOneFaceSuccesses > 0 &&
                    !session.board.getPlacementConstraints().requiresTwoLayerRouting &&
                    session.mediumRouteAttempt == 0) {
                candidateIndex = mediumPlacementIndex(session,
                    session.mediumOneFacePlacementAttempt);
            }
            if (session.board.getPlacementConstraints().placementDensityVersion >= 7 &&
                    hasRepeatedControlRegions(session.board.getPlacementConstraints()))
                candidateIndex = guardedRouteCandidateIndex(session.mediumPlacements, candidateIndex,
                    twoLayer ? session.mediumTwoLayerSuccesses : session.mediumOneFaceSuccesses);
            if(session.board.getPlacementConstraints().placementDensityVersion >= 8) {
                boolean narrowTried=false,narrowSucceeded=false;
                String prefix=(twoLayer?MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER:
                    MediumBoardPhysicalPolicy.P05_ONE_FACE)+"@3=";
                for(String outcome:session.mediumRouteOutcomes)
                    if(outcome.startsWith(prefix)) { narrowTried=true;
                        if(outcome.equals(prefix+"SUCCESS"))narrowSucceeded=true; }
                candidateIndex=narrowRouteCandidateIndex(session.mediumPlacements,
                    session.mediumRouteAttempt,candidateIndex,
                    twoLayer?session.mediumTwoLayerSuccesses:session.mediumOneFaceSuccesses,
                    narrowTried,hasRepeatedControlRegions(session.board.getPlacementConstraints()));
                if(session.board.getPlacementConstraints().placementDensityVersion>=10)
                    candidateIndex=supportRouteCandidateIndex(session.mediumPlacements,session.mediumRouteAttempt,
                        candidateIndex,twoLayer?session.mediumTwoLayerSuccesses:session.mediumOneFaceSuccesses,
                        narrowSucceeded,hasRepeatedControlRegions(session.board.getPlacementConstraints()));
            }
            MediumBoardPhysicalPolicy.PlacementCandidate candidate =
                session.mediumPlacements.get(candidateIndex);
            session.mediumRouteAttempt++;
            if (twoLayer) {
                session.mediumTwoLayerCandidate=candidate;
                session.mediumTwoLayerElapsedMillis=0;
                session.mediumTwoLayerCheckpoint=checkpoint;
                PcbBoardLayout placement=candidate.plan.materialize();
                long started=System.currentTimeMillis();
                session.mediumTwoLayerRoute=PcbLayerRoutingPrototype.begin(session.board,
                    placement,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,session.observer);
                session.mediumTwoLayerElapsedMillis+=System.currentTimeMillis()-started;
                return advanceMediumTwoLayerRoute(session);
            }
            try {
                session.mediumOneFaceAttempts++;
                int variationMode = (int) ((session.seed % 4 + 4) % 4);
                RoutedCandidate routed = routePlan(session.board, session.seed, variationMode,
                    candidate.attempt, session.observer, session.routingSeed,
                    session.statistics, candidate.plan, false);
                if (envelopeFor(session) != null)
                    envelopeFor(session).requireBounds(session.board, routed.layout);
                considerMediumLayout(session, candidate, routed, twoLayer);
                session.mediumRouteOutcomes.add(MediumBoardPhysicalPolicy.P05_ONE_FACE + "@" + candidate.attempt +
                    "=SUCCESS");
            } catch (CandidateRejected failure) {
                session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                    failure.getKind(), checkpoint, session.seed, failure.getMessage(),
                    failure.recovery);
                session.mediumRouteOutcomes.add(routeFailure(false, candidate, failure.getMessage()));
            } catch (SupportedEnvelope.Rejected failure) {
                session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                    PcbRoutingRejectedException.Kind.PLACEMENT, checkpoint, session.seed,
                    failure.getMessage());
                session.mediumRouteOutcomes.add(routeFailure(false, candidate, failure.getMessage()));
            } catch (PcbBoardLayout.RouteQualityRejectedException failure) {
                session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                    PcbRoutingRejectedException.Kind.ROUTING, checkpoint, session.seed,
                    failure.getMessage());
                session.mediumRouteOutcomes.add(routeFailure(false, candidate, failure.getMessage()));
            }
            session.mediumOneFaceSuccesses = countMediumSuccesses(
                session.mediumRouteOutcomes, MediumBoardPhysicalPolicy.P05_ONE_FACE);
            return false;
        }

        if (!twoLayer) {
            session.mediumRouteStage = 2;
            session.mediumRouteAttempt = 0;
            return false;
        }
        if (session.mediumTwoLayerSuccesses > 0 ||
                session.mediumOneFaceSuccesses > 0)
            return finishMediumSuccess(session);
        return finishMediumFailure(session);
    }

    private boolean advanceMediumTwoLayerRoute(Session session) {
        long started=System.currentTimeMillis();
        boolean complete=session.mediumTwoLayerRoute.advanceSlice();
        session.mediumTwoLayerElapsedMillis+=System.currentTimeMillis()-started;
        if(!complete) return false;

        PcbLayerRoutingPrototype.Result result=session.mediumTwoLayerRoute.result();
        MediumBoardPhysicalPolicy.PlacementCandidate candidate=session.mediumTwoLayerCandidate;
        int routeCheckpoint=session.mediumTwoLayerCheckpoint;
        session.mediumTwoLayerRoute=null;
        session.mediumTwoLayerCandidate=null;
        session.mediumTwoLayerCheckpoint=-1;
        session.mediumTwoLayerAttempts++;
        session.statistics.routes+=result.orderings;
        session.statistics.expansions+=result.expansions;
        session.statistics.routeMillis+=session.mediumTwoLayerElapsedMillis;
        session.mediumTwoLayerElapsedMillis=0;
        try {
            RoutedCandidate routed=finishMediumTwoLayerRoute(session.board,session.seed,candidate,result);
            if (envelopeFor(session) != null)
                envelopeFor(session).requireBounds(session.board, routed.layout);
            considerMediumLayout(session, candidate, routed, true);
            session.mediumRouteOutcomes.add(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@" +
                candidate.attempt + "=SUCCESS");
        } catch (CandidateRejected failure) {
            session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                failure.getKind(), routeCheckpoint, session.seed, failure.getMessage(),failure.recovery);
            session.mediumRouteOutcomes.add(routeFailure(true, candidate, failure.getMessage()));
        } catch (SupportedEnvelope.Rejected failure) {
            session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                PcbRoutingRejectedException.Kind.PLACEMENT, routeCheckpoint, session.seed,
                failure.getMessage());
            session.mediumRouteOutcomes.add(routeFailure(true, candidate, failure.getMessage()));
        } catch (PcbBoardLayout.RouteQualityRejectedException failure) {
            session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                PcbRoutingRejectedException.Kind.ROUTING, routeCheckpoint, session.seed,
                failure.getMessage());
            session.mediumRouteOutcomes.add(routeFailure(true, candidate, failure.getMessage()));
        }
        session.mediumTwoLayerSuccesses = countMediumSuccesses(
            session.mediumRouteOutcomes, MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER);
        return false;
    }

    private static int mediumPlacementIndex(Session session, int placementAttempt) {
        for (int index = 0; index < session.mediumPlacements.size(); index++)
            if (session.mediumPlacements.get(index).attempt == placementAttempt)
                return index;
        return 0;
    }

    /** Keep one best-ranked legacy-algorithm fallback inside the existing private v2 route budget. */
    private static Vector<MediumBoardPhysicalPolicy.PlacementCandidate> prioritizeLegacyPlacementRouteCandidate(
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> ranked) {
        int routeLimit = Math.min(MediumBoardPhysicalPolicy.ROUTING_CANDIDATES, ranked.size());
        int roomyAttempt = PcbPlacementPlanner.OUTLINE_CANDIDATES - 2;
        MediumBoardPhysicalPolicy.PlacementCandidate bestRoomy = null;
        for (MediumBoardPhysicalPolicy.PlacementCandidate candidate : ranked)
            if (candidate.attempt >= roomyAttempt) { bestRoomy = candidate; break; }
        if (routeLimit < 2 || bestRoomy == null) return ranked;

        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> selected =
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        for (MediumBoardPhysicalPolicy.PlacementCandidate candidate : ranked) {
            if (candidate.attempt < roomyAttempt && selected.size() < routeLimit - 1)
                selected.add(candidate);
        }
        if (selected.isEmpty()) return ranked;
        selected.add(bestRoomy);

        // Preserve the sorted tail and full metrics; only the bounded route prefix changes.
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> result =
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>(ranked.size());
        for (MediumBoardPhysicalPolicy.PlacementCandidate candidate : selected)
            result.add(candidate);
        for (MediumBoardPhysicalPolicy.PlacementCandidate candidate : ranked) {
            boolean alreadySelected = false;
            for (MediumBoardPhysicalPolicy.PlacementCandidate chosen : selected)
                if (chosen.attempt == candidate.attempt) { alreadySelected = true; break; }
            if (!alreadySelected) result.add(candidate);
        }
        return result;
    }

    private static boolean compactPlacementInterpretation(PcbPlacementConstraints constraints) {
        return constraints.placementDensityVersion == 2 || constraints.placementDensityVersion == 3 ||
            constraints.placementDensityVersion >= 4;
    }

    /** V3 keeps grouped/compact/roomy; v4 keeps tight/retained-compact/proven-roomy.
     * Every interpretation retains the same maximum three routed candidates. */
    static Vector<MediumBoardPhysicalPolicy.PlacementCandidate>
            prioritizeGroupedPlacementRouteCandidates(Vector<MediumBoardPhysicalPolicy.PlacementCandidate> ranked, int version, boolean repeatedControl) {
        boolean dense=version>=4;
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> selected=
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        for(int category=0;category<3;category++)
            for(MediumBoardPhysicalPolicy.PlacementCandidate candidate:ranked) {
                int candidateCategory=candidate.attempt/2;
                if(dense && candidate.attempt==4) candidateCategory=-1;
                if(candidateCategory==category) {selected.add(candidate);break;}
            }
        if (version >= 5 && repeatedControl) {
            // Repeated control groups need the evidenced moderate profile;
            // single-group boards retain their tighter existing alternative.
            for(MediumBoardPhysicalPolicy.PlacementCandidate candidate:ranked)
                if(candidate.attempt==4) {
                    boolean replaced=false;
                    for(int i=0;i<selected.size();i++) if(selected.get(i).attempt<2) {
                        selected.set(i,candidate);replaced=true;break;
                    }
                    if(!replaced && selected.size()<MediumBoardPhysicalPolicy.ROUTING_CANDIDATES)
                        selected.insertElementAt(candidate,0);
                    break;
                }
        }
        if (version >= 6 && !repeatedControl) {
            // Preliminary quality can prefer a larger final crop. Compare both
            // existing tight alternatives, retaining the same roomy fallback.
            selected.clear();
            for(int attempt:new int[]{0,1,5})
                for(MediumBoardPhysicalPolicy.PlacementCandidate candidate:ranked)
                    if(candidate.attempt==attempt) {selected.add(candidate);break;}
        }
        if (version >= 7 && repeatedControl) {
            // Protect the unchanged moderate/compact/roomy route chain from
            // preliminary scoring. Exploration can use slot three only after
            // this routing policy has already produced a validated layout.
            selected.clear();
            for (int attempt : new int[]{4,2,5})
                for (MediumBoardPhysicalPolicy.PlacementCandidate candidate : ranked)
                    if (candidate.attempt == attempt) { selected.add(candidate); break; }
        }
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> result=
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>(selected);
        for(MediumBoardPhysicalPolicy.PlacementCandidate candidate:ranked)
            if(!selected.contains(candidate))result.add(candidate);
        return result;
    }

    /** Policy-local success never substitutes for a missing required P07 route. */
    static int guardedRouteCandidateIndex(
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> candidates,
            int routeIndex, int policySuccesses) {
        // Missing retained placements spend the slot on remaining fallbacks.
        // A complete 4/2/5 chain is required before displacing roomy5.
        if (routeIndex != 2 || policySuccesses <= 0 || candidates.size() <= 3 ||
                candidates.get(0).attempt != 4 || candidates.get(1).attempt != 2 ||
                candidates.get(2).attempt != 5)
            return routeIndex;
        return 3; // Best remaining preliminary rank, with both routed owners retained.
    }

    /** Spend routing room on narrower placement only after this policy has a legal route. */
    static int narrowRouteCandidateIndex(Vector<MediumBoardPhysicalPolicy.PlacementCandidate> candidates,
            int routeIndex,int fallback,int policySuccesses,boolean narrowTried,boolean repeated) {
        int first=repeated?4:0,second=repeated?2:1;
        if(policySuccesses<=0 || candidates.size()<4 || candidates.get(0).attempt!=first ||
                candidates.get(1).attempt!=second || candidates.get(2).attempt!=5)return fallback;
        int narrow=-1,roomier=-1;
        for(int i=3;i<candidates.size();i++) {
            if(candidates.get(i).attempt==3)narrow=i;
            if(candidates.get(i).attempt==0)roomier=i;
        }
        if(narrow<0)return fallback;
        if(routeIndex==1 && repeated)return narrow;
        if(routeIndex==2) {
            if(!narrowTried)return narrow;
            // Candidate0 supplies more routing room; compact2 remains available
            // if that alternative failed placement. Never request candidate3 twice.
            return roomier>=0?roomier:1;
        }
        return fallback;
    }

    /** Explore local support only with the original narrow route already legal in this policy. */
    static int supportRouteCandidateIndex(Vector<MediumBoardPhysicalPolicy.PlacementCandidate> candidates,
            int routeIndex,int fallback,int policySuccesses,boolean narrowSucceeded,boolean repeated) {
        if(routeIndex!=2 || !repeated || !narrowSucceeded || policySuccesses<=0 || candidates.size()<4 ||
                candidates.get(0).attempt!=4 || candidates.get(1).attempt!=2 || candidates.get(2).attempt!=5)
            return fallback;
        int support=-1,narrow=-1;
        for(int i=3;i<candidates.size();i++) {
            if(candidates.get(i).attempt==1)support=i;
            if(candidates.get(i).attempt==3)narrow=i;
        }
        if(support>=0 && narrow>=0)return support;
        return fallback;
    }

    static boolean hasRepeatedControlRegions(PcbPlacementConstraints constraints) {
        String first=null;
        for(PcbPlacementConstraints.Part part:constraints.getParts())
            if(part.regionId.startsWith("channel-")) {
                if(first==null)first=part.regionId;
                else if(!first.equals(part.regionId))return true;
            }
        return false;
    }

    private static String routeFailure(boolean twoLayer,
            MediumBoardPhysicalPolicy.PlacementCandidate candidate, String detail) {
        return (twoLayer ? MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER :
            MediumBoardPhysicalPolicy.P05_ONE_FACE) + "@" + candidate.attempt + "=REJECTED:" + detail;
    }

    private static int countMediumSuccesses(Vector<String> outcomes, String policy) {
        int result = 0;
        String prefix = policy + "@";
        for (String outcome : outcomes)
            if (outcome.startsWith(prefix) && outcome.endsWith("=SUCCESS")) result++;
        return result;
    }

    private void considerMediumLayout(Session session,
            MediumBoardPhysicalPolicy.PlacementCandidate candidate,
            RoutedCandidate routed, boolean twoLayer) {
        PcbBoardLayout layout = routed.layout;
        if (layout == null) throw new IllegalArgumentException("Missing medium routed layout");
        double score = layout.getRouteQualityScore(session.board);
        Rectangle bounds = layout.getBoardOutline();
        long area = (long) bounds.width * bounds.height;
        int majorSide = Math.max(bounds.width, bounds.height);
        boolean preferCompact = compactPlacementInterpretation(session.board.getPlacementConstraints());
        int placementVersion=session.board.getPlacementConstraints().placementDensityVersion;
        boolean preferDensePacking = placementVersion >= 4 && placementVersion < 8;
        if (twoLayer) {
            if (mediumRouteCandidateBetter(score, area, majorSide, candidate,
                    session.mediumTwoLayerLayout, session.mediumTwoLayerQuality,
                    session.mediumTwoLayerArea, session.mediumTwoLayerPlacementAttempt,
                    session.mediumTwoLayerPlacementScore, preferCompact, preferDensePacking)) {
                session.mediumTwoLayerLayout = layout;
                session.mediumTwoLayerPlan = routed.plan;
                session.mediumTwoLayerQuality = score;
                session.mediumTwoLayerArea = area;
                session.mediumTwoLayerPlacementAttempt = candidate.attempt;
                session.mediumTwoLayerPlacementScore = candidate.cheapScore;
            }
        } else if (mediumRouteCandidateBetter(score, area, majorSide, candidate,
                session.mediumOneFaceLayout, session.mediumOneFaceQuality,
                session.mediumOneFaceArea, session.mediumOneFacePlacementAttempt,
                session.mediumOneFacePlacementScore, preferCompact, preferDensePacking)) {
            session.mediumOneFaceLayout = layout;
            session.mediumOneFacePlan = routed.plan;
            session.mediumOneFaceQuality = score;
            session.mediumOneFaceArea = area;
            session.mediumOneFacePlacementAttempt = candidate.attempt;
            session.mediumOneFacePlacementScore = candidate.cheapScore;
        }
    }

    private static boolean mediumRouteCandidateBetter(double quality, long area, int majorSide,
            MediumBoardPhysicalPolicy.PlacementCandidate candidate,
            PcbBoardLayout current, double currentQuality, long currentArea,
            int currentAttempt, long currentPlacementScore, boolean preferCompact,
            boolean preferDensePacking) {
        if (current == null) return true;
        if (preferDensePacking) {
            Rectangle currentBounds = current.getBoardOutline();
            int currentMajorSide = Math.max(currentBounds.width, currentBounds.height);
            // Area dominates; a small shape term rejects thin-ribbon savings.
            // This compares only already legal routes, without another request.
            long packingCost=area*9L+(long)majorSide*majorSide;
            long currentPackingCost=currentArea*9L+(long)currentMajorSide*currentMajorSide;
            if (packingCost != currentPackingCost) return packingCost < currentPackingCost;
        }
        // Every candidate already passed the physical and route-quality guards.
        // New compact boards prioritize occupied outline area among those legal choices.
        if (preferCompact && area != currentArea) return area < currentArea;
        if (quality < currentQuality) return true;
        if (quality > currentQuality) return false;
        if (area < currentArea) return true;
        if (area > currentArea) return false;
        if (candidate.cheapScore < currentPlacementScore) return true;
        if (candidate.cheapScore > currentPlacementScore) return false;
        return candidate.attempt < currentAttempt;
    }

    private static long routeQualityMilli(double quality) {
        if (Double.isNaN(quality) || Double.isInfinite(quality) || quality < 0)
            return -1;
        double scaled = quality * 1000.0;
        if (scaled >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.round(scaled);
    }

    private boolean selectMediumLayout(Session session) {
        if (session.mediumOneFaceLayout == null && session.mediumTwoLayerLayout == null)
            return false;
        boolean chooseTwoLayer = session.mediumOneFaceLayout == null ||
            (session.mediumTwoLayerLayout != null &&
                MediumBoardPhysicalPolicy.materiallyBetterTwoLayer(
                    session.mediumTwoLayerQuality, session.mediumOneFaceQuality));
        if (compactPlacementInterpretation(session.board.getPlacementConstraints()) &&
                session.mediumOneFaceLayout != null && session.mediumTwoLayerLayout != null &&
                session.mediumOneFaceArea != session.mediumTwoLayerArea)
            chooseTwoLayer = session.mediumTwoLayerArea < session.mediumOneFaceArea;
        // A declared owner requirement takes precedence over optional area/quality selection.
        if (session.board.getPlacementConstraints().requiresTwoLayerRouting) {
            if (session.mediumTwoLayerLayout == null) return false;
            chooseTwoLayer = true;
        }
        if (chooseTwoLayer) {
            session.bestLayout = session.mediumTwoLayerLayout;
            session.bestPlan = session.mediumTwoLayerPlan;
            session.bestArea = session.mediumTwoLayerArea;
            session.bestScore = session.mediumTwoLayerQuality;
            session.mediumSelectedRoute = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER;
            session.mediumSelectedPlacementAttempt = session.mediumTwoLayerPlacementAttempt;
            session.mediumSelectedPlacementScore = session.mediumTwoLayerPlacementScore;
            session.mediumSelectedRouteQualityMilli = routeQualityMilli(
                session.mediumTwoLayerQuality);
        } else {
            session.bestLayout = session.mediumOneFaceLayout;
            session.bestPlan = session.mediumOneFacePlan;
            session.bestArea = session.mediumOneFaceArea;
            session.bestScore = session.mediumOneFaceQuality;
            session.mediumSelectedRoute = MediumBoardPhysicalPolicy.P05_ONE_FACE;
            session.mediumSelectedPlacementAttempt = session.mediumOneFacePlacementAttempt;
            session.mediumSelectedPlacementScore = session.mediumOneFacePlacementScore;
            session.mediumSelectedRouteQualityMilli = routeQualityMilli(
                session.mediumOneFaceQuality);
        }
        return session.bestLayout != null;
    }

    private static int accessEdgeMargin(PcbPlacementConstraints constraints,
            Vector<PcbFootprint> footprints, Rectangle occupied, int edgeMargin) {
        for (PcbFootprint footprint:footprints) {
            PcbPlacementConstraints.Part demand=constraints.get(
                footprint.getPlacement().getComponentId());
            Rectangle access=PcbPlacementPlanner.envelope(footprint,demand.accessMargin);
            edgeMargin=Math.max(edgeMargin,occupied.x-access.x+10);
            edgeMargin=Math.max(edgeMargin,occupied.y-access.y+10);
            edgeMargin=Math.max(edgeMargin,access.x+access.width-occupied.x-occupied.width+10);
            edgeMargin=Math.max(edgeMargin,access.y+access.height-occupied.y-occupied.height+10);
        }
        return edgeMargin;
    }

    /** Preserve the existing drawing-grid phase while removing spare border. */
    static int finalEdgeTrimTarget(int oldMargin, int requiredMargin) {
        if (oldMargin < FINAL_EDGE_MARGIN || requiredMargin < FINAL_EDGE_MARGIN)
            throw new IllegalArgumentException("Invalid final PCB edge margin");
        if (requiredMargin >= oldMargin) return oldMargin;
        return oldMargin - ((oldMargin - requiredMargin) / GRID) * GRID;
    }

    /** Selection is complete before this physical border-only contraction. */
    private void trimSelectedMediumBorder(Session session) {
        PcbPlacementConstraints constraints=session.board.getPlacementConstraints();
        if (constraints.placementDensityVersion != 14) return;
        PcbBoardLayout layout=session.bestLayout;
        Rectangle outline=layout.getBoardOutline(),occupied=layout.getOccupiedContentBounds();
        long widthDifference=(long)outline.width-occupied.width;
        long heightDifference=(long)outline.height-occupied.height;
        if (widthDifference < 0 || widthDifference != heightDifference ||
                (widthDifference & 1L) != 0 ||
                (long)occupied.x-outline.x != widthDifference/2L ||
                (long)occupied.y-outline.y != widthDifference/2L)
            throw new IllegalStateException("Selected PCB has no uniform crop margin");
        int oldMargin=PcbCoordinateSystem.checkedInt(widthDifference/2L);
        PcbPlacementPlanner.Plan plan=null;
        for (MediumBoardPhysicalPolicy.PlacementCandidate candidate:session.mediumPlacements)
            if (candidate.attempt == session.mediumSelectedPlacementAttempt) {
                if (plan != null) throw new IllegalStateException("Duplicate selected PCB placement");
                plan=candidate.plan;
            }
        if (plan == null) throw new IllegalStateException("Missing selected PCB placement");
        Vector<PcbFootprint> footprints=translatedPlanFootprints(plan,layout,0,0);
        int requiredMargin=accessEdgeMargin(constraints,footprints,occupied,FINAL_EDGE_MARGIN);
        int target=finalEdgeTrimTarget(oldMargin,requiredMargin);
        if (target == oldMargin) return;
        int validated=validatedCompactEdgeMargin(session.board,constraints,plan,layout,
            outline,occupied,outline.x,outline.y,target);
        if (validated == oldMargin) return;
        if (validated != target)
            throw new IllegalStateException("Selected PCB crop exceeded its valid outline");
        PcbBoardLayout candidate=layout.copyForCompaction();
        try {
            candidate.compactToContent(outline.x,outline.y,validated);
            candidate.positionPartsTrayDisjointFromBoard();
            candidate.ensureCanvasContainsBoard();
            PcbPlacementPlanner.validate(session.board,constraints,candidate.getBoardOutline(),
                translatedPlanFootprints(plan,candidate,0,0));
            candidate.validateGeometry(session.board);
            candidate.validateRoutingGeometry(session.board);
            candidate.validateRouteQuality();
            PcbTraceClearance.validate(candidate.getTraces());
            if (MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(session.mediumSelectedRoute))
                new PcbTwoLayerRules(session.board,candidate).validate(candidate);
        } catch (PcbPlacementPlanner.Rejected rejected) {
            return; // Original selected geometry remains untouched.
        } catch (PcbBoardLayout.RouteQualityRejectedException rejected) {
            return; // Preserve the admitted route when its unchanged quality guard rejects.
        }
        // Unexpected errors and generation cancellation propagate; no live state was mutated.
        session.bestLayout=candidate;
        Rectangle trimmed=candidate.getBoardOutline();
        session.bestArea=(long)trimmed.width*trimmed.height;
        session.bestScore=candidate.getRouteQualityScore(session.board);
        session.mediumSelectedRouteQualityMilli=routeQualityMilli(session.bestScore);
        if (layout == session.mediumTwoLayerLayout) {
            session.mediumTwoLayerLayout=candidate;
            session.mediumTwoLayerArea=session.bestArea;
            session.mediumTwoLayerQuality=session.bestScore;
        } else {
            session.mediumOneFaceLayout=candidate;
            session.mediumOneFaceArea=session.bestArea;
            session.mediumOneFaceQuality=session.bestScore;
        }
    }

    private boolean finishMediumSuccess(Session session) {
        if (!selectMediumLayout(session))
            return finishMediumFailure(session);
        trimSelectedMediumBorder(session);
        if (beginPackingProbe(session)) return false;
        return completeMediumSuccess(session);
    }

    private boolean completeMediumSuccess(Session session) {
        session.bestLayout.setGenerationStatistics(session.statistics.placements,
            session.statistics.routes, session.statistics.expansions,
            session.statistics.routeMillis, session.seed, session.routingSeed);
        session.mediumPolicyResult = new MediumBoardPhysicalPolicy.Result(
            session.bestLayout, mediumStatistics(session, "SUCCESS"), null);
        session.complete = true;
        return true;
    }

    private boolean finishMediumFailure(Session session) {
        if (session.lastFailure == null)
            throw new IllegalStateException("Medium policy stopped without a result or rejection");
        session.mediumPolicyResult = new MediumBoardPhysicalPolicy.Result(null,
            mediumStatistics(session, "REJECTED"), session.lastFailure.getMessage());
        throw PcbRoutingRejectedException.exhausted(session.seed,
            Math.max(1, session.attempt), session.lastFailure);
    }

    private MediumBoardPhysicalPolicy.Statistics mediumStatistics(Session session,
            String outcome) {
        return new MediumBoardPhysicalPolicy.Statistics(outcome,
            session.mediumSelectedRoute, session.mediumPlacementAttempt,
            session.mediumPlacementRejections,
            session.mediumOneFaceAttempts + session.mediumTwoLayerAttempts,
            session.mediumOneFaceAttempts, session.mediumOneFaceSuccesses,
            session.mediumTwoLayerAttempts, session.mediumTwoLayerSuccesses,
            session.mediumSelectedPlacementAttempt, session.mediumRankedAttempts,
            session.mediumPlacementScores, session.mediumRouteOutcomes,
            session.mediumPlacementEvaluations, session.statistics.routes,
            session.statistics.expansions,
            session.mediumSelectedPlacementScore,
            session.mediumSelectedRouteQualityMilli, session.packingProbeReceipt);
    }

    private SupportedEnvelope envelopeFor(Session session) { return session.envelope; }

    private RoutedCandidate finishMediumTwoLayerRoute(TroubleshootBoard board,long seed,
            MediumBoardPhysicalPolicy.PlacementCandidate candidate,
            PcbLayerRoutingPrototype.Result result) {
        if (!result.accepted())
            throw new CandidateRejected(PcbRoutingRejectedException.Kind.ROUTING,
                "P07_FULLER_TWO_LAYER:" + result.outcome);
        return finishRoutedPlan(board, (int) ((seed % 4 + 4) % 4), candidate.plan,
            result.layout, null,
            compactPlacementInterpretation(board.getPlacementConstraints()));
    }

    private static final class RoutedCandidate {
        final PcbPlacementPlanner.Plan plan;
        final PcbBoardLayout layout;
        RoutedCandidate(PcbPlacementPlanner.Plan plan, PcbBoardLayout layout) {
            this.plan = plan;
            this.layout = layout;
        }
    }

    private RoutedCandidate generateAttempt(TroubleshootBoard board, long seed,
            int variationMode, int attempt, AttemptObserver observer,long routingSeed,GenerationStatistics statistics) {
        PcbPlacementPlanner.Plan plan;
        try {
            plan = new PcbPlacementPlanner(footprintRegistry).plan(board,
                board.getPlacementConstraints(), seed, attempt);
        } catch (PcbPlacementPlanner.Rejected failure) {
            throw reject(PcbRoutingRejectedException.Kind.PLACEMENT, failure.reason);
        }
        RoutedCandidate original = routePlan(board, seed, variationMode, attempt, observer,
            routingSeed, statistics, plan, false);
        PcbPlacementPlanner.Plan compact = PcbPlacementCompactor.compactStrips(
            board, board.getPlacementConstraints(), plan);
        if (compact == plan || PcbPlacementCompactor.samePlacement(plan, compact))
            return original;
        // Keep every successful original candidate. A smaller layout is accepted
        // only after its own real routing, access and geometry checks succeed.
        // One bounded compact routing request retains the successful original on exhaustion.
        try {
            RoutedCandidate result = routePlan(board, seed, variationMode, attempt, observer,
                routingSeed, statistics, compact, true);
            Rectangle a=original.layout.getBoardOutline(), b=result.layout.getBoardOutline();
            return (long)b.width*b.height < (long)a.width*a.height ?
                result : original;
        } catch (CandidateRejected unavailable) { return original; }
        catch (PcbBoardLayout.RouteQualityRejectedException unavailable) {
            return original;
        }
    }
    private RoutedCandidate routePlan(TroubleshootBoard board, long seed, int variationMode,
            int attempt, AttemptObserver observer, long routingSeed, GenerationStatistics statistics,
            PcbPlacementPlanner.Plan plan, boolean compactProbe) {
        Rectangle outline = plan.outline;
        PcbBoardLayout layout = null;
        // One scheduler owns all ordering and rip-up budgets for this placement.
        layout=plan.materialize();
        long routingStarted=System.currentTimeMillis();
        try { PcbNetRouter.route(layout,board,outline,attempt,observer,true,routingSeed); }
        catch(PcbNetRouter.Rejected failure) {
            if (compactProbe) TaskPackingObservation.emit("COMPACT_ROUTER_REJECTED",
                "P05_ROUTE_SEARCH", plan, seed, routingSeed, null,
                MediumBoardPhysicalPolicy.P05_ONE_FACE, failure, "ROUTING",
                failure.reason.name(), failure.blockedNet, failure.statistics);
            throw new CandidateRejected(PcbRoutingRejectedException.Kind.ROUTING,
                failure.getMessage(),failure.statistics);
        } finally {
            PcbRoutingWork.Statistics routed=layout.getRoutingRecoveryStatistics();
            if(routed!=null) statistics.routes+=routed.orderingPasses;
            statistics.expansions+=layout.getRoutingExpansions();
            statistics.routeMillis+=System.currentTimeMillis()-routingStarted;
        }
        PcbRoutingWork.Statistics recovery=layout.getRoutingRecoveryStatistics();
        return finishRoutedPlan(board, variationMode, plan, layout, recovery, true);
    }

    private RoutedCandidate finishRoutedPlan(TroubleshootBoard board, int variationMode,
            PcbPlacementPlanner.Plan plan, PcbBoardLayout layout,
            PcbRoutingWork.Statistics recovery, boolean compactRouted) {
        Rectangle outline = plan.outline;
        if (compactRouted) {
            PcbPlacementCompactor.Routed compact = PcbPlacementCompactor.compactRouted(
                board, board.getPlacementConstraints(), plan, layout);
            plan = compact.plan;
            layout = compact.layout;
            outline = plan.outline;
        }
        layout.setRoutingRecoveryStatistics(recovery);
        placeSilkscreen(layout, board, outline);
        layout.validateGeometry(board);
        int edgeMargin=FINAL_EDGE_MARGIN;
        for(PcbPlacementConstraints.Part demand:board.getPlacementConstraints().getParts())
            edgeMargin=Math.max(edgeMargin,demand.accessMargin+30);
        // Cropping visible ink must also retain unpainted escape/access corridors.
        // Otherwise valid layouts with a low LED or a left-facing resistor are
        // rejected after routing, biasing every root toward the same arrangement.
        Rectangle occupied=layout.getOccupiedContentBounds();
        edgeMargin=accessEdgeMargin(board.getPlacementConstraints(),
            plan.footprints,occupied,edgeMargin);
        int preferredBoardX = FINAL_BOARD_X + variationMode * 10;
        int preferredBoardY = FINAL_BOARD_Y + (variationMode % 2) * 10;
        int compactWidth = compactExtent(occupied.width, edgeMargin);
        int compactHeight = compactExtent(occupied.height, edgeMargin);
        // Medium routed candidates can consume most of the planner canvas.
        // Keep the established small-board origin when it fits, then shift the
        // compacted board inward deterministically before moving the tray.
        boolean mediumCanvas = MediumBoardPhysicalPolicy.selected(
            board.getPlacementConstraints());
        int boardX = mediumCanvas ? fitCanvasOrigin(preferredBoardX, compactWidth,
            layout.getWidth()) : preferredBoardX;
        int boardY = mediumCanvas ? fitCanvasOrigin(preferredBoardY, compactHeight,
            layout.getHeight()) : preferredBoardY;
        // Validate the compact destination before mutating the routed layout.
        // Cropping can move a declared escape endpoint onto the wrong side of
        // the access grid even though the routed placement was valid.  If that
        // happens, retain the previously validated planner outline as the
        // deterministic upper bound for the crop margin.
        int compactEdgeMargin = validatedCompactEdgeMargin(board,
            board.getPlacementConstraints(), plan, layout, outline, occupied,
            boardX, boardY, edgeMargin);
        if (mediumCanvas && compactEdgeMargin != edgeMargin) {
            compactWidth = compactExtent(occupied.width, compactEdgeMargin);
            compactHeight = compactExtent(occupied.height, compactEdgeMargin);
            boardX = fitCanvasOrigin(preferredBoardX, compactWidth, layout.getWidth());
            boardY = fitCanvasOrigin(preferredBoardY, compactHeight, layout.getHeight());
        }
        layout.compactToContent(boardX, boardY, compactEdgeMargin);
        layout.positionPartsTrayDisjointFromBoard();
        // Tray placement may return early when the tray fits, even if the crop
        // extends past the old drawing canvas. The PCB geometry stays exact.
        if (mediumCanvas && (board.getPlacementConstraints().placementDensityVersion == 13 ||
                board.getPlacementConstraints().placementDensityVersion == 14))
            layout.ensureCanvasContainsBoard();
        Vector<PcbFootprint> finalFootprints = translatedPlanFootprints(plan, layout, 0, 0);
        try { PcbPlacementPlanner.validate(board,board.getPlacementConstraints(),layout.getBoardOutline(),finalFootprints); }
        catch(PcbPlacementPlanner.Rejected failure) { throw reject(PcbRoutingRejectedException.Kind.PLACEMENT,failure.reason); }
        layout.validateGeometry(board);
        return new RoutedCandidate(plan, layout);
    }

    private static int validatedCompactEdgeMargin(TroubleshootBoard board,
            PcbPlacementConstraints constraints, PcbPlacementPlanner.Plan plan,
            PcbBoardLayout layout, Rectangle validatedOutline, Rectangle occupied,
            int boardX, int boardY, int edgeMargin) {
        int candidateMargin = edgeMargin;
        try {
            validateCompactedPlacement(board, constraints, plan, layout, occupied,
                boardX, boardY, candidateMargin);
            return candidateMargin;
        } catch (PcbPlacementPlanner.Rejected compactRejected) {
            candidateMargin = edgeMarginIncludingOutline(occupied, validatedOutline,
                edgeMargin);
            try {
                validateCompactedPlacement(board, constraints, plan, layout, occupied,
                    boardX, boardY, candidateMargin);
                return candidateMargin;
            } catch (PcbPlacementPlanner.Rejected preservedRejected) {
                throw reject(PcbRoutingRejectedException.Kind.PLACEMENT,
                    preservedRejected.reason);
            }
        }
    }

    private static void validateCompactedPlacement(TroubleshootBoard board,
            PcbPlacementConstraints constraints, PcbPlacementPlanner.Plan plan,
            PcbBoardLayout layout, Rectangle occupied, int boardX, int boardY,
            int edgeMargin) {
        int dx = PcbCoordinateSystem.checkedInt((long) boardX + edgeMargin - occupied.x);
        int dy = PcbCoordinateSystem.checkedInt((long) boardY + edgeMargin - occupied.y);
        Rectangle compactOutline = new Rectangle(boardX, boardY,
            compactExtent(occupied.width, edgeMargin),
            compactExtent(occupied.height, edgeMargin));
        Vector<PcbFootprint> compactFootprints = translatedPlanFootprints(plan, layout,
            dx, dy);
        PcbPlacementPlanner.validate(board, constraints, compactOutline, compactFootprints);
    }

    private static Vector<PcbFootprint> translatedPlanFootprints(
            PcbPlacementPlanner.Plan plan, PcbBoardLayout layout, int dx, int dy) {
        Vector<PcbFootprint> result = new Vector<PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints) {
            PcbComponentPlacement position = layout.getComponent(
                footprint.getPlacement().getComponentId());
            int x = PcbCoordinateSystem.checkedAddBoardCoordinate(position.getX(), dx);
            int y = PcbCoordinateSystem.checkedAddBoardCoordinate(position.getY(), dy);
            result.add(footprint.translated(x, y));
        }
        return result;
    }

    private static int edgeMarginIncludingOutline(Rectangle occupied,
            Rectangle validatedOutline, int currentMargin) {
        if (validatedOutline == null)
            throw new IllegalArgumentException("Missing validated PCB outline");
        long margin = currentMargin;
        margin = Math.max(margin, (long) occupied.x - validatedOutline.x);
        margin = Math.max(margin, (long) occupied.y - validatedOutline.y);
        margin = Math.max(margin, (long) validatedOutline.x + validatedOutline.width -
            occupied.x - occupied.width);
        margin = Math.max(margin, (long) validatedOutline.y + validatedOutline.height -
            occupied.y - occupied.height);
        if (margin < 0 || margin > Integer.MAX_VALUE)
            throw new IllegalArgumentException("PCB compact margin is out of range: " + margin);
        return (int) margin;
    }

    private static int compactExtent(int contentExtent, int edgeMargin) {
        long extent = (long) contentExtent + edgeMargin * 2L;
        if (contentExtent <= 0 || edgeMargin < 0 || extent > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid PCB compacting extent");
        return (int) extent;
    }

    private static int fitCanvasOrigin(int preferred, int extent, int canvas) {
        if (extent <= 0 || canvas <= 0)
            throw new IllegalArgumentException("Invalid PCB compacting envelope");
        if ((long) preferred + extent <= canvas)
            return preferred;
        return Math.max(0, canvas - extent);
    }

    private static final class GenerationStatistics { int placements,routes,expansions;long routeMillis; }

    private void placeSilkscreen(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline) {
        if (layout.isSealed() || !layout.getSilkscreenLabels().isEmpty() ||
                layout.getPackingProbeReceipt() != null)
            throw new IllegalStateException("Silkscreen requires a fresh physical candidate");
        // Plan the ordinary ink before checking leads so clearance cannot send a
        // reference into remote planner space and change the winning board crop.
        PcbBoardLayout planning = layout.copyForCompaction();
        placeInitialSilkscreen(planning, board, outline);
        Rectangle envelope = planning.getOccupiedContentBounds();
        Rectangle physical = layout.getOccupiedContentBounds();
        Vector<PcbSilkscreenLabel> labels = planning.getSilkscreenLabels();
        Collections.sort(labels, new java.util.Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel first, PcbSilkscreenLabel second) {
                return first.getId().compareTo(second.getId());
            }
        });
        for (int index = 0; index < labels.size(); index++) {
            PcbSilkscreenLabel label = labels.get(index);
            if (!overlapsConnectedLead(layout, label.getBounds())) continue;
            Rectangle selected = reflowLabel(layout, board, outline, physical,
                envelope, labels, index);
            labels.set(index, new PcbSilkscreenLabel(label.getId(), label.getText(),
                selected, label.getFontSize(), label.isBold(), label.getTargetPadId()));
        }
        // Validate the entire plan before adding any ink to the actual owner.
        for (int index = 0; index < labels.size(); index++)
            if (!isPlannedLabelPositionFree(layout, board, outline, physical,
                    envelope, labels, index, labels.get(index).getBounds()))
                throw new IllegalStateException("Invalid fixed-envelope silkscreen plan");
        for (PcbSilkscreenLabel label : labels)
            addLabel(layout, board, outline, label.getId(), label.getText(),
                label.getBounds(), label.getFontSize(), label.isBold(), label.getTargetPadId());
        if (!envelope.equals(layout.getOccupiedContentBounds()))
            throw new IllegalStateException("Silkscreen changed the planned content envelope");
    }

    private Rectangle reflowLabel(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Rectangle physical, Rectangle envelope,
            Vector<PcbSilkscreenLabel> labels, int index) {
        PcbSilkscreenLabel label = labels.get(index);
        Rectangle original = label.getBounds();
        Vector<Rectangle> candidates = new Vector<Rectangle>();
        candidates.add(original);
        for (int distance = GRID; distance <= GRID * 4; distance += GRID) {
            int[][] offsets = {{-distance,0},{distance,0},{0,-distance},{0,distance},
                {-distance,-distance},{distance,-distance},
                {-distance,distance},{distance,distance}};
            for (int[] offset : offsets)
                candidates.add(new Rectangle(original.x + offset[0], original.y + offset[1],
                    original.width, original.height));
        }
        if (label.getId().startsWith("component:")) {
            PcbComponentPlacement component = layout.getComponent(
                label.getId().substring("component:".length()));
            candidates.addAll(getReferenceCandidates(component, original.width, original.height));
        }
        for (Rectangle candidate : candidates)
            if (isPlannedLabelPositionFree(layout, board, outline, physical,
                    envelope, labels, index, candidate)) return candidate;
        // Retain the existing grid alignment, bounded by the original content.
        int firstX = outline.x + 4 +
            (Math.max(0, envelope.x - outline.x - 4) + GRID - 1) / GRID * GRID;
        int firstY = outline.y + 4 +
            (Math.max(0, envelope.y - outline.y - 4) + GRID - 1) / GRID * GRID;
        for (int y = firstY; y + original.height <= envelope.y + envelope.height; y += GRID)
            for (int x = firstX; x + original.width <= envelope.x + envelope.width; x += GRID) {
                Rectangle candidate = new Rectangle(x, y, original.width, original.height);
                if (isPlannedLabelPositionFree(layout, board, outline, physical,
                        envelope, labels, index, candidate)) return candidate;
            }
        throw reject(PcbRoutingRejectedException.Kind.PLACEMENT,
            "Unable to clear PCB silkscreen leads within the planned content envelope");
    }

    private boolean isPlannedLabelPositionFree(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Rectangle physical, Rectangle envelope,
            Vector<PcbSilkscreenLabel> labels, int index, Rectangle candidate) {
        if (!isLabelPositionFree(layout, board, outline, candidate)) return false;
        Rectangle occupied = physical.union(candidate);
        for (int other = 0; other < labels.size(); other++) {
            if (other == index) continue;
            Rectangle bounds = labels.get(other).getBounds();
            if (candidate.intersects(bounds)) return false;
            occupied = occupied.union(bounds);
        }
        return envelope.equals(occupied);
    }

    private boolean overlapsConnectedLead(PcbBoardLayout layout, Rectangle bounds) {
        for (PcbComponentPlacement component : layout.getComponents()) {
            int terminalCount = component.getPhysicalGeometry().getTerminals().size();
            for (int index = 0; index < terminalCount; index++)
                if (bounds.intersects(component.getLeadBounds(index))) return true;
        }
        return false;
    }

    private void placeInitialSilkscreen(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline) {
        String fallbackTitle;
        if (board.getId().equals("RB15_CONTROL_BOARD"))
            fallbackTitle = "TSJ CONTROL BOARD";
        else if (board.getId().equals("DIODE_PROTECTED_INDICATOR"))
            fallbackTitle = "TSJ DIODE INDICATOR";
        else if (board.getId().equals("PARALLEL_DUAL_INDICATOR"))
            fallbackTitle = "TSJ PARALLEL INDICATORS";
        else
            fallbackTitle = "TSJ LED INDICATOR";
        String title = board.getSilkscreenTitle();
        if (title == null) title = fallbackTitle;
        // A public title uses the existing family header slot. Shorter ink must
        // not move other labels, change cropping, or alter packing occupancy.
        int titleWidth = Math.max(textWidth(title, 14), textWidth(fallbackTitle, 14));
        int componentLeft = outline.x + outline.width;
        int componentTop = outline.y + outline.height;
        for (PcbComponentPlacement component : layout.getComponents()) {
            componentLeft = Math.min(componentLeft, component.getRoutingCourtyard().x);
            componentTop = Math.min(componentTop, component.getRoutingCourtyard().y);
        }
        int titleY = Math.max(outline.y + 15, componentTop - 34);
        Vector<Rectangle> titleCandidates = new Vector<Rectangle>();
        titleCandidates.add(new Rectangle(componentLeft, titleY,
            titleWidth, 18));
        // The leftmost component and the topmost component can be different
        // placements.  If their independently derived title corner collides
        // with a body, scan the same bounded board envelope used for other
        // labels before rejecting this candidate.
        Rectangle titleBounds = chooseLabelPosition(layout, board, outline,
            titleCandidates, false);
        addLabel(layout, board, outline, "board-title", title,
            titleBounds, 14, true, null, false);

        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            BoardComponent component = board.getComponent(componentId);
            String text = component.getDisplayName();
            int width = textWidth(component.getReferenceLabelCharacters(), 14);
            Vector<Rectangle> candidates = getReferenceCandidates(placement, width, 18, false);
            Rectangle selected = chooseLabelPosition(layout, board, outline, candidates, false);
            addLabel(layout, board, outline, "component:" + componentId, text, selected,
                14, true, null, false);
        }

        for (String powerInputId : board.getPowerInputIds()) {
            ExternalBoardPowerInput input = board.getPowerInput(powerInputId);
            BoardPad positivePad = board.getPad(input.getPositivePadId());
            PcbComponentPlacement positiveComponent = positivePad == null ? null :
                layout.getComponent(positivePad.getComponentId());
            boolean leftEdge = positiveComponent == null ||
                positiveComponent.getX() < outline.x + outline.width / 2;
            placeNetLabel(layout, board, outline, input.getPositivePadId(), "+V", leftEdge);
            placeNetLabel(layout, board, outline, input.getReturnPadId(), "GND", leftEdge);
        }
    }

    private void placeNetLabel(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline,
            String padId, String text, boolean leftEdge) {
        PcbPadPlacement pad = layout.getPad(padId);
        if (pad == null)
            throw new IllegalStateException("Missing connector pad for silkscreen label: " + padId);
        int width = textWidth("+V".equals(text) ? "+12V" : text, 12);
        Vector<Rectangle> candidates = new Vector<Rectangle>();
        int sideX = leftEdge ? pad.getX() + 22 : pad.getX() - width - 22;
        candidates.add(new Rectangle(sideX, pad.getY() - 23, width, 16));
        candidates.add(new Rectangle(sideX, pad.getY() + 8, width, 16));
        int outerX = leftEdge ? pad.getX() - width - 22 : pad.getX() + 22;
        candidates.add(new Rectangle(outerX, pad.getY() - 23, width, 16));
        candidates.add(new Rectangle(outerX, pad.getY() + 8, width, 16));
        Rectangle selected = chooseLabelPosition(layout, board, outline, candidates, false);
        addLabel(layout, board, outline, "net:" + padId, text, selected, 12, false, padId, false);
    }

    private Vector<Rectangle> getReferenceCandidates(PcbComponentPlacement placement, int width,
            int height) {
        return getReferenceCandidates(placement, width, height, true);
    }

    private Vector<Rectangle> getReferenceCandidates(PcbComponentPlacement placement, int width,
            int height, boolean includeRowOffsets) {
        Rectangle bounds = placement.getBodyBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        Vector<Rectangle> candidates = new Vector<Rectangle>();
        if (includeRowOffsets) {
            addReferenceRowCandidates(candidates, bounds, centerX, bounds.y - height - 8,
                width, height);
            addReferenceRowCandidates(candidates, bounds, centerX, bounds.y + bounds.height + 8,
                width, height);
        } else {
            candidates.add(new Rectangle(centerX - width / 2, bounds.y - height - 8, width, height));
            candidates.add(new Rectangle(centerX - width / 2, bounds.y + bounds.height + 8,
                width, height));
        }
        candidates.add(new Rectangle(bounds.x - width - 8, centerY - height / 2, width, height));
        candidates.add(new Rectangle(bounds.x + bounds.width + 8, centerY - height / 2,
            width, height));
        return candidates;
    }

    private void addReferenceRowCandidates(Vector<Rectangle> candidates, Rectangle body,
            int centerX, int y, int width, int height) {
        int centeredX = centerX - width / 2;
        candidates.add(new Rectangle(centeredX, y, width, height));
        for (int offset = GRID; offset <= body.width; offset += GRID) {
            int left = centeredX - offset, right = centeredX + offset;
            if (left >= body.x && left + width <= body.x + body.width)
                candidates.add(new Rectangle(left, y, width, height));
            if (right >= body.x && right + width <= body.x + body.width)
                candidates.add(new Rectangle(right, y, width, height));
        }
    }

    private Rectangle chooseLabelPosition(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Vector<Rectangle> candidates, boolean avoidLeads) {
        for (Rectangle candidate : candidates) {
            if (isLabelPositionFree(layout, board, outline, candidate, avoidLeads))
                return candidate;
        }
        // A derived or conventional label position can be occupied by another
        // body.  Keep labels collision-free by using a deterministic board
        // scan before rejecting an otherwise valid route.
        Rectangle sample = candidates.isEmpty() ? null : candidates.get(0);
        if (sample != null) {
            Rectangle preferred=null;
            if(board.getPlacementConstraints().placementDensityVersion>=5) {
                // A fallback legend must not pull the outline into remote unused
                // planner space. Keep the same full-text/grid candidates, trying
                // those near actual parts/copper first; preserve the complete scan.
                Rectangle content=layout.getOccupiedContentBounds();
                int margin=FINAL_EDGE_MARGIN;
                for(PcbPlacementConstraints.Part part:board.getPlacementConstraints().getParts())
                    margin=Math.max(margin,part.accessMargin+30);
                int left=Math.max(outline.x+4,content.x-margin);
                int top=Math.max(outline.y+4,content.y-margin);
                int right=Math.min(outline.x+outline.width,content.x+content.width+margin);
                int bottom=Math.min(outline.y+outline.height,content.y+content.height+margin);
                int x=outline.x+4+(Math.max(0,left-outline.x-4)+GRID-1)/GRID*GRID;
                int y=outline.y+4+(Math.max(0,top-outline.y-4)+GRID-1)/GRID*GRID;
                preferred=new Rectangle(x,y,Math.max(0,right-x),Math.max(0,bottom-y));
                for(int py=preferred.y;py+sample.height<=preferred.y+preferred.height;py+=GRID)
                    for(int px=preferred.x;px+sample.width<=preferred.x+preferred.width;px+=GRID) {
                        Rectangle fallback=new Rectangle(px,py,sample.width,sample.height);
                        if(isLabelPositionFree(layout,board,outline,fallback,avoidLeads))return fallback;
                    }
            }
            for (int y = outline.y + 4; y + sample.height <= outline.y + outline.height;
                    y += GRID) {
                for (int x = outline.x + 4; x + sample.width <= outline.x + outline.width;
                        x += GRID) {
                    if(preferred!=null && x>=preferred.x && y>=preferred.y &&
                            x+sample.width<=preferred.x+preferred.width &&
                            y+sample.height<=preferred.y+preferred.height)continue;
                    Rectangle fallback = new Rectangle(x, y, sample.width, sample.height);
                    if (isLabelPositionFree(layout, board, outline, fallback, avoidLeads))
                        return fallback;
                }
            }
        }
        throw reject(PcbRoutingRejectedException.Kind.PLACEMENT,
            "Unable to place collision-free PCB silkscreen label");
    }

    private boolean isLabelPositionFree(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Rectangle candidate) {
        return isLabelPositionFree(layout, board, outline, candidate, true);
    }

    private boolean isLabelPositionFree(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Rectangle candidate, boolean avoidLeads) {
        if (candidate.x < outline.x || candidate.y < outline.y ||
                candidate.x + candidate.width > outline.x + outline.width ||
                candidate.y + candidate.height > outline.y + outline.height)
            return false;
        for (PcbComponentPlacement component : layout.getComponents()) {
            if (candidate.intersects(component.getBodyBounds()))
                return false;
        }
        if (avoidLeads && overlapsConnectedLead(layout, candidate)) return false;
        for (PcbPadPlacement pad : layout.getPads()) {
            if (candidate.intersects(pad.getPadBounds()))
                return false;
        }
        for (PcbTraceGeometry trace : layout.getTraces()) {
            if(trace.getLayer()!=PcbCopperLayer.TOP) continue;
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 1; index < xPoints.length; index++) {
                if (candidate.intersects(traceStroke(xPoints[index - 1],
                        yPoints[index - 1], xPoints[index], yPoints[index],trace.getWidth())))
                    return false;
            }
        }
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels()) {
            if (candidate.intersects(label.getBounds()))
                return false;
        }
        return true;
    }

    private void addLabel(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline,
            String id, String text, Rectangle bounds, int fontSize, boolean bold,
            String targetPadId) {
        addLabel(layout, board, outline, id, text, bounds, fontSize, bold, targetPadId, true);
    }

    private void addLabel(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline,
            String id, String text, Rectangle bounds, int fontSize, boolean bold,
            String targetPadId, boolean avoidLeads) {
        if (!isLabelPositionFree(layout, board, outline, bounds, avoidLeads))
            throw new IllegalStateException("PCB silkscreen label position became occupied: " + id);
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text, bounds, fontSize, bold,
            targetPadId));
    }

    private static int textWidth(String text, int fontSize) {
        return textWidth(text.length(), fontSize);
    }

    private static int textWidth(int characters, int fontSize) {
        return Math.max(18, characters * (fontSize <= 12 ? 8 : 9));
    }

    private static Rectangle traceStroke(int firstX,int firstY,int secondX,int secondY,int width) {
        int half = width / 2;
        if (firstX == secondX)
            return new Rectangle(firstX - half, Math.min(firstY, secondY) - half,
                width,Math.abs(secondY-firstY)+width);
        if (firstY == secondY)
            return new Rectangle(Math.min(firstX, secondX) - half, firstY - half,
                Math.abs(secondX-firstX)+width,width);
        throw new IllegalStateException("PCB layout encountered a non-Manhattan trace");
    }

    private static CandidateRejected reject(PcbRoutingRejectedException.Kind kind,
            String message) {
        return new CandidateRejected(kind, message);
    }

    /** Private marker keeps provider and validator RuntimeExceptions fail-fast. */
    private static final class CandidateRejected extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final PcbRoutingRejectedException.Kind kind;
        private final PcbRoutingWork.Statistics recovery;

        CandidateRejected(PcbRoutingRejectedException.Kind kind, String message) {
            this(kind,message,null);
        }
        CandidateRejected(PcbRoutingRejectedException.Kind kind,String message,PcbRoutingWork.Statistics recovery) {
            super(message);
            this.recovery=recovery;
            if (kind == null)
                throw new IllegalArgumentException("Missing PCB candidate rejection kind");
            this.kind = kind;
        }

        PcbRoutingRejectedException.Kind getKind() { return kind; }
    }

}
