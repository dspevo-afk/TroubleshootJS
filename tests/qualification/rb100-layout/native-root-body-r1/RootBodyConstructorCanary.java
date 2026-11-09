package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Constructor-only synthetic canary; never a genuine capture or full-D receipt. */
public final class RootBodyConstructorCanary {
    private static final String RUN="00000000-0000-0000-0000-000000000001";
    private static final String SOURCE="1111111111111111111111111111111111111111111111111111111111111111";
    private static final String CONTEXT="3333333333333333333333333333333333333333333333333333333333333333";
    private static final String[] KEYS={"schema","mode","status","runId",
        "sourceManifestSha256","wireContractSha256","inputContextSha256",
        "qualifiedPrior","originalCapture","cohort","arms","work","cleanup"};
    private static final String[] SMOKE={"root_smoke_actual_meter"};
    private static final String[] SUITE={
        "root_order_headers_null_slots","root_fresh_borrowed_inputs",
        "root_trap_children_unobserved","root_null_meter_guard",
        "root_copy_allowance_12_reject",
        "root_copy_admission_units_13_before_allocation_fee",
        "root_closed_meter_normal_start","root_unit_group_roll",
        "root_group_640_reject","root_expired_operation_reject",
        "root_expired_group_reject","root_staged_bad_headers_deferred",
        "root_staged_null_children_deferred","root_staged_incomplete_slots_reject",
        "root_completed_synthetic_codec_oracle"};

    private RootBodyConstructorCanary() { }

    public static void main(String[] args) {
        String mode=args!=null && args.length==1 && args[0]!=null?args[0]:"INVALID";
        boolean valid="SMOKE".equals(mode) || "SUITE".equals(mode);
        String[] names=valid?("SMOKE".equals(mode)?SMOKE:SUITE):new String[0];
        StringBuilder records=new StringBuilder();
        boolean pass=valid;
        for (int i=0;i<names.length;i++) {
            Result result=run(names[i],"SMOKE".equals(mode)?-1:i);
            if (i!=0) records.append(',');
            records.append(result.json());
            pass=pass && result.pass;
        }
        StringBuilder out=new StringBuilder("{\"mode\":").append(quote(mode));
        out.append(",\"status\":").append(quote(pass?"PASS":"FAIL"));
        out.append(",\"names\":[");
        for (int i=0;i<names.length;i++) {
            if (i!=0) out.append(',');
            out.append(quote(names[i]));
        }
        out.append("],\"cases\":[").append(records).append("]}");
        System.out.println(out.toString());
        if (!pass) System.exit(1);
    }

