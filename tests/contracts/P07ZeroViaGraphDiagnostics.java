package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Vector;

/** Test-only two-face ordinary-edge graph capture over fixed actual Router predicates. */
final class P07ZeroViaGraphDiagnostics {
    private static final int MAX_PATH_NODES = 66;
    private static final int MAX_CELLS = 250000;
    private static final int MAX_ADVANCE_WORK = 32768;
    private static final int TOP = 0;
    private static final int BOTTOM = 1;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DY = {-1, 0, 1, 0};
    private static final String HEX = "0123456789abcdef";

    private P07ZeroViaGraphDiagnostics() { }

    /**
     * Binds the actual two face routers and BranchSearch goal union. The caller
     * must keep every supplied source object quiescent from begin through the
     * cursor's completion. Large goal-mask copying is deferred to the cursor;
     * the bounded physical path and reached-pad identities are copied here.
     */
    static final class Context {
        private final PcbBoardLayout layout;
        private final PcbNetRouter.Router[] faces;
        private final String net;
        private final PcbPadPlacement start;
        private final PcbPadPlacement root;
        private final PcbPadPlacement[] reached;
        private final boolean[] sourceGoalMask;
        final int width, height, originX, originY, pitch, initialLayerMask;
        private final int[][] pathPhysical;
        final int cells;

        Context(PcbBoardLayout layout, PcbNetRouter.Router[] faces, String net,
                PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
                boolean[] actualGoalMask, int width, int height, int originX, int originY,
                int pitch, int initialLayerMask, int[][] pathPhysical) {
            if (layout == null || faces == null || faces.length != 2 ||
                    faces[TOP] == null || faces[BOTTOM] == null || net == null || net.length() == 0 ||
                    start == null || root == null || reached == null || actualGoalMask == null ||
                    pathPhysical == null || width < 1 || height < 1 || pitch < 1 ||
                    (initialLayerMask & ~3) != 0 || initialLayerMask == 0 ||
                    pathPhysical.length < 2 || pathPhysical.length > MAX_PATH_NODES)
                throw new IllegalArgumentException("Invalid P07 zero-via graph context");
            long cellCount = (long) width * (long) height;
            if (cellCount < 1 || cellCount > MAX_CELLS || actualGoalMask.length != cellCount * 2L)
                throw new IllegalArgumentException("P07 zero-via graph grid or goal shape is outside its bound");
            long lastX = (long) originX + (long) (width - 1) * pitch;
            long lastY = (long) originY + (long) (height - 1) * pitch;
            if (lastX > Integer.MAX_VALUE || lastX < Integer.MIN_VALUE ||
                    lastY > Integer.MAX_VALUE || lastY < Integer.MIN_VALUE)
                throw new IllegalArgumentException("P07 zero-via graph grid coordinates overflow");
            if (layout.getPad(start.getPadId()) != start || layout.getPad(root.getPadId()) != root)
                throw new IllegalArgumentException("P07 zero-via graph endpoint identity differs from layout");

            this.reached = new PcbPadPlacement[reached.length];
            for (int i = 0; i < reached.length; i++) {
                PcbPadPlacement pad = reached[i];
                if (pad == null || layout.getPad(pad.getPadId()) != pad ||
                        pad.getAttachment() != PcbTerminalAttachment.PLATED_THROUGH_HOLE || pad == root)
                    throw new IllegalArgumentException("Invalid P07 zero-via reached-PTH authority");
                for (int previous = 0; previous < i; previous++)
                    if (this.reached[previous] == pad)
                        throw new IllegalArgumentException("Duplicate P07 zero-via reached-PTH authority");
                this.reached[i] = pad;
            }

            this.layout = layout;
            this.faces = new PcbNetRouter.Router[] {faces[TOP], faces[BOTTOM]};
            this.net = net;
            this.start = start;
            this.root = root;
            this.sourceGoalMask = actualGoalMask;
            this.width = width;
            this.height = height;
            this.originX = originX;
            this.originY = originY;
            this.pitch = pitch;
            this.initialLayerMask = initialLayerMask;
            this.cells = (int) cellCount;
            this.pathPhysical = new int[pathPhysical.length][3];
            for (int i = 0; i < pathPhysical.length; i++) {
                int[] row = pathPhysical[i];
                if (row == null || row.length != 3 || (row[2] != TOP && row[2] != BOTTOM) ||
                        cell(row[0], row[1], originX, originY, width, height, pitch) < 0)
                    throw new IllegalArgumentException("Invalid P07 zero-via physical path row");
                System.arraycopy(row, 0, this.pathPhysical[i], 0, 3);
                if (i > 0) {
                    long step = Math.abs((long) row[0] - pathPhysical[i - 1][0]) +
                        Math.abs((long) row[1] - pathPhysical[i - 1][1]);
                    if (step != pitch || row[2] != pathPhysical[i - 1][2])
                        throw new IllegalArgumentException("P07 zero-via source path must use same-face adjacent edges");
                }
            }
            if (this.pathPhysical[0][0] != start.getX() ||
                    this.pathPhysical[0][1] != start.getY())
                throw new IllegalArgumentException("P07 zero-via source path does not begin at its actual start pad");
        }
    }

    static Cursor begin(Context context) {
        return new Cursor(context);
    }

    /** Resumable full ordinary-edge graph capture. No via or shortest-path edges are added. */
    static final class Cursor {
        private static final int COPY_BASELINE = 0;
        private static final int COMPARE_BASELINE = 1;
        private static final int COPY_QUERY = 2;
        private static final int COMPARE_QUERY = 3;
        private static final int COPY_GOALS = 4;
        private static final int SCAN = 5;
        private static final int ENCODE = 6;
        private static final int VERIFY_PATH = 7;
        private static final int VERIFY_AFTER = 8;
        private static final int COMPLETE = 9;

