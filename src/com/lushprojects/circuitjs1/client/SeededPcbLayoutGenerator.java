package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/**
 * Generates only physical PCB geometry.  Stable board components, pads, and
 * nets are supplied by the logical board and are never invented here.
 */
class SeededPcbLayoutGenerator {
    /** Current corrected layout algorithm; package geometry remains contract v3. */
    static final int CURRENT_VERSION = 13;
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
    /** One placement and its bounded routing alternatives per generation work unit. */
    final class Session {
        private final TroubleshootBoard board;
        private final long seed,routingSeed;
        private final AttemptObserver observer;
        private final SupportedEnvelope envelope;
        private final GenerationStatistics statistics=new GenerationStatistics();
        private PcbRoutingRejectedException lastFailure;
        private PcbBoardLayout bestLayout;
        private double bestScore=Double.POSITIVE_INFINITY;
        private long bestArea=Long.MAX_VALUE;
        private int attempt,viableCandidates;
        private boolean complete;
        private final boolean mediumPolicy;
        private Vector<MediumBoardPhysicalPolicy.PlacementCandidate> mediumPlacements;
        private int mediumPlacementAttempt, mediumRouteAttempt, mediumRouteStage;
        private int mediumPlacementRejections, mediumOneFaceAttempts,
            mediumOneFaceSuccesses, mediumTwoLayerAttempts, mediumTwoLayerSuccesses;
        private long mediumPlacementEvaluations;
        private String mediumSelectedRoute;
        private int mediumSelectedPlacementAttempt = -1;
        private final Vector<Integer> mediumRankedAttempts = new Vector<Integer>();
        private final Vector<String> mediumPlacementScores = new Vector<String>();
        private final Vector<String> mediumRouteOutcomes = new Vector<String>();
        private PcbBoardLayout mediumOneFaceLayout, mediumTwoLayerLayout;
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
            if (mediumPolicy) return SeededPcbLayoutGenerator.this.advanceMedium(this);
            observer.check(attempt);
            statistics.placements++;
            try {
                int variationMode = (int) ((seed % 4 + 4) % 4);
                PcbBoardLayout candidate = generateAttempt(board, seed,
                    variationMode, attempt, observer,routingSeed,statistics);
                if (envelope != null) envelope.requireBounds(board,candidate);
                double score = candidate.getRouteQualityScore(board);
                Rectangle bounds = candidate.getBoardOutline();
                long area = (long)bounds.width * bounds.height;
                if (bestLayout == null || area < bestArea || (area == bestArea && score < bestScore)) {
                    bestArea = area;
                    bestLayout = candidate;
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
            complete=attempt==MAX_ATTEMPTS || viableCandidates >=
                (board.getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM ? 1 : TARGET_VIABLE_CANDIDATES);
            if(complete) {
                if(bestLayout==null) {
                    if(lastFailure==null)throw new IllegalStateException("PCB generation stopped without a result or rejection");
                    throw PcbRoutingRejectedException.exhausted(seed,MAX_ATTEMPTS,lastFailure);
                }
                bestLayout.setGenerationStatistics(statistics.placements,statistics.routes,statistics.expansions,statistics.routeMillis,seed,routingSeed);
            }
            return complete;
        }
        PcbBoardLayout result() {
            if(!complete || bestLayout==null)throw new IllegalStateException("PCB planning has no completed result");
            return bestLayout;
        }
        MediumBoardPhysicalPolicy.Result mediumResult() { return mediumPolicyResult; }
    }

