package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic-only canary for the bounded current cohort projection. */
public final class CohortProjectionCanary {
    private static final int FIRST_PAIR_GET_ADMISSION_HOOK = 195;
    private static int assertions;
    private static int cases;

    private CohortProjectionCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testValidListValuesAndBoundaries();
        testCohortMapOrder();
        testDispositionShape();
        testListShapes();
        testIdentifierTypesAndBounds();
        testPairAndRequestedRelationships();
        testCleanupSchemaPrecedence();
        testListAdmissionsAndEarliestDenial();

        System.out.println("SYNTHETIC_COHORT_PROJECTION_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testValidListValuesAndBoundaries() throws Exception {
        Map<String, Object> body = validBody();
        check(usesLinkedListsAndEqualDistinctIds(body),
                "LIST_INTERFACE_AND_CONTENT_EQUALITY_NOT_IDENTITY");
        accept(body, "LINKED_LIST_SYNTHETIC_SUFFIX_UNRELATED_TO_CAPTURE");

        body = boundaryBody();
        accept(body, "NONEMPTY_ID_LENGTHS_ONE_AND_256_ACCEPTED");
    }

    private static void testCohortMapOrder() throws Exception {
        Map<String, Object> body = validBody();
        cohort(body).remove("netIds");
        expectFailure("BODY_COHORT_ORDER", "MISSING_COHORT_KEY", body);

        body = validBody();
        cohort(body).put("extraSyntheticKey", "unused");
        expectFailure("BODY_COHORT_ORDER", "EXTRA_COHORT_KEY", body);

        body = validBody();
        Map<String, Object> original = cohort(body);
        Map<String, Object> reversed = new LinkedHashMap<String, Object>();
        reversed.put("requestedNetIds", original.get("requestedNetIds"));
        reversed.put("netIds", original.get("netIds"));
        body.put("cohort", reversed);
        expectFailure("BODY_COHORT_ORDER", "REVERSED_COHORT_KEYS", body);
    }

    private static void testDispositionShape() throws Exception {
        Map<String, Object> body = validBody();
        qualifiedPrior(body).remove("trialDisposition");
        expectFailure("BODY_CORRELATION_MAP", "DISPOSITION_MISSING", body);

        body = validBody();
        setDisposition(body, null);
        expectFailure("BODY_CORRELATION_MAP", "DISPOSITION_NULL", body);

        body = validBody();
        setDisposition(body, "not-a-map");
        expectFailure("BODY_CORRELATION_MAP", "DISPOSITION_WRONG_TYPE", body);

        body = validBody();
        setDispositionField(body, "status", "NOT_RUN");
        expectFailure("BODY_COHORT_DISPOSITION", "STATUS_NOT_RUN", body);

        body = validBody();
        setDispositionField(body, "reason", Integer.valueOf(7));
        expectFailure("BODY_COHORT_DISPOSITION", "REASON_WRONG_TYPE", body);

        body = validBody();
        setDispositionField(body, "orderChanged", Boolean.FALSE);
        expectFailure("BODY_COHORT_DISPOSITION", "ORDER_CHANGED_FALSE", body);

        body = validBody();
        setDispositionField(body, "feedbackValidated", Boolean.FALSE);
        expectFailure("BODY_COHORT_DISPOSITION", "FEEDBACK_VALIDATED_FALSE", body);

        body = validBody();
        setDispositionField(body, "orderChanged", Integer.valueOf(1));
        expectFailure("BODY_COHORT_DISPOSITION", "ORDER_CHANGED_WRONG_TYPE", body);

        body = validBody();
        setDispositionField(body, "feedbackValidated", null);
        expectFailure("BODY_COHORT_DISPOSITION", "FEEDBACK_VALIDATED_NULL", body);
    }

