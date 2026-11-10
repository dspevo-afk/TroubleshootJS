package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic DTO projection canary; no arm producer, graph, session, or export. */
public final class CurrentArmsProjectionCanary {
    private static final String SCHEMA="TSJ_CURRENT_ARMS_PROJECTION_CANARY_R1";
    private static final String COPY_ERROR="copy exceeds its prebounded chunk or aggregate allowance";
    private static final String GROUP_ERROR="native wrapper exceeded 640 measured groups";
    private static final String[] SMOKE={"arms_transfer","arms_ownership_disjoint"};
    private static final String[] SUITE={
        "arms_argument_meter","arms_argument_null_id","arms_argument_null_run",
        "arms_argument_null_observation","arms_argument_null_cleanup",
        "arms_argument_observe_id","arms_argument_observe_run",
        "arms_argument_observe_observation","arms_argument_observe_cleanup",
        "arms_alias_rejected","arms_semantic_passthrough","arms_copy_allowance_4",
        "arms_copy_allowance_5","arms_copy_allowance_10","arms_copy_allowance_12",
        "arms_group_guard","arms_success_open"
    };
    private static int parentConstructions;

    private CurrentArmsProjectionCanary() { }

    public static void main(String[] args) {
        boolean valid=args!=null && args.length==1 &&
            ("SMOKE".equals(args[0]) || "SUITE".equals(args[0]));
        String mode=valid?args[0]:"INVALID";
        List<String> names=new ArrayList<String>();
        List<Map<String,Object>> cases=new ArrayList<Map<String,Object>>();
        boolean pass=valid;
        if (valid) {
            String[] selected="SMOKE".equals(mode)?SMOKE:SUITE;
            for (String name:selected) {
                names.add(name);
                Map<String,Object> result=run(name);
                cases.add(result);
                if (!"PASS".equals(result.get("status"))) pass=false;
            }
        }
        Map<String,Object> output=new LinkedHashMap<String,Object>();
        output.put("schema",SCHEMA);
        output.put("mode",mode);
        output.put("status",pass?"PASS":"FAIL");
        output.put("names",names);
        output.put("cases",cases);
        output.put("parentConstructions",Integer.valueOf(parentConstructions));
        output.put("error",valid?(pass?"":"CANARY_CASE_FAILED"):"CANARY_ARGUMENTS");
        System.out.println(json(output));
        if (!pass) System.exit(1);
    }

    private static Map<String,Object> record(String name) {
        Map<String,Object> r=new LinkedHashMap<String,Object>();
        r.put("name",name); r.put("status","FAIL"); r.put("phase","setup");
        r.put("reason","UNEXPECTED_FAILURE");
        r.put("operation","CurrentResultProjections.arms");
        r.put("error",""); r.put("rejections",Long.valueOf(0));
        r.put("payloadJson","");
        String[] ledger={"units","copiedEntries","copyChunks","groups","priorGroups",
            "groupUnits","maxGroupUnits","maxGroupNanos"};
        for (String key:ledger) r.put(key,Long.valueOf(0));
        r.put("meterOpen",Boolean.FALSE); r.put("finalOpen",Boolean.FALSE);
        r.put("helperUnits",Long.valueOf(0)); r.put("helperCopies",Long.valueOf(0));
        r.put("helperChunks",Long.valueOf(0));
        r.put("identity",identity(false,false,false,false,false,false));
        r.put("inputUnchanged",Boolean.FALSE); r.put("sourceReads",Long.valueOf(0));
        r.put("discarded",Boolean.FALSE); r.put("operationNanos",Long.valueOf(0));
        r.put("totalElapsedNanos",Long.valueOf(0));
        r.put("openGroupElapsedNanos",Long.valueOf(0));
        return r;
    }

