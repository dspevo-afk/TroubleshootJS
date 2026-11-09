package com.lushprojects.circuitjs1.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic source-only composition; no producer, original arm, or host transport. */
public final class ExportCompositionCanary {
    private static final String RUN="00000000-0000-0000-0000-000000000001";
    private static final String SOURCE="1111111111111111111111111111111111111111111111111111111111111111";
    private static final String WIRE="1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed";
    private static final String CONTEXT="ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f";
    private static final String POSITIVE="current_export_composition";
    private static final String NEGATIVE="receipt_changed_before_export_close";
    private static final String[] STRINGS={"name","status","phase","reason","cleanupDisposition",
        "error","cleanupError","expectedFailure","resultHex","markerHex"};
    private static final String[] BOOLEANS={"meterOpen","stdoutRestored","metricsReturned",
        "meterDiscarded","receiptMutationApplied"};
    private static final String[] INTEGERS={"operationNanos","cleanupNanos","pathEntries",
        "receiptBytes","receiptHashBlocks","units","copiedEntries","copyChunks","wrapperGroups",
        "priorGroups","groupUnits","maxGroupUnits","maxGroupNanos","closedGroupMeasures",
        "workFinalizedSlots","exportCutoffNanos","beforeExportElapsedNanos",
        "afterExportElapsedNanos","setupMeasureUnits","setupMeasureNanos",
        "metadataMeasureUnits","metadataMeasureNanos","exportMeasureUnits",
        "exportMeasureNanos","resultBytesObserved","metricsResultBytes","metricsMarkerBytes",
        "metricsExportUnits","metricsFinalExportNanos","metricsElapsedNanos",
        "metricsTotalUnits","metricsTotalGroups","metricsMaxGroupNanos",
        "metricsMaximumUnitsPerGroup","metricsCopiedEntriesDelta","metricsCopyChunksDelta"};
    private static final int SMALL_RESULT_LIMIT=32768, MARKER_LIMIT=656;
    private ExportCompositionCanary() { }

    public static void main(String[] args) {
        String mode=args!=null && args.length>0 &&
            ("SMOKE".equals(args[0]) || "SUITE".equals(args[0]))?args[0]:"INVALID";
        List<String> names=new ArrayList<String>();
        List<Map<String,Object>> cases=new ArrayList<Map<String,Object>>();
        boolean pass=true;
        try {
            check(args!=null && args.length==2 && !"INVALID".equals(mode),"CANARY_ARGUMENTS");
            Path root=emptyRoot(args[1]);
            CohortTerminalTelemetryStudy.R5FileIdentity rootId=id(root,true);
            names.add("SMOKE".equals(mode)?POSITIVE:NEGATIVE);
            Map<String,Object> result=run(root,rootId,names.get(0));
            cases.add(result); pass="PASS".equals(result.get("status"));
            rootId.verify(root,true,false); empty(root);
        } catch (Throwable failure) {
            pass=false;
            Map<String,Object> r=record("protocol");
            fail(r,"protocol",failure); cases.add(r);
        }
        Map<String,Object> output=new LinkedHashMap<String,Object>();
        output.put("mode",mode); output.put("status",pass?"PASS":"FAIL");
        output.put("names",names); output.put("cases",cases);
        System.out.println(json(output));
        if (!pass) System.exit(1);
    }

    private static final class State {
        final Path root; final CohortTerminalTelemetryStudy.R5FileIdentity rootId;
        final String name; final boolean negative;
        Path dir,receipt,result;
        CohortTerminalTelemetryStudy.R5FileIdentity dirId,receiptId,resultId;
        boolean madeDir,madeReceipt,mutation,stdoutRestored;
        byte[] receiptBytes,markerBytes=new byte[0];
        FileTime receiptMtime;
        int paths;
        long blocks,started,u0,c0,beforeElapsed,afterElapsed,cutoff;
        String receiptSha,phase="fixture";
        CohortTerminalTelemetryStudy.SetupMeter meter;
        CohortTerminalTelemetryStudy.SetupResult setup;
        CohortTerminalTelemetryStudy.R7WorkPlan plan;
        CohortTerminalTelemetryStudy.R5ExportMetrics metrics;
        ReceiptContentBinding ticket;
        Map<String,Object> body,work;
        Map<String,Object>[] children;
        State(Path root,CohortTerminalTelemetryStudy.R5FileIdentity rootId,String name) {
            this.root=root; this.rootId=rootId; this.name=name;
            negative=NEGATIVE.equals(name);
        }
    }

