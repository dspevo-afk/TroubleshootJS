package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Selected-source synthetic file/export canary; not a normal host result. */
public final class CurrentV2NativeExportCanary {
    private static final String WIRE_SHA=
        "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed";
    private static final String RUN_ID="00000000-0000-4000-8000-000000000001";
    private static final String SOURCE_SHA=repeat('a',64);
    private static final String CONTEXT_SHA=repeat('c',64);
    private static final String RESULT_SCHEMA="tsj-cohort-terminal-result-v2";
    private static final String RESULT_MODE="OBSERVED_COHORT_TERMINAL_TELEMETRY";
    private static final String RESULT_STATUS="COMPLETED_DIAGNOSTIC_ONLY";
    private static final String MARKER_SCHEMA="tsj-cohort-terminal-marker-v2";
    private static final String MARKER_STATUS="COMPLETED_DIAGNOSTIC";
    private static final int RESULT_LIMIT=1048576;
    private static final int MARKER_LIMIT=656;
    private static final long OPERATION_LIMIT_NANOS=90000000000L;
    private static final long EXPORT_LIMIT_NANOS=5000000000L;
    private static final String REPORT_SCHEMA="synthetic-native-v2-export-audit-r1";
    private static final String[] BODY_KEYS={
        "schema","mode","status","runId","sourceManifestSha256",
        "wireContractSha256","inputContextSha256","qualifiedPrior",
        "originalCapture","cohort","arms","work","cleanup"
    };
    private static final String[] WORK_KEYS={
        "elapsedCutoffNanos","cleanupNanos","groupsBeforeExport",
        "wrapperGroupsBeforeExport","maxGroupNanosBeforeExport",
        "maximumUnitsPerGroupBeforeExport","unitsBeforeExport","setupUnits",
        "setupGroups","setupCopiedEntries","setupCopyChunks",
        "originalCaptureOwnerUnits","originalCaptureWrapperUnits",
        "originalCaptureCallbackUnits","originalCaptureDiagnosticUnits",
        "originalCaptureGroups","originalCaptureCopiedEntries",
        "originalCaptureCopyChunks","nullOwnerUnits","nullWrapperUnits",
        "nullObserverUnits","observeOwnerUnits","observeWrapperUnits",
        "observeObserverUnits","observerUnits","meterUnits","metadataUnits",
        "metadataGroups","metadataCopiedEntries","metadataCopyChunks",
        "copiedEntries","copyChunks","copyChunkLimit","copiedEntryLimit"
    };
    private static final String[] PRIOR_CHILD_KEYS={"fixtureState"};
    private static final String[] CAPTURE_CHILD_KEYS={"source"};
    private static final String[] COHORT_SMALL_KEYS={"fixtureLabel","syntheticOnly"};
    private static final String[] COHORT_WIDE_KEYS={
        "fixtureLabel","syntheticOnly","boundedAsciiPadding"
    };
    private static final String[] ARM_KEYS={"null","observe"};
    private static final String[] CLEANUP_CHILD_KEYS={"fixtureState"};
    private static int assertions;
    private static int cases;

    private CurrentV2NativeExportCanary() { }

    public static void main(String[] args) throws Exception {
        if (args==null || args.length!=2 ||
                !("--smoke".equals(args[0]) || "--suite".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");
        String mode="--smoke".equals(args[0])?"SMOKE":"SUITE";
        Path root=validateEmptyRoot(args[1]);
        List<PositiveRecord> positives=new ArrayList<PositiveRecord>();
        List<FailureArtifact> failureArtifacts=new ArrayList<FailureArtifact>();

        positives.add(runPositive(root,"positive-small",false));
        testReceiptContentMutation(root,"receipt-content-mutation");
        if ("SUITE".equals(mode)) {
            positives.add(runPositive(root,"positive-two-block",true));
            testIdentityMismatch(root);
            testStaleSnapshot(root);
            testForeignFinalizer(root);
            testWrapperGroupMismatch(root);
            testBodyMutation(root);
            testResultFileMutation(root);
            testExtraOutputFile(root);
            testForeignReceiptPath(root);
            testConsumedReceiptTicket(root);
            testMarkerOutputFailure(root);
            testPostPublicationFatalGuard(root,failureArtifacts);
        }

        persistPositiveAudit(root,mode,positives,failureArtifacts);
        System.out.println("SYNTHETIC_NATIVE_V2_EXPORT_CANARY_ONLY mode="+mode+
            " cases="+cases+" assertions="+assertions+"; normalHostAcceptance=NONE");
    }

    private static Path validateEmptyRoot(String raw) throws Exception {
        if (raw==null || raw.length()==0) throw new IllegalArgumentException("CANARY_ROOT");
        Path root=Paths.get(raw);
        if (!root.isAbsolute() || !root.normalize().equals(root) ||
                !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) ||
                !root.toRealPath().equals(root) || Files.isSymbolicLink(root))
            throw new IllegalArgumentException("CANARY_ROOT_IDENTITY");
        for (Path part=root;part!=null;part=part.getParent())
            if (Files.isSymbolicLink(part)) throw new IllegalArgumentException("CANARY_ROOT_REPARSE");
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(root)) {
            if (entries.iterator().hasNext()) throw new IllegalArgumentException("CANARY_ROOT_NOT_EMPTY");
        }
        return root;
    }

