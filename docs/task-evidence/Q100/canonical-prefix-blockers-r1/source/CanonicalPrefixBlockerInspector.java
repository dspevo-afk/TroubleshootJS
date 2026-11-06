package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

/** Native-only bounded observer over the actual Router predicate. */
final class CanonicalPrefixBlockerInspector
        implements ActualGeneratorBranchOrderStudy.TerminalPrefixInspector {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String SCOPE = "CANONICAL_BASE_REQ0_PREFIX_CUT_ONLY";
    private static final String SCHEMA = "tsj-canonical-prefix-blocker-observations-v1";
    private static final String EXCEPTION_MESSAGE = "CANONICAL_PREFIX_INSPECTOR_THROW";
    private static final String FOREIGN = "FOREIGN_OCCUPANCY_REJECTION_RECORDED";
    private static final String UNATTRIBUTED = "BLOCKED_WITHOUT_FOREIGN_OCCUPANCY_REJECTION";
    private static final int MAX_ENTRIES = 1048576;
    private static final int MAX_UNITS = 32768;
    private static final int CHUNK = 4096;
    private static final long MAX_UNIT_NANOS = 5000000000L;
    private static final long MAX_LINE_BYTES = 65536L;

    private final String runId, sourceManifestSha256, wireSha256;
    private final boolean throwAfterPristineCopy;
    private final String[] faceRows = new String[2];
    private final String[] observationRows = new String[CanonicalPrefixCutQueries.COUNT];
    private String prefixGeometryRowsJson, prefixTraceNetCountsJson;
    private String geometryBeforeSha256, geometryAfterSha256, prefixGeometrySha256;
    private String exceptionStage;
    private int prefixGeometryFingerprintUtf8Bytes;
    private int offers, selected, completed, queryCount;
    private int foreignCongestionQueries, unattributedQueries, canaryAssertions;
    private boolean ownersReleased, contextRestored, ownersClosedCallback;
    private boolean ownerReferencesLive;
    private long copyEntries, comparisonEntries, copyUnits, comparisonUnits, queryUnits;
    private long geometryRowEntries, geometryRowUnits, elapsedNanos, maximumUnitNanos;

    CanonicalPrefixBlockerInspector(String runId, String inputSha, String wireSha,
            boolean throwAfterPristineCopy) {
        if (!isUuid(runId) || !isSha256(inputSha) || !isSha256(wireSha))
            throw new IllegalArgumentException("Invalid canonical prefix inspector identity");
        this.runId = runId;
        sourceManifestSha256 = inputSha;
        wireSha256 = wireSha;
        this.throwAfterPristineCopy = throwAfterPristineCopy;
    }

    public void onTerminalPrefix(Object search, PcbBoardLayout layout,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot, String prefixFingerprint,
            String prefixSha256, int prefixBytes, int receiptBranchOrdinal, int groupOrdinal) {
        long started = System.nanoTime();
        ownerReferencesLive = true;
        boolean selectedOffer = false;
        try {
            if (ownersClosedCallback)
                throw new IllegalStateException("Canonical prefix callback followed owner close");
            offers++;
            if (selected != 0)
                throw new IllegalStateException("Canonical prefix selected callback was duplicated");
            if (!isTargetSnapshot(snapshot, receiptBranchOrdinal)) return;
            selected++;
            selectedOffer = true;
            if (selected != 1)
                throw new IllegalStateException("Canonical prefix branch was selected more than once");
            inspectSelected(search, layout, snapshot, prefixFingerprint, prefixSha256,
                prefixBytes, receiptBranchOrdinal, groupOrdinal);
        } finally {
            if (selectedOffer)
                elapsedNanos = saturatedAdd(elapsedNanos, elapsed(started, System.nanoTime()));
            ownerReferencesLive = false;
            search = null;
            layout = null;
            snapshot = null;
            prefixFingerprint = null;
            prefixSha256 = null;
        }
    }

    public void afterOwnersClosed(boolean released, boolean restored) {
        if (ownersClosedCallback)
            throw new IllegalStateException("Canonical prefix owner-close callback was duplicated");
        ownersClosedCallback = true;
        ownersReleased = released;
        contextRestored = restored;
        ownerReferencesLive = false;
        if (offers != 308 || selected != 1)
            throw new IllegalStateException("Canonical prefix source selector did not bind the frozen branch");
    }

    int runCanaries() {
        ownerReferencesLive = true;
        int before = canaryAssertions;
        try {
            runForeignOccupiedCanary();
            runForeignClearanceCanary();
            runSharedClearanceCanary();
            runSameNetOccupiedCanary();
            runSameNetClearanceCanary();
            runCourtyardCanary();
            runEscapeCanary();
            runGuardCanaries();
            return canaryAssertions - before;
        } finally {
            ownerReferencesLive = false;
        }
    }

    String completedJson(String actualGraphSha) {
        requireLifecycle();
        if (throwAfterPristineCopy || exceptionStage != null || completed != 1 ||
                actualGraphSha == null || !CanonicalPrefixCutQueries.GRAPH_SHA.equals(actualGraphSha) ||
                offers != 308 || selected != 1 || queryCount != CanonicalPrefixCutQueries.COUNT ||
                prefixGeometryRowsJson == null || prefixTraceNetCountsJson == null)
            throw new IllegalStateException("Canonical prefix observations are incomplete or graph identity differs");
        for (String row : observationRows)
            if (row == null) throw new IllegalStateException("Canonical prefix observation row is missing");
        String json = observationsJson("COMPLETE_OBSERVATIONS_ONLY", actualGraphSha, null);
        requirePayload(json);
        return json;
    }

    String expectedExceptionJson() {
        requireLifecycle();
        if (!throwAfterPristineCopy || !expectedExceptionObserved() ||
                !"AFTER_PRISTINE_COPY".equals(exceptionStage) || completed != 0 ||
                queryCount != 0 || faceRows[0] == null || prefixGeometryRowsJson == null ||
                prefixTraceNetCountsJson == null)
            throw new IllegalStateException("Expected inspector exception lacks a completed pristine copy");
        String json = observationsJson("EXPECTED_INSPECTOR_THROW_ONLY",
            CanonicalPrefixCutQueries.GRAPH_SHA, exceptionStage);
        requirePayload(json);
        return json;
    }

    boolean expectedExceptionObserved() {
        return throwAfterPristineCopy && "AFTER_PRISTINE_COPY".equals(exceptionStage) &&
            ownersClosedCallback && ownersReleased && contextRestored &&
            !ownerReferencesLive && completed == 0 && queryCount == 0;
    }

    boolean referencesDropped() {
        return !ownerReferencesLive;
    }

    private static boolean isTargetSnapshot(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            int receiptOrdinal) {
        return snapshot != null && receiptOrdinal == 307 &&
            "A_COIL_LOW".equals(snapshot.netId) && "QA.C".equals(snapshot.startPadId) &&
            "DA.A".equals(snapshot.rootPadId);
    }

    private void inspectSelected(Object search, PcbBoardLayout layout,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot, String prefixFingerprint,
            String prefixSha256, int prefixBytes, int receiptBranchOrdinal, int groupOrdinal) {
        if (search == null || layout == null || snapshot == null || prefixFingerprint == null ||
                prefixSha256 == null || groupOrdinal < 0)
            throw new IllegalStateException("Canonical prefix callback omitted actual source data");
        verifySelectedIdentity(search, layout, snapshot, prefixFingerprint, prefixSha256,
            prefixBytes, receiptBranchOrdinal);

        byte[] fingerprintBytes = prefixFingerprint.getBytes(UTF8);
        prefixGeometryFingerprintUtf8Bytes = fingerprintBytes.length;
        prefixGeometrySha256 = sha256(fingerprintBytes);
        if (fingerprintBytes.length != prefixBytes ||
                !prefixGeometrySha256.equals(prefixSha256))
            throw new IllegalStateException("Canonical prefix geometry callback hash or length changed");

        String geometryBefore = layout.geometryFingerprint();
        geometryBeforeSha256 = sha256(geometryBefore.getBytes(UTF8));
        if (!geometryBefore.equals(prefixFingerprint) ||
                !geometryBeforeSha256.equals(prefixGeometrySha256))
            throw new IllegalStateException("Canonical prefix layout differs from its actual branch callback");

        TroubleshootBoard board = getSearchBoard(search);
        GeometryRows geometry = captureGeometryRows(layout, board);
        prefixGeometryRowsJson = geometry.rowsJson;
        prefixTraceNetCountsJson = geometry.netCountsJson;
        geometryRowEntries = geometry.entries;
        geometryRowUnits = geometry.units;
        if (geometry.entries > MAX_ENTRIES)
            throw new IllegalStateException("Canonical prefix geometry rows exceed their entry bound");

        PcbNetRouter.Router[] liveFaces = null;
        PcbNetRouter.Router[] pristineFaces = new PcbNetRouter.Router[2];
        PcbNetRouter.Router[] queryFaces = new PcbNetRouter.Router[2];
        String[] effectsBefore = new String[2];
        String[] effectsBeforeSha = new String[2];
        long[] faceCopyEntries = new long[2];
        long[] faceComparisonEntries = new long[2];
        long[] faceCopyUnits = new long[2];
        long[] faceComparisonUnits = new long[2];
        int[] queryCounts = new int[2];
        boolean[] liveBeforeEqual = new boolean[2];
        boolean[] queryBeforeEqual = new boolean[2];
        boolean[] liveAfterEqual = new boolean[2];
        PcbPadPlacement start = null, root = null;
        PcbPadPlacement[] reached = null;
        Set<String> knownNets = null;
        String afterGeometry = null;
        String sourceNet = null;
        try {
            // Own only this reference wrapper; never clear Search.faces in cleanup.
            liveFaces = ((PcbNetRouter.Router[])readField(search, "faces")).clone();
            if (liveFaces == null || liveFaces.length != 2)
                throw new IllegalStateException("Canonical prefix search does not own two actual Router faces");
            knownNets = new HashSet<String>(board.getNetIds());
            String beforeCopyGeometry = layout.geometryFingerprint();
            if (!beforeCopyGeometry.equals(geometryBefore))
                throw new IllegalStateException("Canonical prefix geometry changed during row capture");

            for (int layer = 0; layer < 2; layer++) {
                PcbNetRouter.Router live = liveFaces[layer];
                if (live == null)
                    throw new IllegalStateException("Canonical prefix search lost a Router face");
                effectsBefore[layer] = live.fixedGraphObservationEffects();
                effectsBeforeSha[layer] = sha256(effectsBefore[layer].getBytes(UTF8));

                CopyResult pristineCopy = copyBounded(live);
                pristineFaces[layer] = pristineCopy.router;
                faceCopyEntries[layer] = saturatedAdd(faceCopyEntries[layer], pristineCopy.entries);
                faceCopyUnits[layer] = saturatedAdd(faceCopyUnits[layer], pristineCopy.units);
                ComparisonResult liveBefore = compareBounded(live, pristineFaces[layer]);
                faceComparisonEntries[layer] = saturatedAdd(faceComparisonEntries[layer], liveBefore.entries);
                faceComparisonUnits[layer] = saturatedAdd(faceComparisonUnits[layer], liveBefore.units);
                liveBeforeEqual[layer] = liveBefore.equal;
                if (!liveBeforeEqual[layer])
                    throw new IllegalStateException("Live Router differs from its pristine observation copy");

                if (throwAfterPristineCopy && layer == 0) {
                    ComparisonResult liveAfterThrow = compareBounded(live, pristineFaces[layer]);
                    faceComparisonEntries[layer] =
                        saturatedAdd(faceComparisonEntries[layer], liveAfterThrow.entries);
                    faceComparisonUnits[layer] =
                        saturatedAdd(faceComparisonUnits[layer], liveAfterThrow.units);
                    liveAfterEqual[layer] = liveAfterThrow.equal;
                    if (!liveAfterEqual[layer] ||
                            !effectsBefore[layer].equals(live.fixedGraphObservationEffects()))
                        throw new IllegalStateException("Live Router changed before inspector exception");
                    queryBeforeEqual[layer] = false;
                    ensureGeometryStable(layout, geometryBefore, geometryBeforeSha256);
                    faceRows[layer] = faceJson(layer, effectsBeforeSha[layer],
                        sha256(live.fixedGraphObservationEffects().getBytes(UTF8)),
                        liveBeforeEqual[layer], false, liveAfterEqual[layer],
                        faceCopyEntries[layer], faceComparisonEntries[layer],
                        faceCopyUnits[layer], faceComparisonUnits[layer], 0);
                    exceptionStage = "AFTER_PRISTINE_COPY";
                    throw new IllegalStateException(EXCEPTION_MESSAGE);
                }

                CopyResult queryCopy = copyBounded(live);
                queryFaces[layer] = queryCopy.router;
                faceCopyEntries[layer] = saturatedAdd(faceCopyEntries[layer], queryCopy.entries);
                faceCopyUnits[layer] = saturatedAdd(faceCopyUnits[layer], queryCopy.units);
                ComparisonResult queryBefore = compareBounded(queryFaces[layer], pristineFaces[layer]);
                faceComparisonEntries[layer] =
                    saturatedAdd(faceComparisonEntries[layer], queryBefore.entries);
                faceComparisonUnits[layer] =
                    saturatedAdd(faceComparisonUnits[layer], queryBefore.units);
                queryBeforeEqual[layer] = queryBefore.equal;
                if (!queryBeforeEqual[layer])
                    throw new IllegalStateException("Fresh query Router differs from the untouched pristine copy");
            }

            Object branch = readField(search, "branch");
            start = (PcbPadPlacement)readField(branch, "start");
            root = (PcbPadPlacement)readField(branch, "root");
            reached = (PcbPadPlacement[])readField(branch, "reachedTerminals");
            sourceNet = stringField(readField(branch, "net"), "actual branch net");
            if (start == null || root == null || reached == null)
                throw new IllegalStateException("Canonical prefix branch lost its actual endpoint data");

            for (int ordinal = 0; ordinal < CanonicalPrefixCutQueries.COUNT; ordinal++) {
                int fromState = CanonicalPrefixCutQueries.fromState(ordinal);
                int toState = CanonicalPrefixCutQueries.toState(ordinal);
                int bit = CanonicalPrefixCutQueries.bit(ordinal);
                int layer = fromState / CanonicalPrefixCutQueries.CELLS;
                if (layer < 0 || layer > 1 ||
                        toState / CanonicalPrefixCutQueries.CELLS != layer ||
                        queryFaces[layer] == null)
                    throw new IllegalStateException("Canonical prefix cut query is not on an actual copied face");
                int fromCell = fromState % CanonicalPrefixCutQueries.CELLS;
                int toCell = toState % CanonicalPrefixCutQueries.CELLS;
                int fromGridX = fromCell % CanonicalPrefixCutQueries.WIDTH;
                int fromGridY = fromCell / CanonicalPrefixCutQueries.WIDTH;
                int toGridX = toCell % CanonicalPrefixCutQueries.WIDTH;
                int toGridY = toCell / CanonicalPrefixCutQueries.WIDTH;
                verifyDirection(fromGridX, fromGridY, toGridX, toGridY, bit);
                int minX = intField(search, "minX");
                int minY = intField(search, "minY");
                int fromX = minX + fromGridX * CanonicalPrefixCutQueries.PITCH;
                int fromY = minY + fromGridY * CanonicalPrefixCutQueries.PITCH;
                int toX = minX + toGridX * CanonicalPrefixCutQueries.PITCH;
                int toY = minY + toGridY * CanonicalPrefixCutQueries.PITCH;
                long queryStarted = System.nanoTime();
                QueryObservation observed;
                try {
                    observed = observeQuery(ordinal, layer, fromState, toState,
                        bit, fromX, fromY, toX, toY, sourceNet, start, root, reached,
                        queryFaces[layer], knownNets);
                } finally {
                    recordUnit(elapsed(queryStarted, System.nanoTime()));
                    queryUnits++;
                    requireObservationUnitBound();
                }
                observationRows[ordinal] = observed.json;
                queryCounts[layer]++;
                queryCount++;
                if (observed.congestionDelta > 0) foreignCongestionQueries++;
                else unattributedQueries++;
            }

            for (int layer = 0; layer < 2; layer++) {
                PcbNetRouter.Router live = liveFaces[layer];
                ComparisonResult after = compareBounded(live, pristineFaces[layer]);
                faceComparisonEntries[layer] =
                    saturatedAdd(faceComparisonEntries[layer], after.entries);
                faceComparisonUnits[layer] =
                    saturatedAdd(faceComparisonUnits[layer], after.units);
                liveAfterEqual[layer] = after.equal;
                if (!liveAfterEqual[layer])
                    throw new IllegalStateException("Live Router changed during disposable prefix queries");
                String effectsAfter = live.fixedGraphObservationEffects();
                if (!effectsBefore[layer].equals(effectsAfter))
                    throw new IllegalStateException("Live Router effects changed during disposable prefix queries");
                faceRows[layer] = faceJson(layer, effectsBeforeSha[layer],
                    sha256(effectsAfter.getBytes(UTF8)), liveBeforeEqual[layer],
                    queryBeforeEqual[layer], liveAfterEqual[layer], faceCopyEntries[layer],
                    faceComparisonEntries[layer], faceCopyUnits[layer],
                    faceComparisonUnits[layer], queryCounts[layer]);
            }

            afterGeometry = layout.geometryFingerprint();
            geometryAfterSha256 = sha256(afterGeometry.getBytes(UTF8));
            if (!afterGeometry.equals(geometryBefore) ||
                    !geometryAfterSha256.equals(geometryBeforeSha256) ||
                    !geometryAfterSha256.equals(prefixGeometrySha256))
                throw new IllegalStateException("Canonical prefix geometry changed during copied Router queries");
            if (queryCount != CanonicalPrefixCutQueries.COUNT)
                throw new IllegalStateException("Canonical prefix cut query set is incomplete");
            completed++;
        } finally {
            if (liveFaces != null) {
                liveFaces[0] = null;
                liveFaces[1] = null;
            }
            liveFaces = null;
            pristineFaces[0] = null;
            pristineFaces[1] = null;
            queryFaces[0] = null;
            queryFaces[1] = null;
            effectsBefore[0] = null;
            effectsBefore[1] = null;
            effectsBeforeSha[0] = null;
            effectsBeforeSha[1] = null;
            start = null;
            root = null;
            reached = null;
            sourceNet = null;
            knownNets = null;
            afterGeometry = null;
            geometryBefore = null;
            board = null;
            search = null;
            layout = null;
            snapshot = null;
        }
    }

    private void verifySelectedIdentity(Object search, PcbBoardLayout layout,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot, String prefixFingerprint,
            String prefixSha256, int prefixBytes, int receiptBranchOrdinal) {
        Object branch = readField(search, "branch");
        if (receiptBranchOrdinal != 307 || prefixBytes != 265894 ||
                intField(snapshot, "pass") != 2 ||
                intField(snapshot, "branchExpanded") != 25 ||
                intField(snapshot, "totalExpanded") != 918222 ||
                intField(snapshot, "frontierCount") != 0 ||
                intField(snapshot, "prefixViaCount") != 48 ||
                !"NO_PATH".equals(stringField(readField(snapshot, "terminalReason"), "terminal reason")) ||
                intField(snapshot, "pass") != intField(search, "pass") ||
                !"A_COIL_LOW".equals(snapshot.netId) ||
                !"QA.C".equals(snapshot.startPadId) ||
                !"DA.A".equals(snapshot.rootPadId) ||
                branch == null || intField(branch, "branchOrdinal") != 77 ||
                !"A_COIL_LOW".equals(stringField(readField(branch, "net"), "branch net")) ||
                !"QA.C".equals(padId(readField(branch, "start"))) ||
                !"DA.A".equals(padId(readField(branch, "root"))) ||
                readField(search, "layout") != layout ||
                intField(search, "width") != CanonicalPrefixCutQueries.WIDTH ||
                intField(search, "height") != CanonicalPrefixCutQueries.HEIGHT ||
                intField(search, "cells") != CanonicalPrefixCutQueries.CELLS ||
                intField(search, "primary") != CanonicalPrefixCutQueries.PRIMARY ||
                intField(search, "minX") != CanonicalPrefixCutQueries.ORIGIN_X ||
                intField(search, "minY") != CanonicalPrefixCutQueries.ORIGIN_Y)
            throw new IllegalStateException("Canonical prefix actual Search/Branch identity changed");
        if (readField(search, "policy") != PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER ||
                snapshot.gridWidth != CanonicalPrefixCutQueries.WIDTH ||
                snapshot.gridHeight != CanonicalPrefixCutQueries.HEIGHT ||
                !CanonicalPrefixCutQueries.PREFIX_SHA.equals(prefixSha256) ||
                prefixBytes < 1 || prefixBytes > 1024 * 1024 ||
                prefixFingerprint.length() < 1)
            throw new IllegalStateException("Canonical prefix source policy or frozen input binding changed");
        PcbPadPlacement[] terminals = (PcbPadPlacement[])readField(branch, "reachedTerminals");
        if (terminals == null)
            throw new IllegalStateException("Canonical prefix source omitted reached terminals");
        for (PcbPadPlacement terminal : terminals)
            if (terminal == null)
                throw new IllegalStateException("Canonical prefix source has a null reached terminal");
    }

    private QueryObservation observeQuery(int ordinal, int layer, int fromState, int toState,
            int bit, int fromX, int fromY, int toX, int toY, String net,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
            PcbNetRouter.Router query, Set<String> knownNets) {
        int routerBefore = query.congestionRejections;
        int workBefore = workCongestion(query);
        TreeMap<String,Integer> conflictsBefore = snapshotConflicts(query.conflicts);
        requireKnownNets(conflictsBefore, knownNets);
        String[][] occupied = stringGrid(query, "occupiedNet");
        String[][] clearance = stringGrid(query, "clearanceNet");
        int gridX = (toX - CanonicalPrefixCutQueries.ORIGIN_X) / CanonicalPrefixCutQueries.PITCH;
        int gridY = (toY - CanonicalPrefixCutQueries.ORIGIN_Y) / CanonicalPrefixCutQueries.PITCH;
        if (gridX < 0 || gridY < 0 || gridX >= CanonicalPrefixCutQueries.WIDTH ||
                gridY >= CanonicalPrefixCutQueries.HEIGHT)
            throw new IllegalStateException("Canonical prefix target is outside its copied Router face");
        String targetOccupied = occupied[gridX][gridY];
        String targetClearance = clearance[gridX][gridY];
        requireKnownOwner(targetOccupied, knownNets, false);
        requireKnownOwner(targetClearance, knownNets, true);
        List<Neighbor> neighbors = occupiedNeighbors(occupied, gridX, gridY);
        for (Neighbor neighbor : neighbors)
            if (!knownNets.contains(neighbor.netId))
                throw new IllegalStateException("Router neighbor references a net outside the board census");

        boolean permits = query.permitsLayerStep(fromX, fromY, toX, toY,
            net, start, root, reached);
        int routerAfter = query.congestionRejections;
        int workAfter = workCongestion(query);
        TreeMap<String,Integer> conflictsAfter = snapshotConflicts(query.conflicts);
        requireKnownNets(conflictsAfter, knownNets);
        TreeMap<String,Integer> conflictsDelta = conflictsDelta(conflictsBefore, conflictsAfter);
        int routerDelta = routerAfter - routerBefore;
        int workDelta = workAfter - workBefore;
        if (permits)
            throw new IllegalStateException("Actual Router predicate permitted a captured false cut bit");
        if (routerDelta < 0 || workDelta != routerDelta)
            throw new IllegalStateException("Router and work congestion deltas disagree");
        TreeMap<String,Integer> foreignHistogram = neighborHistogram(neighbors, net);
        verifyConflictDelta(conflictsDelta, foreignHistogram, routerDelta, net);
        if (routerDelta > 0 && !hasForeignTargetOwner(targetOccupied, targetClearance, net))
            throw new IllegalStateException("Positive congestion delta has no foreign target owner");

        QueryObservation result = new QueryObservation();
        result.congestionDelta = routerDelta;
        String classification = routerDelta > 0 ? FOREIGN : UNATTRIBUTED;
        StringBuilder json = new StringBuilder(640);
        json.append("{\"ordinal\":").append(ordinal)
            .append(",\"layer\":").append(layer)
            .append(",\"fromState\":").append(fromState)
            .append(",\"toState\":").append(toState)
            .append(",\"bit\":").append(bit)
            .append(",\"fromX\":").append(fromX)
            .append(",\"fromY\":").append(fromY)
            .append(",\"toX\":").append(toX)
            .append(",\"toY\":").append(toY)
            .append(",\"permitsStep\":false")
            .append(",\"routerCongestionBefore\":").append(routerBefore)
            .append(",\"routerCongestionAfter\":").append(routerAfter)
            .append(",\"routerCongestionDelta\":").append(routerDelta)
            .append(",\"workCongestionBefore\":").append(workBefore)
            .append(",\"workCongestionAfter\":").append(workAfter)
            .append(",\"workCongestionDelta\":").append(workDelta)
            .append(",\"conflictsBefore\":").append(countMapJson(conflictsBefore))
            .append(",\"conflictsAfter\":").append(countMapJson(conflictsAfter))
            .append(",\"conflictsDelta\":").append(countMapJson(conflictsDelta))
            .append(",\"targetOccupiedNet\":").append(quoteOrNull(targetOccupied))
            .append(",\"targetClearanceNet\":").append(quoteOrNull(targetClearance))
            .append(",\"neighborOccupiedNets\":").append(neighborsJson(neighbors))
            .append(",\"classification\":").append(quote(classification)).append('}');
        result.json = json.toString();
        return result;
    }

    private String observationsJson(String status, String graphSha, String exception) {
        StringBuilder json = new StringBuilder(32768);
        json.append("{\"schema\":").append(quote(SCHEMA))
            .append(",\"status\":").append(quote(status))
            .append(",\"runId\":").append(quote(runId))
            .append(",\"sourceManifestSha256\":").append(quote(sourceManifestSha256))
            .append(",\"wireSha256\":").append(quote(wireSha256))
            .append(",\"scope\":").append(quote(SCOPE))
            .append(",\"layoutQualification\":false")
            .append(",\"cutInputSha256\":").append(quote(CanonicalPrefixCutQueries.CUT_INPUT_SHA))
            .append(",\"graphSha256\":").append(quote(graphSha))
            .append(",\"prefixGeometrySha256\":").append(quote(prefixGeometrySha256))
            .append(",\"receiptBranchOrdinal\":307")
            .append(",\"lineageBranchOrdinal\":77")
            .append(",\"netId\":\"A_COIL_LOW\"")
            .append(",\"startPadId\":\"QA.C\"")
            .append(",\"rootPadId\":\"DA.A\"")
            .append(",\"lifecycle\":{\"offers\":").append(offers)
            .append(",\"selected\":").append(selected)
            .append(",\"completed\":").append(completed)
            .append(",\"ownersReleased\":").append(ownersReleased)
            .append(",\"contextRestored\":").append(contextRestored)
            .append(",\"sourceRefsDropped\":").append(referencesDropped()).append('}')
            .append(",\"sourceProof\":{\"geometryBeforeSha256\":")
            .append(quote(geometryBeforeSha256))
            .append(",\"geometryAfterSha256\":").append(quote(geometryAfterSha256))
            .append(",\"faces\":").append(faceRowsJson()).append('}')
            .append(",\"prefixGeometryRows\":").append(prefixGeometryRowsJson)
            .append(",\"observations\":").append(observationsJsonArray())
            .append(",\"prefixTraceNetCounts\":").append(prefixTraceNetCountsJson)
            .append(",\"totals\":{\"queryCount\":").append(queryCount)
            .append(",\"foreignCongestionQueries\":").append(foreignCongestionQueries)
            .append(",\"unattributedQueries\":").append(unattributedQueries)
            .append(",\"copyEntries\":").append(copyEntries)
            .append(",\"comparisonEntries\":").append(comparisonEntries)
            .append(",\"copyUnits\":").append(copyUnits)
            .append(",\"comparisonUnits\":").append(comparisonUnits)
            .append(",\"queryUnits\":").append(queryUnits)
            .append(",\"elapsedNanos\":").append(elapsedNanos)
            .append(",\"maximumUnitNanos\":").append(maximumUnitNanos)
            .append(",\"geometryRowEntries\":").append(geometryRowEntries)
            .append(",\"geometryRowUnits\":").append(geometryRowUnits)
            .append("},\"exceptionStage\":")
            .append(exception == null ? "null" : quote(exception)).append('}');
        return json.toString();
    }

    private String observationsJsonArray() {
        StringBuilder json = new StringBuilder(24000);
        json.append('[');
        boolean first = true;
        for (String row : observationRows) {
            if (row == null) continue;
            if (!first) json.append(',');
            first = false;
            json.append(row);
        }
        return json.append(']').toString();
    }

    private String faceRowsJson() {
        StringBuilder json = new StringBuilder(320);
        json.append('[');
        boolean first = true;
        for (String row : faceRows) {
            if (row == null) continue;
            if (!first) json.append(',');
            first = false;
            json.append(row);
        }
        return json.append(']').toString();
    }

    private static String faceJson(int layer, String beforeSha, String afterSha,
            boolean liveBefore, boolean queryBefore, boolean liveAfter, long copyEntries,
            long comparisonEntries, long copyUnits, long comparisonUnits, int queryCount) {
        return new StringBuilder(360).append("{\"layer\":").append(layer)
            .append(",\"sourceEffectsBeforeSha256\":").append(quote(beforeSha))
            .append(",\"sourceEffectsAfterSha256\":").append(quote(afterSha))
            .append(",\"liveToPristineBeforeEqual\":").append(liveBefore)
            .append(",\"queryToPristineBeforeEqual\":").append(queryBefore)
            .append(",\"liveToPristineAfterEqual\":").append(liveAfter)
            .append(",\"copyEntries\":").append(copyEntries)
            .append(",\"comparisonEntries\":").append(comparisonEntries)
            .append(",\"copyUnits\":").append(copyUnits)
            .append(",\"comparisonUnits\":").append(comparisonUnits)
            .append(",\"queryCount\":").append(queryCount).append('}').toString();
    }

    private GeometryRows captureGeometryRows(PcbBoardLayout layout, TroubleshootBoard board) {
        GeometryUnitLedger ledger = new GeometryUnitLedger();
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        Vector<PcbBoardHole> holes = layout.getHoles();
        Collections.sort(traces, TRACE_ORDER);
        Collections.sort(holes, new Comparator<PcbBoardHole>() {
            public int compare(PcbBoardHole first, PcbBoardHole second) {
                return first.id.compareTo(second.id);
            }
        });
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        Set<String> knownNets = new HashSet<String>(netIds);
        TreeMap<String,Integer> counts = new TreeMap<String,Integer>();
        for (String net : netIds) counts.put(net, Integer.valueOf(0));
        long entries = 0;
        try {
            StringBuilder traceJson = new StringBuilder(16000);
            traceJson.append('[');
            for (int index = 0; index < traces.size(); index++) {
                if (index != 0) traceJson.append(',');
                PcbTraceGeometry trace = traces.get(index);
                String netId = trace.getNetId();
                if (netId == null || !knownNets.contains(netId))
                    throw new IllegalStateException("Prefix trace references a net outside the actual board census");
                counts.put(netId, Integer.valueOf(counts.get(netId).intValue() + 1));
                int xLength = coordinateLength(trace, "xPoints");
                int yLength = coordinateLength(trace, "yPoints");
                long rowEntries = 9L + xLength + yLength;
                entries = saturatedAdd(entries, rowEntries);
                if (xLength < 2 || xLength != yLength || rowEntries > MAX_ENTRIES ||
                        entries > MAX_ENTRIES)
                    throw new IllegalStateException("Prefix trace rows exceed their bounded entry envelope");
                StringBuilder rowJson = new StringBuilder((xLength + yLength) * 5 + 128);
                rowJson.append('[');
                ledger.beginScalarEntries(9);
                rowJson.append(quoteOrNull(trace.getSourceId())).append(',')
                    .append(quote(netId)).append(',')
                    .append(quoteOrNull(trace.getStartPadId())).append(',')
                    .append(quoteOrNull(trace.getEndPadId())).append(',')
                    .append(trace.getLayer() == PcbCopperLayer.TOP ? 0 : 1).append(',')
                    .append(quote(trace.getExposure().name())).append(',')
                    .append(trace.getWidth()).append(',');
                ledger.finishScalarEntries(9);
                copyTraceCoordinates(trace, "xPoints", xLength, ledger, rowJson);
                rowJson.append(',');
                copyTraceCoordinates(trace, "yPoints", yLength, ledger, rowJson);
                rowJson.append(']');
                traceJson.append(rowJson.toString());
                rowJson = null;
                trace = null;
            }
            traceJson.append(']');
            StringBuilder holeJson = new StringBuilder(1024);
            holeJson.append('[');
            for (int index = 0; index < holes.size(); index++) {
                if (index != 0) holeJson.append(',');
                PcbBoardHole hole = holes.get(index);
                entries = saturatedAdd(entries, 8L);
                if (entries > MAX_ENTRIES)
                    throw new IllegalStateException("Prefix hole rows exceed their bounded entry envelope");
                ledger.beginScalarEntries(8);
                holeJson.append('[').append(quote(hole.id)).append(',')
                    .append(quote(hole.kind.name())).append(',')
                    .append(quoteOrNull(hole.netId)).append(',')
                    .append(hole.x).append(',').append(hole.y).append(',')
                    .append(hole.drillRadius).append(',').append(hole.landRadius).append(',')
                    .append(quote(hole.exposure.name())).append(']');
                ledger.finishScalarEntries(8);
                hole = null;
            }
            holeJson.append(']');
            ledger.finish();
            if (ledger.entries != entries ||
                    ledger.units != (entries + CHUNK - 1L) / CHUNK)
                throw new IllegalStateException("Prefix geometry row entry/unit accounting diverged");
            requireObservationUnitBound();
            StringBuilder countJson = new StringBuilder(1024);
            countJson.append('[');
            for (int index = 0; index < netIds.size(); index++) {
                if (index != 0) countJson.append(',');
                String net = netIds.get(index);
                countJson.append("{\"netId\":").append(quote(net))
                    .append(",\"traceCount\":").append(counts.get(net)).append('}');
            }
            countJson.append(']');
            GeometryRows result = new GeometryRows();
            result.rowsJson = "{\"traces\":" + traceJson.toString() +
                ",\"holes\":" + holeJson.toString() + "}";
            result.netCountsJson = countJson.toString();
            result.entries = entries;
            result.units = ledger.units;
            traces.clear();
            holes.clear();
            netIds.clear();
            knownNets.clear();
            counts.clear();
            traceJson = null;
            holeJson = null;
            countJson = null;
            return result;
        } finally {
            traces.clear();
            holes.clear();
            netIds.clear();
            knownNets.clear();
            counts.clear();
        }
    }

    private void copyTraceCoordinates(PcbTraceGeometry trace, String name, int length,
            GeometryUnitLedger ledger, StringBuilder json) {
        int[] source = (int[])readField(trace, name);
        if (source == null || source.length != length)
            throw new IllegalStateException("Prefix trace coordinate array changed during capture");
        json.append('[');
        int offset = 0;
        while (offset < length) {
            int count = ledger.nextArrayChunk(length - offset);
            for (int index = 0; index < count; index++) {
                if (offset + index != 0) json.append(',');
                json.append(source[offset + index]);
            }
            offset += count;
            ledger.finishArrayChunk(count);
        }
        json.append(']');
        source = null;
    }

    private static int coordinateLength(PcbTraceGeometry trace, String name) {
        Object value = readField(trace, name);
        if (!(value instanceof int[]))
            throw new IllegalStateException("Prefix trace coordinate field is unavailable");
        return ((int[])value).length;
    }

    private final class GeometryUnitLedger {
        long entries, units;
        int currentEntries;
        long started;

        int nextArrayChunk(int remaining) {
            ensureUnit();
            return Math.min(remaining, CHUNK - currentEntries);
        }
        void finishArrayChunk(int count) {
            if (count < 1 || currentEntries + count > CHUNK)
                throw new IllegalStateException("Prefix geometry copy exceeded its checked unit");
            currentEntries += count;
            entries = saturatedAdd(entries, count);
            if (currentEntries == CHUNK) closeUnit();
        }
        void beginScalarEntries(int count) {
            if (count < 1 || count > CHUNK)
                throw new IllegalStateException("Invalid prefix geometry scalar row size");
            if (currentEntries + count > CHUNK) closeUnit();
            ensureUnit();
        }
        void finishScalarEntries(int count) {
            if (count < 1 || currentEntries + count > CHUNK)
                throw new IllegalStateException("Prefix geometry scalar row exceeded its checked unit");
            currentEntries += count;
            entries = saturatedAdd(entries, count);
            if (currentEntries == CHUNK) closeUnit();
        }
        private void ensureUnit() {
            if (currentEntries == 0) started = System.nanoTime();
        }
        private void closeUnit() {
            if (currentEntries == 0) return;
            units++;
            recordUnit(elapsed(started, System.nanoTime()));
            currentEntries = 0;
            started = 0;
        }
        void finish() { closeUnit(); }
    }

    private static final Comparator<PcbTraceGeometry> TRACE_ORDER =
        new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry first, PcbTraceGeometry second) {
                int result = first.getNetId().compareTo(second.getNetId());
                if (result != 0) return result;
                result = String.valueOf(first.getStartPadId()).compareTo(
                    String.valueOf(second.getStartPadId()));
                if (result != 0) return result;
                result = String.valueOf(first.getEndPadId()).compareTo(
                    String.valueOf(second.getEndPadId()));
                if (result != 0) return result;
                result = first.getLayer().compareTo(second.getLayer());
                if (result != 0) return result;
                result = first.getWidth() < second.getWidth() ? -1 :
                    first.getWidth() == second.getWidth() ? 0 : 1;
                return result != 0 ? result : String.valueOf(first.getSourceId()).compareTo(
                    String.valueOf(second.getSourceId()));
            }
        };

    private CopyResult copyBounded(PcbNetRouter.Router source) {
        long unitsBefore = copyUnits;
        PcbNetRouter.Router.FixedGraphCopyTask task = null;
        try {
            long started = System.nanoTime();
            task = source.beginFixedGraphObservationCopy();
            recordUnit(elapsed(started, System.nanoTime()));
            copyUnits++;
            requireObservationUnitBound();
            int previousEntries = 0;
            for (;;) {
                started = System.nanoTime();
                boolean done = task.advanceOne(CHUNK);
                int entries = task.copiedEntries();
                if (entries < previousEntries || entries > MAX_ENTRIES)
                    throw new IllegalStateException("Router observation clone exceeded its one-million-entry bound");
                copyEntries = saturatedAdd(copyEntries, entries - previousEntries);
                previousEntries = entries;
                recordUnit(elapsed(started, System.nanoTime()));
                copyUnits++;
                requireObservationUnitBound();
                if (done) {
                    CopyResult result = new CopyResult();
                    result.router = task.result();
                    result.entries = entries;
                    result.units = copyUnits - unitsBefore;
                    task = null;
                    return result;
                }
            }
        } finally {
            task = null;
            source = null;
        }
    }

    private ComparisonResult compareBounded(PcbNetRouter.Router first,
            PcbNetRouter.Router second) {
        long unitsBefore = comparisonUnits;
        PcbNetRouter.Router.FixedGraphComparisonTask task = null;
        try {
            long started = System.nanoTime();
            task = first.beginFixedGraphComparison(second);
            recordUnit(elapsed(started, System.nanoTime()));
            comparisonUnits++;
            requireObservationUnitBound();
            int previousEntries = 0;
            for (;;) {
                started = System.nanoTime();
                boolean done = task.advanceOne(CHUNK);
                int entries = task.comparedEntries();
                if (entries < previousEntries)
                    throw new IllegalStateException("Router observation comparison counter regressed");
                comparisonEntries = saturatedAdd(comparisonEntries, entries - previousEntries);
                previousEntries = entries;
                recordUnit(elapsed(started, System.nanoTime()));
                comparisonUnits++;
                requireObservationUnitBound();
                if (done) {
                    ComparisonResult result = new ComparisonResult();
                    result.equal = task.equal();
                    result.entries = entries;
                    result.units = comparisonUnits - unitsBefore;
                    task = null;
                    return result;
                }
            }
        } finally {
            task = null;
            first = null;
            second = null;
        }
    }
    private void runForeignOccupiedCanary() {
        CanariesFixture fixture = new CanariesFixture(false, false);
        PcbNetRouter.Router source = fixture.router;
        Vector<Point> literal = new Vector<Point>();
        literal.add(new Point(300, 250));
        source.occupyLayerPath(literal, "FOREIGN");
        CanaryResult result = canaryProbe(fixture, source, 290, 250, 300, 250);
        canaryCheck(!result.permits && result.routerDelta == 1 && result.workDelta == 1 &&
            "FOREIGN".equals(result.targetOccupied) &&
            Integer.valueOf(1).equals(result.conflictDelta.get("FOREIGN")),
            "foreign occupied-cell canary records the literal copper owner", result);
        canaryCheck(result.sourceStable, "occupied-cell probe preserves source state and effects", result);
        literal.clear();
        result = null;
        source = null;
        fixture = null;
    }

    private void runForeignClearanceCanary() {
        CanariesFixture fixture = new CanariesFixture(false, false);
        PcbNetRouter.Router source = fixture.router;
        Vector<Point> literal = new Vector<Point>();
        literal.add(new Point(300, 250));
        source.occupyLayerPath(literal, "FOREIGN");
        CanaryResult result = canaryProbe(fixture, source, 290, 260, 300, 260);
        canaryCheck(!result.permits && result.routerDelta == 1 && result.workDelta == 1 &&
            result.targetOccupied == null && "FOREIGN".equals(result.targetClearance) &&
            Integer.valueOf(1).equals(result.conflictDelta.get("FOREIGN")),
            "foreign clearance-halo canary records adjacent literal copper", result);
        canaryCheck(result.sourceStable, "clearance-halo probe preserves source state and effects", result);
        literal.clear();
        result = null;
        source = null;
        fixture = null;
    }

    private void runSharedClearanceCanary() {
        CanariesFixture fixture = new CanariesFixture(false, false);
        PcbNetRouter.Router source = fixture.router;
        Vector<Point> firstOwner = new Vector<Point>();
        firstOwner.add(new Point(300, 250));
        Vector<Point> secondOwner = new Vector<Point>();
        secondOwner.add(new Point(300, 270));
        source.occupyLayerPath(firstOwner, "A");
        source.occupyLayerPath(secondOwner, "AB");
        CanaryResult result = canaryProbe(fixture, source, 290, 260, 300, 260);
        canaryCheck(!result.permits && result.routerDelta == 1 && result.workDelta == 1 &&
            result.targetOccupied == null && "".equals(result.targetClearance) &&
            Integer.valueOf(1).equals(result.conflictDelta.get("A")) &&
            Integer.valueOf(1).equals(result.conflictDelta.get("AB")) &&
            result.conflictDelta.size() == 2,
            "literal shared-clearance sentinel retains both actual occupied-net owners", result);
        canaryCheck(result.sourceStable,
            "shared-clearance probe preserves source state and effects", result);
        firstOwner.clear();
        secondOwner.clear();
        result = null;
        source = null;
        fixture = null;
    }
    private void runSameNetOccupiedCanary() {
        CanariesFixture fixture = new CanariesFixture(false, false);
        PcbNetRouter.Router source = fixture.router;
        Vector<Point> literal = new Vector<Point>();
        literal.add(new Point(300, 250));
        source.occupyLayerPath(literal, "QUERY");
        CanaryResult result = canaryProbe(fixture, source, 290, 250, 300, 250);
        canaryCheck(result.permits && result.routerDelta == 0 && result.workDelta == 0 &&
            "QUERY".equals(result.targetOccupied),
            "same-net copper canary remains legal without foreign congestion", result);
        canaryCheck(result.sourceStable, "same-net copper probe preserves source state and effects", result);
        literal.clear();
        result = null;
        source = null;
        fixture = null;
    }

    private void runSameNetClearanceCanary() {
        CanariesFixture fixture = new CanariesFixture(false, false);
        PcbNetRouter.Router source = fixture.router;
        Vector<Point> literal = new Vector<Point>();
        literal.add(new Point(300, 250));
        source.occupyLayerPath(literal, "QUERY");
        CanaryResult result = canaryProbe(fixture, source, 290, 260, 300, 260);
        canaryCheck(result.permits && result.routerDelta == 0 && result.workDelta == 0 &&
            result.targetOccupied == null && "QUERY".equals(result.targetClearance),
            "same-net clearance canary remains legal without foreign congestion", result);
        canaryCheck(result.sourceStable, "same-net halo probe preserves source state and effects", result);
        literal.clear();
        result = null;
        source = null;
        fixture = null;
    }

    private void runCourtyardCanary() {
        CanariesFixture fixture = new CanariesFixture(true, false);
        PcbNetRouter.Router source = fixture.router;
        CanaryResult result = canaryProbe(fixture, source, 300, 250, 310, 250);
        canaryCheck(!result.permits && result.routerDelta == 0 && result.workDelta == 0 &&
            result.targetOccupied == null && result.targetClearance == null &&
            result.conflictDelta.isEmpty(),
            "non-copper courtyard canary rejects without foreign congestion", result);
        canaryCheck(result.sourceStable, "courtyard probe preserves source state and effects", result);
        result = null;
        source = null;
        fixture = null;
    }

    private void runEscapeCanary() {
        CanariesFixture fixture = new CanariesFixture(false, true);
        PcbNetRouter.Router source = fixture.router;
        CanaryResult result = canaryProbe(fixture, source, 220, 250, 210, 250);
        canaryCheck(!result.permits && result.routerDelta == 0 && result.workDelta == 0 &&
            result.targetOccupied == null && result.targetClearance == null &&
            result.conflictDelta.isEmpty(),
            "non-copper pad-escape canary rejects without foreign congestion", result);
        canaryCheck(result.sourceStable, "escape probe preserves source state and effects", result);
        result = null;
        source = null;
        fixture = null;
    }

    private void runGuardCanaries() {
        canaryCheck(constructorRejects("invalid", sourceManifestSha256, wireSha256),
            "constructor rejects a malformed run identity");
        canaryCheck(constructorRejects(runId, "invalid", wireSha256),
            "constructor rejects a malformed input hash");
        canaryCheck(constructorRejects(runId, sourceManifestSha256, "invalid"),
            "constructor rejects a malformed wire hash");

        final CanonicalPrefixBlockerInspector mismatched =
            new CanonicalPrefixBlockerInspector(runId, sourceManifestSha256, wireSha256, false);
        mismatched.onTerminalPrefix(null, null, null, null, null, 0, 307, 0);
        canaryCheck(mismatched.offers == 1 && mismatched.selected == 0 &&
                mismatched.referencesDropped(),
            "mismatched callback identity is offered but never selected");
        canaryCheck(rejectsLifecycle(mismatched, "Canonical prefix source selector did not bind the frozen branch"),
            "mismatched callback identity fails owner-close selection validation");

        final CanonicalPrefixBlockerInspector closed =
            new CanonicalPrefixBlockerInspector(runId, sourceManifestSha256, wireSha256, false);
        boolean firstCloseRejected = rejectsLifecycle(closed,
            "Canonical prefix source selector did not bind the frozen branch");
        boolean duplicateCloseRejected = rejectsLifecycle(closed,
            "Canonical prefix owner-close callback was duplicated");
        canaryCheck(firstCloseRejected && duplicateCloseRejected,
            "owner-close callback rejects incomplete lifecycle and duplicate close");
        final Object opaqueSearch = new Object();
        final PcbBoardLayout opaqueLayout = new PcbBoardLayout(800, 600,
            new Rectangle(100, 100, 400, 400), new Rectangle(550, 120, 180, 300));
        boolean callbackAfterCloseRejected = rejectsCallbackAfterClose(closed,
            opaqueSearch, opaqueLayout);
        canaryCheck(callbackAfterCloseRejected && closed.referencesDropped(),
            "callback after owner close rejects and drops its temporary arguments");

        final CanonicalPrefixBlockerInspector unreleased =
            new CanonicalPrefixBlockerInspector(runId, sourceManifestSha256, wireSha256, false);
        canaryCheck(rejectsCompletionBeforeClose(unreleased) && unreleased.referencesDropped(),
            "completion rejects before owners close and source references are released");
    }

    private boolean constructorRejects(String candidateRunId, String candidateInputSha,
            String candidateWireSha) {
        try {
            new CanonicalPrefixBlockerInspector(candidateRunId, candidateInputSha,
                candidateWireSha, false);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private boolean rejectsLifecycle(final CanonicalPrefixBlockerInspector target,
            final String expectedMessage) {
        try {
            target.afterOwnersClosed(true, true);
            return false;
        } catch (IllegalStateException expected) {
            return expectedMessage.equals(expected.getMessage());
        }
    }

    private boolean rejectsCallbackAfterClose(final CanonicalPrefixBlockerInspector target,
            final Object search, final PcbBoardLayout layout) {
        try {
            target.onTerminalPrefix(search, layout, null, "opaque", "opaque", 0, 0, 0);
            return false;
        } catch (IllegalStateException expected) {
            return "Canonical prefix callback followed owner close".equals(expected.getMessage());
        }
    }

    private boolean rejectsCompletionBeforeClose(CanonicalPrefixBlockerInspector target) {
        try {
            target.completedJson(CanonicalPrefixCutQueries.GRAPH_SHA);
            return false;
        } catch (IllegalStateException expected) {
            return "Canonical prefix owner/context cleanup is incomplete".equals(expected.getMessage());
        }
    }

    private CanaryResult canaryProbe(CanariesFixture fixture,
            PcbNetRouter.Router source, int fromX, int fromY, int toX, int toY) {
        String effectsBefore = source.fixedGraphObservationEffects();
        String geometryBefore = fixture.layout.geometryFingerprint();
        PcbNetRouter.Router pristine = null, query = null;
        try {
            pristine = copyBounded(source).router;
            query = copyBounded(source).router;
            ComparisonResult liveBefore = compareBounded(source, pristine);
            ComparisonResult queryBefore = compareBounded(query, pristine);
            canaryCheck(liveBefore.equal && queryBefore.equal,
                "literal canary copies equal the source before query");
            String[][] occupied = stringGrid(query, "occupiedNet");
            String[][] clearance = stringGrid(query, "clearanceNet");
            int gridX = (toX - 110) / 10;
            int gridY = (toY - 110) / 10;
            String targetOccupied = occupied[gridX][gridY];
            String targetClearance = clearance[gridX][gridY];
            List<Neighbor> neighbors = occupiedNeighbors(occupied, gridX, gridY);
            int routerBefore = query.congestionRejections;
            int workBefore = workCongestion(query);
            TreeMap<String,Integer> conflictsBefore = snapshotConflicts(query.conflicts);
            long queryStarted = System.nanoTime();
            boolean permits;
            try {
                permits = query.permitsLayerStep(fromX, fromY, toX, toY,
                    "QUERY", fixture.start, fixture.root, null);
            } finally {
                recordUnit(elapsed(queryStarted, System.nanoTime()));
                queryUnits++;
                requireObservationUnitBound();
            }
            int routerDelta = query.congestionRejections - routerBefore;
            int workDelta = workCongestion(query) - workBefore;
            TreeMap<String,Integer> conflictsDelta = conflictsDelta(conflictsBefore,
                snapshotConflicts(query.conflicts));
            verifyConflictDelta(conflictsDelta, neighborHistogram(neighbors, "QUERY"),
                routerDelta, "QUERY");
            ComparisonResult liveAfter = compareBounded(source, pristine);
            CanaryResult result = new CanaryResult();
            result.permits = permits;
            result.routerDelta = routerDelta;
            result.workDelta = workDelta;
            result.targetOccupied = targetOccupied;
            result.targetClearance = targetClearance;
            result.conflictDelta = conflictsDelta;
            result.sourceStable = liveBefore.equal && queryBefore.equal && liveAfter.equal &&
                effectsBefore.equals(source.fixedGraphObservationEffects()) &&
                geometryBefore.equals(fixture.layout.geometryFingerprint());
            return result;
        } finally {
            pristine = null;
            query = null;
            fixture = null;
            source = null;
        }
    }

    private static final class CanariesFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        final PcbPadPlacement start, root;
        final PcbNetRouter.Router router;

        CanariesFixture(boolean courtyard, boolean escapedStart) {
            board = new TroubleshootBoard("CANONICAL_PREFIX_BLOCKER_CANARY");
            board.addNet(new BoardNet("QUERY"));
            board.addNet(new BoardNet("FOREIGN"));
            board.addNet(new BoardNet("COURT"));
            board.addNet(new BoardNet("A"));
            board.addNet(new BoardNet("AB"));
            layout = new PcbBoardLayout(800, 600, new Rectangle(100, 100, 400, 400),
                new Rectangle(550, 120, 180, 300));
            addPoint("Q0", "QUERY", 200, 250, escapedStart ? 1 : 0,
                0, escapedStart ? 30 : 0, false);
            start = layout.getPad("Q0.1");
            addPoint("Q1", "QUERY", 450, 350, 0, 0, 0, false);
            root = layout.getPad("Q1.1");
            addPoint("F0", "FOREIGN", 150, 350, 0, 0, 0, false);
            addPoint("F1", "FOREIGN", 450, 300, 0, 0, 0, false);
            addPoint("A0", "A", 150, 150, 0, 0, 0, false);
            addPoint("AB0", "AB", 450, 150, 0, 0, 0, false);
            PcbFootprint court = courtyard ?
                addPoint("C0", "COURT", 290, 250, 0, 0, 0, true) : null;
            if (courtyard && court == null)
                throw new IllegalStateException("Canary courtyard fixture was not built");
            board.setPlacementConstraints(PcbPlacementConstraints.standard(board));
            router = new PcbNetRouter.Router(layout, board, layout.getBoardOutline(), 0,
                NOOP, PcbCopperLayer.TOP);
        }

        private PcbFootprint addPoint(String id, String net, int x, int y,
                int escapeDx, int escapeDy, int escapeLength, boolean place) {
            BoardComponent component = new BoardComponent(id, "TEST_POINT", pointPackage(), id);
            board.addComponent(component);
            board.addPad(new BoardPad(id + ".1", id, "1", net));
            if (place) {
                PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, x - 10, y - 10);
                layout.addComponent(footprint.getPlacement());
                for (PcbPadPlacement pad : footprint.getPads()) layout.addPad(pad);
                return footprint;
            }
            PcbPadPlacement pad = new PcbPadPlacement(id + ".1", x, y, escapeDx, escapeDy,
                escapeLength, new Rectangle(x - 4, y - 4, 8, 8),
                new Rectangle(x - 6, y - 6, 12, 12),
                PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
            layout.addPad(pad);
            return null;
        }
    }

    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    private static PhysicalPackage pointPackage() {
        Point pad = new Point(10, 10);
        Point body = new Point(24, 10);
        PhysicalPackageGeometry.Lead connected = new PhysicalPackageGeometry.Lead(pad, body,
            new Rectangle(8, 8, 18, 4), new Point(22, 10), new Rectangle(20, 8, 6, 4));
        PhysicalPackageGeometry.Lead lifted = new PhysicalPackageGeometry.Lead(
            new Point(32, 10), body, new Rectangle(22, 8, 12, 4),
            new Point(30, 10), new Rectangle(28, 8, 6, 4));
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(new PhysicalPackageGeometry.Terminal("1", pad,
            new Rectangle(6, 6, 8, 8), pad, new Rectangle(5, 5, 10, 10),
            connected, lifted, 0, 0, 0));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(40, 20, terminals,
            new Rectangle(22, 6, 10, 8), new Rectangle(20, 4, 16, 12),
            new Rectangle(0, 0, 40, 20), new Rectangle(0, 0, 40, 20),
            new Rectangle(0, 0, 40, 20));
        return new PhysicalPackage("CANONICAL_PREFIX_TEST_POINT", vector("1"),
            new Vector<String>(), false, geometry);
    }

    private static Vector<String> vector(String... values) {
        Vector<String> result = new Vector<String>();
        Collections.addAll(result, values);
        return result;
    }

    private static TreeMap<String,Integer> snapshotConflicts(TreeMap<String,Integer> source) {
        TreeMap<String,Integer> result = new TreeMap<String,Integer>();
        for (Map.Entry<String,Integer> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0 ||
                    entry.getValue() == null || entry.getValue().intValue() < 0)
                throw new IllegalStateException("Router conflict map contains an invalid count");
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static TreeMap<String,Integer> conflictsDelta(TreeMap<String,Integer> before,
            TreeMap<String,Integer> after) {
        TreeMap<String,Integer> delta = new TreeMap<String,Integer>();
        for (Map.Entry<String,Integer> entry : after.entrySet()) {
            int prior = before.containsKey(entry.getKey()) ? before.get(entry.getKey()).intValue() : 0;
            int difference = entry.getValue().intValue() - prior;
            if (difference < 0)
                throw new IllegalStateException("Router conflict count regressed");
            if (difference > 0) delta.put(entry.getKey(), Integer.valueOf(difference));
        }
        for (String net : before.keySet())
            if (!after.containsKey(net))
                throw new IllegalStateException("Router conflict map lost an existing net");
        return delta;
    }

    private static void verifyConflictDelta(TreeMap<String,Integer> delta,
            TreeMap<String,Integer> histogram, int congestionDelta, String queryNet) {
        TreeMap<String,Integer> expected = new TreeMap<String,Integer>();
        if (congestionDelta > 0)
            for (Map.Entry<String,Integer> entry : histogram.entrySet())
                expected.put(entry.getKey(), Integer.valueOf(
                    entry.getValue().intValue() * congestionDelta));
        if (!expected.equals(delta))
            throw new IllegalStateException("Conflict delta differs from foreign occupied-neighbor histogram");
        if (delta.containsKey(queryNet))
            throw new IllegalStateException("Conflict delta identifies the query net as foreign");
    }

    private static TreeMap<String,Integer> neighborHistogram(List<Neighbor> neighbors, String net) {
        TreeMap<String,Integer> result = new TreeMap<String,Integer>();
        for (Neighbor neighbor : neighbors) {
            if (net.equals(neighbor.netId)) continue;
            Integer count = result.get(neighbor.netId);
            result.put(neighbor.netId, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        return result;
    }

    private static List<Neighbor> occupiedNeighbors(String[][] occupied, int x, int y) {
        List<Neighbor> result = new ArrayList<Neighbor>();
        int radius = PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS;
        for (int gridX = Math.max(0, x - radius);
                gridX <= Math.min(occupied.length - 1, x + radius); gridX++) {
            for (int gridY = Math.max(0, y - radius);
                    gridY <= Math.min(occupied[gridX].length - 1, y + radius); gridY++) {
                String net = occupied[gridX][gridY];
                if (net != null) result.add(new Neighbor(gridX, gridY, net));
            }
        }
        return result;
    }

    private static void requireKnownNets(TreeMap<String,Integer> values, Set<String> knownNets) {
        for (String net : values.keySet())
            if (!knownNets.contains(net))
                throw new IllegalStateException("Router conflict map references a net outside the board census");
    }

    private static void requireKnownOwner(String net, Set<String> knownNets,
            boolean allowMixedClearance) {
        if (net == null) return;
        if (net.length() == 0) {
            if (allowMixedClearance) return;
            throw new IllegalStateException("Router occupied cell contains the mixed-clearance sentinel");
        }
        if (!knownNets.contains(net))
            throw new IllegalStateException("Router occupancy references a net outside the board census");
    }

    private static boolean hasForeignTargetOwner(String occupied, String clearance, String net) {
        return occupied != null && !net.equals(occupied) ||
            clearance != null && !net.equals(clearance);
    }

    private static String neighborsJson(List<Neighbor> values) {
        StringBuilder json = new StringBuilder(192);
        json.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index != 0) json.append(',');
            Neighbor neighbor = values.get(index);
            json.append("{\"gridX\":").append(neighbor.gridX)
                .append(",\"gridY\":").append(neighbor.gridY)
                .append(",\"netId\":").append(quote(neighbor.netId)).append('}');
        }
        return json.append(']').toString();
    }

    private static String countMapJson(TreeMap<String,Integer> values) {
        StringBuilder json = new StringBuilder(128);
        json.append('[');
        boolean first = true;
        for (Map.Entry<String,Integer> entry : values.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"netId\":").append(quote(entry.getKey()))
                .append(",\"count\":").append(entry.getValue()).append('}');
        }
        return json.append(']').toString();
    }

    private static String[][] stringGrid(PcbNetRouter.Router router, String name) {
        Object value = readField(router, name);
        if (!(value instanceof String[][]))
            throw new IllegalStateException("Router occupancy field has an unexpected type");
        return (String[][])value;
    }

    private static int workCongestion(PcbNetRouter.Router router) {
        if (router.work == null)
            throw new IllegalStateException("Router observation copy has no work counters");
        return router.work.congestionRejections;
    }

    private void canaryCheck(boolean condition, String message) {
        canaryAssertions++;
        if (!condition)
            throw new IllegalStateException("CANONICAL_PREFIX_BLOCKER_CANARY: " + message);
    }

    private void canaryCheck(boolean condition, String message, CanaryResult actual) {
        canaryAssertions++;
        if (!condition)
            throw new IllegalStateException("CANONICAL_PREFIX_BLOCKER_CANARY: " + message +
                "; actual=" + actual);
    }

    private void ensureGeometryStable(PcbBoardLayout layout, String before, String beforeSha) {
        String after = layout.geometryFingerprint();
        String afterSha = sha256(after.getBytes(UTF8));
        if (!before.equals(after) || !beforeSha.equals(afterSha))
            throw new IllegalStateException("Canonical prefix geometry changed before inspector throw");
        geometryAfterSha256 = afterSha;
        after = null;
    }

    private void requireLifecycle() {
        if (!ownersClosedCallback || !ownersReleased || !contextRestored ||
                ownerReferencesLive || selected != 1)
            throw new IllegalStateException("Canonical prefix owner/context cleanup is incomplete");
    }

    private static void requirePayload(String json) {
        if (json == null || json.length() == 0 ||
                json.getBytes(UTF8).length + 1L > MAX_LINE_BYTES)
            throw new IllegalStateException("Canonical prefix inspector JSON exceeds its fixed 64 KiB line limit");
    }

    private void recordUnit(long nanos) {
        if (nanos < 0)
            throw new IllegalStateException("Canonical prefix observation clock regressed");
        if (nanos > maximumUnitNanos) maximumUnitNanos = nanos;
        if (nanos > MAX_UNIT_NANOS)
            throw new IllegalStateException("Canonical prefix observation unit exceeded five seconds");
    }

    private void requireObservationUnitBound() {
        long units = saturatedAdd(saturatedAdd(copyUnits, comparisonUnits),
            saturatedAdd(saturatedAdd(queryUnits, geometryRowUnits), 0));
        if (units > MAX_UNITS)
            throw new IllegalStateException("Canonical prefix observation units exceeded their bound");
    }

    private static PcbPadPlacement padValue(Object value) {
        if (!(value instanceof PcbPadPlacement))
            throw new IllegalStateException("Canonical prefix endpoint is not a physical pad");
        return (PcbPadPlacement)value;
    }

    private static String padId(Object value) {
        return padValue(value).getPadId();
    }

    private static TroubleshootBoard getSearchBoard(Object search) {
        Object value = readField(search, "board");
        if (!(value instanceof TroubleshootBoard))
            throw new IllegalStateException("Canonical prefix Search has no actual board census");
        return (TroubleshootBoard)value;
    }

    private static int intField(Object owner, String name) {
        Object value = readField(owner, name);
        if (!(value instanceof Integer))
            throw new IllegalStateException("Canonical prefix integer source field is unavailable: " + name);
        return ((Integer)value).intValue();
    }

    private static String stringField(Object value, String name) {
        if (!(value instanceof String) || ((String)value).length() == 0)
            throw new IllegalStateException("Canonical prefix string source field is unavailable: " + name);
        return (String)value;
    }

    private static Object readField(Object owner, String name) {
        if (owner == null) throw new IllegalStateException("Cannot read a field from null: " + name);
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot read canonical prefix source field: " + name, failure);
            }
        }
        throw new IllegalStateException("Canonical prefix source field is unavailable: " + name);
    }

    private static void verifyDirection(int fromX, int fromY, int toX, int toY, int bit) {
        int dx = bit == 2 ? 1 : bit == 8 ? -1 : 0;
        int dy = bit == 4 ? 1 : bit == 1 ? -1 : 0;
        if ((bit != 1 && bit != 2 && bit != 4 && bit != 8) ||
                toX - fromX != dx || toY - fromY != dy)
            throw new IllegalStateException("Canonical prefix query bit differs from its captured directed edge");
    }

    private static long addBounded(long first, long second, String label) {
        if (first < 0 || second < 0 || Long.MAX_VALUE - first < second)
            throw new IllegalStateException(label + " overflowed its bounded counter");
        return first + second;
    }

    private static long elapsed(long started, long ended) {
        long result = ended - started;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    private static long saturatedAdd(long first, long second) {
        if (first < 0 || second < 0 || Long.MAX_VALUE - first < second)
            return Long.MAX_VALUE;
        return first + second;
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (index == 8 || index == 13 || index == 18 || index == 23) {
                if (c != '-') return false;
            } else if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') ||
                    (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }
    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                int hex = value & 255;
                if (hex < 16) result.append('0');
                result.append(Integer.toHexString(hex));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private static String quoteOrNull(String value) {
        return value == null ? "null" : quote(value);
    }

    private static String quote(String value) {
        if (value == null) throw new IllegalArgumentException("Missing JSON string");
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
            case '"': result.append("\\\""); break;
            case '\\': result.append("\\\\"); break;
            case '\b': result.append("\\b"); break;
            case '\f': result.append("\\f"); break;
            case '\n': result.append("\\n"); break;
            case '\r': result.append("\\r"); break;
            case '\t': result.append("\\t"); break;
            default:
                if (c < 0x20) {
                    result.append("\\u00");
                    String hex = Integer.toHexString(c);
                    if (hex.length() < 2) result.append('0');
                    result.append(hex);
                } else result.append(c);
            }
        }
        return result.append('"').toString();
    }

    private static final class CopyResult {
        PcbNetRouter.Router router;
        int entries;
        long units;
    }
    private static final class ComparisonResult {
        boolean equal;
        int entries;
        long units;
    }
    private static final class GeometryRows {
        String rowsJson, netCountsJson;
        long entries, units;
    }
    private static final class QueryObservation {
        String json;
        int congestionDelta;
    }
    private static final class Neighbor {
        final int gridX, gridY;
        final String netId;
        Neighbor(int gridX, int gridY, String netId) {
            this.gridX = gridX;
            this.gridY = gridY;
            this.netId = netId;
        }
    }
    private static final class CanaryResult {
        boolean permits, sourceStable;
        int routerDelta, workDelta;
        String targetOccupied, targetClearance;
        TreeMap<String,Integer> conflictDelta;

        public String toString() {
            return "permits=" + permits + ",routerDelta=" + routerDelta +
                ",workDelta=" + workDelta + ",targetOccupied=" + quoteOrNull(targetOccupied) +
                ",targetClearance=" + quoteOrNull(targetClearance) + ",conflictDelta=" +
                (conflictDelta == null ? "null" : countMapJson(conflictDelta));
        }
    }
}
