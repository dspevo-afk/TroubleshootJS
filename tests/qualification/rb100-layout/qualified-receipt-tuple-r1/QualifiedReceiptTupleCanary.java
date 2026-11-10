package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic-only canary for CurrentAssemblyMetadata's receipt tuple helper. */
public final class QualifiedReceiptTupleCanary {
    private static int cases;
    private static int assertions;

    private QualifiedReceiptTupleCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        String sha = validSha();
        happyCases(sha);
        failureCases(sha);
        propagatedOuterGets(sha);
        admittedOuterReads(sha);
        deniedAdmissionBeforeRead(sha);
        nullMeter(sha);
        System.out.println("SYNTHETIC_QUALIFIED_RECEIPT_TUPLE_COMPONENT_ONLY cases=" + cases +
                " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void happyCases(String sha) throws Exception {
        accept("ONE_BYTE", 1, sha);
        accept("MAX_BYTES", 8388608, sha);
        accept("ALL_LOWERCASE_HEX_CATEGORIES", 64, sha);
    }

    private static void accept(String label, int bytes, String sha) throws Exception {
        LinkedHashMap<String, Object> receipt = receipt("op-1.jsonl",
                Integer.valueOf(bytes), sha);
        LinkedHashMap<String, Object> prior = prior(receipt, sha);
        MapSnapshot before = new MapSnapshot(prior);
        MeterFixture fixture = new MeterFixture();
        try {
            fixture.setup.charge(1L);
            long units = number(fixture.setup.meter, "units");
            long copies = number(fixture.setup.meter, "copiedEntries");
            long chunks = number(fixture.setup.meter, "copyChunks");
            long groups = number(fixture.setup.meter, "groups");
            CurrentAssemblyMetadata.validateReceiptTuple(
                    fixture.setup, prior, (long) bytes, sha);
            ledger(fixture, label, units, copies, chunks, groups, 81L);
            check(before.unchanged(), label + "_TREE_KEYS_REFS_SCALARS_UNCHANGED");
            cases++;
        } finally {
            fixture.setup.finish();
        }
    }