    private static PositiveRecord runPositive(Path root,String label,boolean wide)
            throws Exception {
        ExportFixture fixture=new ExportFixture(root,label,wide,false);
        MeterState before=MeterState.capture(fixture.meter.meter);
        Capture captured=invoke(fixture,fixture.plan,null,RUN_ID,SOURCE_SHA,
            new ByteArrayOutputStream());
        check(captured.failure==null,"POSITIVE_EXPORT_COMPLETES_"+label+"_"+
            failureDescription(captured.failure));
        check(!captured.streamError,"POSITIVE_MARKER_STREAM_HEALTHY_"+label);
        check(captured.metrics!=null,"POSITIVE_METRICS_PRESENT_"+label);
        byte[] marker=captured.bytes;
        byte[] result=Files.readAllBytes(fixture.outputDirectory.resolve("result.json"));
        byte[] independentlyFormatted=independentJsonLine(fixture.result,wide);
        check(Arrays.equals(result,independentlyFormatted),
            "POSITIVE_RESULT_INDEPENDENT_FORMAT_"+label);
        check(isSingleAsciiLine(result),"POSITIVE_RESULT_ONE_FINAL_LF_"+label);
        check(isSingleAsciiLine(marker),"POSITIVE_MARKER_ONE_FINAL_LF_"+label);
        check(Arrays.equals(marker,independentMarker(captured.metrics,result)),
            "POSITIVE_MARKER_INDEPENDENT_FORMAT_"+label);
        check(Files.size(fixture.operation)==fixture.receipt.length,
            "POSITIVE_RECEIPT_SIZE_PRESERVED_"+label);
        check(Arrays.equals(Files.readAllBytes(fixture.operation),fixture.receipt),
            "POSITIVE_RECEIPT_CONTENT_PRESERVED_"+label);
        checkExactOutputClosure(fixture.outputDirectory,label);
        MeterState after=MeterState.capture(fixture.meter.meter);
        check(after.open==false && after.groupUnits==captured.metrics.exportUnits,
            "POSITIVE_EXPORT_GROUP_CLOSED_"+label);
        checkMetrics(captured.metrics,before,after,result.length,marker.length,
            wide?2L:1L,fixture.result,label);
        cases++;
        return new PositiveRecord(label,fixture.outputRelative,
            "audit/"+label+".marker.txt",fixture.receipt.length,
            sha256(fixture.receipt),marker,before,after,captured.metrics);
    }

    private static void testReceiptContentMutation(Path root,String label) throws Exception {
        final ExportFixture fixture=new ExportFixture(root,label,false,false);
        final String expected="FINAL_SHA_MISMATCH";
        final CohortTerminalTelemetryStudy.R5AfterResultWrite hook=
            new CohortTerminalTelemetryStudy.R5AfterResultWrite() {
            public void afterResultWrite(Path resultFile) throws Exception {
                mutateSameSizeAndRestoreTime(fixture.operation,
                    asciiBytes("receipt-A"),asciiBytes("receipt-B"));
            }
        };
        expectExportFailure(label,fixture,fixture.plan,hook,RUN_ID,SOURCE_SHA,expected);
        cases++;
    }

    private static void testIdentityMismatch(Path root) throws Exception {
        ExportFixture fixture=new ExportFixture(root,"identity-mismatch",false,false);
        expectExportFailure("identity-mismatch",fixture,fixture.plan,null,
            "00000000-0000-4000-8000-000000000002",SOURCE_SHA,
            "R5_HEADER_IDENTITY");
        cases++;
    }

    private static void testForeignFinalizer(Path root) throws Exception {
        ExportFixture target=new ExportFixture(root,"foreign-finalizer-target",false,false);
        ExportFixture foreign=new ExportFixture(root,"foreign-finalizer-source",false,false);
        expectExportFailure("foreign-finalizer",target,foreign.plan,null,RUN_ID,SOURCE_SHA,
            "R7_FINALIZER_BINDING");
        cases++;
    }

    private static void testStaleSnapshot(Path root) throws Exception {
        ExportFixture fixture=new ExportFixture(root,"stale-snapshot",false,false);
        fixture.meter.charge(1L); // Deliberately stale the closed synthetic snapshot.
        fixture.meter.finish();
        MeterState beforeAttempt=MeterState.capture(fixture.meter.meter);
        Capture outcome=invoke(fixture,fixture.plan,null,RUN_ID,SOURCE_SHA,
            new ByteArrayOutputStream());
        MeterState afterAttempt=MeterState.capture(fixture.meter.meter);
        check(outcome.failure!=null && "R5_METER_CHANGED".equals(exactCode(outcome.failure)),
            "STALE_SNAPSHOT_REJECTED_BEFORE_EXPORT");
        check(outcome.metrics==null && outcome.bytes.length==0,
            "STALE_SNAPSHOT_NO_SUCCESS_OR_MARKER");
        check(beforeAttempt.same(afterAttempt),"STALE_SNAPSHOT_ATTEMPT_COUNTERS_UNCHANGED");
        checkOnlyOperation(fixture.outputDirectory,"STALE_SNAPSHOT_NO_RESULT_FILE");
        cases++;
    }

    private static void testWrapperGroupMismatch(Path root) throws Exception {
        final ExportFixture fixture=new ExportFixture(root,"wrapper-group-mismatch",false,false);
        CohortTerminalTelemetryStudy.R5WorkFinalizer adversarial=
            new CohortTerminalTelemetryStudy.R5WorkFinalizer() {
                public void verifyBound(CohortTerminalTelemetryStudy.SetupMeter meter,
                        CohortTerminalTelemetryStudy.R5Snapshot pre,Map<?,?> body,
                        Map<?,?> work) throws Exception {
                    fixture.plan.verifyBound(meter,pre,body,work);
                }
                @SuppressWarnings("unchecked")
                public void finalizeWork(long exportStartNanos) throws Exception {
                    fixture.plan.finalizeWork(exportStartNanos);
                    Map<String,Object> work=(Map<String,Object>)fixture.result.get("work");
                    long current=((Number)work.get("wrapperGroupsBeforeExport")).longValue();
                    work.put("wrapperGroupsBeforeExport",Long.valueOf(current+1L));
                }
            };
        expectExportFailure("wrapper-group-mismatch",fixture,adversarial,null,
            RUN_ID,SOURCE_SHA,"R5_WORK_PRE_NATIVE_MISMATCH");
        cases++;
    }

