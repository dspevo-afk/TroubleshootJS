package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Synthetic-only canary for the current V2 codec. The root-owned fixture
 * children intentionally fail normal full-D semantics. This class does not
 * read files, create a producer, run a graph, or publish normal output.
 */
public final class CurrentV2CanonicalCodecCanary {
    private static final long MAX_BODY_VISITS=1000000L;
    private static final int MAX_BODY_DEPTH=64;
    private static final int MAX_MARKER_BYTES=656;
    private static final String WIRE_SHA=
        "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed";
    private static final String[] BODY_KEYS={
        "schema","mode","status","runId","sourceManifestSha256",
        "wireContractSha256","inputContextSha256","qualifiedPrior",
        "originalCapture","cohort","arms","work","cleanup"
    };
    private static final String[] MARKER_KEYS={
        "schema","status","runId","sourceManifestSha256","wireContractSha256",
        "inputContextSha256","resultSha256","resultBytes","elapsedNanos",
        "finalExportNanos","totalGroups","totalUnits","maxGroupNanos",
        "maximumUnitsPerGroup"
    };
    private static final long[] MARKER_MAXIMA={
        1048576L,90000000000L,5000000000L,640L,Long.MAX_VALUE,
        5000000000L,32768L
    };

    private static int assertions;
    private static int cases;

    private CurrentV2CanonicalCodecCanary() { }

    public static void main(String[] args) throws Exception {
        if (args==null || args.length!=0) throw new IllegalArgumentException("CANARY_ARGUMENTS");
        testSmallIndependentBodyOracle();
        testWideActualCountWriteAndCompare();
        testRealCountSinkByteCap();
        testBodyHeadersAndContainers();
        testBodyOrdinaryDomain();
        testDepthBoundary();
        testVisitBoundary();
        testMarkerOracleAndBounds();
        System.out.println("SYNTHETIC_CODEC_COMPONENT_ONLY cases="+cases+
            " assertions="+assertions+"; normalHostAcceptance=NONE");
    }

    private static void testSmallIndependentBodyOracle() throws Exception {
        Map<String,Object> body=CurrentV2CodecOracle.body();
        checkRootKeyOrder(body);
        check(WIRE_SHA.equals(CurrentV2CodecOracle.WIRE_SHA),
            "INDEPENDENT_WIRE_FIXTURE_ID");
        byte[] expected=asciiBytes(CurrentV2CodecOracle.BODY_JSON);
        MemorySink first=new MemorySink();
        CurrentV2CanonicalCodec.writeBody(body,first);
        check(Arrays.equals(first.bytes(),expected),"SMALL_BODY_PYTHON_ORACLE_BYTES");
        check(oneFinalLf(first.bytes()),"SMALL_BODY_ONE_FINAL_LF");
        check(expected.length==1177,"SMALL_BODY_ORACLE_LENGTH");
        cases++;
    }