        private final Context context;
        private final String geometryBefore;
        private final String geometrySha256;
        private final String[] effectsBefore = new String[2];
        private final PcbNetRouter.Router[] pristine = new PcbNetRouter.Router[2];
        private final PcbNetRouter.Router[] query = new PcbNetRouter.Router[2];
        private final int[] copyWatermark = new int[2];
        private final boolean[] goalMask;
        private final char[] topChars;
        private final char[] bottomChars;
        private final char[] goalChars;

        private PcbNetRouter.Router.FixedGraphCopyTask copyTask;
        private PcbNetRouter.Router.FixedGraphComparisonTask comparisonTask;
        private int stage = COPY_BASELINE;
        private int face;
        private int goalIndex;
        private int scanFace;
        private int scanCell;
        private int scanDirection;
        private int encodeCell;
        private int encodeStream;
        private int pathEdge;
        private int afterFace;
        private int lastWork;
        private int advanceCalls;
        private int ordinaryQueries;
        private int goalCells;
        private int encodedCells;
        private long workSteps;
        private long copyEntries;
        private long comparisonEntries;
        private long activeNanos;
        private boolean liveBeforeVerified;
        private boolean liveAfterVerified;
        private boolean liveEffectsUnchanged;
        private boolean goalVerified;
        private boolean originalPathVerified;
        private boolean complete;
        private Result result;

        private Cursor(Context context) {
            if (context == null) throw new IllegalArgumentException("Missing P07 zero-via graph context");
            this.context = context;
            this.geometryBefore = context.layout.geometryFingerprint();
            this.geometrySha256 = sha256(geometryBefore);
            for (int i = 0; i < 2; i++) effectsBefore[i] = context.faces[i].fixedGraphObservationEffects();
            goalMask = new boolean[context.cells * 2];
            topChars = new char[context.cells];
            bottomChars = new char[context.cells];
            goalChars = new char[context.cells];
        }

        boolean advance(int maximumWork) {
            if (maximumWork < 1 || maximumWork > MAX_ADVANCE_WORK)
                throw new IllegalArgumentException("P07 zero-via advance work must be in 1..32768");
            lastWork = 0;
            if (complete) return true;
            long started = System.nanoTime();
            advanceCalls++;
            checkSourceStamps();
            int remaining = maximumWork;
            while (remaining > 0 && !complete) {
                if (stage == COPY_BASELINE) {
                    if (copyTask == null) {
                        copyTask = context.faces[face].beginFixedGraphObservationCopy();
                        copyWatermark[face] = 0;
                    }
                    int debit = remaining;
                    boolean done = copyTask.advanceOne(debit);
                    accountCopy(face);
                    charge(debit, remaining);
                    remaining -= debit;
                    if (done) {
                        pristine[face] = copyTask.result();
                        copyTask = null;
                        comparisonTask = context.faces[face].beginFixedGraphComparison(pristine[face]);
                        stage = COMPARE_BASELINE;
                    }
                } else if (stage == COMPARE_BASELINE) {
                    if (advanceComparison(remaining)) {
                        comparisonTask = null;
                        face++;
                        if (face == 2) {
                            liveBeforeVerified = true;
                            face = 0;
                            stage = COPY_QUERY;
                        } else stage = COPY_BASELINE;
                    }
                    int debit = lastNestedDebit;
                    remaining -= debit;
                } else if (stage == COPY_QUERY) {
                    if (copyTask == null) {
                        copyTask = context.faces[face].beginFixedGraphObservationCopy();
                        copyWatermark[face] = 0;
                    }
                    int debit = remaining;
                    boolean done = copyTask.advanceOne(debit);
                    accountCopy(face);
                    charge(debit, remaining);
                    remaining -= debit;
                    if (done) {
                        query[face] = copyTask.result();
                        copyTask = null;
                        comparisonTask = context.faces[face].beginFixedGraphComparison(query[face]);
                        stage = COMPARE_QUERY;
                    }
                } else if (stage == COMPARE_QUERY) {
                    if (advanceComparison(remaining)) {
                        comparisonTask = null;
                        face++;
                        if (face == 2) {
                            face = 0;
                            stage = COPY_GOALS;
                        } else stage = COPY_QUERY;
                    }
                    int debit = lastNestedDebit;
                    remaining -= debit;
                } else if (stage == COPY_GOALS) {
                    goalMask[goalIndex] = context.sourceGoalMask[goalIndex];
                    goalIndex++;
                    charge(1, remaining);
                    remaining--;
                    if (goalIndex == goalMask.length) stage = SCAN;
                } else if (stage == SCAN) {
                    scanOneDirection();
                    charge(1, remaining);
                    remaining--;
                } else if (stage == ENCODE) {
                    encodeOneCharacter();
                    charge(1, remaining);
                    remaining--;
                    if (encodeCell == context.cells) {
                        goalVerified = isGoal(pathEndCell(), context.pathPhysical[context.pathPhysical.length - 1][2]);
                        if (!goalVerified)
                            throw new IllegalStateException("P07 zero-via captured path end is absent from the actual goal union");
                        stage = VERIFY_PATH;
                    }
                } else if (stage == VERIFY_PATH) {
                    verifyOnePathEdge();
                    charge(1, remaining);
                    remaining--;
                    if (pathEdge == context.pathPhysical.length - 1) {
                        originalPathVerified = true;
                        stage = VERIFY_AFTER;
                    }
                } else if (stage == VERIFY_AFTER) {
                    if (comparisonTask == null)
                        comparisonTask = context.faces[afterFace].beginFixedGraphComparison(pristine[afterFace]);
                    if (advanceComparison(remaining)) {
                        comparisonTask = null;
                        afterFace++;
                        if (afterFace == 2) {
                            checkSourceStamps();
                            liveAfterVerified = true;
                            liveEffectsUnchanged = true;
                            stage = COMPLETE;
                            complete = true;
                        }
                    }
                    int debit = lastNestedDebit;
                    remaining -= debit;
                } else {
                    throw new IllegalStateException("Unknown P07 zero-via graph cursor stage");
                }
                if (lastWork < 1 || lastWork > maximumWork)
                    throw new IllegalStateException("P07 zero-via graph cursor made an invalid work debit");
            }
            checkSourceStamps();
            if (complete && result == null) {
                String top = new String(topChars);
                String bottom = new String(bottomChars);
                String goals = new String(goalChars);
                activeNanos += elapsedNanosSince(started);
                buildResult(top, bottom, goals);
            } else activeNanos += elapsedNanosSince(started);
            return complete;
        }

