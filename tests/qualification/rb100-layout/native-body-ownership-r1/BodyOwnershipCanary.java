package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** Synthetic component only. No input files, genuine arms, graph or publication. */
public final class BodyOwnershipCanary {
    private BodyOwnershipCanary() { }
    private static final String[] NAMES={
        "null-meter", "null-handcount", "empty-map-handcount", "empty-list-handcount",
        "one-map-handcount", "three-list-handcount", "mixed-handcount", "sibling-alias",
        "copy-capacity-zero", "debit-order-map", "scalar-domain-sharing",
        "disjoint-equal-containers", "nested-alias", "self-list-cycle", "self-map-cycle",
        "ancestor-cycle", "unsupported-values", "unsupported-keys", "depth64", "depth65",
        "map-key-depth65", "key-inclusive-visits-1000000", "key-inclusive-visits-1000001",
        "copy-capacity-one-of-two", "group-roll", "group-limit", "operation-expiry",
        "group-expiry", "r7-alias-before-close", "r7-disjoint-handoff"
    };
    private static final int SMOKE_COUNT=9;
    private static final String[] WORK_KEYS={
        "elapsedCutoffNanos","cleanupNanos","groupsBeforeExport","wrapperGroupsBeforeExport",
        "maxGroupNanosBeforeExport","maximumUnitsPerGroupBeforeExport","unitsBeforeExport",
        "setupUnits","setupGroups","setupCopiedEntries","setupCopyChunks",
        "originalCaptureOwnerUnits","originalCaptureWrapperUnits","originalCaptureCallbackUnits",
        "originalCaptureDiagnosticUnits","originalCaptureGroups","originalCaptureCopiedEntries",
        "originalCaptureCopyChunks","nullOwnerUnits","nullWrapperUnits","nullObserverUnits",
        "observeOwnerUnits","observeWrapperUnits","observeObserverUnits","observerUnits",
        "meterUnits","metadataUnits","metadataGroups","metadataCopiedEntries",
        "metadataCopyChunks","copiedEntries","copyChunks","copyChunkLimit","copiedEntryLimit"
    };

    public static void main(String[] args) {
        String mode=args!=null && args.length==1?args[0]:"INVALID";
        boolean valid="SMOKE".equals(mode) || "SUITE".equals(mode);
        int first="SUITE".equals(mode)?SMOKE_COUNT:0;
        int count=valid?("SMOKE".equals(mode)?SMOKE_COUNT:NAMES.length-SMOKE_COUNT):0;
        boolean pass=valid;
        StringBuilder out=new StringBuilder("{\"mode\":").append(quote(mode));
        StringBuilder records=new StringBuilder();
        for (int i=0;i<count;i++) {
            Record r=new Record(NAMES[first+i]);
            long started=System.nanoTime();
            try { run(first+i,r); }
            catch (Throwable failure) {
                r.status="FAIL";
                r.error=failure.getClass().getName()+":"+failure.getMessage();
                pass=false;
            } finally {
                r.operationNanos=System.nanoTime()-started;
                long cleanup=System.nanoTime();
                if (r.meter!=null) {
                    try { if (!r.discardExpected) r.meter.finish(); }
                    catch (Throwable failure) {
                        r.status="FAIL"; pass=false;
                        r.cleanupError=failure.getClass().getName()+":"+failure.getMessage();
                    }
                    try {
                        r.units=number(r.meter,"units");
                        r.copies=number(r.meter,"copiedEntries");
                        r.chunks=number(r.meter,"copyChunks");
                        r.groups=number(r.meter,"groups");
                        require(r.discardExpected || !flag(r.meter,"open"),"CLEANUP_OPEN");
                    } catch (Throwable failure) {
                        r.status="FAIL"; pass=false;
                        r.cleanupError=failure.getClass().getName()+":"+failure.getMessage();
                    }
                }
                r.cleanupNanos=System.nanoTime()-cleanup;
            }
            if (i!=0) records.append(',');
            records.append(r.json());
        }
        out.append(",\"status\":").append(quote(pass?"PASS":"FAIL"));
        out.append(",\"names\":[");
        for (int i=0;i<count;i++) { if (i!=0) out.append(','); out.append(quote(NAMES[first+i])); }
        out.append("],\"cases\":[").append(records).append("]}");
        System.out.println(out.toString());
        if (!pass) System.exit(1);
    }