    private static Map<String,Object> run(String name) {
        Map<String,Object> r=record(name);
        CohortTerminalTelemetryStudy.SetupMeter meter=null;
        Fixture fixture=null;
        String before=null;
        String phase="setup";
        try {
            CohortTerminalTelemetryStudy.Parent parent=
                new CohortTerminalTelemetryStudy.Parent();
            parentConstructions++;
            meter=new CohortTerminalTelemetryStudy.SetupMeter(parent,
                parent.meter(System.nanoTime()));
            fixture=new Fixture("arms_alias_rejected".equals(name));
            before=snapshot(fixture.inputs());

            int allowance=copyAllowance(name);
            if (allowance>=0)
                meter.method("setCopyAllowance",new Class<?>[]{int.class},
                    Integer.valueOf(allowance));
            if ("arms_group_guard".equals(name)) {
                for (int i=0;i<640;i++) {
                    meter.charge(1);
                    meter.finish();
                }
            }

            phase=projectionPhase(name); r.put("phase",phase);
            long[] debit=debit(meter);
            Object[] args=projectionArgs(name,meter,fixture);
            Map<String,Object> payload=null;
            Throwable projectionFailure=null;
            long operationStarted=System.nanoTime();
            fixture.reads.tracking=true;
            try {
                payload=project(args);
            } catch (Throwable failure) {
                projectionFailure=failure;
            } finally {
                fixture.reads.tracking=false;
                r.put("operationNanos",
                    Long.valueOf(elapsed(operationStarted,System.nanoTime())));
                helperDelta(meter,debit,r);
                r.put("sourceReads",Long.valueOf(fixture.reads.count));
                r.put("meterOpen",Boolean.valueOf(open(meter)));
            }

            String expectedError=projectionError(name);
            if (expectedError!=null) {
                String actual=errorText(projectionFailure);
                r.put("error",actual);
                check(projectionFailure!=null && expectedError.equals(actual),
                    "EXPECTED_PROJECTION_REJECTION");
                check(payload==null,"REJECTED_PROJECTION_RETURNED_OUTPUT");
                r.put("reason","EXPECTED_REJECTION");
                r.put("rejections",Long.valueOf(1));
                r.put("discarded",Boolean.TRUE);
                meter.method("check",new Class<?>[0]);
            } else {
                if (projectionFailure!=null) {
                    r.put("error",errorText(projectionFailure));
                    throw new AssertionError("UNEXPECTED_PROJECTION_FAILURE");
                }
                check(payload!=null,"PROJECTION_RETURNED_NULL");
                r.put("identity",verifyProjection(payload,fixture));
                String payloadJson=json(payload);
                r.put("payloadJson",payloadJson);
                check(payloadJson.equals(expectedPayload(
                    "arms_alias_rejected".equals(name))),"PAYLOAD_LITERAL");

                if ("arms_alias_rejected".equals(name)) {
                    phase="ownership_guard"; r.put("phase",phase);
                    Throwable ownershipFailure=null;
                    try {
                        CurrentBodyOwnership.validate(meter,payload);
                    } catch (Throwable failure) {
                        ownershipFailure=failure;
                    }
                    String actual=errorText(ownershipFailure);
                    r.put("error",actual);
                    check(ownershipFailure instanceof IOException &&
                        "BODY_OWNERSHIP_ALIAS".equals(actual),"ALIAS_GUARD_REJECTION");
                    r.put("reason","EXPECTED_REJECTION");
                    r.put("rejections",Long.valueOf(1));
                    r.put("discarded",Boolean.TRUE);
                    meter.method("check",new Class<?>[0]);
                } else if ("arms_ownership_disjoint".equals(name)) {
                    phase="ownership_guard"; r.put("phase",phase);
                    CurrentBodyOwnership.validate(meter,payload);
                    meter.method("check",new Class<?>[0]);
                    meter.finish();
                    meter.method("check",new Class<?>[0]);
                    r.put("reason","OK");
                } else {
                    meter.method("check",new Class<?>[0]);
                    if (!"arms_success_open".equals(name)) {
                        meter.finish();
                        meter.method("check",new Class<?>[0]);
                    }
                    r.put("reason","OK");
                }
            }

            check(helperUnits(r)==expectedHelper(name)[0] &&
                helperCopies(r)==expectedHelper(name)[1] &&
                helperChunks(r)==expectedHelper(name)[2],"HELPER_DEBIT");
            String after=snapshot(fixture.inputs());
            boolean unchanged=before.equals(after);
            r.put("inputUnchanged",Boolean.valueOf(unchanged));
            check(unchanged,"INPUT_MUTATED");
            check(((Number)r.get("sourceReads")).longValue()==0,"CHILD_SOURCE_READ");
            checkExpectedIdentity(name,r);
            r.put("finalOpen",Boolean.valueOf(open(meter)));
            nativeFields(meter,r);
            checkExpectedLedger(name,r);
            check(((Number)r.get("operationNanos")).longValue()<=90000000000L,
                "OPERATION_TIME_BOUND");
            r.put("status","PASS");
            if (!"EXPECTED_REJECTION".equals(r.get("reason")))
                r.put("discarded",Boolean.FALSE);
        } catch (Throwable failure) {
            r.put("status","FAIL");
            r.put("phase",phase);
            r.put("reason",describe(failure));
            if ("".equals(r.get("error"))) r.put("error",describe(failure));
            if (meter!=null) r.put("discarded",Boolean.TRUE);
            if (fixture!=null && before!=null) {
                try {
                    r.put("inputUnchanged",Boolean.valueOf(
                        before.equals(snapshot(fixture.inputs()))));
                    r.put("sourceReads",Long.valueOf(fixture.reads.count));
                } catch (Throwable ignored) {
                    r.put("inputUnchanged",Boolean.FALSE);
                }
            }
        } finally {
            if (meter!=null) {
                try {
                    r.put("finalOpen",Boolean.valueOf(open(meter)));
                    nativeFields(meter,r);
                } catch (Throwable failure) {
                    r.put("status","FAIL");
                    r.put("reason",describe(failure));
                    if ("".equals(r.get("error"))) r.put("error",describe(failure));
                }
            }
        }
        return r;
    }

