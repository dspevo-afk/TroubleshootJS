package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Vector;

/** Bounded, test-only inventory of immutable P07 prefix-hole creation identities. */
final class P07PrefixAllocationDiagnostics {
    private static final int MAX_HOLES = 60;
    private static final int MAX_NETS = 256;
    private static final int MAX_ID_LENGTH = 4096;
    private static final int MAX_ADVANCE_WORK = 32768;
    private static final long MAX_TOTAL_WORK = 640L * MAX_ADVANCE_WORK;
    private static final int MAX_FIXTURE_ADVANCE_CALLS = 4096;
    private static final int VIA_KIND = PcbBoardHole.Kind.VIA.ordinal();
    private static final String HEX = "0123456789abcdef";
    private static int checks;

    private P07PrefixAllocationDiagnostics() { }

    /**
     * References and primitive routing metadata captured under the caller's
     * exclusive, quiescent ownership of one Publisher prefix. The cursor's
     * before/after comparisons are not an atomic concurrent snapshot.
     */
    static final class Context {
        final PcbBoardLayout layout;
        final PcbBoardHole[] prefixHoles;
        final String[] netOrder;
        final int searchNetIndex;
        final String currentNet;
        final int currentBranchOrdinal;
        final int pass;
        final int maximumVias;
        final int currentNetPosition;
        final long captureNanos;

        Context(PcbBoardLayout layout, PcbBoardHole[] prefixHoles, String[] netOrder,
                int searchNetIndex, String currentNet, int currentBranchOrdinal,
                int pass, int maximumVias) {
            long started = System.nanoTime();
            if (layout == null || prefixHoles == null || netOrder == null ||
                    netOrder.length < 1 || netOrder.length > MAX_NETS ||
                    prefixHoles.length > MAX_HOLES || maximumVias < 0 ||
                    maximumVias > PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias ||
                    prefixHoles.length > maximumVias || searchNetIndex < 1 ||
                    searchNetIndex > netOrder.length || currentBranchOrdinal < 1 ||
                    pass < 0 || pass >= PcbLayerRoutingPrototype.ORDERINGS ||
                    layout.getHoleCount() != prefixHoles.length)
                throw new IllegalArgumentException("Invalid P07 prefix-allocation context");
            this.layout = layout;
            this.prefixHoles = new PcbBoardHole[prefixHoles.length];
            for (int i = 0; i < prefixHoles.length; i++) {
                if (prefixHoles[i] == null)
                    throw new IllegalArgumentException("P07 prefix-allocation source has a null hole");
                this.prefixHoles[i] = prefixHoles[i];
            }
            this.netOrder = new String[netOrder.length];
            HashSet<String> seen = new HashSet<String>();
            for (int i = 0; i < netOrder.length; i++) {
                String net = validIdentity(netOrder[i], "net order");
                if (!seen.add(net))
                    throw new IllegalArgumentException("P07 prefix-allocation net order has duplicates");
                this.netOrder[i] = net;
            }
            String net = validIdentity(currentNet, "current net");
            int position = searchNetIndex - 1;
            if (!net.equals(this.netOrder[position]))
                throw new IllegalArgumentException("P07 current net does not match the next-fetch index");
            this.searchNetIndex = searchNetIndex;
            this.currentNet = net;
            this.currentBranchOrdinal = currentBranchOrdinal;
            this.pass = pass;
            this.maximumVias = maximumVias;
            this.currentNetPosition = position;
            this.captureNanos = Math.max(0L, System.nanoTime() - started);
        }
    }

    static Cursor begin(Context context) {
        return new Cursor(context);
    }

    /** Immutable row. Fingerprint is copied from the source hole and checked independently. */
    static final class Row {
        final int index;
        final String id;
        final String netId;
        final PcbBoardHole.Kind kind;
        final int x;
        final int y;
        final int drillRadius;
        final int landRadius;
        final PcbCopperAccess.Exposure exposure;
        final String fingerprint;
        final int creatorNetPosition;
        final int creatorBranchOrdinal;
        final int piece;

        private Row(int index, String id, String netId, PcbBoardHole.Kind kind,
                int x, int y, int drillRadius, int landRadius,
                PcbCopperAccess.Exposure exposure, String fingerprint,
                int creatorNetPosition, int creatorBranchOrdinal, int piece) {
            this.index = index;
            this.id = id;
            this.netId = netId;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.drillRadius = drillRadius;
            this.landRadius = landRadius;
            this.exposure = exposure;
            this.fingerprint = fingerprint;
            this.creatorNetPosition = creatorNetPosition;
            this.creatorBranchOrdinal = creatorBranchOrdinal;
            this.piece = piece;
        }

        /** Parses creation identity only; strict prior-prefix ownership belongs to Context/Cursor. */
        static Row snapshot(PcbBoardHole hole, int index, String[] netOrder) {
            if (hole == null || index < 0 || index >= MAX_HOLES)
                throw new IllegalArgumentException("Invalid P07 prefix-allocation row source");
            validateNetOrder(netOrder);
            String id = validIdentity(hole.id, "hole id");
            String netId = validIdentity(hole.netId, "hole net id");
            validateVia(hole.kind, hole.drillRadius, hole.landRadius, hole.exposure,
                hole.x, hole.y);
            String fingerprint = hole.fingerprint();
            String expected = fingerprint(id, hole.kind, netId, hole.x, hole.y,
                hole.drillRadius, hole.landRadius, hole.exposure);
            if (!expected.equals(fingerprint))
                throw new IllegalStateException("P07 source-hole fingerprint differs from its immutable fields");
            Match match = parseKnownNet(id, netOrder);
            if (!netId.equals(match.netId))
                throw new IllegalArgumentException("P07 hole net does not match its encoded owner");
            return new Row(index, id, netId, hole.kind, hole.x, hole.y,
                hole.drillRadius, hole.landRadius, hole.exposure, fingerprint,
                match.netPosition, match.branchOrdinal, match.piece);
        }

        String toJson() {
            return "{\"index\":" + index + ",\"id\":\"" + json(id) +
                "\",\"netId\":\"" + json(netId) + "\",\"kind\":\"" +
                kind.name() + "\",\"x\":" + x + ",\"y\":" + y +
                ",\"drillRadius\":" + drillRadius + ",\"landRadius\":" +
                landRadius + ",\"exposure\":\"" + exposure.name() +
                "\",\"fingerprint\":\"" + json(fingerprint) +
                "\",\"creatorNetPosition\":" + creatorNetPosition +
                ",\"creatorBranchOrdinal\":" + creatorBranchOrdinal +
                ",\"piece\":" + piece + "}";
        }
    }

