package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic component canary. It does not instantiate an owner or enter normal assembly. */
public final class CurrentV2InputSetupCanary {
    private static final String SCHEMA = "tsj-cohort-terminal-input-context-v2";
    private static final String[] ROOT_KEYS = {
        "prior", "capture", "ownerSettings", "seedValues", "sourceIdentity"
    };
    private static final String[] PRIOR_KEYS = {
        "sourceParent", "manifestSha256", "sourceManifestSha256", "readerSha256",
        "resultSha256", "operation1Sha256", "parentReceiptSha256", "priorRunId",
        "priorOriginalReceipt", "trialDisposition"
    };
    private static final String[] RECEIPT_KEYS = {
        "receiptPath", "receiptBytes", "receiptSha256", "receiptOrdinal", "lineageOrdinal"
    };
    private static final String[] TRIAL_KEYS = {
        "status", "reason", "capturedFailedNetId", "controlFailedNetId", "cohortNetIds",
        "requestedNetIds", "orderChanged", "feedbackValidated"
    };
    private static final String[] CAPTURE_KEYS = {
        "cohortNetIds", "capturedNetIds", "trialDisposition", "controlDisposition",
        "trialOutcome", "passIndex", "failedNetId", "priorPromotedNetId", "liveSearchIndex"
    };
    private static final String[] OWNER_KEYS = {
        "boardMode", "maxExpansions", "expandedAtFailure", "maxVias", "viaCost",
        "maxTransitions", "branchStrategy", "netStrategy", "roleWidthsEnabled",
        "orderingStreamEnabled"
    };
    private static final String[] SEED_KEYS = {"rootSeed", "placementSeed", "routingSeed"};
    private static final String[] IDENTITY_KEYS = {
        "geometryBeforeSha256", "geometryAfterSha256", "detachedGeometrySha256",
        "effectsBeforeSha256", "effectsAfterSha256", "traceCount", "holeCount",
        "padLandIdentity"
    };
    private static final String[] PAD_KEYS = {"format", "rowCount", "sha256"};
    private static final String[][] STRICT_MAP_PATHS = {
        {}, {"prior"}, {"prior", "priorOriginalReceipt"}, {"prior", "trialDisposition"},
        {"capture"}, {"capture", "trialDisposition"}, {"ownerSettings"}, {"seedValues"},
        {"sourceIdentity"}, {"sourceIdentity", "padLandIdentity"}
    };
    private static final String[] STRICT_MAP_LABELS = {
        "root", "prior", "receipt", "priorTrial", "capture", "captureTrial",
        "ownerSettings", "seedValues", "sourceIdentity", "padLandIdentity"
    };
    private static int assertions;
    private static int cases;
    private static String expectedContextHash;
    private static CohortTerminalTelemetryStudy.Parent parent;

    private CurrentV2InputSetupCanary() { }

    private static void need(boolean condition, String code) {
        assertions++;
        if (!condition) throw new AssertionError(code);
    }

    private interface Mutation {
        void apply(Map<String,Object> data);
    }

    private static final class FactorySpy implements CurrentV2InputSetup.Factory {
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final String schemaReply;
        final String hashReply;
        final Integer entriesReply;
        final Mutation mutation;
        final boolean checkPrepayment;
        final boolean forbidDataJson;
        boolean nullSchemaReply;
        boolean nullHashReply;
        int schemaCalls;
        int hashCalls;
        int entriesCalls;
        int dataJsonCalls;
        String schemaObserved;
        String hashObserved;
        int entriesObserved;
        Map<String,Object> returned;

        FactorySpy(CohortTerminalTelemetryStudy.SetupMeter meter, String schemaReply,
                String hashReply, Integer entriesReply, Mutation mutation,
                boolean checkPrepayment, boolean forbidDataJson) {
            this.meter=meter;
            this.schemaReply=schemaReply;
            this.hashReply=hashReply;
            this.entriesReply=entriesReply;
            this.mutation=mutation;
            this.checkPrepayment=checkPrepayment;
            this.forbidDataJson=forbidDataJson;
        }

        public String schemaTag() {
            schemaCalls++;
            schemaObserved=nullSchemaReply ? null
                : schemaReply==null ? CohortTerminalTelemetryInputV2.schemaTag() : schemaReply;
            return schemaObserved;
        }

        public String contextSha256() {
            hashCalls++;
            hashObserved=nullHashReply ? null
                : hashReply==null ? CohortTerminalTelemetryInputV2.contextSha256() : hashReply;
            return hashObserved;
        }

        public int dataEntries() {
            entriesCalls++;
            entriesObserved=entriesReply==null ? CohortTerminalTelemetryInputV2.dataEntries()
                : entriesReply.intValue();
            return entriesObserved;
        }

        public Map<String,Object> dataJson() throws Exception {
            dataJsonCalls++;
            need(!forbidDataJson, "HEADER_REJECTION_CALLED_DATA_JSON");
            if (checkPrepayment) checkPreDataJsonMeter(meter);
            Map<String,Object> data=CohortTerminalTelemetryInputV2.dataJson();
            if (mutation!=null) mutation.apply(data);
            returned=data;
            return data;
        }
    }

    private static final class Success {
        final Map<String,Object> data;
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final int groups;
        Success(Map<String,Object> data, CohortTerminalTelemetryStudy.SetupMeter meter,
                int groups) {
            this.data=data;
            this.meter=meter;
            this.groups=groups;
        }
    }

    private static final class WalkStats {
        int containers;
        int entries;
        long visits;
    }

    private static final class NegativeCase {
        final String name;
        final String code;
        final Mutation mutation;
        NegativeCase(String name, String code, Mutation mutation) {
            this.name=name;
            this.code=code;
            this.mutation=mutation;
        }
    }

    private static CohortTerminalTelemetryStudy.SetupMeter newMeter(long started)
            throws Exception {
        return new CohortTerminalTelemetryStudy.SetupMeter(parent, parent.meter(started));
    }