    private static void testListShapes() throws Exception {
        for (int field = 0; field < 4; field++) {
            for (int kind = 0; kind < 4; kind++) {
                Map<String, Object> body = validBody();
                if (field == 0) {
                    if (kind == 0) setListField(body, field, null);
                    else if (kind == 1) setListField(body, field, "not-a-list");
                    else resizeList(body, field, kind == 2 ? 1 : 3);
                } else if (field == 1) {
                    if (kind == 0) setListField(body, field, null);
                    else if (kind == 1) setListField(body, field, Integer.valueOf(3));
                    else resizeList(body, field, kind == 2 ? 59 : 61);
                } else if (field == 2) {
                    if (kind == 0) removeListField(body, field);
                    else if (kind == 1) setListField(body, field, null);
                    else if (kind == 2) setListField(body, field, Boolean.TRUE);
                    else resizeList(body, field, 1);
                } else {
                    if (kind == 0) removeListField(body, field);
                    else if (kind == 1) setListField(body, field, null);
                    else if (kind == 2) setListField(body, field, "not-a-list");
                    else resizeList(body, field, 61);
                }
                expectFailure("BODY_COHORT_LIST",
                        "LIST_FIELD_" + field + "_KIND_" + kind, body);
            }
        }
    }

    private static void testIdentifierTypesAndBounds() throws Exception {
        Object[] invalid = {
            null, Integer.valueOf(7), "", repeat('x', 257),
            null, Boolean.FALSE, "", repeat('x', 257)
        };
        int[] fields = {0, 0, 0, 0, 1, 1, 1, 1};
        int[] indices = {0, 1, 0, 1, 2, 3, 4, 5};
        for (int test = 0; test < invalid.length; test++) {
            Map<String, Object> body = validBody();
            setListItem(body, fields[test], indices[test], invalid[test]);
            expectFailure("BODY_COHORT_ID", "ID_KIND_" + test, body);
        }
    }

    private static void testPairAndRequestedRelationships() throws Exception {
        Map<String, Object> body = validBody();
        setListItem(body, 2, 0, syntheticId("different-prior-pair"));
        expectFailure("BODY_COHORT_PAIR", "PAIR_PRIOR_CONTENT_MISMATCH", body);

        body = validBody();
        setDispositionField(body, "capturedFailedNetId",
                syntheticId("different-captured-id"));
        expectFailure("BODY_COHORT_PAIR", "CAPTURED_ID_CONTENT_MISMATCH", body);

        body = validBody();
        setDispositionField(body, "controlFailedNetId", Integer.valueOf(7));
        expectFailure("BODY_COHORT_PAIR", "CONTROL_ID_RAW_WRONG_TYPE", body);

        body = validBody();
        setListItem(body, 2, 0, Integer.valueOf(7));
        expectFailure("BODY_COHORT_PAIR", "PRIOR_PAIR_MEMBER_WRONG_TYPE", body);

        body = validBody();
        setListItem(body, 3, 2, null);
        expectFailure("BODY_COHORT_REQUESTED", "PRIOR_REQUESTED_MEMBER_NULL", body);

        body = validBody();
        makePairDuplicate(body);
        expectFailure("BODY_COHORT_DUPLICATE", "PAIR_DUPLICATE", body);

        body = validBody();
        setListItem(body, 1, 10, syntheticId("changed-requested-item"));
        expectFailure("BODY_COHORT_REQUESTED", "REQUESTED_PRIOR_MISMATCH", body);

        body = validBody();
        String changedPrefix = syntheticId("changed-prefix-item");
        setListItem(body, 1, 0, new String(changedPrefix));
        setListItem(body, 3, 0, new String(changedPrefix));
        expectFailure("BODY_COHORT_PREFIX",
                "PREFIX_MISMATCH_AFTER_PRIOR_REQUEST_UPDATE", body);

        body = validBody();
        makeRequestedDuplicate(body, 2, 1);
        expectFailure("BODY_COHORT_DUPLICATE",
                "REQUESTED_DUPLICATE_AT_FIRST_SUFFIX", body);

        body = validBody();
        makeRequestedDuplicate(body, 59, 17);
        expectFailure("BODY_COHORT_DUPLICATE",
                "REQUESTED_DUPLICATE_AT_LAST_POSITION", body);
    }

    private static void testCleanupSchemaPrecedence() throws Exception {
        Map<String, Object> body = validBody();
        child(body, "cleanup").put("schema", Integer.valueOf(7));
        cohort(body).remove("netIds");
        expectFailure("BODY_CLEANUP_SCHEMA",
                "CLEANUP_SCHEMA_PRECEDES_COHORT_ORDER", body);
    }

