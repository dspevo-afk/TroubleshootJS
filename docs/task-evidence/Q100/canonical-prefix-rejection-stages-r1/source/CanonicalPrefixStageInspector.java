package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

/** Native-only capture of actual Router endpoint-predicate stages. */
final class CanonicalPrefixStageInspector
        implements ActualGeneratorBranchOrderStudy.TerminalPrefixInspector {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String SCOPE = "CANONICAL_BASE_REQ0_ZERO_DELTA_STAGES_ONLY";
    private static final String SCHEMA = "tsj-canonical-prefix-stage-observations-v1";
    private static final String ZERO_DELTA_INPUT_SHA =
        "089167366a28d11f4342e07af94db109f3bb8655afd968ad5262c2c75b3a3b6f";
    private static final String EXCEPTION_MESSAGE = "CANONICAL_PREFIX_STAGE_CALLBACK_THROW";
    private static final int WIDTH = 478, HEIGHT = 309, CELLS = 147702, PITCH = 10;
    private static final int PRIMARY = 1, MAX_ENTRIES = 1048576, CHUNK = 4096, MAX_UNITS = 32768;
    private static final long MAX_UNIT_NANOS = 5000000000L, MAX_LINE_BYTES = 65536L;
    private static final int[] SELECTED_ORDINALS = {0,1,2,3,4,5,6,7,8,9,11,15,17,20};
    private static final String NULL = "NULL", OBSERVE = "OBSERVE", CALLBACK_THROW = "CALLBACK_THROW";
    private static final String[] STAGES = {
        "LEGAL_MOVE_REJECTED", "TRAVERSE_REJECTED", "OCCUPANCY_REJECTED", "ALLOWED"
    };

    private final String runId, sourceManifestSha256, wireSha256;
    private final boolean observe, callbackThrow;
    private final String mode;
    private final String[] faceRows = new String[2];
    private final String[] queryRows = new String[CanonicalPrefixCutQueries.COUNT];
    private String prefixGeometrySha256, geometryBeforeSha256, geometryAfterSha256;
    private String reachedPthPadIdsJson, exceptionStage;
    private int offers, selected, completed, queryCount, selectedQueryCount;
    private int callbackQueryCount, eventCount, attachments, detachments;
    private boolean ownersReleased, contextRestored, ownersClosedCallback;
    private boolean ownerReferencesLive, observersDetached;
    private boolean priorObservationUnitsSet;
    private long priorObservationUnits, copyEntries, comparisonEntries;
    private long copyUnits, comparisonUnits, queryUnits, elapsedNanos, maximumUnitNanos;
    private int canaryAssertions;

    CanonicalPrefixStageInspector(String runId, String inputSha, String wireSha,
            boolean observe, boolean callbackThrow) {
        if (!isUuid(runId) || !isSha256(inputSha) || !isSha256(wireSha) ||
                (!observe && callbackThrow))
            throw new IllegalArgumentException("Invalid canonical prefix stage inspector identity/mode");
        this.runId = runId;
        sourceManifestSha256 = inputSha;
        wireSha256 = wireSha;
        this.observe = observe;
        this.callbackThrow = callbackThrow;
        mode = callbackThrow ? CALLBACK_THROW : observe ? OBSERVE : NULL;
    }

    void setPriorObservationUnits(long units) {
        // The composite receives the same 308 callbacks as the blocker first;
        // its selected callback binds P5's offset immediately before this
        // inspector sees the selected callback.
        if (priorObservationUnitsSet || selected != 0)
            throw new IllegalStateException("Prior observation units were already set or selection began");
        if (units < 0 || units > MAX_UNITS || (callbackThrow && units != 0))
            throw new IllegalArgumentException("Prior observation unit offset is outside its frozen bound");
        priorObservationUnits = units;
        priorObservationUnitsSet = true;
        requireObservationUnitBound();
    }

    public void onTerminalPrefix(Object search, PcbBoardLayout layout,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot, String prefixFingerprint,
            String prefixSha256, int prefixBytes, int receiptBranchOrdinal, int groupOrdinal) {
        long started = System.nanoTime();
        ownerReferencesLive = true;
        boolean selectedOffer = false;
        Throwable selectedFailure = null;
        Throwable selectedLimit = null;
        try {
            if (ownersClosedCallback)
                throw new IllegalStateException("Canonical prefix stage callback followed owner close");
            offers++;
            if (selected != 0)
                throw new IllegalStateException("Canonical prefix stage selected callback was duplicated");
            if (!isTargetSnapshot(snapshot, receiptBranchOrdinal)) return;
            selected++;
            selectedOffer = true;
            if (selected != 1 || groupOrdinal < 0)
                throw new IllegalStateException("Canonical prefix stage selected callback identity is invalid");
            if (!callbackThrow && !priorObservationUnitsSet)
                throw new IllegalStateException("Prior blocker observation units were not bound before selection");
            if (callbackThrow && priorObservationUnits != 0)
                throw new IllegalStateException("Callback-throw mode requires zero prior observation units");
            try {
                inspectSelected(search, layout, snapshot, prefixFingerprint, prefixSha256,
                    prefixBytes, receiptBranchOrdinal);
            } catch (Throwable error) {
                selectedFailure = error;
                rethrow(error);
            }
        } finally {
            if (selectedOffer) {
                long selectedNanos = elapsed(started, System.nanoTime());
                elapsedNanos = saturatedAdd(elapsedNanos, selectedNanos);
                if (selectedNanos > MAX_UNIT_NANOS) {
                    IllegalStateException limit = new IllegalStateException(
                        "Selected stage callback exceeded its five-second ADVANCE group bound");
                    if (selectedFailure != null) selectedFailure.addSuppressed(limit);
                    else selectedLimit = limit;
                }
            }
            ownerReferencesLive = false;
            search = null;
            layout = null;
            snapshot = null;
            prefixFingerprint = null;
            prefixSha256 = null;
        }
        if (selectedLimit != null) rethrow(selectedLimit);
    }

    public void afterOwnersClosed(boolean released, boolean restored) {
        if (ownersClosedCallback)
            throw new IllegalStateException("Canonical prefix stage owner-close callback was duplicated");
        ownersClosedCallback = true;
        ownersReleased = released;
        contextRestored = restored;
        ownerReferencesLive = false;
        if (offers != 308 || selected != 1)
            throw new IllegalStateException("Canonical prefix stage selector did not bind the frozen branch");
    }

    String completedJson(String actualGraphSha) {
        requireLifecycle();
        if (callbackThrow || completed != 1 || queryCount != CanonicalPrefixCutQueries.COUNT ||
                selectedQueryCount != SELECTED_ORDINALS.length ||
                !CanonicalPrefixCutQueries.GRAPH_SHA.equals(actualGraphSha) ||
                prefixGeometrySha256 == null || reachedPthPadIdsJson == null ||
                geometryBeforeSha256 == null || geometryAfterSha256 == null ||
                faceRows[0] == null || faceRows[1] == null || !observersDetached ||
                observe && (callbackQueryCount != SELECTED_ORDINALS.length ||
                    attachments != SELECTED_ORDINALS.length || detachments != SELECTED_ORDINALS.length) ||
                !observe && (callbackQueryCount != 0 || eventCount != 0 ||
                    attachments != 0 || detachments != 0))
            throw new IllegalStateException("Canonical prefix stage observations are incomplete");
        for (String row : queryRows)
            if (row == null) throw new IllegalStateException("Canonical prefix stage query row is missing");
        String status = observe ? "COMPLETE_STAGE_OBSERVATIONS_ONLY" : "COMPLETE_NULL_CONTROL_ONLY";
        String json = observationsJson(status, actualGraphSha, null);
        requirePayload(json);
        return json;
    }

    String expectedExceptionJson() {
        requireLifecycle();
        if (!expectedExceptionObserved() || queryCount != 1 || selectedQueryCount != 1 ||
                callbackQueryCount != 1 || eventCount != 1 || attachments != 1 || detachments != 1 ||
                queryUnits != 1 || queryRows[0] == null || !observersDetached ||
                geometryBeforeSha256 == null || geometryAfterSha256 == null ||
                faceRows[0] == null || faceRows[1] == null ||
                !"FIRST_ENDPOINT_CALLBACK".equals(exceptionStage))
            throw new IllegalStateException("Expected stage callback throw lacks completed cleanup proof");
        String json = observationsJson("EXPECTED_CALLBACK_THROW_ONLY",
            CanonicalPrefixCutQueries.GRAPH_SHA, exceptionStage);
        requirePayload(json);
        return json;
    }

    boolean expectedExceptionObserved() {
        return callbackThrow && "FIRST_ENDPOINT_CALLBACK".equals(exceptionStage) &&
            ownersClosedCallback && ownersReleased && contextRestored && !ownerReferencesLive &&
            observersDetached && completed == 0 && queryCount == 1 && selectedQueryCount == 1 &&
            callbackQueryCount == 1 && eventCount == 1 && attachments == 1 && detachments == 1;
    }

    boolean referencesDropped() {
        return !ownerReferencesLive;
    }

    int runCanaries() {
        ownerReferencesLive = true;
        int before = canaryAssertions;
        try {
            runPredicateStageCanaries();
            runBoundsCanary();
            runEndpointOrderCanary();
            runAttachmentCanaries();
            runCallbackThrowCanary();
            runGuardCanaries();
            return canaryAssertions - before;
        } finally {
            ownerReferencesLive = false;
        }
    }

    private static boolean isTargetSnapshot(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            int receiptOrdinal) {
        return snapshot != null && receiptOrdinal == 307 &&
            "A_COIL_LOW".equals(snapshot.netId) && "QA.C".equals(snapshot.startPadId) &&
            "DA.A".equals(snapshot.rootPadId);
    }

    private void inspectSelected(Object search, PcbBoardLayout layout,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot, String prefixFingerprint,
            String prefixSha256, int prefixBytes, int receiptBranchOrdinal) {
        verifySelectedIdentity(search, layout, snapshot, prefixFingerprint, prefixSha256,
            prefixBytes, receiptBranchOrdinal);
        byte[] prefixBytesUtf8 = prefixFingerprint.getBytes(UTF8);
        prefixGeometrySha256 = sha256(prefixBytesUtf8);
        if (prefixBytesUtf8.length != prefixBytes ||
                !prefixGeometrySha256.equals(prefixSha256))
            throw new IllegalStateException("Canonical prefix stage geometry callback hash/length changed");
        String geometryBefore = layout.geometryFingerprint();
        geometryBeforeSha256 = sha256(geometryBefore.getBytes(UTF8));
        if (!geometryBefore.equals(prefixFingerprint) ||
                !geometryBeforeSha256.equals(prefixGeometrySha256))
            throw new IllegalStateException("Canonical prefix stage layout differs from actual callback");

        PcbNetRouter.Router[] liveFaces = null;
        PcbNetRouter.Router[] pristineFaces = new PcbNetRouter.Router[2];
        PcbNetRouter.Router[] queryFaces = new PcbNetRouter.Router[2];
        String[] effectsBefore = new String[2], effectsBeforeSha = new String[2];
        long[] faceCopyEntries = new long[2], faceComparisonEntries = new long[2];
        long[] faceCopyUnits = new long[2], faceComparisonUnits = new long[2];
        int[] faceQueryCounts = new int[2];
        boolean[] liveBeforeEqual = new boolean[2], queryBeforeEqual = new boolean[2];
        boolean[] liveAfterEqual = new boolean[2];
        boolean[] liveNullBefore = new boolean[2], pristineNullBefore = new boolean[2];
        boolean[] liveNullAfter = new boolean[2], pristineNullAfter = new boolean[2];
        boolean[] queryNullAfter = new boolean[2];
        PcbPadPlacement start = null, root = null;
        PcbPadPlacement[] reached = null;
        String sourceNet = null, geometryAfter = null;
        Set<String> knownNets = null;
        String[][] reachedIds = null;
        Throwable failure = null, expectedCallbackFailure = null;
        boolean normalComplete = false;
        try {
            liveFaces = ((PcbNetRouter.Router[])readField(search, "faces")).clone();
            if (liveFaces.length != 2 || liveFaces[0] == null || liveFaces[1] == null)
                throw new IllegalStateException("Selected Search lacks two actual Router faces");
            knownNets = new HashSet<String>(getSearchBoard(search).getNetIds());
            Object branch = readField(search, "branch");
            start = (PcbPadPlacement)readField(branch, "start");
            root = (PcbPadPlacement)readField(branch, "root");
            reached = (PcbPadPlacement[])readField(branch, "reachedTerminals");
            sourceNet = stringField(readField(branch, "net"), "actual branch net");
            if (start == null || root == null || reached == null ||
                    !knownNets.contains(sourceNet))
                throw new IllegalStateException("Selected Search lost actual branch endpoint data");
            reachedIds = reachedPadIds(reached, start.getPadId(), root.getPadId());
            reachedPthPadIdsJson = stringArrayJson(reachedIds[0]);
            if (reachedIds.length != 2 || reachedIds[1] == null)
                throw new IllegalStateException("Selected branch endpoint census is incomplete");

            for (int layer = 0; layer < 2; layer++) {
                PcbNetRouter.Router live = liveFaces[layer];
                liveNullBefore[layer] = !live.hasFixedGraphStageObserverForStudy();
                if (!liveNullBefore[layer])
                    throw new IllegalStateException("Live Router already has a stage observer");
                pristineNullBefore[layer] = false;
                effectsBefore[layer] = live.fixedGraphObservationEffects();
                effectsBeforeSha[layer] = sha256(effectsBefore[layer].getBytes(UTF8));
                CopyResult pristine = copyBounded(live);
                pristineFaces[layer] = pristine.router;
                faceCopyEntries[layer] = saturatedAdd(faceCopyEntries[layer], pristine.entries);
                faceCopyUnits[layer] = saturatedAdd(faceCopyUnits[layer], pristine.units);
                copyEntries = saturatedAdd(copyEntries, pristine.entries);
                copyUnits = saturatedAdd(copyUnits, pristine.units);
                recordUnit(pristine.maximumUnitNanos);
                requireObservationUnitBound();
                pristineNullBefore[layer] = !pristineFaces[layer].hasFixedGraphStageObserverForStudy();
                ComparisonResult liveCopy = compareBounded(live, pristineFaces[layer]);
                faceComparisonEntries[layer] = saturatedAdd(faceComparisonEntries[layer], liveCopy.entries);
                faceComparisonUnits[layer] = saturatedAdd(faceComparisonUnits[layer], liveCopy.units);
                comparisonEntries = saturatedAdd(comparisonEntries, liveCopy.entries);
                comparisonUnits = saturatedAdd(comparisonUnits, liveCopy.units);
                recordUnit(liveCopy.maximumUnitNanos);
                requireObservationUnitBound();
                liveBeforeEqual[layer] = liveCopy.equal;
                if (!liveBeforeEqual[layer] ||
                        pristineFaces[layer].hasFixedGraphStageObserverForStudy())
                    throw new IllegalStateException("Pristine Router copy differs or has a stage observer");
                CopyResult query = copyBounded(live);
                queryFaces[layer] = query.router;
                faceCopyEntries[layer] = saturatedAdd(faceCopyEntries[layer], query.entries);
                faceCopyUnits[layer] = saturatedAdd(faceCopyUnits[layer], query.units);
                copyEntries = saturatedAdd(copyEntries, query.entries);
                copyUnits = saturatedAdd(copyUnits, query.units);
                recordUnit(query.maximumUnitNanos);
                requireObservationUnitBound();
                ComparisonResult queryCopy = compareBounded(queryFaces[layer], pristineFaces[layer]);
                faceComparisonEntries[layer] = saturatedAdd(faceComparisonEntries[layer], queryCopy.entries);
                faceComparisonUnits[layer] = saturatedAdd(faceComparisonUnits[layer], queryCopy.units);
                comparisonEntries = saturatedAdd(comparisonEntries, queryCopy.entries);
                comparisonUnits = saturatedAdd(comparisonUnits, queryCopy.units);
                recordUnit(queryCopy.maximumUnitNanos);
                requireObservationUnitBound();
                queryBeforeEqual[layer] = queryCopy.equal;
                if (!queryBeforeEqual[layer] ||
                        queryFaces[layer].hasFixedGraphStageObserverForStudy())
                    throw new IllegalStateException("Fresh query Router copy differs or has an observer");
            }

            for (int ordinal = 0; ordinal < CanonicalPrefixCutQueries.COUNT; ordinal++) {
                QueryContext context = queryContext(ordinal, search, sourceNet, start, root, reachedIds[1]);
                if (context.selected) selectedQueryCount++;
                QueryObservation observed = observeQuery(context, queryFaces[context.layer],
                    liveFaces[context.layer], pristineFaces[context.layer], knownNets,
                    start, root, reached,
                    liveBeforeEqual[context.layer] && queryBeforeEqual[context.layer]);
                queryRows[ordinal] = observed.json;
                queryCount++;
                faceQueryCounts[context.layer]++;
                if (observed.expectedCallbackThrow) {
                    expectedCallbackFailure = observed.callbackFailure;
                    break;
                }
            }
            if (expectedCallbackFailure == null && queryCount != CanonicalPrefixCutQueries.COUNT)
                throw new IllegalStateException("Canonical stage query set is incomplete");
            normalComplete = expectedCallbackFailure == null;
        } catch (Throwable error) {
            failure = error;
        } finally {
            for (int layer = 0; layer < 2; layer++) {
                PcbNetRouter.Router query = queryFaces[layer];
                if (query != null) {
                    try {
                        if (query.hasFixedGraphStageObserverForStudy()) {
                            query.detachFixedGraphStageObserverForStudy();
                            detachments++;
                        }
                        queryNullAfter[layer] = !query.hasFixedGraphStageObserverForStudy();
                    } catch (Throwable error) { failure = appendFailure(failure, error); }
                }
                PcbNetRouter.Router live = liveFaces == null ? null : liveFaces[layer];
                PcbNetRouter.Router pristine = pristineFaces[layer];
                if (live != null) {
                    try {
                        liveNullAfter[layer] = !live.hasFixedGraphStageObserverForStudy();
                        if (!liveNullAfter[layer])
                            throw new IllegalStateException("Stage observer attached to live Router");
                    } catch (Throwable error) { failure = appendFailure(failure, error); }
                }
                if (pristine != null) {
                    try {
                        pristineNullAfter[layer] = !pristine.hasFixedGraphStageObserverForStudy();
                        if (!pristineNullAfter[layer])
                            throw new IllegalStateException("Stage observer attached to pristine Router");
                    } catch (Throwable error) { failure = appendFailure(failure, error); }
                }
                if (live != null && pristine != null) {
                    try {
                        ComparisonResult after = compareBounded(live, pristine);
                        faceComparisonEntries[layer] = saturatedAdd(faceComparisonEntries[layer], after.entries);
                        faceComparisonUnits[layer] = saturatedAdd(faceComparisonUnits[layer], after.units);
                        comparisonEntries = saturatedAdd(comparisonEntries, after.entries);
                        comparisonUnits = saturatedAdd(comparisonUnits, after.units);
                        recordUnit(after.maximumUnitNanos);
                        requireObservationUnitBound();
                        liveAfterEqual[layer] = after.equal;
                        String effectsAfter = live.fixedGraphObservationEffects();
                        if (!liveAfterEqual[layer] || !effectsBefore[layer].equals(effectsAfter))
                            throw new IllegalStateException("Live Router changed during stage observations");
                        faceRows[layer] = faceJson(layer, effectsBeforeSha[layer],
                            sha256(effectsAfter.getBytes(UTF8)), liveBeforeEqual[layer],
                            queryBeforeEqual[layer], liveAfterEqual[layer], liveNullBefore[layer],
                            pristineNullBefore[layer], liveNullAfter[layer], pristineNullAfter[layer],
                            queryNullAfter[layer], faceCopyEntries[layer], faceComparisonEntries[layer],
                            faceCopyUnits[layer], faceComparisonUnits[layer], faceQueryCounts[layer]);
                    } catch (Throwable error) { failure = appendFailure(failure, error); }
                }
            }
            try {
                long geometryStarted = System.nanoTime();
                geometryAfter = layout.geometryFingerprint();
                recordUnit(elapsed(geometryStarted, System.nanoTime()));
                geometryAfterSha256 = sha256(geometryAfter.getBytes(UTF8));
                if (!geometryAfter.equals(geometryBefore) ||
                        !geometryAfterSha256.equals(geometryBeforeSha256) ||
                        !geometryAfterSha256.equals(prefixGeometrySha256))
                    throw new IllegalStateException("Canonical prefix geometry changed during stage observation");
            } catch (Throwable error) { failure = appendFailure(failure, error); }
            observersDetached = allObservedOwnersDetached(liveFaces, pristineFaces, queryFaces);
            if (!observersDetached)
                failure = appendFailure(failure,
                    new IllegalStateException("Stage observers were not detached from all owners"));
            if (liveFaces != null) { liveFaces[0] = null; liveFaces[1] = null; }
            if (pristineFaces != null) { pristineFaces[0] = null; pristineFaces[1] = null; }
            if (queryFaces != null) { queryFaces[0] = null; queryFaces[1] = null; }
            if (effectsBefore != null) { effectsBefore[0] = null; effectsBefore[1] = null; }
            if (effectsBeforeSha != null) { effectsBeforeSha[0] = null; effectsBeforeSha[1] = null; }
            if (knownNets != null) knownNets.clear();
            if (reachedIds != null) { reachedIds[0] = null; reachedIds[1] = null; }
            liveFaces = null; pristineFaces = null; queryFaces = null;
            effectsBefore = null; effectsBeforeSha = null; knownNets = null;
            reachedIds = null; start = null; root = null; reached = null;
            sourceNet = null; geometryAfter = null; geometryBefore = null;
            prefixBytesUtf8 = null; search = null; layout = null; snapshot = null;
        }
        if (failure != null) {
            if (expectedCallbackFailure != null) failure = appendFailure(failure, expectedCallbackFailure);
            rethrow(failure);
        }
        if (expectedCallbackFailure != null) throw (IllegalStateException)expectedCallbackFailure;
        if (normalComplete) completed++;
    }

    private QueryObservation observeQuery(QueryContext context, PcbNetRouter.Router query,
            PcbNetRouter.Router live, PcbNetRouter.Router pristine, Set<String> knownNets,
            PcbPadPlacement start, PcbPadPlacement root, PcbPadPlacement[] reached,
            boolean comparisonPassed) {
        QueryObservation result = new QueryObservation();
        QueryObserver stageObserver = null;
        boolean attached = false;
        Throwable predicateFailure = null;
        Boolean permits = null;
        int routerBefore = 0, routerAfter = 0, workBefore = 0, workAfter = 0;
        TreeMap<String,Integer> conflictsBefore = null, conflictsAfter = null, delta = null;
        String targetOccupied = null, targetClearance = null;
        String[][] occupied = null, clearance = null;
        List<Neighbor> neighbors = null;
        Throwable failure = null;
        long queryStarted = System.nanoTime();
        try {
            if (query == null || query.hasFixedGraphStageObserverForStudy())
                throw new IllegalStateException("Stage query Router is missing or already observed");
            routerBefore = query.congestionRejections;
            workBefore = workCongestion(query);
            conflictsBefore = snapshotConflicts(query.conflicts);
            requireKnownNets(conflictsBefore, knownNets);
            occupied = stringGrid(query, "occupiedNet");
            clearance = stringGrid(query, "clearanceNet");
            if (occupied.length != WIDTH || clearance.length != WIDTH)
                throw new IllegalStateException("Stage query Router width differs from the frozen source");
            targetOccupied = occupied[context.toGridX][context.toGridY];
            targetClearance = clearance[context.toGridX][context.toGridY];
            requireKnownOwner(targetOccupied, knownNets, false);
            requireKnownOwner(targetClearance, knownNets, true);
            neighbors = occupiedNeighbors(occupied, context.toGridX, context.toGridY);
            for (Neighbor neighbor : neighbors)
                if (!knownNets.contains(neighbor.netId))
                    throw new IllegalStateException("Stage neighbor references an unknown board net");
            if (observe && context.selected || callbackThrow && context.ordinal == 0) {
                stageObserver = new QueryObserver(this, context, expectedEndpoints(root, reached,
                    start.getPadId(), root.getPadId()), callbackThrow);
                query.attachFixedGraphStageObserverForStudy(stageObserver, query, live, pristine,
                    comparisonPassed);
                attached = true;
                attachments++;
            }
            try {
                permits = Boolean.valueOf(query.permitsLayerStep(context.fromX, context.fromY,
                    context.toX, context.toY, context.netId, start, root, reached));
            } catch (IllegalStateException error) {
                if (callbackThrow && context.ordinal == 0 && stageObserver != null &&
                        "FIRST_ENDPOINT_CALLBACK".equals(exceptionStage) &&
                        EXCEPTION_MESSAGE.equals(error.getMessage())) {
                    predicateFailure = error;
                } else throw error;
            }
            routerAfter = query.congestionRejections;
            workAfter = workCongestion(query);
            conflictsAfter = snapshotConflicts(query.conflicts);
            requireKnownNets(conflictsAfter, knownNets);
            delta = conflictsDelta(conflictsBefore, conflictsAfter);
            int routerDelta = routerAfter - routerBefore;
            int workDelta = workAfter - workBefore;
            if (routerDelta < 0 || workDelta != routerDelta)
                throw new IllegalStateException("Stage Router/work congestion deltas disagree");
            TreeMap<String,Integer> histogram = neighborHistogram(neighbors, context.netId);
            verifyConflictDelta(delta, histogram, routerDelta, context.netId);
            if (routerDelta > 0 && !hasForeignTargetOwner(targetOccupied, targetClearance, context.netId))
                throw new IllegalStateException("Positive stage congestion delta has no actual foreign target owner");
            if (predicateFailure == null) {
                if (permits == null || permits.booleanValue())
                    throw new IllegalStateException("Actual full-cut Router predicate did not return frozen false");
                if (stageObserver != null) stageObserver.verifyReturned(permits.booleanValue());
            } else {
                stageObserver.verifyExpectedThrow();
            }
            result.expectedCallbackThrow = predicateFailure != null;
            result.callbackFailure = predicateFailure;
            result.routerBefore = routerBefore; result.routerAfter = routerAfter;
            result.workBefore = workBefore; result.workAfter = workAfter;
            result.routerDelta = routerDelta; result.workDelta = workDelta;
            result.permits = permits;
            result.conflictsBeforeJson = countMapJson(conflictsBefore);
            result.conflictsAfterJson = countMapJson(conflictsAfter);
            result.conflictsDeltaJson = countMapJson(delta);
            result.eventsJson = stageObserver == null ? "[]" : stageObserver.eventsJson();
        } catch (Throwable error) {
            failure = error;
        } finally {
            Throwable cleanupFailure = null;
            try {
                if (attached) {
                    query.detachFixedGraphStageObserverForStudy();
                    detachments++;
                    attached = false;
                }
                if (query != null && query.hasFixedGraphStageObserverForStudy())
                    throw new IllegalStateException("Stage observer remains attached after query");
            } catch (Throwable error) { cleanupFailure = appendFailure(cleanupFailure, error); }
            try {
                if (stageObserver != null) {
                    stageObserver.dropReferences();
                    if (!stageObserver.referencesDropped())
                        throw new IllegalStateException("Query stage observer retained endpoint references");
                }
            } catch (Throwable error) { cleanupFailure = appendFailure(cleanupFailure, error); }
            stageObserver = null;
            try {
                recordUnit(elapsed(queryStarted, System.nanoTime()));
                queryUnits++;
                requireObservationUnitBound();
            } catch (Throwable error) { cleanupFailure = appendFailure(cleanupFailure, error); }
            if (conflictsBefore != null) conflictsBefore.clear();
            if (conflictsAfter != null) conflictsAfter.clear();
            if (delta != null) delta.clear();
            if (neighbors != null) neighbors.clear();
            conflictsBefore = null; conflictsAfter = null; delta = null; neighbors = null;
            occupied = null;
            clearance = null;
            knownNets = null; query = null; live = null; pristine = null;
            reached = null; start = null; root = null;
            targetOccupied = null; targetClearance = null;
            failure = appendFailure(failure, cleanupFailure);
        }
        if (failure != null) rethrow(failure);
        result.json = queryJson(context, result);
        return result;
    }

    private void verifySelectedIdentity(Object search, PcbBoardLayout layout,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot, String fingerprint,
            String fingerprintSha, int fingerprintBytes, int receiptOrdinal) {
        if (search == null || layout == null || snapshot == null || fingerprint == null ||
                fingerprintSha == null || receiptOrdinal != 307)
            throw new IllegalStateException("Canonical stage actual callback identity is incomplete");
        Object branch = readField(search, "branch");
        if (branch == null ||
                fingerprintBytes != 265894 || intField(snapshot, "pass") != 2 ||
                intField(snapshot, "branchExpanded") != 25 ||
                intField(snapshot, "totalExpanded") != 918222 ||
                intField(snapshot, "frontierCount") != 0 ||
                intField(snapshot, "prefixViaCount") != 48 ||
                !"NO_PATH".equals(stringField(readField(snapshot, "terminalReason"),
                    "terminal reason")) ||
                intField(snapshot, "pass") != intField(search, "pass") ||
                !"A_COIL_LOW".equals(snapshot.netId) || !"QA.C".equals(snapshot.startPadId) ||
                !"DA.A".equals(snapshot.rootPadId) ||
                intField(branch, "branchOrdinal") != 77 ||
                !"A_COIL_LOW".equals(stringField(readField(branch, "net"), "branch net")) ||
                !"QA.C".equals(padId(readField(branch, "start"))) ||
                !"DA.A".equals(padId(readField(branch, "root"))) ||
                readField(search, "layout") != layout ||
                intField(search, "width") != WIDTH || intField(search, "height") != HEIGHT ||
                intField(search, "cells") != CELLS || intField(search, "primary") != PRIMARY ||
                intField(search, "minX") != CanonicalPrefixCutQueries.ORIGIN_X ||
                intField(search, "minY") != CanonicalPrefixCutQueries.ORIGIN_Y ||
                readField(search, "policy") != PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER ||
                snapshot.gridWidth != WIDTH || snapshot.gridHeight != HEIGHT ||
                !CanonicalPrefixCutQueries.PREFIX_SHA.equals(fingerprintSha) ||
                fingerprintBytes < 1 || fingerprint.length() == 0)
            throw new IllegalStateException("Canonical stage Search/Branch identity differs from the frozen selection");
        PcbPadPlacement[] terminals = (PcbPadPlacement[])readField(branch, "reachedTerminals");
        if (terminals == null)
            throw new IllegalStateException("Canonical stage source omitted reached PTH endpoints");
        for (PcbPadPlacement terminal : terminals)
            if (terminal == null)
                throw new IllegalStateException("Canonical stage source contains a null reached endpoint");
    }

    private static QueryContext queryContext(int ordinal, Object search, String netId,
            PcbPadPlacement start, PcbPadPlacement root, String[] reachedIds) {
        int fromState = CanonicalPrefixCutQueries.fromState(ordinal);
        int toState = CanonicalPrefixCutQueries.toState(ordinal);
        int bit = CanonicalPrefixCutQueries.bit(ordinal);
        int layer = fromState / CELLS;
        if (layer < 0 || layer > 1 || toState / CELLS != layer)
            throw new IllegalStateException("Frozen full-cut query crosses Router faces");
        int fromCell = fromState % CELLS, toCell = toState % CELLS;
        int fromGridX = fromCell % WIDTH, fromGridY = fromCell / WIDTH;
        int toGridX = toCell % WIDTH, toGridY = toCell / WIDTH;
        verifyDirection(fromGridX, fromGridY, toGridX, toGridY, bit);
        int minX = intField(search, "minX"), minY = intField(search, "minY");
        QueryContext context = new QueryContext();
        context.ordinal = ordinal; context.layer = layer; context.fromState = fromState;
        context.toState = toState; context.bit = bit; context.fromGridX = fromGridX;
        context.fromGridY = fromGridY; context.toGridX = toGridX; context.toGridY = toGridY;
        context.fromX = minX + fromGridX * PITCH; context.fromY = minY + fromGridY * PITCH;
        context.toX = minX + toGridX * PITCH; context.toY = minY + toGridY * PITCH;
        context.direction = bit == 1 ? 0 : bit == 2 ? 1 : bit == 4 ? 2 : 3;
        context.netId = netId; context.startPadId = start.getPadId();
        context.selected = isSelectedOrdinal(ordinal);
        if (reachedIds == null)
            throw new IllegalStateException("Frozen branch has no reached endpoint census");
        return context;
    }

    private static QueryContext literalContext(int ordinal, PcbNetRouter.Router router,
            String netId, PcbPadPlacement start, int fromX, int fromY, int toX, int toY) {
        int minX = intField(router, "minX"), minY = intField(router, "minY");
        int gridWidth = intField(router, "gridWidth"), gridHeight = intField(router, "gridHeight");
        int fromGridX = (fromX - minX) / PITCH, fromGridY = (fromY - minY) / PITCH;
        int toGridX = (toX - minX) / PITCH, toGridY = (toY - minY) / PITCH;
        if (fromX != minX + fromGridX * PITCH || fromY != minY + fromGridY * PITCH ||
                toX != minX + toGridX * PITCH || toY != minY + toGridY * PITCH ||
                fromGridX < 0 || toGridX < 0 || fromGridY < 0 || toGridY < 0 ||
                fromGridX >= gridWidth || toGridX >= gridWidth ||
                fromGridY >= gridHeight || toGridY >= gridHeight)
            throw new IllegalStateException("Literal stage canary edge is outside the Router grid");
        int dx = toGridX - fromGridX, dy = toGridY - fromGridY;
        int direction = dx == 1 ? 1 : dx == -1 ? 3 : dy == 1 ? 2 : dy == -1 ? 0 : -1;
        if (Math.abs(dx) + Math.abs(dy) != 1)
            throw new IllegalStateException("Literal stage canary edge is not one directed grid step");
        QueryContext context = new QueryContext();
        context.ordinal = ordinal;
        context.layer = ((PcbCopperLayer)readField(router, "layer")).ordinal();
        context.fromGridX = fromGridX; context.fromGridY = fromGridY;
        context.toGridX = toGridX; context.toGridY = toGridY;
        context.fromX = fromX; context.fromY = fromY; context.toX = toX; context.toY = toY;
        context.direction = direction; context.netId = netId; context.startPadId = start.getPadId();
        context.fromState = context.layer * CELLS + fromGridY * WIDTH + fromGridX;
        context.toState = context.layer * CELLS + toGridY * WIDTH + toGridX;
        context.bit = direction == 0 ? 1 : direction == 1 ? 2 : direction == 2 ? 4 : 8;
        return context;
    }

    private static boolean isSelectedOrdinal(int ordinal) {
        for (int value : SELECTED_ORDINALS) if (value == ordinal) return true;
        return false;
    }

    private static String[] expectedEndpoints(PcbPadPlacement root,
            PcbPadPlacement[] reached, String startId, String rootId) {
        if (root == null || startId == null || rootId == null)
            throw new IllegalStateException("Stage endpoint order lost actual branch identity");
        List<String> result = new ArrayList<String>();
        result.add(root.getPadId());
        if (reached != null) {
            for (PcbPadPlacement endpoint : reached) {
                if (endpoint == null) throw new IllegalStateException("Null reached endpoint in actual branch");
                String id = endpoint.getPadId();
                if (startId.equals(id) || rootId.equals(id)) continue;
                result.add(id);
            }
        }
        return result.toArray(new String[result.size()]);
    }

    private String queryJson(QueryContext context, QueryObservation result) {
        StringBuilder json = new StringBuilder(1024);
        json.append("{\"ordinal\":").append(context.ordinal)
            .append(",\"layer\":").append(context.layer)
            .append(",\"fromState\":").append(context.fromState)
            .append(",\"toState\":").append(context.toState)
            .append(",\"bit\":").append(context.bit)
            .append(",\"selected\":").append(context.selected)
            .append(",\"fromGridX\":").append(context.fromGridX)
            .append(",\"fromGridY\":").append(context.fromGridY)
            .append(",\"toGridX\":").append(context.toGridX)
            .append(",\"toGridY\":").append(context.toGridY)
            .append(",\"outcome\":").append(quote(result.expectedCallbackThrow ?
                "EXPECTED_CALLBACK_THROW" : "RETURNED"))
            .append(",\"permitsStep\":").append(result.permits == null ? "null" : result.permits)
            .append(",\"routerCongestionBefore\":").append(result.routerBefore)
            .append(",\"routerCongestionAfter\":").append(result.routerAfter)
            .append(",\"routerCongestionDelta\":").append(result.routerDelta)
            .append(",\"workCongestionBefore\":").append(result.workBefore)
            .append(",\"workCongestionAfter\":").append(result.workAfter)
            .append(",\"workCongestionDelta\":").append(result.workDelta)
            .append(",\"conflictsBefore\":").append(result.conflictsBeforeJson)
            .append(",\"conflictsAfter\":").append(result.conflictsAfterJson)
            .append(",\"conflictsDelta\":").append(result.conflictsDeltaJson)
            .append(",\"events\":").append(result.eventsJson).append('}');
        return json.toString();
    }

    private String observationsJson(String status, String graphSha, String exception) {
        StringBuilder json = new StringBuilder(32768);
        json.append("{\"schema\":").append(quote(SCHEMA))
            .append(",\"status\":").append(quote(status))
            .append(",\"mode\":").append(quote(mode))
            .append(",\"runId\":").append(quote(runId))
            .append(",\"sourceManifestSha256\":").append(quote(sourceManifestSha256))
            .append(",\"wireSha256\":").append(quote(wireSha256))
            .append(",\"scope\":").append(quote(SCOPE))
            .append(",\"layoutQualification\":false")
            .append(",\"zeroDeltaInputSha256\":").append(quote(ZERO_DELTA_INPUT_SHA))
            .append(",\"fullCutInputSha256\":").append(quote(CanonicalPrefixCutQueries.CUT_INPUT_SHA))
            .append(",\"graphSha256\":").append(quote(graphSha))
            .append(",\"prefixGeometrySha256\":").append(quote(prefixGeometrySha256))
            .append(",\"receiptBranchOrdinal\":307," + "\"lineageBranchOrdinal\":77")
            .append(",\"netId\":\"A_COIL_LOW\",\"startPadId\":\"QA.C\",\"rootPadId\":\"DA.A\"")
            .append(",\"reachedPthPadIds\":").append(reachedPthPadIdsJson)
            .append(",\"lifecycle\":{\"offers\":").append(offers)
            .append(",\"selected\":").append(selected)
            .append(",\"completed\":").append(completed)
            .append(",\"ownersReleased\":").append(ownersReleased)
            .append(",\"contextRestored\":").append(contextRestored)
            .append(",\"sourceRefsDropped\":").append(referencesDropped())
            .append(",\"observersDetached\":").append(observersDetached).append('}')
            .append(",\"sourceProof\":{\"geometryBeforeSha256\":")
            .append(quote(geometryBeforeSha256))
            .append(",\"geometryAfterSha256\":").append(quote(geometryAfterSha256))
            .append(",\"faces\":").append(faceRowsJson()).append('}')
            .append(",\"queries\":").append(queryRowsJson())
            .append(",\"totals\":{\"queryCount\":").append(queryCount)
            .append(",\"selectedQueryCount\":").append(selectedQueryCount)
            .append(",\"callbackQueryCount\":").append(callbackQueryCount)
            .append(",\"eventCount\":").append(eventCount)
            .append(",\"attachments\":").append(attachments)
            .append(",\"detachments\":").append(detachments)
            .append(",\"copyEntries\":").append(copyEntries)
            .append(",\"comparisonEntries\":").append(comparisonEntries)
            .append(",\"copyUnits\":").append(copyUnits)
            .append(",\"comparisonUnits\":").append(comparisonUnits)
            .append(",\"queryUnits\":").append(queryUnits)
            .append(",\"priorObservationUnits\":").append(priorObservationUnits)
            .append(",\"totalObservationUnits\":").append(totalObservationUnits())
            .append(",\"elapsedNanos\":").append(elapsedNanos)
            .append(",\"maximumUnitNanos\":").append(maximumUnitNanos).append('}')
            .append(",\"exceptionStage\":")
            .append(exception == null ? "null" : quote(exception)).append('}');
        return json.toString();
    }

    private String queryRowsJson() {
        StringBuilder json = new StringBuilder(24000);
        json.append('[');
        boolean first = true;
        for (String row : queryRows) {
            if (row == null) continue;
            if (!first) json.append(',');
            first = false;
            json.append(row);
        }
        return json.append(']').toString();
    }

    private String faceRowsJson() {
        StringBuilder json = new StringBuilder(1200);
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
            boolean liveBefore, boolean queryBefore, boolean liveAfter,
            boolean liveNullBefore, boolean pristineNullBefore, boolean liveNullAfter,
            boolean pristineNullAfter, boolean queryNullAfter, long copyEntries,
            long comparisonEntries, long copyUnits, long comparisonUnits, int queryCount) {
        return new StringBuilder(640).append("{\"layer\":").append(layer)
            .append(",\"sourceEffectsBeforeSha256\":").append(quote(beforeSha))
            .append(",\"sourceEffectsAfterSha256\":").append(quote(afterSha))
            .append(",\"liveToPristineBeforeEqual\":").append(liveBefore)
            .append(",\"queryToPristineBeforeEqual\":").append(queryBefore)
            .append(",\"liveToPristineAfterEqual\":").append(liveAfter)
            .append(",\"liveObserverNullBefore\":").append(liveNullBefore)
            .append(",\"pristineObserverNullBefore\":").append(pristineNullBefore)
            .append(",\"liveObserverNullAfter\":").append(liveNullAfter)
            .append(",\"pristineObserverNullAfter\":").append(pristineNullAfter)
            .append(",\"queryObserverNullAfter\":").append(queryNullAfter)
            .append(",\"copyEntries\":").append(copyEntries)
            .append(",\"comparisonEntries\":").append(comparisonEntries)
            .append(",\"copyUnits\":").append(copyUnits)
            .append(",\"comparisonUnits\":").append(comparisonUnits)
            .append(",\"queryCount\":").append(queryCount).append('}').toString();
    }

    private static CopyResult copyBounded(PcbNetRouter.Router source) {
        CopyResult result = new CopyResult();
        PcbNetRouter.Router.FixedGraphCopyTask task = null;
        try {
            long started = System.nanoTime();
            task = source.beginFixedGraphObservationCopy();
            // The caller owns unit accounting, so begin/advance elapsed values are
            // retained in the result and charged at the helper boundary.
            result.units++;
            result.maximumUnitNanos = elapsed(started, System.nanoTime());
            if (result.maximumUnitNanos > MAX_UNIT_NANOS)
                throw new IllegalStateException("Router observation copy setup exceeded five seconds");
            int previousEntries = 0;
            for (;;) {
                started = System.nanoTime();
                boolean done = task.advanceOne(CHUNK);
                int entries = task.copiedEntries();
                if (entries < previousEntries || entries > MAX_ENTRIES)
                    throw new IllegalStateException("Router observation clone exceeded its per-copy entry bound");
                result.entries = entries;
                result.units++;
                long unit = elapsed(started, System.nanoTime());
                if (unit > MAX_UNIT_NANOS)
                    throw new IllegalStateException("Router observation copy chunk exceeded five seconds");
                if (unit > result.maximumUnitNanos) result.maximumUnitNanos = unit;
                if (done) {
                    result.router = task.result();
                    task = null;
                    return result;
                }
                previousEntries = entries;
            }
        } finally {
            task = null;
            source = null;
        }
    }

    private static ComparisonResult compareBounded(PcbNetRouter.Router first,
            PcbNetRouter.Router second) {
        ComparisonResult result = new ComparisonResult();
        PcbNetRouter.Router.FixedGraphComparisonTask task = null;
        try {
            long started = System.nanoTime();
            task = first.beginFixedGraphComparison(second);
            result.units++;
            result.maximumUnitNanos = elapsed(started, System.nanoTime());
            if (result.maximumUnitNanos > MAX_UNIT_NANOS)
                throw new IllegalStateException("Router observation comparison setup exceeded five seconds");
            int previousEntries = 0;
            for (;;) {
                started = System.nanoTime();
                boolean done = task.advanceOne(CHUNK);
                int entries = task.comparedEntries();
                if (entries < previousEntries)
                    throw new IllegalStateException("Router observation comparison counter regressed");
                result.entries = entries;
                result.units++;
                long unit = elapsed(started, System.nanoTime());
                if (unit > MAX_UNIT_NANOS)
                    throw new IllegalStateException("Router observation comparison chunk exceeded five seconds");
                if (unit > result.maximumUnitNanos) result.maximumUnitNanos = unit;
                if (done) {
                    result.equal = task.equal();
                    task = null;
                    return result;
                }
                previousEntries = entries;
            }
        } finally {
            task = null;
            first = null;
            second = null;
        }
    }

    private void requireLifecycle() {
        if (!ownersClosedCallback || !ownersReleased || !contextRestored ||
                ownerReferencesLive || selected != 1)
            throw new IllegalStateException("Canonical prefix stage owner/context cleanup is incomplete");
    }

    private long totalObservationUnits() {
        return saturatedAdd(priorObservationUnits,
            saturatedAdd(copyUnits, saturatedAdd(comparisonUnits, queryUnits)));
    }

    private void requireObservationUnitBound() {
        if (totalObservationUnits() > MAX_UNITS)
            throw new IllegalStateException("Canonical prefix stage observation units exceeded their bound");
    }

    private void recordUnit(long nanos) {
        if (nanos < 0) throw new IllegalStateException("Canonical stage clock regressed");
        if (nanos > maximumUnitNanos) maximumUnitNanos = nanos;
        if (nanos > MAX_UNIT_NANOS)
            throw new IllegalStateException("Canonical stage observation unit exceeded five seconds");
    }

    private static boolean allObservedOwnersDetached(PcbNetRouter.Router[] live,
            PcbNetRouter.Router[] pristine, PcbNetRouter.Router[] query) {
        if (live == null || pristine == null || query == null ||
                live.length != 2 || pristine.length != 2 || query.length != 2) return false;
        for (int layer = 0; layer < 2; layer++)
            if (live[layer] == null || pristine[layer] == null || query[layer] == null ||
                    live[layer].hasFixedGraphStageObserverForStudy() ||
                    pristine[layer].hasFixedGraphStageObserverForStudy() ||
                    query[layer].hasFixedGraphStageObserverForStudy()) return false;
        return true;
    }

    private static TroubleshootBoard getSearchBoard(Object search) {
        Object value = readField(search, "board");
        if (!(value instanceof TroubleshootBoard))
            throw new IllegalStateException("Selected Search has no actual board net census");
        return (TroubleshootBoard)value;
    }

    private static int workCongestion(PcbNetRouter.Router router) {
        if (router.work == null)
            throw new IllegalStateException("Stage query copy has no work congestion counter");
        return router.work.congestionRejections;
    }

    private static String[][] stringGrid(PcbNetRouter.Router router, String fieldName) {
        Object value = readField(router, fieldName);
        if (!(value instanceof String[][]))
            throw new IllegalStateException("Router occupancy field is unavailable: " + fieldName);
        return (String[][])value;
    }

    private static TreeMap<String,Integer> snapshotConflicts(TreeMap<String,Integer> source) {
        TreeMap<String,Integer> result = new TreeMap<String,Integer>();
        for (Map.Entry<String,Integer> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0 || entry.getValue() == null ||
                    entry.getValue().intValue() < 0)
                throw new IllegalStateException("Router conflict map contains an invalid net/count");
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static TreeMap<String,Integer> conflictsDelta(TreeMap<String,Integer> before,
            TreeMap<String,Integer> after) {
        TreeMap<String,Integer> result = new TreeMap<String,Integer>();
        for (Map.Entry<String,Integer> entry : after.entrySet()) {
            int prior = before.containsKey(entry.getKey()) ? before.get(entry.getKey()).intValue() : 0;
            int difference = entry.getValue().intValue() - prior;
            if (difference < 0) throw new IllegalStateException("Router conflict count regressed");
            if (difference > 0) result.put(entry.getKey(), Integer.valueOf(difference));
        }
        for (String net : before.keySet())
            if (!after.containsKey(net)) throw new IllegalStateException("Router conflict map lost a net");
        return result;
    }

    private static void verifyConflictDelta(TreeMap<String,Integer> delta,
            TreeMap<String,Integer> histogram, int routerDelta, String queryNet) {
        TreeMap<String,Integer> expected = new TreeMap<String,Integer>();
        if (routerDelta > 0)
            for (Map.Entry<String,Integer> entry : histogram.entrySet())
                expected.put(entry.getKey(), Integer.valueOf(entry.getValue().intValue() * routerDelta));
        if (!expected.equals(delta) || delta.containsKey(queryNet))
            throw new IllegalStateException("Router conflict delta differs from known foreign occupied-net histogram");
    }

    private static TreeMap<String,Integer> neighborHistogram(List<Neighbor> neighbors, String queryNet) {
        TreeMap<String,Integer> result = new TreeMap<String,Integer>();
        for (Neighbor neighbor : neighbors) {
            if (queryNet.equals(neighbor.netId)) continue;
            Integer count = result.get(neighbor.netId);
            result.put(neighbor.netId, Integer.valueOf(count == null ? 1 : count.intValue() + 1));
        }
        return result;
    }

    private static List<Neighbor> occupiedNeighbors(String[][] occupied, int x, int y) {
        List<Neighbor> result = new ArrayList<Neighbor>();
        int radius = PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS;
        for (int gridX = Math.max(0, x - radius);
                gridX <= Math.min(occupied.length - 1, x + radius); gridX++)
            for (int gridY = Math.max(0, y - radius);
                    gridY <= Math.min(occupied[gridX].length - 1, y + radius); gridY++) {
                String net = occupied[gridX][gridY];
                if (net != null) result.add(new Neighbor(gridX, gridY, net));
            }
        return result;
    }

    private static void requireKnownNets(TreeMap<String,Integer> values, Set<String> knownNets) {
        for (String net : values.keySet())
            if (!knownNets.contains(net))
                throw new IllegalStateException("Router conflict map references an unknown board net");
    }

    private static void requireKnownOwner(String net, Set<String> knownNets, boolean allowMixedClearance) {
        if (net == null) return;
        if (net.length() == 0) {
            if (allowMixedClearance) return;
            throw new IllegalStateException("Router occupied grid contains a mixed-clearance sentinel");
        }
        if (!knownNets.contains(net))
            throw new IllegalStateException("Router grid references an unknown board net");
    }

    private static boolean hasForeignTargetOwner(String occupied, String clearance, String queryNet) {
        return occupied != null && !queryNet.equals(occupied) ||
            clearance != null && !queryNet.equals(clearance);
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

    private static String[][] reachedPadIds(PcbPadPlacement[] reached, String startId, String rootId) {
        List<String> all = new ArrayList<String>();
        List<String> eligible = new ArrayList<String>();
        for (PcbPadPlacement pad : reached) {
            if (pad == null) throw new IllegalStateException("Actual reached-PTH list contains null");
            String id = pad.getPadId();
            all.add(id);
            if (!startId.equals(id) && !rootId.equals(id)) eligible.add(id);
        }
        return new String[][] {all.toArray(new String[all.size()]),
            eligible.toArray(new String[eligible.size()])};
    }

    private static String stringArrayJson(String[] values) {
        StringBuilder json = new StringBuilder(128);
        json.append('[');
        for (int index = 0; index < values.length; index++) {
            if (index != 0) json.append(',');
            json.append(quote(values[index]));
        }
        return json.append(']').toString();
    }

    private static PcbPadPlacement padValue(Object value) {
        if (!(value instanceof PcbPadPlacement))
            throw new IllegalStateException("Selected branch endpoint is not a physical pad");
        return (PcbPadPlacement)value;
    }

    private static String padId(Object value) { return padValue(value).getPadId(); }

    private static int intField(Object owner, String name) {
        Object value = readField(owner, name);
        if (!(value instanceof Integer))
            throw new IllegalStateException("Canonical stage integer source field is unavailable: " + name);
        return ((Integer)value).intValue();
    }

    private static String stringField(Object value, String description) {
        if (!(value instanceof String) || ((String)value).length() == 0)
            throw new IllegalStateException("Canonical stage string source field is unavailable: " + description);
        return (String)value;
    }

    private static Object readField(Object owner, String name) {
        if (owner == null) throw new IllegalStateException("Cannot read null source field: " + name);
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot read actual stage source field: " + name, failure);
            }
        }
        throw new IllegalStateException("Actual stage source field is unavailable: " + name);
    }

    private static void verifyDirection(int fromX, int fromY, int toX, int toY, int bit) {
        int dx = bit == 2 ? 1 : bit == 8 ? -1 : 0;
        int dy = bit == 4 ? 1 : bit == 1 ? -1 : 0;
        if ((bit != 1 && bit != 2 && bit != 4 && bit != 8) ||
                toX - fromX != dx || toY - fromY != dy)
            throw new IllegalStateException("Frozen query bit differs from its directed grid edge");
    }

    private static String stageName(int stage) {
        if (stage < 0 || stage >= STAGES.length)
            throw new IllegalStateException("Router reported an unknown endpoint stage");
        return STAGES[stage];
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

    private static String quote(String value) {
        if (value == null) throw new IllegalArgumentException("Missing JSON string");
        StringBuilder result = new StringBuilder(value.length() + 2).append('"');
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

    private static String quoteOrNull(String value) { return value == null ? "null" : quote(value); }

    private static long elapsed(long started, long ended) {
        long result = ended - started;
        return result < 0 ? Long.MAX_VALUE : result;
    }

    private static long saturatedAdd(long first, long second) {
        if (first < 0 || second < 0 || Long.MAX_VALUE - first < second) return Long.MAX_VALUE;
        return first + second;
    }

    private static Throwable appendFailure(Throwable primary, Throwable secondary) {
        if (primary == null) return secondary;
        if (secondary != null && secondary != primary) primary.addSuppressed(secondary);
        return primary;
    }

    private static void rethrow(Throwable error) {
        if (error instanceof RuntimeException) throw (RuntimeException)error;
        if (error instanceof Error) throw (Error)error;
        throw new IllegalStateException("Canonical stage inspection failed", error);
    }

    private static void requirePayload(String json) {
        if (json == null || json.length() == 0 || json.indexOf('\r') >= 0 ||
                json.indexOf('\n') >= 0 || json.getBytes(UTF8).length + 1L > MAX_LINE_BYTES)
            throw new IllegalStateException("Canonical stage helper JSON exceeds its fixed single-line bound");
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (c != '-') return false;
            } else if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    private static boolean isSha256(String value) {
        if (value == null || value.length() != 64) return false;
        for (int i = 0; i < value.length(); i++)
            if (!((value.charAt(i) >= '0' && value.charAt(i) <= '9') ||
                    (value.charAt(i) >= 'a' && value.charAt(i) <= 'f'))) return false;
        return true;
    }

    private void runPredicateStageCanaries() {
        CanaryFixtureView fixture = blockerCanaryFixture(false, true);
        CanaryResult result = stageCanaryProbe(fixture, 220, 250, 210, 250, null);
        canaryCheck(!result.permits && "LEGAL_MOVE_REJECTED".equals(result.stages) &&
                result.routerDelta == 0 && result.workDelta == 0 && result.conflictDelta.isEmpty(),
            "literal escaped-start edge reaches the legal-move rejection stage", result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "legal-move stage canary preserves source and drops its observer", result);
        fixture = null; result = null;

        fixture = blockerCanaryFixture(true, false);
        result = stageCanaryProbe(fixture, 300, 250, 310, 250, null);
        canaryCheck(!result.permits && "TRAVERSE_REJECTED".equals(result.stages) &&
                result.routerDelta == 0 && result.workDelta == 0 && result.conflictDelta.isEmpty(),
            "literal non-copper courtyard edge reaches the traversal rejection stage", result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "traversal stage canary preserves source and drops its observer", result);
        fixture = null; result = null;

        fixture = blockerCanaryFixture(false, false);
        Vector<Point> foreign = new Vector<Point>();
        foreign.add(new Point(300, 250));
        fixture.router.occupyLayerPath(foreign, "FOREIGN");
        result = stageCanaryProbe(fixture, 290, 250, 300, 250, null);
        canaryCheck(!result.permits && "OCCUPANCY_REJECTED".equals(result.stages) &&
                result.routerDelta == 1 && result.workDelta == 1 &&
                "FOREIGN".equals(result.targetOccupied) &&
                Integer.valueOf(1).equals(result.conflictDelta.get("FOREIGN")),
            "literal foreign copper reaches the occupancy rejection stage", result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "foreign occupancy stage canary preserves source and drops its observer", result);
        foreign.clear(); fixture = null; result = null;

        fixture = blockerCanaryFixture(false, false);
        Vector<Point> sameNet = new Vector<Point>();
        sameNet.add(new Point(300, 250));
        fixture.router.occupyLayerPath(sameNet, "QUERY");
        result = stageCanaryProbe(fixture, 290, 250, 300, 250, null);
        canaryCheck(result.permits && "ALLOWED".equals(result.stages) &&
                result.routerDelta == 0 && result.workDelta == 0 &&
                "QUERY".equals(result.targetOccupied) && result.conflictDelta.isEmpty(),
            "literal same-net occupied cell reaches the allowed stage", result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "same-net occupancy stage canary preserves source and drops its observer", result);
        sameNet.clear(); fixture = null; result = null;

        fixture = blockerCanaryFixture(false, false);
        sameNet = new Vector<Point>();
        sameNet.add(new Point(300, 250));
        fixture.router.occupyLayerPath(sameNet, "QUERY");
        result = stageCanaryProbe(fixture, 290, 250, 300, 250, new PcbPadPlacement[0]);
        canaryCheck(result.permits && "ALLOWED".equals(result.stages) &&
                "Q1.1".equals(result.endpoints) &&
                result.routerDelta == 0 && result.workDelta == 0 &&
                "QUERY".equals(result.targetOccupied) && result.conflictDelta.isEmpty(),
            "empty reached-PTH array still evaluates the valid root endpoint first", result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "empty reached-PTH stage canary preserves source and drops its observer", result);
        sameNet.clear(); fixture = null; result = null;

        fixture = blockerCanaryFixture(false, false);
        sameNet = new Vector<Point>();
        sameNet.add(new Point(300, 250));
        fixture.router.occupyLayerPath(sameNet, "QUERY");
        result = stageCanaryProbe(fixture, 290, 260, 300, 260, null);
        canaryCheck(result.permits && "ALLOWED".equals(result.stages) &&
                result.routerDelta == 0 && result.workDelta == 0 &&
                result.targetOccupied == null && "QUERY".equals(result.targetClearance),
            "literal same-net clearance remains allowed", result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "same-net clearance stage canary preserves source and drops its observer", result);
        sameNet.clear(); fixture = null; result = null;

        fixture = blockerCanaryFixture(false, false);
        Vector<Point> ownerA = new Vector<Point>(); ownerA.add(new Point(300, 250));
        Vector<Point> ownerAB = new Vector<Point>(); ownerAB.add(new Point(300, 270));
        fixture.router.occupyLayerPath(ownerA, "A");
        fixture.router.occupyLayerPath(ownerAB, "AB");
        result = stageCanaryProbe(fixture, 290, 260, 300, 260, null);
        canaryCheck(!result.permits && "OCCUPANCY_REJECTED".equals(result.stages) &&
                result.routerDelta == 1 && result.workDelta == 1 &&
                result.targetOccupied == null && "".equals(result.targetClearance) &&
                Integer.valueOf(1).equals(result.conflictDelta.get("A")) &&
                Integer.valueOf(1).equals(result.conflictDelta.get("AB")) &&
                result.conflictDelta.size() == 2,
            "literal shared halo preserves its empty mixed-owner sentinel and both copper owners", result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "shared-halo stage canary preserves source and drops its observer", result);
        ownerA.clear(); ownerAB.clear(); fixture = null; result = null;
    }

    private void runEndpointOrderCanary() {
        CanaryFixtureView fixture = multiEndpointFixture();
        CanaryResult result = stageCanaryProbe(fixture, 310, 250, 320, 250, fixture.reached);
        canaryCheck(result.permits &&
                "LEGAL_MOVE_REJECTED,LEGAL_MOVE_REJECTED,ALLOWED".equals(result.stages) &&
                "Q1.1,Q2.1,Q3.1".equals(result.endpoints),
            "actual endpoint loop skips start/root duplicates, reaches multiple PTH candidates, and stops at first allowed endpoint",
            result);
        canaryCheck(result.sourceStable && result.observerDetached && result.observerReferencesDropped,
            "multi-endpoint early-success canary preserves source and drops its observer", result);
        fixture = null; result = null;
    }

    private void runBoundsCanary() {
        CanaryFixtureView fixture = blockerCanaryFixture(false, false);
        PcbNetRouter.Router source = fixture.router, pristine = null, query = null;
        QueryObserver observer = null;
        Throwable failure = null;
        boolean detached = false, sourceStable = false, permits = true, observerDropped = false;
        int eventsBefore = eventCount;
        int routerDelta = -1, workDelta = -1, endpointEvents = -1;
        String stageSequence = null;
        try {
            pristine = copyBounded(source).router;
            query = copyBounded(source).router;
            ComparisonResult liveBefore = compareBounded(source, pristine);
            ComparisonResult queryBefore = compareBounded(query, pristine);
            int minX = intField(query, "minX"), minY = intField(query, "minY");
            QueryContext context = new QueryContext();
            context.ordinal = 0; context.layer = ((PcbCopperLayer)readField(query, "layer")).ordinal();
            context.fromGridX = -1; context.fromGridY = 0; context.toGridX = 0; context.toGridY = 0;
            context.fromX = minX - PITCH; context.fromY = minY;
            context.toX = minX; context.toY = minY; context.direction = 1;
            context.netId = "QUERY"; context.startPadId = fixture.start.getPadId();
            int routerBefore = query.congestionRejections;
            int workBefore = workCongestion(query);
            observer = new QueryObserver(this, context,
                expectedEndpoints(fixture.root, null, fixture.start.getPadId(), fixture.root.getPadId()), false);
            query.attachFixedGraphStageObserverForStudy(observer, query, source, pristine,
                liveBefore.equal && queryBefore.equal);
            try {
                permits = query.permitsLayerStep(context.fromX, context.fromY,
                    context.toX, context.toY, context.netId, fixture.start, fixture.root, null);
                routerDelta = query.congestionRejections - routerBefore;
                workDelta = workCongestion(query) - workBefore;
                endpointEvents = observer.eventCount();
                stageSequence = observer.stageSequence();
            } finally {
                if (query.hasFixedGraphStageObserverForStudy()) {
                    query.detachFixedGraphStageObserverForStudy(); detachments++;
                }
                detached = !query.hasFixedGraphStageObserverForStudy();
            }
            ComparisonResult liveAfter = compareBounded(source, pristine);
            sourceStable = liveAfter.equal &&
                fixture.geometryBefore.equals(fixture.layout.geometryFingerprint()) &&
                fixture.effectsBefore.equals(source.fixedGraphObservationEffects());
        } catch (Throwable error) { failure = error; }
        finally {
            if (query != null && query.hasFixedGraphStageObserverForStudy()) {
                try { query.detachFixedGraphStageObserverForStudy(); detachments++; }
                catch (Throwable error) { failure = appendFailure(failure, error); }
            }
            if (observer != null) {
                try { observer.dropReferences(); observerDropped = observer.referencesDropped(); }
                catch (Throwable error) { failure = appendFailure(failure, error); }
            }
            if (!detached || !sourceStable)
                failure = appendFailure(failure,
                    new IllegalStateException("Bounds canary failed detach or source stability"));
            query = null; pristine = null; source = null; observer = null; fixture = null;
        }
        if (failure != null) rethrow(failure);
        CanaryResult actual = new CanaryResult();
        actual.permits = permits; actual.sourceStable = sourceStable; actual.observerDetached = detached;
        actual.observerReferencesDropped = observerDropped;
        actual.stages = stageSequence; actual.endpoints = "none";
        actual.routerDelta = routerDelta; actual.workDelta = workDelta;
        canaryCheck(!permits && endpointEvents == 0 && eventCount == eventsBefore &&
                routerDelta == 0 && workDelta == 0 && actual.sourceStable && actual.observerDetached &&
                actual.observerReferencesDropped,
            "out-of-bounds Router edge returns before endpoint observation", actual);
    }

    private void runAttachmentCanaries() {
        CanaryFixtureView fixture = blockerCanaryFixture(false, false);
        PcbNetRouter.Router source = fixture.router, pristine = null, query = null;
        try {
            pristine = copyBounded(source).router;
            query = copyBounded(source).router;
            ComparisonResult comparison = compareBounded(query, pristine);
            canaryCheck(comparison.equal, "attachment fixture query copy compares equal before attach");
            PcbNetRouter.Router.FixedGraphStageObserver noop = new NoopStageObserver();
            boolean nullObserver = rejectsAttachment(query, null, query, source, pristine, true);
            boolean duplicateRoles = rejectsAttachment(query, noop, query, query, pristine, true);
            boolean wrongReceiver = rejectsAttachment(source, noop, query, source, pristine, true);
            boolean failedComparison = rejectsAttachment(query, noop, query, source, pristine, false);
            canaryCheck(nullObserver && duplicateRoles && wrongReceiver && failedComparison &&
                    !query.hasFixedGraphStageObserverForStudy() &&
                    !source.hasFixedGraphStageObserverForStudy() &&
                    !pristine.hasFixedGraphStageObserverForStudy(),
                "observer attachment rejects null, aliased, wrong-owner, and unverified roles without mutation");
            query.attachFixedGraphStageObserverForStudy(noop, query, source, pristine, true);
            boolean duplicateAttach = false;
            try { query.attachFixedGraphStageObserverForStudy(noop, query, source, pristine, true); }
            catch (IllegalStateException expected) { duplicateAttach = true; }
            boolean remainedAttached = query.hasFixedGraphStageObserverForStudy();
            query.detachFixedGraphStageObserverForStudy();
            canaryCheck(duplicateAttach && remainedAttached &&
                    !query.hasFixedGraphStageObserverForStudy() &&
                    !source.hasFixedGraphStageObserverForStudy() &&
                    !pristine.hasFixedGraphStageObserverForStudy(),
                "duplicate attach is rejected and the single query observer detaches cleanly");
        } finally {
            if (query != null && query.hasFixedGraphStageObserverForStudy())
                query.detachFixedGraphStageObserverForStudy();
            source = null; query = null; pristine = null; fixture = null;
        }
    }

    private void runCallbackThrowCanary() {
        CanaryFixtureView fixture = blockerCanaryFixture(false, false);
        Vector<Point> foreign = new Vector<Point>(); foreign.add(new Point(300, 250));
        fixture.router.occupyLayerPath(foreign, "FOREIGN");
        fixture.geometryBefore = fixture.layout.geometryFingerprint();
        fixture.effectsBefore = fixture.router.fixedGraphObservationEffects();
        PcbNetRouter.Router source = fixture.router, pristine = null, query = null;
        QueryObserver observer = null;
        Throwable failure = null;
        boolean detached = false, sourceStable = false, exactSentinel = false;
        boolean observerDropped = false;
        try {
            pristine = copyBounded(source).router;
            query = copyBounded(source).router;
            ComparisonResult liveBefore = compareBounded(source, pristine);
            ComparisonResult queryBefore = compareBounded(query, pristine);
            QueryContext context = literalContext(0, query, "QUERY", fixture.start,
                290, 250, 300, 250);
            observer = new QueryObserver(this, context,
                expectedEndpoints(fixture.root, null, fixture.start.getPadId(), fixture.root.getPadId()), true);
            query.attachFixedGraphStageObserverForStudy(observer, query, source, pristine,
                liveBefore.equal && queryBefore.equal);
            try {
                query.permitsLayerStep(context.fromX, context.fromY, context.toX, context.toY,
                    context.netId, fixture.start, fixture.root, null);
            } catch (IllegalStateException expected) {
                exactSentinel = expected.getClass() == IllegalStateException.class &&
                    EXCEPTION_MESSAGE.equals(expected.getMessage());
                if (!exactSentinel) throw expected;
            }
            canaryCheck(exactSentinel && observer.eventCount() == 1 &&
                    "OCCUPANCY_REJECTED".equals(observer.stageSequence()) &&
                    "FIRST_ENDPOINT_CALLBACK".equals(exceptionStage),
                "callback-throw literal canary propagates only after recording the first actual endpoint stage",
                new CanaryResult(exactSentinel, observer.endpointSequence(),
                    observer.stageSequence(), query.congestionRejections,
                    query.work.congestionRejections, null, null, null, false));
        } catch (Throwable error) {
            failure = error;
        } finally {
            if (query != null) {
                try {
                    if (query.hasFixedGraphStageObserverForStudy()) {
                        query.detachFixedGraphStageObserverForStudy();
                        detachments++;
                    }
                    detached = !query.hasFixedGraphStageObserverForStudy();
                } catch (Throwable error) { failure = appendFailure(failure, error); }
            }
            if (observer != null) {
                try { observer.dropReferences(); observerDropped = observer.referencesDropped(); }
                catch (Throwable error) { failure = appendFailure(failure, error); }
            }
            try {
                if (source != null && pristine != null) {
                    ComparisonResult after = compareBounded(source, pristine);
                    sourceStable = after.equal &&
                        fixture.layout.geometryFingerprint().equals(fixture.geometryBefore) &&
                        fixture.effectsBefore.equals(source.fixedGraphObservationEffects());
                }
            } catch (Throwable error) { failure = appendFailure(failure, error); }
            if (!detached || !sourceStable || !observerDropped)
                failure = appendFailure(failure,
                    new IllegalStateException("Callback-throw canary failed reference release, detach, or source stability"));
            observer = null; query = null; pristine = null; source = null; fixture = null;
            foreign.clear(); foreign = null;
        }
        if (failure != null) rethrow(failure);
        canaryCheck(exactSentinel && detached && sourceStable && observerDropped,
            "callback-throw canary drops endpoint references, detaches the copied query, and preserves live source");
    }

    private void runGuardCanaries() {
        canaryCheck(constructorRejects("bad", sourceManifestSha256, wireSha256, true, false),
            "constructor rejects malformed run identity");
        canaryCheck(constructorRejects(runId, "bad", wireSha256, true, false),
            "constructor rejects malformed source manifest hash");
        canaryCheck(constructorRejects(runId, sourceManifestSha256, "bad", true, false),
            "constructor rejects malformed wire hash");
        canaryCheck(constructorRejects(runId, sourceManifestSha256, wireSha256, false, true),
            "constructor rejects callback throwing without observation mode");

        CanonicalPrefixStageInspector mismatched = new CanonicalPrefixStageInspector(
            runId, sourceManifestSha256, wireSha256, true, false);
        mismatched.onTerminalPrefix(null, null, null, null, null, 0, 0, 0);
        boolean priorAllowedAfterOffers = false, repeatedPriorRejected = false;
        try { mismatched.setPriorObservationUnits(0); priorAllowedAfterOffers = true; }
        catch (RuntimeException ignored) { }
        try { mismatched.setPriorObservationUnits(0); }
        catch (IllegalStateException expected) { repeatedPriorRejected = true; }
        boolean incompleteCloseRejected = false, duplicateCloseRejected = false;
        try { mismatched.afterOwnersClosed(true, true); }
        catch (IllegalStateException expected) { incompleteCloseRejected = true; }
        try { mismatched.afterOwnersClosed(true, true); }
        catch (IllegalStateException expected) { duplicateCloseRejected = true; }
        boolean callbackAfterCloseRejected = false;
        try { mismatched.onTerminalPrefix(null, null, null, null, null, 0, 0, 0); }
        catch (IllegalStateException expected) {
            callbackAfterCloseRejected =
                "Canonical prefix stage callback followed owner close".equals(expected.getMessage());
        }
        boolean beforeCloseRejected = false;
        CanonicalPrefixStageInspector lifecycle = new CanonicalPrefixStageInspector(
            runId, sourceManifestSha256, wireSha256, true, false);
        try { lifecycle.completedJson(CanonicalPrefixCutQueries.GRAPH_SHA); }
        catch (IllegalStateException expected) { beforeCloseRejected = true; }
        canaryCheck(mismatched.offers == 1 && mismatched.selected == 0 &&
                mismatched.referencesDropped() && priorAllowedAfterOffers && repeatedPriorRejected &&
                incompleteCloseRejected && duplicateCloseRejected && callbackAfterCloseRejected &&
                beforeCloseRejected,
            "identity mismatch, prior-offset binding, pre-close completion, duplicate close, and post-close callback guards hold");
    }

    private boolean constructorRejects(String candidateRunId, String candidateInputSha,
            String candidateWireSha, boolean observeMode, boolean throwMode) {
        try {
            new CanonicalPrefixStageInspector(candidateRunId, candidateInputSha,
                candidateWireSha, observeMode, throwMode);
            return false;
        } catch (IllegalArgumentException expected) { return true; }
    }

    private static boolean rejectsAttachment(PcbNetRouter.Router receiver,
            PcbNetRouter.Router.FixedGraphStageObserver observer,
            PcbNetRouter.Router expectedQuery, PcbNetRouter.Router live,
            PcbNetRouter.Router pristine, boolean compared) {
        try {
            receiver.attachFixedGraphStageObserverForStudy(observer, expectedQuery,
                live, pristine, compared);
            return false;
        } catch (IllegalArgumentException expected) { return true; }
        catch (IllegalStateException expected) { return true; }
    }

    private CanaryResult stageCanaryProbe(CanaryFixtureView fixture, int fromX, int fromY,
            int toX, int toY, PcbPadPlacement[] reached) {
        if (fixture == null || fixture.router == null || fixture.start == null || fixture.root == null)
            throw new IllegalStateException("Stage canary lacks a literal router fixture");
        CanaryResult result = new CanaryResult();
        PcbNetRouter.Router source = fixture.router, pristine = null, query = null;
        QueryObserver observer = null;
        String[][] occupied = null, clearance = null;
        TreeMap<String,Integer> conflictsBefore = null, conflictsAfter = null, delta = null;
        List<Neighbor> neighbors = null;
        Set<String> knownNets = null;
        Throwable failure = null;
        boolean attached = false;
        String geometryBefore = fixture.layout.geometryFingerprint();
        String effectsBefore = source.fixedGraphObservationEffects();
        try {
            pristine = copyBounded(source).router;
            query = copyBounded(source).router;
            ComparisonResult liveBefore = compareBounded(source, pristine);
            ComparisonResult queryBefore = compareBounded(query, pristine);
            knownNets = new HashSet<String>(fixture.board.getNetIds());
            QueryContext context = literalContext(0, query, "QUERY", fixture.start,
                fromX, fromY, toX, toY);
            occupied = stringGrid(query, "occupiedNet");
            clearance = stringGrid(query, "clearanceNet");
            result.targetOccupied = occupied[context.toGridX][context.toGridY];
            result.targetClearance = clearance[context.toGridX][context.toGridY];
            requireKnownOwner(result.targetOccupied, knownNets, false);
            requireKnownOwner(result.targetClearance, knownNets, true);
            neighbors = occupiedNeighbors(occupied, context.toGridX, context.toGridY);
            conflictsBefore = snapshotConflicts(query.conflicts);
            requireKnownNets(conflictsBefore, knownNets);
            result.routerBefore = query.congestionRejections;
            result.workBefore = workCongestion(query);
            observer = new QueryObserver(this, context,
                expectedEndpoints(fixture.root, reached, fixture.start.getPadId(), fixture.root.getPadId()),
                false);
            query.attachFixedGraphStageObserverForStudy(observer, query, source, pristine,
                liveBefore.equal && queryBefore.equal);
            attached = true; attachments++;
            result.permits = query.permitsLayerStep(context.fromX, context.fromY,
                context.toX, context.toY, context.netId, fixture.start, fixture.root, reached);
            result.routerAfter = query.congestionRejections;
            result.workAfter = workCongestion(query);
            conflictsAfter = snapshotConflicts(query.conflicts);
            requireKnownNets(conflictsAfter, knownNets);
            delta = conflictsDelta(conflictsBefore, conflictsAfter);
            result.routerDelta = result.routerAfter - result.routerBefore;
            result.workDelta = result.workAfter - result.workBefore;
            if (result.routerDelta < 0 || result.workDelta != result.routerDelta)
                throw new IllegalStateException("Literal canary Router/work congestion deltas disagree");
            verifyConflictDelta(delta, neighborHistogram(neighbors, "QUERY"),
                result.routerDelta, "QUERY");
            result.conflictDelta = delta;
            observer.verifyReturned(result.permits);
            result.stages = observer.stageSequence();
            result.endpoints = observer.endpointSequence();
        } catch (Throwable error) { failure = error; }
        finally {
            if (attached && query != null) {
                try { query.detachFixedGraphStageObserverForStudy(); detachments++; }
                catch (Throwable error) { failure = appendFailure(failure, error); }
            }
            if (query != null) {
                try { result.observerDetached = !query.hasFixedGraphStageObserverForStudy(); }
                catch (Throwable error) { failure = appendFailure(failure, error); }
            }
            if (observer != null) {
                try {
                    observer.dropReferences();
                    result.observerReferencesDropped = observer.referencesDropped();
                }
                catch (Throwable error) { failure = appendFailure(failure, error); }
            }
            try {
                if (source != null && pristine != null) {
                    ComparisonResult liveAfter = compareBounded(source, pristine);
                    result.sourceStable = liveAfter.equal &&
                        geometryBefore.equals(fixture.layout.geometryFingerprint()) &&
                        effectsBefore.equals(source.fixedGraphObservationEffects());
                }
            } catch (Throwable error) { failure = appendFailure(failure, error); }
            if (conflictsBefore != null) conflictsBefore.clear();
            if (conflictsAfter != null) conflictsAfter.clear();
            if (delta != null && delta != result.conflictDelta) delta.clear();
            if (neighbors != null) neighbors.clear();
            if (knownNets != null) knownNets.clear();
            occupied = null; clearance = null; conflictsBefore = null; conflictsAfter = null;
            delta = null; neighbors = null; knownNets = null; observer = null;
            geometryBefore = null; effectsBefore = null;
            query = null; pristine = null; source = null;
        }
        if (failure != null) rethrow(failure);
        return result;
    }

    private static CanaryFixtureView blockerCanaryFixture(boolean courtyard, boolean escaped) {
        try {
            Class<?> type = Class.forName(
                "com.lushprojects.circuitjs1.client.CanonicalPrefixBlockerInspector$CanariesFixture");
            Constructor<?> constructor = type.getDeclaredConstructor(Boolean.TYPE, Boolean.TYPE);
            constructor.setAccessible(true);
            Object value = constructor.newInstance(Boolean.valueOf(courtyard), Boolean.valueOf(escaped));
            CanaryFixtureView result = new CanaryFixtureView();
            result.board = (TroubleshootBoard)readField(value, "board");
            result.layout = (PcbBoardLayout)readField(value, "layout");
            result.start = (PcbPadPlacement)readField(value, "start");
            result.root = (PcbPadPlacement)readField(value, "root");
            result.router = (PcbNetRouter.Router)readField(value, "router");
            result.geometryBefore = result.layout.geometryFingerprint();
            result.effectsBefore = result.router.fixedGraphObservationEffects();
            return result;
        } catch (Throwable error) { rethrow(error); throw new AssertionError("unreachable"); }
    }

    private static CanaryFixtureView multiEndpointFixture() {
        CanaryFixtureView result = new CanaryFixtureView();
        result.board = new TroubleshootBoard("CANONICAL_STAGE_MULTI_ENDPOINT_CANARY");
        result.board.addNet(new BoardNet("QUERY"));
        result.layout = new PcbBoardLayout(800, 600, new Rectangle(100, 100, 400, 400),
            new Rectangle(550, 120, 180, 300));
        PcbPadPlacement start = addCanaryPad(result.board, result.layout, "Q0", 200, 250, 0, 0, 0);
        PcbPadPlacement root = addCanaryPad(result.board, result.layout, "Q1", 310, 250, 1, 0, 30);
        PcbPadPlacement second = addCanaryPad(result.board, result.layout, "Q2", 310, 250, 1, 0, 30);
        PcbPadPlacement third = addCanaryPad(result.board, result.layout, "Q3", 450, 350, 0, 0, 0);
        PcbPadPlacement last = addCanaryPad(result.board, result.layout, "Q4", 460, 350, 0, 0, 0);
        result.start = start; result.root = root;
        result.reached = new PcbPadPlacement[] {start, root, second, third, last};
        result.board.setPlacementConstraints(PcbPlacementConstraints.standard(result.board));
        result.router = new PcbNetRouter.Router(result.layout, result.board,
            result.layout.getBoardOutline(), 0, new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { }
            }, PcbCopperLayer.TOP);
        result.geometryBefore = result.layout.geometryFingerprint();
        result.effectsBefore = result.router.fixedGraphObservationEffects();
        return result;
    }

    private static PcbPadPlacement addCanaryPad(TroubleshootBoard board, PcbBoardLayout layout,
            String id, int x, int y, int escapeDx, int escapeDy, int escapeLength) {
        BoardComponent component = new BoardComponent(id, "TEST_POINT", canaryPointPackage(), id);
        board.addComponent(component);
        board.addPad(new BoardPad(id + ".1", id, "1", "QUERY"));
        PcbPadPlacement pad = new PcbPadPlacement(id + ".1", x, y, escapeDx, escapeDy,
            escapeLength, new Rectangle(x - 4, y - 4, 8, 8),
            new Rectangle(x - 6, y - 6, 12, 12),
            PcbTerminalAttachment.PLATED_THROUGH_HOLE, PcbBoardSide.TOP);
        layout.addPad(pad);
        return pad;
    }

    private static PhysicalPackage canaryPointPackage() {
        try {
            Class<?> type = Class.forName(
                "com.lushprojects.circuitjs1.client.CanonicalPrefixBlockerInspector");
            Method method = type.getDeclaredMethod("pointPackage");
            method.setAccessible(true);
            return (PhysicalPackage)method.invoke(null);
        } catch (Throwable error) { rethrow(error); throw new AssertionError("unreachable"); }
    }

    private void canaryCheck(boolean condition, String message) {
        canaryAssertions++;
        if (!condition) throw new IllegalStateException("CANONICAL_PREFIX_STAGE_CANARY: " + message);
    }

    private void canaryCheck(boolean condition, String message, CanaryResult actual) {
        canaryAssertions++;
        if (!condition) throw new IllegalStateException("CANONICAL_PREFIX_STAGE_CANARY: " +
            message + "; actual=" + actual);
    }

    private static final class CanaryFixtureView {
        TroubleshootBoard board;
        PcbBoardLayout layout;
        PcbPadPlacement start, root;
        PcbPadPlacement[] reached;
        PcbNetRouter.Router router;
        String geometryBefore, effectsBefore;
    }

    private static final class NoopStageObserver
            implements PcbNetRouter.Router.FixedGraphStageObserver {
        public void onEndpoint(String netId, String startPadId, String endpointPadId,
                int fromGridX, int fromGridY, int toGridX, int toGridY,
                int direction, int stage) { }
    }

    private static final class QueryContext {
        int ordinal, layer, fromState, toState, bit;
        int fromGridX, fromGridY, toGridX, toGridY;
        int fromX, fromY, toX, toY, direction;
        String netId, startPadId;
        boolean selected;
    }

    private static final class QueryObservation {
        String json, conflictsBeforeJson, conflictsAfterJson, conflictsDeltaJson, eventsJson;
        int routerBefore, routerAfter, routerDelta, workBefore, workAfter, workDelta;
        Boolean permits;
        boolean expectedCallbackThrow;
        Throwable callbackFailure;
    }

    private static final class CopyResult {
        PcbNetRouter.Router router;
        int entries;
        long units, maximumUnitNanos;
    }

    private static final class ComparisonResult {
        boolean equal;
        int entries;
        long units, maximumUnitNanos;
    }

    private static final class Neighbor {
        final int gridX, gridY;
        final String netId;
        Neighbor(int x, int y, String net) { gridX = x; gridY = y; netId = net; }
    }

    private static final class StageEvent {
        final int ordinal, fromGridX, fromGridY, toGridX, toGridY, direction, stage;
        final String netId, startPadId, endpointPadId;
        StageEvent(int eventOrdinal, String net, String start, String endpoint,
                int fromX, int fromY, int toX, int toY, int dir, int stageValue) {
            ordinal = eventOrdinal; netId = net; startPadId = start; endpointPadId = endpoint;
            fromGridX = fromX; fromGridY = fromY; toGridX = toX; toGridY = toY;
            direction = dir; stage = stageValue;
        }
    }

    private static final class QueryObserver
            implements PcbNetRouter.Router.FixedGraphStageObserver {
        private CanonicalPrefixStageInspector inspector;
        private QueryContext context;
        private String[] expectedEndpoints;
        private List<StageEvent> events = new ArrayList<StageEvent>();
        private final boolean throwOnFirst;

        QueryObserver(CanonicalPrefixStageInspector owner, QueryContext query,
                String[] endpoints, boolean throwOnFirstEvent) {
            if (owner == null || query == null || endpoints == null || endpoints.length == 0)
                throw new IllegalArgumentException("Incomplete fixed stage observer context");
            inspector = owner; context = query;
            expectedEndpoints = endpoints.clone(); throwOnFirst = throwOnFirstEvent;
        }

        public void onEndpoint(String netId, String startPadId, String endpointPadId,
                int fromGridX, int fromGridY, int toGridX, int toGridY,
                int direction, int stage) {
            if (inspector == null || context == null || events == null ||
                    events.size() >= expectedEndpoints.length)
                throw new IllegalStateException("Stage observer received an unexpected extra endpoint");
            int ordinal = events.size();
            if (!context.netId.equals(netId) || !context.startPadId.equals(startPadId) ||
                    !expectedEndpoints[ordinal].equals(endpointPadId) ||
                    context.fromGridX != fromGridX || context.fromGridY != fromGridY ||
                    context.toGridX != toGridX || context.toGridY != toGridY ||
                    context.direction != direction || stage < 0 || stage >= STAGES.length)
                throw new IllegalStateException("Stage event differs from actual query endpoint order or coordinates");
            events.add(new StageEvent(ordinal, netId, startPadId, endpointPadId,
                fromGridX, fromGridY, toGridX, toGridY, direction, stage));
            if (ordinal == 0) inspector.callbackQueryCount++;
            inspector.eventCount++;
            if (throwOnFirst && ordinal == 0) {
                inspector.exceptionStage = "FIRST_ENDPOINT_CALLBACK";
                throw new IllegalStateException(EXCEPTION_MESSAGE);
            }
        }

        void verifyReturned(boolean permits) {
            if (events == null || events.isEmpty() || events.size() > expectedEndpoints.length)
                throw new IllegalStateException("Stage observer recorded an invalid returned endpoint count");
            for (int i = 0; i < events.size(); i++) {
                boolean last = i == events.size() - 1;
                if (events.get(i).stage == PcbNetRouter.Router.STAGE_ALLOWED) {
                    if (!permits || !last)
                        throw new IllegalStateException("Stage observer saw ALLOWED without matching early success");
                } else if (last && permits)
                    throw new IllegalStateException("Stage predicate returned true without an ALLOWED event");
            }
            if (!permits && events.size() != expectedEndpoints.length)
                throw new IllegalStateException("False stage predicate omitted an actual endpoint event");
        }

        void verifyExpectedThrow() {
            if (events == null || events.size() != 1 || inspector == null ||
                    !"FIRST_ENDPOINT_CALLBACK".equals(inspector.exceptionStage))
                throw new IllegalStateException("Stage callback throw did not follow its first actual event");
        }

        String eventsJson() {
            if (events == null) throw new IllegalStateException("Stage observer references were already dropped");
            StringBuilder json = new StringBuilder(512).append('[');
            for (int i = 0; i < events.size(); i++) {
                if (i != 0) json.append(',');
                StageEvent event = events.get(i);
                json.append("{\"ordinal\":").append(event.ordinal)
                    .append(",\"netId\":").append(quote(event.netId))
                    .append(",\"startPadId\":").append(quote(event.startPadId))
                    .append(",\"endpointPadId\":").append(quoteOrNull(event.endpointPadId))
                    .append(",\"fromGridX\":").append(event.fromGridX)
                    .append(",\"fromGridY\":").append(event.fromGridY)
                    .append(",\"toGridX\":").append(event.toGridX)
                    .append(",\"toGridY\":").append(event.toGridY)
                    .append(",\"direction\":").append(event.direction)
                    .append(",\"stage\":").append(quote(stageName(event.stage))).append('}');
            }
            return json.append(']').toString();
        }

        String stageSequence() {
            if (events == null) return "<dropped>";
            StringBuilder result = new StringBuilder();
            for (StageEvent event : events) {
                if (result.length() != 0) result.append(',');
                result.append(stageName(event.stage));
            }
            return result.toString();
        }

        String endpointSequence() {
            if (events == null) return "<dropped>";
            StringBuilder result = new StringBuilder();
            for (StageEvent event : events) {
                if (result.length() != 0) result.append(',');
                result.append(event.endpointPadId == null ? "null" : event.endpointPadId);
            }
            return result.toString();
        }

        int eventCount() { return events == null ? 0 : events.size(); }

        boolean referencesDropped() {
            return inspector == null && context == null && expectedEndpoints == null && events == null;
        }

        void dropReferences() {
            if (events != null) events.clear();
            events = null;
            if (expectedEndpoints != null) java.util.Arrays.fill(expectedEndpoints, null);
            expectedEndpoints = null;
            context = null;
            inspector = null;
        }
    }

    private static final class CanaryResult {
        boolean permits, sourceStable, observerDetached, observerReferencesDropped;
        int routerBefore, routerAfter, routerDelta, workBefore, workAfter, workDelta;
        String targetOccupied, targetClearance, stages, endpoints;
        TreeMap<String,Integer> conflictDelta = new TreeMap<String,Integer>();
        CanaryResult() { }
        CanaryResult(boolean actualPermits, String actualEndpoints, String actualStages,
                int actualRouterDelta, int actualWorkDelta, String occupied,
                String clearance, TreeMap<String,Integer> conflicts, boolean stable) {
            permits = actualPermits; endpoints = actualEndpoints; stages = actualStages;
            routerDelta = actualRouterDelta; workDelta = actualWorkDelta;
            targetOccupied = occupied; targetClearance = clearance;
            if (conflicts != null) conflictDelta = conflicts;
            sourceStable = stable;
        }
        public String toString() {
            return "permits=" + permits + ",stages=" + quoteOrNull(stages) +
                ",endpoints=" + quoteOrNull(endpoints) + ",routerBefore=" + routerBefore +
                ",routerAfter=" + routerAfter + ",routerDelta=" + routerDelta +
                ",workBefore=" + workBefore + ",workAfter=" + workAfter +
                ",workDelta=" + workDelta + ",targetOccupied=" + quoteOrNull(targetOccupied) +
                ",targetClearance=" + quoteOrNull(targetClearance) +
                ",conflictDelta=" + countMapJson(conflictDelta) +
                ",sourceStable=" + sourceStable + ",observerDetached=" + observerDetached +
                ",observerReferencesDropped=" + observerReferencesDropped;
        }
    }
}