    private static Object meterField(CohortTerminalTelemetryStudy.SetupMeter meter, String name)
            throws Exception {
        return CohortTerminalTelemetryStudy.field(meter.meter, name);
    }

    private static long longField(CohortTerminalTelemetryStudy.SetupMeter meter, String name)
            throws Exception {
        Object value=meterField(meter,name);
        need(value!=null && value.getClass()==Long.class, "METER_LONG_"+name);
        return ((Long)value).longValue();
    }

    private static int intField(CohortTerminalTelemetryStudy.SetupMeter meter, String name)
            throws Exception {
        Object value=meterField(meter,name);
        need(value!=null && value.getClass()==Integer.class, "METER_INT_"+name);
        return ((Integer)value).intValue();
    }

    private static boolean boolField(CohortTerminalTelemetryStudy.SetupMeter meter, String name)
            throws Exception {
        Object value=meterField(meter,name);
        need(value!=null && value.getClass()==Boolean.class, "METER_BOOLEAN_"+name);
        return ((Boolean)value).booleanValue();
    }

    private static void checkPreDataJsonMeter(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        need(longField(meter,"units")==651L, "PRE_DATA_JSON_UNITS_18_72_290_271");
        need(longField(meter,"copiedEntries")==271L, "PRE_DATA_JSON_COPIES");
        need(intField(meter,"copyChunks")==1, "PRE_DATA_JSON_CHUNKS");
        need(boolField(meter,"open"), "PRE_DATA_JSON_OPEN");
    }

    private static String expectedContextSha() {
        return expectedContextHash;
    }

    private static Success positive(long expectedUnits) throws Exception {
        long started=System.nanoTime();
        CohortTerminalTelemetryStudy.SetupMeter meter=newMeter(started);
        FactorySpy factory=new FactorySpy(meter,null,null,null,null,true,false);
        CohortTerminalTelemetryStudy.SetupResult result=CurrentV2InputSetup.read(
            meter,started,expectedContextSha(),factory);
        cases++;
        need(factory.schemaCalls==1 && factory.hashCalls==1 && factory.entriesCalls==1
            && factory.dataJsonCalls==1, "POSITIVE_FACTORY_CALLS_ONCE");
        need(SCHEMA.equals(factory.schemaObserved), "POSITIVE_SCHEMA_LITERAL");
        need(factory.hashObserved!=null && lowerHex(factory.hashObserved,64),
            "POSITIVE_CONTEXT_HASH_FORMAT");
        need(factory.hashObserved.equals(expectedContextSha()), "POSITIVE_CONTEXT_HASH_BINDING");
        need(factory.entriesObserved==271, "POSITIVE_FACTORY_ENTRY_COUNT");
        need(factory.returned!=null && result.data==factory.returned,
            "RESULT_RETAINS_EXACT_FACTORY_MAP");
        need(result.units==expectedUnits, "EXPECTED_SUCCESSFUL_UNITS");
        need(result.copiedEntries==478L, "RESULT_COPIED_ENTRIES");
        need(result.copyChunks==3, "RESULT_COPY_CHUNKS");
        need(result.groups>0, "RESULT_ACTUAL_GROUPS_POSITIVE");
        need(longField(meter,"units")==result.units, "CLOSED_UNITS_MATCH_RESULT");
        need(longField(meter,"copiedEntries")==result.copiedEntries,
            "CLOSED_COPIES_MATCH_RESULT");
        need(intField(meter,"groups")==result.groups, "CLOSED_GROUPS_MATCH_RESULT");
        need(intField(meter,"copyChunks")==result.copyChunks, "CLOSED_CHUNKS_MATCH_RESULT");
        need(!boolField(meter,"open"), "POSITIVE_METER_CLOSED");
        need(intField(meter,"groupUnits")>0, "FINISHED_GROUP_UNITS_RETAINED");
        need(intField(meter,"copyAllowance")==1048576, "DEFAULT_COPY_ALLOWANCE_RETAINED");
        need(longField(meter,"started")==started, "SIGNED_NANOTIME_ORIGIN_PRESERVED");
        need(intField(meter,"sessionConstructorEntries")==0, "NO_SESSION_CONSTRUCTOR");
        need(intField(meter,"priorGroups")==0, "NO_PRIOR_GROUPS");
        need(intField(meter,"inFlightOwnerGroups")==0, "NO_IN_FLIGHT_OWNER_GROUPS");
        need(!boolField(meter,"originalOwnerStarted")
            && !boolField(meter,"originalOwnerActive")
            && !boolField(meter,"originalOwnerAdopted"), "NO_ORIGINAL_OWNER_FLAGS");
        verifyExpectedProjection(result.data);
        return new Success(result.data,meter,result.groups);
    }

    private static String code(IOException failure) {
        return failure.getMessage()==null ? "" : failure.getMessage();
    }

    private static void expectFailure(String name, String expectedCode,
            CohortTerminalTelemetryStudy.SetupMeter meter, long producerStarted,
            String expectedSha, CurrentV2InputSetup.Factory factory) throws Exception {
        try {
            CurrentV2InputSetup.read(meter,producerStarted,expectedSha,factory);
            throw new AssertionError("NEGATIVE_ACCEPTED_"+name);
        } catch (IOException failure) {
            need(expectedCode.equals(code(failure)), "NEGATIVE_CODE_"+name+"_"+code(failure));
        }
        cases++;
    }