    private static Map<String,Object> record(String name) {
        Map<String,Object> r=new LinkedHashMap<String,Object>();
        for (String key:STRINGS) r.put(key,"");
        for (String key:BOOLEANS) r.put(key,Boolean.FALSE);
        for (String key:INTEGERS) r.put(key,Long.valueOf(0));
        r.put("name",name); r.put("status","PASS"); r.put("phase","complete");
        r.put("reason","OK"); return r;
    }
    private static Map<String,Object> run(Path root,
            CohortTerminalTelemetryStudy.R5FileIdentity rootId,String name) {
        State s=new State(root,rootId,name);
        Map<String,Object> r=record(name);
        long opStart=System.nanoTime(); Throwable failure=null;
        try { fixture(s); exercise(s,r); }
        catch (Throwable caught) { failure=caught; }
        r.put("operationNanos",positiveElapsed(opStart));
        long cleanupStart=System.nanoTime(); Throwable cleanupFailure=null;
        try { capture(s,r); }
        catch (Throwable caught) { cleanupFailure=caught; }
        try {
            s.rootId.verify(s.root,true,false);
            if (s.madeDir) {
                check(s.dir.getParent().equals(s.root) && s.dirId!=null,"CLEANUP_DIRECTORY_OWNER");
                s.dirId.verify(s.dir,true,false);
                if (Files.exists(s.result,LinkOption.NOFOLLOW_LINKS)) {
                    check(s.result.getParent().equals(s.dir),"CLEANUP_RESULT_CONTAINMENT");
                    CohortTerminalTelemetryStudy.R5FileIdentity resultId=id(s.result,false);
                    if (s.resultId!=null) s.resultId.verify(s.result,false,true);
                    resultId.verify(s.result,false,false);
                    Files.delete(s.result);
                    check(!Files.exists(s.result,LinkOption.NOFOLLOW_LINKS),"CLEANUP_RESULT_PRESENT");
                }
                if (s.madeReceipt) {
                    check(s.receipt.getParent().equals(s.dir) && s.receiptId!=null,
                        "CLEANUP_RECEIPT_OWNER");
                    s.receiptId.verify(s.receipt,false,false);
                    Files.delete(s.receipt);
                    check(!Files.exists(s.receipt,LinkOption.NOFOLLOW_LINKS),"CLEANUP_RECEIPT_PRESENT");
                }
                empty(s.dir); s.dirId.verify(s.dir,true,false); Files.delete(s.dir);
                check(!Files.exists(s.dir,LinkOption.NOFOLLOW_LINKS),"CLEANUP_DIRECTORY_PRESENT");
            }
            empty(s.root);
            if (failure==null && cleanupFailure==null) {
                r.put("cleanupDisposition",s.negative?
                    "EXPECTED_PARTIAL_EXPORT_METER_DISCARDED_AND_REMOVED":
                    "ALREADY_CLOSED_AND_REMOVED");
            }
        } catch (Throwable caught) { cleanupFailure=combine(cleanupFailure,caught); }
        r.put("cleanupNanos",positiveElapsed(cleanupStart));
        if (failure!=null) fail(r,s.phase,failure);
        if (cleanupFailure!=null) {
            r.put("status","FAIL"); r.put("phase","cleanup"); r.put("reason","CLEANUP_FAILED");
            r.put("cleanupDisposition","CLEANUP_FAILED");
            r.put("cleanupError",describe(cleanupFailure));
        }
        return r;
    }