    private static String projectionPhase(String name) {
        if (name.startsWith("arms_argument_")) return "argument_guard";
        if (name.startsWith("arms_copy_allowance_") ||
                "arms_group_guard".equals(name)) return "projection_guard";
        return "projection";
    }

    private static int copyAllowance(String name) {
        if ("arms_copy_allowance_4".equals(name)) return 4;
        if ("arms_copy_allowance_5".equals(name)) return 5;
        if ("arms_copy_allowance_10".equals(name)) return 10;
        if ("arms_copy_allowance_12".equals(name)) return 12;
        return -1;
    }

    private static String projectionError(String name) {
        if (name.startsWith("arms_argument_")) return "RESULT_PROJECTION_ARGUMENT";
        if ("arms_copy_allowance_4".equals(name) ||
                "arms_copy_allowance_5".equals(name) ||
                "arms_copy_allowance_10".equals(name)) return COPY_ERROR;
        if ("arms_group_guard".equals(name)) return GROUP_ERROR;
        return null;
    }

    private static Object[] projectionArgs(String name,
            CohortTerminalTelemetryStudy.SetupMeter meter,Fixture f) {
        Object[] args={meter,f.nullId,f.nullRun,f.nullObservation,f.nullCleanup,
            f.observeId,f.observeRun,f.observeObservation,f.observeCleanup};
        if ("arms_argument_meter".equals(name)) args[0]=null;
        else if ("arms_argument_null_id".equals(name)) args[1]=null;
        else if ("arms_argument_null_run".equals(name)) args[2]=null;
        else if ("arms_argument_null_observation".equals(name)) args[3]=null;
        else if ("arms_argument_null_cleanup".equals(name)) args[4]=null;
        else if ("arms_argument_observe_id".equals(name)) args[5]=null;
        else if ("arms_argument_observe_run".equals(name)) args[6]=null;
        else if ("arms_argument_observe_observation".equals(name)) args[7]=null;
        else if ("arms_argument_observe_cleanup".equals(name)) args[8]=null;
        return args;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object value) {
        return (Map<String,Object>)value;
    }

    private static Map<String,Object> project(Object[] a) throws Exception {
        return CurrentResultProjections.arms(
            (CohortTerminalTelemetryStudy.SetupMeter)a[0],
            (String)a[1],map(a[2]),map(a[3]),map(a[4]),
            (String)a[5],map(a[6]),map(a[7]),map(a[8]));
    }

    private static Map<String,Object> verifyProjection(Map<String,Object> out,Fixture f) {
        Map<?,?> nullArm=map(out.get("null"));
        Map<?,?> observeArm=map(out.get("observe"));
        boolean outerFresh=out.getClass()==LinkedHashMap.class && !containsChild(f,out);
        boolean armFresh=nullArm.getClass()==LinkedHashMap.class &&
            observeArm.getClass()==LinkedHashMap.class && nullArm!=observeArm &&
            !containsChild(f,nullArm) && !containsChild(f,observeArm) &&
            out!=nullArm && out!=observeArm;
        boolean order=keyOrder(out,"null","observe") &&
            keyOrder(nullArm,"armRole","armId","freshRun","observation","cleanup") &&
            keyOrder(observeArm,"armRole","armId","freshRun","observation","cleanup");
        boolean ids=nullArm.get("armId")==f.nullId &&
            observeArm.get("armId")==f.observeId;
        boolean children=nullArm.get("freshRun")==f.nullRun &&
            nullArm.get("observation")==f.nullObservation &&
            nullArm.get("cleanup")==f.nullCleanup &&
            observeArm.get("freshRun")==f.observeRun &&
            observeArm.get("observation")==f.observeObservation &&
            observeArm.get("cleanup")==f.observeCleanup;
        boolean disjoint=f.distinctChildren();
        check(outerFresh && armFresh && order && ids && children,"PROJECTION_IDENTITY");
        return identity(outerFresh,armFresh,order,ids,children,disjoint);
    }