        private int lastNestedDebit;

        private boolean advanceComparison(int maximum) {
            if (maximum < 1) throw new IllegalStateException("P07 zero-via comparison has no work allowance");
            int before = comparisonTask.comparedEntries();
            boolean done = comparisonTask.advanceOne(maximum);
            int after = comparisonTask.comparedEntries();
            comparisonEntries += after - before;
            lastNestedDebit = maximum;
            charge(maximum, maximum);
            if (done && !comparisonTask.equal())
                throw new IllegalStateException("P07 zero-via live Router snapshot changed");
            return done;
        }

        private void accountCopy(int copyFace) {
            int copied = copyTask.copiedEntries();
            if (copied < copyWatermark[copyFace])
                throw new IllegalStateException("P07 zero-via router-copy counter regressed");
            copyEntries += copied - copyWatermark[copyFace];
            copyWatermark[copyFace] = copied;
        }

        private void charge(int units, int remaining) {
            if (units < 1 || units > remaining)
                throw new IllegalStateException("P07 zero-via work debit exceeded its advance allowance");
            lastWork += units;
            workSteps += units;
        }

        private void scanOneDirection() {
            int xIndex = scanCell % context.width;
            int yIndex = scanCell / context.width;
            int nxIndex = xIndex + DX[scanDirection];
            int nyIndex = yIndex + DY[scanDirection];
            if (nxIndex >= 0 && nxIndex < context.width && nyIndex >= 0 && nyIndex < context.height) {
                int x = coordinate(context.originX, xIndex, context.pitch);
                int y = coordinate(context.originY, yIndex, context.pitch);
                int nx = coordinate(context.originX, nxIndex, context.pitch);
                int ny = coordinate(context.originY, nyIndex, context.pitch);
                if (query[scanFace].permitsLayerStep(x, y, nx, ny, context.net,
                        context.start, context.root, context.reached)) {
                    if (scanFace == TOP) topChars[scanCell] = (char) (nibble(topChars[scanCell]) |
                        (1 << scanDirection));
                    else bottomChars[scanCell] = (char) (nibble(bottomChars[scanCell]) |
                        (1 << scanDirection));
                }
                ordinaryQueries++;
            }
            scanDirection++;
            if (scanDirection == 4) {
                scanDirection = 0;
                scanCell++;
                if (scanCell == context.cells) {
                    scanCell = 0;
                    scanFace++;
                    if (scanFace == 2) {
                        scanFace = 0;
                        stage = ENCODE;
                    }
                }
            }
        }

        private void encodeOneCharacter() {
            int top = faceGoal(TOP, encodeCell) ? 1 : 0;
            int bottom = faceGoal(BOTTOM, encodeCell) ? 2 : 0;
            int goals = top | bottom;
            if (encodeStream == 0) {
                topChars[encodeCell] = HEX.charAt(nibble(topChars[encodeCell]));
                encodeStream++;
            } else if (encodeStream == 1) {
                bottomChars[encodeCell] = HEX.charAt(nibble(bottomChars[encodeCell]));
                encodeStream++;
            } else {
                goalChars[encodeCell] = HEX.charAt(goals);
                encodeStream = 0;
                goalCells++;
                encodeCell++;
            }
            encodedCells++;
        }

        private void verifyOnePathEdge() {
            int[] from = context.pathPhysical[pathEdge];
            int[] to = context.pathPhysical[pathEdge + 1];
            int fromCell = cell(from[0], from[1], context.originX, context.originY,
                context.width, context.height, context.pitch);
            int direction = direction(from, to);
            int mask = nibble(from[2] == TOP ? topChars[fromCell] : bottomChars[fromCell]);
            if ((mask & (1 << direction)) == 0)
                throw new IllegalStateException("P07 zero-via captured source path has a forbidden actual Router edge");
            pathEdge++;
        }

        private boolean faceGoal(int faceIndex, int cellIndex) {
            return goalMask[faceIndex * context.cells + cellIndex];
        }

        private boolean isGoal(int cellIndex, int layer) {
            return faceGoal(layer, cellIndex);
        }

        private int pathEndCell() {
            int[] end = context.pathPhysical[context.pathPhysical.length - 1];
            return cell(end[0], end[1], context.originX, context.originY,
                context.width, context.height, context.pitch);
        }

        private void buildResult(String top, String bottom, String goals) {
            int[] first = context.pathPhysical[0];
            int[] last = context.pathPhysical[context.pathPhysical.length - 1];
            int startCell = cell(first[0], first[1], context.originX, context.originY,
                context.width, context.height, context.pitch);
            int endCell = cell(last[0], last[1], context.originX, context.originY,
                context.width, context.height, context.pitch);
            result = new Result(context, geometrySha256, liveBeforeVerified,
                liveAfterVerified, liveEffectsUnchanged, goalVerified,
                originalPathVerified, startCell, endCell, ordinaryQueries, goalCells,
                encodedCells, advanceCalls, workSteps, copyEntries, comparisonEntries,
                activeNanos / 1000000L, top, bottom, goals);
        }