    private static void testWideActualCountWriteAndCompare() throws Exception {
        final Map<String,Object> wide=CurrentV2CodecWideOracle.wideBody();
        final byte[] expected=asciiBytes(CurrentV2CodecWideOracle.WIDE_BODY_JSON);
        final String workPrefix="\"work\":{";
        int workStart=CurrentV2CodecWideOracle.WIDE_BODY_JSON.indexOf(workPrefix);
        checkRootKeyOrder(wide);
        check(expected.length==6370,"WIDE_BODY_ORACLE_LENGTH");
        check(workStart>=0,"WIDE_BODY_WORK_PREFIX_PRESENT");

        @SuppressWarnings("unchecked")
        Map<String,Object> rawWork=(Map<String,Object>)wide.get("work");
        SinkPosition position=new SinkPosition();
        ObservableMap observedWork=new ObservableMap(rawWork,position,
            (long)workStart+(long)workPrefix.length());
        wide.put("work",observedWork);
        AdmissionGuardMap guardedBody=new AdmissionGuardMap(wide);

        MeterFixture meter=new MeterFixture();
        meter.setup.charge(1L); // open the real selected Meter group for R5CountSink
        long beforeUnits=number(meter.setup.meter,"units");
        long exportStarted=number(meter.setup.meter,"groupStarted");
        CohortTerminalTelemetryStudy.R5CountSink countSink=
            new CohortTerminalTelemetryStudy.R5CountSink(meter.setup,meter.started,exportStarted);
        position.bind(countSink);
        CurrentV2CanonicalCodec.writeBody(guardedBody,new AdmissionSink(countSink,guardedBody));
        countSink.complete((long)expected.length);
        check(countSink.position==(long)expected.length,"WIDE_ACTUAL_COUNT_SINK_LENGTH");
        check(observedWork.descendantReads>0,
            "WIDE_DESCENDANT_READ_AFTER_WORK_PREFIX");
        check(guardedBody.admitted,"WIDE_ACTUAL_SINK_ADMITTED_BEFORE_BODY_READ");
        long expectedBlocks=((long)expected.length+4095L)/4096L;
        check(expectedBlocks==2L,"WIDE_CROSSES_TWO_REAL_COUNT_BLOCKS");
        check(number(meter.setup.meter,"units")-beforeUnits==expectedBlocks,
            "WIDE_REAL_R5_COUNT_SINK_METER_DELTA");
        meter.setup.finish();

        guardedBody.resetAdmission();
        MemorySink writeSink=new MemorySink();
        position.bind(writeSink);
        CurrentV2CanonicalCodec.writeBody(guardedBody,new AdmissionSink(writeSink,guardedBody));
        byte[] written=writeSink.bytes();
        check(Arrays.equals(written,expected),"WIDE_BODY_PYTHON_ORACLE_BYTES");
        check(oneFinalLf(written),"WIDE_BODY_ONE_FINAL_LF");
        check(observedWork.descendantReads>0,"WIDE_WRITE_DESCENDANT_AFTER_PREFIX");

        guardedBody.resetAdmission();
        MemorySink compareSink=new MemorySink();
        position.bind(compareSink);
        CurrentV2CanonicalCodec.writeBody(guardedBody,
            new AdmissionSink(compareSink,guardedBody));
        check(Arrays.equals(compareSink.bytes(),written),"WIDE_BODY_REPEAT_PASS_IDENTICAL");
        check(Arrays.equals(compareSink.bytes(),expected),"WIDE_BODY_COMPARE_ORACLE_IDENTICAL");
        cases++;
    }

    private static void testRealCountSinkByteCap() throws Exception {
        Map<String,Object> body=CurrentV2CodecOracle.body();
        work(body).put("byteCap",new LazyNullList(300000));
        long independentVisits=independentVisitCount(body);
        long independentMinimumBytes=(5L*300000L)-1L;
        check(independentVisits<MAX_BODY_VISITS,"BYTE_CAP_BODY_WITHIN_DOMAIN_VISITS");
        check(independentMinimumBytes>1048576L,"BYTE_CAP_BODY_EXCEEDS_RESULT_LIMIT");

        MeterFixture meter=new MeterFixture();
        meter.setup.charge(1L);
        long beforeUnits=number(meter.setup.meter,"units");
        long beforeCopies=number(meter.setup.meter,"copiedEntries");
        long beforeChunks=number(meter.setup.meter,"copyChunks");
        long exportStarted=number(meter.setup.meter,"groupStarted");
        CohortTerminalTelemetryStudy.R5CountSink sink=
            new CohortTerminalTelemetryStudy.R5CountSink(meter.setup,meter.started,exportStarted);
        IOException lengthFailure=null;
        try {
            CurrentV2CanonicalCodec.writeBody(body,sink);
        } catch (IOException failed) {
            lengthFailure=failed;
        }
        check(lengthFailure!=null && "R5_RESULT_LENGTH".equals(lengthFailure.getMessage()),
            "REAL_COUNT_SINK_EXACT_RESULT_LENGTH_FAILURE");
        check(sink.position==1048576L,"REAL_COUNT_SINK_STOPS_AT_ONE_MIB");
        check(number(meter.setup.meter,"units")-beforeUnits==256L,
            "REAL_COUNT_SINK_CHARGES_256_BLOCKS");
        check(number(meter.setup.meter,"copiedEntries")==beforeCopies &&
            number(meter.setup.meter,"copyChunks")==beforeChunks,
            "REAL_COUNT_SINK_DOES_NOT_ADMIT_COPIES");
        meter.setup.finish();
        cases++;
    }

