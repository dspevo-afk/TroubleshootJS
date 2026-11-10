package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native-only fixture for the bounded original DTO meter adapters. */
public final class OriginalDtoMeterCanary {
    private static final String SCHEMA = "TSJ_ORIGINAL_DTO_METER_CANARY_R1";
    private static final String RUN_ID = "11111111-1111-4111-8111-111111111111";
    private static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final long MAX_TOTAL_NANOS = 90000000000L;
    private static final long MAX_OPERATION_NANOS = 90000000000L;
    private static final long MAX_GROUP_NANOS = 5000000000L;
    private static final int GROUP_LIMIT = 640;
    private static final long GROUP_UNITS_LIMIT = 32768L;
    private static final int OUTPUT_LIMIT = 1048576;

    private OriginalDtoMeterCanary() { }

    private static final class CountingMap extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID = 1L;
        int reads;
        @Override public Object get(Object key) { reads++; return super.get(key); }
    }

    private static final class Row {
        String name, status = "FAIL", phase, reason = "", error = "", payload = "";
        long units, copiedEntries, maxGroupNanos, sourceReads;
        long operationNanos, totalElapsedNanos, openGroupElapsedNanos;
        int copyChunks, groups, priorGroups, groupUnits, maxGroupUnits;
        boolean open, identity, discarded;
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
        String error = "";
        try {
            CohortTerminalTelemetryStudy.Parent parent =
                new CohortTerminalTelemetryStudy.Parent();
            parentConstructions++;
            CohortTerminalTelemetryStudy.Arguments arguments = arguments();
            for (int i = 0; i < names.size(); i++)
                rows.add(run(names.get(i), started, parent, arguments));
        } catch (Throwable failure) {
            error = errorText(failure);
        }
        boolean withinTotal = false;
        try { withinTotal = elapsed(started) <= MAX_TOTAL_NANOS; }
        catch (Throwable failure) { error = errorText(failure); }
        boolean pass = error.length() == 0 && rows.size() == names.size() && withinTotal;
        for (int i = 0; i < rows.size(); i++)
            pass = pass && "PASS".equals(rows.get(i).status);
        if (!pass && error.length() == 0 && !withinTotal)
            error = "CANARY_TOTAL_TIMEOUT";
        emit(mode, pass ? "PASS" : "FAIL", names, rows, parentConstructions, error);
        if (!pass) System.exit(1);
    }

    private static List<String> names(String mode) {
        List<String> out = new ArrayList<String>();
        if ("SMOKE".equals(mode)) {
            out.add("map_read");
            out.add("synthetic_recipe");
        } else {
            out.add("field_read");
            out.add("map_null_source");
            out.add("field_null_source");
            out.add("map_group_guard");
            out.add("field_group_guard");
            out.add("invalid_capture_null");
            out.add("invalid_capture_text");
            out.add("invalid_capture_same_run");
        }
        return out;
    }

    private static Row run(String name, long totalStarted,
            CohortTerminalTelemetryStudy.Parent parent,
            CohortTerminalTelemetryStudy.Arguments arguments) {
        Row row = new Row();
        row.name = name;
        row.phase = name.startsWith("invalid_capture_") ? "projection" :
            (name.equals("synthetic_recipe") ? "recipe" :
            (name.equals("map_read") || name.equals("map_null_source") ? "map" :
            (name.equals("field_read") || name.equals("field_null_source") ? "field" :
            "guard")));
        long opStarted = System.nanoTime();
        CohortTerminalTelemetryStudy.SetupMeter meter = null;
        CountingMap counted = null;
        try {
            meter = new CohortTerminalTelemetryStudy.SetupMeter(
                parent, parent.meter(opStarted));
            if ("map_read".equals(name)) {
                counted = mapRead(meter);
                row.sourceReads = counted.reads;
                row.status = "PASS"; row.reason = "OK"; row.identity = true;
                row.payload = "SYNTHETIC_METADATA";
                meter.finish();
                ledger(meter, 1L, 0L, 0, 1, 1L, 1L, false);
            } else if ("synthetic_recipe".equals(name)) {
                counted = syntheticRecipe(meter);
                row.sourceReads = counted.reads;
                row.status = "PASS"; row.reason = "OK"; row.identity = true;
                row.payload = "SYNTHETIC_RECIPE_NOT_ORIGINAL_DTO";
                meter.finish();
                ledger(meter, 154L, 105L, 2, 1, 154L, 154L, false);
            } else if ("field_read".equals(name)) {
                counted = fieldRead(meter);
                row.sourceReads = 0L;
                row.status = "PASS"; row.reason = "OK"; row.identity = true;
                row.payload = "SYNTHETIC_METADATA";
                meter.finish();
                ledger(meter, 1L, 0L, 0, 1, 1L, 1L, false);
            } else if ("map_null_source".equals(name)) {
                row.error = rejectedMapNull(meter);
                row.status = "PASS"; row.reason = "EXPECTED_REJECTION";
                row.discarded = true;
                ledger(meter, 0L, 0L, 0, 0, 0L, 0L, false);
            } else if ("field_null_source".equals(name)) {
                row.error = rejectedFieldNull(meter);
                row.status = "PASS"; row.reason = "EXPECTED_REJECTION";
                row.discarded = true;
                ledger(meter, 0L, 0L, 0, 0, 0L, 0L, false);
            } else if ("map_group_guard".equals(name)) {
                counted = groupGuard(meter, true, opStarted, totalStarted);
                row.sourceReads = counted.reads;
                row.error = guardError(meter, counted, true);
                row.status = "PASS"; row.reason = "EXPECTED_REJECTION";
                row.discarded = true;
            } else if ("field_group_guard".equals(name)) {
                counted = groupGuard(meter, false, opStarted, totalStarted);
                row.error = guardError(meter, counted, false);
                row.status = "PASS"; row.reason = "EXPECTED_REJECTION";
                row.discarded = true;
            } else {
                String captureId = "invalid_capture_null".equals(name) ? null :
                    ("invalid_capture_text".equals(name) ? "not-a-uuid" : RUN_ID);
                long expected = "invalid_capture_same_run".equals(name) ? 3L : 2L;
                row.error = rejectedCapture(meter, arguments, captureId, expected);
                row.status = "PASS"; row.reason = "EXPECTED_REJECTION";
                row.discarded = true;
            }
            check(elapsed(opStarted) <= MAX_OPERATION_NANOS, "CANARY_OPERATION_TIMEOUT");
            check(elapsed(totalStarted) <= MAX_TOTAL_NANOS, "CANARY_TOTAL_TIMEOUT");
            bounds(meter);
        } catch (Throwable failure) {
            row.status = "FAIL"; row.reason = "UNEXPECTED_FAILURE";
            row.error = errorText(failure); row.discarded = true;
        }
        try {
            row.operationNanos = elapsed(opStarted);
            row.totalElapsedNanos = elapsed(totalStarted);
        } catch (Throwable clockFailure) {
            row.status = "FAIL"; row.reason = "UNEXPECTED_FAILURE";
            row.error = errorText(clockFailure); row.discarded = true;
        }
        row.sourceReads = counted == null ? row.sourceReads : counted.reads;
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
    private static CountingMap mapRead(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        CountingMap source = new CountingMap();
        Object expected = new Object();
        source.put("synthetic-key", expected);
        Object value = invokeMapRead(meter, source, "synthetic-key");
        check(value == expected && source.reads == 1, "MAP_READ_IDENTITY_OR_COUNT");
        check(units(meter) == 1L, "MAP_READ_UNITS");
        return source;
    }

    /** Pricing recipe only; never calls originalDto or represents an accepted capture. */
    private static CountingMap syntheticRecipe(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        CountingMap source = new CountingMap();
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < 14; i++) {
            Object value = new Object();
            values.add(value);
            source.put("synthetic-map-" + i, value);
        }
        CohortTerminalTelemetryStudy.SetupResult setup =
            new CohortTerminalTelemetryStudy.SetupResult(source, 0L, 0L, 0, 0);
        check(setup.units == 0L && setup.copiedEntries == 0L &&
            setup.groups == 0 && setup.copyChunks == 0, "SYNTHETIC_SETUP_METADATA");

        meter.charge(2L);
        for (int i = 0; i < 25; i++)
            check(invokeFieldRead(meter, setup, "data") == source, "RECIPE_FIELD_IDENTITY");
        for (int i = 0; i < 14; i++)
            check(invokeMapRead(meter, source, "synthetic-map-" + i) == values.get(i),
                "RECIPE_MAP_IDENTITY");
        check(source.reads == 14, "RECIPE_MAP_READ_COUNT");

        List<String> sourceNets = new ArrayList<String>();
        for (int i = 0; i < 60; i++) sourceNets.add("synthetic-net-" + i);
        meter.copy(60);
        meter.charge(1L);
        List<String> detachedNets = new ArrayList<String>(sourceNets);
        check(detachedNets.size() == 60 && detachedNets.get(59) == sourceNets.get(59),
            "RECIPE_LIST_COPY");
        meter.copy(45);

        Map<String,Object> receipt = chargedMap(meter); putMany(receipt, "receipt", 5);
        Map<String,Object> request = chargedMap(meter); putMany(request, "request", 3);
        Map<String,Object> seeds = chargedMap(meter); putMany(seeds, "seed", 3);
        Map<String,Object> settings = chargedMap(meter); putMany(settings, "setting", 10);
        Map<String,Object> template = chargedMap(meter); putMany(template, "template", 6);
        Map<String,Object> pad = chargedMap(meter); putMany(pad, "pad", 3);
        Map<String,Object> output = chargedMap(meter); putMany(output, "output", 15);
        check(receipt.size() + request.size() + seeds.size() + settings.size() +
            template.size() + pad.size() + output.size() == 45, "RECIPE_STORED_SLOTS");
        check(units(meter) == 154L && copies(meter) == 105L && chunks(meter) == 2,
            "RECIPE_PRICE");
        return source;
    }

    private static Map<String,Object> chargedMap(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        meter.charge(1L);
        return new LinkedHashMap<String,Object>();
    }

    private static void putMany(Map<String,Object> target, String prefix, int count) {
        for (int i = 0; i < count; i++)
            target.put(prefix + i, "SYNTHETIC_METADATA");
    }

    private static CountingMap fieldRead(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        CountingMap source = new CountingMap();
        source.put("synthetic", new Object());
        CohortTerminalTelemetryStudy.SetupResult setup =
            new CohortTerminalTelemetryStudy.SetupResult(source, 0L, 0L, 0, 0);
        check(invokeFieldRead(meter, setup, "data") == source, "FIELD_READ_IDENTITY");
        check(units(meter) == 1L && source.reads == 0, "FIELD_READ_UNITS_OR_MAP_COUNT");
        return source;
    }

    private static String rejectedMapNull(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        Exception failure = null;
        try { invokeMapRead(meter, null, "synthetic-key"); }
        catch (Exception caught) { failure = caught; }
        check(failure != null &&
            "ORIGINAL_DTO_SOURCE_MAP_MISSING".equals(errorText(failure)), "NULL_MAP_ERROR");
        return errorText(failure);
    }

    private static String rejectedFieldNull(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        Exception failure = null;
        try { invokeFieldRead(meter, null, "data"); }
        catch (Exception caught) { failure = caught; }
        check(failure != null && "R7_SOURCE_MISSING".equals(errorText(failure)),
            "NULL_FIELD_ERROR");
        return errorText(failure);
    }

    private static CountingMap groupGuard(CohortTerminalTelemetryStudy.SetupMeter meter,
            boolean mapCase, long operationStarted, long totalStarted) throws Exception {
        CountingMap source = new CountingMap();
        source.put("synthetic-key", new Object());
        fillGroups(meter, operationStarted, totalStarted);
        long before = units(meter);
        Exception failure = null;
        if (mapCase) {
            try { invokeMapRead(meter, source, "synthetic-key"); }
            catch (Exception caught) { failure = caught; }
        } else {
            CohortTerminalTelemetryStudy.SetupResult setup =
                new CohortTerminalTelemetryStudy.SetupResult(source, 0L, 0L, 0, 0);
            try { invokeFieldRead(meter, setup, "data"); }
            catch (Exception caught) { failure = caught; }
        }
        check(failure != null &&
            "native wrapper exceeded 640 measured groups".equals(errorText(failure)),
            "GROUP_GUARD_ERROR");
        check(units(meter) == before && before == GROUP_LIMIT, "GROUP_GUARD_UNCHARGED");
        check(source.reads == 0, "GROUP_GUARD_SOURCE_READ");
        ledger(meter, GROUP_LIMIT, 0L, 0, GROUP_LIMIT, 1L, 1L, false);
        return source;
    }

    private static String guardError(CohortTerminalTelemetryStudy.SetupMeter meter,
            CountingMap source, boolean mapCase) throws Exception {
        check(source.reads == 0 && groups(meter) == GROUP_LIMIT, "GROUP_GUARD_REPORT");
        return "native wrapper exceeded 640 measured groups";
    }

    private static void fillGroups(CohortTerminalTelemetryStudy.SetupMeter meter,
            long operationStarted, long totalStarted) throws Exception {
        for (int i = 0; i < GROUP_LIMIT; i++) {
            meter.charge(1L);
            meter.finish();
            check(elapsed(operationStarted) <= MAX_OPERATION_NANOS, "CANARY_OPERATION_TIMEOUT");
            check(elapsed(totalStarted) <= MAX_TOTAL_NANOS, "CANARY_TOTAL_TIMEOUT");
        }
        check(groups(meter) == GROUP_LIMIT && units(meter) == GROUP_LIMIT &&
            !isOpen(meter), "GROUP_PREFILL_LEDGER");
    }

    private static String rejectedCapture(CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.Arguments arguments, String captureId,
            long expectedUnits) throws Exception {
        Method originalDto = CohortTerminalTelemetryStudy.class.getDeclaredMethod(
            "originalDto", Object.class, CohortTerminalTelemetryStudy.Arguments.class,
            CohortTerminalTelemetryStudy.PadProof.class, String.class,
            CohortTerminalTelemetryStudy.SetupMeter.class);
        originalDto.setAccessible(true);
        Exception failure = null;
        try {
            CohortTerminalTelemetryStudy.invoke(originalDto, null, null, arguments,
                null, captureId, meter);
        } catch (Exception caught) { failure = caught; }
        check(failure != null && "CAPTURE_ID".equals(errorText(failure)), "CAPTURE_ID_ERROR");
        check(units(meter) == expectedUnits, "CAPTURE_ID_UNITS");
        ledger(meter, expectedUnits, 0L, 0, 0, expectedUnits, 0L, true);
        check(priorGroups(meter) == 0, "CAPTURE_ID_PRIOR_GROUPS");
        return errorText(failure);
    }

    private static CohortTerminalTelemetryStudy.Arguments arguments() throws Exception {
        Constructor<CohortTerminalTelemetryStudy.Arguments> constructor =
            CohortTerminalTelemetryStudy.Arguments.class.getDeclaredConstructor(
                String.class, String.class, String.class, String.class, Path.class);
        constructor.setAccessible(true);
        try {
            return constructor.newInstance(RUN_ID, SHA, SHA, SHA, Paths.get("."));
        } catch (java.lang.reflect.InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException(cause);
        }
    }

    private static Object invokeMapRead(CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<?,?> source, String key) throws Exception {
        Method method = CohortTerminalTelemetryStudy.class.getDeclaredMethod(
            "originalDtoMapRead", CohortTerminalTelemetryStudy.SetupMeter.class,
            Map.class, String.class);
        method.setAccessible(true);
        return CohortTerminalTelemetryStudy.invoke(method, null, meter, source, key);
    }

    private static Object invokeFieldRead(CohortTerminalTelemetryStudy.SetupMeter meter,
            Object source, String name) throws Exception {
        Method method = CohortTerminalTelemetryStudy.class.getDeclaredMethod(
            "r7ReadObject", CohortTerminalTelemetryStudy.SetupMeter.class,
            Object.class, String.class);
        method.setAccessible(true);
        return CohortTerminalTelemetryStudy.invoke(method, null, meter, source, name);
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
            if (row.open) {
                long groupStarted = number(raw, "groupStarted").longValue();
                row.openGroupElapsedNanos =
                    elapsed(groupStarted);
            }
            if (row.units < 0L || row.copiedEntries < 0L ||
                    row.maxGroupNanos < 0L || row.copyChunks < 0 ||
                    row.groups < 0 || row.priorGroups < 0 ||
                    row.groupUnits < 0 || row.maxGroupUnits < 0 ||
                    row.operationNanos < 0L || row.totalElapsedNanos < 0L ||
                    row.openGroupElapsedNanos < 0L) {
                row.status = "FAIL"; row.reason = "UNEXPECTED_FAILURE";
                row.error = "CANARY_NEGATIVE_LEDGER"; row.discarded = true;
            }
        } catch (Throwable failure) {
            row.status = "FAIL"; row.reason = "UNEXPECTED_FAILURE";
            row.error = errorText(failure); row.discarded = true;
        }
    }

    private static Number number(Object source, String name) throws Exception {
        Object value = CohortTerminalTelemetryStudy.field(source, name);
        check(value instanceof Number, "METER_FIELD_" + name);
        return (Number) value;
    }

    private static boolean booleanField(Object source, String name) throws Exception {
        Object value = CohortTerminalTelemetryStudy.field(source, name);
        check(value instanceof Boolean, "METER_FIELD_" + name);
        return ((Boolean) value).booleanValue();
    }

    private static long units(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception { return number(meter.meter, "units").longValue(); }

    private static long copies(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception { return number(meter.meter, "copiedEntries").longValue(); }

    private static int chunks(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception { return number(meter.meter, "copyChunks").intValue(); }

    private static int groups(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception { return number(meter.meter, "groups").intValue(); }

    private static int priorGroups(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception { return number(meter.meter, "priorGroups").intValue(); }

    private static boolean isOpen(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception { return booleanField(meter.meter, "open"); }

    private static void ledger(CohortTerminalTelemetryStudy.SetupMeter meter,
            long expectedUnits, long expectedCopies, int expectedChunks,
            int expectedGroups, long expectedGroupUnits, long expectedMaxGroupUnits,
            boolean expectedOpen) throws Exception {
        check(units(meter) == expectedUnits, "LEDGER_UNITS");
        check(copies(meter) == expectedCopies, "LEDGER_COPIES");
        check(chunks(meter) == expectedChunks, "LEDGER_CHUNKS");
        check(groups(meter) == expectedGroups, "LEDGER_GROUPS");
        check(priorGroups(meter) == 0, "LEDGER_PRIOR_GROUPS");
        check(number(meter.meter, "groupUnits").longValue() == expectedGroupUnits,
            "LEDGER_GROUP_UNITS");
        check(number(meter.meter, "maxGroupUnits").longValue() == expectedMaxGroupUnits,
            "LEDGER_MAX_GROUP_UNITS");
        check(isOpen(meter) == expectedOpen, "LEDGER_OPEN");
    }

    private static void bounds(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        check(units(meter) >= 0L && copies(meter) >= 0L && chunks(meter) >= 0,
            "METER_NEGATIVE");
        check(groups(meter) >= 0 && groups(meter) <= GROUP_LIMIT &&
            priorGroups(meter) >= 0 && priorGroups(meter) <= GROUP_LIMIT,
            "METER_GROUP_LIMIT");
        check(number(meter.meter, "groupUnits").longValue() >= 0L &&
            number(meter.meter, "groupUnits").longValue() <= GROUP_UNITS_LIMIT &&
            number(meter.meter, "maxGroupUnits").longValue() >= 0L &&
            number(meter.meter, "maxGroupUnits").longValue() <= GROUP_UNITS_LIMIT,
            "METER_GROUP_UNITS_LIMIT");
        check(number(meter.meter, "maxGroupNanos").longValue() >= 0L &&
            number(meter.meter, "maxGroupNanos").longValue() <= MAX_GROUP_NANOS,
            "METER_GROUP_TIME_LIMIT");
    }

    private static long elapsed(long started) {
        long value = System.nanoTime() - started;
        if (value < 0L) throw new IllegalStateException("CANARY_NEGATIVE_CLOCK");
        return value;
    }

    private static void check(boolean condition, String code) throws IOException {
        if (!condition) throw new IOException(code);
    }

    private static String errorText(Throwable failure) {
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
        StringBuilder out = new StringBuilder(4096);
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
            json = "{\"schema\":\"TSJ_ORIGINAL_DTO_METER_CANARY_R1\",\"mode\":\"\",\"status\":\"FAIL\",\"names\":[],\"cases\":[],\"parentConstructions\":0,\"error\":\"CANARY_OUTPUT_BOUND\"}";
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
        longField(out, "sourceReads", row.sourceReads);
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
