package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Test-only, bounded observation of geometrically-shorter same-face P07 paths.
 *
 * <p><strong>Precondition:</strong> from {@link #begin(Context)} until its cursor
 * completes, the caller must keep the live router, its layout, and endpoint-pad
 * state exclusively quiescent. The cursor checks equality before query work and
 * after query work; these checks are not an atomic snapshot and cannot detect
 * arbitrary concurrent mutation while an incremental baseline copy is being
 * built. The source-mutation canary below covers only an already-copied cell.
 */
final class P07ShortcutQueryDiagnostics {
    private static final int MAX_PATH_NODES = 66;
    private static final int MAX_CELLS = 250000;
    private static final int MAX_BENDS = 16;
    private static final String FAMILY_ORDER = "I_J_ORIENTATION_X_FIRST_0_Y_FIRST_1";

    private P07ShortcutQueryDiagnostics() { }

    /**
     * Diagnostic input bindings. Primitive arrays are copied; pad references
     * deliberately retain identity. The router, layout, and pads remain live
     * source objects. Keep them exclusively quiescent from
     * {@link P07ShortcutQueryDiagnostics#begin(Context)} through completion.
     */
    static final class Context {
        final PcbBoardLayout layout;
        final PcbNetRouter.Router liveFace;
        final String net;
        final PcbPadPlacement start;
        final PcbPadPlacement root;
        private final PcbPadPlacement[] reached;
        private final boolean[] goalMask;
        final int width, height, originX, originY, pitch, layer;
        private final int[][] path;
        final int cells;
        final int baseLength, directLength, minimumSaving, maximumLength;
        final String prefixGeometryFingerprint;

        Context(PcbBoardLayout layout, PcbNetRouter.Router liveFace, String net,
                PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
                boolean[] goalMask, int width, int height, int originX, int originY,
                int pitch, int layer, int[][] path) {
            if (layout == null || liveFace == null || net == null || net.length() == 0 ||
                    start == null || root == null || reached == null || goalMask == null ||
                    path == null || width < 1 || height < 1 || pitch < 1 || layer < 0 || layer > 1 ||
                    path.length < 2 || path.length > MAX_PATH_NODES ||
                    (long) width * (long) height > MAX_CELLS)
                throw new IllegalArgumentException("Invalid P07 shortcut-query context");
            long cellCount = (long) width * (long) height;
            if (goalMask.length != cellCount * 2L)
                throw new IllegalArgumentException("P07 shortcut-query goal mask has the wrong shape");
            if (layout.getPad(start.getPadId()) != start || layout.getPad(root.getPadId()) != root ||
                    !PcbCopperAccess.hasCopper(start, PcbCopperLayer.values()[layer]) ||
                    !PcbCopperAccess.hasCopper(root, PcbCopperLayer.values()[layer]))
                throw new IllegalArgumentException("P07 shortcut-query endpoint identity or face mismatch");
            this.reached = new PcbPadPlacement[reached.length];
            for (int i = 0; i < reached.length; i++) {
                PcbPadPlacement pad = reached[i];
                if (pad == null || layout.getPad(pad.getPadId()) != pad ||
                        pad == root ||
                        pad.getAttachment() != PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                        !PcbCopperAccess.hasCopper(pad, PcbCopperLayer.values()[layer]))
                    throw new IllegalArgumentException("Invalid P07 reached PTH authority");
                for (int previous = 0; previous < i; previous++)
                    if (this.reached[previous] == pad)
                        throw new IllegalArgumentException("Duplicate P07 reached PTH authority");
                this.reached[i] = pad;
            }
            this.layout = layout;
            this.liveFace = liveFace;
            this.net = net;
            this.start = start;
            this.root = root;
            this.goalMask = new boolean[goalMask.length];
            System.arraycopy(goalMask, 0, this.goalMask, 0, goalMask.length);
            this.width = width;
            this.height = height;
            this.originX = originX;
            this.originY = originY;
            this.pitch = pitch;
            this.layer = layer;
            this.cells = (int) cellCount;
            this.path = new int[path.length][3];
            for (int i = 0; i < path.length; i++) {
                if (path[i] == null || path[i].length != 3 || path[i][2] != layer ||
                        cell(path[i][0], path[i][1], originX, originY, width, height, pitch) < 0)
                    throw new IllegalArgumentException("Invalid same-face P07 shortcut path row");
                this.path[i][0] = path[i][0];
                this.path[i][1] = path[i][1];
                this.path[i][2] = path[i][2];
                if (i > 0) {
                    long step = Math.abs((long) path[i][0] - path[i - 1][0]) +
                        Math.abs((long) path[i][1] - path[i - 1][1]);
                    if (step != pitch)
                        throw new IllegalArgumentException("P07 shortcut source path is not grid-adjacent");
                }
            }
            if (this.path[0][0] != start.getX() || this.path[0][1] != start.getY())
                throw new IllegalArgumentException("P07 shortcut path does not begin at its actual start pad");
            int endCell = cell(this.path[this.path.length - 1][0],
                this.path[this.path.length - 1][1], originX, originY, width, height, pitch);
            if (!this.goalMask[layer * cells + endCell])
                throw new IllegalArgumentException("P07 shortcut path end is not an actual goal");
            long base = (long) (path.length - 1) * (long) pitch;
            if (base > Integer.MAX_VALUE)
                throw new IllegalArgumentException("P07 shortcut base length is outside the bounded domain");
            this.baseLength = (int) base;
            long direct = Math.abs((long) this.path[0][0] - this.path[path.length - 1][0]) +
                Math.abs((long) this.path[0][1] - this.path[path.length - 1][1]);
            if (direct > Integer.MAX_VALUE / 3)
                throw new IllegalArgumentException("P07 shortcut direct length is outside the bounded domain");
            this.directLength = (int) direct;
            this.minimumSaving = Math.max(1, baseLength - 3 * directLength);
            this.maximumLength = 3 * directLength;
            this.prefixGeometryFingerprint = layout.geometryFingerprint();
        }

        private static int cell(int x, int y, int originX, int originY,
                int width, int height, int pitch) {
            long dx = (long) x - originX;
            long dy = (long) y - originY;
            if (dx < 0 || dy < 0 || dx % pitch != 0 || dy % pitch != 0)
                return -1;
            long cx = dx / pitch, cy = dy / pitch;
            if (cx >= width || cy >= height) return -1;
            return (int) (cy * width + cx);
        }
    }

    /**
     * Starts the resumable observation. The caller must keep the live router,
     * its layout, and endpoint-pad state exclusively quiescent until
     * completion.
     * The incremental equality checks do not provide a concurrent atomic snapshot.
     */
    static Cursor begin(Context context) {
        return new Cursor(context);
    }

    /** Bounded cursor; each pending call consumes at least one and at most maximumWork units. */
    static final class Cursor {
        private static final int COPY_PRISTINE = 0;
        private static final int COMPARE_PRISTINE = 1;
        private static final int COPY_QUERY = 2;
        private static final int COMPARE_QUERY = 3;
        private static final int ENUMERATE = 4;
        private static final int SCAN_BENDS = 5;
        private static final int ENUMERATE_EDGES = 6;
        private static final int QUERY_EDGES = 7;
        private static final int BUILD_ORIGINAL_PATH = 8;
        private static final int ORIGINAL_PATH = 9;
        private static final int CONJOIN = 10;
        private static final int VERIFY_LIVE = 11;
        private static final int COMPLETE = 12;

        private final Context context;
        private final String liveEffectsBefore;
        private final String layoutFingerprintBefore;
        private final String prefixGeometrySha256;
        private final TreeMap<Long, EdgeDraft> edgeMap = new TreeMap<Long, EdgeDraft>();
        private final TreeMap<Long, EdgeDraft> originalPathEdgeMap = new TreeMap<Long, EdgeDraft>();
        private final ArrayList<CandidateDraft> candidates = new ArrayList<CandidateDraft>();
        private PcbNetRouter.Router.FixedGraphCopyTask copyTask;
        private PcbNetRouter.Router.FixedGraphComparisonTask comparisonTask;
        private PcbNetRouter.Router pristine;
        private PcbNetRouter.Router query;
        private int stage = COPY_PRISTINE;
        private int lastWork;
        private int advanceCalls;
        private long workSteps, copyEntries, comparisonEntries, activeNanos;
        private int queryCalls, replacementEdgeVisits, reusedEdgeVisits;
        private int originalPathQueries, originalPathIndex, copyEntryWatermark;
        private boolean originalPathVerified;
        private int nextI, nextJ = 1, nextOrientation;
        private CandidateDraft bendCandidate, edgeCandidate;
        private int bendPhase, bendPathIndex, bendAxisPhase;
        private int bendRemainingX, bendRemainingY, bendPreviousDirection = -1, bendCount;
        private int bendX, bendY;
        private int edgeRemainingX, edgeRemainingY, edgeAxisPhase, edgeX, edgeY;
        private Iterator<Map.Entry<Long, EdgeDraft>> edgeIterator;
        private Iterator<Map.Entry<Long, EdgeDraft>> originalPathIterator;
        private int conjoinCandidateIndex, conjoinEdgeIndex;
        private long queryQualifiedCount;
        private Integer minimumQualifiedLength;
        private CandidateDraft firstQualified;
        private Result result;
        private boolean complete;

        private Cursor(Context context) {
            if (context == null) throw new IllegalArgumentException("Missing P07 shortcut-query context");
            this.context = context;
            this.layoutFingerprintBefore = context.prefixGeometryFingerprint;
            this.liveEffectsBefore = context.liveFace.fixedGraphObservationEffects();
            if (!layoutFingerprintBefore.equals(context.layout.geometryFingerprint()))
                throw new IllegalStateException("P07 shortcut-query layout changed before cursor creation");
            this.prefixGeometrySha256 = sha256(layoutFingerprintBefore);
        }

        boolean advance(int maximumWork) {
            if (maximumWork < 1)
                throw new IllegalArgumentException("P07 shortcut-query work chunk must be positive");
            lastWork = 0;
            if (complete) return true;
            long started = System.nanoTime();
            advanceCalls++;
            int remaining = maximumWork;
            while (remaining > 0 && !complete) {
                int previousWork = lastWork;
                if (stage == COPY_PRISTINE) {
                    if (copyTask == null) {
                        copyTask = context.liveFace.beginFixedGraphObservationCopy();
                        copyEntryWatermark = 0;
                    }
                    boolean copied = copyTask.advanceOne(remaining);
                    int copiedNow = copyTask.copiedEntries();
                    int delta = copiedNow - copyEntryWatermark;
                    copyEntryWatermark = copiedNow;
                    copyEntries += delta;
                    charge(remaining, remaining);
                    if (copied) {
                        pristine = copyTask.result();
                        copyTask = null;
                        copyEntryWatermark = 0;
                        comparisonTask = context.liveFace.beginFixedGraphComparison(pristine);
                        stage = COMPARE_PRISTINE;
                    }
                } else if (stage == COMPARE_PRISTINE) {
                    if (advanceComparison(remaining)) {
                        comparisonTask = null;
                        copyTask = context.liveFace.beginFixedGraphObservationCopy();
                        copyEntryWatermark = 0;
                        stage = COPY_QUERY;
                    }
                } else if (stage == COPY_QUERY) {
                    boolean copied = copyTask.advanceOne(remaining);
                    int copiedNow = copyTask.copiedEntries();
                    int delta = copiedNow - copyEntryWatermark;
                    copyEntryWatermark = copiedNow;
                    copyEntries += delta;
                    charge(remaining, remaining);
                    if (copied) {
                        query = copyTask.result();
                        copyTask = null;
                        copyEntryWatermark = 0;
                        comparisonTask = context.liveFace.beginFixedGraphComparison(query);
                        stage = COMPARE_QUERY;
                    }
                } else if (stage == COMPARE_QUERY) {
                    if (advanceComparison(remaining)) {
                        comparisonTask = null;
                        stage = ENUMERATE;
                    }
                } else if (stage == ENUMERATE) {
                    enumerateTuple();
                    charge(1, remaining);
                } else if (stage == SCAN_BENDS) {
                    scanOneBendStep();
                    charge(1, remaining);
                } else if (stage == ENUMERATE_EDGES) {
                    enumerateOneReplacementEdge();
                    charge(1, remaining);
                } else if (stage == QUERY_EDGES) {
                    queryOneEdge();
                    charge(1, remaining);
                } else if (stage == BUILD_ORIGINAL_PATH) {
                    buildOneOriginalPathEdge();
                    charge(1, remaining);
                } else if (stage == ORIGINAL_PATH) {
                    queryOneOriginalPathEdge();
                    charge(1, remaining);
                } else if (stage == CONJOIN) {
                    conjoinOneCandidateEdge();
                    charge(1, remaining);
                } else if (stage == VERIFY_LIVE) {
                    if (comparisonTask == null) {
                        comparisonTask = context.liveFace.beginFixedGraphComparison(pristine);
                    }
                    if (advanceComparison(remaining)) {
                        comparisonTask = null;
                        verifyLiveSource();
                        stage = COMPLETE;
                        complete = true;
                    }
                } else {
                    throw new IllegalStateException("Unknown P07 shortcut-query stage");
                }
                int consumed = lastWork - previousWork;
                if (consumed < 1 || consumed > remaining)
                    throw new IllegalStateException("P07 shortcut-query advance made an invalid debit");
                remaining -= consumed;
            }
            activeNanos += elapsedNanosSince(started);
            if (complete) buildResult();
            return complete;
        }

        private boolean advanceComparison(int maximum) {
            int before = comparisonTask.comparedEntries();
            boolean done = comparisonTask.advanceOne(maximum);
            int after = comparisonTask.comparedEntries();
            int delta = after - before;
            comparisonEntries += delta;
            charge(maximum, maximum);
            if (done && !comparisonTask.equal())
                throw new IllegalStateException("P07 shortcut-query router snapshot changed");
            return done;
        }

        private void charge(int units, int remaining) {
            if (units < 1 || units > remaining)
                throw new IllegalStateException("P07 shortcut-query work debit exceeded its chunk");
            lastWork += units;
            workSteps += units;
        }

        private void enumerateTuple() {
            if (nextI >= context.path.length - 1) {
                edgeIterator = edgeMap.entrySet().iterator();
                stage = QUERY_EDGES;
                return;
            }
            int i = nextI, j = nextJ, orientation = nextOrientation;
            long dx = (long) context.path[j][0] - context.path[i][0];
            long dy = (long) context.path[j][1] - context.path[i][1];
            boolean aligned = dx == 0 || dy == 0;
            advanceTuplePosition(aligned, orientation);
            if (aligned && orientation == 1) return;
            long direct = Math.abs(dx) + Math.abs(dy);
            long length = (long) i * context.pitch + direct +
                (long) (context.path.length - 1 - j) * context.pitch;
            long saving = (long) context.baseLength - length;
            if (saving < context.minimumSaving || length > context.maximumLength)
                return;
            bendCandidate = new CandidateDraft(i, j, orientation, (int) length, (int) saving);
            bendPathIndex = 0;
            bendPhase = 0;
            bendAxisPhase = 0;
            bendRemainingX = 0;
            bendRemainingY = 0;
            bendPreviousDirection = -1;
            bendCount = 0;
            bendX = context.path[i][0];
            bendY = context.path[i][1];
            stage = SCAN_BENDS;
        }

        private void advanceTuplePosition(boolean aligned, int orientation) {
            if (orientation == 0 && !aligned) {
                nextOrientation = 1;
                return;
            }
            nextOrientation = 0;
            nextJ++;
            if (nextJ >= context.path.length) {
                nextI++;
                nextJ = nextI + 1;
            }
        }

        private void scanOneBendStep() {
            int direction = nextRouteDirection();
            if (direction < 0) {
                bendCandidate.bends = bendCount;
                if (bendCount <= MAX_BENDS) {
                    candidates.add(bendCandidate);
                    edgeCandidate = bendCandidate;
                    edgeCandidate.edges = new ArrayList<Long>();
                    edgeX = context.path[edgeCandidate.i][0];
                    edgeY = context.path[edgeCandidate.i][1];
                    edgeRemainingX = gridSteps(context.path[edgeCandidate.j][0], edgeX,
                        context.pitch);
                    edgeRemainingY = gridSteps(context.path[edgeCandidate.j][1], edgeY,
                        context.pitch);
                    edgeAxisPhase = 0;
                    stage = ENUMERATE_EDGES;
                } else {
                    bendCandidate = null;
                    stage = ENUMERATE;
                }
                return;
            }
            if (bendPreviousDirection >= 0 && direction != bendPreviousDirection) bendCount++;
            bendPreviousDirection = direction;
        }

        /** Emits one edge direction from prefix, replacement, or suffix. */
        private int nextRouteDirection() {
            int n = context.path.length;
            if (bendPhase == 0) {
                if (bendPathIndex < bendCandidate.i) {
                    int direction = direction(context.path[bendPathIndex],
                        context.path[bendPathIndex + 1]);
                    bendPathIndex++;
                    return direction;
                }
                bendPhase = 1;
                bendX = context.path[bendCandidate.i][0];
                bendY = context.path[bendCandidate.i][1];
                bendRemainingX = gridSteps(context.path[bendCandidate.j][0], bendX,
                    context.pitch);
                bendRemainingY = gridSteps(context.path[bendCandidate.j][1], bendY,
                    context.pitch);
                bendAxisPhase = 0;
            }
            if (bendPhase == 1) {
                while (bendAxisPhase < 2) {
                    boolean hasSteps = bendCandidate.orientation == 0 ?
                        (bendAxisPhase == 0 ? bendRemainingX > 0 : bendRemainingY > 0) :
                        (bendAxisPhase == 0 ? bendRemainingY > 0 : bendRemainingX > 0);
                    if (hasSteps) break;
                    bendAxisPhase++;
                }
                if (bendAxisPhase < 2) {
                    boolean xAxis = bendCandidate.orientation == 0 ? bendAxisPhase == 0 :
                        bendAxisPhase == 1;
                    int target = xAxis ? context.path[bendCandidate.j][0] :
                        context.path[bendCandidate.j][1];
                    int current = xAxis ? bendX : bendY;
                    int step = target > current ? context.pitch : -context.pitch;
                    if (xAxis) { bendX += step; bendRemainingX--; }
                    else { bendY += step; bendRemainingY--; }
                    if ((bendCandidate.orientation == 0 &&
                            ((bendAxisPhase == 0 && bendRemainingX == 0) ||
                             (bendAxisPhase == 1 && bendRemainingY == 0))) ||
                            (bendCandidate.orientation == 1 &&
                            ((bendAxisPhase == 0 && bendRemainingY == 0) ||
                             (bendAxisPhase == 1 && bendRemainingX == 0))) )
                        bendAxisPhase++;
                    return xAxis ? (step > 0 ? 1 : 3) : (step > 0 ? 2 : 0);
                }
                bendPhase = 2;
                bendPathIndex = bendCandidate.j;
            }
            if (bendPhase == 2) {
                if (bendPathIndex < n - 1) {
                    int direction = direction(context.path[bendPathIndex],
                        context.path[bendPathIndex + 1]);
                    bendPathIndex++;
                    return direction;
                }
                bendPhase = 3;
            }
            return -1;
        }

        private void enumerateOneReplacementEdge() {
            if (edgeRemainingX == 0 && edgeRemainingY == 0) {
                edgeCandidate = null;
                stage = ENUMERATE;
                return;
            }
            boolean xAxis;
            if (edgeCandidate.orientation == 0) {
                if (edgeRemainingX > 0) xAxis = true;
                else xAxis = false;
            } else {
                if (edgeRemainingY > 0) xAxis = false;
                else xAxis = true;
            }
            int fromX = edgeX, fromY = edgeY;
            int target = xAxis ? context.path[edgeCandidate.j][0] :
                context.path[edgeCandidate.j][1];
            int current = xAxis ? edgeX : edgeY;
            int step = target > current ? context.pitch : -context.pitch;
            if (xAxis) { edgeX += step; edgeRemainingX--; }
            else { edgeY += step; edgeRemainingY--; }
            int fromCell = cell(fromX, fromY);
            int toCell = cell(edgeX, edgeY);
            long key = ((long) fromCell * (long) context.cells) + (long) toCell;
            EdgeDraft edge = edgeMap.get(Long.valueOf(key));
            if (edge == null) {
                edge = new EdgeDraft(fromCell, toCell);
                edgeMap.put(Long.valueOf(key), edge);
            } else {
                reusedEdgeVisits++;
            }
            edgeCandidate.edges.add(Long.valueOf(key));
            replacementEdgeVisits++;
        }

        private void queryOneEdge() {
            if (edgeIterator == null || !edgeIterator.hasNext()) {
                stage = BUILD_ORIGINAL_PATH;
                return;
            }
            EdgeDraft edge = edgeIterator.next().getValue();
            int fromX = coordinate(context.originX, edge.fromCell % context.width, context.pitch);
            int fromY = coordinate(context.originY, edge.fromCell / context.width, context.pitch);
            int toX = coordinate(context.originX, edge.toCell % context.width, context.pitch);
            int toY = coordinate(context.originY, edge.toCell / context.width, context.pitch);
            edge.allowed = query.permitsLayerStep(fromX, fromY, toX, toY,
                context.net, context.start, context.root, context.reached);
            queryCalls++;
        }

        private void buildOneOriginalPathEdge() {
            if (originalPathIndex + 1 >= context.path.length) {
                originalPathIterator = originalPathEdgeMap.entrySet().iterator();
                stage = ORIGINAL_PATH;
                return;
            }
            int fromCell = cell(context.path[originalPathIndex][0],
                context.path[originalPathIndex][1]);
            int toCell = cell(context.path[originalPathIndex + 1][0],
                context.path[originalPathIndex + 1][1]);
            long key = ((long) fromCell * (long) context.cells) + (long) toCell;
            if (!originalPathEdgeMap.containsKey(Long.valueOf(key)))
                originalPathEdgeMap.put(Long.valueOf(key), new EdgeDraft(fromCell, toCell));
            originalPathIndex++;
        }

        private void queryOneOriginalPathEdge() {
            if (originalPathIterator == null || !originalPathIterator.hasNext()) {
                originalPathVerified = true;
                stage = CONJOIN;
                return;
            }
            EdgeDraft original = originalPathIterator.next().getValue();
            long key = ((long) original.fromCell * (long) context.cells) +
                (long) original.toCell;
            EdgeDraft family = edgeMap.get(Long.valueOf(key));
            if (family != null) {
                if (family.allowed == null || !family.allowed.booleanValue())
                    throw new IllegalStateException("P07 shortcut source path edge failed its family query");
                return;
            }
            int fromX = coordinate(context.originX, original.fromCell % context.width,
                context.pitch);
            int fromY = coordinate(context.originY, original.fromCell / context.width,
                context.pitch);
            int toX = coordinate(context.originX, original.toCell % context.width,
                context.pitch);
            int toY = coordinate(context.originY, original.toCell / context.width,
                context.pitch);
            if (!query.permitsLayerStep(fromX, fromY, toX, toY,
                    context.net, context.start, context.root, context.reached))
                throw new IllegalStateException("P07 shortcut source path is not permitted by its query clone");
            originalPathQueries++;
            queryCalls++;
        }

        private void conjoinOneCandidateEdge() {
            if (conjoinCandidateIndex >= candidates.size()) {
                stage = VERIFY_LIVE;
                return;
            }
            CandidateDraft candidate = candidates.get(conjoinCandidateIndex);
            if (candidate.edges.isEmpty()) {
                finishCandidate(candidate);
                conjoinCandidateIndex++;
                conjoinEdgeIndex = 0;
                return;
            }
            long key = candidate.edges.get(conjoinEdgeIndex).longValue();
            EdgeDraft edge = edgeMap.get(Long.valueOf(key));
            if (edge == null || edge.allowed == null)
                throw new IllegalStateException("P07 shortcut candidate references an unqueried edge");
            if (!edge.allowed.booleanValue()) candidate.allowed = false;
            conjoinEdgeIndex++;
            if (conjoinEdgeIndex >= candidate.edges.size()) {
                finishCandidate(candidate);
                conjoinCandidateIndex++;
                conjoinEdgeIndex = 0;
            }
        }

        private void finishCandidate(CandidateDraft candidate) {
            if (candidate.allowed && queryQualifiedCount == 0) firstQualified = candidate;
            if (candidate.allowed) {
                queryQualifiedCount++;
                if (minimumQualifiedLength == null || candidate.length < minimumQualifiedLength.intValue())
                    minimumQualifiedLength = Integer.valueOf(candidate.length);
            }
        }

        private void verifyLiveSource() {
            if (!layoutFingerprintBefore.equals(context.layout.geometryFingerprint()) ||
                    !liveEffectsBefore.equals(context.liveFace.fixedGraphObservationEffects()) ||
                    !originalPathVerified ||
                    context.layout.getPad(context.start.getPadId()) != context.start ||
                    context.layout.getPad(context.root.getPadId()) != context.root ||
                    !context.goalMask[context.layer * context.cells + cell(
                        context.path[context.path.length - 1][0],
                        context.path[context.path.length - 1][1])])
                throw new IllegalStateException("P07 shortcut query changed its live source context");
            for (PcbPadPlacement pad : context.reached)
                if (context.layout.getPad(pad.getPadId()) != pad)
                    throw new IllegalStateException("P07 shortcut query changed a reached pad identity");
        }

        private void buildResult() {
            long resultAssemblyStarted = System.nanoTime();
            ArrayList<EdgeResult> edges = new ArrayList<EdgeResult>();
            for (EdgeDraft edge : edgeMap.values()) {
                if (edge.allowed == null)
                    throw new IllegalStateException("P07 shortcut query omitted a directed edge");
                edges.add(new EdgeResult(edge.fromCell, edge.toCell, edge.allowed.booleanValue()));
            }
            ArrayList<CandidateResult> candidateResults = new ArrayList<CandidateResult>();
            StringBuilder tupleJson = new StringBuilder("[");
            for (int i = 0; i < candidates.size(); i++) {
                CandidateDraft candidate = candidates.get(i);
                if (i != 0) tupleJson.append(',');
                tupleJson.append('[').append(candidate.i).append(',').append(candidate.j).append(',')
                    .append(candidate.orientation).append(',').append(candidate.saving).append(',')
                    .append(candidate.bends).append(']');
                candidateResults.add(new CandidateResult(candidate.i, candidate.j,
                    candidate.orientation, candidate.length, candidate.bends,
                    candidate.saving, candidate.allowed));
            }
            tupleJson.append(']');
            CandidateResult first = null;
            if (firstQualified != null)
                first = new CandidateResult(firstQualified.i, firstQualified.j,
                    firstQualified.orientation, firstQualified.length, firstQualified.bends,
                    firstQualified.saving, true);
            String tupleHash = sha256(tupleJson.toString());
            List<EdgeResult> immutableEdges = Collections.unmodifiableList(edges);
            List<CandidateResult> immutableCandidates = Collections.unmodifiableList(candidateResults);
            // Accumulate nanoseconds across all advance calls, then include result payload assembly.
            // The final immutable Result wrapper and later JSON serialization are outside this metric.
            long activeMillis = (activeNanos + elapsedNanosSince(resultAssemblyStarted)) / 1000000L;
            result = new Result(prefixGeometrySha256, FAMILY_ORDER, context.minimumSaving,
                context.maximumLength, MAX_BENDS, candidateResults.size(), tupleHash,
                replacementEdgeVisits, edges.size(), advanceCalls, workSteps, copyEntries,
                comparisonEntries, queryCalls, originalPathQueries, reusedEdgeVisits, activeMillis,
                immutableEdges, immutableCandidates, queryQualifiedCount, minimumQualifiedLength, first);
        }

        private static long elapsedNanosSince(long started) {
            long elapsed = System.nanoTime() - started;
            return elapsed > 0L ? elapsed : 0L;
        }

        private int cell(int x, int y) {
            return Context.cell(x, y, context.originX, context.originY,
                context.width, context.height, context.pitch);
        }

        int lastWork() { return lastWork; }
        boolean isComplete() { return complete; }
        Result result() {
            if (!complete || result == null)
                throw new IllegalStateException("P07 shortcut-query result is incomplete");
            return result;
        }
        String bodyJson() { return result().bodyJson(); }
    }

    static final class Result {
        final int queryQualifiedCount;
        final int minimumSaving, maximumLength, maximumBends, candidateCount;
        final int replacementEdgeVisits, distinctDirectedEdges, advanceCalls, queryCalls,
            originalPathQueries;
        final long workSteps, copyEntries, comparisonEntries, activeMillis;
        final int reusedEdgeVisits;
        final String prefixGeometrySha256, order, candidateTupleSha256;
        final List<EdgeResult> edges;
        final List<CandidateResult> candidates;
        final Integer minimumQualifiedLength;
        final CandidateResult firstQualified;

        private Result(String prefixGeometrySha256, String order, int minimumSaving,
                int maximumLength, int maximumBends, int candidateCount,
                String candidateTupleSha256, int replacementEdgeVisits,
                int distinctDirectedEdges, int advanceCalls, long workSteps,
                long copyEntries, long comparisonEntries, int queryCalls,
                int originalPathQueries,
                int reusedEdgeVisits, long activeMillis, List<EdgeResult> edges,
                List<CandidateResult> candidates, long queryQualifiedCount,
                Integer minimumQualifiedLength, CandidateResult firstQualified) {
            if (queryQualifiedCount < 0 || queryQualifiedCount > Integer.MAX_VALUE)
                throw new IllegalStateException("P07 shortcut-query result count overflow");
            this.queryQualifiedCount = (int) queryQualifiedCount;
            this.prefixGeometrySha256 = prefixGeometrySha256;
            this.order = order;
            this.minimumSaving = minimumSaving;
            this.maximumLength = maximumLength;
            this.maximumBends = maximumBends;
            this.candidateCount = candidateCount;
            this.candidateTupleSha256 = candidateTupleSha256;
            this.replacementEdgeVisits = replacementEdgeVisits;
            this.distinctDirectedEdges = distinctDirectedEdges;
            this.advanceCalls = advanceCalls;
            this.workSteps = workSteps;
            this.copyEntries = copyEntries;
            this.comparisonEntries = comparisonEntries;
            this.queryCalls = queryCalls;
            this.originalPathQueries = originalPathQueries;
            this.reusedEdgeVisits = reusedEdgeVisits;
            this.activeMillis = activeMillis;
            this.edges = edges;
            this.candidates = candidates;
            this.minimumQualifiedLength = minimumQualifiedLength;
            this.firstQualified = firstQualified;
        }

        String bodyJson() {
            StringBuilder json = new StringBuilder(1024 + edges.size() * 48 + candidates.size() * 60);
            json.append("{\"stability\":{\"prefixGeometrySha256\":\"")
                .append(prefixGeometrySha256)
                .append("\",\"liveBeforeVerified\":true,\"liveAfterVerified\":true," +
                    "\"liveEffectsUnchanged\":true,\"goalVerified\":true," +
                    "\"originalPathVerified\":true},\"family\":{")
                .append("\"order\":\"").append(order).append("\",\"minimumSaving\":")
                .append(minimumSaving).append(",\"maximumLength\":").append(maximumLength)
                .append(",\"maximumBends\":").append(maximumBends).append(",\"candidateCount\":")
                .append(candidateCount).append(",\"candidateTupleSha256\":\"")
                .append(candidateTupleSha256).append("\",\"replacementEdgeVisits\":")
                .append(replacementEdgeVisits).append(",\"distinctDirectedEdges\":")
                .append(distinctDirectedEdges).append("},\"work\":{\"advanceCalls\":")
                .append(advanceCalls).append(",\"workSteps\":").append(workSteps)
                .append(",\"copyEntries\":").append(copyEntries)
                .append(",\"comparisonEntries\":").append(comparisonEntries)
                .append(",\"queryCalls\":").append(queryCalls)
                .append(",\"originalPathQueries\":").append(originalPathQueries)
                .append(",\"reusedEdgeVisits\":").append(reusedEdgeVisits)
                .append(",\"activeMillis\":").append(activeMillis).append("},\"edges\":[");
            for (int i = 0; i < edges.size(); i++) {
                EdgeResult edge = edges.get(i);
                if (i != 0) json.append(',');
                json.append("{\"fromCell\":").append(edge.fromCell)
                    .append(",\"toCell\":").append(edge.toCell)
                    .append(",\"allowed\":").append(edge.allowed).append('}');
            }
            json.append("],\"candidates\":[");
            for (int i = 0; i < candidates.size(); i++) {
                CandidateResult candidate = candidates.get(i);
                if (i != 0) json.append(',');
                json.append("{\"i\":").append(candidate.i).append(",\"j\":")
                    .append(candidate.j).append(",\"orientation\":").append(candidate.orientation)
                    .append(",\"length\":").append(candidate.length).append(",\"bends\":")
                    .append(candidate.bends).append(",\"allowed\":").append(candidate.allowed).append('}');
            }
            json.append("],\"result\":{\"queryQualifiedCount\":").append(queryQualifiedCount)
                .append(",\"minimumQualifiedLength\":")
                .append(minimumQualifiedLength == null ? "null" : minimumQualifiedLength.toString())
                .append(",\"firstQualified\":");
            if (firstQualified == null) json.append("null");
            else json.append("{\"i\":").append(firstQualified.i).append(",\"j\":")
                .append(firstQualified.j).append(",\"orientation\":")
                .append(firstQualified.orientation).append(",\"length\":")
                .append(firstQualified.length).append(",\"bends\":")
                .append(firstQualified.bends).append('}');
            return json.append("}}").toString();
        }
    }

    static final class EdgeResult {
        final int fromCell, toCell;
        final boolean allowed;
        private EdgeResult(int fromCell, int toCell, boolean allowed) {
            this.fromCell = fromCell; this.toCell = toCell; this.allowed = allowed;
        }
    }

    static final class CandidateResult {
        final int i, j, orientation, length, bends, saving;
        final boolean allowed;
        private CandidateResult(int i, int j, int orientation, int length,
                int bends, int saving, boolean allowed) {
            this.i = i; this.j = j; this.orientation = orientation;
            this.length = length; this.bends = bends; this.saving = saving;
            this.allowed = allowed;
        }
    }

    private static final class EdgeDraft {
        final int fromCell, toCell;
        Boolean allowed;
        EdgeDraft(int fromCell, int toCell) { this.fromCell = fromCell; this.toCell = toCell; }
    }

    private static final class CandidateDraft {
        final int i, j, orientation, length, saving;
        int bends;
        boolean allowed = true;
        ArrayList<Long> edges;
        CandidateDraft(int i, int j, int orientation, int length, int saving) {
            this.i = i; this.j = j; this.orientation = orientation;
            this.length = length; this.saving = saving;
        }
    }

    private static int direction(int[] from, int[] to) {
        if (to[0] > from[0]) return 1;
        if (to[0] < from[0]) return 3;
        if (to[1] > from[1]) return 2;
        if (to[1] < from[1]) return 0;
        throw new IllegalArgumentException("P07 shortcut path has a zero-length edge");
    }

    private static int gridSteps(int first, int second, int pitch) {
        long distance = Math.abs((long) first - second);
        if (distance % pitch != 0 || distance / pitch > Integer.MAX_VALUE)
            throw new IllegalStateException("P07 shortcut segment is outside the grid domain");
        return (int) (distance / pitch);
    }

    private static int coordinate(int origin, int index, int pitch) {
        long value = (long) origin + (long) index * pitch;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalStateException("P07 shortcut cell is outside the coordinate domain");
        return (int) value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte valueByte : digest) {
                int unsigned = valueByte & 255;
                if (unsigned < 16) result.append('0');
                result.append(Integer.toHexString(unsigned));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required for P07 shortcut-query identity", unavailable);
        }
    }

    /* Focused native contract. The production diagnostic suite drives this same helper. */
    static void runContracts() {
        int before = Contract.checks;
        resumableAlignedThresholdFixture();
        realRouterLAndForeignCopperCanaries();
        directionalReachedEndpointCanary();
        interiorSameNetCopperGoalCanary();
        defensiveCopyAndSourceMutationCanaries();
        System.out.println("PASS: P07 shortcut query contracts assertions=" + (Contract.checks - before));
    }

    private static void resumableAlignedThresholdFixture() {
        int[][] path = thresholdPath();
        Fixture fixture = new Fixture("P07_SHORTCUT_ALIGNED", path, false, true);
        PcbPadPlacement[] reached = new PcbPadPlacement[] {fixture.layout.getPad("REACHED.1")};
        Context context = fixture.context(path, reached);
        reached[0] = null;
        Cursor one = begin(context);
        drain(one, 1);
        Result single = one.result();
        Cursor seven = begin(context);
        drain(seven, 7);
        Result small = seven.result();
        Cursor full = begin(context);
        drain(full, 32768);
        Result large = full.result();
        Contract.check(single.minimumSaving == 20 && single.maximumLength == 120 &&
                single.candidateCount == 45 && single.replacementEdgeVisits == 260 &&
                single.distinctDirectedEdges == 36 &&
                "4667917dc35fd5bbea5403921ea3c0db3c1c383f71ec09c4c2674e328c6d94b6".
                    equals(single.candidateTupleSha256),
            "literal aligned fixture preserves exact20 saving/length boundaries and its 45-candidate family");
        Contract.check(single.queryQualifiedCount == 45 && single.minimumQualifiedLength != null &&
                single.minimumQualifiedLength.intValue() == 40 &&
                single.firstQualified.i == 0 && single.firstQualified.j == 10 &&
                single.firstQualified.orientation == 0,
            "all45 replacement candidates and the source path qualify under the actual Router");
        int aligned = 0, exact20 = 0;
        for (CandidateResult candidate : single.candidates) {
            if (candidate.i == 0 && candidate.j == 14) {
                aligned++;
                Contract.check(candidate.orientation == 0,
                    "aligned endpoint pair has only canonical orientation zero");
            }
            if (candidate.saving == 20 && candidate.length == 120) exact20++;
        }
        Contract.check(aligned == 1 && exact20 == 17,
            "aligned pairs deduplicate and exact20 candidates meet both inclusive bounds");
        Contract.check(single.queryCalls == 42 && single.originalPathQueries == 6 &&
                single.reusedEdgeVisits == 224 &&
                single.copyEntries > 0 && single.comparisonEntries > 0 &&
                single.workSteps >= single.copyEntries + single.comparisonEntries + single.queryCalls,
            "family edges and three uncovered source edges use the actual clone with counted copy/comparison work");
        Contract.check(sameSemantics(single, small) && sameSemantics(single, large),
            "chunk1, chunk7 and chunk32768 preserve the exact diagnostic result");
        Contract.check(one.lastWork() > 0 && seven.lastWork() > 0 && full.lastWork() > 0,
            "completed resumable advances report their actual bounded work");
        int[][] expectedDirectedEdges = new int[][] {
            {600,601}, {600,659}, {601,602}, {602,603}, {603,604}, {604,663},
            {659,600}, {659,660}, {659,718}, {660,661}, {661,662}, {662,663},
            {663,604}, {663,722}, {718,659}, {718,719}, {718,777}, {719,720},
            {720,721}, {721,722}, {722,663}, {722,781}, {777,718}, {777,778},
            {777,836}, {778,779}, {779,780}, {780,781}, {781,722}, {781,840},
            {836,777}, {836,837}, {837,838}, {838,839}, {839,840}, {840,781}
        };
        Contract.check(exactAllowedDirectedEdges(single, expectedDirectedEdges) &&
                sortedEdges(single) && !edge(single, 601, 660) &&
                !edge(single, 660, 601),
            "independent literal directed edge set is exact, sorted and fully allowed");
        Contract.check(single.bodyJson().contains("\"goalVerified\":true") &&
                single.bodyJson().contains("\"liveAfterVerified\":true") &&
                single.bodyJson().contains("\"originalPathVerified\":true"),
            "completed wire body exposes verified source, goal and actual-path positive control");
    }

    private static void realRouterLAndForeignCopperCanaries() {
        Fixture open = lFixture("P07_SHORTCUT_L_OPEN");
        PcbPadPlacement start = open.layout.getPad("START.1");
        PcbPadPlacement root = open.layout.getPad("ROOT.1");
        PcbNetRouter.Router openRouter = open.router(PcbCopperLayer.TOP);
        Contract.check(Math.abs(root.getX() - start.getX()) == 30 &&
                Math.abs(root.getY() - start.getY()) == 30,
            "literal L fixture contains six directed grid edges per orientation");
        Contract.check(routeL(openRouter, start, root, false) &&
                routeL(openRouter, start, root, true) &&
                openRouter.permitsLayerStep(220, 210, 230, 210, "N", start, root, null) &&
                openRouter.permitsLayerStep(210, 220, 210, 230, "N", start, root, null),
            "both literal monotone L replacements and their distinct edges pass the actual Router predicates");
        Result openResult = diagnose(open, openRouter);
        CandidateResult openX = candidate(openResult, 0, open.path.length - 1, 0);
        CandidateResult openY = candidate(openResult, 0, open.path.length - 1, 1);
        Contract.check(openX != null && openY != null && openX.allowed && openY.allowed &&
                edge(openResult, 10 * open.width + 11, 10 * open.width + 12) &&
                edge(openResult, 11 * open.width + 10, 12 * open.width + 10),
            "both actual-router L replacements remain qualified in the shortcut family");

        Fixture oneBlocked = lFixture("P07_SHORTCUT_L_ONE_BLOCKED");
        PcbPadPlacement oneStart = oneBlocked.layout.getPad("START.1");
        PcbPadPlacement oneRoot = oneBlocked.layout.getPad("ROOT.1");
        PcbNetRouter.Router oneRouter = oneBlocked.router(PcbCopperLayer.TOP);
        occupy(oneRouter, 240, 210);
        Contract.check(!routeL(oneRouter, oneStart, oneRoot, false) &&
                routeL(oneRouter, oneStart, oneRoot, true) &&
                !oneRouter.permitsLayerStep(230, 210, 240, 210, "N", oneStart, oneRoot, null) &&
                oneRouter.permitsLayerStep(210, 220, 210, 230, "N", oneStart, oneRoot, null),
            "one foreign-copper cell blocks only the X-first actual L replacement edge");
        Result oneBlockedResult = diagnose(oneBlocked, oneRouter);
        CandidateResult oneX = candidate(oneBlockedResult, 0, oneBlocked.path.length - 1, 0);
        CandidateResult oneY = candidate(oneBlockedResult, 0, oneBlocked.path.length - 1, 1);
        Contract.check(oneX != null && oneY != null && !oneX.allowed && oneY.allowed &&
                !edge(oneBlockedResult, 10 * oneBlocked.width + 12,
                    10 * oneBlocked.width + 13),
            "shortcut candidate conjunction preserves the literal forbidden X-first edge and allowed Y-first route");

        Fixture bothBlocked = lFixture("P07_SHORTCUT_L_BOTH_BLOCKED");
        PcbPadPlacement bothStart = bothBlocked.layout.getPad("START.1");
        PcbPadPlacement bothRoot = bothBlocked.layout.getPad("ROOT.1");
        PcbNetRouter.Router bothRouter = bothBlocked.router(PcbCopperLayer.TOP);
        occupy(bothRouter, 240, 210);
        occupy(bothRouter, 210, 240);
        Contract.check(!routeL(bothRouter, bothStart, bothRoot, false) &&
                !routeL(bothRouter, bothStart, bothRoot, true) &&
                !bothRouter.permitsLayerStep(230, 210, 240, 210, "N", bothStart, bothRoot, null) &&
                !bothRouter.permitsLayerStep(210, 230, 210, 240, "N", bothStart, bothRoot, null),
            "independent foreign-copper owners block the exact directed edges in both L replacements");
        Result bothBlockedResult = diagnose(bothBlocked, bothRouter);
        CandidateResult bothX = candidate(bothBlockedResult, 0, bothBlocked.path.length - 1, 0);
        CandidateResult bothY = candidate(bothBlockedResult, 0, bothBlocked.path.length - 1, 1);
        Contract.check(bothX != null && bothY != null && !bothX.allowed && !bothY.allowed &&
                !edge(bothBlockedResult, 10 * bothBlocked.width + 12,
                    10 * bothBlocked.width + 13) &&
                !edge(bothBlockedResult, 12 * bothBlocked.width + 10,
                    13 * bothBlocked.width + 10),
            "both shortcut candidate conjunctions reject their literal foreign-copper edges");
    }

    private static void directionalReachedEndpointCanary() {
        ReachedFixture fixture = new ReachedFixture();
        PcbPadPlacement start = fixture.layout.getPad("TREE_C.1");
        PcbPadPlacement root = fixture.layout.getPad("TREE_A.1");
        PcbPadPlacement target = fixture.layout.getPad("TREE_B.1");
        Contract.check(target.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
                target.getEscapeLength() >= 20 && target.getEscapeDx() != 0,
            "directional endpoint canary uses the actual escaped PTH connector terminal");
        PcbPadPlacement[] reached = new PcbPadPlacement[] {target};
        int towardX = target.getX() + target.getEscapeDx() * 20;
        int towardY = target.getY() + target.getEscapeDy() * 20;
        int nearX = target.getX() + target.getEscapeDx() * 10;
        int nearY = target.getY() + target.getEscapeDy() * 10;
        PcbNetRouter.Router router = fixture.router(PcbCopperLayer.TOP);
        Contract.check(router.permitsLayerStep(towardX, towardY, nearX, nearY,
                "TREE", start, root, reached) &&
                !router.permitsLayerStep(nearX, nearY, towardX, towardY,
                "TREE", start, root, reached),
            "the exact reached-PTH authority permits approach and rejects reverse escape traversal");
        PcbNetRouter.Router foreign = fixture.router(PcbCopperLayer.TOP);
        Vector<Point> point = new Vector<Point>(); point.add(new Point(nearX, nearY));
        foreign.occupyLayerPath(point, "BLOCK");
        Contract.check(!foreign.permitsLayerStep(towardX, towardY, nearX, nearY,
                "TREE", start, root, reached),
            "reached-PTH endpoint permission does not waive foreign copper ownership");
    }

    private static void interiorSameNetCopperGoalCanary() {
        int[][] path = interiorCopperPath();
        Fixture fixture = new Fixture("P07_SHORTCUT_INTERIOR_COPPER_GOAL", path, false, false);
        PcbPadPlacement root = fixture.layout.getPad(fixture.rootPadId);
        PcbPadPlacement reachedPad = fixture.layout.getPad("REACHED.1");
        PcbNetRouter.Router router = fixture.router(PcbCopperLayer.TOP);
        PcbTraceGeometry tree = router.route("N", root.getPadId(), reachedPad.getPadId());
        fixture.layout.addTrace(tree);
        fixture.layout.validateAgainst(fixture.board);
        int goalX = 450, goalY = 400;
        int goalCell = (goalY - fixture.originY) / fixture.pitch * fixture.width +
            (goalX - fixture.originX) / fixture.pitch;
        boolean[] goals = routerGoals(fixture, router, root, reachedPad);
        Contract.check(router.ownsLayerGridCell((goalX - fixture.originX) / fixture.pitch,
                (goalY - fixture.originY) / fixture.pitch, "N") &&
                goals[PcbCopperLayer.TOP.ordinal() * fixture.width * fixture.height + goalCell] &&
                path[path.length - 1][0] == goalX && path[path.length - 1][1] == goalY &&
                (goalX != root.getX() || goalY != root.getY()) &&
                (goalX != reachedPad.getX() || goalY != reachedPad.getY()),
            "the literal goal is interior same-net copper, distinct from root and reached PTH pads");
        Context context = fixture.context(path, new PcbPadPlacement[] {reachedPad}, router,
            PcbCopperLayer.TOP.ordinal(), goals);
        Cursor cursor = begin(context);
        drain(cursor, 7);
        Result result = cursor.result();
        CandidateResult directReplacement = candidate(result, 0, path.length - 1, 1);
        Contract.check(result.bodyJson().contains("\"originalPathVerified\":true") &&
                directReplacement != null && directReplacement.allowed &&
                result.queryQualifiedCount > 0 &&
                result.queryCalls == result.distinctDirectedEdges + result.originalPathQueries,
            "actual-router source and replacement edges qualify when the found end is an interior copper goal");
    }

    private static boolean[] routerGoals(Fixture fixture, PcbNetRouter.Router router,
            PcbPadPlacement root, PcbPadPlacement reached) {
        int cells = fixture.width * fixture.height;
        boolean[] goals = new boolean[cells * 2];
        int rootCell = (root.getY() - fixture.originY) / fixture.pitch * fixture.width +
            (root.getX() - fixture.originX) / fixture.pitch;
        goals[PcbCopperLayer.TOP.ordinal() * cells + rootCell] = true;
        for (int y = 0; y < fixture.height; y++)
            for (int x = 0; x < fixture.width; x++)
                if (router.ownsLayerGridCell(x, y, "N"))
                    goals[PcbCopperLayer.TOP.ordinal() * cells + y * fixture.width + x] = true;
        if (PcbCopperAccess.hasCopper(reached, PcbCopperLayer.TOP)) {
            int cell = (reached.getY() - fixture.originY) / fixture.pitch * fixture.width +
                (reached.getX() - fixture.originX) / fixture.pitch;
            goals[PcbCopperLayer.TOP.ordinal() * cells + cell] = true;
        }
        return goals;
    }

    private static void defensiveCopyAndSourceMutationCanaries() {
        int[][] path = thresholdPath();
        Fixture copied = new Fixture("P07_SHORTCUT_DEFENSIVE", path, false, true);
        boolean[] goals = copied.goalMask(path);
        PcbPadPlacement[] reached = new PcbPadPlacement[] {copied.layout.getPad("REACHED.1")};
        Context context = new Context(copied.layout, copied.router(), "N",
            copied.layout.getPad(copied.startPadId), copied.layout.getPad(copied.rootPadId), reached,
            goals, copied.width, copied.height, copied.originX, copied.originY,
            copied.pitch, 0, path);
        path[0][0] = -999;
        int endX = (250 - copied.originX) / copied.pitch;
        int endY = (210 - copied.originY) / copied.pitch;
        goals[endY * copied.width + endX] = false;
        reached[0] = null;
        Cursor copyCursor = begin(context);
        drain(copyCursor, 7);
        Contract.check(copyCursor.result().candidateCount == 45 &&
                copyCursor.result().queryQualifiedCount == 45,
            "Context defensively copies path rows, goal mask and reached PTH identity array");

        // This canary mutates only the origin cell after seven advances, once it
        // has been copied. It proves a later check rejects that mutation; it does
        // not cover occupancy changes in cells not yet copied during baseline
        // construction.
        Fixture changed = new Fixture("P07_SHORTCUT_SOURCE_MUTATION", thresholdPath(), false, true);
        PcbNetRouter.Router live = changed.router();
        Context mutationContext = changed.context(changed.path, new PcbPadPlacement[0], live);
        Cursor mutation = begin(mutationContext);
        mutation.advance(7);
        occupy(live, changed.originX, changed.originY);
        boolean rejected = false;
        try { drain(mutation, 32768); }
        catch (IllegalStateException expected) { rejected = true; }
        Contract.check(rejected && !mutation.isComplete(),
            "live fixed-graph mutation after cursor creation rejects the result before completion");
        boolean hidden = false;
        try { mutation.result(); } catch (IllegalStateException expected) { hidden = true; }
        Contract.check(hidden, "a failed source-stability check cannot expose a partial result");
    }

    private static boolean sameSemantics(Result a, Result b) {
        if (a.minimumSaving != b.minimumSaving || a.maximumLength != b.maximumLength ||
                a.candidateCount != b.candidateCount ||
                !a.candidateTupleSha256.equals(b.candidateTupleSha256) ||
                a.replacementEdgeVisits != b.replacementEdgeVisits ||
                a.distinctDirectedEdges != b.distinctDirectedEdges ||
                a.queryQualifiedCount != b.queryQualifiedCount ||
                a.queryCalls != b.queryCalls || a.originalPathQueries != b.originalPathQueries ||
                !equal(a.minimumQualifiedLength, b.minimumQualifiedLength) ||
                !sameCandidate(a.firstQualified, b.firstQualified) ||
                a.edges.size() != b.edges.size() || a.candidates.size() != b.candidates.size())
            return false;
        for (int i = 0; i < a.edges.size(); i++) {
            EdgeResult x = a.edges.get(i), y = b.edges.get(i);
            if (x.fromCell != y.fromCell || x.toCell != y.toCell || x.allowed != y.allowed) return false;
        }
        for (int i = 0; i < a.candidates.size(); i++) {
            CandidateResult x = a.candidates.get(i), y = b.candidates.get(i);
            if (x.i != y.i || x.j != y.j || x.orientation != y.orientation ||
                    x.length != y.length || x.bends != y.bends || x.saving != y.saving ||
                    x.allowed != y.allowed) return false;
        }
        return true;
    }

    private static boolean equal(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean sameCandidate(CandidateResult a, CandidateResult b) {
        return a == null ? b == null : b != null && a.i == b.i && a.j == b.j &&
            a.orientation == b.orientation && a.length == b.length && a.bends == b.bends &&
            a.saving == b.saving && a.allowed == b.allowed;
    }

    private static boolean exactAllowedDirectedEdges(Result result, int[][] expected) {
        if (result.edges.size() != expected.length) return false;
        for (int index = 0; index < expected.length; index++) {
            EdgeResult actual = result.edges.get(index);
            if (actual.fromCell != expected[index][0] || actual.toCell != expected[index][1] ||
                    !actual.allowed) return false;
        }
        return true;
    }
    private static boolean sortedEdges(Result result) {
        for (int i = 1; i < result.edges.size(); i++) {
            EdgeResult previous = result.edges.get(i - 1), current = result.edges.get(i);
            if (current.fromCell < previous.fromCell ||
                    (current.fromCell == previous.fromCell && current.toCell <= previous.toCell))
                return false;
        }
        return true;
    }

    private static boolean edge(Result result, int from, int to) {
        for (EdgeResult edge : result.edges)
            if (edge.fromCell == from && edge.toCell == to) return edge.allowed;
        return false;
    }

    private static CandidateResult candidate(Result result, int i, int j, int orientation) {
        for (CandidateResult candidate : result.candidates)
            if (candidate.i == i && candidate.j == j && candidate.orientation == orientation)
                return candidate;
        return null;
    }

    private static Result diagnose(Fixture fixture, PcbNetRouter.Router router) {
        int layer = PcbCopperLayer.TOP.ordinal();
        int[][] rows = copyPath(fixture.path);
        for (int i = 0; i < rows.length; i++) rows[i][2] = layer;
        Cursor cursor = begin(fixture.context(rows,
            new PcbPadPlacement[0], router, layer));
        drain(cursor, 7);
        return cursor.result();
    }

    private static void drain(Cursor cursor, int chunk) {
        int guard = 0;
        while (!cursor.isComplete()) {
            boolean done = cursor.advance(chunk);
            if (cursor.lastWork() < 1 || cursor.lastWork() > chunk)
                throw new AssertionError("P07 shortcut-query advance exceeded its work chunk");
            if (done != cursor.isComplete())
                throw new AssertionError("P07 shortcut-query completion receipt is inconsistent");
            if (++guard > 1000000)
                throw new AssertionError("P07 shortcut-query fixture exceeded its resume guard");
        }
    }

    private static boolean routeL(PcbNetRouter.Router router, PcbPadPlacement start,
            PcbPadPlacement root, boolean yFirst) {
        int x = start.getX(), y = start.getY(), targetX = root.getX(), targetY = root.getY();
        if (!yFirst) {
            while (x != targetX) {
                int nextX = x + (targetX > x ? 10 : -10);
                if (!router.permitsLayerStep(x, y, nextX, y, "N", start, root, null)) return false;
                x = nextX;
            }
            while (y != targetY) {
                int nextY = y + (targetY > y ? 10 : -10);
                if (!router.permitsLayerStep(x, y, x, nextY, "N", start, root, null)) return false;
                y = nextY;
            }
        } else {
            while (y != targetY) {
                int nextY = y + (targetY > y ? 10 : -10);
                if (!router.permitsLayerStep(x, y, x, nextY, "N", start, root, null)) return false;
                y = nextY;
            }
            while (x != targetX) {
                int nextX = x + (targetX > x ? 10 : -10);
                if (!router.permitsLayerStep(x, y, nextX, y, "N", start, root, null)) return false;
                x = nextX;
            }
        }
        return true;
    }

    private static void occupy(PcbNetRouter.Router router, int x, int y) {
        Vector<Point> point = new Vector<Point>(); point.add(new Point(x, y));
        router.occupyLayerPath(point, "BLOCK");
    }

    private static final class Contract {
        static int checks;
        static void check(boolean value, String message) {
            checks++;
            if (!value) throw new AssertionError(message);
        }
    }

    private static final class Fixture {
        final Rectangle outline = new Rectangle(100, 100, 600, 500);
        final TroubleshootBoard board;
        final PcbBoardLayout layout = new PcbBoardLayout(900, 700, outline,
            new Rectangle(720, 120, 150, 520));
        final int width = (outline.width - 20) / 10 + 1;
        final int height = (outline.height - 20) / 10 + 1;
        final int originX = outline.x + 10, originY = outline.y + 10, pitch = 10;
        final int[][] path;
        final boolean directionalTarget;
        String startPadId = "START.1", rootPadId = "ROOT.1";

        Fixture(String id, int[][] path, boolean directionalTarget, boolean rootAtEnd) {
            this.board = new TroubleshootBoard(id);
            this.path = copyPath(path);
            this.directionalTarget = directionalTarget;
            board.addNet(new BoardNet("N"));
            board.addNet(new BoardNet("BLOCK", BoardNet.RoutingRole.RETURN));
            addPoint("START", "N", path[0][0], path[0][1], PcbBoardSide.BOTTOM);
            if (directionalTarget) {
                addPoint("ROOT", "N", 450, 350, PcbBoardSide.BOTTOM);
                addConnector("TARGET", "N", path[path.length - 1][0],
                    path[path.length - 1][1], PcbBoardSide.TOP);
            } else if (rootAtEnd) {
                addPoint("ROOT", "N", path[path.length - 1][0],
                    path[path.length - 1][1], PcbBoardSide.BOTTOM);
            } else if (!rootAtEnd) {
                addPoint("ROOT", "N", 450, 350, PcbBoardSide.BOTTOM);
            }
            addPoint("REACHED", "N", 450, 450, PcbBoardSide.BOTTOM);
            addPoint("BLOCK_OWNER", "BLOCK", 500, 500, PcbBoardSide.BOTTOM);
            Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
            for (String component : board.getComponentIds())
                parts.add(new PcbPlacementConstraints.Part(component, "p07",
                    "shortcut query fixture", "p07", PcbPlacementConstraints.Anchor.NONE, 16));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
                PcbTraceWidthProfile.UNIFORM));
            board.validate();
            layout.validateAgainst(board);
        }

        Context context(int[][] rows, PcbPadPlacement[] reached) {
            return context(rows, reached, router());
        }
        Context context(int[][] rows, PcbPadPlacement[] reached, PcbNetRouter.Router router) {
            return context(rows, reached, router, PcbCopperLayer.TOP.ordinal());
        }
        Context context(int[][] rows, PcbPadPlacement[] reached, PcbNetRouter.Router router,
                int layer) {
            return context(rows, reached, router, layer, goalMask(rows, layer));
        }
        Context context(int[][] rows, PcbPadPlacement[] reached, PcbNetRouter.Router router,
                int layer, boolean[] goals) {
            return new Context(layout, router, "N", layout.getPad(startPadId),
                layout.getPad(rootPadId), reached, goals, width, height,
                originX, originY, pitch, layer, rows);
        }
        boolean[] goalMask(int[][] rows) {
            return goalMask(rows, PcbCopperLayer.TOP.ordinal());
        }
        boolean[] goalMask(int[][] rows, int layer) {
            boolean[] goals = new boolean[width * height * 2];
            int x = (rows[rows.length - 1][0] - originX) / pitch;
            int y = (rows[rows.length - 1][1] - originY) / pitch;
            goals[layer * width * height + y * width + x] = true;
            return goals;
        }
        PcbNetRouter.Router router() { return router(PcbCopperLayer.TOP); }
        PcbNetRouter.Router router(PcbCopperLayer layer) {
            return new PcbNetRouter.Router(layout, board, outline, 0,
                P06FactoryLinkFixtures.OBSERVER, layer);
        }
        private void addPoint(String id, String net, int x, int y, PcbBoardSide side) {
            PhysicalPackage physical = P06FactoryLinkFixtures.TEST_POINT;
            PcbPackagePose reference = new PcbPackagePose(100, 100,
                PcbRotation.DEG_0, side);
            Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
            addPackage(id, physical, new String[] {net}, new PcbPackagePose(
                100 + x - local.x, 100 + y - local.y, PcbRotation.DEG_0, side));
        }
        private void addConnector(String id, String net, int x, int y, PcbBoardSide side) {
            PhysicalPackage physical = PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
            PcbPackagePose reference = new PcbPackagePose(100, 100,
                PcbRotation.DEG_0, side);
            Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
            addPackage(id, physical, new String[] {net, "N"}, new PcbPackagePose(
                100 + x - local.x, 100 + y - local.y, PcbRotation.DEG_0, side));
        }
        private void addPackage(String id, PhysicalPackage physical, String[] nets,
                PcbPackagePose pose) {
            BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
            board.addComponent(component);
            Vector<String> terminals = physical.getTerminalIds();
            for (int i = 0; i < terminals.size(); i++)
                board.addPad(new BoardPad(id + "." + terminals.get(i), id,
                    terminals.get(i), nets[i]));
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose,
                physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
    }

    private static Fixture lFixture(String id) {
        int[][] path = new int[][] {
            {210, 210, 0}, {200, 210, 0}, {190, 210, 0}, {190, 220, 0},
            {190, 230, 0}, {190, 240, 0}, {190, 250, 0}, {190, 260, 0},
            {200, 260, 0}, {210, 260, 0}, {220, 260, 0}, {230, 260, 0},
            {230, 250, 0}, {230, 240, 0}, {240, 240, 0}
        };
        return new Fixture(id, path, false, true);
    }

    private static int[][] thresholdPath() {
        return new int[][] {
            {210, 210, 0}, {210, 220, 0}, {210, 230, 0}, {210, 240, 0},
            {210, 250, 0}, {210, 260, 0}, {220, 260, 0}, {230, 260, 0},
            {240, 260, 0}, {250, 260, 0}, {250, 250, 0}, {250, 240, 0},
            {250, 230, 0}, {250, 220, 0}, {250, 210, 0}
        };
    }

    private static int[][] interiorCopperPath() {
        ArrayList<int[]> rows = new ArrayList<int[]>();
        rows.add(new int[] {210, 210, PcbCopperLayer.TOP.ordinal()});
        for (int y = 220; y <= 500; y += 10)
            rows.add(new int[] {210, y, PcbCopperLayer.TOP.ordinal()});
        for (int x = 220; x <= 400; x += 10)
            rows.add(new int[] {x, 500, PcbCopperLayer.TOP.ordinal()});
        for (int y = 490; y >= 400; y -= 10)
            rows.add(new int[] {400, y, PcbCopperLayer.TOP.ordinal()});
        for (int x = 410; x <= 450; x += 10)
            rows.add(new int[] {x, 400, PcbCopperLayer.TOP.ordinal()});
        return rows.toArray(new int[0][3]);
    }

    private static int[][] copyPath(int[][] source) {
        int[][] copy = new int[source.length][3];
        for (int i = 0; i < source.length; i++)
            System.arraycopy(source[i], 0, copy[i], 0, 3);
        return copy;
    }

    private static final class ReachedFixture {
        static final Rectangle OUTLINE = new Rectangle(100, 100, 800, 700);
        final TroubleshootBoard board = new TroubleshootBoard("P07_SHORTCUT_REACHED_ENDPOINT");
        final PcbBoardLayout layout = new PcbBoardLayout(1200, 900, OUTLINE,
            new Rectangle(950, 120, 200, 700));

        ReachedFixture() {
            board.addNet(new BoardNet("BLOCK", BoardNet.RoutingRole.RETURN));
            board.addNet(new BoardNet("TREE", BoardNet.RoutingRole.HIGH_CURRENT));
            for (String id : new String[] {"A", "B", "C"}) board.addNet(new BoardNet("90_AUX_" + id));
            addPoint("BLOCK_A", "BLOCK", 500, 110);
            addPoint("BLOCK_B", "BLOCK", 500, 790);
            addHeader("TREE_A", "90_AUX_A", 220, 400);
            addHeader("TREE_B", "90_AUX_B", 420, 400);
            addHeader("TREE_C", "90_AUX_C", 760, 400);
            Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
            for (String component : board.getComponentIds())
                parts.add(new PcbPlacementConstraints.Part(component, "p07",
                    "reached PTH fixture", "p07", PcbPlacementConstraints.Anchor.NONE, 16));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
                PcbTraceWidthProfile.UNIFORM));
            board.validate();
            layout.validateAgainst(board);
        }
        PcbNetRouter.Router router(PcbCopperLayer layer) {
            return new PcbNetRouter.Router(layout, board, OUTLINE, 0,
                P06FactoryLinkFixtures.OBSERVER, layer);
        }
        private void addHeader(String id, String auxiliaryNet, int x, int y) {
            PhysicalPackage physical = PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
            BoardComponent component = new BoardComponent(id, "STRUCTURAL", physical);
            board.addComponent(component);
            board.addPad(new BoardPad(id + ".1", id, "1", "TREE"));
            board.addPad(new BoardPad(id + ".2", id, "2", auxiliaryNet));
            PcbPackagePose pose = new PcbPackagePose(x - 90, y - 40,
                PcbRotation.DEG_0, PcbBoardSide.TOP);
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component,
                pose, physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
        private void addPoint(String id, String net, int x, int y) {
            PhysicalPackage physical = P06FactoryLinkFixtures.TEST_POINT;
            BoardComponent component = new BoardComponent(id, "STRUCTURAL", physical);
            board.addComponent(component);
            board.addPad(new BoardPad(id + ".1", id, "1", net));
            PcbPackagePose reference = new PcbPackagePose(100, 100,
                PcbRotation.DEG_0, PcbBoardSide.TOP);
            Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
            PcbPackagePose pose = new PcbPackagePose(100 + x - local.x,
                100 + y - local.y, PcbRotation.DEG_0, PcbBoardSide.TOP);
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component,
                pose, physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
    }
}