    private static void testBodyHeadersAndContainers() throws Exception {
        expectBodyCode("BODY_ROOT_TYPE",null,new MemorySink());
        expectBodyCode("BODY_SINK",CurrentV2CodecOracle.body(),null);

        Map<String,Object> reordered=CurrentV2CodecOracle.body();
        Object schema=reordered.remove("schema");
        reordered.put("schema",schema);
        expectBodyCode("BODY_ROOT_ORDER",reordered,new MemorySink());

        Map<String,Object> missing=CurrentV2CodecOracle.body();
        missing.remove("cleanup");
        expectBodyCode("BODY_ROOT_ORDER",missing,new MemorySink());

        Map<String,Object> extra=CurrentV2CodecOracle.body();
        extra.put("extra",Boolean.TRUE);
        expectBodyCode("BODY_ROOT_ORDER",extra,new MemorySink());

        Map<String,Object> bad=CurrentV2CodecOracle.body();
        bad.put("schema","old-result-schema");
        expectBodyCode("BODY_SCHEMA",bad,new MemorySink());
        bad=CurrentV2CodecOracle.body();
        bad.put("mode","CONTROL");
        expectBodyCode("BODY_MODE",bad,new MemorySink());
        bad=CurrentV2CodecOracle.body();
        bad.put("status","COMPLETED");
        expectBodyCode("BODY_STATUS",bad,new MemorySink());

        bad=CurrentV2CodecOracle.body();
        bad.put("runId","00000000-0000-0000-0000-00000000000G");
        expectBodyCode("BODY_RUN_ID",bad,new MemorySink());
        bad=CurrentV2CodecOracle.body();
        bad.put("sourceManifestSha256",repeat('A',64));
        expectBodyCode("BODY_SOURCE_ID",bad,new MemorySink());
        bad=CurrentV2CodecOracle.body();
        bad.put("wireContractSha256",repeat('2',64));
        expectBodyCode("BODY_WIRE_ID",bad,new MemorySink());
        bad=CurrentV2CodecOracle.body();
        bad.put("inputContextSha256",repeat('g',64));
        expectBodyCode("BODY_CONTEXT_ID",bad,new MemorySink());

        String[] containers={
            "qualifiedPrior","originalCapture","cohort","arms","work","cleanup"
        };
        for (int i=0;i<containers.length;i++) {
            bad=CurrentV2CodecOracle.body();
            bad.put(containers[i],Boolean.TRUE);
            expectBodyCode("BODY_ROOT_CONTAINER",bad,new MemorySink());
        }
        bad=CurrentV2CodecOracle.body();
        bad.put("arms",new ArrayList<Object>());
        expectBodyCode("BODY_ROOT_CONTAINER",bad,new MemorySink());
    }

    private static void testBodyOrdinaryDomain() throws Exception {
        Map<String,Object> body=CurrentV2CodecOracle.body();
        work(body).put(repeat('k',256),Boolean.TRUE);
        DiscardSink acceptedKeySink=new DiscardSink();
        CurrentV2CanonicalCodec.writeBody(body,acceptedKeySink);
        check(acceptedKeySink.position>0L,"KEY_ASCII_256_ACCEPTED");
        cases++;

        expectBodyCode("BODY_STRING",bodyWithWork("tooLong",repeat('x',257)),
            new MemorySink());
        expectBodyCode("BODY_MAP_KEY",bodyWithWork(repeat('k',257),Boolean.TRUE),
            new MemorySink());
        expectBodyCode("BODY_STRING",bodyWithWork("control","bad"+(char)31+"text"),
            new MemorySink());
        expectBodyCode("BODY_STRING",bodyWithWork("nonAscii","caf"+(char)233),
            new MemorySink());
        expectBodyCode("BODY_STRING",bodyWithWork("geometryCanonical",repeat('x',4096)),
            new MemorySink());
        expectBodyCode("BODY_VALUE",bodyWithWork("float",Float.valueOf(1.25f)),
            new MemorySink());
        expectBodyCode("BODY_VALUE",bodyWithWork("double",Double.valueOf(1.25d)),
            new MemorySink());
        expectBodyCode("BODY_VALUE",bodyWithWork("otherNumber",java.math.BigInteger.ONE),
            new MemorySink());
        expectBodyCode("BODY_VALUE",bodyWithWork("array",new int[]{1,2,3}),
            new MemorySink());

        Map<String,Object> cyclic=CurrentV2CodecOracle.body();
        List<Object> cycle=new ArrayList<Object>();
        cycle.add(cycle);
        work(cyclic).put("cycle",cycle);
        expectBodyCode("BODY_CYCLE",cyclic,new MemorySink());

        Map<String,Object> invalidKeyBody=CurrentV2CodecOracle.body();
        Map<Object,Object> invalidKeyMap=new LinkedHashMap<Object,Object>();
        invalidKeyMap.put(Integer.valueOf(7),Boolean.TRUE);
        work(invalidKeyBody).put("badMap",invalidKeyMap);
        expectBodyCode("BODY_MAP_KEY",invalidKeyBody,new MemorySink());
    }

