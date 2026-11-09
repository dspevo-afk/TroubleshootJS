package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic component canary. No original owner, producer, graph, or host resources. */
public final class CurrentResultProjectionsCanary {
    private static final String SCHEMA="TSJ_CURRENT_RESULT_PROJECTIONS_CANARY_R1";
    private static final String GEOMETRY="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String EFFECTS="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DIFFERENT="cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String[] SMOKE={"cohort_transfer","cleanup_true"};
    private static final String[] SUITE={"cohort_requested_mismatch","cohort_duplicate_capture",
        "cohort_missing_capture","cohort_incompatible_disposition","cleanup_null_alias_false",
        "cleanup_observe_alias_false","cleanup_geometry_false","cleanup_effects_false",
        "cleanup_capture_context_false","cleanup_null_context_false","cleanup_observe_context_false",
        "cleanup_owner_false","cleanup_scalar_below_sum","argument_guards","group_guard_before_read"};
    private static final String[] ALIASES={"adapterSessionAliasDropped","adapterSearchAliasDropped",
        "adapterCandidateAliasDropped","adapterRouteResultAliasDropped","adapterBoardTemplateAliasesDropped"};
    private static int parentConstructions;
    private CurrentResultProjectionsCanary() { }

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
        output.put("schema",SCHEMA); output.put("mode",mode);
        output.put("status",pass?"PASS":"FAIL"); output.put("names",names);
        output.put("cases",cases); output.put("parentConstructions",Integer.valueOf(parentConstructions));
        output.put("error",valid?(pass?"":"CANARY_CASE_FAILED"):"CANARY_ARGUMENTS");
        System.out.println(json(output));
        if (!pass) System.exit(1);
    }

    private static Map<String,Object> record(String name) {
        Map<String,Object> r=new LinkedHashMap<String,Object>();
        r.put("name",name); r.put("status","PASS"); r.put("phase","complete"); r.put("reason","OK");
        r.put("payloadJson",""); r.put("observedError",""); r.put("rejections",Long.valueOf(0));
        String[] numeric={"units","copiedEntries","copyChunks","groups","priorGroups","groupUnits",
            "maxGroupUnits","maxGroupNanos"};
        for (String key:numeric) r.put(key,Long.valueOf(0));
        r.put("meterOpen",Boolean.FALSE);
        String[] counters={"helperUnits","helperCopies","helperChunks","operationNanos",
            "totalElapsedNanos","openGroupElapsedNanos"};
        for (String key:counters) r.put(key,Long.valueOf(0));
        r.put("cleanupDisposition","NO_OWNED_OS_RESOURCES"); r.put("inputUnchanged",Boolean.TRUE);
        r.put("listsTransferred",Boolean.FALSE); r.put("externalContainersDetached",Boolean.FALSE);
        r.put("noInputContainersRetained",Boolean.FALSE); r.put("sourceReads",Long.valueOf(0));
        r.put("sourceSemanticRejectionExpected",Boolean.FALSE); r.put("discarded",Boolean.FALSE);
        return r;
    }

    private static Map<String,Object> run(String name) {
        final long operationStarted=System.nanoTime();
        Map<String,Object> r=record(name);
        CohortTerminalTelemetryStudy.SetupMeter m=null;
        String phase="meter";
        try {
            CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
            parentConstructions++;
            m=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(System.nanoTime()));
            if (name.startsWith("cohort_")) {
                phase="cohort_projection"; cohortCase(name,m,r);
            } else if ("argument_guards".equals(name)) {
                phase="argument_guards"; argumentCase(m,r);
            } else {
                phase="cleanup_projection"; cleanupCase(name,m,r);
            }
            phase="native_check";
            m.method("check",new Class<?>[0]);
            nativeFields(m,r);
            assertObservations(name,r);
        } catch (Throwable failure) {
            r.put("status","FAIL"); r.put("phase",phase); r.put("reason",describe(failure));
            if (m!=null) {
                try { m.method("check",new Class<?>[0]); nativeFields(m,r); }
                catch (Throwable meterFailure) {
                    r.put("reason",describe(failure)+";"+describe(meterFailure));
                }
                r.put("discarded",Boolean.TRUE);
                r.put("cleanupDisposition","METER_DISCARDED_NO_OS_RESOURCES");
            }
        }
        r.put("operationNanos",Long.valueOf(elapsed(operationStarted,System.nanoTime())));
        return r;
    }

    private static void cohortCase(String name,CohortTerminalTelemetryStudy.SetupMeter m,
            Map<String,Object> r) throws Exception {
        List<String> original=new ArrayList<String>();
        for (int i=0;i<60;i++) original.add("SYNTH_N"+(i<10?"0":"")+i);
        List<String> pair=new ArrayList<String>(Arrays.asList("SYNTH_N37","SYNTH_N11"));
        // Literal fixture order is independent of deriveCohort and of its returned payload.
        int[] requestedOrder={37,11,0,1,2,3,4,5,6,7,8,9,10,12,13,14,15,16,17,18,19,20,
            21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,38,39,40,41,42,43,44,45,
            46,47,48,49,50,51,52,53,54,55,56,57,58,59};
        List<String> requested=new ArrayList<String>();
        for (int index:requestedOrder) requested.add(original.get(index));
        Map<String,Object> disposition=new LinkedHashMap<String,Object>();
        disposition.put("cohortNetIds",pair); disposition.put("status","RUN");
        disposition.put("reason","TRIAL_EXECUTED"); disposition.put("feedbackValidated",Boolean.TRUE);
        disposition.put("orderChanged",Boolean.TRUE); disposition.put("capturedFailedNetId",pair.get(0));
        disposition.put("controlFailedNetId",pair.get(1)); disposition.put("requestedNetIds",requested);
        Map<String,Object> prior=new LinkedHashMap<String,Object>(); prior.put("trialDisposition",disposition);
        Map<String,Object> input=new LinkedHashMap<String,Object>(); input.put("qualifiedPrior",prior);
        String expected="";
        if ("cohort_requested_mismatch".equals(name)) {
            requested.set(0,"SYNTH_N11"); requested.set(1,"SYNTH_N37"); expected="COHORT_REQUESTED_VECTOR";
        } else if ("cohort_duplicate_capture".equals(name)) {
            original.set(20,"SYNTH_N19"); expected="COHORT_VECTOR_DUPLICATE";
        } else if ("cohort_missing_capture".equals(name)) {
            original.set(37,"SYNTH_MISSING"); expected="COHORT_NOT_IN_CAPTURE";
        } else if ("cohort_incompatible_disposition".equals(name)) {
            disposition.put("status","SKIP"); expected="COHORT_DISPOSITION";
        }
        List<Object> fixtures=new ArrayList<Object>(); fixtures.add(input); fixtures.add(original);
        String before=snapshot(fixtures);
        Map<String,Object> payload=null;
        try {
            CohortTerminalTelemetryStudy.Cohort derived=
                CohortTerminalTelemetryStudy.deriveCohort(input,original,m);
            check(expected.length()==0,"EXPECTED_COHORT_REJECTION");
            long[] debit=debit(m);
            try { payload=CurrentResultProjections.cohort(m,derived); }
            finally { helperDelta(m,debit,r); }
            m.method("check",new Class<?>[0]);
            check(payload.size()==2 && payload.containsKey("netIds") &&
                payload.containsKey("requestedNetIds"),"COHORT_OUTPUT_KEYS");
            boolean transferred=payload.get("netIds")==derived.netIds &&
                payload.get("requestedNetIds")==derived.requested;
            check(transferred,"COHORT_TRANSFER");
            boolean detached=!retains(payload,containers(fixtures));
            check(detached,"COHORT_EXTERNAL_CONTAINERS");
            check(derived.netIds.equals(pair) && derived.requested.equals(requested),"COHORT_FIXTURE_ORDER");
            r.put("listsTransferred",Boolean.valueOf(transferred));
            r.put("externalContainersDetached",Boolean.valueOf(detached));
            r.put("payloadJson",json(payload));
            m.finish();
        } catch (IOException failure) {
            check(expected.length()!=0 && expected.equals(failure.getMessage()),"UNEXPECTED_COHORT_ERROR:"+failure);
            check(payload==null,"COHORT_ERROR_RETURNED_OUTPUT");
            rejected(r,failure.getMessage(),1);
            m.method("check",new Class<?>[0]);
            // Partial open Meter is deliberately discarded, never finished.
        }
        boolean unchanged=before.equals(snapshot(fixtures));
        r.put("inputUnchanged",Boolean.valueOf(unchanged)); check(unchanged,"COHORT_INPUT_MUTATION");
    }

    private static final class ReadCounter { long reads; }
    private static final class CountingMap extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID=1L;
        final ReadCounter counter;
        CountingMap(ReadCounter counter) { this.counter=counter; }
        public Object get(Object key) { counter.reads++; return super.get(key); }
    }
    private static final class CleanupFixture {
        final ReadCounter counter=new ReadCounter();
        final CountingMap capture=new CountingMap(counter),template=new CountingMap(counter);
        final CountingMap nullArm=new CountingMap(counter),observeArm=new CountingMap(counter);
        // Source duration metadata stays outside the projection views and helper call.
        final List<Long> sourceDurations=Arrays.asList(Long.valueOf(100),Long.valueOf(200),Long.valueOf(300));
        CleanupFixture() {
            capture.put("ownersReleased",Boolean.TRUE); capture.put("contextRestored",Boolean.TRUE);
            template.put("geometryBeforeSha256",GEOMETRY); template.put("geometryAfterSha256",GEOMETRY);
            template.put("effectsBeforeSha256",EFFECTS); template.put("effectsAfterSha256",EFFECTS);
            capture.put("template",template);
            for (CountingMap arm:Arrays.asList(nullArm,observeArm)) {
                for (String alias:ALIASES) arm.put(alias,Boolean.TRUE);
                arm.put("contextRestored",Boolean.TRUE);
                arm.put("sourceGeometryBeforeSha256",GEOMETRY); arm.put("sourceGeometryAfterSha256",GEOMETRY);
            }
        }
        List<Object> views() { return Arrays.<Object>asList(capture,nullArm,observeArm,sourceDurations); }
    }

    private static void cleanupCase(String name,CohortTerminalTelemetryStudy.SetupMeter m,
            Map<String,Object> r) throws Exception {
        CleanupFixture f=new CleanupFixture();
        long scalar=600;
        if ("cleanup_null_alias_false".equals(name)) f.nullArm.put(ALIASES[0],Boolean.FALSE);
        else if ("cleanup_observe_alias_false".equals(name)) f.observeArm.put(ALIASES[0],Boolean.FALSE);
        else if ("cleanup_geometry_false".equals(name)) f.nullArm.put("sourceGeometryAfterSha256",DIFFERENT);
        else if ("cleanup_effects_false".equals(name)) f.template.put("effectsAfterSha256",DIFFERENT);
        else if ("cleanup_capture_context_false".equals(name)) f.capture.put("contextRestored",Boolean.FALSE);
        else if ("cleanup_null_context_false".equals(name)) f.nullArm.put("contextRestored",Boolean.FALSE);
        else if ("cleanup_observe_context_false".equals(name)) f.observeArm.put("contextRestored",Boolean.FALSE);
        else if ("cleanup_owner_false".equals(name)) f.capture.put("ownersReleased",Boolean.FALSE);
        else if ("cleanup_scalar_below_sum".equals(name)) scalar=599;
        String before=snapshot(f.views());
        boolean guard="group_guard_before_read".equals(name);
        if (guard) {
            for (int i=0;i<640;i++) { m.charge(1); m.finish(); }
        }
        long[] debit=debit(m);
        Map<String,Object> payload=null;
        try {
            try { payload=CurrentResultProjections.cleanup(m,f.capture,f.nullArm,f.observeArm,scalar); }
            finally { helperDelta(m,debit,r); }
            m.method("check",new Class<?>[0]);
            check(!guard,"EXPECTED_GROUP_GUARD");
            check(payload.size()==10,"CLEANUP_OUTPUT_KEYS");
            boolean detached=!retains(payload,containers(f.views()));
            check(detached,"CLEANUP_INPUT_RETAINED");
            r.put("noInputContainersRetained",Boolean.valueOf(detached));
            r.put("sourceSemanticRejectionExpected",Boolean.valueOf(!"cleanup_true".equals(name)));
            r.put("payloadJson",json(payload));
            m.finish();
        } catch (IOException failure) {
            check(guard && "native wrapper exceeded 640 measured groups".equals(failure.getMessage()),
                "UNEXPECTED_CLEANUP_ERROR:"+failure);
            check(payload==null,"GROUP_GUARD_RETURNED_OUTPUT");
            rejected(r,failure.getMessage(),1);
            m.method("check",new Class<?>[0]);
        }
        boolean unchanged=before.equals(snapshot(f.views()));
        r.put("inputUnchanged",Boolean.valueOf(unchanged)); check(unchanged,"CLEANUP_INPUT_MUTATION");
        r.put("sourceReads",Long.valueOf(f.counter.reads));
        check(f.counter.reads==(guard?0:23),"CLEANUP_ACTUAL_READS");
    }

    private static void argumentCase(CohortTerminalTelemetryStudy.SetupMeter m,Map<String,Object> r)
            throws Exception {
        CleanupFixture f=new CleanupFixture(); String before=snapshot(f.views());
        long[] debit=debit(m); int count=0;
        try { CurrentResultProjections.cohort(m,null); throw new AssertionError("NULL_COHORT_ACCEPTED"); }
        catch (IOException failure) { argumentError(failure); count++; m.method("check",new Class<?>[0]); }
        try { CurrentResultProjections.cleanup(m,null,f.nullArm,f.observeArm,600); throw new AssertionError("NULL_CAPTURE_ACCEPTED"); }
        catch (IOException failure) { argumentError(failure); count++; m.method("check",new Class<?>[0]); }
        try { CurrentResultProjections.cleanup(null,f.capture,f.nullArm,f.observeArm,600); throw new AssertionError("NULL_METER_ACCEPTED"); }
        catch (IOException failure) { argumentError(failure); count++; m.method("check",new Class<?>[0]); }
        helperDelta(m,debit,r); rejected(r,"RESULT_PROJECTION_ARGUMENT",count);
        boolean unchanged=before.equals(snapshot(f.views()));
        r.put("inputUnchanged",Boolean.valueOf(unchanged)); check(unchanged,"ARGUMENT_INPUT_MUTATION");
        r.put("sourceReads",Long.valueOf(f.counter.reads)); check(f.counter.reads==0,"ARGUMENT_SOURCE_READ");
    }
    private static void argumentError(IOException failure) {
        check("RESULT_PROJECTION_ARGUMENT".equals(failure.getMessage()),"ARGUMENT_ERROR:"+failure);
    }
    private static void rejected(Map<String,Object> r,String error,int count) {
        r.put("observedError",error); r.put("rejections",Long.valueOf(count));
        r.put("discarded",Boolean.TRUE); r.put("cleanupDisposition","METER_DISCARDED_NO_OS_RESOURCES");
    }
    private static long number(CohortTerminalTelemetryStudy.SetupMeter m,String name) throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(m.meter,name)).longValue();
    }
    private static long[] debit(CohortTerminalTelemetryStudy.SetupMeter m) throws Exception {
        return new long[]{number(m,"units"),number(m,"copiedEntries"),number(m,"copyChunks")};
    }
    private static void helperDelta(CohortTerminalTelemetryStudy.SetupMeter m,long[] before,
            Map<String,Object> r) throws Exception {
        long[] after=debit(m);
        r.put("helperUnits",Long.valueOf(after[0]-before[0]));
        r.put("helperCopies",Long.valueOf(after[1]-before[1]));
        r.put("helperChunks",Long.valueOf(after[2]-before[2]));
    }
    private static void nativeFields(CohortTerminalTelemetryStudy.SetupMeter m,Map<String,Object> r)
            throws Exception {
        String[] keys={"units","copiedEntries","copyChunks","groups","priorGroups","groupUnits",
            "maxGroupUnits","maxGroupNanos"};
        for (String key:keys) r.put(key,Long.valueOf(number(m,key)));
        boolean open=Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(m.meter,"open"));
        r.put("meterOpen",Boolean.valueOf(open));
        long now=System.nanoTime();
        r.put("totalElapsedNanos",Long.valueOf(elapsed(number(m,"started"),now)));
        r.put("openGroupElapsedNanos",Long.valueOf(open?elapsed(number(m,"groupStarted"),now):0));
    }
    private static void assertObservations(String name,Map<String,Object> r) {
        boolean cohort="cohort_transfer".equals(name),cleanup=name.startsWith("cleanup_");
        boolean guard="group_guard_before_read".equals(name),arguments="argument_guards".equals(name);
        long units=cleanup?55:cohort?260:guard?640:arguments?0:
            "cohort_duplicate_capture".equals(name)?137:"cohort_missing_capture".equals(name)?254:
            "cohort_incompatible_disposition".equals(name)?12:255;
        long copies=cleanup?10:cohort?124:guard || arguments?0:
            "cohort_duplicate_capture".equals(name)?83:"cohort_incompatible_disposition".equals(name)?0:122;
        long chunks=cleanup?1:cohort?63:guard || arguments?0:
            "cohort_duplicate_capture".equals(name)?23:"cohort_incompatible_disposition".equals(name)?0:62;
        check(n(r,"units")==units && n(r,"copiedEntries")==copies && n(r,"copyChunks")==chunks,"NATIVE_DEBIT");
        check(n(r,"groups")==((cohort || cleanup)?1:guard?640:0) && n(r,"priorGroups")==0,"NATIVE_GROUPS");
        check(n(r,"groupUnits")== (guard?1:units),"NATIVE_GROUP_UNITS");
        check(n(r,"maxGroupUnits")==((cohort || cleanup)?units:guard?1:0),"NATIVE_MAX_GROUP_UNITS");
        boolean open=!cohort && !cleanup && !guard && !arguments;
        check(Boolean.valueOf(open).equals(r.get("meterOpen")),"NATIVE_OPEN");
        check(n(r,"helperUnits")== (cohort?5:cleanup?55:0) &&
            n(r,"helperCopies")== (cohort?2:cleanup?10:0) &&
            n(r,"helperChunks")== (cohort || cleanup?1:0),"HELPER_DELTA");
        check(n(r,"rejections")== (cohort || cleanup?0:arguments?3:1),"REJECTIONS");
        check(n(r,"sourceReads")== (cleanup?23:0),"READ_CENSUS");
        check(n(r,"totalElapsedNanos")<=90000000000L && n(r,"maxGroupNanos")<=5000000000L &&
            n(r,"openGroupElapsedNanos")<=5000000000L && (open || n(r,"openGroupElapsedNanos")==0),"NATIVE_CLOCKS");
        check(Boolean.TRUE.equals(r.get("inputUnchanged")),"INPUT_CHANGED");
    }
    private static long n(Map<String,Object> r,String key) { return ((Number)r.get(key)).longValue(); }
    private static long elapsed(long start,long end) { long value=end-start; check(value>=0,"NEGATIVE_CLOCK"); return value; }

    // Snapshot uses entrySet, never CountingMap.get; flattening keeps the unchanged writer's depth bound.
    private static String snapshot(Object value) {
        List<String> tokens=new ArrayList<String>(); snapshotTokens(value,tokens); return json(tokens);
    }
    private static void snapshotTokens(Object value,List<String> tokens) {
        if (value instanceof Map) {
            tokens.add("MAP");
            for (Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) {
                tokens.add("KEY:"+e.getKey()); snapshotTokens(e.getValue(),tokens);
            }
            tokens.add("END_MAP");
        } else if (value instanceof List) {
            tokens.add("LIST"); for (Object item:(List<?>)value) snapshotTokens(item,tokens); tokens.add("END_LIST");
        } else tokens.add(value.getClass().getName()+":"+value);
    }
    private static IdentityHashMap<Object,Boolean> containers(Object value) {
        IdentityHashMap<Object,Boolean> found=new IdentityHashMap<Object,Boolean>(); collect(value,found); return found;
    }
    private static void collect(Object value,IdentityHashMap<Object,Boolean> found) {
        if (!(value instanceof Map || value instanceof List) || found.put(value,Boolean.TRUE)!=null) return;
        if (value instanceof Map) for (Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) collect(e.getValue(),found);
        else for (Object item:(List<?>)value) collect(item,found);
    }
    private static boolean retains(Object value,IdentityHashMap<Object,Boolean> inputs) {
        if (!(value instanceof Map || value instanceof List)) return false;
        if (inputs.containsKey(value)) return true;
        if (value instanceof Map) {
            for (Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) if (retains(e.getValue(),inputs)) return true;
        } else for (Object item:(List<?>)value) if (retains(item,inputs)) return true;
        return false;
    }
    private static String describe(Throwable failure) {
        String value=failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());
        StringBuilder out=new StringBuilder();
        for (int i=0;i<value.length() && i<240;i++) { char c=value.charAt(i); out.append(c>=32 && c<=126?c:'?'); }
        return out.toString();
    }
    private static void check(boolean ok,String reason) { if (!ok) throw new AssertionError(reason); }

    private static String json(Object value) {
        StringBuilder out=new StringBuilder();
        write(value,out,0); check(out.length()<=1048576,"SUMMARY_BOUND");
        return out.toString();
    }
    private static void write(Object value,StringBuilder out,int depth) {
        check(depth<=4 && out.length()<=1048576,"SUMMARY_BOUND");
        if (value instanceof String) {
            out.append('"');
            String s=(String)value;
            for (int i=0;i<s.length();i++) {
                char c=s.charAt(i);
                check(c>=32 && c<=126,"SUMMARY_ASCII");
                if (c=='"' || c=='\\') out.append('\\');
                out.append(c);
            }
            out.append('"');
        } else if (value instanceof Number || value instanceof Boolean) out.append(value);
        else if (value instanceof Map) {
            out.append('{'); boolean first=true;
            for (Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) {
                if (!first) out.append(',');
                first=false; write(e.getKey(),out,depth+1); out.append(':');
                write(e.getValue(),out,depth+1);
            }
            out.append('}');
        } else if (value instanceof List) {
            out.append('['); boolean first=true;
            for (Object item:(List<?>)value) {
                if (!first) out.append(',');
                first=false; write(item,out,depth+1);
            }
            out.append(']');
        } else throw new AssertionError("SUMMARY_TYPE");
        check(out.length()<=1048576,"SUMMARY_BOUND");
    }
}