    private static void failureCases(String sha) throws Exception {
        reject("NULL_PRIOR", null, 1L, sha, "PRIOR", 1);
        LinkedHashMap<String, Object> missingReceipt = prior(
                receipt("op-1.jsonl", 1, sha), sha);
        missingReceipt.remove("priorOriginalReceipt");
        reject("MAP_MISSING", missingReceipt, 1L, sha, "MAP", 3);
        reject("MAP_NULL", prior(null, sha), 1L, sha, "MAP", 3);
        reject("MAP_WRONG_TYPE", prior("not-a-map", sha), 1L, sha, "MAP", 3);
        reject("MAP_WRONG_MAP_CLASS", prior(new HashMap<String, Object>(), sha), 1L, sha, "MAP", 3);
        reject("MAP_LINKEDHASHMAP_SUBCLASS", prior(new ReceiptMapSubclass(), sha), 1L, sha, "MAP", 3);

        reject("PATH_MISMATCH", prior(receipt("other.jsonl", 1, sha), sha), 1L, sha, "PATH", 5);
        reject("PATH_WRONG_TYPE", prior(receipt(Integer.valueOf(1), 1, sha), sha), 1L, sha, "PATH", 5);
        reject("PATH_NULL", prior(receipt(null, 1, sha), sha), 1L, sha, "PATH", 5);
        LinkedHashMap<String, Object> missingPath = receipt("op-1.jsonl", 1, sha);
        missingPath.remove("receiptPath");
        reject("PATH_MISSING", prior(missingPath, sha), 1L, sha, "PATH", 5);

        Object[] wrongCounts = { Long.valueOf(1L), Boolean.TRUE, Double.valueOf(1.0), null };
        String[] countLabels = { "BYTES_LONG", "BYTES_BOOLEAN", "BYTES_DOUBLE", "BYTES_NULL" };
        for (int i = 0; i < wrongCounts.length; i++)
            reject(countLabels[i], prior(receipt("op-1.jsonl", wrongCounts[i], sha), sha),
                    1L, sha, "BYTES", 7);
        LinkedHashMap<String, Object> missingBytes = receipt("op-1.jsonl", 1, sha);
        missingBytes.remove("receiptBytes");
        reject("BYTES_MISSING", prior(missingBytes, sha), 1L, sha, "BYTES", 7);

        int[] badSizes = { 0, -1, 8388609 };
        String[] sizeLabels = { "BYTES_ZERO", "BYTES_NEGATIVE", "BYTES_ABOVE_MAX" };
        for (int i = 0; i < badSizes.length; i++)
            reject(sizeLabels[i], prior(receipt("op-1.jsonl", badSizes[i], sha), sha),
                    (long) badSizes[i], sha, "BYTES", 9);
        int[] storedCounts = { 2, 1, 1 };
        long[] expectedCounts = { 1L, Long.MAX_VALUE, 4294967297L };
        String[] expectedLabels = { "EXPECTED_BYTES_MISMATCH", "EXPECTED_BYTES_LONG_MAX",
                "EXPECTED_BYTES_INT_ALIAS_OVERFLOW" };
        for (int i = 0; i < expectedCounts.length; i++)
            reject(expectedLabels[i], prior(receipt("op-1.jsonl", storedCounts[i], sha), sha),
                    expectedCounts[i], sha, "BYTES", 10);

        Object[] wrongShas = { Integer.valueOf(7), Boolean.TRUE, new StringBuilder(sha), null };
        String[] shaTypeLabels = { "SHA_INTEGER", "SHA_BOOLEAN", "SHA_STRING_BUILDER", "SHA_NULL" };
        for (int i = 0; i < wrongShas.length; i++)
            reject(shaTypeLabels[i], prior(receipt("op-1.jsonl", 1, wrongShas[i]), sha),
                    1L, sha, "SHA", 12);
        LinkedHashMap<String, Object> missingSha = receipt("op-1.jsonl", 1, sha);
        missingSha.remove("receiptSha256");
        reject("SHA_MISSING", prior(missingSha, sha), 1L, sha, "SHA", 12);
        for (int size : new int[] { 63, 65 })
            reject("SHA_LENGTH_" + size, prior(receipt("op-1.jsonl", 1, filled('0', size)), sha),
                    1L, sha, "SHA", 14);

        for (int i = 0; i < 64; i++)
            reject("SHA_INVALID_INDEX_" + i,
                    prior(receipt("op-1.jsonl", 1, changed(sha, i, 'g')), sha),
                    1L, sha, "SHA", 15 + i);
        char[] bad = { 'A', 'G', '/', ':', '@', (char) 0, (char) 127 };
        String[] labels = { "UPPER_A", "UPPER_G", "SLASH_BOUNDARY", "COLON_BOUNDARY",
                "AT_BOUNDARY", "NUL", "DEL" };
        for (int i = 0; i < bad.length; i++)
            reject("SHA_" + labels[i], prior(receipt("op-1.jsonl", 1,
                    changed(sha, 0, bad[i])), sha), 1L, sha, "SHA", 15);

        reject("EXPECTED_SHA_MISMATCH", prior(receipt("op-1.jsonl", 1, sha), sha), 1L,
                changed(sha, 0, 'f'), "SHA", 79);
        reject("EXPECTED_SHA_NULL", prior(receipt("op-1.jsonl", 1, sha), sha), 1L, null, "SHA", 79);
        reject("OPERATION_WRONG_TYPE", prior(receipt("op-1.jsonl", 1, sha), Integer.valueOf(1)),
                1L, sha, "OPERATION", 81);
        reject("OPERATION_NULL", prior(receipt("op-1.jsonl", 1, sha), null), 1L, sha, "OPERATION", 81);
        reject("OPERATION_MISMATCH", prior(receipt("op-1.jsonl", 1, sha), changed(sha, 0, 'f')),
                1L, sha, "OPERATION", 81);
        LinkedHashMap<String, Object> missingOperation = prior(
                receipt("op-1.jsonl", 1, sha), sha);
        missingOperation.remove("operation1Sha256");
        reject("OPERATION_MISSING", missingOperation, 1L, sha, "OPERATION", 81);
    }