    private static void testDepthBoundary() throws Exception {
        Map<String,Object> atLimit=bodyWithWork("deep",nestedLists(62));
        check(independentMaximumDepth(atLimit)==MAX_BODY_DEPTH,
            "INDEPENDENT_DEPTH_64_FIXTURE");
        DiscardSink accepted=new DiscardSink();
        CurrentV2CanonicalCodec.writeBody(atLimit,accepted);
        check(accepted.position>0L,"BODY_DEPTH_64_ACCEPTED");
        cases++;

        Map<String,Object> over=bodyWithWork("deep",nestedLists(63));
        check(independentMaximumDepth(over)==MAX_BODY_DEPTH+1,
            "INDEPENDENT_DEPTH_65_FIXTURE");
        expectBodyCode("BODY_DEPTH",over,new DiscardSink());
    }

    private static void testVisitBoundary() throws Exception {
        Map<String,Object> exact=bodyAtVisitCount(MAX_BODY_VISITS);
        DiscardSink accepted=new DiscardSink();
        CurrentV2CanonicalCodec.writeBody(exact,accepted);
        check(accepted.position>0L,"BODY_VISIT_LIMIT_EXACTLY_ACCEPTED");
        cases++;

        Map<String,Object> over=bodyAtVisitCount(MAX_BODY_VISITS+1L);
        expectBodyCode("BODY_VISITS",over,new DiscardSink());
    }