    private static void testListAdmissionsAndEarliestDenial() throws Exception {
        Map<String, Object> body = validBody();
        PermitState state = new PermitState();
        Map<String, Object> guarded = guardedTree(body, state);
        TreeSnapshot before = new TreeSnapshot(guarded);
        PermitSink sink = new PermitSink(state);
        validator().invoke(null, guarded, sink);
        check(sink.hooks == 4162, "TOTAL_CORRELATION_HOOKS_4162");
        check(state.mapGets == 65, "TOTAL_CORRELATION_MAP_GETS_65");
        check(state.listGets == 1897, "TOTAL_COHORT_LIST_GETS_1897");
        check(state.rejectedGets == 0, "EVERY_GUARDED_GET_HAS_FRESH_ADMISSION");
        check(before.unchanged(guarded), "GUARDED_VALID_TREE_UNCHANGED");
        cases++;

        body = validBody();
        Map<String, Object> c = cohort(body);
        ThrowingList firstPair = new ThrowingList(copyList(body, 0));
        c.put("netIds", firstPair);
        TreeSnapshot deniedBefore = new TreeSnapshot(body);
        DenyAtHookSink deniedSink = new DenyAtHookSink(
                FIRST_PAIR_GET_ADMISSION_HOOK);
        Throwable cause = null;
        try {
            validator().invoke(null, body, deniedSink);
        } catch (InvocationTargetException rejected) {
            cause = rejected.getCause();
        }
        check(cause instanceof AdmissionDenied &&
                deniedSink.hooks == FIRST_PAIR_GET_ADMISSION_HOOK,
                "SINK_DENIES_AT_FIRST_PAIR_GET_ADMISSION");
        check(firstPair.getCalls == 0, "DENIAL_PRECEDES_THROWING_LIST_GET");
        check(deniedSink.writes == 0, "DENIED_PRECHECK_WRITES_NO_BYTES");
        check(deniedBefore.unchanged(body), "DENIED_LIST_READ_TREE_UNCHANGED");
        cases++;

        body = validBody();
        c = cohort(body);
        ThrowingList admittedPair = new ThrowingList(copyList(body, 0));
        c.put("netIds", admittedPair);
        TreeSnapshot throwingBefore = new TreeSnapshot(body);
        DenyAtHookSink throwingSink = new DenyAtHookSink(-1);
        cause = null;
        try {
            validator().invoke(null, body, throwingSink);
        } catch (InvocationTargetException propagated) {
            cause = propagated.getCause();
        }
        check(cause != null && cause.getClass() == ListGetAttempted.class,
                "ADMITTED_LIST_GET_EXCEPTION_PROPAGATES_EXACTLY");
        check(admittedPair.getCalls == 1, "ADMITTED_THROWING_LIST_GET_REACHED");
        check(throwingSink.hooks == FIRST_PAIR_GET_ADMISSION_HOOK,
                "THROWING_GET_HAS_PRECEDING_ADMISSION");
        check(throwingSink.writes == 0, "THROWING_VALIDATION_WRITES_NO_BYTES");
        check(throwingBefore.unchanged(body), "THROWING_LIST_TREE_UNCHANGED");
        cases++;
    }

    private static void accept(Map<String, Object> body, String label)
            throws Exception {
        TreeSnapshot before = new TreeSnapshot(body);
        MemorySink lower = new MemorySink();
        MemorySink correlated = new MemorySink();
        CurrentV2CanonicalCodec.writeBody(body, lower);
        CurrentV2CanonicalCodec.writeCorrelatedBody(body, correlated);
        check(Arrays.equals(lower.bytes(), correlated.bytes()),
                label + "_MATCHES_LOWER_WRITE_BODY");
        check(before.unchanged(body), label + "_TREE_UNCHANGED");
        cases++;
    }

    private static void expectFailure(String expectedCode, String label,
            Map<String, Object> body) throws Exception {
        TreeSnapshot before = new TreeSnapshot(body);
        MemorySink sink = new MemorySink();
        CurrentV2CanonicalCodec.CodecFailure failure = null;
        try {
            CurrentV2CanonicalCodec.writeCorrelatedBody(body, sink);
        } catch (CurrentV2CanonicalCodec.CodecFailure rejected) {
            failure = rejected;
        }
        check(failure != null && expectedCode.equals(failure.code),
                label + "_EXACT_FAILURE");
        check(sink.bytes().length == 0, label + "_ZERO_ENCODED_BYTES");
        check(before.unchanged(body), label + "_TREE_UNCHANGED");
        cases++;
    }