    private static void testBodyMutation(Path root) throws Exception {
        final ExportFixture fixture=new ExportFixture(root,"body-mutation",false,false);
        final CohortTerminalTelemetryStudy.R5AfterResultWrite hook=
            new CohortTerminalTelemetryStudy.R5AfterResultWrite() {
            @SuppressWarnings("unchecked")
            public void afterResultWrite(Path resultFile) {
                Map<String,Object> cohort=(Map<String,Object>)fixture.result.get("cohort");
                cohort.put("fixtureLabel","synthetic-b");
            }
        };
        expectExportFailure("body-mutation",fixture,fixture.plan,hook,RUN_ID,SOURCE_SHA,
            "R5_READBACK_MISMATCH");
        cases++;
    }

    private static void testResultFileMutation(Path root) throws Exception {
        final ExportFixture fixture=new ExportFixture(root,"result-file-mutation",false,false);
        final CohortTerminalTelemetryStudy.R5AfterResultWrite hook=
            new CohortTerminalTelemetryStudy.R5AfterResultWrite() {
            public void afterResultWrite(Path resultFile) throws Exception {
                mutateSameSizeAndRestoreTime(resultFile,
                    asciiBytes("\"fixtureLabel\":\"synthetic-a\""),
                    asciiBytes("\"fixtureLabel\":\"synthetic-b\""));
            }
        };
        expectExportFailure("result-file-mutation",fixture,fixture.plan,hook,
            RUN_ID,SOURCE_SHA,"R5_READBACK_MISMATCH");
        cases++;
    }

    private static void testExtraOutputFile(Path root) throws Exception {
        ExportFixture fixture=new ExportFixture(root,"extra-output-file",false,false);
        writeNew(fixture.outputDirectory.resolve("unexpected.txt"),
            asciiBytes("synthetic-extra\n"));
        expectExportFailure("extra-output-file",fixture,fixture.plan,null,RUN_ID,SOURCE_SHA,
            "R5_OUTPUT_EXTRA_FILE");
        cases++;
    }

    private static void testForeignReceiptPath(Path root) throws Exception {
        ExportFixture fixture=new ExportFixture(root,"foreign-receipt-path",false,true);
        expectExportFailure("foreign-receipt-path",fixture,fixture.plan,null,RUN_ID,SOURCE_SHA,
            "FINAL_AUTHORITY_MISMATCH");
        cases++;
    }

    private static void testConsumedReceiptTicket(Path root) throws Exception {
        ExportFixture fixture=new ExportFixture(root,"consumed-receipt-ticket",false,false);
        fixture.receiptBinding.verifyFinal(fixture.meter,fixture.started,fixture.operation);
        expectExportFailure("consumed-receipt-ticket",fixture,fixture.plan,null,
            RUN_ID,SOURCE_SHA,"FINAL_ATTEMPT_REUSED");
        cases++;
    }

    private static void testMarkerOutputFailure(Path root) throws Exception {
        ExportFixture fixture=new ExportFixture(root,"marker-output-failure",false,false);
        FailingOutputStream failing=new FailingOutputStream();
        Capture outcome=invoke(fixture,fixture.plan,null,RUN_ID,SOURCE_SHA,failing);
        check(failing.attempted,"MARKER_FAILURE_REACHED_SELECTED_STDOUT");
        check(outcome.failure!=null && outcome.metrics==null,
            "MARKER_FAILURE_IS_SURFACED_BY_EXPORT");
        check(outcome.streamError,"MARKER_FAILURE_STREAM_FLAGGED");
        check(outcome.bytes.length==0,"MARKER_FAILURE_EMITS_NO_COMPLETE_OR_SECOND_LINE");
        check("R5_STDOUT_ERROR".equals(exactCode(outcome.failure)),
            "MARKER_FAILURE_EXACT_CODE");
        cases++;
    }

    private static void testPostPublicationFatalGuard(Path root,
            List<FailureArtifact> artifacts) throws Exception {
        ExportFixture fixture=new ExportFixture(root,"postpublication-fatal",false,false);
        PostPublicationOutputStream target=new PostPublicationOutputStream(fixture.meter.meter);
        Capture outcome=invoke(fixture,fixture.plan,null,RUN_ID,SOURCE_SHA,target);
        check(target.mutated,"POST_PUBLICATION_CONTROLLED_METER_MUTATION");
        check(outcome.failure!=null && "R5_METER_CHANGED".equals(exactCode(outcome.failure)),
            "POST_PUBLICATION_METER_GUARD_FAILURE");
        check(outcome.metrics==null,"POST_PUBLICATION_FATAL_NOT_SUCCESS_METRICS");
        check(!outcome.streamError,"POST_PUBLICATION_MARKER_STREAM_ACCEPTED");
        check(target.writes==1 && isSingleAsciiLine(outcome.bytes) &&
            outcome.bytes[0]=='{' && outcome.bytes[outcome.bytes.length-2]=='}' &&
            outcome.bytes.length<=MARKER_LIMIT,
            "POST_PUBLICATION_ONE_COMPLETE_MARKER_NO_RETRY");
        checkExactOutputClosure(fixture.outputDirectory,"POST_PUBLICATION_FILES_RETAINED");
        artifacts.add(new FailureArtifact("postpublication-fatal/marker-after-fatal.txt",
            outcome.bytes));
        cases++;
    }

    private static void expectExportFailure(String label,ExportFixture fixture,
            CohortTerminalTelemetryStudy.R5WorkFinalizer finalizer,
            CohortTerminalTelemetryStudy.R5AfterResultWrite hook,
            String runId,String sourceSha,String expectedCode)
            throws Exception {
        Capture outcome=invoke(fixture,finalizer,hook,runId,sourceSha,
            new ByteArrayOutputStream());
        check(outcome.failure!=null,"NEGATIVE_REJECTED_"+label);
        check(expectedCode.equals(exactCode(outcome.failure)),
            "NEGATIVE_EXACT_CODE_"+label+"_EXPECTED_"+expectedCode+"_GOT_"+
            exactCode(outcome.failure));
        check(outcome.bytes.length==0,"NEGATIVE_NO_MARKER_"+label);
        check(outcome.metrics==null,"NEGATIVE_NO_SUCCESS_METRICS_"+label);
    }