    private static void reject(String label, Map<?, ?> prior, long expectedBytes,
            String expectedSha, String suffix, long debit) throws Exception {
        MapSnapshot before = new MapSnapshot(prior);
        MeterFixture fixture = new MeterFixture();
        try {
            fixture.setup.charge(1L);
            long units = number(fixture.setup.meter, "units");
            long copies = number(fixture.setup.meter, "copiedEntries");
            long chunks = number(fixture.setup.meter, "copyChunks");
            long groups = number(fixture.setup.meter, "groups");
            IOException failure = null;
            try {
                CurrentAssemblyMetadata.validateReceiptTuple(
                        fixture.setup, prior, expectedBytes, expectedSha);
            } catch (IOException expected) {
                failure = expected;
            }
            check(failure != null && ("METADATA_RECEIPT_" + suffix).equals(
                    failure.getMessage()), label + "_EXACT_FAILURE");
            ledger(fixture, label, units, copies, chunks, groups, debit);
            check(before.unchanged(), label + "_TREE_KEYS_REFS_SCALARS_UNCHANGED");
            cases++;
        } finally {
            fixture.setup.finish();
        }
    }

    private static void propagatedOuterGets(String sha) throws Exception {
        propagatedOuterGet("FIRST_OUTER_GET", 1, 2L, sha);
        propagatedOuterGet("SECOND_OUTER_GET", 2, 80L, sha);
    }

    private static void propagatedOuterGet(String label, int failOnGet, long debit,
            String sha) throws Exception {
        LinkedHashMap<String, Object> backing = prior(receipt("op-1.jsonl", 1, sha), sha);
        MapSnapshot before = new MapSnapshot(backing);
        IllegalStateException sentinel = new IllegalStateException("OUTER_GET_SENTINEL");
        ThrowingOuterMap guarded = new ThrowingOuterMap(backing, failOnGet, sentinel);
        MeterFixture fixture = new MeterFixture();
        try {
            fixture.setup.charge(1L);
            long units = number(fixture.setup.meter, "units");
            long copies = number(fixture.setup.meter, "copiedEntries");
            long chunks = number(fixture.setup.meter, "copyChunks");
            long groups = number(fixture.setup.meter, "groups");
            IllegalStateException observed = null;
            try {
                CurrentAssemblyMetadata.validateReceiptTuple(
                        fixture.setup, guarded, 1L, sha);
            } catch (IllegalStateException expected) {
                observed = expected;
            }
            check(observed == sentinel, label + "_EXCEPTION_PROPAGATED");
            check(guarded.gets == failOnGet, label + "_ONLY_EXPECTED_GETS");
            ledger(fixture, label, units, copies, chunks, groups, debit);
            check(before.unchanged(), label + "_TREE_UNCHANGED");
            cases++;
        } finally {
            fixture.setup.finish();
        }
    }

    private static void admittedOuterReads(String sha) throws Exception {
        LinkedHashMap<String, Object> backing = prior(receipt("op-1.jsonl", 1, sha), sha);
        MapSnapshot before = new MapSnapshot(backing);
        MeterFixture fixture = new MeterFixture();
        try {
            fixture.setup.charge(1L);
            long units = number(fixture.setup.meter, "units");
            long copies = number(fixture.setup.meter, "copiedEntries");
            long chunks = number(fixture.setup.meter, "copyChunks");
            long groups = number(fixture.setup.meter, "groups");
            OuterReadGuardMap guarded = new OuterReadGuardMap(
                    backing, fixture.setup.meter, units);
            CurrentAssemblyMetadata.validateReceiptTuple(
                    fixture.setup, guarded, 1L, sha);
            check(guarded.gets == 2, "OUTER_READS_ONLY_AT_UNITS_2_AND_80");
            ledger(fixture, "ADMITTED_OUTER_READS", units, copies, chunks, groups, 81L);
            check(before.unchanged(), "ADMITTED_OUTER_READ_TREE_UNCHANGED");
            cases++;
        } finally {
            fixture.setup.finish();
        }
    }