        private void checkSourceStamps() {
            if (!geometryBefore.equals(context.layout.geometryFingerprint()))
                throw new IllegalStateException("P07 zero-via layout changed during graph capture");
            for (int i = 0; i < 2; i++)
                if (!effectsBefore[i].equals(context.faces[i].fixedGraphObservationEffects()))
                    throw new IllegalStateException("P07 zero-via live Router effects changed during graph capture");
        }

        int lastWork() { return lastWork; }
        boolean isComplete() { return complete; }
        String bodyJson() { return result().bodyJson(); }
        Result result() {
            if (!complete || result == null)
                throw new IllegalStateException("P07 zero-via graph result is incomplete");
            return result;
        }
    }

    /** Immutable completed graph record. */
    static final class Result {
        final int width, height, cells, originX, originY, pitch;
        final int startCell, endCell, endLayer, initialLayerMask;
        final int advanceCalls, ordinaryQueries, goalCells, encodedCells;
        final long workSteps, copyEntries, comparisonEntries, activeMillis;
        final boolean liveBeforeVerified, liveAfterVerified, liveEffectsUnchanged;
        final boolean goalVerified, originalPathVerified;
        final String prefixGeometrySha256;
        final String topStepsHex, bottomStepsHex, goalFacesHex;

        private Result(Context context, String geometrySha256, boolean liveBefore,
                boolean liveAfter, boolean effectsUnchanged, boolean goalVerified,
                boolean pathVerified, int startCell, int endCell, int ordinaryQueries,
                int goalCells, int encodedCells, int advanceCalls, long workSteps,
                long copyEntries, long comparisonEntries, long activeMillis,
                String topStepsHex, String bottomStepsHex, String goalFacesHex) {
            this.width = context.width;
            this.height = context.height;
            this.cells = context.cells;
            this.originX = context.originX;
            this.originY = context.originY;
            this.pitch = context.pitch;
            this.startCell = startCell;
            this.endCell = endCell;
            this.endLayer = context.pathPhysical[context.pathPhysical.length - 1][2];
            this.initialLayerMask = context.initialLayerMask;
            this.advanceCalls = advanceCalls;
            this.ordinaryQueries = ordinaryQueries;
            this.goalCells = goalCells;
            this.encodedCells = encodedCells;
            this.workSteps = workSteps;
            this.copyEntries = copyEntries;
            this.comparisonEntries = comparisonEntries;
            this.activeMillis = activeMillis;
            this.liveBeforeVerified = liveBefore;
            this.liveAfterVerified = liveAfter;
            this.liveEffectsUnchanged = effectsUnchanged;
            this.goalVerified = goalVerified;
            this.originalPathVerified = pathVerified;
            this.prefixGeometrySha256 = geometrySha256;
            this.topStepsHex = topStepsHex;
            this.bottomStepsHex = bottomStepsHex;
            this.goalFacesHex = goalFacesHex;
        }

        String bodyJson() {
            StringBuilder json = new StringBuilder(512 + cells * 3);
            json.append("{\"stability\":{\"prefixGeometrySha256\":\"")
                .append(prefixGeometrySha256).append("\",\"liveBeforeVerified\":")
                .append(liveBeforeVerified).append(",\"liveAfterVerified\":")
                .append(liveAfterVerified).append(",\"liveEffectsUnchanged\":")
                .append(liveEffectsUnchanged).append(",\"goalVerified\":")
                .append(goalVerified).append(",\"originalPathVerified\":")
                .append(originalPathVerified).append("},\"grid\":{\"width\":")
                .append(width).append(",\"height\":").append(height).append(",\"cells\":")
                .append(cells).append(",\"originX\":").append(originX)
                .append(",\"originY\":").append(originY).append(",\"pitch\":").append(pitch)
                .append(",\"startCell\":").append(startCell)
                .append(",\"initialLayerMask\":").append(initialLayerMask)
                .append(",\"endCell\":").append(endCell).append(",\"endLayer\":").append(endLayer)
                .append("},\"work\":{\"advanceCalls\":").append(advanceCalls)
                .append(",\"workSteps\":").append(workSteps)
                .append(",\"copyEntries\":").append(copyEntries)
                .append(",\"comparisonEntries\":").append(comparisonEntries)
                .append(",\"ordinaryQueries\":").append(ordinaryQueries)
                .append(",\"goalCells\":").append(goalCells)
                .append(",\"encodedCells\":").append(encodedCells)
                .append(",\"activeMillis\":").append(activeMillis)
                .append("},\"masks\":{\"topStepsHex\":\"").append(topStepsHex)
                .append("\",\"bottomStepsHex\":\"").append(bottomStepsHex)
                .append("\",\"goalFacesHex\":\"").append(goalFacesHex).append("\"}}");
            return json.toString();
        }
    }