    static final class Result {
        final int totalHoles;
        private final Row[] rows;
        private final int[] byKind;
        private final int[] byNet;

        private Result(Row[] rows, int[] byKind, int[] byNet) {
            this.totalHoles = rows.length;
            this.rows = rows;
            this.byKind = byKind;
            this.byNet = byNet;
        }

        int rowCount() { return rows.length; }
        Row rowAt(int index) { return rows[index]; }
        int kindCount(PcbBoardHole.Kind kind) { return byKind[kind.ordinal()]; }
        int netCountAt(int position) { return byNet[position]; }
    }

    /** Resumable, bounded prefix scan. All work counters count explicit logical units. */
    static final class Cursor {
        private static final int VERIFY_BEFORE = 0;
        private static final int SCAN_ROWS = 1;
        private static final int WRITE_NET_TOTALS = 2;
        private static final int VERIFY_AFTER = 3;
        private static final int VERIFY_LINEAGE_ORDER = 4;
        private static final int COMPLETE = 5;

        private final Context context;
        private final Vector<PcbBoardHole> beforeHoles;
        private final String geometryBefore;
        private final String prefixGeometrySha256;
        private final String[] baselineIds;
        private final String[] baselineFingerprints;
        private final Row[] rows;
        private final int[] byKind = new int[PcbBoardHole.Kind.values().length];
        private final int[] byNet;
        private final StringBuilder rowsJson = new StringBuilder("[");
        private final StringBuilder byNetJson = new StringBuilder("[");

        private Vector<PcbBoardHole> afterHoles;
        private RowDraft rowDraft;
        private int stage = VERIFY_BEFORE;
        private int beforeIndex;
        private int rowIndex;
        private int netCompareIndex;
        private int rowMatchCount;
        private Match rowMatch;
        private int netWriteIndex;
        private int afterIndex;
        private int lineageFirst;
        private int lineageSecond = 1;
        private int totalByNet;
        private int advanceCalls;
        private int lastWork;
        private int rowsScanned;
        private int netComparisons;
        private int netTotalsWritten;
        private long workSteps;
        private long activeNanos;
        private boolean liveBeforeVerified;
        private boolean liveAfterVerified;
        private boolean holeIdentitiesVerified;
        private boolean lineageVerified;
        private boolean complete;
        private String bodyJson;
        private Result result;

        private Cursor(Context context) {
            if (context == null)
                throw new IllegalArgumentException("Missing P07 prefix-allocation context");
            long started = System.nanoTime();
            this.context = context;
            this.beforeHoles = context.layout.getHoles();
            if (beforeHoles.size() != context.prefixHoles.length)
                throw new IllegalStateException("P07 prefix-hole count changed before allocation scan");
            this.geometryBefore = context.layout.geometryFingerprint();
            this.prefixGeometrySha256 = sha256(geometryBefore);
            this.baselineIds = new String[context.prefixHoles.length];
            this.baselineFingerprints = new String[context.prefixHoles.length];
            this.rows = new Row[context.prefixHoles.length];
            this.byNet = new int[context.netOrder.length];
            activeNanos = context.captureNanos + Math.max(0L, System.nanoTime() - started);
        }