    private static Capture invoke(ExportFixture fixture,
            CohortTerminalTelemetryStudy.R5WorkFinalizer finalizer,
            CohortTerminalTelemetryStudy.R5AfterResultWrite hook,
            String runId,String sourceSha,OutputStream target)
            throws Exception {
        PrintStream previous=System.out;
        PrintStream captured=new PrintStream(target,true,"US-ASCII");
        CohortTerminalTelemetryStudy.R5ExportMetrics metrics=null;
        Throwable failure=null;
        boolean streamError=false;
        try {
            System.setOut(captured);
            metrics=CohortTerminalTelemetryStudy.exportV2(
                runId,sourceSha,WIRE_SHA,CONTEXT_SHA,fixture.outputDirectory,
                fixture.result,fixture.meter,fixture.started,fixture.plan.pre,
                finalizer,fixture.receiptBinding,hook);
        } catch (Throwable thrown) {
            failure=thrown;
        } finally {
            try {
                captured.flush();
                streamError=captured.checkError();
            } finally {
                System.setOut(previous);
                captured.close();
            }
        }
        byte[] bytes;
        if (target instanceof FailingOutputStream)
            bytes=((FailingOutputStream)target).bytes();
        else if (target instanceof PostPublicationOutputStream)
            bytes=((PostPublicationOutputStream)target).bytes();
        else bytes=((ByteArrayOutputStream)target).toByteArray();
        return new Capture(metrics,failure,bytes,streamError);
    }

    private static void checkMetrics(CohortTerminalTelemetryStudy.R5ExportMetrics m,
            MeterState pre,MeterState post,long resultBytes,long markerBytes,
            long expectedBlocks,Map<String,Object> result,String label) {
        long blocks=(resultBytes+4095L)/4096L;
        check(blocks==expectedBlocks,"POSITIVE_EXPECTED_BLOCKS_"+label);
        long expectedFee=5L*blocks+3L;
        long expectedCopies=2L*resultBytes+32L+markerBytes;
        long expectedChunks=2L*blocks+2L;
        check(m.resultBytes==resultBytes && m.markerBytes==markerBytes,
            "POSITIVE_REPORTED_OUTPUT_LENGTHS_"+label);
        check(resultBytes<=RESULT_LIMIT && markerBytes<=MARKER_LIMIT,
            "POSITIVE_OUTPUT_BOUNDS_"+label);
        check(m.exportUnits==expectedFee,"POSITIVE_EXPORT_UNIT_FORMULA_"+label);
        check(m.copiedEntriesDelta==expectedCopies,
            "POSITIVE_COPY_ENTRY_FORMULA_"+label);
        check(m.copyChunksDelta==expectedChunks,
            "POSITIVE_COPY_CHUNK_FORMULA_"+label);
        check(post.units-pre.units==expectedFee,"POSITIVE_METER_UNIT_DELTA_"+label);
        check(post.groups-pre.groups==1L && post.prior==pre.prior,
            "POSITIVE_METER_GROUP_DELTA_"+label);
        check(post.copies-pre.copies==expectedCopies &&
            post.chunks-pre.chunks==expectedChunks,
            "POSITIVE_METER_COPY_DELTAS_"+label);
        long expectedPreExportUnits=pre.units+13L+17L+19L;
        check(numberFromMap(result,"unitsBeforeExport")==expectedPreExportUnits,
            "POSITIVE_LITERAL_PREEXPORT_ATTRIBUTION_"+label);
        long expectedGroups=numberFromMap(result,"groupsBeforeExport");
        long expectedMaxNanos=Math.max(numberFromMap(result,"maxGroupNanosBeforeExport"),
            m.finalExportNanos);
        long expectedMaxUnits=Math.max(
            numberFromMap(result,"maximumUnitsPerGroupBeforeExport"),expectedFee);
        long cutoff=numberFromMap(result,"elapsedCutoffNanos");
        check(expectedGroups==pre.groups+pre.prior &&
            numberFromMap(result,"wrapperGroupsBeforeExport")==pre.groups &&
            numberFromMap(result,"copiedEntries")==pre.copies &&
            numberFromMap(result,"copyChunks")==pre.chunks,
            "POSITIVE_PREEXPORT_SNAPSHOT_BINDINGS_"+label);
        check(m.totalUnits==expectedPreExportUnits+expectedFee &&
            m.totalGroups==expectedGroups+1L && m.totalGroups==post.groups+post.prior,
            "POSITIVE_TOTAL_METER_METRICS_"+label);
        check(m.maxGroupNanos==expectedMaxNanos &&
            m.maximumUnitsPerGroup==expectedMaxUnits &&
            m.maxGroupNanos==post.maxNanos && m.maximumUnitsPerGroup==post.maxUnits,
            "POSITIVE_MAXIMUM_GROUP_METRICS_"+label);
        check(m.finalExportNanos>=0L && m.finalExportNanos<=EXPORT_LIMIT_NANOS &&
            cutoff>0L && m.elapsedNanos>=cutoff+m.finalExportNanos &&
            m.elapsedNanos<=OPERATION_LIMIT_NANOS,
            "POSITIVE_REAL_CLOCK_BOUNDS_"+label);
        check(post.groups<=640L && post.maxUnits<=32768L,
            "POSITIVE_METER_GLOBAL_BOUNDS_"+label);
        check(post.maxNanos>=pre.maxNanos && post.maxUnits>=pre.maxUnits,
            "POSITIVE_METER_MAX_MONOTONIC_"+label);
    }

