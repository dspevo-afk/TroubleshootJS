package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/**
 * Generates only physical PCB geometry.  Stable board components, pads, and
 * nets are supplied by the logical board and are never invented here.
 */
class SeededPcbLayoutGenerator {
    /** Current corrected layout algorithm; package geometry remains contract v3. */
    static final int CURRENT_VERSION = 7;
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
    /** One placement and its bounded routing alternatives per generation work unit. */
    final class Session {
        private final TroubleshootBoard board;
        private final long seed,routingSeed;
        private final AttemptObserver observer;
        private final GenerationStatistics statistics=new GenerationStatistics();
        private PcbRoutingRejectedException lastFailure;
        private PcbBoardLayout bestLayout;
        private double bestScore=Double.POSITIVE_INFINITY;
        private int attempt,viableCandidates;
        private boolean complete;
        Session(TroubleshootBoard board,long seed,long routingSeed,AttemptObserver observer) {
            if(board==null)throw new IllegalArgumentException("Missing logical board for PCB generation");
            this.board=board;this.seed=seed;this.routingSeed=routingSeed;
            this.observer=observer==null?attemptObserver:observer;
        }
        boolean advance() {
            if(complete)throw new IllegalStateException("PCB planning is already complete");
            observer.check(attempt);
            statistics.placements++;
            try {
                int variationMode = (int) ((seed % 4 + 4) % 4);
                PcbBoardLayout candidate = generateAttempt(board, seed,
                    variationMode, attempt, observer,routingSeed,statistics);
                double score = candidate.getRouteQualityScore(board);
                if (bestLayout == null || score < bestScore) {
                    bestLayout = candidate;
                    bestScore = score;
                }
                viableCandidates++;
            } catch (CandidateRejected failure) {
                lastFailure = PcbRoutingRejectedException.attemptRejected(
                    failure.getKind(), attempt, seed, failure.getMessage());
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
        Rectangle outline = plan.outline;
        PcbBoardLayout layout = null;
        // Four feedback alternatives, then an independent reverse/farthest
        // tree. The latter avoids a cycle caused by repeatedly promoting blockers.
        int treeVariants=board.getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM ? 5 : 1;
        Vector<String> blocked=new Vector<String>();
        for(int tree=0;tree<treeVariants;tree++) {
            layout=plan.materialize();
            statistics.routes++;long routingStarted=System.currentTimeMillis();
            try { PcbNetRouter.route(layout, board, outline, attempt, observer,true,routingSeed,tree,tree==4?null:blocked); break; }
            catch (PcbNetRouter.Rejected failure) {
                if(failure.blockedNet!=null) {blocked.remove(failure.blockedNet);blocked.add(failure.blockedNet);}
                if(tree+1==treeVariants) throw reject(PcbRoutingRejectedException.Kind.ROUTING, failure.getMessage());
            }
            finally {statistics.expansions+=layout.getRoutingExpansions();statistics.routeMillis+=System.currentTimeMillis()-routingStarted;}
        }
        placeSilkscreen(layout, board, outline);
        layout.validateGeometry(board);
        int edgeMargin=FINAL_EDGE_MARGIN;
        for(PcbPlacementConstraints.Part demand:board.getPlacementConstraints().getParts())
            edgeMargin=Math.max(edgeMargin,demand.accessMargin+30);
        layout.compactToContent(FINAL_BOARD_X + variationMode * 10,
            FINAL_BOARD_Y + (variationMode % 2) * 10, edgeMargin);
        layout.positionPartsTrayDisjointFromBoard();
        Vector<PcbFootprint> finalFootprints=new Vector<PcbFootprint>();
        for(PcbFootprint footprint:plan.footprints) {
            PcbComponentPlacement position=layout.getComponent(footprint.getPlacement().getComponentId());
            finalFootprints.add(footprint.translated(position.getX(),position.getY()));
        }
        try { PcbPlacementPlanner.validate(board,board.getPlacementConstraints(),layout.getBoardOutline(),finalFootprints); }
        catch(PcbPlacementPlanner.Rejected failure) { throw reject(PcbRoutingRejectedException.Kind.PLACEMENT,failure.reason); }
        layout.validateGeometry(board);
        return layout;
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

        CandidateRejected(PcbRoutingRejectedException.Kind kind, String message) {
            super(message);
            if (kind == null)
                throw new IllegalArgumentException("Missing PCB candidate rejection kind");
            this.kind = kind;
        }

        PcbRoutingRejectedException.Kind getKind() { return kind; }
    }

}