    private static Map<String, Object> validBody() {
        Map<String, Object> body = BodyCorrelationOracle.body();
        Map<String, Object> c = cohort(body);
        Map<String, Object> d = trialDisposition(body);
        List<Object> pair = new LinkedList<Object>();
        List<Object> requested = new LinkedList<Object>();
        List<Object> priorPair = new LinkedList<Object>();
        List<Object> priorRequested = new LinkedList<Object>();
        for (int index = 0; index < 60; index++) {
            String value = syntheticId("item-" + index);
            requested.add(new String(value));
            priorRequested.add(new String(value));
            if (index < 2) {
                pair.add(new String(value));
                priorPair.add(new String(value));
            }
        }
        c.put("netIds", pair);
        c.put("requestedNetIds", requested);
        d.put("status", "RUN");
        d.put("reason", "TRIAL_EXECUTED");
        d.put("orderChanged", Boolean.TRUE);
        d.put("feedbackValidated", Boolean.TRUE);
        d.put("capturedFailedNetId", new String(syntheticId("item-0")));
        d.put("controlFailedNetId", new String(syntheticId("item-1")));
        d.put("cohortNetIds", priorPair);
        d.put("requestedNetIds", priorRequested);
        return body;
    }

    private static Map<String, Object> boundaryBody() {
        Map<String, Object> body = validBody();
        String first = "x";
        String second = repeat('y', 256);
        String suffix = repeat('z', 256);
        setListItem(body, 0, 0, new String(first));
        setListItem(body, 0, 1, new String(second));
        setListItem(body, 2, 0, new String(first));
        setListItem(body, 2, 1, new String(second));
        setListItem(body, 1, 0, new String(first));
        setListItem(body, 1, 1, new String(second));
        setListItem(body, 3, 0, new String(first));
        setListItem(body, 3, 1, new String(second));
        setListItem(body, 1, 2, new String(suffix));
        setListItem(body, 3, 2, new String(suffix));
        setDispositionField(body, "capturedFailedNetId", new String(first));
        setDispositionField(body, "controlFailedNetId", new String(second));
        return body;
    }

    private static boolean usesLinkedListsAndEqualDistinctIds(
            Map<String, Object> body) {
        List<?> pair = (List<?>) listValue(body, 0);
        List<?> requested = (List<?>) listValue(body, 1);
        List<?> priorPair = (List<?>) listValue(body, 2);
        List<?> priorRequested = (List<?>) listValue(body, 3);
        Object pairFirst = pair.get(0);
        Object requestFirst = requested.get(0);
        Object priorFirst = priorPair.get(0);
        Object priorRequestedFirst = priorRequested.get(0);
        Object captured = trialDisposition(body).get("capturedFailedNetId");
        return pair instanceof LinkedList && requested instanceof LinkedList &&
                priorPair instanceof LinkedList && priorRequested instanceof LinkedList &&
                pairFirst instanceof String && pairFirst != requestFirst &&
                pairFirst != priorFirst && pairFirst != captured &&
                pairFirst.equals(requestFirst) && pairFirst.equals(priorFirst) &&
                pairFirst.equals(priorRequestedFirst) && pairFirst.equals(captured);
    }

    private static void makePairDuplicate(Map<String, Object> body) {
        String first = (String) ((List<?>) listValue(body, 0)).get(0);
        setListItem(body, 0, 1, new String(first));
        setListItem(body, 2, 1, new String(first));
        setDispositionField(body, "controlFailedNetId", new String(first));
        setListItem(body, 1, 1, new String(first));
        setListItem(body, 3, 1, new String(first));
    }

    private static void makeRequestedDuplicate(Map<String, Object> body,
            int target, int previous) {
        String value = (String) ((List<?>) listValue(body, 1)).get(previous);
        setListItem(body, 1, target, new String(value));
        setListItem(body, 3, target, new String(value));
    }

    private static void resizeList(Map<String, Object> body, int field, int length) {
        List<Object> values = copyList(body, field);
        while (values.size() > length) values.remove(values.size() - 1);
        while (values.size() < length)
            values.add(syntheticId("resize-" + field + "-" + values.size()));
        setListField(body, field, values);
    }

    private static List<Object> copyList(Map<String, Object> body, int field) {
        List<Object> copy = new ArrayList<Object>();
        copy.addAll((List<?>) listValue(body, field));
        return copy;
    }