    private static void testMarkerOracleAndBounds() throws Exception {
        long[] maxima=CurrentV2CodecOracle.markerMaxima();
        check(Arrays.equals(maxima,MARKER_MAXIMA),"INDEPENDENT_MARKER_NUMERIC_MAXIMA");
        String expectedText=CurrentV2CodecOracle.MAX_MARKER_JSON;
        byte[] expected=asciiBytes(expectedText);
        check(expected.length==MAX_MARKER_BYTES,"MARKER_ORACLE_656_BYTES");
        CurrentV2CanonicalCodec.Marker maximum=marker(maxima);
        check(CurrentV2CanonicalCodec.countMarker(maximum)==MAX_MARKER_BYTES,
            "MARKER_MAXIMUM_COUNT");
        byte[] target=new byte[CurrentV2CanonicalCodec.countMarker(maximum)];
        CurrentV2CanonicalCodec.writeMarker(maximum,target);
        check(Arrays.equals(target,expected),"MARKER_PYTHON_ORACLE_BYTES");
        check(oneFinalLf(target),"MARKER_ONE_FINAL_LF");
        check(markerKeyOrder(expectedText),"MARKER_INDEPENDENT_KEY_ORDER");
        cases++;

        long[] minimum={1L,0L,0L,0L,0L,0L,0L};
        CurrentV2CanonicalCodec.Marker minMarker=marker(minimum);
        int minLength=CurrentV2CanonicalCodec.countMarker(minMarker);
        byte[] minTarget=new byte[minLength];
        CurrentV2CanonicalCodec.writeMarker(minMarker,minTarget);
        check(minLength>=2 && minLength<MAX_MARKER_BYTES,"MARKER_MINIMUM_DOMAIN_ACCEPTED");
        check(oneFinalLf(minTarget),"MARKER_MINIMUM_ONE_FINAL_LF");
        cases++;

        for (int i=0;i<MARKER_MAXIMA.length;i++) {
            long[] below=MARKER_MAXIMA.clone();
            below[i]=(i==0?0L:-1L);
            expectMarkerCode("MARKER_NUMERIC_BOUND",marker(below));
            if (MARKER_MAXIMA[i]!=Long.MAX_VALUE) {
                long[] above=MARKER_MAXIMA.clone();
                above[i]=MARKER_MAXIMA[i]+1L;
                expectMarkerCode("MARKER_NUMERIC_BOUND",marker(above));
            }
        }

        expectMarkerCode("MARKER_NULL",null);
        CurrentV2CanonicalCodec.Marker valid=marker(MARKER_MAXIMA.clone());
        expectMarkerCode("MARKER_RUN_ID",marker("bad",CurrentV2CodecOracle.SOURCE_SHA,
            WIRE_SHA,CurrentV2CodecOracle.CONTEXT_SHA,CurrentV2CodecOracle.digest(),
            MARKER_MAXIMA.clone()));
        expectMarkerCode("MARKER_SOURCE_ID",marker(CurrentV2CodecOracle.RUN_ID,
            repeat('A',64),WIRE_SHA,CurrentV2CodecOracle.CONTEXT_SHA,
            CurrentV2CodecOracle.digest(),MARKER_MAXIMA.clone()));
        expectMarkerCode("MARKER_WIRE_ID",marker(CurrentV2CodecOracle.RUN_ID,
            CurrentV2CodecOracle.SOURCE_SHA,repeat('2',64),
            CurrentV2CodecOracle.CONTEXT_SHA,CurrentV2CodecOracle.digest(),
            MARKER_MAXIMA.clone()));
        expectMarkerCode("MARKER_CONTEXT_ID",marker(CurrentV2CodecOracle.RUN_ID,
            CurrentV2CodecOracle.SOURCE_SHA,WIRE_SHA,repeat('g',64),
            CurrentV2CodecOracle.digest(),MARKER_MAXIMA.clone()));
        expectMarkerCode("MARKER_RESULT_DIGEST",marker(CurrentV2CodecOracle.RUN_ID,
            CurrentV2CodecOracle.SOURCE_SHA,WIRE_SHA,CurrentV2CodecOracle.CONTEXT_SHA,
            new byte[31],MARKER_MAXIMA.clone()));
        expectMarkerCode("MARKER_RESULT_DIGEST",marker(CurrentV2CodecOracle.RUN_ID,
            CurrentV2CodecOracle.SOURCE_SHA,WIRE_SHA,CurrentV2CodecOracle.CONTEXT_SHA,
            null,MARKER_MAXIMA.clone()));

        try {
            CurrentV2CanonicalCodec.writeMarker(valid,new byte[MAX_MARKER_BYTES-1]);
        } catch (CurrentV2CanonicalCodec.CodecFailure failure) {
            if ("MARKER_LENGTH".equals(failure.code)) {
                assertions++;
                cases++;
                valid=null;
            } else {
                throw new AssertionError("EXPECTED_MARKER_LENGTH_GOT_"+failure.code,failure);
            }
        }
        if (valid!=null) throw new AssertionError("MISSING_MARKER_LENGTH");
        expectMarkerTarget(validMarker(),null,"MARKER_TARGET");
        expectMarkerTarget(validMarker(),new byte[MAX_MARKER_BYTES+1],"MARKER_TARGET");
    }

    private static void expectMarkerTarget(CurrentV2CanonicalCodec.Marker marker,
            byte[] target,String expected) throws Exception {
        try {
            CurrentV2CanonicalCodec.writeMarker(marker,target);
        } catch (CurrentV2CanonicalCodec.CodecFailure failure) {
            if (expected.equals(failure.code)) { assertions++; cases++; return; }
            throw new AssertionError("EXPECTED_"+expected+"_GOT_"+failure.code,failure);
        }
        throw new AssertionError("MISSING_"+expected);
    }