    private static void deniedAdmissionBeforeRead(String sha) throws Exception {
        LinkedHashMap<String, Object> backing = prior(receipt("op-1.jsonl", 1, sha), sha);
        MapSnapshot before = new MapSnapshot(backing);
        MeterFixture fixture = new MeterFixture();
        try {
            for (int i = 0; i < 640; i++) {
                fixture.setup.charge(1L);
                fixture.setup.finish();
            }
            check(number(fixture.setup.meter, "units") == 640L, "DENY_PREFILL_UNITS");
            check(number(fixture.setup.meter, "groups") == 640L, "DENY_PREFILL_GROUPS");
            check(number(fixture.setup.meter, "copiedEntries") == 0L &&
                    number(fixture.setup.meter, "copyChunks") == 0L,
                    "DENY_PREFILL_NO_COPIES_OR_CHUNKS");
            OuterReadGuardMap guarded = new OuterReadGuardMap(
                    backing, fixture.setup.meter, 640L);
            Exception failure = null;
            try {
                CurrentAssemblyMetadata.validateReceiptTuple(
                        fixture.setup, guarded, 1L, sha);
            } catch (Exception expected) {
                failure = expected;
            }
            check(failure != null &&
                    "native wrapper exceeded 640 measured groups".equals(failure.getMessage()),
                    "DENIED_ADMISSION_EXACT_METER_FAILURE");
            check(guarded.gets == 0, "DENIED_ADMISSION_BEFORE_FIRST_GET");
            check(number(fixture.setup.meter, "units") == 640L &&
                    number(fixture.setup.meter, "groups") == 640L,
                    "DENIED_ADMISSION_NO_METER_DELTA");
            check(number(fixture.setup.meter, "copiedEntries") == 0L &&
                    number(fixture.setup.meter, "copyChunks") == 0L,
                    "DENIED_ADMISSION_NO_COPIES_OR_CHUNKS");
            check(before.unchanged(), "DENIED_ADMISSION_TREE_UNCHANGED");
            cases++;
        } finally {
            fixture.setup.finish();
        }
    }

    private static void nullMeter(String sha) throws Exception {
        LinkedHashMap<String, Object> prior = prior(receipt("op-1.jsonl", 1, sha), sha);
        MapSnapshot before = new MapSnapshot(prior);
        MeterFixture fixture = new MeterFixture();
        try {
            fixture.setup.charge(1L);
            long units = number(fixture.setup.meter, "units");
            long copies = number(fixture.setup.meter, "copiedEntries");
            long chunks = number(fixture.setup.meter, "copyChunks");
            long groups = number(fixture.setup.meter, "groups");
            IOException failure = null;
            try {
                CurrentAssemblyMetadata.validateReceiptTuple(null, prior, 1L, sha);
            } catch (IOException expected) {
                failure = expected;
            }
            check(failure != null && "METADATA_ARGUMENTS".equals(failure.getMessage()),
                    "NULL_METER_ARGUMENT_ERROR");
            ledger(fixture, "NULL_METER", units, copies, chunks, groups, 0L);
            check(before.unchanged(), "NULL_METER_TREE_UNCHANGED");
            cases++;
        } finally {
            fixture.setup.finish();
        }
    }

    private static void ledger(MeterFixture fixture, String label, long units,
            long copies, long chunks, long groups, long expectedUnits) throws Exception {
        check(number(fixture.setup.meter, "units") - units == expectedUnits,
                label + "_UNIT_DELTA_" + expectedUnits);
        check(number(fixture.setup.meter, "copiedEntries") - copies == 0L,
                label + "_ZERO_COPY_ENTRIES");
        check(number(fixture.setup.meter, "copyChunks") - chunks == 0L,
                label + "_ZERO_COPY_CHUNKS");
        check(number(fixture.setup.meter, "groups") - groups == 0L,
                label + "_ZERO_GROUP_DELTA");
    }

    private static LinkedHashMap<String, Object> receipt(Object path,
            Object bytes, Object sha) {
        LinkedHashMap<String, Object> receipt = new LinkedHashMap<String, Object>();
        receipt.put("receiptPath", path);
        receipt.put("receiptBytes", bytes);
        receipt.put("receiptSha256", sha);
        receipt.put("extension", "kept");
        return receipt;
    }

    private static LinkedHashMap<String, Object> prior(Object receipt,
            Object operationSha) {
        LinkedHashMap<String, Object> prior = new LinkedHashMap<String, Object>();
        prior.put("priorOriginalReceipt", receipt);
        prior.put("operation1Sha256", operationSha);
        prior.put("sentinel", "kept");
        return prior;
    }

    private static String validSha() {
        StringBuilder value = new StringBuilder(64);
        for (int i = 0; i < 4; i++) value.append("0123456789abcdef");
        return value.toString();
    }

