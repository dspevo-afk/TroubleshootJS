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