    private boolean advanceMedium(Session session) {
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
        if (twoLayer && session.mediumOneFaceSuccesses > 0)
            limit = Math.min(1, limit);
        if (session.mediumRouteAttempt < limit) {
            int candidateIndex = session.mediumRouteAttempt;
            if (twoLayer && session.mediumOneFaceSuccesses > 0 &&
                    session.mediumRouteAttempt == 0) {
                candidateIndex = mediumPlacementIndex(session,
                    session.mediumOneFacePlacementAttempt);
            }
            MediumBoardPhysicalPolicy.PlacementCandidate candidate =
                session.mediumPlacements.get(candidateIndex);
            session.mediumRouteAttempt++;
            try {
                PcbBoardLayout layout;
                if (twoLayer) {
                    session.mediumTwoLayerAttempts++;
                    layout = routeMediumTwoLayer(session.board, session.seed,
                        session.routingSeed, candidate, session.observer, session.statistics);
                } else {
                    session.mediumOneFaceAttempts++;
                    int variationMode = (int) ((session.seed % 4 + 4) % 4);
                    layout = routePlan(session.board, session.seed, variationMode,
                        candidate.attempt, session.observer, session.routingSeed,
                        session.statistics, candidate.plan, false);
                }
                if (envelopeFor(session) != null)
                    envelopeFor(session).requireBounds(session.board, layout);
                considerMediumLayout(session, candidate, layout, twoLayer);
                session.mediumRouteOutcomes.add((twoLayer ?
                    MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER :
                    MediumBoardPhysicalPolicy.P05_ONE_FACE) + "@" + candidate.attempt +
                    "=SUCCESS");
            } catch (CandidateRejected failure) {
                session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                    failure.getKind(), checkpoint, session.seed, failure.getMessage(),
                    failure.recovery);
                session.mediumRouteOutcomes.add(routeFailure(twoLayer, candidate, failure.getMessage()));
            } catch (SupportedEnvelope.Rejected failure) {
                session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                    PcbRoutingRejectedException.Kind.PLACEMENT, checkpoint, session.seed,
                    failure.getMessage());
                session.mediumRouteOutcomes.add(routeFailure(twoLayer, candidate, failure.getMessage()));
            } catch (PcbBoardLayout.RouteQualityRejectedException failure) {
                session.lastFailure = PcbRoutingRejectedException.attemptRejected(
                    PcbRoutingRejectedException.Kind.ROUTING, checkpoint, session.seed,
                    failure.getMessage());
                session.mediumRouteOutcomes.add(routeFailure(twoLayer, candidate, failure.getMessage()));
            }
            if (twoLayer) session.mediumTwoLayerSuccesses = countMediumSuccesses(
                session.mediumRouteOutcomes, MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER);
            else session.mediumOneFaceSuccesses = countMediumSuccesses(
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

    private static int mediumPlacementIndex(Session session, int placementAttempt) {
        for (int index = 0; index < session.mediumPlacements.size(); index++)
            if (session.mediumPlacements.get(index).attempt == placementAttempt)
                return index;
        return 0;
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
            PcbBoardLayout layout, boolean twoLayer) {
        if (layout == null) throw new IllegalArgumentException("Missing medium routed layout");
        double score = layout.getRouteQualityScore(session.board);
        Rectangle bounds = layout.getBoardOutline();
        long area = (long) bounds.width * bounds.height;
        if (twoLayer) {
            if (mediumRouteCandidateBetter(score, area, candidate,
                    session.mediumTwoLayerLayout, session.mediumTwoLayerQuality,
                    session.mediumTwoLayerArea, session.mediumTwoLayerPlacementAttempt,
                    session.mediumTwoLayerPlacementScore)) {
                session.mediumTwoLayerLayout = layout;
                session.mediumTwoLayerQuality = score;
                session.mediumTwoLayerArea = area;
                session.mediumTwoLayerPlacementAttempt = candidate.attempt;
                session.mediumTwoLayerPlacementScore = candidate.cheapScore;
            }
        } else if (mediumRouteCandidateBetter(score, area, candidate,
                session.mediumOneFaceLayout, session.mediumOneFaceQuality,
                session.mediumOneFaceArea, session.mediumOneFacePlacementAttempt,
                session.mediumOneFacePlacementScore)) {
            session.mediumOneFaceLayout = layout;
            session.mediumOneFaceQuality = score;
            session.mediumOneFaceArea = area;
            session.mediumOneFacePlacementAttempt = candidate.attempt;
            session.mediumOneFacePlacementScore = candidate.cheapScore;
        }
    }

    private static boolean mediumRouteCandidateBetter(double quality, long area,
            MediumBoardPhysicalPolicy.PlacementCandidate candidate,
            PcbBoardLayout current, double currentQuality, long currentArea,
            int currentAttempt, long currentPlacementScore) {
        if (current == null) return true;
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
        if (chooseTwoLayer) {
            session.bestLayout = session.mediumTwoLayerLayout;
            session.bestArea = session.mediumTwoLayerArea;
            session.bestScore = session.mediumTwoLayerQuality;
            session.mediumSelectedRoute = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER;
            session.mediumSelectedPlacementAttempt = session.mediumTwoLayerPlacementAttempt;
            session.mediumSelectedPlacementScore = session.mediumTwoLayerPlacementScore;
            session.mediumSelectedRouteQualityMilli = routeQualityMilli(
                session.mediumTwoLayerQuality);
        } else {
            session.bestLayout = session.mediumOneFaceLayout;
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

    private boolean finishMediumSuccess(Session session) {
        if (!selectMediumLayout(session))
            return finishMediumFailure(session);
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
            session.mediumSelectedRouteQualityMilli);
    }

    private SupportedEnvelope envelopeFor(Session session) { return session.envelope; }

    private PcbBoardLayout routeMediumTwoLayer(TroubleshootBoard board, long seed,
            long routingSeed, MediumBoardPhysicalPolicy.PlacementCandidate candidate,
            AttemptObserver observer, GenerationStatistics statistics) {
        PcbBoardLayout placement = candidate.plan.materialize();
        long started = System.currentTimeMillis();
        PcbLayerRoutingPrototype.Result result = PcbLayerRoutingPrototype.route(board,
            placement, PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, observer);
        statistics.routes += result.orderings;
        statistics.expansions += result.expansions;
        statistics.routeMillis += System.currentTimeMillis() - started;
        if (!result.accepted())
            throw new CandidateRejected(PcbRoutingRejectedException.Kind.ROUTING,
                "P07_FULLER_TWO_LAYER:" + result.outcome);
        return finishRoutedPlan(board, (int) ((seed % 4 + 4) % 4), candidate.plan,
            result.layout, null, false);
    }

    private PcbBoardLayout generateAttempt(TroubleshootBoard board, long seed,
            int variationMode, int attempt, AttemptObserver observer,long routingSeed,GenerationStatistics statistics) {
        PcbPlacementPlanner.Plan plan;
        try {
            plan = new PcbPlacementPlanner(footprintRegistry).plan(board,
                board.getPlacementConstraints(), seed, attempt);
        } catch (PcbPlacementPlanner.Rejected failure) {
            throw reject(PcbRoutingRejectedException.Kind.PLACEMENT, failure.reason);
        }
        PcbBoardLayout original = routePlan(board, seed, variationMode, attempt, observer,
            routingSeed, statistics, plan, false);
        PcbPlacementPlanner.Plan compact = PcbPlacementCompactor.compact(board, board.getPlacementConstraints(), plan);
        if (compact == plan) return original;
        // Keep every successful original candidate. A smaller layout is accepted
        // only after its own real routing, access and geometry checks succeed.
        // One bounded compact routing request retains the successful original on exhaustion.
        try {
            PcbBoardLayout result = routePlan(board, seed, variationMode, attempt, observer,
                routingSeed, statistics, compact, true);
            Rectangle a=original.getBoardOutline(), b=result.getBoardOutline();
            return (long)b.width*b.height < (long)a.width*a.height ? result : original;
        } catch (CandidateRejected unavailable) { return original; }
        catch (PcbBoardLayout.RouteQualityRejectedException unavailable) { return original; }
    }
    private PcbBoardLayout routePlan(TroubleshootBoard board, long seed, int variationMode,
            int attempt, AttemptObserver observer, long routingSeed, GenerationStatistics statistics,
            PcbPlacementPlanner.Plan plan, boolean compactProbe) {
        Rectangle outline = plan.outline;
        PcbBoardLayout layout = null;
        // One scheduler owns all ordering and rip-up budgets for this placement.
        layout=plan.materialize();
        long routingStarted=System.currentTimeMillis();
        try { PcbNetRouter.route(layout,board,outline,attempt,observer,true,routingSeed); }
        catch(PcbNetRouter.Rejected failure) {
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

    private PcbBoardLayout finishRoutedPlan(TroubleshootBoard board, int variationMode,
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
        for (PcbFootprint footprint:plan.footprints) {
            PcbPlacementConstraints.Part demand=board.getPlacementConstraints().get(
                footprint.getPlacement().getComponentId());
            Rectangle access=PcbPlacementPlanner.envelope(footprint,demand.accessMargin);
            edgeMargin=Math.max(edgeMargin,occupied.x-access.x+10);
            edgeMargin=Math.max(edgeMargin,occupied.y-access.y+10);
            edgeMargin=Math.max(edgeMargin,access.x+access.width-occupied.x-occupied.width+10);
            edgeMargin=Math.max(edgeMargin,access.y+access.height-occupied.y-occupied.height+10);
        }
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
        Vector<PcbFootprint> finalFootprints = translatedPlanFootprints(plan, layout, 0, 0);
        try { PcbPlacementPlanner.validate(board,board.getPlacementConstraints(),layout.getBoardOutline(),finalFootprints); }
        catch(PcbPlacementPlanner.Rejected failure) { throw reject(PcbRoutingRejectedException.Kind.PLACEMENT,failure.reason); }
        layout.validateGeometry(board);
        return layout;
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
        String title;
        if (board.getId().equals("RB15_CONTROL_BOARD"))
            title = "TSJ CONTROL BOARD";
        else if (board.getId().equals("DIODE_PROTECTED_INDICATOR"))
            title = "TSJ DIODE INDICATOR";
        else if (board.getId().equals("PARALLEL_DUAL_INDICATOR"))
            title = "TSJ PARALLEL INDICATORS";
        else
            title = "TSJ LED INDICATOR";
        int componentLeft = outline.x + outline.width;
        int componentTop = outline.y + outline.height;
        for (PcbComponentPlacement component : layout.getComponents()) {
            componentLeft = Math.min(componentLeft, component.getRoutingCourtyard().x);
            componentTop = Math.min(componentTop, component.getRoutingCourtyard().y);
        }
        int titleY = Math.max(outline.y + 15, componentTop - 34);
        Vector<Rectangle> titleCandidates = new Vector<Rectangle>();
        titleCandidates.add(new Rectangle(componentLeft, titleY,
            textWidth(title, 14), 18));
        // The leftmost component and the topmost component can be different
        // placements.  If their independently derived title corner collides
        // with a body, scan the same bounded board envelope used for other
        // labels before rejecting this candidate.
        Rectangle titleBounds = chooseLabelPosition(layout, board, outline,
            titleCandidates);
        addLabel(layout, board, outline, "board-title", title,
            titleBounds, 14, true, null);

        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            String text = board.getComponent(componentId).getDisplayName();
            int width = textWidth(text, 14);
            Vector<Rectangle> candidates = getReferenceCandidates(placement, width, 18);
            Rectangle selected = chooseLabelPosition(layout, board, outline, candidates);
            addLabel(layout, board, outline, "component:" + componentId, text, selected,
                14, true, null);
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
        Rectangle selected = chooseLabelPosition(layout, board, outline, candidates);
        addLabel(layout, board, outline, "net:" + padId, text, selected, 12, false, padId);
    }

    private Vector<Rectangle> getReferenceCandidates(PcbComponentPlacement placement, int width,
            int height) {
        Rectangle bounds = placement.getBodyBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        Vector<Rectangle> candidates = new Vector<Rectangle>();
        candidates.add(new Rectangle(centerX - width / 2, bounds.y - height - 8, width, height));
        candidates.add(new Rectangle(centerX - width / 2, bounds.y + bounds.height + 8,
            width, height));
        candidates.add(new Rectangle(bounds.x - width - 8, centerY - height / 2, width, height));
        candidates.add(new Rectangle(bounds.x + bounds.width + 8, centerY - height / 2,
            width, height));
        return candidates;
    }

    private Rectangle chooseLabelPosition(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Vector<Rectangle> candidates) {
        for (Rectangle candidate : candidates) {
            if (isLabelPositionFree(layout, board, outline, candidate))
                return candidate;
        }
        // A derived or conventional label position can be occupied by another
        // body.  Keep labels collision-free by using a deterministic board
        // scan before rejecting an otherwise valid route.
        Rectangle sample = candidates.isEmpty() ? null : candidates.get(0);
        if (sample != null) {
            for (int y = outline.y + 4; y + sample.height <= outline.y + outline.height;
                    y += GRID) {
                for (int x = outline.x + 4; x + sample.width <= outline.x + outline.width;
                        x += GRID) {
                    Rectangle fallback = new Rectangle(x, y, sample.width, sample.height);
                    if (isLabelPositionFree(layout, board, outline, fallback))
                        return fallback;
                }
            }
        }
        throw reject(PcbRoutingRejectedException.Kind.PLACEMENT,
            "Unable to place collision-free PCB silkscreen label");
    }

    private boolean isLabelPositionFree(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Rectangle candidate) {
        if (candidate.x < outline.x || candidate.y < outline.y ||
                candidate.x + candidate.width > outline.x + outline.width ||
                candidate.y + candidate.height > outline.y + outline.height)
            return false;
        for (PcbComponentPlacement component : layout.getComponents()) {
            if (candidate.intersects(component.getBodyBounds()))
                return false;
        }
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
                        yPoints[index - 1], xPoints[index], yPoints[index])))
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
        if (!isLabelPositionFree(layout, board, outline, bounds))
            throw new IllegalStateException("PCB silkscreen label position became occupied: " + id);
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text, bounds, fontSize, bold,
            targetPadId));
    }

    private static int textWidth(String text, int fontSize) {
        return Math.max(18, text.length() * (fontSize <= 12 ? 8 : 9));
    }

    private static Rectangle traceStroke(int firstX, int firstY, int secondX, int secondY) {
        int half = PcbTraceRules.TRACE_WIDTH / 2;
        if (firstX == secondX)
            return new Rectangle(firstX - half, Math.min(firstY, secondY) - half,
                PcbTraceRules.TRACE_WIDTH,
                Math.abs(secondY - firstY) + PcbTraceRules.TRACE_WIDTH);
        if (firstY == secondY)
            return new Rectangle(Math.min(firstX, secondX) - half, firstY - half,
                Math.abs(secondX - firstX) + PcbTraceRules.TRACE_WIDTH,
                PcbTraceRules.TRACE_WIDTH);
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
