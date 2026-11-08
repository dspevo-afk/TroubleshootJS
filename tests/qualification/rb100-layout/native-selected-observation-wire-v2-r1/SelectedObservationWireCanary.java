package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.*;

/** Generic, bounded callback-context fixtures; no authored input or whole-route claim. */
public final class SelectedObservationWireCanary {
    private static final String MODE="SELECTED_OBSERVATION_CANARIES_V2";
    private static final String WIRE="bebf6775a66a8702862d421fd9fd180364b8c0fcb393b8e9f34b1369e052dc80";
    private static final String PHYSICAL="7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c";
    private static final String STUDY="d6485b0b0f9c518431d440fcb57e477143d50cae78302c29598d12aaa4601dbd";
    private static final String REQUEST="generic-selected-wire";
    private static final String OBS_KEYS="schema|mode|state|callbackCount|foundCallbackCount|nonFoundCallbackCount|firstNetOrdinal|lastNetOrdinal|lastCallbackTotalExpanded|selectedCallbackOrdinal|transientTupleCopies|transientFieldCopies|validationCount|observerUnits|copiedEntries|copyChunks|selectedSnapshot|selectedHead";
    private static final String SNAP_KEYS="pass|netId|startPadId|rootPadId|startX|startY|rootX|rootY|gridWidth|gridHeight|goalsTop|goalsBottom|prefixViaCount|reachedPadCount|initialManhattanDistance|minimumExploredManhattanDistance|branchExpanded|totalExpanded|expandedTop|expandedBottom|frontierCount|bestStateCount|terminalReason|relaxedBoundStarted|relaxedBoundComplete|relaxedBoundObservationChecked|relaxedBoundLiveLayoutStable|relaxedBoundRouterStateStable|relaxedBoundRouterEffectsStable|relaxedBoundWorkUnits|relaxedBoundOrdinaryProbes|relaxedBoundViaProbes|relaxedBoundRefreshes";
    private static final String HEAD_KEYS="branchOrdinal|cell|layer|direction|transitions|cost|storedHeuristic|serial|key|storedPriority";
    private SelectedObservationWireCanary() { }