        boolean advance(int maximumWork) {
            if (maximumWork < 1 || maximumWork > MAX_ADVANCE_WORK)
                throw new IllegalArgumentException("P07 prefix-allocation work must be in 1..32768");
            lastWork = 0;
            if (complete) return true;
            advanceCalls++;
            long started = System.nanoTime();
            try {
                int remaining = maximumWork;
                while (remaining > 0 && stage != COMPLETE) {
                    if (stage == VERIFY_BEFORE) {
                        if (beforeIndex < context.prefixHoles.length) {
                            PcbBoardHole source = context.prefixHoles[beforeIndex];
                            if (beforeHoles.get(beforeIndex) != source)
                                throw new IllegalStateException("P07 prefix-hole identity/order differs before scan");
                            String id = validIdentity(source.id, "hole id");
                            String fingerprint = sourceFingerprint(source);
                            if (beforeIndex > 0 && baselineIds[beforeIndex - 1].compareTo(id) >= 0)
                                throw new IllegalStateException("P07 prefix holes are not in canonical Java String order");
                            baselineIds[beforeIndex] = id;
                            baselineFingerprints[beforeIndex] = fingerprint;
                            beforeIndex++;
                            debit();
                            remaining--;
                        } else {
                            liveBeforeVerified = true;
                            stage = SCAN_ROWS;
                            debit();
                            remaining--;
                        }
                    } else if (stage == SCAN_ROWS) {
                        if (rowIndex >= context.prefixHoles.length) {
                            rowsJson.append(']');
                            stage = WRITE_NET_TOTALS;
                            debit();
                            remaining--;
                            continue;
                        }
                        if (rowDraft == null) {
                            rowDraft = new RowDraft(context.prefixHoles[rowIndex]);
                            netCompareIndex = 0;
                            rowMatchCount = 0;
                            rowMatch = null;
                            rowsScanned++;
                            debit();
                            remaining--;
                            continue;
                        }
                        String knownNet = context.netOrder[netCompareIndex];
                        Match candidate = matchKnownNet(rowDraft.id, knownNet, netCompareIndex);
                        netComparisons++;
                        if (candidate != null) {
                            rowMatchCount++;
                            rowMatch = candidate;
                        }
                        netCompareIndex++;
                        debit();
                        remaining--;
                        if (netCompareIndex == context.netOrder.length) {
                            finishRow();
                        }
                    } else if (stage == WRITE_NET_TOTALS) {
                        if (netWriteIndex >= context.netOrder.length) {
                            byNetJson.append(']');
                            stage = VERIFY_AFTER;
                            afterHoles = context.layout.getHoles();
                            if (afterHoles.size() != context.prefixHoles.length)
                                throw new IllegalStateException("P07 prefix-hole count changed during allocation scan");
                            debit();
                            remaining--;
                            continue;
                        }
                        if (netWriteIndex > 0) byNetJson.append(',');
                        byNetJson.append("{\"netPosition\":").append(netWriteIndex)
                            .append(",\"netId\":\"").append(json(context.netOrder[netWriteIndex]))
                            .append("\",\"holeCount\":").append(byNet[netWriteIndex]).append('}');
                        totalByNet += byNet[netWriteIndex];
                        netWriteIndex++;
                        netTotalsWritten++;
                        debit();
                        remaining--;
                    } else if (stage == VERIFY_AFTER) {
                        if (afterIndex < context.prefixHoles.length) {
                            PcbBoardHole source = context.prefixHoles[afterIndex];
                            if (afterHoles.get(afterIndex) != source ||
                                    !baselineIds[afterIndex].equals(source.id) ||
                                    !baselineFingerprints[afterIndex].equals(sourceFingerprint(source)))
                                throw new IllegalStateException("P07 prefix-hole identity/fingerprint changed after scan");
                            if (afterIndex > 0 &&
                                    afterHoles.get(afterIndex - 1).id.compareTo(source.id) >= 0)
                                throw new IllegalStateException("P07 prefix-hole order changed after scan");
                            afterIndex++;
                            debit();
                            remaining--;
                        } else {
                            if (!geometryBefore.equals(context.layout.geometryFingerprint()))
                                throw new IllegalStateException("P07 prefix geometry changed during allocation scan");
                            if (!liveBeforeVerified || rowsScanned != context.prefixHoles.length ||
                                    netComparisons != context.prefixHoles.length * context.netOrder.length ||
                                    netTotalsWritten != context.netOrder.length || totalByNet != rows.length ||
                                    byKind[VIA_KIND] != rows.length ||
                                    byKind[PcbBoardHole.Kind.PLATED.ordinal()] != 0 ||
                                    byKind[PcbBoardHole.Kind.NON_PLATED.ordinal()] != 0)
                                throw new IllegalStateException("P07 prefix-allocation row/totals conservation failed");
                            liveAfterVerified = true;
                            holeIdentitiesVerified = true;
                            stage = VERIFY_LINEAGE_ORDER;
                            debit();
                            remaining--;
                        }
                    } else if (stage == VERIFY_LINEAGE_ORDER) {
                        if (lineageFirst < rows.length && lineageSecond < rows.length) {
                            Row first = rows[lineageFirst];
                            Row second = rows[lineageSecond];
                            if (first.creatorBranchOrdinal == second.creatorBranchOrdinal) {
                                if (first.creatorNetPosition != second.creatorNetPosition ||
                                        !first.netId.equals(second.netId))
                                    throw new IllegalArgumentException(
                                        "P07 prefix rows assign one creator branch to different nets");
                            } else if (first.creatorBranchOrdinal < second.creatorBranchOrdinal) {
                                if (first.creatorNetPosition > second.creatorNetPosition)
                                    throw new IllegalArgumentException(
                                        "P07 prefix creator net position moves backward by branch ordinal");
                            } else if (second.creatorNetPosition > first.creatorNetPosition) {
                                throw new IllegalArgumentException(
                                    "P07 prefix creator net position moves backward by branch ordinal");
                            }
                            lineageSecond++;
                            if (lineageSecond >= rows.length) {
                                lineageFirst++;
                                lineageSecond = lineageFirst + 1;
                            }
                            debit();
                            remaining--;
                        } else {
                            lineageVerified = true;
                            stage = COMPLETE;
                            debit();
                            remaining--;
                        }
                    } else {
                        throw new IllegalStateException("Unknown P07 prefix-allocation cursor stage");
                    }
                }
            } finally {
                activeNanos += Math.max(0L, System.nanoTime() - started);
            }
            if (stage == COMPLETE) {
                long assemblyStarted = System.nanoTime();
                BodyTemplate bodyTemplate;
                String placeholderBody;
                try {
                    result = new Result(rows, byKind, byNet);
                    bodyTemplate = assembleBodyTemplate();
                    placeholderBody = bodyTemplate.materializePlaceholder();
                } finally {
                    activeNanos += Math.max(0L, System.nanoTime() - assemblyStarted);
                }
                // Result creation, full payload assembly, and one complete
                // placeholder-JSON String materialization are measured. Inserting
                // activeMillis into that placeholder and materializing the final
                // String are excluded because the value depends on its own build;
                // the caller's outer clock includes both finalization operations.
                bodyJson = bodyTemplate.applyActiveMillis(placeholderBody,
                    activeNanos / 1000000L);
                complete = true;
            }
            return complete;
        }

        private void finishRow() {
            if (rowMatchCount != 1 || rowMatch == null)
                throw new IllegalArgumentException("P07 prefix-hole ID has no unique known-net owner");
            if (!rowDraft.netId.equals(rowMatch.netId))
                throw new IllegalArgumentException("P07 prefix-hole net does not match its encoded owner");
            if (rowMatch.netPosition > context.currentNetPosition ||
                    rowMatch.branchOrdinal >= context.currentBranchOrdinal)
                throw new IllegalArgumentException("P07 prefix-hole creator is not strictly before the current branch");
            Row row = new Row(rowIndex, rowDraft.id, rowDraft.netId, rowDraft.kind,
                rowDraft.x, rowDraft.y, rowDraft.drillRadius, rowDraft.landRadius,
                rowDraft.exposure, rowDraft.fingerprint, rowMatch.netPosition,
                rowMatch.branchOrdinal, rowMatch.piece);
            if (rowIndex > 0 && rows[rowIndex - 1].id.compareTo(row.id) >= 0)
                throw new IllegalStateException("P07 prefix rows are not in canonical Java String order");
            rows[rowIndex] = row;
            byKind[row.kind.ordinal()]++;
            byNet[row.creatorNetPosition]++;
            if (rowIndex > 0) rowsJson.append(',');
            rowsJson.append(row.toJson());
            rowDraft = null;
            rowIndex++;
        }

        private void debit() {
            lastWork++;
            workSteps++;
            if (lastWork > MAX_ADVANCE_WORK ||
                    workSteps > MAX_TOTAL_WORK)
                throw new IllegalStateException("P07 prefix-allocation work cap exceeded");
        }