    private static boolean containsChild(Fixture f,Object value) {
        for (Object child:f.children()) if (value==child) return true;
        return false;
    }

    private static boolean keyOrder(Map<?,?> map,String... expected) {
        if (map.size()!=expected.length) return false;
        Iterator<?> it=map.keySet().iterator();
        for (String key:expected) if (!it.hasNext() || !key.equals(it.next())) return false;
        return !it.hasNext();
    }

    private static Map<String,Object> identity(boolean outerFresh,boolean armFresh,
            boolean order,boolean ids,boolean children,boolean disjoint) {
        Map<String,Object> value=new LinkedHashMap<String,Object>();
        value.put("outerFresh",Boolean.valueOf(outerFresh));
        value.put("armMapsFresh",Boolean.valueOf(armFresh));
        value.put("fieldOrder",Boolean.valueOf(order));
        value.put("idsPreserved",Boolean.valueOf(ids));
        value.put("childMapsPreserved",Boolean.valueOf(children));
        value.put("inputMapsDisjoint",Boolean.valueOf(disjoint));
        return value;
    }

    private static void checkExpectedIdentity(String name,Map<String,Object> r) {
        @SuppressWarnings("unchecked")
        Map<String,Object> value=(Map<String,Object>)r.get("identity");
        boolean hasOutput=!String.valueOf(r.get("payloadJson")).isEmpty();
        boolean alias="arms_alias_rejected".equals(name);
        boolean all=hasOutput;
        check(Boolean.valueOf(all).equals(value.get("outerFresh")) &&
            Boolean.valueOf(all).equals(value.get("armMapsFresh")) &&
            Boolean.valueOf(all).equals(value.get("fieldOrder")) &&
            Boolean.valueOf(all).equals(value.get("idsPreserved")) &&
            Boolean.valueOf(all).equals(value.get("childMapsPreserved")) &&
            Boolean.valueOf(all && !alias).equals(value.get("inputMapsDisjoint")),
            "IDENTITY_REPORT");
    }

    private static String expectedPayload(boolean alias) {
        String observeRun=alias?"{\"nullRun\":\"N_RUN\"}":"{\"observeRun\":\"O_RUN\"}";
        return "{\"null\":{\"armRole\":\"NULL\",\"armId\":\"SYNTH_NULL\","+
            "\"freshRun\":{\"nullRun\":\"N_RUN\"},\"observation\":{\"nullObservation\":\"N_OBS\"},"+
            "\"cleanup\":{\"nullCleanup\":\"N_CLEAN\"}},\"observe\":{\"armRole\":\"OBSERVE\","+
            "\"armId\":\"SYNTH_OBSERVE\",\"freshRun\":"+observeRun+
            ",\"observation\":{\"observeObservation\":\"O_OBS\"},"+
            "\"cleanup\":{\"observeCleanup\":\"O_CLEAN\"}}}";
    }

    private static long[] expectedHelper(String name) {
        if (name.startsWith("arms_argument_") || "arms_group_guard".equals(name) ||
                "arms_copy_allowance_4".equals(name)) return new long[]{0,0,0};
        if ("arms_copy_allowance_5".equals(name)) return new long[]{6,5,1};
        if ("arms_copy_allowance_10".equals(name)) return new long[]{12,10,2};
        return new long[]{15,12,3};
    }

    private static long helperUnits(Map<String,Object> r) {
        return ((Number)r.get("helperUnits")).longValue();
    }
    private static long helperCopies(Map<String,Object> r) {
        return ((Number)r.get("helperCopies")).longValue();
    }
    private static long helperChunks(Map<String,Object> r) {
        return ((Number)r.get("helperChunks")).longValue();
    }