    private static final class Record {
        final String name;
        String status="PASS",error="",cleanupError="";
        CohortTerminalTelemetryStudy.SetupMeter meter;
        boolean discardExpected;
        long units,copies,chunks,groups,operationNanos,cleanupNanos,observedItems;
        Record(String name) { this.name=name; }
        String json() {
            return "{\"name\":"+quote(name)+",\"status\":"+quote(status)+
                ",\"units\":"+units+",\"copies\":"+copies+",\"chunks\":"+chunks+
                ",\"groups\":"+groups+",\"observedItems\":"+observedItems+
                ",\"operationNanos\":"+operationNanos+",\"cleanupNanos\":"+cleanupNanos+
                ",\"error\":"+quote(error)+",\"cleanupError\":"+quote(cleanupError)+
                ",\"cleanupDisposition\":"+quote(discardExpected?
                    "DISCARDED_EXPECTED_REJECTION":"CLOSED")+"}";
        }
    }
    private static CohortTerminalTelemetryStudy.SetupMeter meter(Record r) throws Exception {
        return meter(r,System.nanoTime());
    }
    private static CohortTerminalTelemetryStudy.SetupMeter meter(Record r,long started)
            throws Exception {
        CohortTerminalTelemetryStudy.Parent p=new CohortTerminalTelemetryStudy.Parent();
        r.meter=new CohortTerminalTelemetryStudy.SetupMeter(p,p.meter(started));
        return r.meter;
    }
    private static void run(int i,Record r) throws Exception {
        if (i==0) {
            TrapList trap=new TrapList();
            expect(null,trap,"BODY_OWNERSHIP_ARGUMENT");
            require(!trap.observed,"NULL_METER_OBSERVED_BODY"); return;
        }
        CohortTerminalTelemetryStudy.SetupMeter m=meter(r);
        switch(i) {
        case 1: positive(m,null,5,0); break;
        case 2: {
            Map<String,Object> body=map(); positive(m,body,11,1);
            require(body.isEmpty(),"EMPTY_MAP_CHANGED"); break;
        }
        case 3: {
            List<Object> body=list(); positive(m,body,10,1);
            require(body.isEmpty(),"EMPTY_LIST_CHANGED"); break;
        }
        case 4: {
            Map<String,Object> body=map(); body.put("a",null);
            positive(m,body,21,1);
            require(body.size()==1 && body.containsKey("a") && body.get("a")==null,"MAP_CHANGED");
            break;
        }
        case 5: {
            List<Object> body=list(); body.add(null); body.add(null); body.add(null);
            positive(m,body,25,1);
            require(body.size()==3 && body.get(0)==null && body.get(2)==null,"LIST_CHANGED");
            break;
        }
        case 6: {
            Map<String,Object> body=map(); List<Object> child=list();
            child.add(null); child.add(null); body.put("a",child);
            positive(m,body,36,2);
            require(body.size()==1 && body.get("a")==child && child.size()==2 &&
                child.get(0)==null && child.get(1)==null,"MIXED_CHANGED"); break;
        }
        case 7: {
            List<Object> body=list(),shared=list(); body.add(shared); body.add(shared);
            expect(m,body,"BODY_OWNERSHIP_ALIAS");
            require(body.size()==2 && body.get(0)==shared && body.get(1)==shared && shared.isEmpty(),
                "ALIAS_CHANGED"); break;
        }
        case 8: {
            m.method("setCopyAllowance",new Class<?>[]{Integer.TYPE},Integer.valueOf(0));
            TrapList body=new TrapList();
            expect(m,body,"copy exceeds its prebounded chunk or aggregate allowance");
            require(!body.observed && number(m,"units")==5 && number(m,"copiedEntries")==0,
                "COPY_NOT_PREPAID"); break;
        }
        case 9: {
            DebitMap body=new DebitMap(m); positive(m,body,21,1);
            require(body.stage==7,"MAP_CALLBACK_CLOSURE"); break;
        }
        case 10: {
            List<Object> body=list(); String text=new String("same");
            Long n=Long.valueOf(41); Integer k=Integer.valueOf(7);
            body.add(null); body.add(Boolean.TRUE); body.add(Boolean.FALSE);
            body.add(k); body.add(k); body.add(n); body.add(n); body.add(text); body.add(text);
            positive(m,body,55,1);
            require(body.size()==9 && body.get(3)==k && body.get(4)==k && body.get(7)==text &&
                body.get(8)==text,"SCALAR_IDENTITIES_CHANGED"); break;
        }
        case 11: {
            List<Object> body=list(),a=list(),b=list(); body.add(a); body.add(b);
            positive(m,body,30,3);
            require(a!=b && a.isEmpty() && b.isEmpty() && body.get(0)==a && body.get(1)==b,
                "EQUAL_CONTAINERS_CHANGED"); break;
        }
        case 12: {
            Map<String,Object> body=map(),a=map(),b=map(),shared=map();
            a.put("x",shared); b.put("y",shared); body.put("a",a); body.put("b",b);
            expect(m,body,"BODY_OWNERSHIP_ALIAS");
            require(a.get("x")==shared && b.get("y")==shared && body.get("a")==a &&
                body.get("b")==b && shared.isEmpty(),"NESTED_ALIAS_CHANGED"); break;
        }
        case 13: {
            List<Object> body=list(); body.add(body); expect(m,body,"BODY_OWNERSHIP_ALIAS");
            require(body.size()==1 && body.get(0)==body,"SELF_LIST_CHANGED"); break;
        }
        case 14: {
            Map<String,Object> body=map(); body.put("x",body);
            expect(m,body,"BODY_OWNERSHIP_ALIAS");
            require(body.size()==1 && body.get("x")==body,"SELF_MAP_CHANGED"); break;
        }
        case 15: {
            List<Object> body=list(); Map<String,Object> child=map(); body.add(child);
            child.put("parent",body); expect(m,body,"BODY_OWNERSHIP_ALIAS");
            require(body.size()==1 && body.get(0)==child && child.get("parent")==body,
                "ANCESTOR_CHANGED"); break;
        }
        case 16: {
            Object[] bad={Double.valueOf(1),Float.valueOf(1),Short.valueOf((short)1),
                Byte.valueOf((byte)1),Character.valueOf('a'),new Object(),new Object[0]};
            for (Object value:bad) expect(m,value,"BODY_OWNERSHIP_VALUE");
            require(number(m,"units")==35 && number(m,"copiedEntries")==0,"VALUE_DEBITS"); break;
        }
        case 17: {
            Object[] keys={null,Integer.valueOf(1),Boolean.TRUE,list(),map()};
            for (Object key:keys) {
                Map<Object,Object> body=new LinkedHashMap<Object,Object>(); body.put(key,null);
                expect(m,body,"BODY_OWNERSHIP_KEY");
                require(body.size()==1 && body.containsKey(key) && body.get(key)==null,"KEY_CHANGED");
            }
            require(number(m,"units")==80 && number(m,"copiedEntries")==5,"KEY_DEBITS"); break;
        }
        case 18: {
            Object body=chain(64,null); Object[] links=chainLinks(body,64);
            positive(m,body,645,64); unchangedChain(body,links,null); break;
        }
        case 19: {
            Object body=chain(65,null); Object[] links=chainLinks(body,65);
            expect(m,body,"BODY_OWNERSHIP_DEPTH"); unchangedChain(body,links,null); break;
        }
        case 20: {
            Map<String,Object> leaf=map(); leaf.put("at65",null);
            Object body=chain(64,leaf); Object[] links=chainLinks(body,64);
            expect(m,body,"BODY_OWNERSHIP_DEPTH"); unchangedChain(body,links,leaf);
            require(leaf.size()==1 && leaf.get("at65")==null,"DEEP_MAP_CHANGED"); break;
        }
        case 21: million(m,r,false); break;
        case 22: million(m,r,true); break;
        case 23: {
            m.method("setCopyAllowance",new Class<?>[]{Integer.TYPE},Integer.valueOf(1));
            List<Object> body=list(); TrapList child=new TrapList(); body.add(child);
            expect(m,body,"copy exceeds its prebounded chunk or aggregate allowance");
            require(body.size()==1 && body.get(0)==child && !child.observed &&
                number(m,"copiedEntries")==1 && number(m,"copyChunks")==1,"SECOND_COPY_ADMISSION");
            break;
        }
        case 24: {
            m.charge(32766); long before=number(m,"units");
            CurrentBodyOwnership.validate(m,null);
            require(number(m,"units")-before==5 && number(m,"groups")==1 &&
                number(m,"groupUnits")==3 && flag(m,"open"),"GROUP_ROLL_DEBITS"); break;
        }
        case 25: {
            for (int j=0;j<640;j++) { m.charge(1); m.finish(); }
            TrapList body=new TrapList();
            expect(m,body,"native wrapper exceeded 640 measured groups");
            require(!body.observed && number(m,"groups")==640 && number(m,"units")==640 &&
                !flag(m,"open"),"GROUP_BOUND_MUTATION"); break;
        }
        case 26: {
            m=meter(r,System.nanoTime()-91000000000L); TrapList body=new TrapList();
            expect(m,body,"failed-first process exceeded 90 seconds");
            require(!body.observed && number(m,"units")==0 && !flag(m,"open"),"EXPIRED_MUTATION");
            break;
        }
        case 27: {
            SlowList body=new SlowList();
            expect(m,body,"native wrapper group exceeded five seconds");
            require(body.observed && number(m,"units")==9 && flag(m,"open") &&
                number(m,"groups")==0,"GROUP_DEADLINE_DEBITS");
            // A failed Meter is caller-discardable state, with no OS resource or listener.
            // Do not invoke an expired finish or relabel its exception as successful cleanup.
            r.discardExpected=true; break;
        }
        case 28: r7Alias(m,r); break;
        case 29: r7Positive(m,r); break;
        default: throw new AssertionError("CASE_INDEX");
        }
    }

