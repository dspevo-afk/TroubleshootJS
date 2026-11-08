package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.Parent;
import com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.SetupMeter;

/** Two ordinary, source-frozen generic references; no authored/captured input. */
public final class GenericRoutingReferenceCanary {
    private static final String KIND="GENERIC_ROUTING_REFERENCE_V1";
    private static final String WIRE="dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6";
    private static final String ORACLE="5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a";
    private GenericRoutingReferenceCanary() { }

    public static void run(String[] args,long started) throws Exception {
        // This guard precedes even Parent's class discovery and either factory.
        if (args==null || args.length!=4 || !KIND.equals(args[0]) ||
                !lowerHex(args[1],36,true) || !lowerHex(args[2],64,false) ||
                !WIRE.equals(args[3])) throw new IOException("GENERIC_REFERENCE_ARGS");
        // Inherited fixed owner bootstrap: no usable Meter exists before these
        // constructors. The caller's first-operation clock covers this work.
        // All new wrapper allocations below use the established SetupMeter.
        Parent parent=new Parent();
        SetupMeter m=new SetupMeter(parent,parent.meter(started));
        Map<String,Object> body=null,references=null,review=null;
        CohortTerminalTelemetryStudy.R9CanaryPublication publication=null;
        try {
            body=object(m);
            put(m,body,"schema","tsj-generic-routing-reference-v1");
            put(m,body,"status","GENERIC_NATURAL_REFERENCES_RECORDED_ONLY");
            put(m,body,"runId",args[1]);
            put(m,body,"sourceManifestSha256",args[2]);
            put(m,body,"wireContractSha256",args[3]);
            references=object(m);
            put(m,references,"OPEN",reference(parent,m,false,started));
            put(m,references,"BLOCKED",reference(parent,m,true,started));
            put(m,body,"references",references);
            review=object(m);
            put(m,review,"scope","GENERIC_NATURAL_ROUTING_REFERENCE_ONLY");
            put(m,review,"physicalOwnerSha256","7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c");
            put(m,review,"studyParentSha256","88986781508aa34783d11e651082122f4bf8e0710a2263b39cc9b597fce9a8cf");
            put(m,review,"runOwnerSha256","4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4");
            put(m,review,"fixtureSourceSha256","dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619");
            put(m,review,"fixtureOracleSha256",ORACLE);
            long constructors=constructorEntries(m);
            require(m,constructors==2,"GENERIC_REFERENCE_CONSTRUCTORS");
            m.charge(1); // boxed recorded scalar
            put(m,review,"sessionConstructorEntries",Long.valueOf(constructors));
            put(m,body,"sourceReview",review);
            // Only this stack owns the complete body until synchronous publication.
            references=null; review=null;
            publication=CohortTerminalTelemetryStudy.prepareR9CanaryPublication(body,m,started);
            CohortTerminalTelemetryStudy.publishR9CanaryStdout(body,m,started,publication);
        } finally {
            publication=null; body=null; references=null; review=null;
            try { m.finish(); } finally { parent=null; m=null; }
        }
    }

    private static boolean lowerHex(String s,int length,boolean uuid) {
        if (s==null || s.length()!=length) return false;
        for (int i=0;i<length;i++) {
            char c=s.charAt(i);
            if (uuid && (i==8 || i==13 || i==18 || i==23)) {
                if (c!='-') return false;
            } else if (!((c>='0' && c<='9') || (c>='a' && c<='f'))) return false;
        }
        return true;
    }