    private static void fixture(State s) throws Exception {
        s.rootId.verify(s.root,true,false);
        s.dir=s.root.resolve(s.name); check(s.dir.getParent().equals(s.root),"FIXTURE_PARENT");
        Files.createDirectory(s.dir); s.madeDir=true; s.dirId=id(s.dir,true);
        s.receipt=s.dir.resolve("op-1.jsonl"); s.result=s.dir.resolve("result.json");
        s.receiptBytes=new byte[4096]; Arrays.fill(s.receiptBytes,(byte)'x');
        s.receiptBytes[4095]=(byte)'\n';
        Files.createFile(s.receipt); s.madeReceipt=true;
        Files.write(s.receipt,s.receiptBytes,StandardOpenOption.WRITE);
        s.receiptId=id(s.receipt,false);
        check(s.receipt.equals(s.receipt.toRealPath()),"RECEIPT_CANONICAL");
        check(!Files.exists(s.result,LinkOption.NOFOLLOW_LINKS),"RESULT_PREEXISTS");
        s.paths=s.receipt.getNameCount()+1; s.blocks=1;
        s.receiptSha=sha(s.receiptBytes);
        s.receiptMtime=Files.getLastModifiedTime(s.receipt,LinkOption.NOFOLLOW_LINKS);
        check(Arrays.equals(s.receiptBytes,Files.readAllBytes(s.receipt)),"RECEIPT_FIXTURE");
        s.u0=28712L+10L*s.paths+6L*s.blocks; s.c0=618L+2L*s.paths;
        s.children=emptyChildren();
        CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
        s.started=System.nanoTime();
        s.meter=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(s.started));
        check(num(s.meter,"units")==0 && !flag(s.meter,"open"),"ZERO_CLOSED_METER");
    }

    private static void exercise(final State s,Map<String,Object> r) throws Exception {
        s.phase="input_setup";
        final Object nativeMeter=s.meter.meter;
        s.setup=CurrentV2InputSetup.read(s.meter,s.started,CONTEXT,
            new CurrentV2InputSetup.Factory() {
                public String schemaTag() { return CohortTerminalTelemetryInputV2.schemaTag(); }
                public String contextSha256() { return CohortTerminalTelemetryInputV2.contextSha256(); }
                public int dataEntries() { return CohortTerminalTelemetryInputV2.dataEntries(); }
                public Map<String,Object> dataJson() { return CohortTerminalTelemetryInputV2.dataJson(); }
            });
        check(s.meter.meter==nativeMeter && num(s.meter,"started")==s.started,
            "SETUP_METER_IDENTITY");
        check(s.setup.units==10007 && s.setup.copiedEntries==478 &&
            s.setup.copyChunks==3 && s.setup.groups==1,"SETUP_ATTRIBUTION");
        checkTotals(s,10007,478,3,1,false);
        s.meter.method("beginOriginalOwner",new Class<?>[0]);
        s.meter.method("admitOwnerGroup",new Class<?>[]{int.class},Integer.valueOf(0));
        s.meter.method("adoptOriginalOwner",new Class<?>[]{int.class},Integer.valueOf(1));
        check(num(s.meter,"priorGroups")==1,"PRIOR_CREDIT");
        s.phase="root_constructor";
        s.meter.charge(4);
        s.body=CurrentV2CanonicalCodec.createBody(s.meter,RUN,SOURCE,CONTEXT,
            s.children[0],s.children[1],s.children[2],s.children[3]);
        check(s.body.getClass()==LinkedHashMap.class && s.body.size()==13 &&
            s.body.get("qualifiedPrior")==null && s.body.get("work")==null,
            "ROOT_SHAPE");
        checkBorrowedChildren(s);
        checkTotals(s,10025,491,4,1,true);
        s.phase="metadata_prepare";
        s.ticket=CurrentAssemblyMetadata.prepare(s.meter,s.started,s.setup,CONTEXT,
            s.receipt,4096,s.receiptSha,s.body);
        check(s.ticket!=null && s.ticket.getClass()==ReceiptContentBinding.class &&
            s.meter.meter==nativeMeter,"PREPARE_OWNERS");
        checkTotals(s,10025+268L+10L*s.paths+6L*s.blocks,512L+2L*s.paths,9,1,true);
        s.phase="r7_capture";
        s.plan=CohortTerminalTelemetryStudy.captureDetachedR7WorkPlan(
            s.meter,s.started,s.body,
            s.setup.units,s.setup.groups,s.setup.copiedEntries,s.setup.copyChunks,
            0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);
        check(s.plan!=null && s.plan.pre!=null && s.meter.meter==nativeMeter,
            "R7_SAME_OWNER");
        s.work=work(s.body);
        check(s.work.getClass()==LinkedHashMap.class && s.work.size()==34,
            "R7_WORK_SHAPE");
        for (Object value:s.work.values()) check(Long.valueOf(0).equals(value),
            "R7_UNFINALIZED");
        s.plan.pre.verifyReady(s.meter,s.started);
        s.plan.verifyBound(s.meter,s.plan.pre,s.body,s.work);
        checkTotals(s,s.u0,s.c0,23,2,false);
        check(num(s.meter,"priorGroups")==1 && measures(s).size()==2,
            "R7_MEASURES");
        checkBorrowedChildren(s);
        s.phase="export";
        s.beforeElapsed=positiveElapsed(s.started).longValue();
        PrintStream original=System.out;
        BoundedOutput capture=new BoundedOutput(MARKER_LIMIT);
        PrintStream redirected=new PrintStream(capture,true,"US-ASCII");
        Throwable exportFailure=null;
        try {
            System.setOut(redirected);
            s.metrics=CohortTerminalTelemetryStudy.exportV2(
                RUN,SOURCE,WIRE,CONTEXT,s.dir,s.body,s.meter,s.started,
                s.plan.pre,s.plan,s.ticket,s.negative?
                    new CohortTerminalTelemetryStudy.R5AfterResultWrite() {
                        public void afterResultWrite(Path resultFile) throws Exception {
                            check(resultFile.equals(s.result),"HOOK_RESULT_IDENTITY");
                            s.receiptId.verify(s.receipt,false,true);
                            byte[] changed=s.receiptBytes.clone(); changed[0]=(byte)'y';
                            Files.write(s.receipt,changed,StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING);
                            Files.setLastModifiedTime(s.receipt,s.receiptMtime);
                            check(Files.size(s.receipt)==4096 &&
                                Files.getLastModifiedTime(s.receipt,LinkOption.NOFOLLOW_LINKS)
                                    .equals(s.receiptMtime),"HOOK_RESTORED_SIZE_MTIME");
                            s.receiptId.verify(s.receipt,false,true);
                            s.mutation=true;
                        }
                    }:null);
        } catch (Throwable thrown) {
            exportFailure=thrown;
        } finally {
            try { redirected.flush(); }
            finally {
                System.setOut(original);
                s.stdoutRestored=System.out==original;
                redirected.close();
            }
        }
        s.afterElapsed=positiveElapsed(s.started).longValue();
        s.cutoff=num(s.meter,"groupStarted")-s.started;
        s.markerBytes=capture.toByteArray();
        check(s.stdoutRestored && !redirected.checkError(),"STDOUT_RESTORATION");
        check(s.beforeElapsed<=s.cutoff && s.cutoff<=s.afterElapsed &&
            s.afterElapsed<=90000000000L,"EXPORT_CUTOFF_CLOCK");
        check(s.body.get("work")==s.work && s.plan.pre!=null &&
            s.meter.meter==nativeMeter,"EXPORT_IDENTITIES");
        for (Object value:s.work.values()) check(value instanceof Long,"WORK_FINALIZED_TYPE");
        if (s.negative) {
            check(exportFailure instanceof ReceiptContentBinding.BindingFailure &&
                "FINAL_SHA_MISMATCH".equals(
                    ((ReceiptContentBinding.BindingFailure)exportFailure).code),
                "EXPECTED_FINAL_SHA_MISMATCH_"+describe(exportFailure));
            check(s.metrics==null && s.markerBytes.length==0 && s.mutation,
                "NEGATIVE_NO_PUBLICATION");
            r.put("expectedFailure","FINAL_SHA_MISMATCH");
        } else {
            if (exportFailure!=null) throw new AssertionError(
                "POSITIVE_EXPORT_"+describe(exportFailure),exportFailure);
            check(s.metrics!=null && s.markerBytes.length>=1 &&
                s.markerBytes.length<=MARKER_LIMIT && !s.mutation,
                "POSITIVE_PUBLICATION");
        }
        s.phase="complete";
    }

    private static void capture(State s,Map<String,Object> r) throws Exception {
        r.put("stdoutRestored",Boolean.valueOf(s.stdoutRestored));
        r.put("receiptMutationApplied",Boolean.valueOf(s.mutation));
        r.put("pathEntries",Long.valueOf(s.paths));
        r.put("receiptBytes",Long.valueOf(s.receiptBytes==null?0:s.receiptBytes.length));
        r.put("receiptHashBlocks",Long.valueOf(s.blocks));
        r.put("beforeExportElapsedNanos",Long.valueOf(s.beforeElapsed));
        r.put("afterExportElapsedNanos",Long.valueOf(s.afterElapsed));
        r.put("exportCutoffNanos",Long.valueOf(s.cutoff));
        r.put("markerHex",hex(s.markerBytes));
        if (s.meter!=null) {
            r.put("units",Long.valueOf(num(s.meter,"units")));
            r.put("copiedEntries",Long.valueOf(num(s.meter,"copiedEntries")));
            r.put("copyChunks",Long.valueOf(num(s.meter,"copyChunks")));
            r.put("wrapperGroups",Long.valueOf(num(s.meter,"groups")));
            r.put("priorGroups",Long.valueOf(num(s.meter,"priorGroups")));
            r.put("groupUnits",Long.valueOf(num(s.meter,"groupUnits")));
            r.put("maxGroupUnits",Long.valueOf(num(s.meter,"maxGroupUnits")));
            r.put("maxGroupNanos",Long.valueOf(num(s.meter,"maxGroupNanos")));
            r.put("meterOpen",Boolean.valueOf(flag(s.meter,"open")));
            List<?> ms=measures(s);
            r.put("closedGroupMeasures",Long.valueOf(ms.size()));
            if (ms.size()>0) measure(r,ms.get(0),"setupMeasureUnits","setupMeasureNanos");
            if (ms.size()>1) measure(r,ms.get(1),"metadataMeasureUnits","metadataMeasureNanos");
            if (ms.size()>2) measure(r,ms.get(2),"exportMeasureUnits","exportMeasureNanos");
            if (s.negative && s.mutation) r.put("meterDiscarded",Boolean.TRUE);
        }
        if (s.work!=null) {
            boolean finalized=s.plan!=null && Boolean.TRUE.equals(
                CohortTerminalTelemetryStudy.field(s.plan,"finalized"));
            r.put("workFinalizedSlots",Long.valueOf(finalized?s.work.size():0));
        }
        if (s.madeDir && Files.exists(s.result,LinkOption.NOFOLLOW_LINKS)) {
            check(s.result.getParent().equals(s.dir),"RESULT_CONTAINMENT");
            s.resultId=id(s.result,false);
            long bytes=Files.size(s.result);
            check(bytes>=1 && bytes<=SMALL_RESULT_LIMIT,"SMALL_RESULT_BOUND");
            byte[] actual=readSmallResult(s.result);
            s.resultId.verify(s.result,false,true);
            check(actual.length==bytes,"RESULT_READ_LENGTH");
            r.put("resultBytesObserved",Long.valueOf(actual.length));
            r.put("resultHex",hex(actual));
        }
        if (s.metrics!=null) {
            CohortTerminalTelemetryStudy.R5ExportMetrics m=s.metrics;
            r.put("metricsReturned",Boolean.TRUE);
            r.put("metricsResultBytes",Long.valueOf(m.resultBytes));
            r.put("metricsMarkerBytes",Long.valueOf(m.markerBytes));
            r.put("metricsExportUnits",Long.valueOf(m.exportUnits));
            r.put("metricsFinalExportNanos",Long.valueOf(m.finalExportNanos));
            r.put("metricsElapsedNanos",Long.valueOf(m.elapsedNanos));
            r.put("metricsTotalUnits",Long.valueOf(m.totalUnits));
            r.put("metricsTotalGroups",Long.valueOf(m.totalGroups));
            r.put("metricsMaxGroupNanos",Long.valueOf(m.maxGroupNanos));
            r.put("metricsMaximumUnitsPerGroup",Long.valueOf(m.maximumUnitsPerGroup));
            r.put("metricsCopiedEntriesDelta",Long.valueOf(m.copiedEntriesDelta));
            r.put("metricsCopyChunksDelta",Long.valueOf(m.copyChunksDelta));
        }
        if ("complete".equals(s.phase)) checkObserved(s,r);
    }

    private static void checkObserved(State s,Map<String,Object> r) throws Exception {
        long R=n(r,"resultBytesObserved"), C=(R+4095)/4096, M=s.markerBytes.length;
        check(R>=1 && R<=SMALL_RESULT_LIMIT && C>=1,"RESULT_OBSERVED");
        check(s.work.size()==34 && n(r,"workFinalizedSlots")==34,"FINALIZED_SLOTS");
        check(n(r,"setupMeasureUnits")==10007 &&
            n(r,"metadataMeasureUnits")==s.u0-10007 &&
            between(n(r,"setupMeasureNanos"),0,5000000000L) &&
            between(n(r,"metadataMeasureNanos"),0,5000000000L),"BASE_MEASURES");
        check(n(r,"priorGroups")==1 && s.beforeElapsed<=s.cutoff &&
            s.cutoff<=s.afterElapsed,"CLOCK_BOUNDS");
        if (s.negative) {
            check(flag(s.meter,"open") && n(r,"units")==s.u0+5*C+1 &&
                n(r,"copiedEntries")==s.c0+2*R &&
                n(r,"copyChunks")==23+2*C &&
                n(r,"wrapperGroups")==2 && n(r,"closedGroupMeasures")==2 &&
                n(r,"groupUnits")==5*C+1 && M==0 && s.metrics==null &&
                n(r,"exportMeasureUnits")==0 && n(r,"exportMeasureNanos")==0 &&
                s.afterElapsed-s.cutoff<=5000000000L,"NEGATIVE_LEDGER");
            check(n(r,"maxGroupUnits")==Math.max(10007,s.u0-10007) &&
                n(r,"maxGroupNanos")==Math.max(n(r,"setupMeasureNanos"),
                    n(r,"metadataMeasureNanos")),"NEGATIVE_MAXIMA");
        } else {
            long fee=5*C+3;
            check(!flag(s.meter,"open") && n(r,"units")==s.u0+fee &&
                n(r,"copiedEntries")==s.c0+2*R+32+M &&
                n(r,"copyChunks")==23+2*C+2 &&
                n(r,"wrapperGroups")==3 && n(r,"closedGroupMeasures")==3 &&
                n(r,"groupUnits")==fee && n(r,"exportMeasureUnits")==fee &&
                between(n(r,"exportMeasureNanos"),0,5000000000L),
                "POSITIVE_LEDGER");
            long maxUnits=Math.max(Math.max(10007,s.u0-10007),fee);
            long maxNanos=Math.max(Math.max(n(r,"setupMeasureNanos"),
                n(r,"metadataMeasureNanos")),n(r,"exportMeasureNanos"));
            check(n(r,"maxGroupUnits")==maxUnits && n(r,"maxGroupNanos")==maxNanos,
                "POSITIVE_MAXIMA");
            check(s.metrics.resultBytes==R && s.metrics.markerBytes==M &&
                s.metrics.exportUnits==fee &&
                s.metrics.finalExportNanos==n(r,"exportMeasureNanos") &&
                s.metrics.elapsedNanos>=s.cutoff+n(r,"exportMeasureNanos") &&
                s.metrics.elapsedNanos<=s.afterElapsed &&
                s.metrics.totalUnits==s.u0+fee && s.metrics.totalGroups==4 &&
                s.metrics.maxGroupNanos==maxNanos &&
                s.metrics.maximumUnitsPerGroup==maxUnits &&
                s.metrics.copiedEntriesDelta==2*R+32+M &&
                s.metrics.copyChunksDelta==2*C+2,"RETURNED_METRICS");
        }
    }

    private static void measure(Map<String,Object> r,Object m,String units,String nanos) throws Exception {
        r.put(units,Long.valueOf(((Number)CohortTerminalTelemetryStudy.field(m,"units")).longValue()));
        r.put(nanos,Long.valueOf(((Number)CohortTerminalTelemetryStudy.field(m,"nanos")).longValue()));
    }
    private static List<?> measures(State s) throws Exception {
        return (List<?>)CohortTerminalTelemetryStudy.field(s.meter.meter,"groupMeasures");
    }
    private static void checkTotals(State s,long units,long copies,long chunks,long groups,
            boolean open) throws Exception {
        check(num(s.meter,"units")==units && num(s.meter,"copiedEntries")==copies &&
            num(s.meter,"copyChunks")==chunks && num(s.meter,"groups")==groups &&
            flag(s.meter,"open")==open && num(s.meter,"started")==s.started,
            "ACTUAL_METER_TOTALS");
    }
    private static void checkBorrowedChildren(State s) {
        String[] keys={"originalCapture","cohort","arms","cleanup"};
        IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<Object,Boolean>();
        for (int i=0;i<4;i++) check(s.body.get(keys[i])==s.children[i] &&
            s.children[i].isEmpty() && seen.put(s.children[i],Boolean.TRUE)==null,
            "DISTINCT_BORROWED_CHILDREN");
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object>[] emptyChildren() {
        Map<String,Object>[] result=(Map<String,Object>[])new Map<?,?>[4];
        for (int i=0;i<4;i++) result[i]=new LinkedHashMap<String,Object>();
        return result;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> work(Map<String,Object> body) {
        check(body.get("work") instanceof Map,"WORK_MISSING");
        return (Map<String,Object>)body.get("work");
    }
    private static final class BoundedOutput extends ByteArrayOutputStream {
        final int limit;
        BoundedOutput(int limit) { this.limit=limit; }
        public synchronized void write(int value) {
            if (count>=limit) throw new AssertionError("MARKER_CAPTURE_BOUND");
            super.write(value);
        }
        public synchronized void write(byte[] b,int off,int len) {
            if (len>limit-count) throw new AssertionError("MARKER_CAPTURE_BOUND");
            super.write(b,off,len);
        }
    }
    /** Read at most the fixture limit plus one rejection byte, even after a size race. */
    private static byte[] readSmallResult(Path path) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream(4096);
        byte[] chunk=new byte[4096];
        int observed=0;
        try (InputStream in=Files.newInputStream(path,StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)) {
            while (true) {
                int allowance=SMALL_RESULT_LIMIT+1-observed;
                check(allowance>0,"SMALL_RESULT_BOUND");
                int count=in.read(chunk,0,Math.min(chunk.length,allowance));
                if (count<0) break;
                if (count==0) continue;
                observed+=count;
                check(observed<=SMALL_RESULT_LIMIT,"SMALL_RESULT_BOUND");
                out.write(chunk,0,count);
            }
        }
        return out.toByteArray();
    }
    private static Path emptyRoot(String raw) throws Exception {
        check(java.io.File.separatorChar=='\\',"WINDOWS_REQUIRED");
        Path root=Paths.get(raw);
        check(root.isAbsolute() && root.normalize().equals(root) &&
            root.toRealPath().equals(root) && raw.equals(root.toString()) &&
            !Files.isSymbolicLink(root) &&
            Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"CANARY_ROOT");
        for (Path p=root;p!=null;p=p.getParent()) id(p,true);
        empty(root); return root;
    }
    private static void empty(Path dir) throws Exception {
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(dir)) {
            check(!entries.iterator().hasNext(),"DIRECTORY_NOT_EMPTY");
        }
    }
    private static CohortTerminalTelemetryStudy.R5FileIdentity id(Path path,boolean dir)
            throws Exception {
        return new CohortTerminalTelemetryStudy.R5FileIdentity(path,dir);
    }
    private static long num(CohortTerminalTelemetryStudy.SetupMeter m,String name) throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(m.meter,name)).longValue();
    }
    private static boolean flag(CohortTerminalTelemetryStudy.SetupMeter m,String name)
            throws Exception {
        return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(m.meter,name));
    }
    private static Long positiveElapsed(long start) {
        long value=System.nanoTime()-start;
        check(value>=0,"NEGATIVE_CLOCK"); return Long.valueOf(value);
    }
    private static long n(Map<String,Object> r,String key) {
        return ((Long)r.get(key)).longValue();
    }
    private static boolean between(long x,long lo,long hi) { return x>=lo && x<=hi; }
    private static void check(boolean ok,String reason) {
        if (!ok) throw new AssertionError(reason);
    }
    private static void fail(Map<String,Object> r,String phase,Throwable error) {
        r.put("status","FAIL"); r.put("phase",phase);
        r.put("reason",describe(error)); r.put("error",describe(error));
    }
    private static Throwable combine(Throwable first,Throwable next) {
        if (first==null) return next;
        if (first!=next) first.addSuppressed(next);
        return first;
    }
    private static String sha(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static String hex(byte[] bytes) {
        StringBuilder result=new StringBuilder(bytes.length*2);
        for (byte b:bytes) {
            result.append("0123456789abcdef".charAt((b&255)>>>4));
            result.append("0123456789abcdef".charAt(b&15));
        }
        return result.toString();
    }
    private static String describe(Throwable error) {
        if (error==null) return "null";
        String value=error.getClass().getSimpleName()+":"+String.valueOf(error.getMessage());
        StringBuilder out=new StringBuilder();
        for (int i=0;i<value.length() && i<240;i++) {
            char c=value.charAt(i); out.append(c>=32 && c<=126?c:'?');
        }
        return out.toString();
    }
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
