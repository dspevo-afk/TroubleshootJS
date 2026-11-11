package com.lushprojects.circuitjs1.client;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Current V2 canonical body and marker formatting policy.
 *
 * The body and marker digest are borrowed. Their caller must keep them
 * exclusively stable through count, write, compare, and publication.
 */
final class CurrentV2CanonicalCodec {
    private static final String RESULT_SCHEMA = "tsj-cohort-terminal-result-v2";
    private static final String RESULT_MODE = "OBSERVED_COHORT_TERMINAL_TELEMETRY";
    private static final String RESULT_STATUS = "COMPLETED_DIAGNOSTIC_ONLY";
    private static final String MARKER_SCHEMA = "tsj-cohort-terminal-marker-v2";
    private static final String MARKER_STATUS = "COMPLETED_DIAGNOSTIC";
    private static final String WIRE_CONTRACT_SHA256 =
            "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed";
    private static final String HEX = "0123456789abcdef";

    private static final int BODY_MAX_DEPTH = 64;
    private static final long BODY_MAX_VISITS = 1000000L;
    private static final int BODY_STRING_LIMIT = 256;
    private static final int MARKER_MAX_BYTES = 656;
    private static final int MARKER_TRANSPORT_MAX_BYTES = 722;

    private static final String[] BODY_ROOT_KEYS = {
        "schema", "mode", "status", "runId", "sourceManifestSha256",
        "wireContractSha256", "inputContextSha256", "qualifiedPrior",
        "originalCapture", "cohort", "arms", "work", "cleanup"
    };

    static final class CodecFailure extends IllegalArgumentException {
        final String code;

        CodecFailure(String code) {
            super(code);
            this.code = code;
        }
    }

    /**
     * The digest reference is borrowed without cloning. The caller must not
     * mutate it while either marker operation or publication is in progress.
     */
    static final class Marker {
        private final String runId;
        private final String sourceManifestSha256;
        private final String wireContractSha256;
        private final String inputContextSha256;
        private final byte[] resultDigest;
        private final long resultBytes;
        private final long elapsedNanos;
        private final long finalExportNanos;
        private final long totalGroups;
        private final long totalUnits;
        private final long maxGroupNanos;
        private final long maximumUnitsPerGroup;

        Marker(
                String runId,
                String sourceManifestSha256,
                String wireContractSha256,
                String inputContextSha256,
                byte[] resultDigest,
                long resultBytes,
                long elapsedNanos,
                long finalExportNanos,
                long totalGroups,
                long totalUnits,
                long maxGroupNanos,
                long maximumUnitsPerGroup) {
            this.runId = runId;
            this.sourceManifestSha256 = sourceManifestSha256;
            this.wireContractSha256 = wireContractSha256;
            this.inputContextSha256 = inputContextSha256;
            this.resultDigest = resultDigest;
            this.resultBytes = resultBytes;
            this.elapsedNanos = elapsedNanos;
            this.finalExportNanos = finalExportNanos;
            this.totalGroups = totalGroups;
            this.totalUnits = totalUnits;
            this.maxGroupNanos = maxGroupNanos;
            this.maximumUnitsPerGroup = maximumUnitsPerGroup;
        }
    }

    private static final class BodyWalk {
        long visits;
        final Object[] active = new Object[BODY_MAX_DEPTH + 1];
    }

    private static final class MarkerSink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        final byte[] target;
        int position;
        boolean ended;

        MarkerSink(byte[] target) {
            this.target = target;
        }

        public void beforeDataOperation() {
            if (ended) throw failure("MARKER_OUTPUT");
            int limit = target == null ? MARKER_MAX_BYTES : target.length;
            if (position >= limit) throw failure("MARKER_LENGTH");
        }

        public void writeByte(int ascii) {
            beforeDataOperation();
            if (ascii != 10 && (ascii < 32 || ascii > 126))
                throw failure("MARKER_OUTPUT");
            if (target != null) target[position] = (byte) ascii;
            position++;
            if (ascii == 10) ended = true;
        }