    private static String filled(char value, int size) {
        char[] result = new char[size];
        for (int i = 0; i < size; i++) result[i] = value;
        return new String(result);
    }

    private static String changed(String value, int index, char replacement) {
        char[] result = value.toCharArray();
        result[index] = replacement;
        return new String(result);
    }

    private static long number(Object owner, String name) throws Exception {
        Object value = CohortTerminalTelemetryStudy.field(owner, name);
        if (!(value instanceof Number)) throw new AssertionError("METER_FIELD_" + name);
        return ((Number) value).longValue();
    }

    private static long unitsNow(Object meter) {
        try { return number(meter, "units"); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static void check(boolean condition, String code) {
        assertions++;
        if (!condition) throw new AssertionError(code);
    }

    private static final class MeterFixture {
        final long started = System.nanoTime();
        final CohortTerminalTelemetryStudy.Parent parent =
                new CohortTerminalTelemetryStudy.Parent();
        final CohortTerminalTelemetryStudy.SetupMeter setup =
                new CohortTerminalTelemetryStudy.SetupMeter(parent, parent.meter(started));

        MeterFixture() throws Exception { }
    }

    private static final class ReceiptMapSubclass extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    private static final class OuterReadGuardMap extends AbstractMap<Object, Object> {
        final Map<String, Object> delegate;
        final long baseUnits;
        Object meter;
        int gets;

        OuterReadGuardMap(Map<String, Object> delegate, Object meter, long baseUnits) {
            this.delegate = delegate;
            this.meter = meter;
            this.baseUnits = baseUnits;
        }

        public Object get(Object key) {
            long expected = gets == 0 ? 2L : gets == 1 ? 80L : -1L;
            if (unitsNow(meter) - baseUnits != expected)
                throw new AssertionError("OUTER_GET_WITHOUT_EXPECTED_ADMISSION_" + gets);
            String expectedKey = gets == 0 ? "priorOriginalReceipt" : "operation1Sha256";
            if (gets > 1 || !expectedKey.equals(key))
                throw new AssertionError("OUTER_GET_ORDER_OR_EXTRA_READ");
            gets++;
            return delegate.get(key);
        }

        public Set<Map.Entry<Object, Object>> entrySet() {
            throw new AssertionError("OUTER_MAP_ENUMERATED_OR_COPIED");
        }
    }

    private static final class ThrowingOuterMap extends AbstractMap<Object, Object> {
        final Map<String, Object> delegate;
        final int failOnGet;
        final RuntimeException failure;
        int gets;

        ThrowingOuterMap(Map<String, Object> delegate, int failOnGet,
                RuntimeException failure) {
            this.delegate = delegate;
            this.failOnGet = failOnGet;
            this.failure = failure;
        }

        public Object get(Object key) {
            gets++;
            if (gets == failOnGet) throw failure;
            return delegate.get(key);
        }

        public Set<Map.Entry<Object, Object>> entrySet() {
            throw new AssertionError("OUTER_MAP_ENUMERATED_OR_COPIED");
        }
    }

    private static final class MapSnapshot {
        final IdentityHashMap<Map<?, ?>, List<Object[]>> entries =
                new IdentityHashMap<Map<?, ?>, List<Object[]>>();

        MapSnapshot(Object root) { capture(root); }

        private void capture(Object value) {
            if (!(value instanceof Map)) return;
            Map<?, ?> map = (Map<?, ?>) value;
            if (entries.containsKey(map)) return;
            List<Object[]> saved = new ArrayList<Object[]>();
            entries.put(map, saved);
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object child = entry.getValue();
                saved.add(new Object[] { key, child });
                capture(child);
            }
        }

        boolean unchanged() {
            for (Map.Entry<Map<?, ?>, List<Object[]>> snapshot : entries.entrySet()) {
                Map<?, ?> map = snapshot.getKey();
                List<Object[]> saved = snapshot.getValue();
                if (map.size() != saved.size()) return false;
                Iterator<? extends Map.Entry<?, ?>> actual = map.entrySet().iterator();
                for (Object[] pair : saved) {
                    if (!actual.hasNext()) return false;
                    Map.Entry<?, ?> entry = actual.next();
                    if (entry.getKey() != pair[0] || entry.getValue() != pair[1]) return false;
                }
                if (actual.hasNext()) return false;
            }
            return true;
        }
    }
}