    private static void checkExpectedLedger(String name,Map<String,Object> r) {
        long units,copies,chunks,groups;
        if (name.startsWith("arms_argument_") ||
                "arms_copy_allowance_4".equals(name)) {
            units=0; copies=0; chunks=0; groups=0;
        } else if ("arms_group_guard".equals(name)) {
            units=640; copies=0; chunks=0; groups=640;
        } else if ("arms_copy_allowance_5".equals(name)) {
            units=6; copies=5; chunks=1; groups=0;
        } else if ("arms_copy_allowance_10".equals(name)) {
            units=12; copies=10; chunks=2; groups=0;
        } else if ("arms_alias_rejected".equals(name)) {
            units=187; copies=19; chunks=10; groups=0;
        } else if ("arms_ownership_disjoint".equals(name)) {
            units=254; copies=21; chunks=12; groups=1;
        } else {
            units=15; copies=12; chunks=3; groups="arms_success_open".equals(name)?0:1;
        }
        check(number(r,"units")==units && number(r,"copiedEntries")==copies &&
            number(r,"copyChunks")==chunks && number(r,"groups")==groups &&
            number(r,"priorGroups")==0,"FINAL_NATIVE_LEDGER");
        boolean openAfter="arms_success_open".equals(name) ||
            "arms_copy_allowance_5".equals(name) ||
            "arms_copy_allowance_10".equals(name) ||
            "arms_alias_rejected".equals(name);
        check(Boolean.valueOf(openAfter).equals(r.get("finalOpen")),"FINAL_OPEN_STATE");
        boolean openAfterProjection=units>0 && !name.startsWith("arms_argument_") &&
            !"arms_group_guard".equals(name) &&
            !"arms_copy_allowance_4".equals(name);
        check(Boolean.valueOf(openAfterProjection).equals(r.get("meterOpen")),
            "POST_PROJECTION_OPEN_STATE");
    }

    private static long number(Map<String,Object> values,String key) {
        return ((Number)values.get(key)).longValue();
    }