    private static final class State {
        CohortTerminalTelemetryStudy.SetupMeter meter;
        long baseUnits,baseCopies,baseChunks,baseGroups;
        TrapMap[] traps;
        boolean discardExpired;
        void fresh(long started) throws Exception {
            CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
            meter=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
            baseline();
        }
        void baseline() throws Exception {
            baseUnits=number("units"); baseCopies=number("copiedEntries");
            baseChunks=number("copyChunks"); baseGroups=number("groups");
        }
        long number(String name) throws Exception {
            return ((Number)CohortTerminalTelemetryStudy.field(meter.meter,name)).longValue();
        }
        boolean open() throws Exception {
            return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter,"open"));
        }
        void call(String name) throws Exception { meter.method(name,new Class<?>[0]); }
    }

    private static final class Result {
        final String name;
        boolean pass=true;
        long units,copies,chunks,groups,observedItems,operationNanos,cleanupNanos;
        String cleanupDisposition="NONE",error="",cleanupError="";
        Result(String name) { this.name=name; }
        String json() {
            return "{\"name\":"+quote(name)+",\"status\":"+quote(pass?"PASS":"FAIL")+
                ",\"units\":"+units+",\"copies\":"+copies+",\"chunks\":"+chunks+
                ",\"groups\":"+groups+",\"observedItems\":"+observedItems+
                ",\"operationNanos\":"+operationNanos+",\"cleanupNanos\":"+cleanupNanos+
                ",\"cleanupDisposition\":"+quote(cleanupDisposition)+",\"error\":"+quote(error)+
                ",\"cleanupError\":"+quote(cleanupError)+"}";
        }
    }

    private static Result run(String name,int index) {
        State state=new State(); Result result=new Result(name);
        long operationStarted=System.nanoTime();
        try { exercise(state,index); }
        catch (Throwable failure) { result.pass=false; result.error=describe(failure); }
        finally {
            result.operationNanos=System.nanoTime()-operationStarted;
            long cleanupStarted=System.nanoTime();
            try {
                if (state.traps!=null)
                    for (TrapMap trap:state.traps) result.observedItems+=trap.observations;
                if (state.meter!=null) {
                    result.units=state.number("units")-state.baseUnits;
                    result.copies=state.number("copiedEntries")-state.baseCopies;
                    result.chunks=state.number("copyChunks")-state.baseChunks;
                    result.groups=state.number("groups")-state.baseGroups;
                    if (state.discardExpired)
                        result.cleanupDisposition="EXPECTED_EXPIRED_METER_DISCARDED";
                    else if (state.open()) {
                        state.meter.finish();
                        require(!state.open(),"cleanup did not close meter");
                        result.cleanupDisposition="CLOSED";
                    } else result.cleanupDisposition="ALREADY_CLOSED";
                }
            } catch (Throwable failure) {
                result.pass=false; result.cleanupDisposition="CLEANUP_FAILED";
                result.cleanupError=describe(failure);
            }
            result.cleanupNanos=System.nanoTime()-cleanupStarted;
        }
        return result;
    }

    private static void exercise(State s,int index) throws Exception {
        if (index==3) {
            try { create(null,RUN,SOURCE,CONTEXT,children()); }
            catch (CurrentV2CanonicalCodec.CodecFailure expected) {
                require("BODY_CONSTRUCTOR_METER".equals(expected.code),"null-meter code"); return;
            }
            throw new AssertionError("null meter accepted");
        }
        s.fresh(index==9?System.nanoTime()-91000000000L:System.nanoTime());
        Map<String,Object>[] child=children();
        switch (index) {
        case -1:
        case 0: {
            Map<String,Object> root=create(s.meter,RUN,SOURCE,CONTEXT,child);
            verifyRoot(root,child); delta(s,14,13,1); require(s.open(),"metadata closed"); break;
        }
        case 1: {
            Map<String,Object>[] before=snapshotChildren(child);
            String run=new String(RUN),source=new String(SOURCE),context=new String(CONTEXT);
            Map<String,Object> a=create(s.meter,run,source,context,child);
            Map<String,Object> b=create(s.meter,RUN,SOURCE,CONTEXT,child);
            require(a!=b,"root reused"); verifyRoot(a,child); verifyRoot(b,child);
            require(a.get("runId")==run && a.get("sourceManifestSha256")==source &&
                a.get("inputContextSha256")==context,"header parameter references");
            a.put("qualifiedPrior",new LinkedHashMap<String,Object>());
            require(b.containsKey("qualifiedPrior") && b.get("qualifiedPrior")==null,"roots alias");
            for (int i=0;i<child.length;i++)
                require(child[i].equals(before[i]) && child[i].size()==1,"input changed");
            delta(s,28,26,2); break;
        }
        case 2: {
            s.traps=new TrapMap[]{new TrapMap(),new TrapMap(),new TrapMap(),new TrapMap()};
            Map<String,Object> root=create(s.meter,RUN,SOURCE,CONTEXT,s.traps);
            verifyRoot(root,s.traps);
            for (TrapMap trap:s.traps) require(trap.observations==0,"child observed");
            delta(s,14,13,1); break;
        }
        case 4:
            s.meter.method("setCopyAllowance",new Class<?>[]{int.class},Integer.valueOf(12));
            expectAdmission(s,child,IOException.class,"copy exceeds its prebounded chunk or aggregate allowance");
            delta(s,0,0,0); require(!s.open(),"allowance rejection opened group"); break;
        case 5:
            closeGroups(s,639); s.call("beginGroup"); s.meter.charge(32755); s.baseline();
            expectAdmission(s,child,IOException.class,"native wrapper exceeded 640 measured groups");
            delta(s,13,13,1);
            require(s.number("groups")==640 && !s.open(),"allocation-fee boundary state"); break;
        case 6:
            require(!s.open(),"new meter already open");
            verifyRoot(create(s.meter,RUN,SOURCE,CONTEXT,child),child);
            delta(s,14,13,1);
            require(s.open() && s.number("groups")==0 && s.number("groupUnits")==14,"normal start"); break;
        case 7:
            s.meter.charge(32760); s.baseline();
            verifyRoot(create(s.meter,RUN,SOURCE,CONTEXT,child),child);
            delta(s,14,13,1);
            require(s.number("groups")==1 && s.open() && s.number("groupUnits")==14,"normal roll"); break;
        case 8:
            closeGroups(s,640); s.baseline();
            expectAdmission(s,child,IOException.class,"native wrapper exceeded 640 measured groups");
            delta(s,0,0,0); require(!s.open(),"group cap opened group"); break;
        case 9:
            expectAdmission(s,child,IllegalStateException.class,"failed-first process exceeded 90 seconds");
            delta(s,0,0,0); require(!s.open(),"expired operation opened group");
            s.discardExpired=true; break;
        case 10:
            s.call("beginGroup"); s.baseline();
            Thread.sleep(5100L);
            expectAdmission(s,child,IllegalStateException.class,"native wrapper group exceeded five seconds");
            delta(s,0,0,0); require(s.open(),"expected expired group state");
            s.discardExpired=true; break;
        case 11: {
            Map<String,Object> badRun=create(s.meter,"bad-run",SOURCE,CONTEXT,child);
            fillSlots(badRun); expectCodec(badRun,"BODY_RUN_ID");
            Map<String,Object> badSource=create(s.meter,RUN,"bad-source",CONTEXT,child);
            fillSlots(badSource); expectCodec(badSource,"BODY_SOURCE_ID");
            Map<String,Object> badContext=create(s.meter,RUN,SOURCE,null,child);
            fillSlots(badContext); expectCodec(badContext,"BODY_CONTEXT_ID");
            require("bad-run".equals(badRun.get("runId")) &&
                "bad-source".equals(badSource.get("sourceManifestSha256")) &&
                badContext.get("inputContextSha256")==null,"invalid headers not staged");
            badRun.put("runId",RUN);
            String[] fixed={"schema","mode","status","wireContractSha256"};
            String[] codes={"BODY_SCHEMA","BODY_MODE","BODY_STATUS","BODY_WIRE_ID"};
            for (int i=0;i<fixed.length;i++) {
                Object original=badRun.put(fixed[i],"wrong-fixed-header");
                expectCodec(badRun,codes[i]); badRun.put(fixed[i],original);
            }
            delta(s,42,39,3); break;
        }
        case 12: {
            Map<String,Object>[] absent=nullChildren();
            Map<String,Object> root=create(s.meter,RUN,SOURCE,CONTEXT,absent);
            verifyRoot(root,absent); fillSlots(root); expectCodec(root,"BODY_ROOT_CONTAINER");
            delta(s,14,13,1); break;
        }
        case 13: {
            Map<String,Object> root=create(s.meter,RUN,SOURCE,CONTEXT,child);
            expectCodec(root,"BODY_ROOT_CONTAINER");
            root.put("qualifiedPrior",new LinkedHashMap<String,Object>());
            expectCodec(root,"BODY_ROOT_CONTAINER");
            require(root.containsKey("work") && root.get("work")==null,"work slot changed");
            delta(s,14,13,1); break;
        }
        case 14: {
            Map<String,Object> oracle=CurrentV2CodecOracle.body();
            Map<String,Object>[] synthetic=oracleChildren(oracle);
            Map<String,Object> root=create(s.meter,RUN,SOURCE,CONTEXT,synthetic);
            verifyRoot(root,synthetic);
            root.put("qualifiedPrior",oracle.get("qualifiedPrior"));
            root.put("work",oracle.get("work"));
            Bytes sink=new Bytes(); CurrentV2CanonicalCodec.writeBody(root,sink);
            byte[] actual=sink.bytes.toByteArray();
            byte[] expected=CurrentV2CodecOracle.BODY_JSON.getBytes("US-ASCII");
            require(java.util.Arrays.equals(actual,expected),"independent BODY_JSON mismatch");
            require(java.util.Arrays.equals(MessageDigest.getInstance("SHA-256").digest(actual),
                MessageDigest.getInstance("SHA-256").digest(expected)),"oracle digest mismatch");
            delta(s,14,13,1); break;
        }
        default: throw new AssertionError("unknown frozen case");
        }
    }

    private static Map<String,Object> create(CohortTerminalTelemetryStudy.SetupMeter meter,
            String run,String source,String context,Map<String,Object>[] child) throws Exception {
        return CurrentV2CanonicalCodec.createBody(meter,run,source,context,
            child[0],child[1],child[2],child[3]);
    }
    private static void verifyRoot(Map<String,Object> root,Map<String,Object>[] child) {
        require(root.getClass()==LinkedHashMap.class,"exact root class");
        require(root.size()==13,"root size"); int i=0;
        for (String key:root.keySet()) require(KEYS[i++].equals(key),"literal root order");
        require(i==13,"root order count");
        require("tsj-cohort-terminal-result-v2".equals(root.get("schema")),"schema");
        require("OBSERVED_COHORT_TERMINAL_TELEMETRY".equals(root.get("mode")),"mode");
        require("COMPLETED_DIAGNOSTIC_ONLY".equals(root.get("status")),"status");
        require("1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed".
            equals(root.get("wireContractSha256")),"wire");
        require(RUN.equals(root.get("runId")) && SOURCE.equals(root.get("sourceManifestSha256")) &&
            CONTEXT.equals(root.get("inputContextSha256")),"header parameter values");
        require(root.containsKey("qualifiedPrior") && root.get("qualifiedPrior")==null &&
            root.containsKey("work") && root.get("work")==null,"present null slots");
        require(root.get("originalCapture")==child[0] && root.get("cohort")==child[1] &&
            root.get("arms")==child[2] && root.get("cleanup")==child[3],"borrowed children");
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object>[] children() {
        Map<String,Object>[] result=(Map<String,Object>[])new Map<?,?>[4];
        for (int i=0;i<4;i++) {
            result[i]=new LinkedHashMap<String,Object>();
            result[i].put("syntheticChild",Integer.valueOf(i));
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object>[] nullChildren() {
        return (Map<String,Object>[])new Map<?,?>[4];
    }
    private static Map<String,Object>[] snapshotChildren(Map<String,Object>[] child) {
        Map<String,Object>[] copy=nullChildren();
        for (int i=0;i<4;i++) copy[i]=new LinkedHashMap<String,Object>(child[i]);
        return copy;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object>[] oracleChildren(Map<String,Object> oracle) {
        Map<String,Object>[] result=nullChildren();
        result[0]=(Map<String,Object>)oracle.get("originalCapture");
        result[1]=(Map<String,Object>)oracle.get("cohort");
        result[2]=(Map<String,Object>)oracle.get("arms");
        result[3]=(Map<String,Object>)oracle.get("cleanup"); return result;
    }
    private static void fillSlots(Map<String,Object> root) {
        root.put("qualifiedPrior",new LinkedHashMap<String,Object>());
        root.put("work",new LinkedHashMap<String,Object>());
    }
    private static void closeGroups(State s,int count) throws Exception {
        for (int i=0;i<count;i++) { s.call("beginGroup"); s.meter.finish(); }
    }
    private static void delta(State s,long units,long copies,long chunks) throws Exception {
        require(s.number("units")-s.baseUnits==units,"unit debit");
        require(s.number("copiedEntries")-s.baseCopies==copies,"copy debit");
        require(s.number("copyChunks")-s.baseChunks==chunks,"chunk debit");
    }
    private static void expectAdmission(State s,Map<String,Object>[] child,
            Class<?> type,String message) throws Exception {
        try { create(s.meter,RUN,SOURCE,CONTEXT,child); }
        catch (Exception expected) {
            require(expected.getClass()==type && message.equals(expected.getMessage()),
                "admission exception: "+describe(expected)); return;
        }
        throw new AssertionError("admission returned a root");
    }
    private static void expectCodec(Map<String,Object> root,String code) throws Exception {
        try { CurrentV2CanonicalCodec.writeBody(root,new Bytes()); }
        catch (CurrentV2CanonicalCodec.CodecFailure expected) {
            require(code.equals(expected.code),"codec code: "+expected.code); return;
        }
        throw new AssertionError("incomplete/invalid staged body serialized");
    }
    private static final class Bytes implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        public void beforeDataOperation() { require(bytes.size()<1048576,"canary byte cap"); }
        public void writeByte(int ascii) {
            beforeDataOperation(); require(ascii==10 || ascii>=32 && ascii<=126,"ASCII output");
            bytes.write(ascii);
        }
    }
    private static final class TrapMap extends AbstractMap<String,Object> {
        int observations;
        private AssertionError trap() { observations++; return new AssertionError("child observation"); }
        public Set<Map.Entry<String,Object>> entrySet() { throw trap(); }
        public int size() { throw trap(); }
        public boolean isEmpty() { throw trap(); }
        public boolean containsKey(Object key) { throw trap(); }
        public boolean containsValue(Object value) { throw trap(); }
        public Object get(Object key) { throw trap(); }
        public Object put(String key,Object value) { throw trap(); }
        public void putAll(Map<? extends String,?> value) { throw trap(); }
        public Object remove(Object key) { throw trap(); }
        public void clear() { throw trap(); }
        public Set<String> keySet() { throw trap(); }
        public Collection<Object> values() { throw trap(); }
        public boolean equals(Object other) { throw trap(); }
        public int hashCode() { throw trap(); }
        public String toString() { throw trap(); }
    }
    private static void require(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static String describe(Throwable failure) {
        String message=failure.getMessage();
        if (message!=null && message.length()>512) message=message.substring(0,512);
        return failure.getClass().getSimpleName()+":"+(message==null?"":message);
    }
    private static String quote(String value) {
        StringBuilder out=new StringBuilder("\"");
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (c=='"' || c=='\\') out.append('\\').append(c);
            else if (c<32 || c>126) {
                String hex=Integer.toHexString(c); out.append("\\u");
                for (int j=hex.length();j<4;j++) out.append('0'); out.append(hex);
            } else out.append(c);
        }
        return out.append('"').toString();
    }
}
