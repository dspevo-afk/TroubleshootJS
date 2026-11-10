package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native-only source/meter fixture over synthetic List and validator metadata. */
public final class CurrentArmReadCanary {
    private static final String SCHEMA = "TSJ_CURRENT_ARM_READ_METER_CANARY_R1";
    private static final long MAX_TOTAL_NANOS = 90000000000L;
    private static final long MAX_OPERATION_NANOS = 90000000000L;
    private static final long MAX_GROUP_NANOS = 5000000000L;
    private static final long MAX_GROUP_UNITS = 32768L;
    private static final int GROUP_LIMIT = 640;
    private static final int OUTPUT_LIMIT = 1048576;
    private static final String NET = "SYNTH_NET_A";

    private CurrentArmReadCanary() { }

    private static final class Reads {
        long mapReads, listGetReads, sizeReads, iteratorConstructions;
        long hasNextReads, nextReads;
    }

    private static final class CountingMap extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID = 1L;
        final Reads reads;
        CountingMap(Reads reads) { this.reads = reads; }
        @Override public Object get(Object key) {
            reads.mapReads++;
            return super.get(key);
        }
    }

    /** Probe list observations and ensure the corresponding meter admission happened first. */
    private static final class CountingList extends ArrayList<Object> {
        private static final long serialVersionUID = 1L;
        final Reads reads;
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        long lastSizeUnits, lastGetUnits, sizeUnits, iteratorAdmission;
        int lastSize = -1;

        CountingList(CohortTerminalTelemetryStudy.SetupMeter meter, Reads reads,
                Object[] values) throws Exception {
            this.meter = meter;
            this.reads = reads;
            lastSizeUnits = units(meter);
            lastGetUnits = units(meter);
            for (int i = 0; i < values.length; i++) super.add(values[i]);
        }

        @Override public int size() {
            try {
                long now = units(meter);
                require(now > lastSizeUnits, "UNPAID_LIST_SIZE");
                int result = super.size();
                reads.sizeReads++;
                lastSizeUnits = now;
                sizeUnits = now;
                lastSize = result;
                return result;
            } catch (Exception failure) { throw probeFailure(failure); }
        }

        @Override public Object get(int index) {
            long now;
            try {
                now = units(meter);
                require(now > lastGetUnits, "UNPAID_LIST_GET");
            } catch (Exception failure) { throw probeFailure(failure); }
            reads.listGetReads++;
            lastGetUnits = now;
            return super.get(index);
        }

        @Override public Iterator<Object> iterator() {
            try {
                long now = units(meter);
                require(lastSize >= 0 && now - sizeUnits >= 2L * lastSize + 2L,
                    "UNPAID_LIST_ITERATOR");
                reads.iteratorConstructions++;
                iteratorAdmission = now;
                return counted(super.iterator());
            } catch (Exception failure) { throw probeFailure(failure); }
        }

        private Iterator<Object> counted(final Iterator<Object> delegate) {
            return new Iterator<Object>() {
                public boolean hasNext() {
                    try {
                        require(units(meter) >= iteratorAdmission, "UNPAID_LIST_HAS_NEXT");
                        reads.hasNextReads++;
                        return delegate.hasNext();
                    } catch (Exception failure) { throw probeFailure(failure); }
                }
                public Object next() {
                    try {
                        require(units(meter) >= iteratorAdmission, "UNPAID_LIST_NEXT");
                        reads.nextReads++;
                        return delegate.next();
                    } catch (Exception failure) { throw probeFailure(failure); }
                }
                public void remove() { delegate.remove(); }
            };
        }

        private RuntimeException probeFailure(Exception failure) {
            return new IllegalStateException(failure.getMessage(), failure);
        }
    }

    private static final class Row {
        String name, status = "FAIL", phase, reason = "", error = "", payload = "";
        long units, copiedEntries, maxGroupNanos;
        long mapReads, listGetReads, sizeReads, iteratorConstructions;
        long hasNextReads, nextReads, operationNanos, totalElapsedNanos;
        long openGroupElapsedNanos;
        int copyChunks, groups, priorGroups, groupUnits, maxGroupUnits;
        boolean open, identity, discarded;
    }

    private static final class ValidatorInput {
        final CountingMap run, observation;
        final CountingList requested;
        final Reads reads;
        ValidatorInput(CountingMap run, CountingMap observation,
                CountingList requested, Reads reads) {
            this.run = run; this.observation = observation;
            this.requested = requested; this.reads = reads;
        }
    }

    public static void main(String[] args) {
        long started = System.nanoTime();
        if (args == null || args.length != 1 ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0]))) {
            emit("", "FAIL", new ArrayList<String>(), new ArrayList<Row>(), 0,
                "CANARY_ARGUMENTS");
            System.exit(1);
            return;
        }
        String mode = args[0];
        List<String> names = names(mode);
        List<Row> rows = new ArrayList<Row>();
        int parentConstructions = 0;
        String topError = "";
        try {
            CohortTerminalTelemetryStudy.Parent parent =
                new CohortTerminalTelemetryStudy.Parent();
            parentConstructions++;
            for (int i = 0; i < names.size(); i++)
                rows.add(runCase(names.get(i), started, parent));
        } catch (Throwable failure) {
            topError = errorText(failure);
        }
        boolean withinTotal = false;
        try { withinTotal = elapsed(started) <= MAX_TOTAL_NANOS; }
        catch (Throwable failure) { topError = errorText(failure); }
        boolean pass = topError.length() == 0 && withinTotal && rows.size() == names.size();
        for (int i = 0; i < rows.size(); i++)
            pass = pass && "PASS".equals(rows.get(i).status);
        if (!pass && topError.length() == 0 && !withinTotal)
            topError = "CANARY_TOTAL_TIMEOUT";
        emit(mode, pass ? "PASS" : "FAIL", names, rows, parentConstructions, topError);
        if (!pass) System.exit(1);
    }

    private static List<String> names(String mode) {
        List<String> out = new ArrayList<String>();
        if ("SMOKE".equals(mode)) {
            out.add("list_read");
            out.add("contains_first");
            out.add("validator_no_failure_zero");
        } else {
            out.add("contains_last");
            out.add("contains_missing");
            out.add("contains_empty");
            out.add("list_index_rejected");
            out.add("list_group_guard");
            out.add("contains_group_guard");
            out.add("validator_schema_rejected");
            out.add("validator_callback_limit");
            out.add("validator_no_failure_callbacks");
            out.add("validator_quality_limit_zero");
            out.add("validator_first_non_found");
            out.add("validator_quality_net_callbacks");
        }
        return out;
    }

    private static String phase(String name) {
        if (name.endsWith("_group_guard")) return "guard";
        if (name.startsWith("validator_")) return "validator";
        if (name.startsWith("contains_")) return "contains";
        return "list";
    }

    private static Row runCase(String name, long totalStarted,
            CohortTerminalTelemetryStudy.Parent parent) {
        Row row = new Row();
        row.name = name;
        row.phase = phase(name);
        long opStarted = System.nanoTime();
        CohortTerminalTelemetryStudy.SetupMeter meter = null;
        Reads reads = new Reads();
        try {
            meter = new CohortTerminalTelemetryStudy.SetupMeter(
                parent, parent.meter(opStarted));
            if ("list_read".equals(name)) {
                listRead(meter, reads);
                success(row, meter, reads, 1L, 0L, 0, 1, 1L, 1L,
                    0L, 1L, 0L, 0L, 0L, 0L, "SYNTHETIC_METADATA");
            } else if ("contains_first".equals(name)) {
                containsCase(meter, reads, "first");
                success(row, meter, reads, 10L, 0L, 0, 1, 10L, 10L,
                    0L, 0L, 1L, 1L, 1L, 1L, "SYNTHETIC_METADATA");
            } else if ("contains_last".equals(name)) {
                containsCase(meter, reads, "last");
                success(row, meter, reads, 12L, 0L, 0, 1, 12L, 12L,
                    0L, 0L, 1L, 1L, 3L, 3L, "SYNTHETIC_METADATA");
            } else if ("contains_missing".equals(name)) {
                containsCase(meter, reads, "missing");
                success(row, meter, reads, 12L, 0L, 0, 1, 12L, 12L,
                    0L, 0L, 1L, 1L, 4L, 3L, "SYNTHETIC_METADATA");
            } else if ("contains_empty".equals(name)) {
                containsCase(meter, reads, "empty");
                success(row, meter, reads, 3L, 0L, 0, 1, 3L, 3L,
                    0L, 0L, 1L, 1L, 1L, 0L, "SYNTHETIC_METADATA");
            } else if ("list_index_rejected".equals(name)) {
                Throwable failure = listIndexFailure(meter, reads);
                reject(row, meter, reads, errorClass(failure),
                    "java.lang.IndexOutOfBoundsException", 1L, 0L, 0, 0,
                    1L, 0L, true, 0L, 1L, 0L, 0L, 0L, 0L);
            } else if ("list_group_guard".equals(name)) {
                prefillGroups(meter, opStarted, totalStarted);
                Throwable failure = listGroupFailure(meter, reads);
                reject(row, meter, reads, errorText(failure),
                    "native wrapper exceeded 640 measured groups", 640L, 0L, 0,
                    640, 1L, 1L, false, 0L, 0L, 0L, 0L, 0L, 0L);
            } else if ("contains_group_guard".equals(name)) {
                prefillGroups(meter, opStarted, totalStarted);
                Throwable failure = containsGroupFailure(meter, reads);
                reject(row, meter, reads, errorText(failure),
                    "native wrapper exceeded 640 measured groups", 640L, 0L, 0,
                    640, 1L, 1L, false, 0L, 0L, 0L, 0L, 0L, 0L);
            } else if ("validator_schema_rejected".equals(name)) {
                ValidatorInput input = validatorInput(meter, reads, "NO_FAILURE", 0,
                    0, null, null, null, "FOUND", "SYNTH_FOUND", 0, "wrong",
                    false, false);
                Throwable failure = validatorFailure(meter, input);
                reject(row, meter, reads, errorText(failure), "OBSERVER_V2_SCHEMA",
                    1L, 0L, 0, 0, 1L, 0L, true, 1L, 0L, 0L, 0L, 0L, 0L);
            } else if ("validator_callback_limit".equals(name)) {
                ValidatorInput input = validatorInput(meter, reads, "NO_FAILURE",
                    248, 300, null, null, null, "FOUND", "SYNTH_FOUND", 0,
                    null, false, false);
                Throwable failure = validatorFailure(meter, input);
                reject(row, meter, reads, errorText(failure), "OBSERVER_CALLBACK_COUNT",
                    7L, 0L, 0, 0, 7L, 0L, true, 6L, 0L, 0L, 0L, 0L, 0L);
            } else if ("validator_no_failure_zero".equals(name)) {
                ValidatorInput input = validatorInput(meter, reads, "NO_FAILURE",
                    0, 0, null, null, null, "FOUND", "SYNTH_FOUND", 0,
                    null, false, false);
                validatorSuccess(meter, input, row, 18L, 0L, 0, 1, 18L, 18L,
                    11L, 0L, 0L, 0L, 0L, 0L);
            } else if ("validator_no_failure_callbacks".equals(name)) {
                ValidatorInput input = validatorInput(meter, reads, "NO_FAILURE",
                    1, 1, Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1),
                    "FOUND", "SYNTH_FOUND", 1, null, false, false);
                validatorSuccess(meter, input, row, 40L, 0L, 0, 1, 40L, 40L,
                    15L, 2L, 3L, 2L, 2L, 2L);
            } else if ("validator_quality_limit_zero".equals(name)) {
                ValidatorInput input = validatorInput(meter, reads,
                    "NON_BRANCH_REJECTION", 0, 0, null, null, null,
                    "QUALITY_REJECTED", "QUALITY_LIMIT", 0, null, false, false);
                validatorSuccess(meter, input, row, 24L, 0L, 0, 1, 24L, 24L,
                    13L, 0L, 0L, 0L, 0L, 0L);
            } else if ("validator_first_non_found".equals(name)) {
                ValidatorInput input = validatorInput(meter, reads, "FIRST_NON_FOUND",
                    1, 1, Integer.valueOf(1), Integer.valueOf(1), Integer.valueOf(1),
                    "SYNTH_REJECTED", NET + ":SYNTH_REASON", 1, null, true, false);
                validatorSuccess(meter, input, row, 61L, 1L, 1, 1, 61L, 61L,
                    26L, 3L, 5L, 3L, 3L, 3L);
            } else if ("validator_quality_net_callbacks".equals(name)) {
                ValidatorInput input = validatorInput(meter, reads,
                    "NON_BRANCH_REJECTION", 1, 1, Integer.valueOf(1),
                    Integer.valueOf(1), Integer.valueOf(1), "QUALITY_REJECTED",
                    NET + ":BRANCH_QUALITY_LIMIT", 1, null, false, true);
                validatorSuccess(meter, input, row, 55L, 1L, 1, 1, 55L, 55L,
                    22L, 4L, 4L, 2L, 2L, 2L);
            } else throw new IOException("CANARY_CASE");
            require(elapsed(opStarted) <= MAX_OPERATION_NANOS, "CANARY_OPERATION_TIMEOUT");
            require(elapsed(totalStarted) <= MAX_TOTAL_NANOS, "CANARY_TOTAL_TIMEOUT");
            meterBounds(meter);
        } catch (Throwable failure) {
            row.status = "FAIL"; row.reason = "UNEXPECTED_FAILURE";
            row.error = errorText(failure); row.discarded = true;
        }
        try {
            row.operationNanos = elapsed(opStarted);
            row.totalElapsedNanos = elapsed(totalStarted);
        } catch (Throwable failure) {
            row.status = "FAIL"; row.reason = "UNEXPECTED_FAILURE";
            row.error = errorText(failure); row.discarded = true;
        }
        copyReads(row, reads);
        if (meter != null) sample(row, meter);
        if (row.operationNanos > MAX_OPERATION_NANOS ||
                row.totalElapsedNanos > MAX_TOTAL_NANOS ||
                row.maxGroupNanos > MAX_GROUP_NANOS ||
                row.openGroupElapsedNanos > MAX_GROUP_NANOS) {
            row.status = "FAIL"; row.reason = "UNEXPECTED_FAILURE";
            if (row.error.length() == 0) row.error = "CANARY_TIME_BOUND";
            row.discarded = true;
        }
        if (!"PASS".equals(row.status)) {
            row.identity = false;
            row.payload = "";
            row.discarded = true;
        }
        return row;
    }
    private static void listRead(CohortTerminalTelemetryStudy.SetupMeter meter,
            Reads reads) throws Exception {
        Object first = new Object(), expected = new Object(), last = new Object();
        CountingList values = new CountingList(meter, reads,
            new Object[]{first, expected, last});
        Object value = invokeListRead(meter, values, 1);
        require(value == expected, "LIST_READ_IDENTITY");
    }

    private static void containsCase(CohortTerminalTelemetryStudy.SetupMeter meter,
            Reads reads, String kind) throws Exception {
        Object a = new Object(), b = new Object(), c = new Object();
        Object[] items;
        Object query;
        boolean expected;
        if ("empty".equals(kind)) {
            items = new Object[0]; query = new Object(); expected = false;
        } else if ("first".equals(kind)) {
            items = new Object[]{a, b, c}; query = a; expected = true;
        } else if ("last".equals(kind)) {
            items = new Object[]{a, b, c}; query = c; expected = true;
        } else {
            items = new Object[]{a, b, c}; query = new Object(); expected = false;
        }
        CountingList values = new CountingList(meter, reads, items);
        boolean actual = invokeContains(meter, values, query);
        require(actual == expected, "CONTAINS_RESULT");
    }

    private static Throwable listIndexFailure(
            CohortTerminalTelemetryStudy.SetupMeter meter, Reads reads) throws Exception {
        CountingList values = new CountingList(meter, reads,
            new Object[]{new Object(), new Object(), new Object()});
        try { invokeListRead(meter, values, 3); }
        catch (Throwable failure) { return failure; }
        return null;
    }

    private static Throwable listGroupFailure(
            CohortTerminalTelemetryStudy.SetupMeter meter, Reads reads) throws Exception {
        CountingList values = new CountingList(meter, reads, new Object[]{new Object()});
        try { invokeListRead(meter, values, 0); }
        catch (Throwable failure) { return failure; }
        return null;
    }

    private static Throwable containsGroupFailure(
            CohortTerminalTelemetryStudy.SetupMeter meter, Reads reads) throws Exception {
        CountingList values = new CountingList(meter, reads, new Object[]{new Object()});
        try { invokeContains(meter, values, new Object()); }
        catch (Throwable failure) { return failure; }
        return null;
    }

    private static void validatorSuccess(CohortTerminalTelemetryStudy.SetupMeter meter,
            ValidatorInput input, Row row, long units, long copies, int chunks,
            int groups, long groupUnits, long maxGroupUnits, long mapReads,
            long listGets, long sizeReads, long iterators, long hasNext, long next)
            throws Exception {
        invokeValidator(meter, input);
        success(row, meter, input.reads, units, copies, chunks, groups,
            groupUnits, maxGroupUnits, mapReads, listGets, sizeReads,
            iterators, hasNext, next, "SYNTHETIC_VALIDATOR_METADATA");
    }

    private static ValidatorInput validatorInput(
            CohortTerminalTelemetryStudy.SetupMeter meter, Reads reads, String state,
            int callbacks, int advanceCalls, Integer first, Integer last,
            Integer lastTotal, String disposition, String reason, int expanded,
            String schema, boolean selectedSnapshot, boolean terminalProof)
            throws Exception {
        CountingList requested = new CountingList(meter, reads,
            callbacks > 0 ? new Object[]{NET} : new Object[0]);
        CountingList traversed = new CountingList(meter, reads,
            callbacks > 0 ? new Object[]{NET} : new Object[0]);

        CountingMap outcome = new CountingMap(reads);
        outcome.put("disposition", disposition);
        outcome.put("expanded", Integer.valueOf(expanded));
        outcome.put("reason", reason);

        CountingMap work = new CountingMap(reads);
        work.put("advanceCalls", Integer.valueOf(advanceCalls));

        CountingMap ordering = new CountingMap(reads);
        ordering.put("traversedNetIds", traversed);

        CountingMap run = new CountingMap(reads);
        run.put("outcome", outcome);
        run.put("work", work);
        run.put("ordering", ordering);

        if (terminalProof) {
            CountingMap terminal = new CountingMap(reads);
            terminal.put("failedNetId", NET);
            terminal.put("ownerReason", reason);
            run.put("terminalFailure", terminal);
        } else run.put("terminalFailure", null);

        CountingMap selected = null;
        if (selectedSnapshot) {
            selected = new CountingMap(reads);
            selected.put("netId", NET);
            selected.put("terminalReason", "SYNTH_REASON");
            selected.put("pass", Integer.valueOf(2));
            selected.put("totalExpanded", Integer.valueOf(1));
        }

        CountingMap obs = new CountingMap(reads);
        obs.put("schema", schema == null ?
            "tsj-cohort-terminal-observation-v2" : schema);
        obs.put("mode", "OBSERVE");
        obs.put("state", state);
        obs.put("callbackCount", Integer.valueOf(callbacks));
        obs.put("foundCallbackCount", Integer.valueOf(
            "NO_FAILURE".equals(state) ? callbacks : 0));
        obs.put("nonFoundCallbackCount", Integer.valueOf(
            "NO_FAILURE".equals(state) ? 0 : callbacks));
        obs.put("firstNetOrdinal", first);
        obs.put("lastNetOrdinal", last);
        obs.put("lastCallbackTotalExpanded", lastTotal);
        obs.put("selectedCallbackOrdinal", selectedSnapshot ? Integer.valueOf(1) : null);
        obs.put("transientTupleCopies", Integer.valueOf(0));
        obs.put("transientFieldCopies", Integer.valueOf(0));
        obs.put("validationCount", Integer.valueOf(0));
        obs.put("observerUnits", Integer.valueOf(0));
        obs.put("copiedEntries", Integer.valueOf(0));
        obs.put("copyChunks", Integer.valueOf(0));
        obs.put("selectedSnapshot", selected);
        obs.put("selectedHead", null);
        return new ValidatorInput(run, obs, requested, reads);
    }

    private static Throwable validatorFailure(
            CohortTerminalTelemetryStudy.SetupMeter meter, ValidatorInput input) {
        try { invokeValidator(meter, input); }
        catch (Throwable failure) { return failure; }
        return null;
    }

    private static void invokeValidator(CohortTerminalTelemetryStudy.SetupMeter meter,
            ValidatorInput input) throws Exception {
        Method method = CohortTerminalTelemetryStudy.class.getDeclaredMethod(
            "validateObservation", Map.class, Map.class, List.class,
            CohortTerminalTelemetryStudy.SetupMeter.class);
        method.setAccessible(true);
        CohortTerminalTelemetryStudy.invoke(method, null, input.run,
            input.observation, input.requested, meter);
    }

    private static Object invokeListRead(CohortTerminalTelemetryStudy.SetupMeter meter,
            List<?> source, int index) throws Exception {
        Method method = CohortTerminalTelemetryStudy.class.getDeclaredMethod(
            "armListRead", CohortTerminalTelemetryStudy.SetupMeter.class,
            List.class, int.class);
        method.setAccessible(true);
        return CohortTerminalTelemetryStudy.invoke(method, null, meter, source,
            Integer.valueOf(index));
    }

    private static boolean invokeContains(CohortTerminalTelemetryStudy.SetupMeter meter,
            List<?> values, Object value) throws Exception {
        Method method = CohortTerminalTelemetryStudy.class.getDeclaredMethod(
            "containsMetered", CohortTerminalTelemetryStudy.SetupMeter.class,
            List.class, Object.class);
        method.setAccessible(true);
        return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.invoke(
            method, null, meter, values, value));
    }
    private static void success(Row row,
            CohortTerminalTelemetryStudy.SetupMeter meter, Reads reads,
            long units, long copies, int chunks, int groups,
            long groupUnits, long maxGroupUnits, long mapReads,
            long listGets, long sizeReads, long iterators, long hasNext, long next,
            String payload) throws Exception {
        meter.finish();
        ledger(meter, units, copies, chunks, groups, groupUnits, maxGroupUnits, false);
        counters(reads, mapReads, listGets, sizeReads, iterators, hasNext, next);
        row.status = "PASS";
        row.reason = "OK";
        row.identity = true;
        row.payload = payload;
    }

    private static void reject(Row row,
            CohortTerminalTelemetryStudy.SetupMeter meter, Reads reads,
            String actualError, String expectedError, long units, long copies,
            int chunks, int groups, long groupUnits, long maxGroupUnits,
            boolean open, long mapReads, long listGets, long sizeReads,
            long iterators, long hasNext, long next) throws Exception {
        require(expectedError.equals(actualError), "EXPECTED_ERROR");
        ledger(meter, units, copies, chunks, groups, groupUnits,
            maxGroupUnits, open);
        counters(reads, mapReads, listGets, sizeReads, iterators, hasNext, next);
        row.status = "PASS";
        row.reason = "EXPECTED_REJECTION";
        row.error = expectedError;
        row.payload = "";
        row.identity = false;
        row.discarded = true;
    }

    private static void prefillGroups(CohortTerminalTelemetryStudy.SetupMeter meter,
            long operationStarted, long totalStarted) throws Exception {
        for (int i = 0; i < GROUP_LIMIT; i++) {
            meter.charge(1L);
            meter.finish();
            require(elapsed(operationStarted) <= MAX_OPERATION_NANOS,
                "CANARY_OPERATION_TIMEOUT");
            require(elapsed(totalStarted) <= MAX_TOTAL_NANOS,
                "CANARY_TOTAL_TIMEOUT");
        }
        ledger(meter, GROUP_LIMIT, 0L, 0, GROUP_LIMIT, 1L, 1L, false);
    }

    private static void ledger(CohortTerminalTelemetryStudy.SetupMeter meter,
            long expectedUnits, long expectedCopies, int expectedChunks,
            int expectedGroups, long expectedGroupUnits, long expectedMaxGroupUnits,
            boolean expectedOpen) throws Exception {
        Object raw = meter.meter;
        require(number(raw, "units").longValue() == expectedUnits, "LEDGER_UNITS");
        require(number(raw, "copiedEntries").longValue() == expectedCopies,
            "LEDGER_COPIES");
        require(number(raw, "copyChunks").intValue() == expectedChunks,
            "LEDGER_CHUNKS");
        require(number(raw, "groups").intValue() == expectedGroups, "LEDGER_GROUPS");
        require(number(raw, "priorGroups").intValue() == 0, "LEDGER_PRIOR_GROUPS");
        require(number(raw, "groupUnits").longValue() == expectedGroupUnits,
            "LEDGER_GROUP_UNITS");
        require(number(raw, "maxGroupUnits").longValue() == expectedMaxGroupUnits,
            "LEDGER_MAX_GROUP_UNITS");
        require(booleanField(raw, "open") == expectedOpen, "LEDGER_OPEN");
    }

    private static void counters(Reads reads, long mapReads, long listGets,
            long sizeReads, long iterators, long hasNext, long next)
            throws IOException {
        require(reads.mapReads == mapReads, "COUNTER_MAP_READS");
        require(reads.listGetReads == listGets, "COUNTER_LIST_GETS");
        require(reads.sizeReads == sizeReads, "COUNTER_SIZE_READS");
        require(reads.iteratorConstructions == iterators, "COUNTER_ITERATORS");
        require(reads.hasNextReads == hasNext, "COUNTER_HAS_NEXT");
        require(reads.nextReads == next, "COUNTER_NEXT");
    }

    private static void meterBounds(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        Object raw = meter.meter;
        require(number(raw, "units").longValue() >= 0L, "METER_NEGATIVE_UNITS");
        require(number(raw, "copiedEntries").longValue() >= 0L &&
            number(raw, "copiedEntries").longValue() <= 1048576L,
            "METER_COPY_BOUND");
        require(number(raw, "copyChunks").intValue() >= 0, "METER_NEGATIVE_CHUNKS");
        require(number(raw, "groups").intValue() >= 0 &&
            number(raw, "groups").intValue() <= GROUP_LIMIT &&
            number(raw, "priorGroups").intValue() == 0, "METER_GROUP_BOUND");
        require(number(raw, "groupUnits").longValue() >= 0L &&
            number(raw, "groupUnits").longValue() <= MAX_GROUP_UNITS &&
            number(raw, "maxGroupUnits").longValue() >= 0L &&
            number(raw, "maxGroupUnits").longValue() <= MAX_GROUP_UNITS,
            "METER_GROUP_UNITS_BOUND");
        require(number(raw, "maxGroupNanos").longValue() >= 0L &&
            number(raw, "maxGroupNanos").longValue() <= MAX_GROUP_NANOS,
            "METER_GROUP_TIME_BOUND");
    }

    private static long units(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        return number(meter.meter, "units").longValue();
    }

    private static Number number(Object object, String name) throws Exception {
        Object value = CohortTerminalTelemetryStudy.field(object, name);
        require(value instanceof Number, "METER_FIELD_" + name);
        return (Number) value;
    }

    private static boolean booleanField(Object object, String name) throws Exception {
        Object value = CohortTerminalTelemetryStudy.field(object, name);
        require(value instanceof Boolean, "METER_FIELD_" + name);
        return ((Boolean) value).booleanValue();
    }

    private static void copyReads(Row row, Reads reads) {
        row.mapReads = reads.mapReads;
        row.listGetReads = reads.listGetReads;
        row.sizeReads = reads.sizeReads;
        row.iteratorConstructions = reads.iteratorConstructions;
        row.hasNextReads = reads.hasNextReads;
        row.nextReads = reads.nextReads;
    }

    private static void sample(Row row,
            CohortTerminalTelemetryStudy.SetupMeter meter) {
        try {
            Object raw = meter.meter;
            row.units = number(raw, "units").longValue();
            row.copiedEntries = number(raw, "copiedEntries").longValue();
            row.copyChunks = number(raw, "copyChunks").intValue();
            row.groups = number(raw, "groups").intValue();
            row.priorGroups = number(raw, "priorGroups").intValue();
            row.groupUnits = number(raw, "groupUnits").intValue();
            row.maxGroupUnits = number(raw, "maxGroupUnits").intValue();
            row.maxGroupNanos = number(raw, "maxGroupNanos").longValue();
            row.open = booleanField(raw, "open");
            if (row.open)
                row.openGroupElapsedNanos =
                    elapsed(number(raw, "groupStarted").longValue());
            require(row.units >= 0L && row.copiedEntries >= 0L &&
                row.maxGroupNanos >= 0L && row.copyChunks >= 0 &&
                row.groups >= 0 && row.priorGroups >= 0 &&
                row.groupUnits >= 0 && row.maxGroupUnits >= 0 &&
                row.openGroupElapsedNanos >= 0L, "CANARY_NEGATIVE_LEDGER");
        } catch (Throwable failure) {
            row.status = "FAIL";
            row.reason = "UNEXPECTED_FAILURE";
            row.error = errorText(failure);
            row.discarded = true;
        }
    }

    private static long elapsed(long started) {
        long value = System.nanoTime() - started;
        if (value < 0L) throw new IllegalStateException("CANARY_NEGATIVE_CLOCK");
        return value;
    }

    private static void require(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException(code);
    }

    private static RuntimeException probeFailure(Exception failure) {
        return new IllegalStateException(failure.getMessage(), failure);
    }

    private static String errorClass(Throwable failure) {
        return failure == null ? "CANARY_EXPECTED_REJECTION_MISSING" :
            failure.getClass().getName();
    }

    private static String errorText(Throwable failure) {
        if (failure == null) return "CANARY_EXPECTED_REJECTION_MISSING";
        Throwable current = failure;
        while (current instanceof java.lang.reflect.InvocationTargetException &&
                ((java.lang.reflect.InvocationTargetException) current).getCause() != null)
            current = ((java.lang.reflect.InvocationTargetException) current).getCause();
        String message = current.getMessage();
        return message == null || message.length() == 0 ?
            current.getClass().getName() : message;
    }
    private static void emit(String mode, String status, List<String> names,
            List<Row> rows, int parentConstructions, String error) {
        StringBuilder out = new StringBuilder(8192);
        out.append('{');
        stringField(out, "schema", SCHEMA, false);
        stringField(out, "mode", mode, true);
        stringField(out, "status", status, true);
        out.append(",\"names\":[");
        for (int i = 0; i < names.size(); i++) {
            if (i != 0) out.append(',');
            jsonString(out, names.get(i));
        }
        out.append("],\"cases\":[");
        for (int i = 0; i < rows.size(); i++) {
            if (i != 0) out.append(',');
            appendCase(out, rows.get(i));
        }
        out.append("],\"parentConstructions\":").append(parentConstructions);
        stringField(out, "error", error, true);
        out.append('}');
        String json = out.toString();
        if (json.length() > OUTPUT_LIMIT || !asciiOnly(json))
            json = "{\"schema\":\"TSJ_CURRENT_ARM_READ_METER_CANARY_R1\",\"mode\":\"\",\"status\":\"FAIL\",\"names\":[],\"cases\":[],\"parentConstructions\":0,\"error\":\"CANARY_OUTPUT_BOUND\"}";
        System.out.println(json);
    }

    private static void appendCase(StringBuilder out, Row row) {
        out.append('{');
        stringField(out, "name", row.name, false);
        stringField(out, "status", row.status, true);
        stringField(out, "phase", row.phase, true);
        stringField(out, "reason", row.reason, true);
        stringField(out, "error", row.error, true);
        longField(out, "units", row.units);
        longField(out, "copiedEntries", row.copiedEntries);
        intField(out, "copyChunks", row.copyChunks);
        intField(out, "groups", row.groups);
        intField(out, "priorGroups", row.priorGroups);
        intField(out, "groupUnits", row.groupUnits);
        intField(out, "maxGroupUnits", row.maxGroupUnits);
        longField(out, "maxGroupNanos", row.maxGroupNanos);
        booleanField(out, "open", row.open);
        longField(out, "mapReads", row.mapReads);
        longField(out, "listGetReads", row.listGetReads);
        longField(out, "sizeReads", row.sizeReads);
        longField(out, "iteratorConstructions", row.iteratorConstructions);
        longField(out, "hasNextReads", row.hasNextReads);
        longField(out, "nextReads", row.nextReads);
        booleanField(out, "identity", row.identity);
        stringField(out, "payload", row.payload, true);
        longField(out, "operationNanos", row.operationNanos);
        longField(out, "totalElapsedNanos", row.totalElapsedNanos);
        longField(out, "openGroupElapsedNanos", row.openGroupElapsedNanos);
        booleanField(out, "discarded", row.discarded);
        out.append('}');
    }

    private static void stringField(StringBuilder out, String key, String value,
            boolean comma) {
        if (comma) out.append(',');
        jsonString(out, key);
        out.append(':');
        jsonString(out, value == null ? "" : value);
    }

    private static void longField(StringBuilder out, String key, long value) {
        out.append(','); jsonString(out, key); out.append(':').append(value);
    }

    private static void intField(StringBuilder out, String key, int value) {
        out.append(','); jsonString(out, key); out.append(':').append(value);
    }

    private static void booleanField(StringBuilder out, String key, boolean value) {
        out.append(','); jsonString(out, key); out.append(':').append(value);
    }

    private static void jsonString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') out.append("\\\"");
            else if (c == '\\') out.append("\\\\");
            else if (c >= 32 && c <= 126) out.append(c);
            else {
                out.append("\\u");
                String hex = "0123456789abcdef";
                out.append(hex.charAt((c >>> 12) & 15));
                out.append(hex.charAt((c >>> 8) & 15));
                out.append(hex.charAt((c >>> 4) & 15));
                out.append(hex.charAt(c & 15));
            }
        }
        out.append('"');
    }

    private static boolean asciiOnly(String value) {
        for (int i = 0; i < value.length(); i++)
            if (value.charAt(i) < 32 || value.charAt(i) > 126) return false;
        return true;
    }
}