    private static void expectBodyCode(String expected,Map<String,Object> body,
            CohortTerminalTelemetryStudy.CanonicalJsonSink sink) throws Exception {
        try {
            CurrentV2CanonicalCodec.writeBody(body,sink);
        } catch (CurrentV2CanonicalCodec.CodecFailure failure) {
            if (expected.equals(failure.code)) { assertions++; cases++; return; }
            throw new AssertionError("EXPECTED_"+expected+"_GOT_"+failure.code,failure);
        }
        throw new AssertionError("MISSING_"+expected);
    }

    private static void expectMarkerCode(String expected,
            CurrentV2CanonicalCodec.Marker marker) throws Exception {
        try {
            CurrentV2CanonicalCodec.countMarker(marker);
        } catch (CurrentV2CanonicalCodec.CodecFailure failure) {
            if (expected.equals(failure.code)) { assertions++; cases++; return; }
            throw new AssertionError("EXPECTED_"+expected+"_GOT_"+failure.code,failure);
        }
        throw new AssertionError("MISSING_"+expected);
    }

    private static void checkRootKeyOrder(Map<String,Object> body) {
        if (body.size()!=BODY_KEYS.length) throw new AssertionError("ROOT_FIXTURE_SIZE");
        Iterator<String> actual=body.keySet().iterator();
        for (int i=0;i<BODY_KEYS.length;i++) {
            if (!actual.hasNext() || !BODY_KEYS[i].equals(actual.next()))
                throw new AssertionError("INDEPENDENT_BODY_ROOT_ORDER_"+i);
        }
        if (actual.hasNext()) throw new AssertionError("INDEPENDENT_BODY_ROOT_EXTRA_KEY");
        assertions++;
    }

    private static boolean markerKeyOrder(String json) {
        int position=0;
        for (int i=0;i<MARKER_KEYS.length;i++) {
            String token="\""+MARKER_KEYS[i]+"\":";
            int found=json.indexOf(token,position);
            if (found<position) return false;
            position=found+token.length();
        }
        return true;
    }

    private static CurrentV2CanonicalCodec.Marker validMarker() {
        return marker(MARKER_MAXIMA.clone());
    }

    private static CurrentV2CanonicalCodec.Marker marker(long[] values) {
        return marker(CurrentV2CodecOracle.RUN_ID,CurrentV2CodecOracle.SOURCE_SHA,
            CurrentV2CodecOracle.WIRE_SHA,CurrentV2CodecOracle.CONTEXT_SHA,
            CurrentV2CodecOracle.digest(),values);
    }

    private static CurrentV2CanonicalCodec.Marker marker(String runId,String sourceSha,
            String wireSha,String contextSha,byte[] digest,long[] values) {
        return new CurrentV2CanonicalCodec.Marker(runId,sourceSha,wireSha,contextSha,
            digest,values[0],values[1],values[2],values[3],values[4],values[5],values[6]);
    }