        private BodyTemplate assembleBodyTemplate() {
            StringBuilder body = new StringBuilder(512 + rowsJson.length() + byNetJson.length());
            body.append("{\"stability\":{\"prefixGeometrySha256\":\"")
                .append(prefixGeometrySha256).append("\",\"liveBeforeVerified\":")
                .append(liveBeforeVerified).append(",\"liveAfterVerified\":")
                .append(liveAfterVerified).append(",\"holeIdentitiesVerified\":")
                .append(holeIdentitiesVerified).append(",\"lineageVerified\":")
                .append(lineageVerified).append("},\"work\":{\"advanceCalls\":")
                .append(advanceCalls).append(",\"workSteps\":").append(workSteps)
                .append(",\"rowsScanned\":").append(rowsScanned)
                .append(",\"netComparisons\":").append(netComparisons)
                .append(",\"netTotalsWritten\":").append(netTotalsWritten)
                .append(",\"activeMillis\":");
            int activeMillisStart = body.length();
            int activeMillisWidth = 19;
            for (int i = 0; i < activeMillisWidth; i++) body.append(' ');
            body.append("},\"totals\":{\"totalHoles\":").append(rows.length)
                .append(",\"byKind\":{\"VIA\":")
                .append(byKind[PcbBoardHole.Kind.VIA.ordinal()])
                .append(",\"PLATED\":").append(byKind[PcbBoardHole.Kind.PLATED.ordinal()])
                .append(",\"NON_PLATED\":")
                .append(byKind[PcbBoardHole.Kind.NON_PLATED.ordinal()])
                .append("},\"byNet\":").append(byNetJson)
                .append("},\"rows\":").append(rowsJson).append('}');
            return new BodyTemplate(body, activeMillisStart, activeMillisWidth);
        }

        int lastWork() { return lastWork; }
        boolean isComplete() { return complete; }

        String bodyJson() {
            if (!complete || bodyJson == null)
                throw new IllegalStateException("P07 prefix-allocation body is incomplete");
            return bodyJson;
        }

        Result result() {
            if (!complete || result == null)
                throw new IllegalStateException("P07 prefix-allocation result is incomplete");
            return result;
        }
    }

    private static final class BodyTemplate {
        private final StringBuilder json;
        private final int activeMillisStart;
        private final int activeMillisWidth;

        BodyTemplate(StringBuilder json, int activeMillisStart, int activeMillisWidth) {
            this.json = json;
            this.activeMillisStart = activeMillisStart;
            this.activeMillisWidth = activeMillisWidth;
        }

        String materializePlaceholder() {
            json.setCharAt(activeMillisStart, '0');
            return json.toString();
        }

        String applyActiveMillis(String placeholderBody, long activeMillis) {
            if (placeholderBody == null || activeMillis < 0)
                throw new IllegalStateException("Invalid P07 active-time placeholder");
            String digits = Long.toString(activeMillis);
            if (digits.length() > activeMillisWidth)
                throw new IllegalStateException("P07 active time exceeds its reserved JSON field");
            StringBuilder finalJson = new StringBuilder(placeholderBody);
            finalJson.replace(activeMillisStart, activeMillisStart + activeMillisWidth, digits);
            return finalJson.toString();
        }
    }

    private static final class RowDraft {
        final String id;
        final String netId;
        final PcbBoardHole.Kind kind;
        final int x, y, drillRadius, landRadius;
        final PcbCopperAccess.Exposure exposure;
        final String fingerprint;

        RowDraft(PcbBoardHole hole) {
            if (hole == null) throw new IllegalArgumentException("Missing P07 prefix-hole source");
            id = validIdentity(hole.id, "hole id");
            netId = validIdentity(hole.netId, "hole net id");
            validateVia(hole.kind, hole.drillRadius, hole.landRadius, hole.exposure,
                hole.x, hole.y);
            fingerprint = sourceFingerprint(hole);
            kind = hole.kind;
            x = hole.x;
            y = hole.y;
            drillRadius = hole.drillRadius;
            landRadius = hole.landRadius;
            exposure = hole.exposure;
        }
    }

    private static final class Match {
        final String netId;
        final int netPosition, branchOrdinal, piece;
        Match(String netId, int netPosition, int branchOrdinal, int piece) {
            this.netId = netId;
            this.netPosition = netPosition;
            this.branchOrdinal = branchOrdinal;
            this.piece = piece;
        }
    }

    private static Match parseKnownNet(String id, String[] netOrder) {
        Match match = null;
        int count = 0;
        for (int i = 0; i < netOrder.length; i++) {
            Match candidate = matchKnownNet(id, netOrder[i], i);
            if (candidate != null) {
                count++;
                match = candidate;
            }
        }
        if (count != 1)
            throw new IllegalArgumentException("P07 prefix-hole ID must have one unambiguous known-net owner");
        return match;
    }

    private static Match matchKnownNet(String id, String knownNet, int netPosition) {
        String prefix = "p07-via/" + knownNet + "/";
        if (!id.startsWith(prefix)) return null;
        String suffix = id.substring(prefix.length());
        int separator = suffix.indexOf('/');
        if (separator <= 0 || suffix.indexOf('/', separator + 1) >= 0) return null;
        String ordinalText = suffix.substring(0, separator);
        String pieceText = suffix.substring(separator + 1);
        int ordinal = canonicalDecimal(ordinalText, true);
        int piece = canonicalDecimal(pieceText, false);
        if (ordinal < 1 || piece < 0) return null;
        return new Match(knownNet, netPosition, ordinal, piece);
    }

    private static int canonicalDecimal(String value, boolean positive) {
        if (value == null || value.length() == 0 ||
                (value.length() > 1 && value.charAt(0) == '0')) return -1;
        long number = 0;
        for (int i = 0; i < value.length(); i++) {
            char digit = value.charAt(i);
            if (digit < '0' || digit > '9') return -1;
            number = number * 10L + digit - '0';
            if (number > Integer.MAX_VALUE) return -1;
        }
        if (positive && number == 0) return -1;
        return (int) number;
    }

    private static void validateNetOrder(String[] netOrder) {
        if (netOrder == null || netOrder.length < 1 || netOrder.length > MAX_NETS)
            throw new IllegalArgumentException("Invalid P07 prefix-allocation net order");
        HashSet<String> seen = new HashSet<String>();
        for (String net : netOrder) {
            String value = validIdentity(net, "net order");
            if (!seen.add(value))
                throw new IllegalArgumentException("P07 prefix-allocation net order has duplicates");
        }
    }

    private static String validIdentity(String value, String label) {
        if (value == null || value.length() > MAX_ID_LENGTH)
            throw new IllegalArgumentException("Invalid P07 " + label);
        return PcbConductorGraph.requireId(value);
    }

    private static void validateVia(PcbBoardHole.Kind kind, int drillRadius, int landRadius,
            PcbCopperAccess.Exposure exposure, int x, int y) {
        if (kind != PcbBoardHole.Kind.VIA || drillRadius != PcbTwoLayerRules.VIA_DRILL ||
                landRadius != PcbTwoLayerRules.VIA_LAND ||
                exposure != PcbCopperAccess.Exposure.EXPOSED)
            throw new IllegalArgumentException("P07 prefix allocation requires a factory VIA row");
        PcbCoordinateSystem.requireBoardCoordinate(x);
        PcbCoordinateSystem.requireBoardCoordinate(y);
    }