        void complete() {
            if (!ended || position < 2 || position > MARKER_MAX_BYTES ||
                    position > MARKER_TRANSPORT_MAX_BYTES)
                throw failure("MARKER_LENGTH");
            if (target != null &&
                    (position != target.length || target[position - 1] != (byte) 10))
                throw failure("MARKER_LENGTH");
        }
    }

    /**
     * Stages the current result body from caller-prevalidated header values and
     * borrowed child maps. The caller keeps those children stable through later
     * preparation, R7, and export; the two null slots are filled by later owners.
     */
    static Map<String, Object> createBody(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            String runId,
            String sourceManifestSha,
            String inputContextSha,
            Map<String, Object> originalCapture,
            Map<String, Object> cohort,
            Map<String, Object> arms,
            Map<String, Object> cleanup) throws Exception {
        if (meter == null) throw failure("BODY_CONSTRUCTOR_METER");

        meter.copy(13);
        meter.charge(1);
        Map<String, Object> body =
                new java.util.LinkedHashMap<String, Object>(32);
        body.put(BODY_ROOT_KEYS[0], RESULT_SCHEMA);
        body.put(BODY_ROOT_KEYS[1], RESULT_MODE);
        body.put(BODY_ROOT_KEYS[2], RESULT_STATUS);
        body.put(BODY_ROOT_KEYS[3], runId);
        body.put(BODY_ROOT_KEYS[4], sourceManifestSha);
        body.put(BODY_ROOT_KEYS[5], WIRE_CONTRACT_SHA256);
        body.put(BODY_ROOT_KEYS[6], inputContextSha);
        body.put(BODY_ROOT_KEYS[7], null);
        body.put(BODY_ROOT_KEYS[8], originalCapture);
        body.put(BODY_ROOT_KEYS[9], cohort);
        body.put(BODY_ROOT_KEYS[10], arms);
        body.put(BODY_ROOT_KEYS[11], null);
        body.put(BODY_ROOT_KEYS[12], cleanup);
        return body;
    }
    /** Partial current-result correlations, not full DTO qualification. */
    static void writeCorrelatedBody(
            Map<String, Object> body,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        if (sink == null) throw failure("BODY_SINK");
        validateBodyHeader(body, sink);
        validateCorrelations(body, sink);
        writeBodyValue(body, sink, new BodyWalk(), 0);
        sink.writeByte(10);
    }

    private static void validateCorrelations(Map<String, Object> body,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        Object context = correlationRead(body, "inputContextSha256", sink);
        Map<?, ?> prior = correlationMap(
                correlationRead(body, "qualifiedPrior", sink), sink);
        Object priorContext = correlationRead(prior, "inputContextSha256", sink);
        sink.beforeDataOperation();
        if (!(context instanceof String) || !(priorContext instanceof String) ||
                !((String) context).equals(priorContext))
            throw failure("BODY_PRIOR_CONTEXT");

        Map<?, ?> arms = correlationMap(correlationRead(body, "arms", sink), sink);
        Map<?, ?> nullArm = correlationMap(correlationRead(arms, "null", sink), sink);
        Object nullRole = correlationRead(nullArm, "armRole", sink);
        sink.beforeDataOperation();
        if (!"NULL".equals(nullRole)) throw failure("BODY_NULL_ROLE");
        Map<?, ?> observeArm = correlationMap(
                correlationRead(arms, "observe", sink), sink);
        Object observeRole = correlationRead(observeArm, "armRole", sink);
        sink.beforeDataOperation();
        if (!"OBSERVE".equals(observeRole)) throw failure("BODY_OBSERVE_ROLE");

        Map<?, ?> cleanup = correlationMap(correlationRead(body, "cleanup", sink), sink);
        Map<?, ?> work = correlationMap(correlationRead(body, "work", sink), sink);
        long cleanupNanos = correlationNanos(
                correlationRead(cleanup, "cleanupNanos", sink), sink);
        long workNanos = correlationNanos(
                correlationRead(work, "cleanupNanos", sink), sink);
        sink.beforeDataOperation();
        if (cleanupNanos != workNanos) throw failure("BODY_CLEANUP_NANOS");
        Map<?, ?> capture = correlationMap(
                correlationRead(body, "originalCapture", sink), sink);
        boolean captureRestored = correlationBoolean(capture, "contextRestored", sink, "BODY_RESTORATION_BOOLEAN");
        Map<?, ?> nullCleanup = correlationMap(
                correlationRead(nullArm, "cleanup", sink), sink);
        boolean nullRestored = correlationBoolean(nullCleanup, "contextRestored", sink, "BODY_RESTORATION_BOOLEAN");
        Map<?, ?> observeCleanup = correlationMap(
                correlationRead(observeArm, "cleanup", sink), sink);
        boolean observeRestored = correlationBoolean(observeCleanup, "contextRestored", sink, "BODY_RESTORATION_BOOLEAN");
        boolean captureSummary = correlationBoolean(
                cleanup, "originalCaptureContextRestored", sink, "BODY_RESTORATION_BOOLEAN");
        boolean totalSummary = correlationBoolean(cleanup, "contextRestored", sink, "BODY_RESTORATION_BOOLEAN");
        sink.beforeDataOperation();
        boolean allRestored = captureRestored & nullRestored;
        sink.beforeDataOperation();
        allRestored = allRestored & observeRestored;
        sink.beforeDataOperation();
        if (captureSummary != captureRestored) throw failure("BODY_RESTORATION_CAPTURE");
        sink.beforeDataOperation();
        if (totalSummary != allRestored) throw failure("BODY_RESTORATION_TOTAL");
        boolean captureOwnersReleased = correlationBoolean(
                capture, "ownersReleased", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean captureOwnersSummary = correlationBoolean(
                cleanup, "originalCaptureOwnersReleased", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean nullSessionDropped = correlationBoolean(
                nullCleanup, "adapterSessionAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean nullSearchDropped = correlationBoolean(
                nullCleanup, "adapterSearchAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean nullCandidateDropped = correlationBoolean(
                nullCleanup, "adapterCandidateAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean nullRouteDropped = correlationBoolean(
                nullCleanup, "adapterRouteResultAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean nullBoardAliasesDropped = correlationBoolean(
                nullCleanup, "adapterBoardTemplateAliasesDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean nullAliasSummary = correlationBoolean(
                cleanup, "nullArmAliasesDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean observeSessionDropped = correlationBoolean(
                observeCleanup, "adapterSessionAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean observeSearchDropped = correlationBoolean(
                observeCleanup, "adapterSearchAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean observeCandidateDropped = correlationBoolean(
                observeCleanup, "adapterCandidateAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean observeRouteDropped = correlationBoolean(
                observeCleanup, "adapterRouteResultAliasDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean observeBoardAliasesDropped = correlationBoolean(
                observeCleanup, "adapterBoardTemplateAliasesDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        boolean observeAliasSummary = correlationBoolean(
                cleanup, "observeArmAliasesDropped", sink, "BODY_OWNERSHIP_BOOLEAN");
        sink.beforeDataOperation();
        boolean nullAllDropped = nullSessionDropped & nullSearchDropped;
        sink.beforeDataOperation();
        nullAllDropped = nullAllDropped & nullCandidateDropped;
        sink.beforeDataOperation();
        nullAllDropped = nullAllDropped & nullRouteDropped;
        sink.beforeDataOperation();
        nullAllDropped = nullAllDropped & nullBoardAliasesDropped;
        sink.beforeDataOperation();
        boolean observeAllDropped = observeSessionDropped & observeSearchDropped;
        sink.beforeDataOperation();
        observeAllDropped = observeAllDropped & observeCandidateDropped;
        sink.beforeDataOperation();
        observeAllDropped = observeAllDropped & observeRouteDropped;
        sink.beforeDataOperation();
        observeAllDropped = observeAllDropped & observeBoardAliasesDropped;
        sink.beforeDataOperation();
        if (captureOwnersSummary != captureOwnersReleased) throw failure("BODY_OWNERSHIP_CAPTURE");
        sink.beforeDataOperation();
        if (nullAliasSummary != nullAllDropped) throw failure("BODY_OWNERSHIP_NULL");
        sink.beforeDataOperation();
        if (observeAliasSummary != observeAllDropped) throw failure("BODY_OWNERSHIP_OBSERVE");
        Map<?, ?> geometryTemplate = correlationMap(
                correlationRead(capture, "template", sink), sink);
        String geometryBefore = correlationString(
                geometryTemplate, "geometryBeforeSha256", sink, "BODY_GEOMETRY_STRING");
        String geometryAfter = correlationString(
                geometryTemplate, "geometryAfterSha256", sink, "BODY_GEOMETRY_STRING");
        String nullGeometryBefore = correlationString(
                nullCleanup, "sourceGeometryBeforeSha256", sink, "BODY_GEOMETRY_STRING");
        String nullGeometryAfter = correlationString(
                nullCleanup, "sourceGeometryAfterSha256", sink, "BODY_GEOMETRY_STRING");
        String observeGeometryBefore = correlationString(
                observeCleanup, "sourceGeometryBeforeSha256", sink, "BODY_GEOMETRY_STRING");
        String observeGeometryAfter = correlationString(
                observeCleanup, "sourceGeometryAfterSha256", sink, "BODY_GEOMETRY_STRING");
        boolean geometrySummary = correlationBoolean(
                cleanup, "sourceGeometryUnchanged", sink, "BODY_GEOMETRY_BOOLEAN");
        sink.beforeDataOperation();
        boolean geometryMatch1 = geometryBefore.equals(geometryAfter);
        sink.beforeDataOperation();
        boolean geometryMatch2 = geometryBefore.equals(nullGeometryBefore);
        sink.beforeDataOperation();
        boolean geometryMatch3 = geometryBefore.equals(nullGeometryAfter);
        sink.beforeDataOperation();
        boolean geometryMatch4 = geometryBefore.equals(observeGeometryBefore);
        sink.beforeDataOperation();
        boolean geometryMatch5 = geometryBefore.equals(observeGeometryAfter);
        sink.beforeDataOperation();
        boolean geometryUnchanged = geometryMatch1 & geometryMatch2;
        sink.beforeDataOperation();
        geometryUnchanged = geometryUnchanged & geometryMatch3;
        sink.beforeDataOperation();
        geometryUnchanged = geometryUnchanged & geometryMatch4;
        sink.beforeDataOperation();
        geometryUnchanged = geometryUnchanged & geometryMatch5;
        sink.beforeDataOperation();
        if (geometrySummary != geometryUnchanged) throw failure("BODY_GEOMETRY_SUMMARY");
        String effectsBefore = correlationString(
                geometryTemplate, "effectsBeforeSha256", sink, "BODY_EFFECTS_STRING");
        String effectsAfter = correlationString(
                geometryTemplate, "effectsAfterSha256", sink, "BODY_EFFECTS_STRING");
        boolean copyEffectsSummary = correlationBoolean(
                cleanup, "originalCaptureCopyEffectsUnchanged", sink, "BODY_EFFECTS_BOOLEAN");
        Object effectsScope = correlationRead(cleanup, "effectsScope", sink);
        requireFixedString(effectsScope, "ORIGINAL_CAPTURE_COPY_ONLY", sink, "BODY_EFFECTS_SCOPE");
        sink.beforeDataOperation();
        boolean copyEffectsUnchanged = effectsBefore.equals(effectsAfter);
        sink.beforeDataOperation();
        if (copyEffectsSummary != copyEffectsUnchanged) throw failure("BODY_EFFECTS_SUMMARY");
        Object cleanupSchema = correlationRead(cleanup, "schema", sink);
        requireFixedString(cleanupSchema, "tsj-cohort-terminal-cleanup-v1", sink,
                "BODY_CLEANUP_SCHEMA");
        Map<?, ?> cohort = correlationMap(correlationRead(body, "cohort", sink), sink);
        validateCohortProjection(cohort, prior, sink);
        validateArmProjections(arms, nullArm, observeArm, sink);
        validateCleanupProjection(cleanup, sink);
        validatePriorEnvelope(prior, sink);
    }

    private static void validateCohortProjection(Map<?, ?> cohort, Map<?, ?> prior,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        Iterator<? extends Map.Entry<?, ?>> entries = cohort.entrySet().iterator();
        for (int index = 0; index < 2; index++) {
            sink.beforeDataOperation();
            if (!entries.hasNext()) throw failure("BODY_COHORT_ORDER");
            sink.beforeDataOperation();
            Map.Entry<?, ?> entry = entries.next();
            sink.beforeDataOperation();
            Object key = entry.getKey();
            requireFixedString(key, index == 0 ? "netIds" : "requestedNetIds",
                    sink, "BODY_COHORT_ORDER");
        }
        sink.beforeDataOperation();
        if (entries.hasNext()) throw failure("BODY_COHORT_ORDER");
        List<?> pair = cohortList(correlationRead(cohort, "netIds", sink), 2, sink);
        List<?> requested = cohortList(
                correlationRead(cohort, "requestedNetIds", sink), 60, sink);
        Map<?, ?> disposition = correlationMap(
                correlationRead(prior, "trialDisposition", sink), sink);
        requireFixedString(correlationRead(disposition, "status", sink), "RUN",
                sink, "BODY_COHORT_DISPOSITION");
        requireFixedString(correlationRead(disposition, "reason", sink), "TRIAL_EXECUTED",
                sink, "BODY_COHORT_DISPOSITION");
        boolean orderChanged = correlationBoolean(disposition, "orderChanged", sink,
                "BODY_COHORT_DISPOSITION");
        sink.beforeDataOperation();
        if (!orderChanged) throw failure("BODY_COHORT_DISPOSITION");
        boolean feedbackValidated = correlationBoolean(disposition, "feedbackValidated", sink,
                "BODY_COHORT_DISPOSITION");
        sink.beforeDataOperation();
        if (!feedbackValidated) throw failure("BODY_COHORT_DISPOSITION");
        List<?> priorPair = cohortList(
                correlationRead(disposition, "cohortNetIds", sink), 2, sink);
        List<?> priorRequested = cohortList(
                correlationRead(disposition, "requestedNetIds", sink), 60, sink);
        Object captured = correlationRead(disposition, "capturedFailedNetId", sink);
        Object control = correlationRead(disposition, "controlFailedNetId", sink);
        for (int index = 0; index < 2; index++) {
            String id = cohortId(cohortRead(pair, index, sink), sink);
            Object priorId = cohortRead(priorPair, index, sink);
            sink.beforeDataOperation();
            if (!id.equals(priorId)) throw failure("BODY_COHORT_PAIR");
            sink.beforeDataOperation();
            if (!id.equals(index == 0 ? captured : control))
                throw failure("BODY_COHORT_PAIR");
            if (index == 1) {
                Object previous = cohortRead(pair, 0, sink);
                sink.beforeDataOperation();
                if (id.equals(previous)) throw failure("BODY_COHORT_DUPLICATE");
            }
        }
        for (int index = 0; index < 60; index++) {
            String id = cohortId(cohortRead(requested, index, sink), sink);
            Object priorId = cohortRead(priorRequested, index, sink);
            sink.beforeDataOperation();
            if (!id.equals(priorId)) throw failure("BODY_COHORT_REQUESTED");
            if (index < 2) {
                Object prefix = cohortRead(pair, index, sink);
                sink.beforeDataOperation();
                if (!id.equals(prefix)) throw failure("BODY_COHORT_PREFIX");
            }
            for (int previousIndex = 0; previousIndex < index; previousIndex++) {
                Object previous = cohortRead(requested, previousIndex, sink);
                sink.beforeDataOperation();
                if (id.equals(previous)) throw failure("BODY_COHORT_DUPLICATE");
            }
        }
    }

    private static List<?> cohortList(Object value, int length,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        if (!(value instanceof List)) throw failure("BODY_COHORT_LIST");
        List<?> list = (List<?>) value;
        sink.beforeDataOperation();
        if (list.size() != length) throw failure("BODY_COHORT_LIST");
        return list;
    }

    private static Object cohortRead(List<?> values, int index,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        return values.get(index);
    }

    private static String cohortId(Object value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        if (!(value instanceof String)) throw failure("BODY_COHORT_ID");
        String id = (String) value;
        sink.beforeDataOperation();
        int length = id.length();
        if (length == 0 || length > BODY_STRING_LIMIT) throw failure("BODY_COHORT_ID");
        return id;
    }
    private static void validateArmProjections(Map<?, ?> arms, Map<?, ?> nullArm,
            Map<?, ?> observeArm,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        validateArmProjectionKeys(arms, true, sink);
        validateArmProjectionKeys(nullArm, false, sink);
        validateArmProjectionKeys(observeArm, false, sink);
        correlationString(nullArm, "armId", sink, "BODY_ARM_PROJECTION_ID");
        correlationMap(correlationRead(nullArm, "freshRun", sink), sink);
        correlationMap(correlationRead(nullArm, "observation", sink), sink);
        correlationString(observeArm, "armId", sink, "BODY_ARM_PROJECTION_ID");
        correlationMap(correlationRead(observeArm, "freshRun", sink), sink);
        correlationMap(correlationRead(observeArm, "observation", sink), sink);
    }

    private static void validateArmProjectionKeys(Map<?, ?> values, boolean outer,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        int count = outer ? 2 : 5;
        sink.beforeDataOperation();
        Iterator<? extends Map.Entry<?, ?>> entries = values.entrySet().iterator();
        for (int index = 0; index < count; index++) {
            sink.beforeDataOperation();
            if (!entries.hasNext()) throw failure("BODY_ARM_PROJECTION_ORDER");
            sink.beforeDataOperation();
            Map.Entry<?, ?> entry = entries.next();
            sink.beforeDataOperation();
            Object key = entry.getKey();
            String expected = outer ? (index == 0 ? "null" : "observe") :
                    (index == 0 ? "armRole" : index == 1 ? "armId" :
                    index == 2 ? "freshRun" : index == 3 ? "observation" : "cleanup");
            requireFixedString(key, expected, sink, "BODY_ARM_PROJECTION_ORDER");
        }
        sink.beforeDataOperation();
        if (entries.hasNext()) throw failure("BODY_ARM_PROJECTION_ORDER");
    }
    private static void validateCleanupProjection(Map<?, ?> cleanup,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        Iterator<? extends Map.Entry<?, ?>> entries = cleanup.entrySet().iterator();
        for (int index = 0; index < 10; index++) {
            sink.beforeDataOperation();
            if (!entries.hasNext()) throw failure("BODY_CLEANUP_PROJECTION_ORDER");
            sink.beforeDataOperation();
            Map.Entry<?, ?> entry = entries.next();
            sink.beforeDataOperation();
            Object key = entry.getKey();
            String expected;
            switch (index) {
            case 0: expected = "schema"; break;
            case 1: expected = "originalCaptureOwnersReleased"; break;
            case 2: expected = "originalCaptureContextRestored"; break;
            case 3: expected = "nullArmAliasesDropped"; break;
            case 4: expected = "observeArmAliasesDropped"; break;
            case 5: expected = "sourceGeometryUnchanged"; break;
            case 6: expected = "effectsScope"; break;
            case 7: expected = "originalCaptureCopyEffectsUnchanged"; break;
            case 8: expected = "contextRestored"; break;
            default: expected = "cleanupNanos"; break;
            }
            requireFixedString(key, expected, sink, "BODY_CLEANUP_PROJECTION_ORDER");
        }
        sink.beforeDataOperation();
        if (entries.hasNext()) throw failure("BODY_CLEANUP_PROJECTION_ORDER");
    }
    private static void validatePriorEnvelope(Map<?, ?> prior,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        Iterator<? extends Map.Entry<?, ?>> entries = prior.entrySet().iterator();
        for (int index = 0; index < 12; index++) {
            sink.beforeDataOperation();
            if (!entries.hasNext()) throw failure("BODY_PRIOR_ENVELOPE_ORDER");
            sink.beforeDataOperation();
            Map.Entry<?, ?> entry = entries.next();
            sink.beforeDataOperation();
            Object key = entry.getKey();
            String expected;
            switch (index) {
            case 0: expected = "schema"; break;
            case 1: expected = "inputContextSha256"; break;
            case 2: expected = "sourceParent"; break;
            case 3: expected = "manifestSha256"; break;
            case 4: expected = "sourceManifestSha256"; break;
            case 5: expected = "readerSha256"; break;
            case 6: expected = "resultSha256"; break;
            case 7: expected = "operation1Sha256"; break;
            case 8: expected = "parentReceiptSha256"; break;
            case 9: expected = "priorRunId"; break;
            case 10: expected = "priorOriginalReceipt"; break;
            default: expected = "trialDisposition"; break;
            }
            requireFixedString(key, expected, sink, "BODY_PRIOR_ENVELOPE_ORDER");
            if (index == 0) {
                sink.beforeDataOperation();
                Object schema = entry.getValue();
                requireFixedString(schema, CurrentAssemblyMetadata.QUALIFIED_PRIOR_SCHEMA,
                        sink, "BODY_PRIOR_ENVELOPE_SCHEMA");
            }
        }
        sink.beforeDataOperation();
        if (entries.hasNext()) throw failure("BODY_PRIOR_ENVELOPE_ORDER");
    }
    private static String correlationString(Map<?, ?> values, String key,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink, String errorCode) throws Exception {
        Object value = correlationRead(values, key, sink);
        sink.beforeDataOperation();
        if (!(value instanceof String)) throw failure(errorCode);
        String text = (String) value;
        sink.beforeDataOperation();
        if (text.length() > BODY_STRING_LIMIT) throw failure(errorCode);
        return text;
    }

    private static Object correlationRead(Map<?, ?> values, String key,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        return values.get(key);
    }

    private static Map<?, ?> correlationMap(Object value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        if (!(value instanceof Map)) throw failure("BODY_CORRELATION_MAP");
        return (Map<?, ?>) value;
    }

    private static long correlationNanos(Object value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        if (!(value instanceof Integer) && !(value instanceof Long))
            throw failure("BODY_CLEANUP_NANOS");
        sink.beforeDataOperation();
        long nanos = ((Number) value).longValue();
        sink.beforeDataOperation();
        if (nanos < 0L || nanos > 120000000000L)
            throw failure("BODY_CLEANUP_NANOS");
        return nanos;
    }

    private static boolean correlationBoolean(Map<?, ?> values, String key,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink, String errorCode) throws Exception {
        Object value = correlationRead(values, key, sink);
        sink.beforeDataOperation();
        if (!(value instanceof Boolean)) throw failure(errorCode);
        sink.beforeDataOperation();
        return ((Boolean) value).booleanValue();
    }

    private CurrentV2CanonicalCodec() {
    }

    /**
     * Use one fused, key-inclusive domain traversal for count, write, and
     * read-compare sinks. The selected sink owns bytes and export fees.
     */
    static void writeBody(
            Map<String, Object> body,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        if (sink == null) throw failure("BODY_SINK");
        validateBodyHeader(body, sink);
        writeBodyValue(body, sink, new BodyWalk(), 0);
        sink.writeByte(10);
    }

    /**
     * Count the exact marker line without allocating serialized marker bytes.
     */
    static int countMarker(Marker marker) throws Exception {
        MarkerSink sink = new MarkerSink(null);
        validateMarker(marker, sink);
        emitMarker(marker, sink);
        sink.complete();
        return sink.position;
    }

    /**
     * Write once into the caller-preallocated exact-size target. There is no
     * count pass, marker Map/key array, encoded-body array, or digest copy.
     */
    static void writeMarker(Marker marker, byte[] exactTarget) throws Exception {
        if (exactTarget == null || exactTarget.length < 2 ||
                exactTarget.length > MARKER_MAX_BYTES ||
                exactTarget.length > MARKER_TRANSPORT_MAX_BYTES)
            throw failure("MARKER_TARGET");
        if (marker != null && marker.resultDigest == exactTarget)
            throw failure("MARKER_TARGET");

        MarkerSink sink = new MarkerSink(exactTarget);
        validateMarker(marker, sink);
        emitMarker(marker, sink);
        sink.complete();
    }

    private static void validateBodyHeader(
            Map<String, Object> body,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        if (body == null) throw failure("BODY_ROOT_TYPE");

        sink.beforeDataOperation();
        Iterator<? extends Map.Entry<?, ?>> entries =
                ((Map<?, ?>) body).entrySet().iterator();

        for (int index = 0; index < BODY_ROOT_KEYS.length; index++) {
            sink.beforeDataOperation();
            if (!entries.hasNext()) throw failure("BODY_ROOT_ORDER");
            sink.beforeDataOperation();
            Map.Entry<?, ?> entry = entries.next();
            sink.beforeDataOperation();
            Object rawKey = entry.getKey();
            if (!(rawKey instanceof String) ||
                    !BODY_ROOT_KEYS[index].equals(rawKey))
                throw failure("BODY_ROOT_ORDER");

            sink.beforeDataOperation();
            Object value = entry.getValue();
            switch (index) {
            case 0:
                requireFixedString(value, RESULT_SCHEMA, sink, "BODY_SCHEMA");
                break;
            case 1:
                requireFixedString(value, RESULT_MODE, sink, "BODY_MODE");
                break;
            case 2:
                requireFixedString(value, RESULT_STATUS, sink, "BODY_STATUS");
                break;
            case 3:
                requireUuid(value, sink, "BODY_RUN_ID");
                break;
            case 4:
                requireSha(value, sink, "BODY_SOURCE_ID");
                break;
            case 5:
                requireFixedString(value, WIRE_CONTRACT_SHA256, sink, "BODY_WIRE_ID");
                break;
            case 6:
                requireSha(value, sink, "BODY_CONTEXT_ID");
                break;
            default:
                if (!(value instanceof Map)) throw failure("BODY_ROOT_CONTAINER");
                break;
            }
        }
        sink.beforeDataOperation();
        if (entries.hasNext()) throw failure("BODY_ROOT_ORDER");
    }

    private static void writeBodyValue(
            Object value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink,
            BodyWalk walk,
            int depth) throws Exception {
        sink.beforeDataOperation();
        if (depth > BODY_MAX_DEPTH) throw failure("BODY_DEPTH");
        if (walk.visits >= BODY_MAX_VISITS) throw failure("BODY_VISITS");
        walk.visits++;

        if (value == null) {
            CohortTerminalTelemetryStudy.writeCanonicalLiteral(sink, "null");
            return;
        }
        if (value instanceof Boolean) {
            CohortTerminalTelemetryStudy.writeCanonicalLiteral(
                    sink, ((Boolean) value).booleanValue() ? "true" : "false");
            return;
        }
        if (value instanceof Integer || value instanceof Long) {
            CohortTerminalTelemetryStudy.writeCanonicalInteger(
                    ((Number) value).longValue(), sink);
            return;
        }
        if (value instanceof String) {
            writeBodyString((String) value, sink, "BODY_STRING");
            return;
        }
        if (value instanceof Map) {
            enterContainer(value, sink, walk, depth);
            sink.writeByte('{');
            sink.beforeDataOperation();
            Iterator<? extends Map.Entry<?, ?>> entries =
                    ((Map<?, ?>) value).entrySet().iterator();
            boolean first = true;
            while (true) {
                sink.beforeDataOperation();
                if (!entries.hasNext()) break;
                sink.beforeDataOperation();
                Map.Entry<?, ?> entry = entries.next();
                sink.beforeDataOperation();
                Object key = entry.getKey();
                if (walk.visits >= BODY_MAX_VISITS)
                    throw failure("BODY_VISITS");
                walk.visits++;
                if (!(key instanceof String)) throw failure("BODY_MAP_KEY");
                if (!first) sink.writeByte(',');
                first = false;
                writeBodyString((String) key, sink, "BODY_MAP_KEY");
                sink.writeByte(':');
                sink.beforeDataOperation();
                Object child = entry.getValue();
                writeBodyValue(child, sink, walk, depth + 1);
            }
            sink.writeByte('}');
            walk.active[depth] = null;
            return;
        }
        if (value instanceof List) {
            enterContainer(value, sink, walk, depth);
            sink.writeByte('[');
            sink.beforeDataOperation();
            Iterator<?> entries = ((List<?>) value).iterator();
            boolean first = true;
            while (true) {
                sink.beforeDataOperation();
                if (!entries.hasNext()) break;
                sink.beforeDataOperation();
                Object child = entries.next();
                if (!first) sink.writeByte(',');
                first = false;
                writeBodyValue(child, sink, walk, depth + 1);
            }
            sink.writeByte(']');
            walk.active[depth] = null;
            return;
        }
        throw failure("BODY_VALUE");
    }

    private static void enterContainer(
            Object value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink,
            BodyWalk walk,
            int depth) throws Exception {
        for (int index = 0; index < depth; index++) {
            sink.beforeDataOperation();
            if (walk.active[index] == value) throw failure("BODY_CYCLE");
        }
        walk.active[depth] = value;
    }

    private static void writeBodyString(
            String value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink,
            String code) throws Exception {
        sink.beforeDataOperation();
        if (value.length() > BODY_STRING_LIMIT) throw failure(code);
        sink.writeByte('"');
        for (int index = 0; index < value.length(); index++) {
            sink.beforeDataOperation();
            char c = value.charAt(index);
            if (c < 32 || c > 126) throw failure(code);
            if (c == '"' || c == '\\') sink.writeByte('\\');
            sink.writeByte(c);
        }
        sink.writeByte('"');
    }

    private static void requireFixedString(
            Object value,
            String expected,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink,
            String code) throws Exception {
        sink.beforeDataOperation();
        if (!(value instanceof String) || !expected.equals(value))
            throw failure(code);
    }

    private static void requireUuid(
            Object value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink,
            String code) throws Exception {
        sink.beforeDataOperation();
        if (!(value instanceof String)) throw failure(code);
        String text = (String) value;
        if (text.length() != 36) throw failure(code);
        for (int index = 0; index < 36; index++) {
            sink.beforeDataOperation();
            char c = text.charAt(index);
            if (index == 8 || index == 13 || index == 18 || index == 23) {
                if (c != '-') throw failure(code);
            } else if (!isLowerHex(c)) {
                throw failure(code);
            }
        }
    }

    private static void requireSha(
            Object value,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink,
            String code) throws Exception {
        sink.beforeDataOperation();
        if (!(value instanceof String)) throw failure(code);
        String text = (String) value;
        if (text.length() != 64) throw failure(code);
        for (int index = 0; index < 64; index++) {
            sink.beforeDataOperation();
            if (!isLowerHex(text.charAt(index))) throw failure(code);
        }
    }

    private static boolean isLowerHex(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f';
    }

    private static void validateMarker(
            Marker marker,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        sink.beforeDataOperation();
        if (marker == null) throw failure("MARKER_NULL");

        requireUuid(marker.runId, sink, "MARKER_RUN_ID");
        requireSha(marker.sourceManifestSha256, sink, "MARKER_SOURCE_ID");
        requireFixedString(marker.wireContractSha256, WIRE_CONTRACT_SHA256,
                sink, "MARKER_WIRE_ID");
        requireSha(marker.inputContextSha256, sink, "MARKER_CONTEXT_ID");

        sink.beforeDataOperation();
        if (marker.resultDigest == null || marker.resultDigest.length != 32)
            throw failure("MARKER_RESULT_DIGEST");

        sink.beforeDataOperation();
        if (marker.resultBytes < 1L || marker.resultBytes > 1048576L ||
                marker.elapsedNanos < 0L || marker.elapsedNanos > 90000000000L ||
                marker.finalExportNanos < 0L || marker.finalExportNanos > 5000000000L ||
                marker.totalGroups < 0L || marker.totalGroups > 640L ||
                marker.totalUnits < 0L ||
                marker.maxGroupNanos < 0L || marker.maxGroupNanos > 5000000000L ||
                marker.maximumUnitsPerGroup < 0L ||
                marker.maximumUnitsPerGroup > 32768L)
            throw failure("MARKER_NUMERIC_BOUND");
    }

    private static void emitMarker(
            Marker marker,
            MarkerSink sink) throws Exception {
        sink.writeByte('{');
        writeMarkerString(sink, "schema", MARKER_SCHEMA, true);
        writeMarkerString(sink, "status", MARKER_STATUS, false);
        writeMarkerString(sink, "runId", marker.runId, false);
        writeMarkerString(sink, "sourceManifestSha256",
                marker.sourceManifestSha256, false);
        writeMarkerString(sink, "wireContractSha256",
                marker.wireContractSha256, false);
        writeMarkerString(sink, "inputContextSha256",
                marker.inputContextSha256, false);
        writeMarkerKey(sink, "resultSha256", false);
        writeDigestHex(sink, marker.resultDigest);
        writeMarkerNumber(sink, "resultBytes", marker.resultBytes);
        writeMarkerNumber(sink, "elapsedNanos", marker.elapsedNanos);
        writeMarkerNumber(sink, "finalExportNanos", marker.finalExportNanos);
        writeMarkerNumber(sink, "totalGroups", marker.totalGroups);
        writeMarkerNumber(sink, "totalUnits", marker.totalUnits);
        writeMarkerNumber(sink, "maxGroupNanos", marker.maxGroupNanos);
        writeMarkerNumber(sink, "maximumUnitsPerGroup",
                marker.maximumUnitsPerGroup);
        sink.writeByte('}');
        sink.writeByte(10);
    }

    private static void writeMarkerString(
            MarkerSink sink, String key, String value, boolean first) throws Exception {
        writeMarkerKey(sink, key, first);
        CohortTerminalTelemetryStudy.writeCanonicalString(value, sink);
    }

    private static void writeMarkerNumber(
            MarkerSink sink, String key, long value) throws Exception {
        writeMarkerKey(sink, key, false);
        CohortTerminalTelemetryStudy.writeCanonicalInteger(value, sink);
    }

    private static void writeMarkerKey(
            MarkerSink sink, String key, boolean first) throws Exception {
        if (!first) sink.writeByte(',');
        CohortTerminalTelemetryStudy.writeCanonicalString(key, sink);
        sink.writeByte(':');
    }

    private static void writeDigestHex(
            MarkerSink sink, byte[] digest) throws Exception {
        sink.writeByte('"');
        for (int index = 0; index < 32; index++) {
            sink.beforeDataOperation();
            int value = digest[index] & 255;
            sink.writeByte(HEX.charAt(value >>> 4));
            sink.writeByte(HEX.charAt(value & 15));
        }
        sink.writeByte('"');
    }

    private static CodecFailure failure(String code) {
        return new CodecFailure(code);
    }
}