    private static Map<String,Object> bodyWithWork(String key,Object value) {
        Map<String,Object> body=CurrentV2CodecOracle.body();
        work(body).put(key,value);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> work(Map<String,Object> body) {
        return (Map<String,Object>) body.get("work");
    }

    private static Map<String,Object> bodyAtVisitCount(long target) {
        Map<String,Object> body=CurrentV2CodecOracle.body();
        Map<String,Object> oldWork=work(body);
        long fixed=independentVisitCount(body)-independentVisitCount(oldWork)+1L;
        long remaining=target-fixed;
        boolean special=(remaining&1L)!=0L;
        if (special) remaining-=3L;
        if (remaining<0L || remaining%2L!=0L || remaining/2L>Integer.MAX_VALUE)
            throw new AssertionError("VISIT_FIXTURE_ARITHMETIC");
        int entryCount=(int)(remaining/2L);
        body.put("work",new LazyNullMap(entryCount,special));
        long predicted=fixed+(2L*(long)entryCount)+(special?3L:0L);
        check(predicted==target,"INDEPENDENT_KEY_INCLUSIVE_VISIT_ARITHMETIC");
        return body;
    }

    private static long independentVisitCount(Object value) {
        return independentVisitCount(value,new IdentityHashMap<Object,Boolean>());
    }

    private static long independentVisitCount(Object value,
            IdentityHashMap<Object,Boolean> active) {
        long visits=1L;
        if (value instanceof Map) {
            if (active.put(value,Boolean.TRUE)!=null)
                throw new AssertionError("VISIT_ORACLE_CYCLE");
            for (Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                visits++;
                visits+=independentVisitCount(entry.getValue(),active);
            }
            active.remove(value);
        } else if (value instanceof List) {
            if (active.put(value,Boolean.TRUE)!=null)
                throw new AssertionError("VISIT_ORACLE_CYCLE");
            for (Object child:(List<?>)value) visits+=independentVisitCount(child,active);
            active.remove(value);
        }
        return visits;
    }

    private static int independentMaximumDepth(Object value) {
        return independentMaximumDepth(value,0,new IdentityHashMap<Object,Boolean>());
    }

    private static int independentMaximumDepth(Object value,int depth,
            IdentityHashMap<Object,Boolean> active) {
        int maximum=depth;
        if (value instanceof Map) {
            if (active.put(value,Boolean.TRUE)!=null)
                throw new AssertionError("DEPTH_ORACLE_CYCLE");
            for (Map.Entry<?,?> entry:((Map<?,?>)value).entrySet())
                maximum=Math.max(maximum,independentMaximumDepth(entry.getValue(),depth+1,active));
            active.remove(value);
        } else if (value instanceof List) {
            if (active.put(value,Boolean.TRUE)!=null)
                throw new AssertionError("DEPTH_ORACLE_CYCLE");
            for (Object child:(List<?>)value)
                maximum=Math.max(maximum,independentMaximumDepth(child,depth+1,active));
            active.remove(value);
        }
        return maximum;
    }

    private static Object nestedLists(int count) {
        Object value=null;
        for (int i=0;i<count;i++) {
            List<Object> parent=new ArrayList<Object>();
            parent.add(value);
            value=parent;
        }
        return value;
    }

    private static String repeat(char c,int count) {
        StringBuilder result=new StringBuilder(count);
        for (int i=0;i<count;i++) result.append(c);
        return result.toString();
    }

    private static byte[] asciiBytes(String text) {
        if (text==null) throw new AssertionError("ORACLE_TEXT_MISSING");
        for (int i=0;i<text.length();i++)
            if (text.charAt(i)>127) throw new AssertionError("ORACLE_NOT_ASCII");
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean oneFinalLf(byte[] bytes) {
        if (bytes==null || bytes.length==0 || bytes[bytes.length-1]!=(byte)10) return false;
        int count=0;
        for (int i=0;i<bytes.length;i++) {
            if ((bytes[i]&255)==10) {
                count++;
                if (i!=bytes.length-1) return false;
            }
            if ((bytes[i]&255)>127) return false;
        }
        return count==1;
    }

    private static long number(Object owner,String name) throws Exception {
        Object value=CohortTerminalTelemetryStudy.field(owner,name);
        if (!(value instanceof Number)) throw new AssertionError("METER_FIELD_"+name);
        return ((Number)value).longValue();
    }

    private static void check(boolean condition,String code) {
        assertions++;
        if (!condition) throw new AssertionError(code);
    }

    private static final class MeterFixture {
        final long started;
        final CohortTerminalTelemetryStudy.Parent parent;
        final CohortTerminalTelemetryStudy.SetupMeter setup;

        MeterFixture() throws Exception {
            started=System.nanoTime();
            parent=new CohortTerminalTelemetryStudy.Parent();
            setup=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
        }
    }

    private static final class MemorySink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        long position;

        public void beforeDataOperation() { }
        public void writeByte(int ascii) throws Exception {
            if (ascii<0 || ascii>127) throw new IOException("MEMORY_SINK_NON_ASCII");
            output.write(ascii);
            position++;
        }
        byte[] bytes() { return output.toByteArray(); }
    }

    private static final class DiscardSink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        long position;
        public void beforeDataOperation() { }
        public void writeByte(int ascii) throws Exception {
            if (ascii<0 || ascii>127) throw new IOException("DISCARD_SINK_NON_ASCII");
            position++;
        }
    }