    private static void setListItem(Map<String, Object> body, int field,
            int index, Object value) {
        List<Object> values = copyList(body, field);
        values.set(index, value);
        setListField(body, field, values);
    }

    private static Object listValue(Map<String, Object> body, int field) {
        return listOwner(body, field).get(listKey(field));
    }

    private static void setListField(Map<String, Object> body, int field,
            Object value) {
        listOwner(body, field).put(listKey(field), value);
    }

    private static void removeListField(Map<String, Object> body, int field) {
        listOwner(body, field).remove(listKey(field));
    }

    private static Map<String, Object> listOwner(Map<String, Object> body,
            int field) {
        return field < 2 ? cohort(body) : trialDisposition(body);
    }

    private static String listKey(int field) {
        if (field == 0) return "netIds";
        if (field == 1 || field == 3) return "requestedNetIds";
        return "cohortNetIds";
    }

    private static void setDisposition(Map<String, Object> body, Object value) {
        qualifiedPrior(body).put("trialDisposition", value);
    }

    private static void setDispositionField(Map<String, Object> body,
            String key, Object value) {
        trialDisposition(body).put(key, value);
    }

    private static Map<String, Object> cohort(Map<String, Object> body) {
        return child(body, "cohort");
    }

    private static Map<String, Object> qualifiedPrior(Map<String, Object> body) {
        return child(body, "qualifiedPrior");
    }

    private static Map<String, Object> trialDisposition(Map<String, Object> body) {
        return child(qualifiedPrior(body), "trialDisposition");
    }

    private static Method validator() throws Exception {
        Method method = CurrentV2CanonicalCodec.class.getDeclaredMethod(
                "validateCorrelations", Map.class,
                CohortTerminalTelemetryStudy.CanonicalJsonSink.class);
        method.setAccessible(true);
        return method;
    }

    private static String syntheticId(String suffix) {
        return "synthetic-cohort-" + suffix;
    }

    private static String repeat(char value, int length) {
        char[] chars = new char[length];
        for (int index = 0; index < chars.length; index++) chars[index] = value;
        return new String(chars);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> body, String key) {
        return (Map<String, Object>) body.get(key);
    }

    private static void check(boolean condition, String code) {
        assertions++;
        if (!condition) throw new AssertionError(code);
    }

    private static Object guardedValue(Object value, PermitState state) {
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
                copy.put((String) entry.getKey(), guardedValue(entry.getValue(), state));
            return new GuardedMap(copy, state);
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<Object>();
            for (Object child : (List<?>) value) copy.add(guardedValue(child, state));
            return new GuardedList(copy, state);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> guardedTree(Map<String, Object> body,
            PermitState state) {
        return (Map<String, Object>) guardedValue(body, state);
    }

    private static final class PermitState {
        int serial;
        int consumedSerial;
        int mapGets;
        int listGets;
        int rejectedGets;
        boolean permitOutstanding;

        void grant() {
            serial++;
            permitOutstanding = true;
        }

        void consume(boolean list) {
            if (!permitOutstanding || serial <= consumedSerial) {
                rejectedGets++;
                throw new IllegalStateException("GET_WITHOUT_FRESH_SINK_ADMISSION");
            }
            consumedSerial = serial;
            if (list) listGets++;
            else mapGets++;
            permitOutstanding = false;
        }

        void revoke() { permitOutstanding = false; }
    }

    private static final class GuardedMap extends AbstractMap<String, Object> {
        private final Map<String, Object> delegate;
        private final PermitState state;

        GuardedMap(Map<String, Object> delegate, PermitState state) {
            this.delegate = delegate;
            this.state = state;
        }

        public Object get(Object key) {
            state.consume(false);
            return delegate.get(key);
        }

        public Set<Map.Entry<String, Object>> entrySet() {
            final Set<Map.Entry<String, Object>> entries = delegate.entrySet();
            return new AbstractSet<Map.Entry<String, Object>>() {
                public int size() {
                    state.revoke();
                    return entries.size();
                }

                public Iterator<Map.Entry<String, Object>> iterator() {
                    state.revoke();
                    final Iterator<Map.Entry<String, Object>> iterator = entries.iterator();
                    return new Iterator<Map.Entry<String, Object>>() {
                        public boolean hasNext() {
                            state.revoke();
                            return iterator.hasNext();
                        }

                        public Map.Entry<String, Object> next() {
                            state.revoke();
                            return iterator.next();
                        }

                        public void remove() {
                            state.revoke();
                            throw new UnsupportedOperationException();
                        }
                    };
                }
            };
        }
    }

    private static final class GuardedList extends AbstractList<Object> {
        private final List<Object> delegate;
        private final PermitState state;

        GuardedList(List<Object> delegate, PermitState state) {
            this.delegate = delegate;
            this.state = state;
        }

        public Object get(int index) {
            state.consume(true);
            return delegate.get(index);
        }

        public int size() {
            int size = delegate.size();
            state.revoke();
            return size;
        }

        public Iterator<Object> iterator() {
            state.revoke();
            return delegate.iterator();
        }
    }

    private static final class PermitSink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        final PermitState state;
        int hooks;

        PermitSink(PermitState state) { this.state = state; }

        public void beforeDataOperation() {
            hooks++;
            state.grant();
        }

        public void writeByte(int ascii) throws Exception {
            state.revoke();
            throw new IOException("UNEXPECTED_VALIDATION_OUTPUT");
        }
    }