    private static String sourceFingerprint(PcbBoardHole hole) {
        String source = hole.fingerprint();
        String expected = fingerprint(hole.id, hole.kind, hole.netId, hole.x, hole.y,
            hole.drillRadius, hole.landRadius, hole.exposure);
        if (!expected.equals(source))
            throw new IllegalStateException("P07 source-hole fingerprint differs from its immutable fields");
        return source;
    }

    private static String fingerprint(String id, PcbBoardHole.Kind kind, String netId,
            int x, int y, int drillRadius, int landRadius,
            PcbCopperAccess.Exposure exposure) {
        return field(id) + kind.name() + field(netId) + x + "," + y + "," +
            drillRadius + "," + landRadius + "," + exposure.name();
    }

    private static String field(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static boolean fingerprintMatches(PcbBoardHole hole, String claimed) {
        if (hole == null || claimed == null) return false;
        return fingerprint(hole.id, hole.kind, hole.netId, hole.x, hole.y,
            hole.drillRadius, hole.landRadius, hole.exposure).equals(claimed);
    }

    private static String json(String value) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') result.append('\\').append(c);
            else if (c == '\b') result.append("\\b");
            else if (c == '\f') result.append("\\f");
            else if (c == '\n') result.append("\\n");
            else if (c == '\r') result.append("\\r");
            else if (c == '\t') result.append("\\t");
            else if (c < 32 || c == 127) {
                result.append("\\u00").append(HEX.charAt((c >>> 4) & 15)).append(HEX.charAt(c & 15));
            } else result.append(c);
        }
        return result.toString();
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int octet = b & 255;
                result.append(HEX.charAt(octet >>> 4)).append(HEX.charAt(octet & 15));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static void runContracts() {
        checks = 0;
        rowCodecFixtures();
        cursorAndAggregateFixtures();
        publisherInsertionFixture();
        nonemptyInputRejected();
        System.out.println("PASS: P07 prefix-allocation contracts assertions=" + checks);
    }