    private static Map<String,Object> object(SetupMeter m) throws Exception {
        m.charge(1); return new LinkedHashMap<String,Object>();
    }
    private static void put(SetupMeter m,Map<String,Object> map,String key,Object value)
            throws Exception {
        m.copy(1); m.charge(1); map.put(key,value);
    }
    private static Object get(SetupMeter m,Map<?,?> map,String key) throws Exception {
        m.charge(1); return map.get(key);
    }
    private static void require(SetupMeter m,boolean ok,String reason) throws Exception {
        m.charge(1); if (!ok) throw new IOException(reason);
    }
    private static Object field(SetupMeter m,Object owner,String name) throws Exception {
        // Discovery, accessibility and read are wrapper metadata, outside runFresh.
        m.charge(4); return CohortTerminalTelemetryStudy.field(owner,name);
    }
    private static long constructorEntries(SetupMeter m) throws Exception {
        Object value=field(m,m.meter,"sessionConstructorEntries");
        m.charge(1); require(m,value instanceof Integer,"GENERIC_REFERENCE_CONSTRUCTOR_TYPE");
        m.charge(1); return ((Integer)value).longValue();
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(SetupMeter m,Object value) throws Exception {
        m.charge(1); require(m,value instanceof Map,"GENERIC_REFERENCE_MAP");
        return (Map<String,Object>)value;
    }
    private static void equal(SetupMeter m,Map<?,?> map,String key,Object expected)
            throws Exception {
        Object actual=get(m,map,key);
        m.charge(1);
        if (expected==null ? actual!=null : !expected.equals(actual))
            throw new IOException("GENERIC_REFERENCE_VALUE");
    }
    private static void integer(SetupMeter m,Map<?,?> map,String key,long expected)
            throws Exception {
        Object actual=get(m,map,key);
        m.charge(2); require(m,actual instanceof Integer || actual instanceof Long,"GENERIC_REFERENCE_INTEGER");
        m.charge(2); require(m,((Number)actual).longValue()==expected,"GENERIC_REFERENCE_INTEGER_VALUE");
    }
    /** Key spelling/order is literal source context, never inferred from output. */
    private static void keys(SetupMeter m,Map<?,?> map,String spelling) throws Exception {
        m.charge(1); int n=1;
        for (int i=0;i<spelling.length();i++) { m.charge(2); if (spelling.charAt(i)=='|') n++; }
        m.charge(1); require(m,map.size()==n,"GENERIC_REFERENCE_KEYS");
        m.charge(3); Iterator<?> it=map.keySet().iterator();
        int start=0;
        for (int index=0;index<n;index++) {
            m.charge(1); int end=spelling.indexOf('|',start);
            if (end<0) { m.charge(1); end=spelling.length(); }
            m.charge(1+end-start); m.copy(end-start);
            String expected=spelling.substring(start,end);
            m.charge(2); require(m,it.hasNext() && expected.equals(it.next()),"GENERIC_REFERENCE_KEY_ORDER");
            start=end+1;
        }
        m.charge(1); require(m,!it.hasNext(),"GENERIC_REFERENCE_KEY_COUNT");
    }
    private static void ids(SetupMeter m,Object value,String spelling) throws Exception {
        m.charge(1); require(m,value instanceof List,"GENERIC_REFERENCE_IDS");
        List<?> actual=(List<?>)value;
        int count=1;
        for (int i=0;i<spelling.length();i++) { m.charge(2); if (spelling.charAt(i)=='|') count++; }
        m.charge(1); require(m,actual.size()==count,"GENERIC_REFERENCE_IDS_COUNT");
        int start=0;
        for (int i=0;i<count;i++) {
            m.charge(1); int end=spelling.indexOf('|',start);
            if (end<0) { m.charge(1); end=spelling.length(); }
            m.charge(1+end-start); m.copy(end-start);
            String expected=spelling.substring(start,end);
            m.charge(2); require(m,expected.equals(actual.get(i)),"GENERIC_REFERENCE_IDS_VALUE");
            start=end+1;
        }
    }

    private static Map<String,Object> reference(Parent p,SetupMeter m,boolean blocked,
            long started) throws Exception {
        FailedFirstFixtures.Fixture fixture=null;
        TroubleshootBoard board=null;
        PcbBoardLayout layout=null;
        List<String> cohort=null;
        Class<?>[] types=null;
        Object[] arguments=null;
        Object fresh=null;
        Map<String,Object> raw=null;
        Map<String,Object> dto=object(m);
        String before=null,after=null;
        long constructorBefore=-1,constructorAfter=-1,cleanupNanos=-1;
        boolean ownerDropped=false,wrapperDropped=false;
        try {
            m.charge(1); // immutable, bounded source-known fixture factory
            fixture=blocked?FailedFirstFixtures.blocked():FailedFirstFixtures.open();
            m.charge(2); board=fixture.board; layout=fixture.layout;
            verifyFixture(m,board,layout,blocked);
            before=geometry(p,m,layout);
            m.charge(1); m.copy(2); cohort=new ArrayList<String>(2);
            m.charge(2); cohort.add("NET_A"); cohort.add("NET_B");
            m.charge(9); m.copy(8);
            types=new Class<?>[]{TroubleshootBoard.class,PcbBoardLayout.class,
                p.captureType,List.class,String.class,p.meterType,long.class,
                PcbLayerRoutingPrototype.Diagnostics.class};
            m.charge(10); m.copy(8);
            arguments=new Object[]{board,layout,null,cohort,KIND,m.meter,Long.valueOf(started),null};
            constructorBefore=constructorEntries(m);
            require(m,constructorBefore==(blocked?1:0),"GENERIC_REFERENCE_CONSTRUCTOR_BEFORE");
            // Prepay Parent.call discovery/invoke and the four immediate post-call
            // field discoveries/reads (constructor/json/cleanup/dropped) before run.
            m.charge(4+4*4+3);
            m.finish();
            fresh=p.call("runFresh",types,arguments);
            Object count=CohortTerminalTelemetryStudy.field(m.meter,"sessionConstructorEntries");
            Object json=CohortTerminalTelemetryStudy.field(fresh,"json");
            Object nanos=CohortTerminalTelemetryStudy.field(fresh,"cleanupNanos");
            Object dropped=CohortTerminalTelemetryStudy.field(fresh,"referencesDropped");
            m.charge(3); require(m,count instanceof Integer && nanos instanceof Long && dropped instanceof Boolean,
                "GENERIC_REFERENCE_RETURN_TYPES");
            m.charge(3);
            constructorAfter=((Integer)count).longValue();
            cleanupNanos=((Long)nanos).longValue(); ownerDropped=((Boolean)dropped).booleanValue();
            m.charge(4); require(m,constructorAfter==constructorBefore+1 && ownerDropped &&
                cleanupNanos>=0 && cleanupNanos<=120000000000L,"GENERIC_REFERENCE_OWNER_CLEANUP");
            raw=map(m,json); json=null;
            validateRun(m,raw,blocked);
            after=geometry(p,m,layout);
            m.charge(1); require(m,before.equals(after),"GENERIC_REFERENCE_SOURCE_CHANGED");
            put(m,dto,"caseId",blocked?"BLOCKED":"OPEN");
            put(m,dto,"fixtureContextSha256",ORACLE);
            put(m,dto,"run",raw); // actual returned tree; no rewriting or reconstruction
        } finally {
            // Drop every fixture/live/run and invocation-array alias before the
            // wrapper flag. The detached JSON tree is owned only by dto now.
            fixture=null; board=null; layout=null; cohort=null;
            fresh=null; raw=null; types=null; arguments=null;
            wrapperDropped=true;
        }
        Map<String,Object> cleanup=object(m);
        put(m,cleanup,"ownerReferencesDropped",Boolean.valueOf(ownerDropped));
        m.charge(1); put(m,cleanup,"ownerCleanupNanos",Long.valueOf(cleanupNanos));
        put(m,cleanup,"wrapperAliasesDropped",Boolean.valueOf(wrapperDropped));
        put(m,dto,"cleanup",cleanup); cleanup=null;
        Map<String,Object> construction=object(m);
        m.charge(2);
        put(m,construction,"before",Long.valueOf(constructorBefore));
        put(m,construction,"after",Long.valueOf(constructorAfter));
        put(m,dto,"construction",construction); construction=null;
        Map<String,Object> source=object(m);
        put(m,source,"beforeSha256",before); put(m,source,"afterSha256",after);
        put(m,source,"stable",Boolean.TRUE); put(m,dto,"sourceGeometry",source);
        m.finish(); return dto;
    }

    private static String geometry(Parent p,SetupMeter m,PcbBoardLayout layout) throws Exception {
        // Existing helper owns fingerprint copy census, canonical text and hash.
        // Prepay its reflection Class[3]/Object[3]/Class[1]/Object[1] metadata.
        m.charge(14); m.copy(3); m.copy(3); m.copy(1); m.copy(1);
        String hash=CohortTerminalTelemetryStudy.geometry(p,m,layout);
        m.charge(64); require(m,lowerHex(hash,64,false),"GENERIC_REFERENCE_GEOMETRY_SHA");
        return hash;
    }

    private static void verifyFixture(SetupMeter m,TroubleshootBoard b,PcbBoardLayout l,
            boolean blocked) throws Exception {
        int pads=blocked?8:4,nets=blocked?6:2;
        m.charge(5);
        require(m,(blocked?"FAILED_FIRST_BLOCKED":"FAILED_FIRST_OPEN").equals(b.getId()) &&
            l.getWidth()==900 && l.getHeight()==700,"GENERIC_REFERENCE_FIXTURE");
        // Census reads borrow the source maps/vectors: no copied census or Session.
        census(m,b,"nets",nets); census(m,b,"pads",pads); census(m,b,"components",pads);
        census(m,l,"pads",pads); census(m,l,"components",pads);
        census(m,l,"traces",0); census(m,l,"holes",0);
        m.charge(2); Rectangle outline=l.getBoardOutline(); Rectangle tray=l.getPartsTray();
        m.charge(8);
        require(m,outline.x==100 && outline.y==100 && outline.width==600 && outline.height==500 &&
            tray.x==720 && tray.y==120 && tray.width==160 && tray.height==400,"GENERIC_REFERENCE_BOUNDS");
        pad(m,b,l,"A1.1","NET_A","A1",260,300,BoardNet.RoutingRole.HIGH_CURRENT,2,outline);
        pad(m,b,l,"A2.1","NET_A","A2",500,300,BoardNet.RoutingRole.HIGH_CURRENT,2,outline);
        pad(m,b,l,"B1.1","NET_B","B1",260,450,BoardNet.RoutingRole.CONTROL,2,outline);
        pad(m,b,l,"B2.1","NET_B","B2",500,450,BoardNet.RoutingRole.CONTROL,2,outline);
        if (blocked) {
            pad(m,b,l,"BLOCK_E.1","BLOCK_E_NET","BLOCK_E",520,300,BoardNet.RoutingRole.SIGNAL,1,outline);
            pad(m,b,l,"BLOCK_W.1","BLOCK_W_NET","BLOCK_W",480,300,BoardNet.RoutingRole.SIGNAL,1,outline);
            pad(m,b,l,"BLOCK_N.1","BLOCK_N_NET","BLOCK_N",500,280,BoardNet.RoutingRole.SIGNAL,1,outline);
            pad(m,b,l,"BLOCK_S.1","BLOCK_S_NET","BLOCK_S",500,320,BoardNet.RoutingRole.SIGNAL,1,outline);
        }
    }
    private static void census(SetupMeter m,Object owner,String name,int expected) throws Exception {
        Object value=field(m,owner,name);
        m.charge(2); int count;
        if (value instanceof Map) count=((Map<?,?>)value).size();
        else if (value instanceof java.util.Collection) count=((java.util.Collection<?>)value).size();
        else throw new IOException("GENERIC_REFERENCE_CENSUS_TYPE");
        require(m,count==expected,"GENERIC_REFERENCE_CENSUS");
    }
    private static void pad(SetupMeter m,TroubleshootBoard b,PcbBoardLayout l,String padId,
            String netId,String componentId,int x,int y,BoardNet.RoutingRole role,int netPads,
            Rectangle outline) throws Exception {
        m.charge(3); BoardPad logical=b.getPad(padId); PcbPadPlacement physical=l.getPad(padId);
        BoardNet net=b.getNet(netId);
        m.charge(3); require(m,logical!=null && physical!=null && net!=null,"GENERIC_REFERENCE_PAD_MISSING");
        m.charge(14);
        require(m,padId.equals(logical.getId()) && netId.equals(logical.getNetId()) &&
            componentId.equals(logical.getComponentId()) && "1".equals(logical.getTerminalId()) &&
            padId.equals(physical.getPadId()) && physical.getX()==x && physical.getY()==y &&
            physical.getAttachment()==PcbTerminalAttachment.PLATED_THROUGH_HOLE &&
            netId.equals(net.getId()) && net.getRoutingRole()==role && b.getComponent(componentId)!=null &&
            l.getComponent(componentId)!=null && componentId.equals(l.getComponent(componentId).getComponentId()),
            "GENERIC_REFERENCE_PAD_IDENTITY");
        census(m,net,"padIds",netPads);
        Object members=field(m,net,"padIds");
        m.charge(2); require(m,members instanceof List && ((List<?>)members).contains(padId),
            "GENERIC_REFERENCE_NET_PAD_MEMBERSHIP");
        m.charge(2); Rectangle land=physical.getPadBounds(),probe=physical.getProbeBounds();
        m.charge(16);
        require(m,contains(outline,land) && contains(outline,probe),"GENERIC_REFERENCE_PAD_BOUNDS");
    }
    private static boolean contains(Rectangle outer,Rectangle inner) {
        return inner.width>0 && inner.height>0 && inner.x>=outer.x && inner.y>=outer.y &&
            (long)inner.x+inner.width<=(long)outer.x+outer.width &&
            (long)inner.y+inner.height<=(long)outer.y+outer.height;
    }

    private static void validateRun(SetupMeter m,Map<String,Object> run,boolean blocked)
            throws Exception {
        keys(m,run,"kind|initial|ordering|outcome|terminalFailure|finalizers|layout|work");
        equal(m,run,"kind",KIND);
        Map<String,Object> initial=map(m,get(m,run,"initial"));
        keys(m,initial,"pass|passes|expanded|maximumExpansions|maximumVias|priorPromotedNetId|policy|viaCost|policyTransitions|policyVias|branchOrderStrategy|netOrderStrategy|roleWidths|capturedOrderingStream|sessionOrderingStream|instrumentationModeDifference|routingRelevantSettingsMatch|traceCount|holeCount");
        integer(m,initial,"pass",2); integer(m,initial,"passes",0); integer(m,initial,"expanded",0);
        integer(m,initial,"maximumExpansions",1000000); integer(m,initial,"maximumVias",48);
        equal(m,initial,"priorPromotedNetId",null); equal(m,initial,"policy","FULLER_TWO_LAYER");
        integer(m,initial,"viaCost",60); integer(m,initial,"policyTransitions",4); integer(m,initial,"policyVias",48);
        equal(m,initial,"branchOrderStrategy","CANONICAL_NEAREST"); equal(m,initial,"netOrderStrategy","CANONICAL");
        equal(m,initial,"roleWidths",Boolean.TRUE); equal(m,initial,"capturedOrderingStream",Boolean.FALSE);
        equal(m,initial,"sessionOrderingStream",Boolean.FALSE); equal(m,initial,"instrumentationModeDifference",Boolean.FALSE);
        equal(m,initial,"routingRelevantSettingsMatch",Boolean.TRUE);
        integer(m,initial,"traceCount",0); integer(m,initial,"holeCount",0);
        Map<String,Object> order=map(m,get(m,run,"ordering"));
        keys(m,order,"capturedNetIds|computedNetIds|requestedCohortNetIds|requestedNetIds|traversedNetIds|orderMatchesCapture|searchCount");
        String computed=blocked?"BLOCK_W_NET|BLOCK_S_NET|BLOCK_N_NET|BLOCK_E_NET|NET_B|NET_A":"NET_B|NET_A";
        ids(m,get(m,order,"capturedNetIds"),computed); ids(m,get(m,order,"computedNetIds"),computed);
        ids(m,get(m,order,"requestedCohortNetIds"),"NET_A|NET_B");
        ids(m,get(m,order,"requestedNetIds"),blocked?"NET_A|NET_B|BLOCK_W_NET|BLOCK_S_NET|BLOCK_N_NET|BLOCK_E_NET":"NET_A|NET_B");
        ids(m,get(m,order,"traversedNetIds"),blocked?"NET_A":"NET_A|NET_B");
        equal(m,order,"orderMatchesCapture",Boolean.TRUE); integer(m,order,"searchCount",1);
        Map<String,Object> outcome=map(m,get(m,run,"outcome"));
        keys(m,outcome,"disposition|reason|complete|ownerComplete|pass|passes|expanded|maximumExpansions|errorClass|errorMessage");
        equal(m,outcome,"disposition",blocked?"NO_PATH":"FOUND"); equal(m,outcome,"reason",blocked?"NET_A:NO_PATH":"SUCCESS");
        equal(m,outcome,"complete",Boolean.valueOf(!blocked)); equal(m,outcome,"ownerComplete",Boolean.TRUE);
        integer(m,outcome,"pass",blocked?3:2); integer(m,outcome,"passes",1);
        integer(m,outcome,"maximumExpansions",1000000); equal(m,outcome,"errorClass",null); equal(m,outcome,"errorMessage",null);
        Object terminal=get(m,run,"terminalFailure");
        if (blocked) {
            Map<String,Object> failure=map(m,terminal);
            keys(m,failure,"failedNetId|searchIndex|searchPass|passBefore|passAfter|advanceCallOrdinal|ownerSearchCleared|searchReleased|ownerPromotedNetId|ownerReason");
            equal(m,failure,"failedNetId","NET_A"); integer(m,failure,"searchIndex",1);
            integer(m,failure,"searchPass",2); integer(m,failure,"passBefore",2); integer(m,failure,"passAfter",3);
            equal(m,failure,"ownerSearchCleared",Boolean.TRUE); equal(m,failure,"searchReleased",Boolean.TRUE);
            equal(m,failure,"ownerPromotedNetId","NET_A"); equal(m,failure,"ownerReason","NET_A:NO_PATH");
        } else { m.charge(1); require(m,terminal==null,"GENERIC_REFERENCE_OPEN_TERMINAL"); }
        Map<String,Object> finalizers=map(m,get(m,run,"finalizers"));
        keys(m,finalizers,"routingGeometry|allNetConnectivity|twoLayerRules|routeQuality|viaCap");
        String verdict=blocked?"NOT_RUN":"PASS";
        equal(m,finalizers,"routingGeometry",verdict); equal(m,finalizers,"allNetConnectivity",verdict);
        equal(m,finalizers,"twoLayerRules",verdict); equal(m,finalizers,"routeQuality",verdict); equal(m,finalizers,"viaCap",verdict);
        Object layout=get(m,run,"layout");
        m.charge(1); require(m,blocked?layout==null:layout instanceof Map,"GENERIC_REFERENCE_LAYOUT");
        Map<String,Object> work=map(m,get(m,run,"work"));
        // Actual source insertion order, rather than N's set-based key tuple.
        keys(m,work,"advanceCalls|failureObservationUnits|terminalEvidenceUnits|ownerUnits|wrapperUnits|copyEntries|copyChunks|groups|maxGroupNanos|maximumUnitsPerGroup");
        integer(m,work,"terminalEvidenceUnits",blocked?12:0);
        // Independent work equations and complete copper validation belong to
        // the pinned pure reader; no counters are synthesized here.
    }
}