    private static final class DenyAtHookSink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        final int denyAt;
        int hooks;
        int writes;

        DenyAtHookSink(int denyAt) { this.denyAt = denyAt; }

        public void beforeDataOperation() {
            hooks++;
            if (hooks == denyAt) throw new AdmissionDenied();
        }

        public void writeByte(int ascii) throws Exception {
            writes++;
            throw new IOException("UNEXPECTED_DENIAL_OUTPUT");
        }
    }

    private static final class AdmissionDenied extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class ListGetAttempted extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class ThrowingList extends AbstractList<Object> {
        private final List<Object> delegate;
        int getCalls;

        ThrowingList(List<Object> delegate) { this.delegate = delegate; }

        public Object get(int index) {
            getCalls++;
            throw new ListGetAttempted();
        }

        public int size() { return delegate.size(); }

        public Iterator<Object> iterator() { return delegate.iterator(); }
    }

    private static final class MemorySink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        public void beforeDataOperation() { }

        public void writeByte(int ascii) throws Exception {
            if (ascii < 0 || ascii > 127) throw new IOException("SINK_ASCII");
            output.write(ascii);
        }

        byte[] bytes() { return output.toByteArray(); }
    }

    private static final class TreeSnapshot {
        private final IdentityHashMap<Object, List<Object[]>> contents =
                new IdentityHashMap<Object, List<Object[]>>();

        TreeSnapshot(Object root) { capture(root); }

        private void capture(Object value) {
            if (value instanceof Map) {
                if (contents.containsKey(value)) return;
                List<Object[]> entries = new ArrayList<Object[]>();
                contents.put(value, entries);
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    Object key = entry.getKey();
                    Object child = entry.getValue();
                    entries.add(new Object[]{key, child});
                    capture(child);
                }
            } else if (value instanceof List) {
                if (contents.containsKey(value)) return;
                List<Object[]> entries = new ArrayList<Object[]>();
                contents.put(value, entries);
                for (Object child : (List<?>) value) {
                    entries.add(new Object[]{child});
                    capture(child);
                }
            }
        }

        boolean unchanged(Object root) { return matches(root); }

        private boolean matches(Object value) {
            List<Object[]> expected = contents.get(value);
            if (expected == null) return true;
            if (value instanceof Map) {
                Iterator<? extends Map.Entry<?, ?>> actual =
                        ((Map<?, ?>) value).entrySet().iterator();
                if (((Map<?, ?>) value).size() != expected.size()) return false;
                for (int index = 0; index < expected.size(); index++) {
                    if (!actual.hasNext()) return false;
                    Map.Entry<?, ?> entry = actual.next();
                    Object[] pair = expected.get(index);
                    if (entry.getKey() != pair[0] || entry.getValue() != pair[1] ||
                            !matches(pair[1])) return false;
                }
                return !actual.hasNext();
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (list.size() != expected.size()) return false;
                Iterator<?> actual = list.iterator();
                for (int index = 0; index < expected.size(); index++) {
                    if (!actual.hasNext()) return false;
                    Object child = expected.get(index)[0];
                    if (actual.next() != child || !matches(child)) return false;
                }
                return !actual.hasNext();
            }
            return false;
        }
    }
}