    static void runContracts() {
        Fixture fixture = new Fixture("P07_ZERO_VIA_GRAPH_MAIN");
        PcbNetRouter.Router[] faces = fixture.faces();
        PcbPadPlacement start = fixture.layout.getPad("START.1");
        PcbPadPlacement root = fixture.layout.getPad("ROOT.1");
        PcbPadPlacement reached = fixture.layout.getPad("REACHED.1");
        PcbPadPlacement[] reachedPads = new PcbPadPlacement[] {reached};

        // A top-face foreign-copper owner distinguishes the two actual Router
        // graphs while the bottom-face source positive control remains clear.
        int foreignX = 650, foreignY = 550;
        occupy(faces[TOP], foreignX, foreignY, "BLOCK");
        require(!faces[TOP].permitsLayerStep(foreignX - 10, foreignY, foreignX, foreignY,
                    "N", start, root, reachedPads) &&
                faces[BOTTOM].permitsLayerStep(foreignX - 10, foreignY, foreignX, foreignY,
                    "N", start, root, reachedPads),
            "foreign copper blocks only its owning actual face graph");

        PcbPadPlacement blocker = fixture.layout.getPad("BLOCKER.1");
        require(blocker.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
                blocker.getEscapeLength() >= 20 &&
                (blocker.getEscapeDx() != 0 || blocker.getEscapeDy() != 0),
            "reservation canary uses the declared valid through-hole connector geometry");
        int towardX = blocker.getX() + blocker.getEscapeDx() * 20;
        int towardY = blocker.getY() + blocker.getEscapeDy() * 20;
        int nearX = blocker.getX() + blocker.getEscapeDx() * 10;
        int nearY = blocker.getY() + blocker.getEscapeDy() * 10;
        require(!faces[TOP].permitsLayerStep(towardX, towardY, nearX, nearY,
                    "N", start, root, reachedPads),
            "foreign plated-terminal escape reservation rejects an ordinary approach edge");

        int[][] path = fixture.sourcePath();
        require(path.length == MAX_PATH_NODES,
            "literal positive-control fixture reaches the frozen 66-node physical-path bound");
        boolean[] goals = fixture.actualGoals(faces, root, reachedPads);
        requireActualSourcePath(faces[path[0][2]], path, start, root, reachedPads);
        int[] topOracle = directMasks(faces[TOP], fixture, start, root, reachedPads);
        int[] bottomOracle = directMasks(faces[BOTTOM], fixture, start, root, reachedPads);
        int literalCell = cell(350, 300, fixture);
        int literalBottomMask = literalFourWayMask(faces[BOTTOM], fixture, start, root, reachedPads,
            350, 300);
        int expectedQueries = (int) (2L * (4L * fixture.cells - 2L * fixture.width - 2L * fixture.height));
        require(expectedQueries == 2 * (4 * fixture.cells - 2 * fixture.width - 2 * fixture.height),
            "independent rectangular-grid formula counts every in-bounds directed neighbor");

        int expectedGoalPopulation = countGoalCells(goals, fixture.cells);
        String geometryBefore = fixture.layout.geometryFingerprint();
        String topEffectsBefore = faces[TOP].fixedGraphObservationEffects();
        String bottomEffectsBefore = faces[BOTTOM].fixedGraphObservationEffects();
        int[][] callerPath = copyPath(path);
        PcbPadPlacement[] callerReached = new PcbPadPlacement[] {reached};
        Context context = new Context(fixture.layout, faces, "N", start, root, callerReached,
            goals, fixture.width, fixture.height, fixture.originX, fixture.originY, fixture.pitch,
            3, callerPath);
        callerPath[0][0] = -1;
        callerReached[0] = null;

        Cursor one = begin(context);
        drain(one, 1);
        Result resultOne = one.result();
        Cursor seven = begin(context);
        drain(seven, 7);
        Result resultSeven = seven.result();
        Cursor maximum = begin(context);
        drain(maximum, MAX_ADVANCE_WORK);
        Result resultMaximum = maximum.result();

        require(resultOne.ordinaryQueries == expectedQueries &&
                resultSeven.ordinaryQueries == expectedQueries &&
                resultMaximum.ordinaryQueries == expectedQueries,
            "both faces query exactly every in-bounds directed ordinary edge");
        require(expectedGoalPopulation > 3 && expectedGoalPopulation < fixture.cells &&
                resultOne.goalCells == fixture.cells && resultSeven.goalCells == fixture.cells &&
                resultMaximum.goalCells == fixture.cells &&
                resultOne.encodedCells == 3 * fixture.cells &&
                resultSeven.encodedCells == 3 * fixture.cells &&
                resultMaximum.encodedCells == 3 * fixture.cells,
            "goal-cell positions and three encoded streams match frozen N and 3N wire counts");
        require(sameSemanticGraph(resultOne, resultSeven) && sameSemanticGraph(resultOne, resultMaximum),
            "chunk sizes 1, 7 and 32768 preserve the exact two-face graph and positive controls");
        require(resultOne.originalPathVerified && resultOne.goalVerified &&
                resultOne.liveBeforeVerified && resultOne.liveAfterVerified &&
                resultOne.liveEffectsUnchanged &&
                resultOne.copyEntries > 0 && resultOne.comparisonEntries > 0 &&
                resultOne.workSteps >= resultOne.copyEntries + resultOne.comparisonEntries +
                    goals.length + 11L * fixture.cells + path.length - 1,
            "source-path, live-state and conservative nested-work evidence is complete");
        require(isLowerHex(resultOne.topStepsHex, fixture.cells) &&
                isLowerHex(resultOne.bottomStepsHex, fixture.cells) &&
                isLowerHex(resultOne.goalFacesHex, fixture.cells),
            "all three canonical masks contain one lowercase hex digit per cell");
        require((nibble(resultOne.topStepsHex.charAt(cell(path[0][0], path[0][1], fixture))) & 4) != 0 &&
                (nibble(resultOne.goalFacesHex.charAt(cell(root.getX(), root.getY(), fixture))) & 1) != 0 &&
                (nibble(resultOne.goalFacesHex.charAt(cell(reached.getX(), reached.getY(), fixture))) & 3) == 3,
            "literal S-bit and two-face multi-goal nibbles use TOP=1 and BOTTOM=2");
        require(compareMasksWithIndependentPredicates(resultOne, topOracle, bottomOracle, goals, fixture),
            "each source cell/direction bit matches the independent literal actual-Router query oracle");
        require(nibble(resultOne.bottomStepsHex.charAt(literalCell)) == literalBottomMask,
            "literal N/E/S/W calls encode bits 1/2/4/8 in the frozen order");
        require(!edge(resultOne.topStepsHex, cell(foreignX - 10, foreignY, fixture), 1) &&
                edge(resultOne.bottomStepsHex, cell(foreignX - 10, foreignY, fixture), 1),
            "foreign copper edge is represented on the correct face and direction bit");
        int interiorGoal = fixture.interiorTraceGoal(faces[TOP], root, reached);
        require(interiorGoal >= 0 && (nibble(resultOne.goalFacesHex.charAt(interiorGoal)) & 1) != 0 &&
                expectedGoalPopulation > 3,
            "actual same-net copper interior and multiple physical targets remain in the goal union");
        String body = one.bodyJson();
        require(body.startsWith("{\"stability\":{") && body.contains("\"ordinaryQueries\":" + expectedQueries) &&
                body.contains("\"topStepsHex\":\"") && body.contains("\"bottomStepsHex\":\"") &&
                body.contains("\"goalFacesHex\":\""),
            "completed immutable body exposes the frozen stability/grid/work/masks payload");
        require(geometryBefore.equals(fixture.layout.geometryFingerprint()) &&
                topEffectsBefore.equals(faces[TOP].fixedGraphObservationEffects()) &&
                bottomEffectsBefore.equals(faces[BOTTOM].fixedGraphObservationEffects()),
            "both live face routers and the layout retain their pre-capture state and effects");

        directionalReachedCanary(fixture, faces, start, root, reachedPads);
        alreadyCopiedMutationCanary();
        System.out.println("PASS: P07 zero-via graph contracts assertions=" + Contract.checks);
    }