    public static void run(String[] args,long started) throws Exception {
        // Fixed-mode/context rejection precedes Parent, Meter, fixture and Session access.
        if (args==null || args.length!=4 || !MODE.equals(args[0]))
            throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");
        if (args[1]==null || !args[1].matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IOException("SELECTED_WIRE_UUID");
        if (args[2]==null || !args[2].matches("[0-9a-f]{64}") || !WIRE.equals(args[3]))
            throw new IOException("SELECTED_WIRE_SHA");
        Parent parent=null;
        Object nativeMeter=null;
        SetupMeter meter=null;
        Driver driver=null;
        Map<String,Object> body=null;
        R9CanaryPublication publication=null;
        try {
            parent=new Parent();
            nativeMeter=parent.meter(started);
            meter=new SetupMeter(parent,nativeMeter);
            meter.charge(1); meter.copy(3);
            driver=new Driver(meter);
            body=driver.body(args,started);
            publication=CohortTerminalTelemetryStudy.prepareR9CanaryPublication(body,meter,started);
            CohortTerminalTelemetryStudy.publishR9CanaryStdout(body,meter,started,publication);
        } finally {
            // No post-publication metrics inspection/output or successor owner exists here.
            body=null; publication=null; driver=null;
            if (meter!=null) meter.finish();
            meter=null; nativeMeter=null; parent=null;
        }
    }

    private static final class Driver {
        private final SetupMeter m;
        private int assertions,beginCount;
        Driver(SetupMeter meter) { m=meter; }

        Map<String,Object> body(String[] args,long started) throws Exception {
            Map<String,Object> root=null,complete=null,blocked=null,quality=null,review=null;
            Map<String,Object> generic=null,direct=null;
            try {
                complete=map(2);
                put(complete,"NULL",literal("NULL"));
                put(complete,"FOUND",literal("FOUND"));
                blocked=map(3);
                put(blocked,"BRANCH_SEARCH_LIMIT",injected("BRANCH_SEARCH_LIMIT"));
                put(blocked,"NO_PATH",injected("NO_PATH"));
                put(blocked,"TOTAL_SEARCH_LIMIT",injected("TOTAL_SEARCH_LIMIT"));
                quality=map(1);
                put(quality,"QUALITY_LIMIT",literal("QUALITY_LIMIT"));
                generic=CohortTerminalTelemetryStudy.runR9GenericCallbackThrow(m,started);
                direct=CohortTerminalTelemetryStudy.runR9DirectOwnerAbortReuse(m,started);
                assertGeneric(generic,direct);
                check(beginCount==3,"three actual injected Session begins");
                review=map(6);
                put(review,"scope","GENERIC_OBSERVATION_V2_ONLY");
                put(review,"physicalOwnerSha256",PHYSICAL);
                put(review,"studyParentSha256",STUDY);
                put(review,"headSemantics","SELECTED_AND_POPPED_BEFORE_REFRESH_NOT_PATH_WITNESS");
                put(review,"injectedSessionBegins",Integer.valueOf(beginCount));
                put(review,"otherSessionCounts","SEPARATE_EXISTING_GENERIC_HELPERS");
                ordered(review,6,"scope|physicalOwnerSha256|studyParentSha256|headSemantics|injectedSessionBegins|otherSessionCounts");
                root=map(11);
                put(root,"schema","tsj-selected-observation-wire-canary-v2");
                put(root,"status","GENERIC_OBSERVATIONS_RECORDED_ONLY");
                put(root,"runId",args[1]); put(root,"sourceManifestSha256",args[2]);
                put(root,"wireContractSha256",args[3]);
                put(root,"complete",complete); put(root,"blocked",blocked);
                put(root,"nonBranchQuality",quality); put(root,"genericCallbackThrow",generic);
                put(root,"directOwnerAbortReuse",direct); put(root,"sourceReview",review);
                ordered(root,11,"schema|status|runId|sourceManifestSha256|wireContractSha256|complete|blocked|nonBranchQuality|genericCallbackThrow|directOwnerAbortReuse|sourceReview");
                return root;
            } finally { root=null; complete=null; blocked=null; quality=null; review=null; generic=null; direct=null; }
        }

        Map<String,Object> literal(String id) throws Exception {
            Context c=null; TerminalSink sink=null; Callback callback=null;
            PcbLayerRoutingPrototype.BranchSnapshot snapshot=null;
            try {
                m.charge(1); m.copy(16);
                c=new Context("N","S","R",1,2,8,9,10,10,1,0,0,1,14,1,0);
                if (!"NULL".equals(id)) sink=sink(c);
                m.charge(1); m.copy(11);
                callback=new Callback(this,sink,null,c,id);
                if ("FOUND".equals(id)) {
                    snapshot=found(); callback.receive(snapshot,null);
                } else callback.zero();
                return finishCase(id,c,sink,callback,false);
            } finally { snapshot=null; sink=null; c=null; if (callback!=null) callback.drop(); callback=null; }
        }

        Map<String,Object> injected(String reason) throws Exception {
            Frame f=null; Callback callback=null; TerminalSink sink=null; Context context=null;
            Object chosen=null,later=null;
            try {
                m.charge(1); m.copy(11);
                callback=new Callback(this,null,null,null,reason);
                f=frame(callback); context=f.context; sink=sink(context);
                callback.sink=sink; callback.frame=f; callback.context=context;
                int cells=context.width*context.height;
                if (!"NO_PATH".equals(reason)) {
                    chosen=node(cells,7,context.primary,1,0,11,13,17);
                    later=node(cells,8,context.primary,1,0,12,13,18);
                }
                frontier(f.branch,chosen,later);
                int local="NO_PATH".equals(reason)?0:100000;
                int total="TOTAL_SEARCH_LIMIT".equals(reason)?1000000:local;
                check(get(f.session,"budget")==get(f.search,"budget"),"Session/Search share actual budget");
                set(f.branch,"expanded",Integer.valueOf(local));
                set(f.branch,"expandedTop",Integer.valueOf(context.primary==0?local:0));
                set(f.branch,"expandedBottom",Integer.valueOf(context.primary==1?local:0));
                set(f.branch,"minimumExploredManhattanDistance",Integer.valueOf(local==0?Integer.MAX_VALUE:0));
                set(get(f.session,"budget"),"expanded",Integer.valueOf(total));
                m.charge(1); f.session.advanceSlice(1);
                m.charge(1); int terminalWork=f.session.lastWork(); m.charge(Math.max(1,terminalWork));
                check(terminalWork>=0 && terminalWork<=1,"bounded terminal owner work");
                check(callback.calls==1 && callback.selectedCalls==("BRANCH_SEARCH_LIMIT".equals(reason)?1:0),"actual callback cardinality");
                check(get(f.session,"search")==null,"terminal Session drops search");
                m.charge(3);
                check(f.fixture.layout.getTraces().isEmpty() && f.fixture.layout.getHoles().isEmpty(),"source fixture retains no routes");
                String after=CohortTerminalTelemetryStudy.geometry(m.parent,m,f.fixture.layout);
                check(f.before.equals(after),"source placement unchanged after injected callback");
                return finishCase(reason,context,sink,callback,true);
            } finally {
                chosen=null; later=null; sink=null; context=null;
                if (callback!=null) callback.drop(); callback=null;
                if (f!=null) f.drop(); f=null;
            }
        }

        private TerminalSink sink(Context c) throws Exception {
            Map<String,PadIdentity> pads=null;
            Map<String,Integer> ordinals=null,counts=null;
            ObserverMeter port=null; ObservationSpec spec=null;
            TerminalTelemetryObserver observer=null;
            try {
                m.charge(1); m.copy(2); pads=new LinkedHashMap<String,PadIdentity>();
                m.charge(2); m.copy(6);
                pads.put(c.start,new PadIdentity(c.net,c.sx,c.sy));
                pads.put(c.root,new PadIdentity(c.net,c.rx,c.ry));
                m.charge(1); m.copy(1); ordinals=new LinkedHashMap<String,Integer>();
                ordinals.put(c.net,Integer.valueOf(1));
                m.charge(1); m.copy(1); counts=new LinkedHashMap<String,Integer>();
                counts.put(c.net,Integer.valueOf(2));
                m.charge(1); m.copy(1); port=new ObserverMeter(m);
                spec=ObservationSpec.create(port,REQUEST,2,2,1,pads,ordinals,counts);
                m.charge(1); m.copy(2); observer=new TerminalTelemetryObserver(port,spec);
                m.charge(1); m.copy(3); return new TerminalSink(port,observer,REQUEST);
            } finally { pads=null; ordinals=null; counts=null; port=null; spec=null; observer=null; }
        }

        private Frame frame(Callback callback) throws Exception {
            P06FactoryLinkFixtures.Fixture fixture=null;
            PcbLayerRoutingPrototype.Session session=null;
            Object search=null,branch=null;
            Context context=null;
            try {
                m.charge(1); fixture=new P06FactoryLinkFixtures.Fixture(true,0,0,true);
                String before=CohortTerminalTelemetryStudy.geometry(m.parent,m,fixture.layout);
                m.charge(1); m.copy(5);
                beginCount++;
                session=PcbLayerRoutingPrototype.begin(fixture.board,fixture.layout,
                    PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,P06FactoryLinkFixtures.OBSERVER,
                    1000000,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,callback);
                // Explicit injected pass BEFORE the first advance/search construction.
                set(session,"pass",Integer.valueOf(2));
                for (int i=0;i<1000 && branch==null;i++) {
                    m.charge(1); session.advanceSlice(1);
                    m.charge(1); int work=session.lastWork(); m.charge(Math.max(1,work));
                    search=get(session,"search"); branch=search==null?null:get(search,"branch");
                }
                check(search!=null && branch!=null && number(branch,"expanded")==0,"bounded unexpanded actual P06 branch");
                check(number(search,"pass")==2,"actual Search preserves injected pass two");
                PcbPadPlacement start=(PcbPadPlacement)get(branch,"start");
                PcbPadPlacement root=(PcbPadPlacement)get(branch,"root");
                String net=(String)get(branch,"net");
                m.charge(8);
                String sid=start.getPadId(),rid=root.getPadId();
                int sx=start.getX(),sy=start.getY(),rx=root.getX(),ry=root.getY();
                check(!sid.equals(rid),"distinct source pad identities");
                int width=(int)number(search,"width"),height=(int)number(search,"height");
                int top=(int)number(branch,"goalsTop"),bottom=(int)number(branch,"goalsBottom");
                m.charge(1); int prefix=((Object[])get(branch,"holes")).length;
                m.charge(1); int reached=((List<?>)get(search,"reachedPadIds")).size();
                int[] distance=(int[])get(branch,"distance");
                int ox=(int)number(search,"minX"),oy=(int)number(search,"minY");
                int cell=((sy-oy)/PcbLayerRoutingPrototype.GRID)*width+(sx-ox)/PcbLayerRoutingPrototype.GRID;
                m.charge(1); int initial=distance[cell];
                int ordinal=(int)number(branch,"branchOrdinal"),primary=(int)number(search,"primary");
                check(ordinal==1 && prefix==0 && reached==1,"first-branch fixture census");
                m.charge(1); m.copy(16);
                context=new Context(net,sid,rid,sx,sy,rx,ry,width,height,top,bottom,prefix,reached,initial,ordinal,primary);
                m.charge(1); m.copy(6);
                return new Frame(fixture,session,search,branch,context,before);
            } finally { fixture=null; session=null; search=null; branch=null; context=null; }
        }

        private void frontier(Object branch,Object chosen,Object later) throws Exception {
            Object queue=get(branch,"queue"),index=get(branch,"best");
            Object[] q=(Object[])get(queue,"values"),v=(Object[])get(index,"values");
            int[] keys=(int[])get(index,"keys");
            // Bounded writes to the owner's existing containers; no cloned ledger/queue.
            m.charge(q.length+v.length+keys.length);
            for(int i=0;i<q.length;i++) q[i]=null;
            for(int i=0;i<v.length;i++) v[i]=null;
            for(int i=0;i<keys.length;i++) keys[i]=0;
            set(queue,"size",Integer.valueOf(0)); set(index,"size",Integer.valueOf(0));
            if (chosen!=null) { offer(queue,index,chosen); offer(queue,index,later); }
        }
        private void offer(Object queue,Object index,Object node) throws Exception {
            m.charge(2); m.copy(3);
            Class<?>[] one=new Class<?>[]{ownerType("Node")};
            Object[] value=new Object[]{node};
            invoke(queue,"add",one,value);
            m.charge(2); m.copy(4);
            Class<?>[] two=new Class<?>[]{Integer.TYPE,ownerType("Node")};
            Object[] values=new Object[]{Integer.valueOf((int)number(node,"key")),node};
            invoke(index,"put",two,values);
        }
        private Object node(int cells,int cell,int layer,int direction,int transitions,int cost,int h,int serial) throws Exception {
            Class<?> t=ownerType("Node");
            m.charge(2); m.copy(18);
            Class<?>[] types=new Class<?>[]{Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
                Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,t};
            Object[] values=new Object[]{Integer.valueOf(cell),Integer.valueOf(layer),Integer.valueOf(direction),
                Integer.valueOf(transitions),Integer.valueOf(cost),Integer.valueOf(h),Integer.valueOf(serial),Integer.valueOf(cells),null};
            m.charge(1); return construct(t,types,values);
        }
        private PcbLayerRoutingPrototype.BranchSnapshot found() throws Exception {
            m.charge(2); m.copy(66);
            Object[] v=new Object[]{2,"N","S","R",1,2,8,9,10,10,1,0,0,1,14,0,1,1,1,0,0,1,"FOUND",
                false,false,false,false,false,false,0,0,0,0};
            Class<?>[] types=new Class<?>[]{Integer.TYPE,String.class,String.class,String.class,
                Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
                Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,
                Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE,String.class,
                Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,Boolean.TYPE,
                Integer.TYPE,Integer.TYPE,Integer.TYPE,Integer.TYPE};
            m.charge(1); return (PcbLayerRoutingPrototype.BranchSnapshot)construct(PcbLayerRoutingPrototype.BranchSnapshot.class,types,v);
        }

        private Map<String,Object> finishCase(String id,Context c,TerminalSink sink,Callback callback,boolean actual) throws Exception {
            boolean enabled=sink!=null,found="FOUND".equals(id),quality="QUALITY_LIMIT".equals(id);
            int callbacks=(found||actual)?1:0,heads="BRANCH_SEARCH_LIMIT".equals(id)?1:0;
            int units=heads==1?192:found?160:actual?159:0;
            int copies=33*callbacks+10*heads,chunks=callbacks+heads,validations=heads==1?102:found?91:actual?90:0;
            check(callback.units==units && callback.copies==copies && callback.chunks==chunks,"actual callback delta matches literal oracle");
            if (sink!=null) {
                m.charge(1); sink.observer.seal();
                assertObserver(sink,callbacks,found?1:0,actual?1:0,heads,validations,units,callback);
            }
            // Prepay exactly the four surrounding logical Meter inspections. No charge
            // or counter read intervenes inside the common materializer's own delta.
            m.charge(4);
            long beforeCopies=rawNumber(m.meter,"copiedEntries"),beforeChunks=rawNumber(m.meter,"copyChunks");
            Map<String,Object> observation=CohortTerminalTelemetryStudy.materializeObservation(sink,m);
            long metadataCopies=rawNumber(m.meter,"copiedEntries")-beforeCopies;
            long metadataChunks=rawNumber(m.meter,"copyChunks")-beforeChunks;
            int expectedCopies=enabled?36+(actual?33:0)+10*heads:18;
            int expectedChunks=enabled?2+(actual?1:0)+heads:1;
            check(metadataCopies==expectedCopies && metadataChunks==expectedChunks,"common owner exact metadata delta");
            if(quality) { m.charge(2); check("NO_FAILURE".equals(observation.get("state")),"quality starts from actual empty observer map"); observation.put("state","NON_BRANCH_REJECTION"); }
            assertObservation(observation,sink,id,callbacks,heads,validations,units,c);
            Map<String,Object> delta=map(3);
            put(delta,"units",Long.valueOf(callback.units)); put(delta,"copiedEntries",Long.valueOf(callback.copies)); put(delta,"copyChunks",Long.valueOf(callback.chunks));
            Map<String,Object> metadata=map(2);
            put(metadata,"copiedEntries",Long.valueOf(metadataCopies)); put(metadata,"copyChunks",Long.valueOf(metadataChunks));
            ordered(delta,3,"units|copiedEntries|copyChunks"); ordered(metadata,2,"copiedEntries|copyChunks");
            Map<String,Object> context=context(c,id,actual,callbacks);
            Map<String,Object> result=map(7);
            put(result,"caseId",id); put(result,"provenance",actual?"INJECTED_ACTUAL_P06_SESSION":"LITERAL_CALLBACK_FIXTURE");
            put(result,"armRole",enabled?"OBSERVE":"NULL"); put(result,"observation",observation);
            put(result,"context",context); put(result,"meterDelta",delta); put(result,"metadata",metadata);
            ordered(result,7,"caseId|provenance|armRole|observation|context|meterDelta|metadata");
            return result;
        }

        private Map<String,Object> context(Context c,String id,boolean actual,int callbacks) throws Exception {
            Map<String,Object> out=map(8),pads=map(2),start=map(3),root=map(3),outcome=map(3),terminal=null;
            put(start,"netId",c.net); put(start,"x",Integer.valueOf(c.sx)); put(start,"y",Integer.valueOf(c.sy));
            put(root,"netId",c.net); put(root,"x",Integer.valueOf(c.rx)); put(root,"y",Integer.valueOf(c.ry));
            put(pads,c.start,start); put(pads,c.root,root);
            String disposition=actual?("NO_PATH".equals(id)?"NO_PATH":"CAP_REJECTED"):
                "FOUND".equals(id)?"FOUND":"NULL".equals(id)?"NOT_RUN":"QUALITY_REJECTED";
            put(outcome,"disposition",disposition);
            m.charge(1);
            put(outcome,"reason",actual?c.net+":"+id:"NULL".equals(id)?"OBSERVER_DISABLED":id);
            put(outcome,"expanded",Integer.valueOf(total(id)));
            if(actual) { terminal=map(3); put(terminal,"failedNetId",c.net); put(terminal,"ownerReason",c.net+":"+id); put(terminal,"searchPass",Integer.valueOf(2)); }
            put(out,"requestedNetIds",list(c.net,true)); put(out,"traversedNetIds",list(c.net,callbacks==1));
            put(out,"padsById",pads); put(out,"gridWidth",Integer.valueOf(c.width)); put(out,"gridHeight",Integer.valueOf(c.height));
            put(out,"outcome",outcome); put(out,"terminalFailure",terminal); put(out,"callbackOpportunities",Integer.valueOf(callbacks));
            ordered(out,8,"requestedNetIds|traversedNetIds|padsById|gridWidth|gridHeight|outcome|terminalFailure|callbackOpportunities");
            return out;
        }

        private void assertObserver(TerminalSink sink,int d,int found,int failures,int h,int v,int u,Callback cb) throws Exception {
            Object o=sink.observer;
            expectField(o,"meter",sink.meter); check(get(o,"spec")!=null,"observer retains immutable spec");
            expectField(o,"sealed",Boolean.TRUE); expectField(o,"poisoned",Boolean.FALSE); expectField(o,"inCallback",Boolean.FALSE);
            expectField(o,"dispatches",d); expectField(o,"accepted",d); expectField(o,"found",found); expectField(o,"failures",failures);
            expectField(o,"inspections",33*d+10*h); expectField(o,"copiedSlots",33*d+10*h); expectField(o,"copyChunks",d+h);
            expectField(o,"checks",v); expectField(o,"selections",d); expectField(o,"releases",d);
            expectField(o,"headCopies",h); expectField(o,"headReleases",h);
            expectField(o,"unitsCharged",u); expectField(o,"unitsMeasured",u); expectField(o,"groupSamples",d);
            expectField(o,"maxCallbackUnits",u); expectField(o,"maxGroupUnits",u);
            expectField(o,"lastGroupBefore",d==0?-1:cb.groupBefore); expectField(o,"lastGroupAfter",d==0?-1:cb.groupBefore+u);
            expectField(o,"firstOrdinal",d==0?-1:1); expectField(o,"lastOrdinal",d==0?-1:1);
            expectField(o,"lastTotal",d==0?0:total(cb.reason)); expectField(o,"selectedCallbackOrdinal",failures);
            check((get(o,"selected")!=null)==(failures==1),"private selected snapshot presence");
            check((get(o,"selectedHead")!=null)==(h==1),"private selected head presence");
            check(!sink.meter.insideOwner && sink.meter.entriesRemaining==0,"real adapter restores its callback state");
        }
        @SuppressWarnings("unchecked")
        private void assertObservation(Map<String,Object> out,TerminalSink sink,String id,int d,int h,int v,int u,Context c) throws Exception {
            ordered(out,18,OBS_KEYS);
            expect(out,"schema","tsj-cohort-terminal-observation-v2"); expect(out,"mode",sink==null?"NULL":"OBSERVE");
            expect(out,"state",sink==null?"NOT_ENABLED":"QUALITY_LIMIT".equals(id)?"NON_BRANCH_REJECTION":"FOUND".equals(id)?"NO_FAILURE":"FIRST_NON_FOUND");
            expect(out,"callbackCount",d); expect(out,"foundCallbackCount","FOUND".equals(id)?1:0);
            int failure=(d==1 && !"FOUND".equals(id))?1:0;
            expect(out,"nonFoundCallbackCount",failure); expect(out,"firstNetOrdinal",d==0?null:Integer.valueOf(1));
            expect(out,"lastNetOrdinal",d==0?null:Integer.valueOf(1)); expect(out,"lastCallbackTotalExpanded",d==0?null:Integer.valueOf(total(id)));
            expect(out,"selectedCallbackOrdinal",failure==0?null:Integer.valueOf(1));
            expect(out,"transientTupleCopies",d+h); expect(out,"transientFieldCopies",33*d+10*h);
            expect(out,"validationCount",v); expect(out,"observerUnits",u); expect(out,"copiedEntries",33*d+10*h); expect(out,"copyChunks",d+h);
            m.charge(2); Object snapshot=out.get("selectedSnapshot"),head=out.get("selectedHead");
            check((snapshot!=null)==(failure==1) && (head!=null)==(h==1),"wire snapshot/head presence");
            if(snapshot!=null) {
                Map<String,Object> sm=(Map<String,Object>)snapshot; ordered(sm,33,SNAP_KEYS);
                Object detached=get(sink.observer,"selected"); assertSnapshot(detached,c,id);
                assertMapFields(sm,detached,33);
            }
            if(head!=null) {
                Map<String,Object> hm=(Map<String,Object>)head; ordered(hm,10,HEAD_KEYS);
                Object detached=get(sink.observer,"selectedHead"); assertHead(detached,c); assertMapFields(hm,detached,10);
            }
        }
        private void assertSnapshot(Object s,Context c,String reason) throws Exception {
            expectField(s,"pass",2); expectField(s,"netId",c.net); expectField(s,"startPadId",c.start); expectField(s,"rootPadId",c.root);
            expectField(s,"startX",c.sx); expectField(s,"startY",c.sy); expectField(s,"rootX",c.rx); expectField(s,"rootY",c.ry);
            expectField(s,"gridWidth",c.width); expectField(s,"gridHeight",c.height); expectField(s,"goalsTop",c.goalsTop); expectField(s,"goalsBottom",c.goalsBottom);
            expectField(s,"prefixViaCount",c.prefix); expectField(s,"reachedPadCount",c.reached); expectField(s,"initialManhattanDistance",c.initial);
            int local="FOUND".equals(reason)?1:"NO_PATH".equals(reason)?0:100000;
            expectField(s,"minimumExploredManhattanDistance",local==0?-1:0); expectField(s,"branchExpanded",local); expectField(s,"totalExpanded",total(reason));
            expectField(s,"expandedTop",c.primary==0?local:0); expectField(s,"expandedBottom",c.primary==1?local:0);
            expectField(s,"frontierCount","NO_PATH".equals(reason)||"FOUND".equals(reason)?0:1);
            expectField(s,"bestStateCount","NO_PATH".equals(reason)?0:"FOUND".equals(reason)?1:2); expectField(s,"terminalReason",reason);
            expectField(s,"relaxedBoundStarted",Boolean.FALSE); expectField(s,"relaxedBoundComplete",Boolean.FALSE);
            expectField(s,"relaxedBoundObservationChecked",Boolean.FALSE); expectField(s,"relaxedBoundLiveLayoutStable",Boolean.FALSE);
            expectField(s,"relaxedBoundRouterStateStable",Boolean.FALSE); expectField(s,"relaxedBoundRouterEffectsStable",Boolean.FALSE);
            expectField(s,"relaxedBoundWorkUnits",0); expectField(s,"relaxedBoundOrdinaryProbes",0); expectField(s,"relaxedBoundViaProbes",0); expectField(s,"relaxedBoundRefreshes",0);
        }
        private void assertHead(Object h,Context c) throws Exception {
            expectField(h,"branchOrdinal",1); expectField(h,"cell",7); expectField(h,"layer",c.primary); expectField(h,"direction",1);
            expectField(h,"transitions",0); expectField(h,"cost",11); expectField(h,"storedHeuristic",13); expectField(h,"serial",17);
            expectField(h,"key",((c.primary*5+1)*(c.width*c.height))+7); expectField(h,"storedPriority",24);
        }
        private void assertMapFields(Map<String,Object> map,Object detached,int count) throws Exception {
            m.charge(1); m.copy(count); Field[] fields=detached.getClass().getDeclaredFields();
            check(fields.length==count,"exact detached scalar field census");
            for(Field f:fields) { m.charge(3); check(!Modifier.isStatic(f.getModifiers()),"no detached static state"); f.setAccessible(true); expect(map,f.getName(),f.get(detached)); }
        }
        private void assertGeneric(Map<String,Object> generic,Map<String,Object> direct) throws Exception {
            ordered(generic,5,"caughtSameThrowable|constructorEntriesBefore|constructorEntriesAfter|callbackExitMeterUnits|afterInvokeMeterUnits");
            expect(generic,"caughtSameThrowable",Boolean.TRUE);
            long before=mapNumber(generic,"constructorEntriesBefore"),after=mapNumber(generic,"constructorEntriesAfter");
            check(after==before+1,"actual generic helper constructor delta");
            check(mapNumber(generic,"afterInvokeMeterUnits")==mapNumber(generic,"callbackExitMeterUnits")+1,"generic parent charges one wrapper unit after owner abort");
            ordered(direct,10,"sentinelKind|caughtSameThrowable|lastWork|diagnosticFailureSameThrowable|searchCleared|candidateCleared|resultCleared|retrySameThrowable|sourcePlacementUnchanged|harnessAliasesDropped");
            expect(direct,"caughtSameThrowable",Boolean.TRUE); expect(direct,"diagnosticFailureSameThrowable",Boolean.TRUE);
            expect(direct,"searchCleared",Boolean.TRUE); expect(direct,"candidateCleared",Boolean.TRUE); expect(direct,"resultCleared",Boolean.TRUE);
            expect(direct,"retrySameThrowable",Boolean.TRUE); expect(direct,"sourcePlacementUnchanged",Boolean.TRUE);
            expect(direct,"harnessAliasesDropped",Boolean.TRUE);
            expect(direct,"sentinelKind","java.lang.IllegalStateException");
            check(mapNumber(direct,"lastWork")==0,"direct owner abort clears lastWork");
        }

        private void reserveCallback() throws Exception {
            // Reserve the unchanged callback ceiling/post-call margin without charging
            // those hypothetical units. The following eight sample reads are separate.
            m.charge(2); m.copy(2);
            Class<?>[] types=new Class<?>[]{Long.TYPE};
            Object[] values=new Object[]{Long.valueOf(512L+104L+8L)};
            m.method("reserve",types,values);
        }
        private Map<String,Object> map(int slots) throws Exception { m.charge(1); m.copy(slots); return new LinkedHashMap<String,Object>(); }
        private List<String> list(String value,boolean present) throws Exception { m.charge(1); if(present)m.copy(1); List<String> list=new ArrayList<String>(present?1:0); if(present)list.add(value); return list; }
        private void put(Map<String,Object> map,String key,Object value) throws Exception { m.charge(1); map.put(key,value); }
        private void ordered(Map<?,?> map,int count,String keys) throws Exception {
            m.charge(2); check(map.size()==count,"exact map key count");
            Iterator<?> it=map.keySet().iterator(); int offset=0,index=0;
            while(true) { m.charge(1); if(!it.hasNext())break; m.charge(2); Object key=it.next(); int end=keys.indexOf('|',offset); if(end<0)end=keys.length();
                check(key instanceof String && ((String)key).length()==end-offset && keys.regionMatches(offset,(String)key,0,end-offset),"exact ordered key"); offset=end+1; index++; }
            check(index==count && offset==keys.length()+1,"complete ordered key specification");
        }
        private void expect(Map<String,Object> map,String key,Object expected) throws Exception { m.charge(1); same(map.get(key),expected,key); }
        private void expectField(Object object,String key,Object expected) throws Exception { same(get(object,key),expected,key); }
        private void same(Object actual,Object expected,String label) throws Exception {
            check(actual instanceof Number && expected instanceof Number?((Number)actual).longValue()==((Number)expected).longValue():expected==null?actual==null:expected.equals(actual),label);
        }
        private void check(boolean value,String message) throws Exception { m.charge(1); assertions++; if(!value)throw new IOException("SELECTED_WIRE_ASSERT: "+message); }
        private long mapNumber(Map<String,Object> map,String key) throws Exception { m.charge(1); return ((Number)map.get(key)).longValue(); }
        private Object get(Object object,String name) throws Exception { m.charge(3); Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        private long number(Object object,String name) throws Exception { return ((Number)get(object,name)).longValue(); }
        private void set(Object object,String name,Object value) throws Exception { m.charge(3); Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
        private Class<?> ownerType(String name) throws Exception { m.charge(2); return Class.forName(PcbLayerRoutingPrototype.class.getName()+"$"+name); }
        private Object construct(Class<?> type,Class<?>[] types,Object[] values) throws Exception { m.charge(3); Constructor<?> c=type.getDeclaredConstructor(types); c.setAccessible(true); try{return c.newInstance(values);}catch(InvocationTargetException e){throwCause(e);return null;} }
        private Object invoke(Object target,String name,Class<?>[] types,Object[] values) throws Exception { m.charge(3); Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);try{return method.invoke(target,values);}catch(InvocationTargetException e){throwCause(e);return null;} }
    }

    /** Stored scalar deltas are samples of the one Meter, never replacement ledgers. */
    private static final class Callback implements PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics {
        private Driver driver;
        private TerminalSink sink;
        private Frame frame;
        private Context context;
        private final String reason;
        private int calls,selectedCalls;
        private long units,copies,chunks,groupBefore;
        Callback(Driver d,TerminalSink s,Frame f,Context c,String r) { driver=d;sink=s;frame=f;context=c;reason=r; }
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot s) { receive(s,null); }
        public void onBranchLimitSelected(PcbLayerRoutingPrototype.BranchSnapshot s,PcbLayerRoutingPrototype.BranchLimitSelection h) { receive(s,h); }
        private void receive(PcbLayerRoutingPrototype.BranchSnapshot s,PcbLayerRoutingPrototype.BranchLimitSelection h) {
            try {
                driver.check(calls==0 && sink!=null,"single prepared callback sink");
                driver.assertSnapshot(s,context,reason);
                if(frame!=null) {
                    driver.check(driver.get(frame.session,"search")==frame.search && driver.get(frame.search,"branch")==frame.branch,"callback uses live actual owner branch");
                    driver.expectField(frame.branch,"terminalEmitted",Boolean.TRUE);
                }
                if(h!=null) { selectedCalls++; driver.assertHead(h,context); }
                // All eight actual logical inspections are prepaid before the first sample.
                driver.reserveCallback();
                driver.m.charge(8);
                long u=rawNumber(driver.m.meter,"units"),c=rawNumber(driver.m.meter,"copiedEntries"),k=rawNumber(driver.m.meter,"copyChunks");
                groupBefore=rawNumber(driver.m.meter,"groupUnits");
                if(h==null)sink.onTerminalBranch(s);else sink.onBranchLimitSelected(s,h);
                long afterU=rawNumber(driver.m.meter,"units"),afterC=rawNumber(driver.m.meter,"copiedEntries"),afterK=rawNumber(driver.m.meter,"copyChunks");
                long afterG=rawNumber(driver.m.meter,"groupUnits");
                units=afterU-u; copies=afterC-c; chunks=afterK-k; calls++;
                driver.check(afterG-groupBefore==units,"callback stays in its admitted group");
            } catch(RuntimeException e) { throw e; } catch(Exception e) { throw new IllegalStateException("SELECTED_WIRE_CALLBACK",e); }
        }
        private void zero() throws Exception {
            driver.m.charge(6);
            long u=rawNumber(driver.m.meter,"units"),c=rawNumber(driver.m.meter,"copiedEntries"),k=rawNumber(driver.m.meter,"copyChunks");
            units=rawNumber(driver.m.meter,"units")-u; copies=rawNumber(driver.m.meter,"copiedEntries")-c; chunks=rawNumber(driver.m.meter,"copyChunks")-k;
        }
        void drop() { driver=null; sink=null; frame=null; context=null; }
    }
    private static final class Context {
        final String net,start,root;
        final int sx,sy,rx,ry,width,height,goalsTop,goalsBottom,prefix,reached,initial,branchOrdinal,primary;
        Context(String n,String s,String r,int x,int y,int a,int b,int w,int h,int t,int z,int p,int count,int distance,int ordinal,int layer) {
            net=n;start=s;root=r;sx=x;sy=y;rx=a;ry=b;width=w;height=h;goalsTop=t;goalsBottom=z;prefix=p;reached=count;initial=distance;branchOrdinal=ordinal;primary=layer;
        }
    }
    private static final class Frame {
        P06FactoryLinkFixtures.Fixture fixture;
        PcbLayerRoutingPrototype.Session session;
        Object search,branch;
        Context context;
        String before;
        Frame(P06FactoryLinkFixtures.Fixture f,PcbLayerRoutingPrototype.Session s,Object q,Object b,Context c,String fingerprint) {fixture=f;session=s;search=q;branch=b;context=c;before=fingerprint;}
        void drop() {fixture=null;session=null;search=null;branch=null;context=null;before=null;}
    }
    private static int total(String id) {return "FOUND".equals(id)?1:"TOTAL_SEARCH_LIMIT".equals(id)?1000000:"BRANCH_SEARCH_LIMIT".equals(id)?100000:0;}
    private static long rawNumber(Object object,String name) throws Exception {Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return ((Number)f.get(object)).longValue();}
    private static void throwCause(InvocationTargetException e) throws Exception {Throwable t=e.getCause();if(t instanceof Exception)throw (Exception)t;if(t instanceof Error)throw (Error)t;throw new IllegalStateException(t);}
}