    private static long number(CohortTerminalTelemetryStudy.SetupMeter meter,String key)
            throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(meter.meter,key)).longValue();
    }

    private static long[] debit(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        return new long[]{number(meter,"units"),number(meter,"copiedEntries"),
            number(meter,"copyChunks")};
    }

    private static void helperDelta(CohortTerminalTelemetryStudy.SetupMeter meter,
            long[] before,Map<String,Object> r) throws Exception {
        long[] after=debit(meter);
        r.put("helperUnits",Long.valueOf(after[0]-before[0]));
        r.put("helperCopies",Long.valueOf(after[1]-before[1]));
        r.put("helperChunks",Long.valueOf(after[2]-before[2]));
    }

    private static boolean open(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter,"open"));
    }

    private static void nativeFields(CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<String,Object> r) throws Exception {
        String[] keys={"units","copiedEntries","copyChunks","groups","priorGroups",
            "groupUnits","maxGroupUnits","maxGroupNanos"};
        for (String key:keys) r.put(key,Long.valueOf(number(meter,key)));
        boolean isOpen=open(meter);
        r.put("finalOpen",Boolean.valueOf(isOpen));
        long now=System.nanoTime();
        long total=elapsed(number(meter,"started"),now);
        long openNanos=isOpen?elapsed(number(meter,"groupStarted"),now):0;
        r.put("totalElapsedNanos",Long.valueOf(total));
        r.put("openGroupElapsedNanos",Long.valueOf(openNanos));
        check(number(r,"maxGroupNanos")>=0 &&
            number(r,"maxGroupNanos")<=5000000000L,"MAX_GROUP_TIME_BOUND");
        check(total<=90000000000L && openNanos<=5000000000L,"METER_TIME_BOUND");
    }

    private static long elapsed(long start,long end) {
        long value=end-start;
        check(value>=0,"NEGATIVE_CLOCK");
        return value;
    }

    private static String errorText(Throwable failure) {
        if (failure==null) return "";
        if (failure instanceof IOException) return String.valueOf(failure.getMessage());
        return describe(failure);
    }

    private static String describe(Throwable failure) {
        if (failure==null) return "";
        String value=failure.getClass().getSimpleName()+":"+
            String.valueOf(failure.getMessage());
        StringBuilder out=new StringBuilder();
        for (int i=0;i<value.length() && i<240;i++) {
            char c=value.charAt(i);
            out.append(c>=32 && c<=126?c:'?');
        }
        return out.toString();
    }

    private static void check(boolean ok,String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class ReadCounter {
        long count;
        boolean tracking;
        void read() { if (tracking) count++; }
    }

    private static final class CountingMap extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID=1L;
        private final ReadCounter reads;
        CountingMap(ReadCounter reads,String key,String value) {
            this.reads=reads;
            super.put(key,value);
        }
        public Object get(Object key) { reads.read(); return super.get(key); }
        public boolean containsKey(Object key) { reads.read(); return super.containsKey(key); }
        public boolean containsValue(Object value) { reads.read(); return super.containsValue(value); }
        public int size() { reads.read(); return super.size(); }
        public boolean isEmpty() { reads.read(); return super.isEmpty(); }
        public Set<Map.Entry<String,Object>> entrySet() {
            reads.read(); return super.entrySet();
        }
        public Set<String> keySet() { reads.read(); return super.keySet(); }
        public Collection<Object> values() { reads.read(); return super.values(); }
    }

    private static final class Fixture {
        final ReadCounter reads=new ReadCounter();
        final String nullId=new String("SYNTH_NULL");
        final String observeId=new String("SYNTH_OBSERVE");
        final CountingMap nullRun;
        final CountingMap nullObservation;
        final CountingMap nullCleanup;
        final CountingMap observeRun;
        final CountingMap observeObservation;
        final CountingMap observeCleanup;

        Fixture(boolean aliasRun) {
            nullRun=new CountingMap(reads,"nullRun","N_RUN");
            nullObservation=new CountingMap(reads,"nullObservation","N_OBS");
            nullCleanup=new CountingMap(reads,"nullCleanup","N_CLEAN");
            observeRun=aliasRun?nullRun:new CountingMap(reads,"observeRun","O_RUN");
            observeObservation=new CountingMap(reads,"observeObservation","O_OBS");
            observeCleanup=new CountingMap(reads,"observeCleanup","O_CLEAN");
        }

        List<Object> children() {
            List<Object> result=new ArrayList<Object>();
            result.add(nullRun); result.add(nullObservation); result.add(nullCleanup);
            result.add(observeRun); result.add(observeObservation); result.add(observeCleanup);
            return result;
        }

        List<Object> inputs() {
            List<Object> result=new ArrayList<Object>();
            result.add(nullId); result.addAll(children()); result.add(observeId);
            return result;
        }

        boolean distinctChildren() {
            IdentityHashMap<Object,Boolean> found=new IdentityHashMap<Object,Boolean>();
            for (Object value:children())
                if (found.put(value,Boolean.TRUE)!=null) return false;
            return true;
        }
    }

    private static String snapshot(Object value) {
        List<String> tokens=new ArrayList<String>();
        snapshotTokens(value,tokens);
        return json(tokens);
    }

    private static void snapshotTokens(Object value,List<String> tokens) {
        if (value instanceof Map) {
            tokens.add("MAP");
            for (Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                tokens.add("KEY:"+entry.getKey());
                snapshotTokens(entry.getValue(),tokens);
            }
            tokens.add("END_MAP");
        } else if (value instanceof List) {
            tokens.add("LIST");
            for (Object item:(List<?>)value) snapshotTokens(item,tokens);
            tokens.add("END_LIST");
        } else {
            tokens.add(value.getClass().getName()+":"+String.valueOf(value));
        }
    }

    // Testbench serialization is bounded and is not production export evidence.
    private static String json(Object value) {
        StringBuilder out=new StringBuilder();
        write(value,out,0);
        check(out.length()<=1048576,"SUMMARY_BOUND");
        return out.toString();
    }

    private static void write(Object value,StringBuilder out,int depth) {
        check(depth<=4 && out.length()<=1048576,"SUMMARY_BOUND");
        if (value instanceof String) {
            out.append('"');
            String text=(String)value;
            for (int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                check(c>=32 && c<=126,"SUMMARY_ASCII");
                if (c=='"' || c=='\\') out.append('\\');
                out.append(c);
            }
            out.append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map) {
            out.append('{');
            boolean first=true;
            for (Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                if (!first) out.append(',');
                first=false;
                write(entry.getKey(),out,depth+1);
                out.append(':');
                write(entry.getValue(),out,depth+1);
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('[');
            boolean first=true;
            for (Object item:(List<?>)value) {
                if (!first) out.append(',');
                first=false;
                write(item,out,depth+1);
            }
            out.append(']');
        } else {
            throw new AssertionError("SUMMARY_TYPE");
        }
        check(out.length()<=1048576,"SUMMARY_BOUND");
    }
}