    private static void checkExactOutputClosure(Path output,String label) throws Exception {
        List<String> names=new ArrayList<String>();
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(output)) {
            for (Path path:entries) names.add(path.getFileName().toString());
        }
        check(names.size()==2,"POSITIVE_TWO_FILE_CLOSURE_COUNT_"+label);
        check(names.contains("op-1.jsonl") && names.contains("result.json"),
            "POSITIVE_TWO_FILE_CLOSURE_NAMES_"+label);
    }

    private static void checkOnlyOperation(Path output,String code) throws Exception {
        List<String> names=new ArrayList<String>();
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(output)) {
            for (Path path:entries) names.add(path.getFileName().toString());
        }
        check(names.size()==1 && names.contains("op-1.jsonl"),code);
    }

    private static byte[] independentMarker(
            CohortTerminalTelemetryStudy.R5ExportMetrics m,byte[] result) throws Exception {
        StringBuilder out=new StringBuilder(656);
        out.append('{');
        appendField(out,"schema",MARKER_SCHEMA,false);
        appendField(out,"status",MARKER_STATUS,true);
        appendField(out,"runId",RUN_ID,true);
        appendField(out,"sourceManifestSha256",SOURCE_SHA,true);
        appendField(out,"wireContractSha256",WIRE_SHA,true);
        appendField(out,"inputContextSha256",CONTEXT_SHA,true);
        appendField(out,"resultSha256",sha256(result),true);
        appendNumberField(out,"resultBytes",m.resultBytes);
        appendNumberField(out,"elapsedNanos",m.elapsedNanos);
        appendNumberField(out,"finalExportNanos",m.finalExportNanos);
        appendNumberField(out,"totalGroups",m.totalGroups);
        appendNumberField(out,"totalUnits",m.totalUnits);
        appendNumberField(out,"maxGroupNanos",m.maxGroupNanos);
        appendNumberField(out,"maximumUnitsPerGroup",m.maximumUnitsPerGroup);
        out.append('}').append('\n');
        return asciiBytes(out.toString());
    }

    private static byte[] independentJsonLine(Object value,boolean wide) throws Exception {
        if (!(value instanceof Map)) throw new AssertionError("EXPECTED_BODY_ROOT_MAP");
        @SuppressWarnings("unchecked")
        Map<String,Object> body=(Map<String,Object>)value;
        checkOrderedKeys(body,BODY_KEYS,"BODY_ROOT_ORDER_LITERAL");
        checkOrderedKeys((Map<?,?>)body.get("qualifiedPrior"),PRIOR_CHILD_KEYS,
            "PRIOR_CHILD_ORDER_LITERAL");
        checkOrderedKeys((Map<?,?>)body.get("originalCapture"),CAPTURE_CHILD_KEYS,
            "CAPTURE_CHILD_ORDER_LITERAL");
        checkOrderedKeys((Map<?,?>)body.get("cohort"),
            wide?COHORT_WIDE_KEYS:COHORT_SMALL_KEYS,"COHORT_CHILD_ORDER_LITERAL");
        Map<?,?> arms=(Map<?,?>)body.get("arms");
        checkOrderedKeys(arms,ARM_KEYS,"ARM_CHILD_ORDER_LITERAL");
        check(((Map<?,?>)arms.get("null")).isEmpty() &&
            ((Map<?,?>)arms.get("observe")).isEmpty(),"SYNTHETIC_ARM_CHILDREN_EMPTY");
        Object rawWork=body.get("work");
        if (!(rawWork instanceof Map)) throw new AssertionError("EXPECTED_WORK_MAP");
        checkOrderedKeys((Map<?,?>)rawWork,WORK_KEYS,"WORK_ORDER_LITERAL");
        checkOrderedKeys((Map<?,?>)body.get("cleanup"),CLEANUP_CHILD_KEYS,
            "CLEANUP_CHILD_ORDER_LITERAL");
        return asciiBytes(renderJson(value)+"\n");
    }

    private static void checkOrderedKeys(Map<?,?> map,String[] expected,String code) {
        Iterator<?> keys=map.keySet().iterator();
        for (int i=0;i<expected.length;i++) {
            check(keys.hasNext() && expected[i].equals(keys.next()),code+"_"+i);
        }
        check(!keys.hasNext(),code+"_CLOSED");
    }

    private static String renderJson(Object value) throws Exception {
        if (value==null) return "null";
        if (value instanceof String) return quoteAscii((String)value);
        if (value instanceof Boolean) return ((Boolean)value).booleanValue()?"true":"false";
        if (value instanceof Integer || value instanceof Long)
            return String.valueOf(((Number)value).longValue());
        if (value instanceof Map) {
            StringBuilder out=new StringBuilder();
            out.append('{');
            Iterator<? extends Map.Entry<?,?>> entries=
                ((Map<?,?>)value).entrySet().iterator();
            boolean first=true;
            while (entries.hasNext()) {
                Map.Entry<?,?> entry=entries.next();
                if (!(entry.getKey() instanceof String))
                    throw new AssertionError("NONSTRING_FIXTURE_KEY");
                if (!first) out.append(',');
                first=false;
                out.append(quoteAscii((String)entry.getKey())).append(':');
                out.append(renderJson(entry.getValue()));
            }
            return out.append('}').toString();
        }
        if (value instanceof List) {
            StringBuilder out=new StringBuilder();
            out.append('[');
            List<?> list=(List<?>)value;
            for (int i=0;i<list.size();i++) {
                if (i>0) out.append(',');
                out.append(renderJson(list.get(i)));
            }
            return out.append(']').toString();
        }
        throw new AssertionError("UNSUPPORTED_FIXTURE_JSON_TYPE_"+
            value.getClass().getName());
    }

    private static String quoteAscii(String value) {
        StringBuilder out=new StringBuilder(value.length()+2);
        out.append('"');
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if (c=='"') out.append("\\\"");
            else if (c=='\\') out.append("\\\\");
            else if (c=='\b') out.append("\\b");
            else if (c=='\f') out.append("\\f");
            else if (c=='\n') out.append("\\n");
            else if (c=='\r') out.append("\\r");
            else if (c=='\t') out.append("\\t");
            else if (c<0x20) {
                out.append("\\u00");
                out.append("0123456789abcdef".charAt((c>>>4)&15));
                out.append("0123456789abcdef".charAt(c&15));
            } else if (c>0x7e) throw new AssertionError("NONASCII_FIXTURE_STRING");
            else out.append(c);
        }
        return out.append('"').toString();
    }

    private static void appendField(StringBuilder out,String name,String value,
            boolean comma) {
        if (comma) out.append(',');
        out.append('"').append(name).append("\":").append('"').append(value).append('"');
    }

    private static void appendNumberField(StringBuilder out,String name,long value) {
        out.append(',').append('"').append(name).append("\":").append(value);
    }

    private static void persistPositiveAudit(Path root,String mode,
            List<PositiveRecord> positives,List<FailureArtifact> failureArtifacts)
            throws Exception {
        Path audit=root.resolve("audit");
        Files.createDirectory(audit);
        for (FailureArtifact artifact:failureArtifacts)
            writeNew(root.resolve(artifact.relativePath),artifact.bytes);
        List<Object> casesJson=new ArrayList<Object>();
        for (PositiveRecord record:positives) {
            writeNew(root.resolve(record.markerFile),record.marker);
            LinkedHashMap<String,Object> one=new LinkedHashMap<String,Object>();
            one.put("label",record.label);
            one.put("outputDir",record.outputDirectory);
            one.put("markerFile",record.markerFile);
            one.put("receiptBytes",Long.valueOf(record.receiptBytes));
            one.put("receiptSha256",record.receiptSha256);
            one.put("pre",record.pre.toJson());
            one.put("post",record.post.toJson());
            one.put("metrics",record.metrics.toJson());
            casesJson.add(one);
        }
        LinkedHashMap<String,Object> report=new LinkedHashMap<String,Object>();
        report.put("schema",REPORT_SCHEMA);
        report.put("mode",mode);
        report.put("cases",casesJson);
        writeNew(root.resolve("positive-reports.json"),
            asciiBytes(renderJson(report)+"\n"));
    }

    private static String exactCode(Throwable failure) {
        for (Throwable current=failure;current!=null;current=current.getCause()) {
            String message=current.getMessage();
            if (message!=null && message.matches("[A-Z][A-Z0-9_]*")) return message;
        }
        return failure==null?"NO_FAILURE":failure.getClass().getName();
    }

    private static String failureDescription(Throwable failure) {
        return failure==null?"NONE":exactCode(failure);
    }

    private static void mutateSameSizeAndRestoreTime(Path path,byte[] before,
            byte[] after) throws Exception {
        byte[] bytes=Files.readAllBytes(path);
        if (before.length!=after.length) throw new AssertionError("MUTATION_SIZE_FIXTURE");
        int at=indexOf(bytes,before);
        if (at<0) throw new AssertionError("MUTATION_TARGET_NOT_FOUND");
        FileTime modified=Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS);
        System.arraycopy(after,0,bytes,at,after.length);
        Files.write(path,bytes,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
        Files.setLastModifiedTime(path,modified);
        check(Files.size(path)==bytes.length,"MUTATION_PRESERVES_SIZE");
        check(modified.equals(Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS)),
            "MUTATION_RESTORES_MODIFIED_TIME");
    }

    private static int indexOf(byte[] input,byte[] needle) {
        if (needle.length==0) return 0;
        for (int i=0;i<=input.length-needle.length;i++) {
            int j=0;
            while (j<needle.length && input[i+j]==needle[j]) j++;
            if (j==needle.length) return i;
        }
        return -1;
    }

    private static boolean isSingleAsciiLine(byte[] bytes) {
        if (bytes==null || bytes.length==0 || bytes[bytes.length-1]!=(byte)10) return false;
        int lineFeeds=0;
        for (int i=0;i<bytes.length;i++) {
            int value=bytes[i]&255;
            if (value==10) {
                lineFeeds++;
                if (i!=bytes.length-1) return false;
            }
            if (value>127 || value<32 && value!=10) return false;
        }
        return lineFeeds==1;
    }

    private static void writeNew(Path path,byte[] bytes) throws Exception {
        try (OutputStream output=Files.newOutputStream(path,
                StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
            output.write(bytes);
        }
    }

    private static byte[] asciiBytes(String value) {
        for (int i=0;i<value.length();i++)
            if (value.charAt(i)>127) throw new AssertionError("NONASCII_EXPECTED_BYTES");
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder out=new StringBuilder(digest.length*2);
        for (int i=0;i<digest.length;i++) {
            int value=digest[i]&255;
            out.append("0123456789abcdef".charAt(value>>>4));
            out.append("0123456789abcdef".charAt(value&15));
        }
        return out.toString();
    }

    private static String repeat(char value,int count) {
        StringBuilder out=new StringBuilder(count);
        for (int i=0;i<count;i++) out.append(value);
        return out.toString();
    }

    private static void check(boolean ok,String code) {
        assertions++;
        if (!ok) throw new AssertionError(code);
    }

    private static final class ExportFixture {
        final long started;
        final Path caseDirectory,outputDirectory,operation,boundOperation;
        final String outputRelative;
        final byte[] receipt=asciiBytes("{\"synthetic\":\"receipt-A\"}\n");
        final CohortTerminalTelemetryStudy.Parent parent;
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final Map<String,Object> result;
        final ReceiptContentBinding receiptBinding;
        final CohortTerminalTelemetryStudy.R7WorkPlan plan;

        ExportFixture(Path root,String label,boolean wide,boolean foreignReceiptPath)
                throws Exception {
            caseDirectory=root.resolve(label);
            Files.createDirectory(caseDirectory);
            outputDirectory=caseDirectory.resolve("output");
            Files.createDirectory(outputDirectory);
            if (!outputDirectory.toRealPath().equals(outputDirectory))
                throw new AssertionError("FIXTURE_OUTPUT_NOT_CANONICAL");
            operation=outputDirectory.resolve("op-1.jsonl");
            writeNew(operation,receipt);
            if (foreignReceiptPath) {
                Path other=caseDirectory.resolve("different-operation");
                Files.createDirectory(other);
                boundOperation=other.resolve("op-1.jsonl");
                writeNew(boundOperation,receipt);
            } else boundOperation=operation;
            outputRelative=label+"/output";

            started=System.nanoTime();
            parent=new CohortTerminalTelemetryStudy.Parent();
            meter=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
            meter.charge(5L);
            meter.copy(3);
            meter.finish();

            meter.method("beginOriginalOwner",new Class<?>[0]);
            meter.method("admitOwnerGroup",new Class<?>[]{Integer.TYPE},Integer.valueOf(0));
            meter.charge(7L);
            meter.copy(2);
            meter.finish();
            meter.method("adoptOriginalOwner",new Class<?>[]{Integer.TYPE},Integer.valueOf(1));
            long originalMaxNanos=number(meter.meter,"maxGroupNanos");
            long originalMaxUnits=number(meter.meter,"maxGroupUnits");

            meter.charge(3L+4L+2L);
            meter.finish();
            meter.charge(5L+6L+7L);
            meter.finish();

            meter.charge(1L); // Open the shared metadata group before ticket preparation.
            receiptBinding=ReceiptContentBinding.prepare(
                meter,started,boundOperation,(long)receipt.length,sha256(receipt));
            meter.finish();

            result=body(wide);
            plan=CohortTerminalTelemetryStudy.captureDetachedR7WorkPlan(
                meter,started,result,
                8L,1L,3L,1L,
                13L,9L,17L,19L,2L,1L,2L,1L,
                3L,4L,2L,
                5L,6L,7L,0L,23L,0L,0L,0L,
                originalMaxNanos,originalMaxUnits);
        }

        private Map<String,Object> body(boolean wide) {
            LinkedHashMap<String,Object> result=new LinkedHashMap<String,Object>();
            result.put("schema",RESULT_SCHEMA);
            result.put("mode",RESULT_MODE);
            result.put("status",RESULT_STATUS);
            result.put("runId",RUN_ID);
            result.put("sourceManifestSha256",SOURCE_SHA);
            result.put("wireContractSha256",WIRE_SHA);
            result.put("inputContextSha256",CONTEXT_SHA);

            LinkedHashMap<String,Object> qualifiedPrior=new LinkedHashMap<String,Object>();
            qualifiedPrior.put("fixtureState","SYNTHETIC_NON_QUALIFYING");
            result.put("qualifiedPrior",qualifiedPrior);

            LinkedHashMap<String,Object> originalCapture=new LinkedHashMap<String,Object>();
            originalCapture.put("source","DETACHED_SYNTHETIC_SCALARS");
            result.put("originalCapture",originalCapture);

            LinkedHashMap<String,Object> cohort=new LinkedHashMap<String,Object>();
            cohort.put("fixtureLabel","synthetic-a");
            cohort.put("syntheticOnly",Boolean.TRUE);
            if (wide) {
                List<String> padding=new ArrayList<String>();
                for (int i=0;i<20;i++) padding.add(repeat((char)('A'+i),240));
                cohort.put("boundedAsciiPadding",padding);
            }
            result.put("cohort",cohort);

            LinkedHashMap<String,Object> arms=new LinkedHashMap<String,Object>();
            arms.put("null",new LinkedHashMap<String,Object>());
            arms.put("observe",new LinkedHashMap<String,Object>());
            result.put("arms",arms);
            result.put("work",null);

            LinkedHashMap<String,Object> cleanup=new LinkedHashMap<String,Object>();
            cleanup.put("fixtureState","SYNTHETIC_CLOSED");
            result.put("cleanup",cleanup);
            return result;
        }
    }

    private static long number(Object owner,String name) throws Exception {
        Object value=CohortTerminalTelemetryStudy.field(owner,name);
        if (!(value instanceof Integer || value instanceof Long))
            throw new AssertionError("METER_NUMBER_FIELD_"+name);
        return ((Number)value).longValue();
    }

    private static boolean flag(Object owner,String name) throws Exception {
        Object value=CohortTerminalTelemetryStudy.field(owner,name);
        if (!(value instanceof Boolean)) throw new AssertionError("METER_BOOLEAN_FIELD_"+name);
        return ((Boolean)value).booleanValue();
    }

    private static long numberFromMap(Map<String,Object> body,String key) {
        Object rawWork=body.get("work");
        if (!(rawWork instanceof Map)) throw new AssertionError("WORK_MAP_FOR_"+key);
        Object value=((Map<?,?>)rawWork).get(key);
        if (!(value instanceof Integer || value instanceof Long))
            throw new AssertionError("WORK_NUMBER_FOR_"+key);
        return ((Number)value).longValue();
    }

    private static final class MeterState {
        final long units,groups,prior,copies,chunks,maxNanos,maxUnits,groupUnits;
        final long started,groupStarted,copyAllowance,constructorEntries,measureCount;
        final boolean open,ownerStarted,ownerActive,ownerAdopted;
        private MeterState(Object meter) throws Exception {
            units=number(meter,"units");
            groups=number(meter,"groups");
            prior=number(meter,"priorGroups");
            copies=number(meter,"copiedEntries");
            chunks=number(meter,"copyChunks");
            maxNanos=number(meter,"maxGroupNanos");
            maxUnits=number(meter,"maxGroupUnits");
            groupUnits=number(meter,"groupUnits");
            started=number(meter,"started");
            groupStarted=number(meter,"groupStarted");
            copyAllowance=number(meter,"copyAllowance");
            constructorEntries=number(meter,"sessionConstructorEntries");
            Object rawMeasures=CohortTerminalTelemetryStudy.field(meter,"groupMeasures");
            if (!(rawMeasures instanceof List)) throw new AssertionError("METER_MEASURES_TYPE");
            measureCount=((List<?>)rawMeasures).size();
            Object rawOpen=CohortTerminalTelemetryStudy.field(meter,"open");
            if (!(rawOpen instanceof Boolean)) throw new AssertionError("METER_OPEN_TYPE");
            open=((Boolean)rawOpen).booleanValue();
            ownerStarted=flag(meter,"originalOwnerStarted");
            ownerActive=flag(meter,"originalOwnerActive");
            ownerAdopted=flag(meter,"originalOwnerAdopted");
        }
        static MeterState capture(Object meter) throws Exception { return new MeterState(meter); }
        boolean same(MeterState other) {
            return units==other.units && groups==other.groups && prior==other.prior &&
                copies==other.copies && chunks==other.chunks && maxNanos==other.maxNanos &&
                maxUnits==other.maxUnits && groupUnits==other.groupUnits &&
                started==other.started && groupStarted==other.groupStarted &&
                copyAllowance==other.copyAllowance && constructorEntries==other.constructorEntries &&
                measureCount==other.measureCount && open==other.open &&
                ownerStarted==other.ownerStarted && ownerActive==other.ownerActive &&
                ownerAdopted==other.ownerAdopted;
        }
        LinkedHashMap<String,Object> toJson() {
            LinkedHashMap<String,Object> out=new LinkedHashMap<String,Object>();
            out.put("units",Long.valueOf(units));
            out.put("groups",Long.valueOf(groups));
            out.put("prior",Long.valueOf(prior));
            out.put("copies",Long.valueOf(copies));
            out.put("chunks",Long.valueOf(chunks));
            out.put("maxNanos",Long.valueOf(maxNanos));
            out.put("maxUnits",Long.valueOf(maxUnits));
            return out;
        }
    }

    private static final class MetricState {
        final long resultBytes,markerBytes,exportUnits,finalExportNanos,elapsedNanos;
        final long totalUnits,totalGroups,maxGroupNanos,maximumUnitsPerGroup;
        final long copiedEntriesDelta,copyChunksDelta;
        MetricState(CohortTerminalTelemetryStudy.R5ExportMetrics m) {
            resultBytes=m.resultBytes; markerBytes=m.markerBytes; exportUnits=m.exportUnits;
            finalExportNanos=m.finalExportNanos; elapsedNanos=m.elapsedNanos;
            totalUnits=m.totalUnits; totalGroups=m.totalGroups;
            maxGroupNanos=m.maxGroupNanos; maximumUnitsPerGroup=m.maximumUnitsPerGroup;
            copiedEntriesDelta=m.copiedEntriesDelta; copyChunksDelta=m.copyChunksDelta;
        }
        LinkedHashMap<String,Object> toJson() {
            LinkedHashMap<String,Object> out=new LinkedHashMap<String,Object>();
            out.put("resultBytes",Long.valueOf(resultBytes));
            out.put("markerBytes",Long.valueOf(markerBytes));
            out.put("exportUnits",Long.valueOf(exportUnits));
            out.put("finalExportNanos",Long.valueOf(finalExportNanos));
            out.put("elapsedNanos",Long.valueOf(elapsedNanos));
            out.put("totalUnits",Long.valueOf(totalUnits));
            out.put("totalGroups",Long.valueOf(totalGroups));
            out.put("maxGroupNanos",Long.valueOf(maxGroupNanos));
            out.put("maximumUnitsPerGroup",Long.valueOf(maximumUnitsPerGroup));
            out.put("copiedEntriesDelta",Long.valueOf(copiedEntriesDelta));
            out.put("copyChunksDelta",Long.valueOf(copyChunksDelta));
            return out;
        }
    }

    private static final class PositiveRecord {
        final String label,outputDirectory,markerFile,receiptSha256;
        final long receiptBytes;
        final byte[] marker;
        final MeterState pre,post;
        final MetricState metrics;
        PositiveRecord(String label,String outputDirectory,String markerFile,long receiptBytes,
                String receiptSha256,byte[] marker,MeterState pre,MeterState post,
                CohortTerminalTelemetryStudy.R5ExportMetrics metrics) {
            this.label=label; this.outputDirectory=outputDirectory; this.markerFile=markerFile;
            this.receiptBytes=receiptBytes; this.receiptSha256=receiptSha256;
            this.marker=marker; this.pre=pre; this.post=post; this.metrics=new MetricState(metrics);
        }
    }

    private static final class Capture {
        final CohortTerminalTelemetryStudy.R5ExportMetrics metrics;
        final Throwable failure;
        final byte[] bytes;
        final boolean streamError;
        Capture(CohortTerminalTelemetryStudy.R5ExportMetrics metrics,Throwable failure,
                byte[] bytes,boolean streamError) {
            this.metrics=metrics; this.failure=failure; this.bytes=bytes;
            this.streamError=streamError;
        }
    }

    private static final class FailureArtifact {
        final String relativePath;
        final byte[] bytes;
        FailureArtifact(String relativePath,byte[] bytes) {
            this.relativePath=relativePath; this.bytes=bytes;
        }
    }

    private static final class FailingOutputStream extends OutputStream {
        boolean attempted;
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        public void write(int value) throws IOException {
            attempted=true;
            throw new IOException("CANARY_STDOUT_FAILURE");
        }
        public void write(byte[] values,int offset,int length) throws IOException {
            attempted=true;
            throw new IOException("CANARY_STDOUT_FAILURE");
        }
        byte[] bytes() { return bytes.toByteArray(); }
    }

    private static final class PostPublicationOutputStream extends OutputStream {
        final Object meter;
        final ByteArrayOutputStream output=new ByteArrayOutputStream();
        int writes;
        boolean mutated;
        PostPublicationOutputStream(Object meter) { this.meter=meter; }
        public void write(int value) throws IOException {
            output.write(value);
            if (value==10) mutateAfterCompleteLine();
        }
        public void write(byte[] bytes,int offset,int length) throws IOException {
            writes++;
            output.write(bytes,offset,length);
            if (length>0 && bytes[offset+length-1]==(byte)10)
                mutateAfterCompleteLine();
        }
        private void mutateAfterCompleteLine() throws IOException {
            if (mutated) return;
            mutated=true;
            try {
                long units=number(meter,"units");
                CohortTerminalTelemetryStudy.setNumber(meter,"units",Long.valueOf(units+1L));
            } catch (Exception failure) {
                throw new IOException("CANARY_POST_PUBLICATION_MUTATION",failure);
            }
        }
        byte[] bytes() { return output.toByteArray(); }
    }
}