    private static final class AdmissionSink
            implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        final CohortTerminalTelemetryStudy.CanonicalJsonSink delegate;
        final AdmissionGuardMap guard;
        AdmissionSink(CohortTerminalTelemetryStudy.CanonicalJsonSink delegate,
                AdmissionGuardMap guard) {
            this.delegate=delegate;
            this.guard=guard;
        }
        public void beforeDataOperation() throws Exception {
            guard.admitted=true;
            delegate.beforeDataOperation();
        }
        public void writeByte(int ascii) throws Exception { delegate.writeByte(ascii); }
    }

    private static final class AdmissionGuardMap extends AbstractMap<String,Object> {
        final Map<String,Object> delegate;
        boolean admitted;
        AdmissionGuardMap(Map<String,Object> delegate) { this.delegate=delegate; }
        void resetAdmission() { admitted=false; }
        private void requireAdmission() {
            if (!admitted) throw new AssertionError("BODY_INSPECTED_BEFORE_SINK_ADMISSION");
        }
        public Set<Map.Entry<String,Object>> entrySet() {
            requireAdmission();
            return delegate.entrySet();
        }
        public Object get(Object key) {
            requireAdmission();
            return delegate.get(key);
        }
        public int size() {
            requireAdmission();
            return delegate.size();
        }
    }

    private static final class SinkPosition {
        private CohortTerminalTelemetryStudy.CanonicalJsonSink sink;
        void bind(CohortTerminalTelemetryStudy.CanonicalJsonSink sink) { this.sink=sink; }
        long get() {
            if (sink instanceof CohortTerminalTelemetryStudy.R5BodySink)
                return ((CohortTerminalTelemetryStudy.R5BodySink)sink).position;
            if (sink instanceof MemorySink) return ((MemorySink)sink).position;
            throw new AssertionError("UNKNOWN_POSITION_SINK");
        }
    }

    private static final class ObservableMap extends AbstractMap<String,Object> {
        final Map<String,Object> delegate;
        final SinkPosition position;
        final long requiredPrefix;
        int descendantReads;
        ObservableMap(Map<String,Object> delegate,SinkPosition position,long requiredPrefix) {
            this.delegate=delegate;
            this.position=position;
            this.requiredPrefix=requiredPrefix;
        }
        private void requirePrefix() {
            if (position.get()<requiredPrefix)
                throw new AssertionError("DESCENDANT_INSPECTED_BEFORE_OUTPUT_PREFIX");
            descendantReads++;
        }
        public Set<Map.Entry<String,Object>> entrySet() {
            requirePrefix();
            return delegate.entrySet();
        }
        public Object get(Object key) {
            requirePrefix();
            return delegate.get(key);
        }
        public int size() {
            requirePrefix();
            return delegate.size();
        }
    }

    private static final class LazyNullMap extends AbstractMap<String,Object> {
        final int ordinaryEntries;
        final boolean specialEntry;
        LazyNullMap(int ordinaryEntries,boolean specialEntry) {
            this.ordinaryEntries=ordinaryEntries;
            this.specialEntry=specialEntry;
        }
        public int size() { return ordinaryEntries+(specialEntry?1:0); }
        public Set<Map.Entry<String,Object>> entrySet() {
            final int length=ordinaryEntries+(specialEntry?1:0);
            return new AbstractSet<Map.Entry<String,Object>>() {
                public int size() { return length; }
                public Iterator<Map.Entry<String,Object>> iterator() {
                    return new Iterator<Map.Entry<String,Object>>() {
                        int index;
                        public boolean hasNext() { return index<length; }
                        public Map.Entry<String,Object> next() {
                            if (!hasNext()) throw new NoSuchElementException();
                            if (index<ordinaryEntries) {
                                String key="k"+Integer.toString(index++);
                                return new AbstractMap.SimpleImmutableEntry<String,Object>(key,null);
                            }
                            index++;
                            return new AbstractMap.SimpleImmutableEntry<String,Object>(
                                "special",Collections.<Object>singletonList(null));
                        }
                        public void remove() { throw new UnsupportedOperationException(); }
                    };
                }
            };
        }
    }

    private static final class LazyNullList extends AbstractList<Object> {
        final int length;
        LazyNullList(int length) { this.length=length; }
        public int size() { return length; }
        public Object get(int index) {
            if (index<0 || index>=length) throw new IndexOutOfBoundsException();
            return null;
        }
    }
}