    private static void rejectHeader(String name, String expectedCode, long producerStarted,
            String schemaReply, String hashReply, Integer entriesReply,
            String expectedSha, boolean nullMeter) throws Exception {
        CohortTerminalTelemetryStudy.SetupMeter meter=nullMeter ? null : newMeter(producerStarted);
        FactorySpy factory=new FactorySpy(meter,schemaReply,hashReply,entriesReply,
            null,false,true);
        expectFailure(name,expectedCode,meter,producerStarted,expectedSha,factory);
        need(factory.dataJsonCalls==0, "HEADER_FAILS_BEFORE_TREE_"+name);
        if (nullMeter)
            need(factory.schemaCalls==0 && factory.hashCalls==0 && factory.entriesCalls==0,
                "NULL_METER_FAILS_BEFORE_FACTORY");
        if ("V2_SETUP_ENTRIES".equals(expectedCode))
            need(factory.entriesCalls==1, "ENTRY_HEADER_INSPECTED_ONCE");
    }

    private static void rejectExpectedDigest(String name, String expectedCode,
            long producerStarted, String expectedSha, boolean expectFactoryAccess)
            throws Exception {
        CohortTerminalTelemetryStudy.SetupMeter meter=newMeter(producerStarted);
        FactorySpy factory=new FactorySpy(meter,null,null,null,null,false,true);
        expectFailure(name,expectedCode,meter,producerStarted,expectedSha,factory);
        need(factory.dataJsonCalls==0, "EXPECTED_DIGEST_FAILS_BEFORE_TREE_"+name);
        if (!expectFactoryAccess)
            need(factory.schemaCalls==0 && factory.hashCalls==0 && factory.entriesCalls==0,
                "EXPECTED_DIGEST_FAILS_BEFORE_FACTORY_"+name);
    }

    private static void runHeaderCases() throws Exception {
        long started=System.nanoTime();
        rejectHeader("NULL_METER","V2_SETUP_ARGUMENTS",started,null,null,null,
            expectedContextSha(),true);
        CohortTerminalTelemetryStudy.SetupMeter stale=newMeter(started);
        FactorySpy staleFactory=new FactorySpy(stale,null,null,null,null,false,true);
        expectFailure("START_BINDING","V2_SETUP_START",stale,nextTimestamp(started),
            expectedContextSha(),staleFactory);
        need(staleFactory.schemaCalls==0 && staleFactory.hashCalls==0
            && staleFactory.entriesCalls==0 && staleFactory.dataJsonCalls==0,
            "START_FAILS_BEFORE_FACTORY");

        CohortTerminalTelemetryStudy.SetupMeter used=newMeter(started);
        used.charge(1L);
        FactorySpy usedFactory=new FactorySpy(used,null,null,null,null,false,true);
        expectFailure("NONFRESH_METER","V2_SETUP_METER_STATE",used,started,
            expectedContextSha(),usedFactory);
        need(usedFactory.schemaCalls==0 && usedFactory.hashCalls==0
            && usedFactory.entriesCalls==0 && usedFactory.dataJsonCalls==0,
            "METER_STATE_FAILS_BEFORE_FACTORY");

        CohortTerminalTelemetryStudy.SetupMeter noFactoryMeter=newMeter(started);
        expectFailure("NULL_FACTORY","V2_SETUP_ARGUMENTS",noFactoryMeter,started,
            expectedContextSha(),null);
        CohortTerminalTelemetryStudy.SetupMeter noExpectedHashMeter=newMeter(started);
        FactorySpy noExpectedHashFactory=new FactorySpy(noExpectedHashMeter,null,null,null,
            null,false,true);
        expectFailure("NULL_EXPECTED_HASH","V2_SETUP_ARGUMENTS",noExpectedHashMeter,started,
            null,noExpectedHashFactory);
        need(noExpectedHashFactory.schemaCalls==0 && noExpectedHashFactory.hashCalls==0
            && noExpectedHashFactory.entriesCalls==0 && noExpectedHashFactory.dataJsonCalls==0,
            "NULL_EXPECTED_HASH_FAILS_BEFORE_FACTORY");

        String wrongSchema="tsj-cohort-terminal-input-context-v1";
        rejectHeader("SCHEMA","V2_SETUP_SCHEMA",started,wrongSchema,null,null,
            expectedContextSha(),false);
        CohortTerminalTelemetryStudy.SetupMeter nullSchemaMeter=newMeter(started);
        FactorySpy nullSchemaFactory=new FactorySpy(nullSchemaMeter,null,null,null,
            null,false,true);
        nullSchemaFactory.nullSchemaReply=true;
        expectFailure("NULL_SCHEMA","V2_SETUP_SCHEMA",nullSchemaMeter,started,
            expectedContextSha(),nullSchemaFactory);
        need(nullSchemaFactory.dataJsonCalls==0, "NULL_SCHEMA_BEFORE_DATA_JSON");
        rejectHeader("CONTEXT_HASH","V2_SETUP_CONTEXT_SHA",started,null,
            flipFirstHex(expectedContextSha()),null,expectedContextSha(),false);
        rejectHeader("CONTEXT_HASH_LEXICAL","V2_SETUP_CONTEXT_SHA",started,null,
            repeat('A',64),null,expectedContextSha(),false);
        rejectHeader("CONTEXT_HASH_LENGTH","V2_SETUP_CONTEXT_SHA",started,null,
            repeat('a',63),null,expectedContextSha(),false);
        CohortTerminalTelemetryStudy.SetupMeter nullHashMeter=newMeter(started);
        FactorySpy nullHashFactory=new FactorySpy(nullHashMeter,null,null,null,
            null,false,true);
        nullHashFactory.nullHashReply=true;
        expectFailure("NULL_CONTEXT_HASH","V2_SETUP_CONTEXT_SHA",nullHashMeter,started,
            expectedContextSha(),nullHashFactory);
        need(nullHashFactory.dataJsonCalls==0, "NULL_CONTEXT_HASH_BEFORE_DATA_JSON");
        rejectExpectedDigest("EXPECTED_HASH_LEXICAL","V2_SETUP_CONTEXT_SHA",started,
            repeat('A',64),false);
        rejectExpectedDigest("EXPECTED_HASH_LENGTH","V2_SETUP_CONTEXT_SHA",started,
            expectedContextSha().substring(1),false);
        rejectExpectedDigest("EXPECTED_HASH_MISMATCH","V2_SETUP_CONTEXT_SHA",started,
            flipFirstHex(expectedContextSha()),true);
        rejectHeader("ENTRY_COUNT","V2_SETUP_ENTRIES",started,null,null,
            Integer.valueOf(270),expectedContextSha(),false);
        rejectHeader("ENTRY_COUNT_ZERO","V2_SETUP_ENTRIES",started,null,null,
            Integer.valueOf(0),expectedContextSha(),false);
        rejectHeader("ENTRY_COUNT_OVER_LIMIT","V2_SETUP_ENTRIES",started,null,null,
            Integer.valueOf(4097),expectedContextSha(),false);
    }