    private static void directionalReachedCanary(Fixture fixture, PcbNetRouter.Router[] faces,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached) {
        PcbPadPlacement target = reached[0];
        int towardX = target.getX() + target.getEscapeDx() * 20;
        int towardY = target.getY() + target.getEscapeDy() * 20;
        int nearX = target.getX() + target.getEscapeDx() * 10;
        int nearY = target.getY() + target.getEscapeDy() * 10;
        require(target.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
                target.getEscapeLength() >= 20 && target.getEscapeDx() != 0 &&
                faces[TOP].permitsLayerStep(towardX, towardY, nearX, nearY,
                    "N", start, root, reached) &&
                !faces[TOP].permitsLayerStep(nearX, nearY, towardX, towardY,
                    "N", start, root, reached),
            "actual PTH endpoint authority allows directed approach and rejects reverse escape traversal");
    }

    private static void alreadyCopiedMutationCanary() {
        Fixture fixture = new Fixture("P07_ZERO_VIA_GRAPH_MUTATION");
        PcbNetRouter.Router[] faces = fixture.faces();
        PcbPadPlacement start = fixture.layout.getPad("START.1");
        PcbPadPlacement root = fixture.layout.getPad("ROOT.1");
        PcbPadPlacement[] reached = new PcbPadPlacement[] {fixture.layout.getPad("REACHED.1")};
        boolean[] goals = fixture.actualGoals(faces, root, reached);
        Context context = new Context(fixture.layout, faces, "N", start, root, reached,
            goals, fixture.width, fixture.height, fixture.originX, fixture.originY,
            fixture.pitch, 3, fixture.sourcePath());
        Cursor cursor = begin(context);
        cursor.advance(1); // First charged clone entry is occupiedNet[0][0].
        occupy(faces[TOP], fixture.originX, fixture.originY, "BLOCK");
        boolean rejected = false;
        try { drain(cursor, MAX_ADVANCE_WORK); }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected && !cursor.isComplete(),
            "mutation of an already-copied live Router cell is rejected before result publication");
        boolean hidden = false;
        try { cursor.result(); } catch (IllegalStateException expected) { hidden = true; }
        require(hidden, "a failed live-state comparison cannot publish a partial graph");
    }

    private static int[] directMasks(PcbNetRouter.Router router, Fixture fixture,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached) {
        int[] masks = new int[fixture.cells];
        for (int cell = 0; cell < fixture.cells; cell++) {
            int xIndex = cell % fixture.width, yIndex = cell / fixture.width;
            int x = coordinate(fixture.originX, xIndex, fixture.pitch);
            int y = coordinate(fixture.originY, yIndex, fixture.pitch);
            for (int direction = 0; direction < 4; direction++) {
                int nxIndex = xIndex + DX[direction], nyIndex = yIndex + DY[direction];
                if (nxIndex < 0 || nxIndex >= fixture.width || nyIndex < 0 || nyIndex >= fixture.height)
                    continue;
                int nx = coordinate(fixture.originX, nxIndex, fixture.pitch);
                int ny = coordinate(fixture.originY, nyIndex, fixture.pitch);
                if (router.permitsLayerStep(x, y, nx, ny, "N", start, root, reached))
                    masks[cell] |= 1 << direction;
            }
        }
        return masks;
    }

    private static void requireActualSourcePath(PcbNetRouter.Router router, int[][] path,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached) {
        for (int i = 0; i + 1 < path.length; i++) {
            int[] from = path[i], to = path[i + 1];
            boolean allowed = router.permitsLayerStep(from[0], from[1], to[0], to[1],
                "N", start, root, reached);
            if (!allowed)
                throw new AssertionError("Literal P07 zero-via source edge is forbidden at index=" +
                    i + " from=" + from[0] + "," + from[1] + " to=" + to[0] + "," + to[1] +
                    " layer=" + from[2]);
        }
    }

    private static int literalFourWayMask(PcbNetRouter.Router router, Fixture fixture,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
            int x, int y) {
        int mask = 0;
        if (router.permitsLayerStep(x, y, x, y - fixture.pitch, "N", start, root, reached)) mask |= 1;
        if (router.permitsLayerStep(x, y, x + fixture.pitch, y, "N", start, root, reached)) mask |= 2;
        if (router.permitsLayerStep(x, y, x, y + fixture.pitch, "N", start, root, reached)) mask |= 4;
        if (router.permitsLayerStep(x, y, x - fixture.pitch, y, "N", start, root, reached)) mask |= 8;
        return mask;
    }

    private static boolean compareMasksWithIndependentPredicates(Result result, int[] top,
            int[] bottom, boolean[] goals, Fixture fixture) {
        if (top.length != fixture.cells || bottom.length != fixture.cells ||
                goals.length != fixture.cells * 2) return false;
        for (int cell = 0; cell < fixture.cells; cell++) {
            if (nibble(result.topStepsHex.charAt(cell)) != top[cell] ||
                    nibble(result.bottomStepsHex.charAt(cell)) != bottom[cell]) return false;
            int expectedGoals = (goals[cell] ? 1 : 0) | (goals[fixture.cells + cell] ? 2 : 0);
            if (nibble(result.goalFacesHex.charAt(cell)) != expectedGoals) return false;
            int x = cell % fixture.width, y = cell / fixture.width;
            if (y == 0 && ((top[cell] | bottom[cell]) & 1) != 0) return false;
            if (x == fixture.width - 1 && ((top[cell] | bottom[cell]) & 2) != 0) return false;
            if (y == fixture.height - 1 && ((top[cell] | bottom[cell]) & 4) != 0) return false;
            if (x == 0 && ((top[cell] | bottom[cell]) & 8) != 0) return false;
        }
        return true;
    }

    private static boolean sameSemanticGraph(Result a, Result b) {
        return a.width == b.width && a.height == b.height && a.cells == b.cells &&
            a.originX == b.originX && a.originY == b.originY && a.pitch == b.pitch &&
            a.startCell == b.startCell && a.endCell == b.endCell && a.endLayer == b.endLayer &&
            a.initialLayerMask == b.initialLayerMask && a.ordinaryQueries == b.ordinaryQueries &&
            a.goalCells == b.goalCells && a.encodedCells == b.encodedCells &&
            a.copyEntries == b.copyEntries && a.comparisonEntries == b.comparisonEntries &&
            a.liveBeforeVerified == b.liveBeforeVerified && a.liveAfterVerified == b.liveAfterVerified &&
            a.liveEffectsUnchanged == b.liveEffectsUnchanged && a.goalVerified == b.goalVerified &&
            a.originalPathVerified == b.originalPathVerified &&
            a.prefixGeometrySha256.equals(b.prefixGeometrySha256) &&
            a.topStepsHex.equals(b.topStepsHex) && a.bottomStepsHex.equals(b.bottomStepsHex) &&
            a.goalFacesHex.equals(b.goalFacesHex);
    }

    private static int countGoalCells(boolean[] goals, int cells) {
        int count = 0;
        for (int i = 0; i < cells; i++) if (goals[i] || goals[cells + i]) count++;
        return count;
    }

    private static void drain(Cursor cursor, int chunk) {
        int guard = 0;
        while (!cursor.isComplete()) {
            boolean done = cursor.advance(chunk);
            if (cursor.lastWork() < 1 || cursor.lastWork() > chunk ||
                    done != cursor.isComplete())
                throw new AssertionError("P07 zero-via graph exceeded or misreported its work chunk");
            if (++guard > 2000000)
                throw new AssertionError("P07 zero-via graph fixture exceeded its bounded resume guard");
        }
    }

    private static boolean isLowerHex(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) return false;
        for (int i = 0; i < value.length(); i++) if (HEX.indexOf(value.charAt(i)) < 0) return false;
        return true;
    }

    private static boolean edge(String mask, int fromCell, int direction) {
        return (nibble(mask.charAt(fromCell)) & (1 << direction)) != 0;
    }

    private static int nibble(char value) {
        int result = HEX.indexOf(value);
        if (result >= 0) return result;
        if (value >= 0 && value <= 15) return value;
        throw new IllegalArgumentException("Invalid P07 zero-via mask nibble");
    }

    private static int direction(int[] from, int[] to) {
        if (to[1] < from[1]) return 0;
        if (to[0] > from[0]) return 1;
        if (to[1] > from[1]) return 2;
        if (to[0] < from[0]) return 3;
        throw new IllegalArgumentException("P07 zero-via source path contains a zero-length edge");
    }

    private static int cell(int x, int y, Fixture fixture) {
        return cell(x, y, fixture.originX, fixture.originY, fixture.width, fixture.height, fixture.pitch);
    }

    private static int cell(int x, int y, int originX, int originY, int width, int height, int pitch) {
        long dx = (long) x - originX, dy = (long) y - originY;
        if (dx < 0 || dy < 0 || dx % pitch != 0 || dy % pitch != 0) return -1;
        long cx = dx / pitch, cy = dy / pitch;
        if (cx >= width || cy >= height) return -1;
        return (int) (cy * width + cx);
    }

    private static int coordinate(int origin, int index, int pitch) {
        long value = (long) origin + (long) index * pitch;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalStateException("P07 zero-via cell coordinate overflowed");
        return (int) value;
    }

    private static int[][] copyPath(int[][] path) {
        int[][] copy = new int[path.length][3];
        for (int i = 0; i < path.length; i++) System.arraycopy(path[i], 0, copy[i], 0, 3);
        return copy;
    }

    private static void occupy(PcbNetRouter.Router router, int x, int y, String net) {
        Vector<Point> point = new Vector<Point>();
        point.add(new Point(x, y));
        router.occupyLayerPath(point, net);
    }

    private static long elapsedNanosSince(long started) {
        long elapsed = System.nanoTime() - started;
        return elapsed < 0 ? 0 : elapsed;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                int unsigned = item & 255;
                if (unsigned < 16) hex.append('0');
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is required for P07 zero-via graph identity", missing);
        }
    }

    private static final class Fixture {
        final Rectangle outline = new Rectangle(100, 100, 600, 500);
        final int width = (outline.width - 20) / 10 + 1;
        final int height = (outline.height - 20) / 10 + 1;
        final int originX = outline.x + 10, originY = outline.y + 10, pitch = 10;
        final int cells = width * height;
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        private PcbNetRouter.Router[] cachedFaces;

        Fixture(String id) {
            initializeCircuitJS();
            board = new TroubleshootBoard(id);
            board.addNet(new BoardNet("N"));
            board.addNet(new BoardNet("BLOCK", BoardNet.RoutingRole.RETURN));
            layout = new PcbBoardLayout(900, 700, outline, new Rectangle(720, 120, 150, 520));
            addTestPoint("START", "N", 210, 210, PcbBoardSide.BOTTOM, PcbRotation.DEG_180);
            addTestPoint("ROOT", "N", 260, 210, PcbBoardSide.BOTTOM, PcbRotation.DEG_0);
            addHeader("REACHED", "N", "N", 450, 450);
            addHeader("BLOCKER", "BLOCK", "BLOCK", 550, 150);
            Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
            for (String component : board.getComponentIds())
                parts.add(new PcbPlacementConstraints.Part(component, "p07",
                    "zero-via graph fixture", "p07", PcbPlacementConstraints.Anchor.NONE, 16));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false,
                PcbTraceWidthProfile.UNIFORM));
            board.validate();
            layout.validateAgainst(board);
            PcbNetRouter.Router top = new PcbNetRouter.Router(layout, board, outline, 0,
                P06FactoryLinkFixtures.OBSERVER, PcbCopperLayer.TOP);
            PcbTraceGeometry topTree = top.route("N", "ROOT.1", "REACHED.1");
            layout.addTrace(topTree);
            layout.validateAgainst(board);
            PcbNetRouter.Router bottom = new PcbNetRouter.Router(layout, board, outline, 0,
                P06FactoryLinkFixtures.OBSERVER, PcbCopperLayer.BOTTOM);
            cachedFaces = new PcbNetRouter.Router[] {top, bottom};
        }

        PcbNetRouter.Router[] faces() {
            return cachedFaces;
        }

        int[][] sourcePath() {
            int[][] result = new int[MAX_PATH_NODES][3];
            int index = 0;
            for (int column = 0; column < 6; column++) {
                int x = 210 + column * 10;
                int startY = (column & 1) == 0 ? 210 : 310;
                int endY = (column & 1) == 0 ? 310 : 210;
                if (column > 0) {
                    result[index][0] = x;
                    result[index][1] = startY;
                result[index++][2] = PcbCopperLayer.TOP.ordinal();
                }
                int step = endY > startY ? 10 : -10;
                for (int y = column == 0 ? startY : startY + step;
                        step > 0 ? y <= endY : y >= endY; y += step) {
                    result[index][0] = x;
                    result[index][1] = y;
                    result[index++][2] = PcbCopperLayer.TOP.ordinal();
                }
            }
            if (index != result.length || result[index - 1][0] != 260 || result[index - 1][1] != 210)
                throw new AssertionError("Literal P07 zero-via path fixture has an invalid 66-node endpoint");
            return result;
        }

        boolean[] actualGoals(PcbNetRouter.Router[] routers, PcbPadPlacement root,
                PcbPadPlacement[] reached) {
            boolean[] result = new boolean[cells * 2];
            for (int face = 0; face < 2; face++) {
                PcbCopperLayer layer = face == TOP ? PcbCopperLayer.TOP : PcbCopperLayer.BOTTOM;
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                    if (routers[face].ownsLayerGridCell(x, y, "N")) result[face * cells + y * width + x] = true;
                if (PcbCopperAccess.hasCopper(root, layer)) {
                    int rootCell = cell(root.getX(), root.getY(), this);
                    result[face * cells + rootCell] = true;
                }
                for (PcbPadPlacement pad : reached) if (PcbCopperAccess.hasCopper(pad, layer)) {
                    int padCell = cell(pad.getX(), pad.getY(), this);
                    result[face * cells + padCell] = true;
                }
            }
            return result;
        }

        int interiorTraceGoal(PcbNetRouter.Router router, PcbPadPlacement root,
                PcbPadPlacement reached) {
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int px = coordinate(originX, x, pitch), py = coordinate(originY, y, pitch);
                if (router.ownsLayerGridCell(x, y, "N") &&
                        (px != root.getX() || py != root.getY()) &&
                        (px != reached.getX() || py != reached.getY()))
                    return y * width + x;
            }
            return -1;
        }

        private void addTestPoint(String id, String net, int x, int y, PcbBoardSide side,
                PcbRotation rotation) {
            PhysicalPackage physical = P06FactoryLinkFixtures.TEST_POINT;
            PcbPackagePose reference = new PcbPackagePose(100, 100, rotation, side);
            Point local = physical.getGeometry().placedAt(reference).getPadPoint(0);
            addPackage(id, physical, new String[] {net}, new PcbPackagePose(
                100 + x - local.x, 100 + y - local.y, rotation, side));
        }

        private void addHeader(String id, String firstNet, String secondNet, int x, int y) {
            PhysicalPackage physical = PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
            PcbPackagePose pose = new PcbPackagePose(x - 90, y - 40,
                PcbRotation.DEG_0, PcbBoardSide.TOP);
            addPackage(id, physical, new String[] {firstNet, secondNet}, pose);
        }

        private void addPackage(String id, PhysicalPackage physical, String[] nets,
                PcbPackagePose pose) {
            BoardComponent component = new BoardComponent(id, physical.getId(), physical, id);
            board.addComponent(component);
            Vector<String> terminals = physical.getTerminalIds();
            for (int i = 0; i < terminals.size(); i++)
                board.addPad(new BoardPad(id + "." + terminals.get(i), id, terminals.get(i), nets[i]));
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, pose,
                physical.getGeometry());
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
        }
    }

    private static void initializeCircuitJS() {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
    }

    private static final class Contract {
        static int checks;
        static void check(boolean value, String message) {
            checks++;
            if (!value) throw new AssertionError(message);
        }
    }

    private static void require(boolean value, String message) {
        Contract.check(value, message);
    }
}
