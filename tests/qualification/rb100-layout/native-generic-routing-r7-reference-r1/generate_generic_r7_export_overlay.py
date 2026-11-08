"""Derive the source-pinned generic R7 result-file/marker native overlay.

Inputs are authenticated source bytes only. The fixed P generator and exporter
provide checked filesystem I/O; this module is not run during the source handoff.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import types

STUDY_SHA256 = "8581ca447ca5d7fde0c56970daa9ea7fc38a1f2ea97eeb8b911621f3ccedfa14"
DRIVER_SHA256 = "68c78e29148d062969943b6ddf77bba15e3ca352ce03b16000eb9dfcded9e9c0"
WIRE_SHA256 = "4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741"
PLAN_SHA256 = "56fccb76c7170faeb101fce60ebe036e1125b2b05c79ada2425252b64740164f"
BASE_GENERATOR_SHA256 = "d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0"
IO_HELPER_SHA256 = "df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923"
PHYSICAL_OWNER_SHA256 = "7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c"
RUN_OWNER_SHA256 = "4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4"
FIXTURE_SOURCE_SHA256 = "dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619"
FIXTURE_ORACLE_SHA256 = "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a"
MAX_SOURCE_BYTES = 4 * 1024 * 1024
MAX_SMALL_BYTES = 256 * 1024
MAX_WIRE_BYTES = 64 * 1024
MAX_MARKER_BYTES = 666
SCHEMA = "tsj-generic-r7-export-overlay-v1"
OUTPUT_NAMES = (
    "CohortTerminalTelemetryStudy.java",
    "GenericRoutingReferenceCanary.java",
    "SOURCE_PROOF.json",
)
CLI_FLAGS = (
    "--study", "--driver", "--native-support", "--wire",
    "--base-generator", "--io-helper", "--output-dir",
)
SUPPORT_SECTIONS = (
    "GENERIC_ARGUMENTS", "OUTPUT_CLASS", "WORK_VALIDATION", "MARKER_METHODS", "R7_CORE",
)
STUDY_CHANGES = (
    "EXACT_SIX_ARGUMENT_MODE", "GENERIC_ARGUMENT_PARSER", "R5_FINALIZER_BINDING",
    "GENERIC_EMPTY_TO_RESULT_OUTPUT", "R5_SNAPSHOT_NO_ORIGINAL_STATE",
    "GENERIC_WORK_SHAPE_AND_EQUATIONS", "GENERIC_RESULT_OUTPUT_CLASS",
    "GENERIC_FOURTEEN_KEY_MARKER", "GENERIC_R7_METER_LEDGER",
    "R5_OUTPUT_ARGUMENT_TYPE_1", "R5_OUTPUT_ARGUMENT_TYPE_2",
    "R5_OUTPUT_DIRECTORY_ROLE", "R5_FINALIZER_WORK_BINDING",
    "R5_CANARY_COUNT_GEOMETRY_PATH", "R5_CANARY_WRITE_GEOMETRY_PATH",
    "R5_CANARY_READBACK_GEOMETRY_PATH", "R5_EXPORT_WORK_FIELDS",
)
DRIVER_CHANGES = (
    "GENERIC_RESULT_DRIVER", "IMMEDIATE_RUN_METER_BOUNDARIES",
    "R7_MODE_WIRE_IDENTITY",
)


class OverlayError(ValueError):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def need(condition: bool, code: str) -> None:
    if not condition:
        raise OverlayError(code)


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def replace_once(raw: bytes, before: bytes, after: bytes) -> bytes:
    need(type(raw) is bytes and type(before) is bytes and type(after) is bytes,
         "SPLICE_TYPES")
    need(bool(before) and raw.count(before) == 1, "SPLICE_COUNT")
    return raw.replace(before, after, 1)


def replace_region(raw: bytes, start: bytes, end: bytes, replacement: bytes,
                   code: str) -> bytes:
    need(type(raw) is bytes and type(start) is bytes and type(end) is bytes and
         type(replacement) is bytes and start and end, "SPLICE_TYPES")
    need(raw.count(start) == 1, code + "_START_COUNT")
    need(raw.count(end) == 1, code + "_END_COUNT")
    begin = raw.index(start)
    finish = raw.find(end, begin + len(start))
    need(finish >= 0, code + "_END_ORDER")
    return raw[:begin] + replacement + raw[finish:]


def _pinned_lf(raw: bytes, expected: str | None, code: str, limit: int) -> None:
    need(type(raw) is bytes and 0 < len(raw) <= limit, code + "_SIZE")
    need(raw.endswith(b"\n") and b"\r" not in raw, code + "_LINE_ENDINGS")
    try:
        raw.decode("utf-8", errors="strict")
    except UnicodeDecodeError:
        raise OverlayError(code + "_UTF8") from None
    if expected is not None:
        need(sha(raw) == expected, code + "_HASH")


def support_sections(raw: bytes) -> dict[str, bytes]:
    _pinned_lf(raw, None, "NATIVE_SUPPORT", MAX_SOURCE_BYTES)
    result = {}
    for name in SUPPORT_SECTIONS:
        begin = ("/*<" + name + ">*/\n").encode("ascii")
        end = ("/*</" + name + ">*/").encode("ascii")
        need(raw.count(begin) == 1 and raw.count(end) == 1,
             "SUPPORT_SECTION_" + name)
        left = raw.index(begin) + len(begin)
        right = raw.find(end, left)
        need(right > left and raw[right - 1:right] == b"\n",
             "SUPPORT_SECTION_BODY_" + name)
        result[name] = raw[left:right]
    need(sum(raw.count(("/*<" + name + ">*/").encode("ascii"))
             for name in SUPPORT_SECTIONS) == len(SUPPORT_SECTIONS) and
         sum(raw.count(("/*</" + name + ">*/").encode("ascii"))
             for name in SUPPORT_SECTIONS) == len(SUPPORT_SECTIONS),
         "SUPPORT_SECTION_CLOSURE")
    return result


MAIN = b'''    public static void main(String[] args) throws Exception {
        long started=System.nanoTime();
        if (args==null || args.length!=6 ||
                !"GENERIC_ROUTING_R7_REFERENCE_V1".equals(args[0]) ||
                args[1]==null || !UUID.matcher(args[1]).matches() ||
                args[2]==null || !SHA.matcher(args[2]).matches() ||
                !"4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741".equals(args[3]) ||
                !"5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a".equals(args[4]))
            throw new IllegalStateException("GENERIC_ROUTING_R7_REFERENCE_ONLY");
        GenericRoutingReferenceCanary.run(args,started);
    }

'''

FINALIZER = b'''interface R5WorkFinalizer {
    /* Binding rejects stale/foreign work plans before any slot is changed. */
    void verifyBound(SetupMeter meter,R5Snapshot pre,java.util.Map<?,?> body,
            java.util.Map<?,?> work) throws Exception;
    /* Replace only existing numeric slots from frozen scalars; no meter,
     * graph, key insertion, map replacement, or reopened work is permitted. */
    void finalizeWork(long exportStartNanos) throws Exception;
}
'''

EXPORT_WORK = b'''    r5ValidateWork(work,pre,exportStarted);
    java.util.Map<?,?> preExport=(java.util.Map<?,?>)work.get("preExport");
    long bodyPreUnits=r5WorkNumber(preExport,"units");
    long bodyPreMaxNanos=r5WorkNumber(preExport,"maxGroupNanos");
    long bodyPreMaxUnits=r5WorkNumber(preExport,"maximumUnitsPerGroup");
    long bodyCutoff=r5WorkNumber(preExport,"elapsedNanos");
'''

RUN = b'''    public static void run(String[] args,long started) throws Exception {
        // Full mode/identity/output validation precedes Parent and Meter creation.
        CohortTerminalTelemetryStudy.GenericR7Arguments arguments=
            CohortTerminalTelemetryStudy.GenericR7Arguments.parse(args);
        Parent parent=new Parent();
        SetupMeter m=new SetupMeter(parent,parent.meter(started));
        CohortTerminalTelemetryStudy.R7MeterLedger ledger=
            CohortTerminalTelemetryStudy.R7MeterLedger.prepare(m,started);
        Map<String,Object> body=null,references=null,review=null;
        CohortTerminalTelemetryStudy.R7WorkPlan workPlan=null;
        try {
            body=object(m);
            put(m,body,"schema","tsj-generic-routing-r7-result-v1");
            put(m,body,"status","GENERIC_NATURAL_R7_REFERENCE_RECORDED_ONLY");
            put(m,body,"runId",arguments.runId);
            put(m,body,"sourceManifestSha256",arguments.manifestSha);
            put(m,body,"wireContractSha256",arguments.wireSha);
            references=object(m);
            put(m,references,"OPEN",reference(parent,m,false,started,ledger));
            put(m,references,"BLOCKED",reference(parent,m,true,started,ledger));
            put(m,body,"references",references);
            review=object(m);
            put(m,review,"scope","GENERIC_NATURAL_ROUTING_R7_REFERENCE_ONLY");
            put(m,review,"physicalOwnerSha256","7bf4ab57198ff9444381ec5d677c1a2bc309bfa3e7f01b188c631ffe5f2b966c");
            put(m,review,"studyParentSha256","8581ca447ca5d7fde0c56970daa9ea7fc38a1f2ea97eeb8b911621f3ccedfa14");
            put(m,review,"runOwnerSha256","4ed1c8561adf1619a8b7ffbd21e23fdf2544dcaafe541af4e5d2b2b1e95cabe4");
            put(m,review,"fixtureSourceSha256","dd333ff9c448ce012c3c1a4e603be39b70e1ac1d8d7ef79509d12ab21bd17619");
            put(m,review,"fixtureOracleSha256",arguments.fixtureOracleSha);
            long constructors=constructorEntries(m);
            require(m,constructors==2,"GENERIC_R7_CONSTRUCTORS");
            m.charge(1);
            put(m,review,"sessionConstructorEntries",Long.valueOf(constructors));
            put(m,body,"sourceReview",review);
            put(m,body,"work",null);
            references=null; review=null;
            workPlan=CohortTerminalTelemetryStudy.captureR7WorkPlan(ledger,m,started,body);
            CohortTerminalTelemetryStudy.exportR5(arguments,body,m,started,workPlan.pre,workPlan);
        } finally {
            workPlan=null; body=null; references=null; review=null;
            try { m.finish(); } finally { parent=null; m=null; }
        }
    }

'''

REFERENCE = b'''    private static Map<String,Object> reference(Parent p,SetupMeter m,boolean blocked,
            long started,CohortTerminalTelemetryStudy.R7MeterLedger ledger) throws Exception {
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
        Object ownerValue=null,wrapperValue=null,groupsValue=null,copiesValue=null;
        Object chunksValue=null,maxNanosValue=null,maxUnitsValue=null;
        long constructorBefore=-1,constructorAfter=-1,cleanupNanos=-1;
        boolean ownerDropped=false,wrapperDropped=false;
        try {
            m.charge(1);
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
            require(m,constructorBefore==(blocked?1:0),"GENERIC_R7_CONSTRUCTOR_BEFORE");
            /* Before runFresh: Parent call (4), four immediate returned fields
             * (4 x 4), seven FreshRun scalars (7 x 4), and three type checks.
             * Seven scalar slots are copied into the preallocated phase ledger. */
            m.charge(4+11*4+3); m.copy(7);
            m.finish();
            if (blocked) ledger.beforeBlocked(m); else ledger.beforeOpen(m);
            fresh=p.call("runFresh",types,arguments);
            if (blocked) ledger.afterBlocked(m); else ledger.afterOpen(m);
            Object count=CohortTerminalTelemetryStudy.field(m.meter,"sessionConstructorEntries");
            Object json=CohortTerminalTelemetryStudy.field(fresh,"json");
            Object nanos=CohortTerminalTelemetryStudy.field(fresh,"cleanupNanos");
            Object dropped=CohortTerminalTelemetryStudy.field(fresh,"referencesDropped");
            ownerValue=CohortTerminalTelemetryStudy.field(fresh,"ownerUnits");
            wrapperValue=CohortTerminalTelemetryStudy.field(fresh,"wrapperUnits");
            groupsValue=CohortTerminalTelemetryStudy.field(fresh,"groups");
            copiesValue=CohortTerminalTelemetryStudy.field(fresh,"copiedEntries");
            chunksValue=CohortTerminalTelemetryStudy.field(fresh,"copyChunks");
            maxNanosValue=CohortTerminalTelemetryStudy.field(fresh,"maxGroupNanos");
            maxUnitsValue=CohortTerminalTelemetryStudy.field(fresh,"maximumUnitsPerGroup");
            m.charge(3); require(m,count instanceof Integer && nanos instanceof Long && dropped instanceof Boolean,
                "GENERIC_R7_RETURN_TYPES");
            ledger.recordRun(m,fresh,blocked,ownerValue,wrapperValue,groupsValue,
                copiesValue,chunksValue,maxNanosValue,maxUnitsValue);
            m.charge(3);
            constructorAfter=((Integer)count).longValue();
            cleanupNanos=((Long)nanos).longValue(); ownerDropped=((Boolean)dropped).booleanValue();
            m.charge(4); require(m,constructorAfter==constructorBefore+1 && ownerDropped &&
                cleanupNanos>=0 && cleanupNanos<=120000000000L,"GENERIC_R7_OWNER_CLEANUP");
            raw=map(m,json); json=null;
            validateRun(m,raw,blocked);
            validateMeterRunWork(m,raw,ledger,blocked);
            after=geometry(p,m,layout);
            m.charge(1); require(m,before.equals(after),"GENERIC_R7_SOURCE_CHANGED");
            put(m,dto,"caseId",blocked?"BLOCKED":"OPEN");
            put(m,dto,"fixtureContextSha256",ORACLE);
            put(m,dto,"run",raw);
        } finally {
            fixture=null; board=null; layout=null; cohort=null;
            fresh=null; raw=null; types=null; arguments=null;
            ownerValue=null; wrapperValue=null; groupsValue=null; copiesValue=null;
            chunksValue=null; maxNanosValue=null; maxUnitsValue=null;
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

    private static void validateMeterRunWork(SetupMeter m,Map<String,Object> run,
            CohortTerminalTelemetryStudy.R7MeterLedger ledger,boolean blocked) throws Exception {
        Map<String,Object> work=map(m,get(m,run,"work"));
        integer(m,work,"ownerUnits",ledger.metric(blocked,0));
        integer(m,work,"wrapperUnits",ledger.metric(blocked,1));
        integer(m,work,"groups",ledger.metric(blocked,2));
        integer(m,work,"copyEntries",ledger.metric(blocked,3));
        integer(m,work,"copyChunks",ledger.metric(blocked,4));
        integer(m,work,"maxGroupNanos",ledger.metric(blocked,5));
        integer(m,work,"maximumUnitsPerGroup",ledger.metric(blocked,6));
    }

'''


def derive(study: bytes, driver: bytes, support: bytes, wire: bytes):
    _pinned_lf(study, STUDY_SHA256, "STUDY", MAX_SOURCE_BYTES)
    _pinned_lf(driver, DRIVER_SHA256, "DRIVER", MAX_SOURCE_BYTES)
    _pinned_lf(wire, WIRE_SHA256, "WIRE", MAX_WIRE_BYTES)
    sections = support_sections(support)
    s, d = study, driver
    changes = []

    def study_once(name, before, after):
        nonlocal s
        s = replace_once(s, before, after)
        changes.append(name)

    def study_region(name, start, end, replacement):
        nonlocal s
        s = replace_region(s, start, end, replacement, name)
        changes.append(name)

    study_region("EXACT_SIX_ARGUMENT_MODE",
                 b"    public static void main(String[] args) throws Exception {\n",
                 b"    static final class Arguments {\n", MAIN)
    study_once("GENERIC_ARGUMENT_PARSER", b"    static final class Arguments {\n",
               sections["GENERIC_ARGUMENTS"] + b"    static final class Arguments {\n")
    study_region("R5_FINALIZER_BINDING", b"interface R5WorkFinalizer {\n",
                 b"interface R5AfterResultWrite {\n", FINALIZER)
    study_once("GENERIC_EMPTY_TO_RESULT_OUTPUT",
               b"static final int R5_BLOCK=4096, R5_RESULT_LIMIT=1048576, R5_MARKER_BOUND=722;\n",
               b"static final int R5_BLOCK=4096, R5_RESULT_LIMIT=1048576, R5_MARKER_BOUND=666;\n")
    study_once("R5_SNAPSHOT_NO_ORIGINAL_STATE",
               b'''            maxNanos>=0 && maxNanos<=R5_EXPORT_NANOS && maxUnits>=0 && maxUnits<=32768 &&
            groupUnits>=0 && groupUnits<=32768 && constructorEntries>=0 && ownerStarted && ownerAdopted,
''',
               b'''            maxNanos>=0 && maxNanos<=R5_EXPORT_NANOS && maxUnits>=0 && maxUnits<=32768 &&
            groupUnits>=0 && groupUnits<=32768 && constructorEntries>=0 && prior==0 &&
            !ownerStarted && !ownerAdopted,
''')
    study_region("GENERIC_WORK_SHAPE_AND_EQUATIONS",
                 b"/* Fixed order, without an application-owned key array or copied map. */\n",
                 b"/* Java 7 NOFOLLOW basic attributes, canonical paths, and stable file keys.\n",
                 sections["WORK_VALIDATION"])
    study_region("GENERIC_RESULT_OUTPUT_CLASS", b"static final class R5Output {\n",
                 b"/* A credit is installed before the formatter inspects data in the block.\n",
                 sections["OUTPUT_CLASS"])
    study_region("GENERIC_FOURTEEN_KEY_MARKER",
                 b"static void r5Marker(R5MarkerSink sink,Arguments args,byte[] digest,long resultBytes,\n",
                 b"/* This token carries lifecycle, not a second budget. It can be constructed\n",
                 sections["MARKER_METHODS"])
    study_region("GENERIC_R7_METER_LEDGER",
                 b"/* Nested Java 7 members for CohortTerminalTelemetryStudy. Source-only HOLD.\n * Captures a primitive ledger before metadata close; no source owner survives. */\n",
                 b"/* Nested Java7 members. Prospective R9 transport only; not R5 publication.\n",
                 sections["R7_CORE"])
    study_once("R5_OUTPUT_ARGUMENT_TYPE_1",
               b"static R5ExportMetrics exportR5(Arguments args,java.util.Map<String,Object> result,\n"
               b"        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer) throws Exception {\n",
               b"static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,\n"
               b"        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer) throws Exception {\n")
    study_once("R5_OUTPUT_ARGUMENT_TYPE_2",
               b"static R5ExportMetrics exportR5(Arguments args,java.util.Map<String,Object> result,\n"
               b"        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,\n",
               b"static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,\n"
               b"        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,\n")
    study_once("R5_OUTPUT_DIRECTORY_ROLE", b"R5Output output=new R5Output(args.data);\n",
               b"R5Output output=new R5Output(args.outputDirectory);\n")
    study_once("R5_FINALIZER_WORK_BINDING",
               b"    r5WorkShape(work);\n    finalizer.finalizeWork(exportStarted);\n",
               b"    r5WorkShape(work);\n    finalizer.verifyBound(meter,pre,result,work);\n    finalizer.finalizeWork(exportStarted);\n")
    study_once("R5_CANARY_COUNT_GEOMETRY_PATH",
               b"    writeCanonicalJson(result,count); // A: virtual bytes, no encoded body\n",
               b"    writeCanonicalJsonCanaryValues(result,count); // A: typed geometry path preflight\n")
    study_once("R5_CANARY_WRITE_GEOMETRY_PATH",
               b"        writeCanonicalJson(result,writer); // B/C interleaved\n",
               b"        writeCanonicalJsonCanaryValues(result,writer); // B/C same typed path on write\n")
    study_once("R5_CANARY_READBACK_GEOMETRY_PATH",
               b"        writeCanonicalJson(result,reader); // D/E interleaved; expected bytes virtual\n",
               b"        writeCanonicalJsonCanaryValues(result,reader); // D/E same typed path on readback\n")
    study_once("R5_EXPORT_WORK_FIELDS",
               b"    r5ValidateWork(work,pre,exportStarted);\n"
               b"    long bodyPreUnits=r5WorkNumber(work,\"unitsBeforeExport\");\n"
               b"    long bodyPreMaxNanos=r5WorkNumber(work,\"maxGroupNanosBeforeExport\");\n"
               b"    long bodyPreMaxUnits=r5WorkNumber(work,\"maximumUnitsPerGroupBeforeExport\");\n"
               b"    long bodyCutoff=r5WorkNumber(work,\"elapsedCutoffNanos\");\n",
               EXPORT_WORK)

    driver_changes = []
    d = replace_region(d,
        b"    public static void run(String[] args,long started) throws Exception {\n",
        b"    private static boolean lowerHex(String s,int length,boolean uuid) {\n",
        RUN, "GENERIC_RESULT_DRIVER")
    driver_changes.append("GENERIC_RESULT_DRIVER")
    d = replace_region(d,
        b"    private static Map<String,Object> reference(Parent p,SetupMeter m,boolean blocked,\n",
        b"    private static String geometry(Parent p,SetupMeter m,PcbBoardLayout layout) throws Exception {\n",
        REFERENCE, "IMMEDIATE_RUN_METER_BOUNDARIES")
    driver_changes.append("IMMEDIATE_RUN_METER_BOUNDARIES")
    d = replace_once(d,
        b"    private static final String WIRE=\"dd738f0bbac0edc4f4cbaf4a0d2bba80ebeda9a581d79c052e29df8976e73dd6\";\n",
        b"    private static final String WIRE=\"4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741\";\n")
    driver_changes.append("R7_MODE_WIRE_IDENTITY")

    need(tuple(changes) == STUDY_CHANGES and tuple(driver_changes) == DRIVER_CHANGES,
         "SOURCE_CHANGE_CLOSURE")
    need(len(s) <= MAX_SOURCE_BYTES and len(d) <= MAX_SOURCE_BYTES, "DERIVED_SOURCE_SIZE")
    need(s.count(b"writeCanonicalJsonCanaryValues(result,") == 3,
         "GENERIC_EXPORT_GEOMETRY_PASSES")
    need(b"R5_MARKER_BOUND=666" in s and b"R5_MARKER_BOUND=722" not in s,
         "GENERIC_MARKER_BOUND")
    need(b"originalCaptureOwnerUnits" not in s and b"R7_WORK_SLOTS=34" not in s,
         "AUTHORED_WORK_PROFILE_REMAINS")
    need(b"captureR7WorkPlan(ledger,m,started,body)" in d and
         b"OriginalArm" not in sections["R7_CORE"] and
         b"captureData" not in sections["R7_CORE"] and
         b"descriptor" not in sections["R7_CORE"].lower(),
         "GENERIC_R7_NO_ORIGINAL")
    proof = {
        "schema": SCHEMA,
        "status": "GENERATED_SOURCE_ONLY_NOT_COMPILED",
        "planSha256": PLAN_SHA256,
        "wireContractSha256": WIRE_SHA256,
        "studyParentSha256": sha(study),
        "driverParentSha256": sha(driver),
        "nativeSupportSha256": sha(support),
        "wireSourceSha256": sha(wire),
        "baseGeneratorSha256": BASE_GENERATOR_SHA256,
        "ioHelperSha256": IO_HELPER_SHA256,
        "derivedStudySha256": sha(s),
        "derivedStudyBytes": len(s),
        "derivedDriverSha256": sha(d),
        "derivedDriverBytes": len(d),
        "markerMaximumBytes": MAX_MARKER_BYTES,
        "markerMaximumEquation": "quotedKeys207+punctuationAndLf30+fixedQuotedValues375+decimalMaxima54=666",
        "phaseBoundaryReflectedFields": 4 * 18,
        "phaseBoundaryListSizeReads": 4,
        "phaseBoundaryBoundedGuardsPerPoint": 3,
        "phaseBoundaryReadUnitsPerPoint": 18 * 4 + 1 + 3,
        "phaseBoundaryPrepaymentUnits": 4 * (18 * 4 + 1 + 3),
        "phaseLedgerObjectAllocations": 5,
        "phasePointStoredSlots": 4 * (19 + 3) + 21,
        "phasePointPrepaidCopies": 4 * (19 + 3) + 21,
        "freshRunParentCallPrepayment": 4,
        "freshRunReturnedAndScalarFields": 11,
        "freshRunReadPrepaymentPerRun": 7 * 4,
        "freshRunTotalPrepaymentPerRun": 4 + 11 * 4 + 3,
        "freshRunLedgerCopiesPerRun": 7,
        "recordRunControlPrepaymentPerRun": 16,
        "closedPreExportReads": 19,
        "r5SnapshotAllocationUnits": 1,
        "workTreeEntries": 36,
        "workBodySlotReplacementCopies": 1,
        "workPlanArgumentGuardUnits": 1,
        "workSlotInspectionUnits": 2,
        "workMapAllocations": 7,
        "workMapAllocationUnits": 7,
        "workPlanReferenceAndStateCopies": 14,
        "workLimitWrapperCopies": 6,
        "workTreeEntryCopies": 37,
        "workPhaseNumericSlotWrites": 23,
        "phaseFreezeSubtractionAndGuardUnits": 22,
        "finalizerElapsedSlotWrites": 1,
        "frozenAndFinalizerLongWrapperCopies": 24,
        "frozenAndFinalizerSlotWritePrepaymentUnits": 24,
        "phaseFreezeControlPrepaymentUnits": 22,
        "finalizerBindingReadAndGuardPrepaymentUnits": 8,
        "finalizerExistingNumericSlotWrites": 1,
        "geometryPath": "references.{OPEN,BLOCKED}.run.layout.components[].geometryCanonical",
        "geometryPathLimitBytes": 4096,
        "ordinaryStringLimitBytes": 256,
        "outputs": list(OUTPUT_NAMES),
        "studyChanges": list(changes),
        "driverChanges": driver_changes,
        "nativeSupportSections": {
            name: {"sha256": sha(sections[name]), "bytes": len(sections[name])}
            for name in SUPPORT_SECTIONS
        },
        "observedOriginalFlagsExpected": [False, False, False],
        "observedPriorAndInflightGroupsExpected": 0,
        "actualSessionConstructorEntriesExpected": 2,
        "authoredInvocations": 0,
        "fullD": False,
        "R7": False,
        "nativeExecution": "NOT_RUN",
        "compile": "NOT_RUN",
    }
    return s, d, proof


def _raw_canonical_path(value: str, code: str) -> Path:
    need(type(value) is str and value and "\x00" not in value, code + "_VALUE")
    if os.name == "nt":
        need("/" not in value and not value.startswith(("\\\\?\\", "\\\\.\\")),
             code + "_WINDOWS_ALIAS")
        drive = re.match(r"^[A-Za-z]:\\", value)
        unc = value.startswith("\\\\")
        need(drive is not None or unc, code + "_ABSOLUTE")
        pieces = value[2:].split("\\") if unc else value[3:].split("\\")
        if unc:
            need(len(pieces) >= 2, code + "_ALIAS")
        need(all(piece not in ("", ".", "..") and ":" not in piece
                 and not piece.endswith((" ", ".")) for piece in pieces), code + "_ALIAS")
    else:
        need(value.startswith("/") and not value.startswith("//"), code + "_ABSOLUTE")
        pieces = value.split("/")[1:]
        need(all(piece not in ("", ".", "..") for piece in pieces), code + "_ALIAS")
    try:
        path = Path(value)
    except (TypeError, ValueError, OSError):
        raise OverlayError(code + "_PATH") from None
    need(path.is_absolute() and str(path) == value, code + "_CANONICAL")
    return path


def _is_reparse(info) -> bool:
    return stat.S_ISLNK(info.st_mode) or bool(
        getattr(info, "st_file_attributes", 0) & 0x400)


def _identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_ctime_ns", None),
            getattr(info, "st_file_attributes", 0))


def _handle_identity(info):
    return (info.st_dev, info.st_ino, info.st_mode, info.st_size,
            getattr(info, "st_mtime_ns", None), getattr(info, "st_file_attributes", 0))


def _directory_identity(path: Path):
    try:
        info = path.lstat()
    except OSError:
        raise OverlayError("OUTPUT_DIRECTORY_IDENTITY") from None
    need(stat.S_ISDIR(info.st_mode) and not _is_reparse(info) and info.st_ino != 0,
         "OUTPUT_DIRECTORY_IDENTITY")
    return (info.st_dev, info.st_ino, info.st_mode,
            getattr(info, "st_file_attributes", 0))


def _read_pinned_small(path_value: str, expected: str, code: str):
    path = _raw_canonical_path(path_value, code)
    descriptor = None
    try:
        resolved = path.resolve(strict=True)
        need(str(resolved) == str(path), code + "_CANONICAL")
        for item in (path, *path.parents):
            need(not _is_reparse(item.lstat()), code + "_REPARSE")
        before = path.lstat()
        need(stat.S_ISREG(before.st_mode) and not _is_reparse(before)
             and 0 < before.st_size <= MAX_SMALL_BYTES, code + "_FILE")
        flags = os.O_RDONLY | getattr(os, "O_BINARY", 0) | getattr(os, "O_NOFOLLOW", 0)
        descriptor = os.open(str(path), flags)
        opened = os.fstat(descriptor)
        need(stat.S_ISREG(opened.st_mode) and not _is_reparse(opened)
             and _handle_identity(opened) == _handle_identity(before), code + "_IDENTITY")
        with os.fdopen(descriptor, "rb") as stream:
            descriptor = None
            raw = stream.read(MAX_SMALL_BYTES + 1)
        after = path.lstat()
    except OverlayError:
        raise
    except Exception:
        raise OverlayError(code + "_READ") from None
    finally:
        if descriptor is not None:
            os.close(descriptor)
    need(stat.S_ISREG(after.st_mode) and not _is_reparse(after)
         and _identity(before) == _identity(after), code + "_CHANGED")
    need(0 < len(raw) <= MAX_SMALL_BYTES and len(raw) == before.st_size
         and sha(raw) == expected, code + "_HASH")
    return raw, _identity(before)


def _read_input(io_module, path_value: str, expected: str | None,
                limit: int, code: str):
    path = _raw_canonical_path(path_value, code)
    try:
        need(str(path.resolve(strict=True)) == str(path), code + "_CANONICAL")
        for item in (path, *path.parents):
            need(not _is_reparse(item.lstat()), code + "_REPARSE")
        before = path.lstat()
        need(stat.S_ISREG(before.st_mode) and not _is_reparse(before)
             and 0 < before.st_size <= limit, code + "_FILE")
        raw = io_module._read_regular(path, limit, code)
        after = path.lstat()
    except OverlayError:
        raise
    except Exception:
        raise OverlayError(code + "_READ") from None
    need(stat.S_ISREG(after.st_mode) and not _is_reparse(after)
         and _identity(before) == _identity(after), code + "_CHANGED")
    need(type(raw) is bytes and 0 < len(raw) <= limit and len(raw) == before.st_size,
         code + "_SIZE")
    _pinned_lf(raw, expected, code, limit)
    return raw, _identity(before)


def load_base_generator(path_value: str):
    raw, identity = _read_pinned_small(path_value, BASE_GENERATOR_SHA256, "BASE_GENERATOR")
    module = types.ModuleType("_pinned_generic_r7_export_base")
    module.__file__ = "<pinned-generic-r7-export-base>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise OverlayError("BASE_GENERATOR_LOAD") from None
    need(getattr(module, "IO_HELPER_SHA256", None) == IO_HELPER_SHA256,
         "BASE_IO_HELPER_PIN")
    module._checked_source_raw = raw
    module._checked_source_identity = identity
    return module


def load_io_helper(raw: bytes):
    """Load only the helper bytes already read and pinned by this generator."""
    _pinned_lf(raw, IO_HELPER_SHA256, "IO_HELPER", MAX_SMALL_BYTES)
    module = types.ModuleType("_pinned_generic_r7_export_io")
    module.__file__ = "<pinned-generic-r7-export-io>"
    try:
        exec(compile(raw, module.__file__, "exec"), module.__dict__)
    except Exception:
        raise OverlayError("IO_HELPER_LOAD") from None
    module._checked_source_raw = raw
    module._checked_source_sha256 = sha(raw)
    return module


def parse_args(argv):
    need(type(argv) in (list, tuple) and len(argv) == 2 * len(CLI_FLAGS),
         "CLI_ARGUMENTS")
    values = {}
    for at in range(0, len(argv), 2):
        flag, value = argv[at:at + 2]
        need(type(flag) is str and flag in CLI_FLAGS and flag not in values and
             type(value) is str and value and "\x00" not in value and
             not value.startswith("--"), "CLI_ARGUMENTS")
        _raw_canonical_path(value, "CLI_PATH")
        values[flag] = value
    need(set(values) == set(CLI_FLAGS), "CLI_ARGUMENTS")
    return values


def generate(options):
    need(type(options) is dict and set(options) == set(CLI_FLAGS), "GENERATOR_OPTIONS")
    base = load_base_generator(options["--base-generator"])
    base_raw = base._checked_source_raw
    base_identity = base._checked_source_identity
    need(callable(getattr(base, "load_io", None)) and
         getattr(base, "IO_HELPER_SHA256", None) == IO_HELPER_SHA256,
         "BASE_IO_HELPER_API")
    helper_raw, helper_identity = _read_pinned_small(
        options["--io-helper"], IO_HELPER_SHA256, "IO_HELPER")
    io_module = load_io_helper(helper_raw)
    loaded_base, loaded_base_identity = _read_pinned_small(
        options["--base-generator"], BASE_GENERATOR_SHA256, "BASE_GENERATOR")
    loaded_helper, loaded_helper_identity = _read_pinned_small(
        options["--io-helper"], IO_HELPER_SHA256, "IO_HELPER")
    need(loaded_base == base_raw and loaded_base_identity == base_identity and
         loaded_helper == helper_raw and loaded_helper_identity == helper_identity and
         io_module._checked_source_raw == helper_raw and
         io_module._checked_source_sha256 == IO_HELPER_SHA256,
         "BOOTSTRAP_CHANGED")
    need(callable(getattr(io_module, "_checked_directory", None)) and
         callable(getattr(io_module, "_directory_names", None)) and
         callable(getattr(io_module, "_read_regular", None)) and
         callable(getattr(io_module, "_write_exclusive", None)), "IO_HELPER_API")
    output = io_module._checked_directory(options["--output-dir"], "OUTPUT_DIRECTORY")
    need(str(output) == options["--output-dir"] and
         str(output.resolve(strict=True)) == options["--output-dir"],
         "OUTPUT_DIRECTORY_CANONICAL")
    output_identity = _directory_identity(output)
    need(not io_module._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")

    def checked_output():
        current = io_module._checked_directory(
            options["--output-dir"], "OUTPUT_DIRECTORY")
        need(str(current) == options["--output-dir"] and
             str(current.resolve(strict=True)) == options["--output-dir"] and
             _directory_identity(current) == output_identity,
             "OUTPUT_DIRECTORY_CHANGED")
        return current
    input_specs = (
        ("--study", STUDY_SHA256, MAX_SOURCE_BYTES, "STUDY"),
        ("--driver", DRIVER_SHA256, MAX_SOURCE_BYTES, "DRIVER"),
        ("--native-support", None, MAX_SOURCE_BYTES, "NATIVE_SUPPORT"),
        ("--wire", WIRE_SHA256, MAX_WIRE_BYTES, "WIRE"),
    )
    originals, identities = {}, {}
    for key, expected, limit, code in input_specs:
        originals[key], identities[key] = _read_input(
            io_module, options[key], expected, limit, code)
    study, driver, proof = derive(
        originals["--study"], originals["--driver"],
        originals["--native-support"], originals["--wire"])
    proof_raw = (json.dumps(proof, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode("ascii")
    files = {
        "CohortTerminalTelemetryStudy.java": study,
        "GenericRoutingReferenceCanary.java": driver,
        "SOURCE_PROOF.json": proof_raw,
    }

    def recheck_inputs():
        current_base, current_base_identity = _read_pinned_small(
            options["--base-generator"], BASE_GENERATOR_SHA256, "BASE_GENERATOR")
        current_helper, current_helper_identity = _read_pinned_small(
            options["--io-helper"], IO_HELPER_SHA256, "IO_HELPER")
        need(current_base == base_raw and current_base_identity == base_identity and
             current_helper == helper_raw and current_helper_identity == helper_identity,
             "BOOTSTRAP_CHANGED")
        for key, expected, limit, code in input_specs:
            current, identity = _read_input(io_module, options[key], expected, limit, code)
            need(current == originals[key] and identity == identities[key], "INPUT_CHANGED")

    recheck_inputs()
    output = checked_output()
    need(not io_module._directory_names(output, "OUTPUT_DIRECTORY"), "OUTPUT_NOT_EMPTY")
    for name, raw in files.items():
        output = checked_output()
        io_module._write_exclusive(output / name, raw, MAX_SOURCE_BYTES, "OUTPUT_FILE")
    output = checked_output()
    names = io_module._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == len(files) and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        output = checked_output()
        need(io_module._read_regular(output / name, MAX_SOURCE_BYTES, "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    recheck_inputs()
    output = checked_output()
    names = io_module._directory_names(output, "OUTPUT_DIRECTORY")
    need(len(names) == len(files) and set(names) == set(files), "OUTPUT_CLOSURE")
    for name, raw in files.items():
        output = checked_output()
        need(io_module._read_regular(output / name, MAX_SOURCE_BYTES, "OUTPUT_READBACK") == raw,
             "OUTPUT_READBACK_CHANGED")
    return proof


def main(argv=None):
    if not (sys.flags.isolated and sys.dont_write_bytecode):
        sys.stderr.write("GENERIC_R7_EXPORT_OVERLAY_FAIL PYTHON_ISOLATION\n")
        return 2
    try:
        proof = generate(parse_args(sys.argv[1:] if argv is None else argv))
    except Exception as failure:
        code = getattr(failure, "code", "INTERNAL_ERROR")
        if type(code) is not str or not code or any(
                char not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789" for char in code):
            code = "INTERNAL_ERROR"
        sys.stderr.write("GENERIC_R7_EXPORT_OVERLAY_FAIL " + code + "\n")
        return 2
    summary = {"schema": SCHEMA, "status": proof["status"],
               "derivedStudySha256": proof["derivedStudySha256"],
               "derivedDriverSha256": proof["derivedDriverSha256"], "files": 3}
    sys.stdout.write(json.dumps(summary, sort_keys=True, separators=(",", ":")) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