    private static void positive(CohortTerminalTelemetryStudy.SetupMeter m,Object body,
            long units,long copies) throws Exception {
        long u=number(m,"units"),c=number(m,"copiedEntries"),k=number(m,"copyChunks");
        CurrentBodyOwnership.validate(m,body);
        require(number(m,"units")-u==units && number(m,"copiedEntries")-c==copies &&
            number(m,"copyChunks")-k==copies,"INDEPENDENT_CENSUS");
        require(flag(m,"open"),"GUARD_CLOSED_METER");
    }
    private static void expect(CohortTerminalTelemetryStudy.SetupMeter m,Object body,String code)
            throws Exception {
        try { CurrentBodyOwnership.validate(m,body); }
        catch (Exception failure) {
            require(code.equals(failure.getMessage()),"EXPECTED_"+code+"_GOT_"+failure);
            if (code.startsWith("BODY_OWNERSHIP_") || code.startsWith("copy exceeds"))
                require(failure instanceof IOException,"STABLE_IOEXCEPTION");
            return;
        }
        throw new AssertionError("MISSING_"+code);
    }
    private static void million(CohortTerminalTelemetryStudy.SetupMeter m,Record r,boolean over)
            throws Exception {
        // root map + key + child list + 999997 scalar visits = exactly 1000000.
        VirtualNullList child=new VirtualNullList(over?999998:999997);
        Map<String,Object> body=map(); body.put("a",child);
        if (over) expect(m,body,"BODY_OWNERSHIP_VISITS");
        else positive(m,body,5000011,2);
        require(child.nexts==(over?999998:999997) && child.iterators==1 &&
            child.hasNexts==999998,"ACTUAL_VISIT_OBSERVATIONS");
        if (over) require(number(m,"units")==5000013 && number(m,"copiedEntries")==2 &&
            number(m,"copyChunks")==2,"OVER_VISIT_PRECHARGE");
        require(body.size()==1 && body.get("a")==child && child.count==(over?999998:999997),
            "VIRTUAL_CONTENT_CHANGED");
        r.observedItems=child.nexts;
    }
    private static Object chain(int count,Object leaf) {
        Object result=leaf;
        for (int j=0;j<count;j++) { List<Object> parent=list(); parent.add(result); result=parent; }
        return result;
    }
    private static Object[] chainLinks(Object value,int count) {
        Object[] links=new Object[count];
        for (int i=0;i<count;i++) { links[i]=value; value=((List<?>)value).get(0); }
        return links;
    }
    private static void unchangedChain(Object root,Object[] links,Object leaf) {
        Object value=root;
        for (Object link:links) {
            require(value==link && ((List<?>)value).size()==1,"CHAIN_IDENTITY_CHANGED");
            value=((List<?>)value).get(0);
        }
        require(value==leaf,"CHAIN_LEAF_CHANGED");
    }
    private static List<Object> list() { return new ArrayList<Object>(); }
    private static Map<String,Object> map() { return new LinkedHashMap<String,Object>(); }
    private static long number(CohortTerminalTelemetryStudy.SetupMeter m,String key) throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(m.meter,key)).longValue();
    }
    private static boolean flag(CohortTerminalTelemetryStudy.SetupMeter m,String key) throws Exception {
        return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(m.meter,key));
    }
    private static void require(boolean ok,String code) { if (!ok) throw new AssertionError(code); }
    private static String quote(String value) {
        StringBuilder out=new StringBuilder("\"");
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (c=='"' || c=='\\') out.append('\\').append(c);
            else if (c<32) {
                String hex=Integer.toHexString(c); out.append("\\u");
                for (int j=hex.length();j<4;j++) out.append('0'); out.append(hex);
            } else out.append(c);
        }
        return out.append('"').toString();
    }

    private static class TrapList extends AbstractList<Object> {
        boolean observed;
        public int size() { observed=true; throw new AssertionError("UNEXPECTED_SIZE"); }
        public Object get(int index) { observed=true; throw new AssertionError("UNEXPECTED_GET"); }
        public Iterator<Object> iterator() { observed=true; throw new AssertionError("UNEXPECTED_ITERATOR"); }
    }
    private static final class SlowList extends TrapList {
        public Iterator<Object> iterator() {
            observed=true;
            long until=System.nanoTime()+5100000000L;
            while (System.nanoTime()<until) {
                try { Thread.sleep(10); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("INTERRUPTED",interrupted);
                }
            }
            return list().iterator();
        }
    }
    private static final class VirtualNullList extends AbstractList<Object> {
        final int count;
        int nexts,hasNexts,iterators;
        VirtualNullList(int count) { this.count=count; }
        public int size() { throw new AssertionError("VIRTUAL_SIZE_FORBIDDEN"); }
        public Object get(int index) { throw new AssertionError("VIRTUAL_GET_FORBIDDEN"); }
        public Iterator<Object> iterator() {
            iterators++;
            return new Iterator<Object>() {
                int cursor;
                public boolean hasNext() { hasNexts++; return cursor<count; }
                public Object next() {
                    if (cursor>=count) throw new NoSuchElementException();
                    cursor++; nexts++; return null;
                }
                public void remove() { throw new UnsupportedOperationException(); }
            };
        }
    }
    private static final class DebitMap extends AbstractMap<String,Object> {
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        int stage;
        DebitMap(CohortTerminalTelemetryStudy.SetupMeter meter) { this.meter=meter; }
        void at(int expected,long units) {
            try { require(stage++==expected && number(meter,"units")==units,"OBSERVATION_DEBIT_"+expected); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }
        public Set<Map.Entry<String,Object>> entrySet() {
            at(0,9);
            return new AbstractSet<Map.Entry<String,Object>>() {
                public int size() { throw new AssertionError("DEBIT_SIZE"); }
                public Iterator<Map.Entry<String,Object>> iterator() {
                    at(1,10);
                    return new Iterator<Map.Entry<String,Object>>() {
                        boolean sent;
                        public boolean hasNext() { if (!sent) at(2,11); else at(6,21); return !sent; }
                        public Map.Entry<String,Object> next() {
                            at(3,12); sent=true;
                            return new Map.Entry<String,Object>() {
                                public String getKey() { at(4,13); return "a"; }
                                public Object getValue() { at(5,17); return null; }
                                public Object setValue(Object v) { throw new AssertionError("DEBIT_WRITE"); }
                            };
                        }
                        public void remove() { throw new AssertionError("DEBIT_REMOVE"); }
                    };
                }
            };
        }
    }

    private static long prepareDetached(CohortTerminalTelemetryStudy.SetupMeter m) throws Exception {
        long started=number(m,"started");
        // Real lifecycle admission, with detached synthetic demand only; no owner capture.
        m.method("beginOriginalOwner",new Class<?>[0]);
        m.method("admitOwnerGroup",new Class<?>[]{Integer.TYPE},Integer.valueOf(0));
        m.method("adoptOriginalOwner",new Class<?>[]{Integer.TYPE},Integer.valueOf(1));
        return started;
    }
    private static CohortTerminalTelemetryStudy.R7WorkPlan detached(
            CohortTerminalTelemetryStudy.SetupMeter m,long started,Map<String,Object> body)
            throws Exception {
        return CohortTerminalTelemetryStudy.captureDetachedR7WorkPlan(m,started,body,
            0,0,0,0, 0,0,0,0,1,1,0,0, 0,0,0,0,0,0,0,0,0,0,0,0,0);
    }
    private static void r7Alias(CohortTerminalTelemetryStudy.SetupMeter m,Record r) throws Exception {
        long started=prepareDetached(m);
        Map<String,Object> body=map(); List<Object> shared=list();
        body.put("work",null); body.put("a",shared); body.put("b",shared);
        try { detached(m,started,body); throw new AssertionError("R7_ALIAS_NOT_REJECTED"); }
        catch (IOException failure) {
            require("BODY_OWNERSHIP_ALIAS".equals(failure.getMessage()),"R7_GUARD_NOT_ACTIVE");
        }
        require(flag(m,"open") && number(m,"groups")==0 &&
            ((List<?>)CohortTerminalTelemetryStudy.field(m.meter,"groupMeasures")).isEmpty(),
            "R7_ALIAS_CLOSED_METADATA");
        Object work=body.get("work");
        require(work instanceof Map && ((Map<?,?>)work).size()==34,"R7_WORK_NOT_INSTALLED");
        for (Object value:((Map<?,?>)work).values())
            require(value instanceof Long && ((Long)value).longValue()==0,"R7_ALIAS_FINISHED_WORK");
        require(body.size()==3 && body.get("a")==shared && body.get("b")==shared && shared.isEmpty(),
            "R7_ALIAS_INPUT_CHANGED");
    }
    @SuppressWarnings("unchecked")
    private static void r7Positive(CohortTerminalTelemetryStudy.SetupMeter m,Record r) throws Exception {
        long started=prepareDetached(m); Map<String,Object> body=map(); body.put("work",null);
        CohortTerminalTelemetryStudy.R7WorkPlan plan=detached(m,started,body);
        // Independent existing R7 census: 624 + 6 per ASCII work-key char, plus 184 fixed.
        long characters=0; for (String key:WORK_KEYS) characters+=key.length();
        long existing=624+6*characters+184;
        // New guard sees two maps and 35 entries: 5+12+350 = 367 units; two identity copies.
        long expected=existing+367;
        require(number(m,"units")==expected && number(m,"copiedEntries")==97 &&
            number(m,"copyChunks")==5 && number(m,"groups")==1 && !flag(m,"open"),
            "R7_METADATA_CENSUS");
        Map<String,Object> work=(Map<String,Object>)body.get("work");
        require(body.size()==1 && work.size()==34,"R7_BODY_SHAPE");
        Iterator<String> keys=work.keySet().iterator();
        for (String key:WORK_KEYS) {
            require(keys.hasNext() && key.equals(keys.next()) && work.get(key) instanceof Long &&
                ((Long)work.get(key)).longValue()==0,"R7_PLACEHOLDER_ORDER");
        }
        require(!keys.hasNext(),"R7_EXTRA_KEYS");
        plan.pre.verifyReady(m,started); plan.verifyBound(m,plan.pre,body,work);
        long cutoff=System.nanoTime()-started+1;
        plan.finalizeWork(started+cutoff);
        require(number(m,"units")==expected && number(m,"copiedEntries")==97 &&
            number(m,"copyChunks")==5 && !flag(m,"open") && body.get("work")==work,
            "R7_FINALIZER_CHANGED_METER");
        long[] expectedValues={cutoff,0,2,1,number(m,"maxGroupNanos"),expected,expected,
            0,0,0,0, 0,0,0,0,1,0,0, 0,0,0,0,0,0,0,
            expected,expected,1,97,5,97,5,4096,1048576};
        require(expectedValues.length==34,"R7_EXPECTED_VECTOR_LENGTH");
        for (int i=0;i<34;i++)
            require(value(work,WORK_KEYS[i])==expectedValues[i],"R7_EQUATION_"+WORK_KEYS[i]);
        plan.pre.verifyReady(m,started);
    }
    private static long value(Map<String,Object> work,String key) { return ((Long)work.get(key)).longValue(); }
}
