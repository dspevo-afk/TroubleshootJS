package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-bound V2 projection seam; this is not a normal host/assembly entry point. */
final class CurrentV2InputSetup {
    interface Factory {
        String schemaTag() throws Exception;
        String contextSha256() throws Exception;
        int dataEntries() throws Exception;
        Map<String,Object> dataJson() throws Exception;
    }

    private CurrentV2InputSetup() { }

    static CohortTerminalTelemetryStudy.SetupResult read(
            CohortTerminalTelemetryStudy.SetupMeter m, long producerStarted,
            String expectedContextSha, Factory factory) throws Exception {
        if (m == null || m.parent == null || m.meter == null ||
                expectedContextSha == null || factory == null) fail("ARGUMENTS");
        fresh(m, producerStarted);
        m.charge(1);
        int length = expectedContextSha.length();
        if (length != 64) fail("CONTEXT_SHA");
        for (int i = 0; i < length; i++) {
            m.charge(1);
            char c = expectedContextSha.charAt(i);
            if (!lowerHex(c)) fail("CONTEXT_SHA");
        }
        m.charge(1); String schema = factory.schemaTag();
        m.charge(1);
        if (!"tsj-cohort-terminal-input-context-v2".equals(schema)) fail("SCHEMA");
        m.charge(1); String context = factory.contextSha256();
        m.charge(1);
        if (!expectedContextSha.equals(context)) fail("CONTEXT_SHA");
        m.charge(1); int declared = factory.dataEntries();
        m.charge(1);
        if (declared != 271) fail("ENTRIES");
        m.charge(290);
        m.copy(271);
        m.charge(1); Map<String,Object> data = factory.dataJson();
        m.charge(3);
        m.copy(202);
        State s = new State(m);
        walk(s, data, 0);
        validate(s, data);
        s.u(1);
        if (s.inventory != declared) fail("SCHEMA_SHAPE");
        m.charge(1);
        m.copy(5);
        m.charge(4);
        m.charge(1);
        m.finish();
        // These are the only four prepaid native reads after close.
        long units = ((Long)CohortTerminalTelemetryStudy.field(m.meter, "units")).longValue();
        long copied = ((Long)CohortTerminalTelemetryStudy.field(m.meter, "copiedEntries")).longValue();
        int groups = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "groups")).intValue();
        int chunks = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "copyChunks")).intValue();
        return new CohortTerminalTelemetryStudy.SetupResult(data, units, copied, groups, chunks);
    }

    private static void fresh(CohortTerminalTelemetryStudy.SetupMeter m,
            long producerStarted) throws Exception {
        m.charge(18);
        long started = ((Long)CohortTerminalTelemetryStudy.field(m.meter, "started")).longValue();
        long units = ((Long)CohortTerminalTelemetryStudy.field(m.meter, "units")).longValue();
        int groups = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "groups")).intValue();
        long copied = ((Long)CohortTerminalTelemetryStudy.field(m.meter, "copiedEntries")).longValue();
        int chunks = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "copyChunks")).intValue();
        int prior = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "priorGroups")).intValue();
        int allowance = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "copyAllowance")).intValue();
        boolean open = ((Boolean)CohortTerminalTelemetryStudy.field(m.meter, "open")).booleanValue();
        int groupUnits = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "groupUnits")).intValue();
        long maxNanos = ((Long)CohortTerminalTelemetryStudy.field(m.meter, "maxGroupNanos")).longValue();
        int maxUnits = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "maxGroupUnits")).intValue();
        int inFlight = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "inFlightOwnerGroups")).intValue();
        boolean ownerStarted = ((Boolean)CohortTerminalTelemetryStudy.field(m.meter, "originalOwnerStarted")).booleanValue();
        boolean ownerActive = ((Boolean)CohortTerminalTelemetryStudy.field(m.meter, "originalOwnerActive")).booleanValue();
        boolean ownerAdopted = ((Boolean)CohortTerminalTelemetryStudy.field(m.meter, "originalOwnerAdopted")).booleanValue();
        Object measures = CohortTerminalTelemetryStudy.field(m.meter, "groupMeasures");
        int measureCount = ((List<?>)measures).size();
        int constructors = ((Integer)CohortTerminalTelemetryStudy.field(m.meter, "sessionConstructorEntries")).intValue();
        if (started != producerStarted) fail("START");
        if (units != 18L || groups != 0 || copied != 0L || chunks != 0 || prior != 0 ||
                allowance != 1048576 || !open || groupUnits != 18 || maxNanos != 0L ||
                maxUnits != 0 || inFlight != 0 || ownerStarted || ownerActive ||
                ownerAdopted || measureCount != 0 || constructors != 0) fail("METER_STATE");
    }

    /** Exactly three allocated controls, including this state. No projection clone. */
    private static final class State {
        final CohortTerminalTelemetryStudy.SetupMeter m;
        final IdentityHashMap<Object,Boolean> seen = new IdentityHashMap<Object,Boolean>();
        final HashSet<String> unique = new HashSet<String>();
        int visits, inventory, admissions;
        State(CohortTerminalTelemetryStudy.SetupMeter m) { this.m = m; }
        void u(long n) throws Exception { m.charge(n); }
        void visit() throws Exception {
            u(1); if (visits >= 1000000) fail("BOUNDS"); visits++;
        }
        void edge() throws Exception {
            u(1); if (inventory >= 4096) fail("BOUNDS"); inventory++;
        }
        void slot() throws Exception {
            u(1); if (admissions >= 202) fail("BOUNDS"); admissions++;
        }
    }

    private static boolean lowerHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    /** This frozen generated profile admits exact LinkedHashMap/ArrayList only. */
    private static int kind(State s, Object value) throws Exception {
        s.u(1);
        if (value == null) return 0;
        Class<?> type = value.getClass();
        if (type == String.class) return 1;
        if (type == Integer.class) return 2;
        if (type == Boolean.class) return 3;
        if (type == LinkedHashMap.class) return 4;
        if (type == ArrayList.class) return 5;
        fail("JSON_DOMAIN"); return -1;
    }

    private static int down(State s, int depth) throws Exception {
        s.u(1); if (depth == Integer.MAX_VALUE) fail("BOUNDS"); return depth + 1;
    }

    private static void ascii(State s, String value) throws Exception {
        s.u(1); int n = value.length();
        if (n > 256) fail("JSON_DOMAIN");
        for (int i = 0; i < n; i++) {
            s.u(1); char c = value.charAt(i);
            if (c < 32 || c > 126) fail("JSON_DOMAIN");
        }
    }

    private static void walk(State s, Object value, int depth) throws Exception {
        s.visit();
        s.u(1); if (depth > 32) fail("BOUNDS");
        int k = kind(s, value);
        if (k == 1) { ascii(s, (String)value); return; }
        if (k < 4) return;
        s.slot();
        s.u(1); Object duplicate = s.seen.put(value, Boolean.TRUE);
        s.u(1); if (duplicate != null) fail("ALIAS");
        s.edge();
        if (k == 4) {
            Map<?,?> map = (Map<?,?>)value;
            s.u(1); int n = map.size();
            s.u(1); if (n > 4096) fail("BOUNDS");
            s.u(1); java.util.Set<? extends Map.Entry<?,?>> entries = map.entrySet();
            s.u(1); Iterator<? extends Map.Entry<?,?>> it = entries.iterator();
            while (true) {
                s.u(1); if (!it.hasNext()) break;
                s.u(1); Map.Entry<?,?> entry = it.next();
                s.u(1); Object key = entry.getKey();
                s.u(1); if (!(key instanceof String)) fail("JSON_DOMAIN");
                walk(s, key, down(s, depth));
                s.edge();
                s.u(1); Object child = entry.getValue();
                walk(s, child, down(s, depth));
            }
        } else {
            List<?> list = (List<?>)value;
            s.u(1); int n = list.size();
            s.u(1); if (n > 4096) fail("BOUNDS");
            s.u(1); Iterator<?> it = list.iterator();
            while (true) {
                s.u(1); if (!it.hasNext()) break;
                s.edge();
                s.u(1); Object child = it.next();
                walk(s, child, down(s, depth));
            }
        }
    }

    private static Object get(State s, Map<?,?> map, String key) throws Exception {
        s.u(1); return map.get(key);
    }

    private static Object at(State s, List<?> list, int index) throws Exception {
        s.u(1); return list.get(index);
    }

    private static Map<?,?> shape(State s, Object value, int schema) throws Exception {
        s.u(1);
        if (value == null || value.getClass() != LinkedHashMap.class) fail("SCHEMA_SHAPE");
        Map<?,?> map = (Map<?,?>)value;
        s.u(1); int n = map.size();
        s.u(1); if (n != keyCount(schema)) fail("SCHEMA_SHAPE");
        s.u(1); java.util.Set<? extends Map.Entry<?,?>> entries = map.entrySet();
        s.u(1); Iterator<? extends Map.Entry<?,?>> it = entries.iterator();
        int index = 0;
        while (true) {
            s.u(1); if (!it.hasNext()) break;
            s.u(1); Map.Entry<?,?> entry = it.next();
            s.u(1); Object key = entry.getKey();
            s.u(1); if (!(key instanceof String)) fail("SCHEMA_SHAPE");
            s.u(1); if (!key(schema, index).equals(key)) fail("SCHEMA_SHAPE");
            index++;
        }
        return map;
    }

    private static int keyCount(int schema) {
        switch (schema) {
            case 0: return 5;
            case 1: return 10;
            case 2: return 5;
            case 3: return 8;
            case 4: return 9;
            case 5: return 10;
            case 6: return 3;
            case 7: return 8;
            case 8: return 3;
            default: throw new AssertionError("schema selector");
        }
    }

    /** Application key metadata uses switches, with no allocated key arrays. */
    private static String key(int schema, int index) {
        switch (schema) {
            case 0: switch (index) {
                case 0:return "prior"; case 1:return "capture"; case 2:return "ownerSettings";
                case 3:return "seedValues"; case 4:return "sourceIdentity";
            } break;
            case 1: switch (index) {
                case 0:return "sourceParent"; case 1:return "manifestSha256";
                case 2:return "sourceManifestSha256"; case 3:return "readerSha256";
                case 4:return "resultSha256"; case 5:return "operation1Sha256";
                case 6:return "parentReceiptSha256"; case 7:return "priorRunId";
                case 8:return "priorOriginalReceipt"; case 9:return "trialDisposition";
            } break;
            case 2: switch (index) {
                case 0:return "receiptPath"; case 1:return "receiptBytes"; case 2:return "receiptSha256";
                case 3:return "receiptOrdinal"; case 4:return "lineageOrdinal";
            } break;
            case 3: switch (index) {
                case 0:return "status"; case 1:return "reason"; case 2:return "capturedFailedNetId";
                case 3:return "controlFailedNetId"; case 4:return "cohortNetIds";
                case 5:return "requestedNetIds"; case 6:return "orderChanged";
                case 7:return "feedbackValidated";
            } break;
            case 4: switch (index) {
                case 0:return "cohortNetIds"; case 1:return "capturedNetIds";
                case 2:return "trialDisposition"; case 3:return "controlDisposition";
                case 4:return "trialOutcome"; case 5:return "passIndex"; case 6:return "failedNetId";
                case 7:return "priorPromotedNetId"; case 8:return "liveSearchIndex";
            } break;
            case 5: switch (index) {
                case 0:return "boardMode"; case 1:return "maxExpansions";
                case 2:return "expandedAtFailure"; case 3:return "maxVias"; case 4:return "viaCost";
                case 5:return "maxTransitions"; case 6:return "branchStrategy";
                case 7:return "netStrategy"; case 8:return "roleWidthsEnabled";
                case 9:return "orderingStreamEnabled";
            } break;
            case 6: switch (index) {
                case 0:return "rootSeed"; case 1:return "placementSeed"; case 2:return "routingSeed";
            } break;
            case 7: switch (index) {
                case 0:return "geometryBeforeSha256"; case 1:return "geometryAfterSha256";
                case 2:return "detachedGeometrySha256"; case 3:return "effectsBeforeSha256";
                case 4:return "effectsAfterSha256"; case 5:return "traceCount";
                case 6:return "holeCount"; case 7:return "padLandIdentity";
            } break;
            case 8: switch (index) {
                case 0:return "format"; case 1:return "rowCount"; case 2:return "sha256";
            } break;
            default: break;
        }
        throw new AssertionError("key selector");
    }

    private static String text(State s, Object value) throws Exception {
        s.u(1); if (!(value instanceof String)) fail("POLICY");
        return (String)value;
    }

    private static void fixed(State s, Object value, String expected) throws Exception {
        String actual = text(s, value);
        s.u(1); if (!expected.equals(actual)) fail("POLICY");
    }

    private static void hex(State s, Object value, int width) throws Exception {
        String actual = text(s, value);
        s.u(1); int n = actual.length();
        s.u(1); if (n != width) fail("POLICY");
        for (int i = 0; i < n; i++) {
            s.u(1); char c = actual.charAt(i);
            if (!lowerHex(c)) fail("POLICY");
        }
    }

    private static void uuid(State s, Object value) throws Exception {
        String actual = text(s, value);
        s.u(1); int n = actual.length();
        s.u(1); if (n != 36) fail("POLICY");
        for (int i = 0; i < n; i++) {
            s.u(1); char c = actual.charAt(i);
            if (i == 8 || i == 13 || i == 18 || i == 23) {
                if (c != '-') fail("POLICY");
            } else if (!lowerHex(c)) fail("POLICY");
        }
    }

    private static void integer(State s, Object value, int min, int max, int expected)
            throws Exception {
        s.u(1); if (!(value instanceof Integer)) fail("POLICY");
        s.u(1); int actual = ((Integer)value).intValue();
        s.u(1); if (actual < min || actual > max || actual != expected) fail("POLICY");
    }

    private static void positiveInt(State s, Object value) throws Exception {
        s.u(1); if (!(value instanceof Integer)) fail("POLICY");
        s.u(1); int actual = ((Integer)value).intValue();
        s.u(1); if (actual < 1) fail("POLICY");
    }

    private static void bool(State s, Object value, boolean requireTrue) throws Exception {
        s.u(1); if (!(value instanceof Boolean)) fail("POLICY");
        s.u(1); boolean actual = ((Boolean)value).booleanValue();
        s.u(1); if (requireTrue && !actual) fail("POLICY");
    }

    private static List<?> unique(State s, Object value, int expected) throws Exception {
        s.u(1); if (value == null || value.getClass() != ArrayList.class) fail("POLICY");
        List<?> list = (List<?>)value;
        s.u(1); int n = list.size();
        s.u(1); if (n != expected) fail("POLICY");
        s.u(1); s.unique.clear();
        for (int i = 0; i < n; i++) {
            String item = text(s, at(s, list, i));
            s.u(1); int length = item.length();
            s.u(1); if (length == 0) fail("POLICY");
            s.slot();
            s.u(1); boolean added = s.unique.add(item);
            s.u(1); if (!added) fail("POLICY");
        }
        return list;
    }

    private static void member(State s, Object item) throws Exception {
        s.u(1); boolean present = s.unique.contains(item);
        s.u(1); if (!present) fail("POLICY");
    }

    private static void cohort(State s, Object value) throws Exception {
        List<?> list = unique(s, value, 2);
        s.u(1); if (!"A_COIL_LOW".equals(at(s, list, 0))) fail("POLICY");
        s.u(1); if (!"B_COIL_LOW".equals(at(s, list, 1))) fail("POLICY");
    }

    private static List<?> sixty(State s, Object value) throws Exception {
        List<?> list = unique(s, value, 60);
        member(s, "A_COIL_LOW"); member(s, "B_COIL_LOW");
        return list;
    }

    private static Map<?,?> trial(State s, Object value) throws Exception {
        Map<?,?> trial = shape(s, value, 3);
        fixed(s, get(s, trial, "status"), "RUN");
        fixed(s, get(s, trial, "reason"), "TRIAL_EXECUTED");
        fixed(s, get(s, trial, "capturedFailedNetId"), "A_COIL_LOW");
        fixed(s, get(s, trial, "controlFailedNetId"), "B_COIL_LOW");
        cohort(s, get(s, trial, "cohortNetIds"));
        sixty(s, get(s, trial, "requestedNetIds"));
        // RUN/TRIAL_EXECUTED is emitted only for a changed requested order.
        bool(s, get(s, trial, "orderChanged"), true);
        bool(s, get(s, trial, "feedbackValidated"), true);
        return trial;
    }

    /** Both operands have already passed strict field/list type checks. */
    private static void sameList(State s, List<?> left, List<?> right) throws Exception {
        s.u(1); int n = left.size();
        s.u(1); int other = right.size();
        s.u(1); if (n != other) fail("POLICY");
        for (int i = 0; i < n; i++) {
            Object a = at(s, left, i), b = at(s, right, i);
            s.u(1); if (!a.equals(b)) fail("POLICY");
        }
    }

    /** Explicit fixed fields: never invoke recursive Map.equals. */
    private static void sameTrial(State s, Map<?,?> left, Map<?,?> right) throws Exception {
        for (int i = 0; i < 4; i++) {
            Object a = get(s, left, key(3, i)), b = get(s, right, key(3, i));
            s.u(1); if (!a.equals(b)) fail("POLICY");
        }
        for (int i = 4; i < 6; i++) {
            List<?> a = (List<?>)get(s, left, key(3, i));
            List<?> b = (List<?>)get(s, right, key(3, i));
            sameList(s, a, b);
        }
        for (int i = 6; i < 8; i++) {
            Object a = get(s, left, key(3, i)), b = get(s, right, key(3, i));
            s.u(1); boolean av = ((Boolean)a).booleanValue();
            s.u(1); boolean bv = ((Boolean)b).booleanValue();
            s.u(1); if (av != bv) fail("POLICY");
        }
    }

    private static void validate(State s, Map<String,Object> data) throws Exception {
        Map<?,?> root = shape(s, data, 0);
        Map<?,?> prior = shape(s, get(s, root, "prior"), 1);
        hex(s, get(s, prior, "sourceParent"), 40);
        for (int i = 1; i < 7; i++) hex(s, get(s, prior, key(1, i)), 64);
        uuid(s, get(s, prior, "priorRunId"));
        Map<?,?> receipt = shape(s, get(s, prior, "priorOriginalReceipt"), 2);
        fixed(s, get(s, receipt, "receiptPath"), "op-1.jsonl");
        positiveInt(s, get(s, receipt, "receiptBytes"));
        Object receiptSha = get(s, receipt, "receiptSha256");
        hex(s, receiptSha, 64);
        Object operationSha = get(s, prior, "operation1Sha256");
        s.u(1); if (!receiptSha.equals(operationSha)) fail("POLICY");
        integer(s, get(s, receipt, "receiptOrdinal"), 307, 307, 307);
        integer(s, get(s, receipt, "lineageOrdinal"), 77, 77, 77);
        Map<?,?> priorTrial = trial(s, get(s, prior, "trialDisposition"));

        Map<?,?> capture = shape(s, get(s, root, "capture"), 4);
        Object captureCohort = get(s, capture, "cohortNetIds");
        cohort(s, captureCohort);
        Map<?,?> captureTrial = trial(s, get(s, capture, "trialDisposition"));
        sameTrial(s, priorTrial, captureTrial);
        sameList(s, (List<?>)captureCohort,
                (List<?>)get(s, captureTrial, "cohortNetIds"));
        fixed(s, get(s, capture, "controlDisposition"), "NO_PATH");
        fixed(s, get(s, capture, "trialOutcome"), "CAP_REJECTED");
        integer(s, get(s, capture, "passIndex"), 2, 2, 2);
        Object failed = get(s, capture, "failedNetId");
        fixed(s, failed, "A_COIL_LOW");
        Object promoted = get(s, capture, "priorPromotedNetId");
        fixed(s, promoted, "D_SENSE");
        integer(s, get(s, capture, "liveSearchIndex"), 44, 44, 44);
        List<?> captured = sixty(s, get(s, capture, "capturedNetIds"));
        s.u(1); if (!failed.equals(at(s, captured, 43))) fail("POLICY");
        member(s, promoted);

        Map<?,?> owner = shape(s, get(s, root, "ownerSettings"), 5);
        fixed(s, get(s, owner, "boardMode"), "FULLER_TWO_LAYER");
        integer(s, get(s, owner, "maxExpansions"), 1, 1000000, 1000000);
        integer(s, get(s, owner, "expandedAtFailure"), 0, 1000000, 918222);
        integer(s, get(s, owner, "maxVias"), 0, 48, 48);
        integer(s, get(s, owner, "viaCost"), 0, 1000000, 60);
        integer(s, get(s, owner, "maxTransitions"), 0, 4, 4);
        fixed(s, get(s, owner, "branchStrategy"), "CANONICAL_NEAREST");
        fixed(s, get(s, owner, "netStrategy"), "CANONICAL");
        bool(s, get(s, owner, "roleWidthsEnabled"), true);
        bool(s, get(s, owner, "orderingStreamEnabled"), true);
        Map<?,?> seeds = shape(s, get(s, root, "seedValues"), 6);
        // Bounded exact equality establishes canonical signed-long syntax/range too.
        fixed(s, get(s, seeds, "rootSeed"), "9007199254740993");
        fixed(s, get(s, seeds, "placementSeed"), "-6587958367974952701");
        fixed(s, get(s, seeds, "routingSeed"), "8445301674744508279");
        Map<?,?> identity = shape(s, get(s, root, "sourceIdentity"), 7);
        for (int i = 0; i < 5; i++) hex(s, get(s, identity, key(7, i)), 64);
        integer(s, get(s, identity, "traceCount"), 0, 0, 0);
        integer(s, get(s, identity, "holeCount"), 0, 0, 0);
        Map<?,?> pad = shape(s, get(s, identity, "padLandIdentity"), 8);
        fixed(s, get(s, pad, "format"), "TSJ_PAD_LAND_ASCII_V1");
        integer(s, get(s, pad, "rowCount"), 246, 246, 246);
        fixed(s, get(s, pad, "sha256"),
                "541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0");
    }

    private static void fail(String code) throws IOException {
        throw new IOException("V2_SETUP_" + code);
    }
}