    private static long nextTimestamp(long value) {
        return value==Long.MAX_VALUE ? value-1L : value+1L;
    }

    private static String flipFirstHex(String value) {
        return (value.charAt(0)=='0' ? "1" : "0")+value.substring(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> mapAt(Map<String,Object> root, String[] path) {
        Map<String,Object> current=root;
        for (int i=0;i<path.length;i++) {
            Object next=current.get(path[i]);
            if (!(next instanceof Map)) throw new AssertionError("FIXTURE_PATH_"+path[i]);
            current=(Map<String,Object>)next;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listAt(Map<String,Object> root, String[] mapPath, String key) {
        Object value=mapAt(root,mapPath).get(key);
        if (!(value instanceof List)) throw new AssertionError("FIXTURE_LIST_"+key);
        return (List<Object>)value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> objectMap(Object value, String label) {
        need(value instanceof Map, "EXPECTED_MAP_"+label);
        return (Map<String,Object>)value;
    }

    private static final class MapShapeMutation implements Mutation {
        final String[] path;
        final int kind;
        MapShapeMutation(String[] path, int kind) { this.path=path; this.kind=kind; }
        public void apply(Map<String,Object> data) {
            Map<String,Object> target=mapAt(data,path);
            if (kind==0) {
                List<Map.Entry<String,Object>> rows=new ArrayList<Map.Entry<String,Object>>(
                    target.entrySet());
                String first=rows.get(0).getKey();
                String second=rows.get(1).getKey();
                Object firstValue=target.get(first);
                Object secondValue=target.get(second);
                LinkedHashMap<String,Object> reordered=new LinkedHashMap<String,Object>();
                reordered.put(second,secondValue);
                reordered.put(first,firstValue);
                for (int i=2;i<rows.size();i++)
                    reordered.put(rows.get(i).getKey(),rows.get(i).getValue());
                target.clear();
                target.putAll(reordered);
            } else if (kind==1) {
                String last=null;
                for (String key:target.keySet()) last=key;
                target.remove(last);
            } else {
                target.put("unexpectedField","x");
            }
        }
    }

    private static final class PutMutation implements Mutation {
        final String[] path;
        final String key;
        final Object value;
        PutMutation(String[] path, String key, Object value) {
            this.path=path; this.key=key; this.value=value;
        }
        public void apply(Map<String,Object> data) { mapAt(data,path).put(key,value); }
    }

    private static final class ListSetMutation implements Mutation {
        final String[] path;
        final String key;
        final int index;
        final Object value;
        ListSetMutation(String[] path, String key, int index, Object value) {
            this.path=path; this.key=key; this.index=index; this.value=value;
        }
        public void apply(Map<String,Object> data) {
            listAt(data,path,key).set(index,value);
        }
    }

    private static final class ListRemoveMutation implements Mutation {
        final String[] path;
        final String key;
        final int index;
        ListRemoveMutation(String[] path, String key, int index) {
            this.path=path; this.key=key; this.index=index;
        }
        public void apply(Map<String,Object> data) { listAt(data,path,key).remove(index); }
    }

    private static final class ListAppendMutation implements Mutation {
        final String[] path;
        final String key;
        final Object value;
        ListAppendMutation(String[] path, String key, Object value) {
            this.path=path; this.key=key; this.value=value;
        }
        public void apply(Map<String,Object> data) { listAt(data,path,key).add(value); }
    }

    private static final class CompoundMutation implements Mutation {
        final Mutation[] parts;
        CompoundMutation(Mutation... parts) { this.parts=parts; }
        public void apply(Map<String,Object> data) {
            for (int i=0;i<parts.length;i++) parts[i].apply(data);
        }
    }

    private static final class ArrayListVariant extends ArrayList<Object> {
        private static final long serialVersionUID=1L;
    }

    private static void runNegativeCase(NegativeCase negative) throws Exception {
        long started=System.nanoTime();
        CohortTerminalTelemetryStudy.SetupMeter meter=newMeter(started);
        FactorySpy factory=new FactorySpy(meter,null,null,null,negative.mutation,true,false);
        expectFailure(negative.name,negative.code,meter,started,expectedContextSha(),factory);
        need(factory.schemaCalls==1 && factory.hashCalls==1 && factory.entriesCalls==1,
            "NEGATIVE_HEADER_CALLS_"+negative.name);
        need(factory.dataJsonCalls==1, "NEGATIVE_TREE_CALL_ONCE_"+negative.name);
    }

    private static void runNestedShapeCases() throws Exception {
        for (int i=0;i<STRICT_MAP_PATHS.length;i++) {
            final String label=STRICT_MAP_LABELS[i];
            runNegativeCase(new NegativeCase(label+"_REORDER", "V2_SETUP_SCHEMA_SHAPE",
                new MapShapeMutation(STRICT_MAP_PATHS[i],0)));
            runNegativeCase(new NegativeCase(label+"_MISSING_KEY", "V2_SETUP_SCHEMA_SHAPE",
                new MapShapeMutation(STRICT_MAP_PATHS[i],1)));
            runNegativeCase(new NegativeCase(label+"_EXTRA_KEY", "V2_SETUP_SCHEMA_SHAPE",
                new MapShapeMutation(STRICT_MAP_PATHS[i],2)));
        }
    }

    private static String repeat(char value, int count) {
        char[] chars=new char[count];
        Arrays.fill(chars,value);
        return new String(chars);
    }

    private static Mutation deepMutation() {
        return new Mutation() {
            public void apply(Map<String,Object> data) {
                Object nested="leaf";
                for (int i=0;i<40;i++) {
                    ArrayList<Object> one=new ArrayList<Object>();
                    one.add(nested);
                    nested=one;
                }
                mapAt(data,new String[]{"capture"}).put("cohortNetIds",nested);
            }
        };
    }

    private static Mutation oversizedMutation() {
        return new Mutation() {
            public void apply(Map<String,Object> data) {
                ArrayList<Object> many=new ArrayList<Object>();
                for (int i=0;i<4097;i++) many.add(null);
                mapAt(data,new String[]{"seedValues"}).put("rootSeed",many);
            }
        };
    }

    private static Mutation aliasMutation() {
        return new Mutation() {
            public void apply(Map<String,Object> data) {
                Object priorCohort=mapAt(data,new String[]{"prior","trialDisposition"})
                    .get("cohortNetIds");
                mapAt(data,new String[]{"capture"}).put("cohortNetIds",priorCohort);
            }
        };
    }

    private static Mutation cycleMutation() {
        return new Mutation() {
            public void apply(Map<String,Object> data) {
                Map<String,Object> capture=mapAt(data,new String[]{"capture"});
                capture.put("cohortNetIds",capture);
            }
        };
    }

    private static void runValueAndPolicyCases() throws Exception {
        ArrayList<NegativeCase> negatives=new ArrayList<NegativeCase>();
        negatives.add(new NegativeCase("INTEGER_LONG", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"prior","priorOriginalReceipt"},"receiptBytes",
                Long.valueOf(4096L))));
        negatives.add(new NegativeCase("INTEGER_BOOLEAN", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"prior","priorOriginalReceipt"},"receiptBytes",
                Boolean.TRUE)));
        negatives.add(new NegativeCase("INTEGER_FLOAT", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"prior","priorOriginalReceipt"},"receiptBytes",
                Float.valueOf(4096.0f))));
        negatives.add(new NegativeCase("UNSUPPORTED_OBJECT", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"seedValues"},"rootSeed",new Object())));
        negatives.add(new NegativeCase("CUSTOM_MAP", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"seedValues"},"rootSeed",
                new java.util.HashMap<String,Object>())));
        negatives.add(new NegativeCase("LIST_SUBCLASS", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"seedValues"},"rootSeed",new ArrayListVariant())));
        negatives.add(new NegativeCase("EMPTY_STRING", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"prior"},"sourceParent","")));
        negatives.add(new NegativeCase("NON_ASCII_STRING", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"prior"},"sourceParent","caf\u00e9")));
        negatives.add(new NegativeCase("CONTROL_CHAR_STRING", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"prior"},"sourceParent",
                new String(new char[]{'A',(char)31}))));
        negatives.add(new NegativeCase("STRING_OVER_256", "V2_SETUP_JSON_DOMAIN",
            new PutMutation(new String[]{"prior"},"sourceParent",repeat('x',257))));
        negatives.add(new NegativeCase("NULL_FIXED_SLOT", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"seedValues"},"rootSeed",null)));
        negatives.add(new NegativeCase("OWNER_FIXED_VALUE", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"ownerSettings"},"viaCost",Integer.valueOf(61))));
        negatives.add(new NegativeCase("SEED_FIXED_VALUE", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"seedValues"},"rootSeed","9007199254740992")));
        negatives.add(new NegativeCase("HASH_LEXICAL", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"prior"},"manifestSha256",repeat('A',64))));
        negatives.add(new NegativeCase("RECEIPT_HASH_JOIN", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"prior","priorOriginalReceipt"},"receiptSha256",
                repeat('7',64))));
        negatives.add(new NegativeCase("UUID_LEXICAL", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"prior"},"priorRunId",
                "12345678-1234-4234-8234-123456789abC")));
        negatives.add(new NegativeCase("TRIAL_JOIN", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"capture","trialDisposition"},"orderChanged",
                Boolean.FALSE)));
        negatives.add(new NegativeCase("CAPTURE_COHORT_SUBSET", "V2_SETUP_POLICY",
            new ListSetMutation(new String[]{"capture"},"capturedNetIds",50,"NET_050")));
        negatives.add(new NegativeCase("CAPTURE_INDEX_43", "V2_SETUP_POLICY",
            new CompoundMutation(
                new ListSetMutation(new String[]{"capture"},"capturedNetIds",43,"NET_043"),
                new ListSetMutation(new String[]{"capture"},"capturedNetIds",44,"A_COIL_LOW"))));
        negatives.add(new NegativeCase("PROMOTED_NET_MISSING", "V2_SETUP_POLICY",
            new ListSetMutation(new String[]{"capture"},"capturedNetIds",10,"NET_010")));
        negatives.add(new NegativeCase("REQUESTED_DUPLICATE", "V2_SETUP_POLICY",
            new ListSetMutation(new String[]{"prior","trialDisposition"},
                "requestedNetIds",1,"NET_000")));
        negatives.add(new NegativeCase("CAPTURED_DUPLICATE", "V2_SETUP_POLICY",
            new ListSetMutation(new String[]{"capture"},"capturedNetIds",1,"NET_000")));
        negatives.add(new NegativeCase("REQUESTED_COHORT_SUBSET", "V2_SETUP_POLICY",
            new CompoundMutation(
                new ListSetMutation(new String[]{"prior","trialDisposition"},
                    "requestedNetIds",0,"NET_143"),
                new ListSetMutation(new String[]{"capture","trialDisposition"},
                    "requestedNetIds",0,"NET_143"))));
        negatives.add(new NegativeCase("CAPTURE_COHORT_ORDER", "V2_SETUP_POLICY",
            new CompoundMutation(
                new ListSetMutation(new String[]{"capture"},"cohortNetIds",0,"B_COIL_LOW"),
                new ListSetMutation(new String[]{"capture"},"cohortNetIds",1,"A_COIL_LOW"))));
        negatives.add(new NegativeCase("COHORT_LIST_SIZE", "V2_SETUP_POLICY",
            new ListRemoveMutation(new String[]{"capture"},"cohortNetIds",1)));
        negatives.add(new NegativeCase("COHORT_LIST_OVER_SIZE", "V2_SETUP_POLICY",
            new ListAppendMutation(new String[]{"capture"},"cohortNetIds","NET_EXTRA")));
        negatives.add(new NegativeCase("REQUESTED_LIST_SIZE", "V2_SETUP_POLICY",
            new ListRemoveMutation(new String[]{"prior","trialDisposition"},
                "requestedNetIds",59)));
        negatives.add(new NegativeCase("LIVE_SEARCH_INDEX", "V2_SETUP_POLICY",
            new PutMutation(new String[]{"capture"},"liveSearchIndex",Integer.valueOf(43))));
        negatives.add(new NegativeCase("ALIAS", "V2_SETUP_ALIAS", aliasMutation()));
        negatives.add(new NegativeCase("CYCLE", "V2_SETUP_ALIAS", cycleMutation()));
        negatives.add(new NegativeCase("DEPTH_LIMIT", "V2_SETUP_BOUNDS", deepMutation()));
        negatives.add(new NegativeCase("ENTRY_LIMIT", "V2_SETUP_BOUNDS", oversizedMutation()));

        for (int i=0;i<negatives.size();i++) runNegativeCase(negatives.get(i));
    }

    private static void checkOrderedKeys(Map<?,?> map, String[] expected, String label) {
        need(map.size()==expected.length, "EXPECTED_SIZE_"+label);
        Iterator<?> keys=map.keySet().iterator();
        for (int i=0;i<expected.length;i++) {
            need(keys.hasNext() && expected[i].equals(keys.next()),
                "EXPECTED_ORDER_"+label+"_"+i);
        }
        need(!keys.hasNext(), "NO_TRAILING_KEY_"+label);
    }

    private static Map<String,Object> checkedMap(Object value, String[] keys, String label) {
        Map<String,Object> map=objectMap(value,label);
        checkOrderedKeys(map,keys,label);
        return map;
    }

    private static Object value(Map<String,Object> map, String key) {
        need(map.containsKey(key), "EXPECTED_FIELD_"+key);
        return map.get(key);
    }

    private static String text(Map<String,Object> map, String key, String expected) {
        Object got=value(map,key);
        need(got!=null && got.getClass()==String.class, "EXPECTED_STRING_"+key);
        need(expected.equals(got), "EXPECTED_TEXT_"+key);
        return (String)got;
    }

    private static int integer(Map<String,Object> map, String key, int expected) {
        Object got=value(map,key);
        need(got!=null && got.getClass()==Integer.class, "EXPECTED_INTEGER_"+key);
        need(((Integer)got).intValue()==expected, "EXPECTED_INTEGER_VALUE_"+key);
        return ((Integer)got).intValue();
    }

    private static boolean bool(Map<String,Object> map, String key, boolean expected) {
        Object got=value(map,key);
        need(got!=null && got.getClass()==Boolean.class, "EXPECTED_BOOLEAN_"+key);
        need(((Boolean)got).booleanValue()==expected, "EXPECTED_BOOLEAN_VALUE_"+key);
        return ((Boolean)got).booleanValue();
    }

    private static List<?> checkedList(Map<String,Object> map, String key, int size) {
        Object got=value(map,key);
        need(got instanceof List, "EXPECTED_LIST_"+key);
        List<?> list=(List<?>)got;
        need(list.size()==size, "EXPECTED_LIST_SIZE_"+key);
        return list;
    }

    private static String expectedNet(int index) {
        if (index==10) return "D_SENSE";
        if (index==43) return "A_COIL_LOW";
        if (index==50) return "B_COIL_LOW";
        return "NET_"+(index<10 ? "00" : index<100 ? "0" : "")+index;
    }

    private static final int[] REQUESTED_INDICES = {
        43, 50, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21,
        22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42,
        44, 45, 46, 47, 48, 49, 51, 52, 53, 54, 55, 56, 57, 58, 59
    };

    private static void checkNetList(List<?> list, String label, boolean pairFirst) {
        Set<String> unique=new HashSet<String>();
        for (int i=0;i<60;i++) {
            Object item=list.get(i);
            need(item!=null && item.getClass()==String.class, "NET_STRING_"+label+"_"+i);
            int expectedIndex=pairFirst ? REQUESTED_INDICES[i] : i;
            need(expectedNet(expectedIndex).equals(item), "NET_LITERAL_"+label+"_"+i);
            need(unique.add((String)item), "NET_UNIQUE_"+label+"_"+i);
        }
        need(unique.contains("A_COIL_LOW") && unique.contains("B_COIL_LOW"),
            "NET_COHORT_SUBSET_"+label);
    }

    private static void checkTrial(Map<String,Object> trial, String label) {
        checkOrderedKeys(trial,TRIAL_KEYS,label);
        text(trial,"status","RUN");
        text(trial,"reason","TRIAL_EXECUTED");
        text(trial,"capturedFailedNetId","A_COIL_LOW");
        text(trial,"controlFailedNetId","B_COIL_LOW");
        List<?> cohort=checkedList(trial,"cohortNetIds",2);
        need("A_COIL_LOW".equals(cohort.get(0)) && "B_COIL_LOW".equals(cohort.get(1)),
            "TRIAL_COHORT_LITERAL_"+label);
        List<?> requested=checkedList(trial,"requestedNetIds",60);
        checkNetList(requested,label+"_REQUESTED",true);
        bool(trial,"orderChanged",true);
        bool(trial,"feedbackValidated",true);
    }

    private static boolean lowerHex(String value, int length) {
        if (value==null || value.length()!=length) return false;
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (!((c>='0' && c<='9') || (c>='a' && c<='f'))) return false;
        }
        return true;
    }

    private static void checkAsciiTree(Object value, IdentityHashMap<Object,Boolean> seen,
            int depth, WalkStats stats) {
        need(depth<=32, "FIXTURE_DEPTH");
        stats.visits++;
        need(stats.visits<=1000000L, "FIXTURE_VISITS");
        if (value instanceof Map) {
            need(value.getClass()==LinkedHashMap.class, "FIXTURE_MAP_IMPLEMENTATION");
            need(seen.put(value,Boolean.TRUE)==null, "FIXTURE_ALIAS");
            stats.containers++;
            Map<?,?> map=(Map<?,?>)value;
            stats.entries+=1+map.size();
            for (Map.Entry<?,?> row:map.entrySet()) {
                Object key=row.getKey();
                stats.visits++;
                need(key!=null && key.getClass()==String.class, "FIXTURE_KEY_TYPE");
                checkAscii((String)key,"FIXTURE_KEY_DOMAIN");
                need(stats.visits<=1000000L, "FIXTURE_VISITS");
                checkAsciiTree(row.getValue(),seen,depth+1,stats);
            }
        } else if (value instanceof List) {
            need(value.getClass()==ArrayList.class, "FIXTURE_LIST_IMPLEMENTATION");
            need(seen.put(value,Boolean.TRUE)==null, "FIXTURE_ALIAS");
            stats.containers++;
            List<?> list=(List<?>)value;
            stats.entries+=1+list.size();
            for (int i=0;i<list.size();i++) checkAsciiTree(list.get(i),seen,depth+1,stats);
        } else if (value!=null && value.getClass()==String.class) {
            checkAscii((String)value,"FIXTURE_STRING_DOMAIN");
        } else {
            need(value!=null && (value.getClass()==Integer.class
                || value.getClass()==Boolean.class), "FIXTURE_SCALAR_TYPE");
        }
        need(stats.entries<=4096, "FIXTURE_ENTRY_LIMIT");
    }

    private static void checkAscii(String value, String label) {
        need(value.length()>0 && value.length()<=256, label+"_LENGTH");
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            need(c>=32 && c<=126, label+"_CHAR_"+i);
        }
    }

    private static void verifyExpectedProjection(Map<String,Object> data) {
        checkOrderedKeys(data,ROOT_KEYS,"root");
        WalkStats stats=new WalkStats();
        checkAsciiTree(data,new IdentityHashMap<Object,Boolean>(),0,stats);
        need(stats.containers==16, "EXPECTED_CONTAINER_COUNT");
        need(stats.entries==271, "EXPECTED_PROJECTION_ENTRIES");
        need(stats.visits==325L, "EXPECTED_KEY_INCLUSIVE_VISITS");

        Map<String,Object> prior=checkedMap(value(data,"prior"),PRIOR_KEYS,"prior");
        need(lowerHex(text(prior,"sourceParent",repeat('d',40)),40), "SOURCE_PARENT_FORMAT");
        text(prior,"manifestSha256",repeat('1',64));
        text(prior,"sourceManifestSha256",repeat('2',64));
        text(prior,"readerSha256",repeat('3',64));
        text(prior,"resultSha256",repeat('4',64));
        text(prior,"operation1Sha256",repeat('5',64));
        text(prior,"parentReceiptSha256",repeat('6',64));
        String runId=text(prior,"priorRunId","12345678-1234-4234-8234-123456789abc");
        need(runId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "UUID_LEXICAL_RULE");

        Map<String,Object> receipt=checkedMap(value(prior,"priorOriginalReceipt"),
            RECEIPT_KEYS,"priorOriginalReceipt");
        text(receipt,"receiptPath","op-1.jsonl");
        integer(receipt,"receiptBytes",4096);
        String receiptSha=text(receipt,"receiptSha256",repeat('5',64));
        need(receiptSha.equals(value(prior,"operation1Sha256")), "RECEIPT_SHA_BINDING");
        integer(receipt,"receiptOrdinal",307);
        integer(receipt,"lineageOrdinal",77);
        checkTrial(checkedMap(value(prior,"trialDisposition"),TRIAL_KEYS,"priorTrial"),
            "priorTrial");

        Map<String,Object> capture=checkedMap(value(data,"capture"),CAPTURE_KEYS,"capture");
        List<?> cohort=checkedList(capture,"cohortNetIds",2);
        need("A_COIL_LOW".equals(cohort.get(0)) && "B_COIL_LOW".equals(cohort.get(1)),
            "CAPTURE_COHORT_LITERAL");
        List<?> captured=checkedList(capture,"capturedNetIds",60);
        checkNetList(captured,"captured",false);
        checkTrial(checkedMap(value(capture,"trialDisposition"),TRIAL_KEYS,"captureTrial"),
            "captureTrial");
        Map<String,Object> priorTrial=objectMap(value(prior,"trialDisposition"),"priorTrialJoin");
        Map<String,Object> captureTrial=objectMap(value(capture,"trialDisposition"),"captureTrialJoin");
        for (int i=0;i<TRIAL_KEYS.length;i++) {
            String key=TRIAL_KEYS[i];
            Object left=priorTrial.get(key);
            Object right=captureTrial.get(key);
            if (left instanceof List && right instanceof List) {
                List<?> a=(List<?>)left, b=(List<?>)right;
                need(a.size()==b.size(), "TRIAL_JOIN_LIST_SIZE_"+key);
                for (int j=0;j<a.size();j++) need(a.get(j).equals(b.get(j)),
                    "TRIAL_JOIN_LIST_"+key+"_"+j);
            } else {
                need(left==null ? right==null : left.equals(right), "TRIAL_JOIN_"+key);
            }
        }
        text(capture,"controlDisposition","NO_PATH");
        text(capture,"trialOutcome","CAP_REJECTED");
        integer(capture,"passIndex",2);
        text(capture,"failedNetId","A_COIL_LOW");
        text(capture,"priorPromotedNetId","D_SENSE");
        integer(capture,"liveSearchIndex",44);
        need("A_COIL_LOW".equals(captured.get(43)), "CAPTURE_INDEX_43");
        need(captured.contains("D_SENSE"), "PROMOTED_NET_IN_CAPTURE");

        Map<String,Object> owner=checkedMap(value(data,"ownerSettings"),OWNER_KEYS,"ownerSettings");
        text(owner,"boardMode","FULLER_TWO_LAYER");
        integer(owner,"maxExpansions",1000000);
        integer(owner,"expandedAtFailure",918222);
        integer(owner,"maxVias",48);
        integer(owner,"viaCost",60);
        integer(owner,"maxTransitions",4);
        text(owner,"branchStrategy","CANONICAL_NEAREST");
        text(owner,"netStrategy","CANONICAL");
        bool(owner,"roleWidthsEnabled",true);
        bool(owner,"orderingStreamEnabled",true);

        Map<String,Object> seeds=checkedMap(value(data,"seedValues"),SEED_KEYS,"seedValues");
        text(seeds,"rootSeed","9007199254740993");
        text(seeds,"placementSeed","-6587958367974952701");
        text(seeds,"routingSeed","8445301674744508279");

        Map<String,Object> identity=checkedMap(value(data,"sourceIdentity"),
            IDENTITY_KEYS,"sourceIdentity");
        text(identity,"geometryBeforeSha256",repeat('8',64));
        text(identity,"geometryAfterSha256",repeat('9',64));
        text(identity,"detachedGeometrySha256",repeat('a',64));
        text(identity,"effectsBeforeSha256",repeat('b',64));
        text(identity,"effectsAfterSha256",repeat('c',64));
        integer(identity,"traceCount",0);
        integer(identity,"holeCount",0);
        Map<String,Object> pad=checkedMap(value(identity,"padLandIdentity"),PAD_KEYS,
            "padLandIdentity");
        text(pad,"format","TSJ_PAD_LAND_ASCII_V1");
        integer(pad,"rowCount",246);
        text(pad,"sha256","541543170f9d48428f41ae881c596384597543ec03453fc9f9ea8d14033275a0");
    }

    private static void collectContainers(Object value, IdentityHashMap<Object,Boolean> out) {
        if (value instanceof Map) {
            need(out.put(value,Boolean.TRUE)==null, "POSITIVE_MAP_ALIAS");
            for (Object child:((Map<?,?>)value).values()) collectContainers(child,out);
        } else if (value instanceof List) {
            need(out.put(value,Boolean.TRUE)==null, "POSITIVE_LIST_ALIAS");
            for (Object child:(List<?>)value) collectContainers(child,out);
        }
    }

    private static void checkFreshTrees(Map<String,Object> first, Map<String,Object> second) {
        need(first!=second, "FRESH_ROOT_MAP");
        IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<Object,Boolean>();
        collectContainers(first,seen);
        IdentityHashMap<Object,Boolean> next=new IdentityHashMap<Object,Boolean>();
        collectContainers(second,next);
        for (Object container:next.keySet()) need(!seen.containsKey(container), "FRESH_NESTED_TREE");
    }

    public static void main(String[] args) throws Exception {
        need(args!=null && args.length==1, "CANARY_ARGUMENTS_EXPECTED_SUCCESSFUL_UNITS");
        long expectedUnits;
        try {
            expectedUnits=Long.parseLong(args[0]);
        } catch (NumberFormatException invalid) {
            throw new IOException("CANARY_EXPECTED_UNITS",invalid);
        }
        final long fixedUnits=18L+72L+290L+271L+3L+202L+11L;
        need(fixedUnits==867L, "FIXED_SETUP_DEBIT_867");
        need(expectedUnits>fixedUnits, "EXPECTED_VALIDATOR_DEBIT_POSITIVE");
        final long validatorUnits=expectedUnits-fixedUnits;
        need(validatorUnits>0L, "VALIDATOR_DEBIT_NOT_DERIVED_FROM_METER");
        need(271+202+5==478, "FIXED_COPY_DEBIT_478");
        need(1+1+1==3, "FIXED_COPY_CHUNKS_3");
        expectedContextHash=CohortTerminalTelemetryInputV2.contextSha256();
        need(lowerHex(expectedContextHash,64), "SYNTHETIC_CONTEXT_HASH_FORMAT");
        parent=new CohortTerminalTelemetryStudy.Parent();

        Success first=positive(expectedUnits);
        Success second=positive(expectedUnits);
        checkFreshTrees(first.data,second.data);
        runHeaderCases();
        runNestedShapeCases();
        runValueAndPolicyCases();

        System.out.println("{\"status\":\"PASS_SYNTHETIC_V2_INPUT_SETUP_COMPONENT\","
            +"\"cases\":"+cases+",\"assertions\":"+assertions
            +",\"successfulUnits\":"+expectedUnits+",\"validatorUnits\":"+validatorUnits
            +",\"copiedEntries\":478,\"groups\":"+first.groups
            +",\"copyChunks\":3,\"freshPositiveMeters\":2"
            +",\"ownerInstances\":0,\"sessions\":0,\"originalCaptures\":0"
            +",\"routingRuns\":0,\"normalHostAcceptance\":false}");
    }
}