    private static void rowCodecFixtures() {
        String[] slashOrder = {"LEFT", "RIGHT", "N/with/slash", "N", "N/12", "Ω😀"};
        PcbBoardHole slash = via("p07-via/N/with/slash/12/3", "N/with/slash", 211, 307);
        Row slashRow = Row.snapshot(slash, 0, slashOrder);
        require(slashRow.creatorNetPosition == 2 && slashRow.creatorBranchOrdinal == 12 &&
            slashRow.piece == 3 && slashRow.netId.equals("N/with/slash"),
            "slash-bearing net ID parses at its full known-net boundary");
        require(slashRow.fingerprint.equals(slash.fingerprint()) &&
            fingerprintMatches(slash, slashRow.fingerprint),
            "snapshot preserves the source fingerprint and matches the independent formula");
        require(!fingerprintMatches(slash, slashRow.fingerprint + "!"),
            "a tampered fingerprint is rejected by the independent formula check");

        String[] numericTrapOrder = {"N", "N/12", "N/12/3"};
        PcbBoardHole numericTrap = via("p07-via/N/12/3/4", "N/12", 220, 310);
        Row numericRow = Row.snapshot(numericTrap, 1, numericTrapOrder);
        require(numericRow.creatorNetPosition == 1 && numericRow.creatorBranchOrdinal == 3 &&
            numericRow.piece == 4,
            "numeric-looking slash prefix is not mistaken for an ordinal on a shorter net");

        PcbBoardHole unicode = via("p07-via/Ω😀/7/0", "Ω😀", 230, 320);
        Row unicodeRow = Row.snapshot(unicode, 2, new String[] {"Ω😀", "ΩZ"});
        String unicodeFingerprint = unicode.id.length() + ":" + unicode.id + "VIA" +
            unicode.netId.length() + ":" + unicode.netId + "230,320,2,6,EXPOSED";
        require(unicode.id.length() == "p07-via/Ω😀/7/0".length() &&
            unicodeRow.fingerprint.equals(unicodeFingerprint),
            "fingerprint field lengths use Java UTF-16 code units for supplementary characters");
        require(unicodeRow.creatorNetPosition == 0,
            "source net order is preserved rather than lexically resorted");

        PcbBoardHole currentCreator = via("p07-via/N/5/9", "N", 240, 330);
        Row currentSnapshot = Row.snapshot(currentCreator, 0, new String[] {"N", "OTHER"});
        require(currentSnapshot.creatorBranchOrdinal == 5 && currentSnapshot.piece == 9,
            "row snapshot can observe a creator at the current ordinal for the Publisher microfixture");

        reject(new Runnable() { public void run() {
            Row.snapshot(via("p07-via/N/01/0", "N", 250, 340), 0, new String[] {"N"});
        }}, "noncanonical leading-zero ordinal rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(via("p07-via/N/1/00", "N", 250, 340), 0, new String[] {"N"});
        }}, "noncanonical leading-zero piece rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(via("p07-via/N/0/0", "N", 250, 340), 0, new String[] {"N"});
        }}, "zero creator ordinal rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(via("p07-via/N/1/0/extra", "N", 250, 340), 0, new String[] {"N"});
        }}, "extra ID suffix rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(via("p07-via/N/1/0", "OTHER", 250, 340), 0, new String[] {"N", "OTHER"});
        }}, "encoded owner and immutable hole net mismatch rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(via("p07-via/N/1/0", "N", 250, 340), 0, new String[] {"N", "N"});
        }}, "duplicate known-net order rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(via("p07-via/N/1/0", "N", 250, 340), 0,
                new String[] {"N", String.valueOf(new char[] {'B', 'A', 'D', 1, 'N', 'E', 'T'})});
        }}, "control character in a known net ID rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(plated("p07-via/N/1/0", "N"), 0, new String[] {"N"});
        }}, "non-VIA kind rejected by the P07 allocation row codec");
        reject(new Runnable() { public void run() {
            Row.snapshot(new PcbBoardHole("p07-via/N/1/0", PcbBoardHole.Kind.VIA,
                "N", 250, 340, 3, 7, PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM,
                PcbCopperAccess.Exposure.EXPOSED), 0, new String[] {"N"});
        }}, "non-factory VIA radii rejected");
        reject(new Runnable() { public void run() {
            Row.snapshot(new PcbBoardHole("p07-via/N/1/0", PcbBoardHole.Kind.VIA,
                "N", 250, 340, 2, 6, PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM,
                PcbCopperAccess.Exposure.COVERED), 0, new String[] {"N"});
        }}, "non-exposed VIA rejected");
    }

    private static void cursorAndAggregateFixtures() {
        final PcbBoardLayout layout = emptyLayout();
        PcbBoardHole later = via("p07-via/SECOND/1/1", "SECOND", 310, 320);
        PcbBoardHole earlier = via("p07-via/FIRST/2/0", "FIRST", 210, 220);
        layout.addHole(later);
        layout.addHole(earlier);
        final PcbBoardHole[] canonical = layout.getHoles().toArray(new PcbBoardHole[0]);
        require(canonical[0] == earlier && canonical[1] == later,
            "layout inventory exposes Java TreeMap ID order independent of insertion chronology");
        Context valid = new Context(layout, canonical, new String[] {"SECOND", "FIRST", "ZERO"},
            3, "ZERO", 10, 1, 48);
        final Cursor cursor = begin(valid);
        reject(new Runnable() { public void run() { cursor.advance(0); } },
            "zero-work cursor advance rejected");
        reject(new Runnable() { public void run() { cursor.advance(32769); } },
            "over-quantum cursor advance rejected");
        drain(cursor, 1);
        require(cursor.isComplete() && cursor.result().totalHoles == 2 &&
            cursor.result().rowAt(0).id.equals(earlier.id) &&
            cursor.result().rowAt(1).id.equals(later.id),
            "resumed scan returns immutable rows in canonical ID order");
        require(cursor.result().kindCount(PcbBoardHole.Kind.VIA) == 2 &&
            cursor.result().kindCount(PcbBoardHole.Kind.PLATED) == 0 &&
            cursor.result().kindCount(PcbBoardHole.Kind.NON_PLATED) == 0 &&
            cursor.result().netCountAt(0) == 1 && cursor.result().netCountAt(1) == 1 &&
            cursor.result().netCountAt(2) == 0,
            "kind and source-order net totals conserve holes and include zero nets");
        String body = cursor.bodyJson();
        require(body == cursor.bodyJson() && body.contains("\"byKind\":{\"VIA\":2,\"PLATED\":0,\"NON_PLATED\":0}") &&
            body.contains("\"byNet\":[{\"netPosition\":0,\"netId\":\"SECOND\",\"holeCount\":1},{\"netPosition\":1,\"netId\":\"FIRST\",\"holeCount\":1},{\"netPosition\":2,\"netId\":\"ZERO\",\"holeCount\":0}]") &&
            body.contains("\"liveBeforeVerified\":true") && body.contains("\"liveAfterVerified\":true") &&
            body.contains("\"holeIdentitiesVerified\":true") && body.contains("\"lineageVerified\":true"),
            "complete body is immutable and contains closed stability, kind, and ordered-net records");
        require(cursor.lastWork() >= 0 && cursor.result().totalHoles == 2,
            "completed cursor exposes stable result and last-work counter");
        require(cursor.advance(1) && cursor.lastWork() == 0,
            "advancing a completed cursor is idempotent and performs no work");
        require(body.contains("\"rowsScanned\":2") && body.contains("\"netComparisons\":6") &&
            body.contains("\"netTotalsWritten\":3") && body.contains("\"workSteps\":"),
            "work counters report exact rows, every known-net comparison, and each net total");
        long workSteps = jsonNumber(body, "workSteps");
        long calls = jsonNumber(body, "advanceCalls");
        long rowsScanned = jsonNumber(body, "rowsScanned");
        long comparisons = jsonNumber(body, "netComparisons");
        long totalsWritten = jsonNumber(body, "netTotalsWritten");
        require(workSteps >= rowsScanned + comparisons + totalsWritten + 1 &&
            workSteps <= calls * MAX_ADVANCE_WORK && workSteps <= MAX_TOTAL_WORK,
            "work steps include every unordered lineage pair and stay within both unchanged caps");

        final PcbBoardHole[] reverse = {later, earlier};
        final Context wrongOrder = new Context(layout, reverse,
            new String[] {"SECOND", "FIRST", "ZERO"}, 3, "ZERO", 10, 1, 48);
        reject(new Runnable() { public void run() { drain(begin(wrongOrder), 7); } },
            "noncanonical source reference order rejected before row emission");
        final PcbBoardHole[] duplicate = {earlier, earlier};
        final Context duplicateRefs = new Context(layout, duplicate,
            new String[] {"SECOND", "FIRST", "ZERO"}, 3, "ZERO", 10, 1, 48);
        reject(new Runnable() { public void run() { drain(begin(duplicateRefs), 32768); } },
            "duplicate source reference rejected by the live layout identity binding");

        final Context wrongNextIndex = new Context(layout, canonical,
            new String[] {"SECOND", "FIRST", "ZERO"}, 3, "ZERO", 10, 1, 48);
        require(wrongNextIndex.searchNetIndex == 3 && wrongNextIndex.currentNetPosition == 2,
            "search index is next-fetch and current position is index minus one");
        reject(new Runnable() { public void run() {
            new Context(layout, canonical, new String[] {"SECOND", "FIRST", "ZERO"},
                2, "ZERO", 10, 1, 48);
        }}, "current net mismatching the next-fetch position rejected");
        reject(new Runnable() { public void run() {
            new Context(layout, canonical, new String[] {"SECOND", "FIRST", "ZERO"},
                0, "SECOND", 10, 1, 48);
        }}, "zero next-fetch index rejected");
        reject(new Runnable() { public void run() {
            new Context(layout, canonical, new String[] {"SECOND", "FIRST", "ZERO"},
                3, "ZERO", 0, 1, 48);
        }}, "nonpositive current branch ordinal rejected");
        reject(new Runnable() { public void run() {
            new Context(layout, canonical, new String[] {"SECOND", "FIRST", "ZERO"},
                3, "ZERO", 10, 1, 1);
        }}, "prefix larger than via allowance rejected");

        final PcbBoardLayout futureNetLayout = emptyLayout();
        final PcbBoardHole futureNet = via("p07-via/SECOND/1/0", "SECOND", 410, 420);
        futureNetLayout.addHole(futureNet);
        final Context futureNetContext = new Context(futureNetLayout,
            futureNetLayout.getHoles().toArray(new PcbBoardHole[0]),
            new String[] {"FIRST", "SECOND"}, 1, "FIRST", 8, 0, 48);
        reject(new Runnable() { public void run() { drain(begin(futureNetContext), 7); } },
            "future net owner position rejected by strict prefix cursor");

        final PcbBoardLayout futureBranchLayout = emptyLayout();
        final PcbBoardHole futureBranch = via("p07-via/FIRST/9/0", "FIRST", 430, 440);
        futureBranchLayout.addHole(futureBranch);
        final Context futureBranchContext = new Context(futureBranchLayout,
            futureBranchLayout.getHoles().toArray(new PcbBoardHole[0]),
            new String[] {"FIRST", "SECOND"}, 1, "FIRST", 8, 0, 48);
        reject(new Runnable() { public void run() { drain(begin(futureBranchContext), 7); } },
            "same-net future creator ordinal rejected by strict prior-branch cursor");

        final PcbBoardLayout sameNetPriorLayout = emptyLayout();
        final PcbBoardHole sameNetPrior = via("p07-via/FIRST/7/0", "FIRST", 450, 460);
        sameNetPriorLayout.addHole(sameNetPrior);
        final Context sameNetPriorContext = new Context(sameNetPriorLayout,
            sameNetPriorLayout.getHoles().toArray(new PcbBoardHole[0]),
            new String[] {"FIRST", "SECOND"}, 1, "FIRST", 8, 0, 48);
        Cursor sameNetCursor = begin(sameNetPriorContext);
        drain(sameNetCursor, 32768);
        require(sameNetCursor.result().rowAt(0).creatorNetPosition ==
            sameNetPriorContext.currentNetPosition &&
            sameNetCursor.result().rowAt(0).creatorBranchOrdinal == 7,
            "a prior branch on the current net is valid at equal net position");

        final PcbBoardLayout duplicateOrdinalLayout = emptyLayout();
        duplicateOrdinalLayout.addHole(via("p07-via/A/3/0", "A", 470, 480));
        duplicateOrdinalLayout.addHole(via("p07-via/B/3/0", "B", 490, 500));
        final Context duplicateOrdinalContext = new Context(duplicateOrdinalLayout,
            duplicateOrdinalLayout.getHoles().toArray(new PcbBoardHole[0]),
            new String[] {"A", "B", "Z"}, 3, "Z", 20, 0, 48);
        reject(new Runnable() { public void run() { drain(begin(duplicateOrdinalContext), 7); } },
            "one creator branch ordinal cannot claim two distinct nets");

        final PcbBoardLayout backwardOrdinalLayout = emptyLayout();
        backwardOrdinalLayout.addHole(via("p07-via/B/3/0", "B", 510, 520));
        backwardOrdinalLayout.addHole(via("p07-via/A/4/0", "A", 530, 540));
        final Context backwardOrdinalContext = new Context(backwardOrdinalLayout,
            backwardOrdinalLayout.getHoles().toArray(new PcbBoardHole[0]),
            new String[] {"A", "B", "Z"}, 3, "Z", 20, 0, 48);
        reject(new Runnable() { public void run() { drain(begin(backwardOrdinalContext), 1); } },
            "creator net positions cannot move backward as global branch ordinals increase");

        final PcbBoardLayout sameBranchLayout = emptyLayout();
        sameBranchLayout.addHole(via("p07-via/A/3/0", "A", 550, 560));
        sameBranchLayout.addHole(via("p07-via/A/3/1", "A", 570, 580));
        final Context sameBranchContext = new Context(sameBranchLayout,
            sameBranchLayout.getHoles().toArray(new PcbBoardHole[0]),
            new String[] {"A", "Z"}, 2, "Z", 8, 0, 48);
        Cursor sameBranchCursor = begin(sameBranchContext);
        drain(sameBranchCursor, 1);
        require(sameBranchCursor.result().totalHoles == 2 &&
            sameBranchCursor.result().rowAt(0).creatorBranchOrdinal ==
                sameBranchCursor.result().rowAt(1).creatorBranchOrdinal,
            "multiple pieces on the same creator branch remain valid");
    }

    private static void publisherInsertionFixture() {
        final P06FactoryLinkFixtures.Fixture fixture =
            new P06FactoryLinkFixtures.Fixture(true, 0, 0, true);
        final PcbLayerRoutingPrototype.Policy policy =
            PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER;
        final PcbLayerRoutingPrototype.Session session = PcbLayerRoutingPrototype.begin(
            fixture.board, fixture.layout, policy, P06FactoryLinkFixtures.OBSERVER);
        boolean observed = false;
        int workCalls = 0;
        while (!observed) {
            if (++workCalls > 100000)
                throw new AssertionError("P07 Publisher transition was not reached in the microfixture");
            Object search = read(session, "search");
            Object publisher = search == null ? null : read(search, "publisher");
            if (publisher != null && intField(publisher, "stage") == 5 &&
                    booleanField(publisher, "transition")) {
                Object candidateLayout = read(search, "layout");
                String net = (String) read(publisher, "net");
                int branchOrdinal = intField(search, "branches");
                int piece = intField(publisher, "piece");
                int pathCount = intField(publisher, "pathCount");
                int pieceEnd = intField(publisher, "pieceEnd");
                int expectedPiece = piece + (pathCount > 1 ? 1 : 0);
                Object endNode = invoke(publisher, "nodeAt", new Class<?>[] {int.class},
                    new Object[] {Integer.valueOf(pieceEnd)});
                int endCell = intField(endNode, "cell");
                int expectedX = intMethod(search, "x", endCell);
                int expectedY = intMethod(search, "y", endCell);

                @SuppressWarnings("unchecked")
                Vector<String> searchNets = (Vector<String>) read(search, "nets");
                String[] netOrder = searchNets.toArray(new String[0]);
                int nextFetch = intField(search, "netIndex");
                String currentNet = (String) read(search, "currentNet");
                if (nextFetch < 1 || nextFetch > netOrder.length ||
                        !net.equals(currentNet) || !net.equals(netOrder[nextFetch - 1]))
                    throw new AssertionError("P07 Publisher microfixture lost actual next-fetch net ownership");

                PcbBoardLayout liveLayout = (PcbBoardLayout) candidateLayout;
                Vector<PcbBoardHole> before = liveLayout.getHoles();
                for (PcbBoardHole hole : before) {
                    if (hole.x == expectedX && hole.y == expectedY)
                        throw new AssertionError("First P07 Publisher transition unexpectedly reuses a via coordinate");
                }
                String expectedId = "p07-via/" + net + "/" + branchOrdinal + "/" + expectedPiece;
                String expectedFingerprint = expectedId.length() + ":" + expectedId + "VIA" +
                    net.length() + ":" + net + expectedX + "," + expectedY + ",2,6,EXPOSED";
                int expectedIndex = 0;
                for (PcbBoardHole hole : before)
                    if (hole.id.compareTo(expectedId) < 0) expectedIndex++;
                int netPosition = nextFetch - 1;
                int heldNextFetch = nextFetch;
                String heldCurrentNet = currentNet;
                Vector<String> heldSearchNets = new Vector<String>(searchNets);
                PcbLayerRoutingPrototype.Session heldSession = session;
                heldSession.advanceSlice(1);

                Vector<PcbBoardHole> after = liveLayout.getHoles();
                PcbBoardHole inserted = null;
                int insertedIndex = -1;
                for (int i = 0; i < after.size(); i++) {
                    boolean existed = false;
                    for (PcbBoardHole old : before) if (old == after.get(i)) existed = true;
                    if (!existed) {
                        if (inserted != null)
                            throw new AssertionError("One Publisher unit inserted multiple prefix holes");
                        inserted = after.get(i);
                        insertedIndex = i;
                    }
                }
                if (after.size() != before.size() + 1 || inserted == null)
                    throw new AssertionError("Actual P07 Publisher transition did not insert exactly one immutable hole");
                Row actual = Row.snapshot(inserted, insertedIndex, netOrder);
                require(actual.id.equals(expectedId) && actual.netId.equals(net) &&
                    actual.index == expectedIndex && insertedIndex == expectedIndex &&
                    actual.kind == PcbBoardHole.Kind.VIA && actual.x == expectedX &&
                    actual.y == expectedY && actual.drillRadius == 2 && actual.landRadius == 6 &&
                    actual.exposure == PcbCopperAccess.Exposure.EXPOSED &&
                    actual.fingerprint.equals(expectedFingerprint) &&
                    actual.creatorNetPosition == netPosition &&
                    actual.creatorBranchOrdinal == branchOrdinal && actual.piece == expectedPiece,
                    "real stage-5 Publisher insertion matches its pre-operation independent expectation");
                require(intField(search, "netIndex") == heldNextFetch &&
                    heldCurrentNet.equals(read(search, "currentNet")) &&
                    heldSearchNets.equals(read(search, "nets")) &&
                    intField(search, "branches") == branchOrdinal,
                    "one Publisher insertion leaves actual net order and branch cursor at the same quiescent owner");
                observed = true;
            } else if (session.advanceSlice(1)) {
                break;
            }
        }
        require(observed, "microfixture observes a real transition before the insertion operation");
        int slices = 0;
        while (!session.advanceSlice(32768)) {
            if (++slices > PcbLayerRoutingPrototype.MAX_EXPANSIONS * 2)
                throw new AssertionError("P06 crossing did not complete within the existing expansion envelope");
        }
        PcbLayerRoutingPrototype.Result result = session.result();
        require(result.accepted() && result.vias == 2 && result.layout.getHoleCount() == 2,
            "real P06 crossing continues to its accepted two-via result");
    }

    private static void nonemptyInputRejected() {
        final P06FactoryLinkFixtures.Fixture fixture =
            new P06FactoryLinkFixtures.Fixture(true, 0, 0, true);
        fixture.layout.addHole(PcbTwoLayerRules.via("p07-via/OVER/1/0", "OVER", 300, 300));
        reject(new Runnable() { public void run() {
            PcbLayerRoutingPrototype.begin(fixture.board, fixture.layout,
                PcbLayerRoutingPrototype.Policy.RESTRICTED_TWO_LAYER,
                P06FactoryLinkFixtures.OBSERVER);
        }}, "real P07 Session rejects nonempty initial holes");
    }

    private static PcbBoardLayout emptyLayout() {
        return new PcbBoardLayout(900, 650, new Rectangle(100, 100, 420, 400),
            new Rectangle(650, 120, 200, 400));
    }

    private static PcbBoardHole via(String id, String net, int x, int y) {
        return PcbTwoLayerRules.via(id, net, x, y);
    }

    private static PcbBoardHole plated(String id, String net) {
        return new PcbBoardHole(id, PcbBoardHole.Kind.PLATED, net, 250, 340, 2, 6,
            PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM, PcbCopperAccess.Exposure.EXPOSED);
    }

    private static void drain(Cursor cursor, int quantum) {
        int calls = 0;
        while (!cursor.advance(quantum)) {
            if (cursor.lastWork() < 1 || cursor.lastWork() > quantum)
                throw new AssertionError("P07 prefix-allocation cursor violated its declared work chunk");
            if (++calls > MAX_FIXTURE_ADVANCE_CALLS)
                throw new AssertionError("P07 prefix-allocation cursor exceeded its bounded call count");
        }
        require(cursor.isComplete() && cursor.result().totalHoles >= 0,
            "bounded cursor reaches a complete immutable result");
    }

    private static Object read(Object target, String name) {
        if (target == null) throw new AssertionError("Missing reflective read target for " + name);
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot read P07 fixture field " + name, failure);
        }
    }

    private static int intField(Object target, String name) {
        return ((Integer) read(target, name)).intValue();
    }

    private static boolean booleanField(Object target, String name) {
        return ((Boolean) read(target, name)).booleanValue();
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes,
            Object[] arguments) {
        try {
            Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot invoke read-only P07 fixture method " + name, failure);
        }
    }

    private static int intMethod(Object target, String name, int argument) {
        return ((Integer) invoke(target, name, new Class<?>[] {int.class},
            new Object[] {Integer.valueOf(argument)})).intValue();
    }

    private static long jsonNumber(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) throw new AssertionError("Missing serialized P07 work counter " + key);
        start += marker.length();
        int end = start;
        while (end < json.length() && json.charAt(end) >= '0' && json.charAt(end) <= '9') end++;
        if (end == start) throw new AssertionError("Invalid serialized P07 work counter " + key);
        return Long.parseLong(json.substring(start, end));
    }

    private static void require(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }

    private static void reject(Runnable action, String reason) {
        boolean rejected = false;
        try { action.run(); }
        catch (IllegalArgumentException expected) { rejected = true; }
        catch (IllegalStateException expected) { rejected = true; }
        require(rejected, reason);
    }
}
