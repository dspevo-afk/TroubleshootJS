package com.lushprojects.circuitjs1.client;

import java.io.IOException;
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

/** Synthetic composition only. No producer, owner arm, export, or normal assembly. */
public final class ComposedMetadataCanary {
    private static final String RUN="00000000-0000-0000-0000-000000000001";
    private static final String SOURCE="1111111111111111111111111111111111111111111111111111111111111111";
    private static final String CONTEXT="ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f";
    private static final String[] SMOKE={"composition_receipt_1_byte",
        "composition_receipt_4096_bytes","composition_receipt_4097_bytes"};
    private static final String[] SUITE={"ownership_sibling_alias","ownership_prior_alias",
        "metadata_repeat_slot","work_slot_nonempty","r7_wrong_start","r7_without_owner_credit",
        "receipt_changed_after_close","ticket_wrong_meter","ticket_wrong_start","ticket_wrong_path",
        "ticket_reuse","finalizer_reuse","snapshot_rejects_later_meter_work","final_copy_cap",
        "group640_snapshot_reject","intentional_metadata_split_accounted"};
    private static final String[] ROOT_KEYS={"schema","mode","status","runId",
        "sourceManifestSha256","wireContractSha256","inputContextSha256","qualifiedPrior",
        "originalCapture","cohort","arms","work","cleanup"};
    private static final String[] PRIOR_KEYS={"sourceParent","manifestSha256","sourceManifestSha256",
        "readerSha256","resultSha256","operation1Sha256","parentReceiptSha256","priorRunId",
        "priorOriginalReceipt","trialDisposition"};
    // Independent literal order from the pre-code contract/census, never r5WorkKey.
    private static final String[] WORK_KEYS={"elapsedCutoffNanos","cleanupNanos","groupsBeforeExport",
        "wrapperGroupsBeforeExport","maxGroupNanosBeforeExport","maximumUnitsPerGroupBeforeExport",
        "unitsBeforeExport","setupUnits","setupGroups","setupCopiedEntries","setupCopyChunks",
        "originalCaptureOwnerUnits","originalCaptureWrapperUnits","originalCaptureCallbackUnits",
        "originalCaptureDiagnosticUnits","originalCaptureGroups","originalCaptureCopiedEntries",
        "originalCaptureCopyChunks","nullOwnerUnits","nullWrapperUnits","nullObserverUnits",
        "observeOwnerUnits","observeWrapperUnits","observeObserverUnits","observerUnits","meterUnits",
        "metadataUnits","metadataGroups","metadataCopiedEntries","metadataCopyChunks","copiedEntries",
        "copyChunks","copyChunkLimit","copiedEntryLimit"};
    private static final int JSON_LIMIT=1048576;
    private ComposedMetadataCanary() { }

    public static void main(String[] args) {
        String mode=args!=null && args.length>0 &&
            ("SMOKE".equals(args[0]) || "SUITE".equals(args[0]))?args[0]:"INVALID";
        List<String> names=new ArrayList<String>();
        List<Map<String,Object>> records=new ArrayList<Map<String,Object>>();
        boolean pass=true;
        try {
            require(args!=null && args.length==2 && !"INVALID".equals(mode),"CANARY_ARGUMENTS");
            Path root=emptyRoot(args[1]);
            CohortTerminalTelemetryStudy.R5FileIdentity rootIdentity=identity(root,true);
            names.addAll(Arrays.asList("SMOKE".equals(mode)?SMOKE:SUITE));
            for (String name:names) {
                Map<String,Object> record=run(root,rootIdentity,name);
                records.add(record); if (!"PASS".equals(record.get("status"))) pass=false;
            }
            rootIdentity.verify(root,true,false); requireEmpty(root);
        } catch (Throwable failure) {
            pass=false;
            Map<String,Object> record=record("protocol");
            record.put("status","FAIL"); record.put("phase","protocol");
            record.put("reason",describe(failure)); record.put("error",describe(failure));
            records.add(record);
        }
        Map<String,Object> out=new LinkedHashMap<String,Object>();
        out.put("mode",mode); out.put("status",pass?"PASS":"FAIL");
        out.put("names",names); out.put("cases",records);
        System.out.println(json(out)); if (!pass) System.exit(1);
    }

    private static final class State {
        final Path root; final String name;
        final CohortTerminalTelemetryStudy.R5FileIdentity rootIdentity;
        Path directory,receipt;
        CohortTerminalTelemetryStudy.R5FileIdentity directoryIdentity,receiptIdentity;
        boolean createdDirectory,createdReceipt;
        byte[] bytes; String sha,inputBefore; FileTime modified;
        int size,paths; long blocks,started,u,c,su,sg,sc,sk;
        CohortTerminalTelemetryStudy.SetupMeter meter;
        CohortTerminalTelemetryStudy.SetupResult setup;
        CohortTerminalTelemetryStudy.R7WorkPlan plan;
        ReceiptContentBinding ticket;
        Map<String,Object> body,work;
        Map<String,Object>[] child;
        int finalizedSlots;
        String phase="fixture";
        State(Path root,CohortTerminalTelemetryStudy.R5FileIdentity rootIdentity,String name) {
            this.root=root; this.rootIdentity=rootIdentity; this.name=name;
        }
        boolean is(String value) { return name.equals(value); }
    }

    private static Map<String,Object> record(String name) {
        Map<String,Object> r=new LinkedHashMap<String,Object>();
        r.put("name",name); r.put("status","PASS"); r.put("phase","complete"); r.put("reason","OK");
        r.put("operationNanos",Long.valueOf(0)); r.put("cleanupNanos",Long.valueOf(0));
        r.put("cleanupDisposition","NONE"); r.put("error",""); r.put("cleanupError","");
        String[] numbers={"units","copies","chunks","groups","priorGroups","pathEntries","hashBlocks",
            "normalUnitsOracle","normalCopiesOracle","normalChunksOracle","workSlots","finalizedSlots"};
        for (String key:numbers) r.put(key,Long.valueOf(0));
        r.put("metadataOpen",Boolean.FALSE); return r;
    }

    private static Map<String,Object> run(Path root,
            CohortTerminalTelemetryStudy.R5FileIdentity rootIdentity,String name) {
        State s=new State(root,rootIdentity,name); Map<String,Object> r=record(name);
        long operationStarted=System.nanoTime(); Throwable failure=null;
        try { fixture(s); exercise(s); }
        catch (Throwable caught) { failure=caught; }
        r.put("operationNanos",Long.valueOf(System.nanoTime()-operationStarted));
        long cleanupStarted=System.nanoTime(); Throwable cleanupFailure=null;
        try { captureMetrics(s,r); }
        catch (Throwable caught) { cleanupFailure=caught; }
        try {
            boolean open=s.meter!=null && flag(s.meter,"open");
            if (open) { s.meter.finish(); require(!flag(s.meter,"open"),"CLEANUP_METER_OPEN"); }
            r.put("cleanupDisposition",open?"CLOSED_AND_REMOVED":"ALREADY_CLOSED_AND_REMOVED");
        } catch (Throwable caught) { cleanupFailure=combine(cleanupFailure,caught); }
        // Known exact leaves only: identity/containment checks precede every delete.
        try {
            if (s.createdReceipt) {
                s.rootIdentity.verify(s.root,true,false);
                require(s.receipt.getParent().equals(s.directory) &&
                    s.directory.getParent().equals(s.root),"CLEANUP_CONTAINMENT");
                require(s.directoryIdentity!=null && s.receiptIdentity!=null,"CLEANUP_IDENTITY_MISSING");
                s.directoryIdentity.verify(s.directory,true,false);
                s.receiptIdentity.verify(s.receipt,false,false); Files.delete(s.receipt);
                require(!Files.exists(s.receipt,LinkOption.NOFOLLOW_LINKS),"CLEANUP_RECEIPT_PRESENT");
            }
        } catch (Throwable caught) { cleanupFailure=combine(cleanupFailure,caught); }
        try {
            if (s.createdDirectory) {
                s.rootIdentity.verify(s.root,true,false);
                require(s.directory.getParent().equals(s.root) && s.directoryIdentity!=null,
                    "CLEANUP_DIRECTORY_CONTAINMENT");
                s.directoryIdentity.verify(s.directory,true,false); requireEmpty(s.directory);
                Files.delete(s.directory);
                require(!Files.exists(s.directory,LinkOption.NOFOLLOW_LINKS),"CLEANUP_DIRECTORY_PRESENT");
            }
        } catch (Throwable caught) { cleanupFailure=combine(cleanupFailure,caught); }
        r.put("cleanupNanos",Long.valueOf(System.nanoTime()-cleanupStarted));
        if (failure!=null) {
            r.put("status","FAIL"); r.put("phase",s.phase); r.put("reason",describe(failure));
            r.put("error",describe(failure));
        }
        if (cleanupFailure!=null) {
            r.put("status","FAIL"); r.put("phase","cleanup"); r.put("reason","CLEANUP_FAILED");
            r.put("cleanupDisposition","CLEANUP_FAILED"); r.put("cleanupError",describe(cleanupFailure));
        }
        return r;
    }

    private static void fixture(State s) throws Exception {
        s.rootIdentity.verify(s.root,true,false);
        s.directory=s.root.resolve(s.name); require(s.directory.getParent().equals(s.root),"FIXTURE_PARENT");
        Files.createDirectory(s.directory); s.createdDirectory=true;
        s.directoryIdentity=identity(s.directory,true);
        s.receipt=s.directory.resolve("op-1.jsonl");
        s.size=s.is("composition_receipt_1_byte")?1:s.is("composition_receipt_4097_bytes")?4097:4096;
        s.bytes=new byte[s.size]; Arrays.fill(s.bytes,(byte)'x'); s.bytes[s.size-1]=(byte)10;
        Files.createFile(s.receipt); s.createdReceipt=true;
        Files.write(s.receipt,s.bytes,StandardOpenOption.WRITE);
        s.receiptIdentity=identity(s.receipt,false);
        require(s.receipt.equals(s.receipt.toRealPath()),"FIXTURE_CANONICAL");
        s.paths=s.receipt.getNameCount()+1; s.blocks=(s.size+4095L)/4096L;
        s.sha=sha256(s.bytes); s.modified=Files.getLastModifiedTime(s.receipt,LinkOption.NOFOLLOW_LINKS);
        require(Arrays.equals(s.bytes,Files.readAllBytes(s.receipt)),"FIXTURE_BYTES");
        s.u=28712L+10L*s.paths+6L*s.blocks; s.c=618L+2L*s.paths;
        s.child=emptyChildren();
        // All receipt/path/hash/P/B preparation and Parent resolution precede producer time.
        CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
        s.started=System.nanoTime();
        s.meter=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(s.started));
        require(number(s.meter,"units")==0 && !flag(s.meter,"open"),"ZERO_CLOSED_METER");
    }

    private static void exercise(State s) throws Exception {
        s.phase="input_setup";
        final Object nativeMeter=s.meter.meter;
        s.setup=CurrentV2InputSetup.read(s.meter,s.started,CONTEXT,new CurrentV2InputSetup.Factory() {
            public String schemaTag() { return CohortTerminalTelemetryInputV2.schemaTag(); }
            public String contextSha256() { return CohortTerminalTelemetryInputV2.contextSha256(); }
            public int dataEntries() { return CohortTerminalTelemetryInputV2.dataEntries(); }
            public Map<String,Object> dataJson() { return CohortTerminalTelemetryInputV2.dataJson(); }
        });
        require(s.meter.meter==nativeMeter && number(s.meter,"started")==s.started,"SETUP_METER_IDENTITY");
        require(s.setup.units==10007 && s.setup.copiedEntries==478 && s.setup.copyChunks==3 &&
            s.setup.groups==1,"SETUP_ATTRIBUTION_ORACLE");
        totals(s,10007,478,3,1,false); census(s,new long[]{10007});
        s.inputBefore=json(s.setup.data);
        if (!s.is("r7_without_owner_credit")) {
            call(s.meter,"beginOriginalOwner");
            s.meter.method("admitOwnerGroup",new Class<?>[]{int.class},Integer.valueOf(0));
            s.meter.method("adoptOriginalOwner",new Class<?>[]{int.class},Integer.valueOf(1));
        }
        require(number(s.meter,"priorGroups")== (s.is("r7_without_owner_credit")?0:1),"PRIOR_CREDIT");
        if (s.is("group640_snapshot_reject"))
            for (int i=0;i<637;i++) { call(s.meter,"beginGroup"); s.meter.finish(); }
        s.phase="root_constructor";
        // The actual caller prepays four source observations before their reads.
        s.meter.charge(4); s.su=s.setup.units; s.sg=s.setup.groups;
        s.sc=s.setup.copiedEntries; s.sk=s.setup.copyChunks;
        s.body=CurrentV2CanonicalCodec.createBody(s.meter,RUN,SOURCE,CONTEXT,
            s.child[0],s.child[1],s.child[2],s.child[3]);
        checkRoot(s); require(s.body.get("qualifiedPrior")==null && s.body.get("work")==null,"ROOT_NULL_SLOTS");
        totals(s,10025,491,4,s.is("group640_snapshot_reject")?638:1,true);
        s.phase="metadata_prepare";
        final CohortTerminalTelemetryStudy.SetupResult actualSetup=s.setup;
        s.ticket=CurrentAssemblyMetadata.prepare(s.meter,s.started,s.setup,CONTEXT,
            s.receipt,s.size,s.sha,s.body);
        require(s.ticket!=null && s.ticket.getClass()==ReceiptContentBinding.class && s.setup==actualSetup &&
            s.meter.meter==nativeMeter,"PREPARE_SAME_OWNERS");
        checkQualified(s);
        long metadata=268L+10L*s.paths+6L*s.blocks;
        totals(s,10025+metadata,512+2L*s.paths,9,
            s.is("group640_snapshot_reject")?638:1,true);
        if (s.is("metadata_repeat_slot")) {
            MeterImage before=new MeterImage(s.meter);
            try {
                CurrentAssemblyMetadata.prepare(s.meter,s.started,s.setup,CONTEXT,
                    s.receipt,s.size,s.sha,s.body);
                throw new AssertionError("REPEAT_METADATA_ACCEPTED");
            } catch (IOException expected) { requireCode(expected,"METADATA_BODY_SLOT"); }
            require(number(s.meter,"units")==before.number("units")+4 &&
                number(s.meter,"copiedEntries")==before.number("copiedEntries") &&
                number(s.meter,"copyChunks")==before.number("copyChunks") && flag(s.meter,"open"),
                "REPEAT_METADATA_DEBIT");
            totals(s,10029+metadata,512+2L*s.paths,9,1,true);
            require(s.body.get("work")==null,"REPEAT_METADATA_WORK_SLOT");
            checkQualified(s); unchangedInput(s); return;
        }
        if (s.is("ownership_sibling_alias")) s.body.put("cohort",s.child[0]);
        if (s.is("ownership_prior_alias"))
            s.body.put("originalCapture",((Map<?,?>)s.setup.data.get("prior")).get("priorOriginalReceipt"));
        if (s.is("work_slot_nonempty")) {
            s.work=new LinkedHashMap<String,Object>(); s.body.put("work",s.work);
        }
        if (s.is("final_copy_cap"))
            s.meter.method("setCopyAllowance",new Class<?>[]{int.class},Integer.valueOf((int)(s.c-1)));
        boolean split=s.is("intentional_metadata_split_accounted");
        if (split) { s.meter.finish(); require(!flag(s.meter,"open"),"INTENTIONAL_SPLIT_CLOSE"); }
        s.phase="r7_capture";
        String expected=s.is("ownership_sibling_alias") || s.is("ownership_prior_alias")?
            "BODY_OWNERSHIP_ALIAS":s.is("work_slot_nonempty")?"R7_WORK_SLOT_NOT_EMPTY":
            s.is("r7_wrong_start") || s.is("r7_without_owner_credit") || s.is("group640_snapshot_reject")?
            "R5_METADATA_SNAPSHOT":s.is("final_copy_cap")?
            "copy exceeds its prebounded chunk or aggregate allowance":null;
        if (expected!=null) {
            long before=number(s.meter,"units");
            try { detached(s,s.is("r7_wrong_start")?s.started+1:s.started); }
            catch (IOException rejected) {
                requireCode(rejected,expected); checkR7Rejection(s,before,metadata); unchangedInput(s); return;
            }
            throw new AssertionError("R7_NEGATIVE_ACCEPTED");
        }
        s.plan=detached(s,s.started); s.work=work(s.body);
        checkWork(s.work,new long[34]);
        s.plan.pre.verifyReady(s.meter,s.started);
        s.plan.verifyBound(s.meter,s.plan.pre,s.body,s.work);
        totals(s,s.u,s.c,23,split?3:2,false);
        if (split) census(s,new long[]{10007,18+metadata,18419});
        else census(s,new long[]{10007,s.u-10007});
        checkChildren(s); checkQualified(s); unchangedInput(s);
        s.phase="finalizer";
        final CohortTerminalTelemetryStudy.R5Snapshot sameSnapshot=s.plan.pre;
        MeterImage closed=new MeterImage(s.meter);
        s.plan.finalizeWork(s.started+1000000L); s.finalizedSlots=34;
        require(s.plan.pre==sameSnapshot && s.body.get("work")==s.work,"FINALIZER_IDENTITIES");
        checkWork(s.work,workValues(s,split)); closed.unchanged(s.meter);
        s.plan.pre.verifyReady(s.meter,s.started);
        if (s.is("finalizer_reuse")) {
            try { s.plan.finalizeWork(s.started+1000000L); throw new AssertionError("FINALIZER_REUSED"); }
            catch (IOException rejected) { requireCode(rejected,"R7_FINALIZER_REUSED"); }
            checkWork(s.work,workValues(s,split)); closed.unchanged(s.meter);
        }
        s.phase="final_ticket_verification";
        if (s.is("receipt_changed_after_close")) {
            s.bytes[0]=(byte)'y';
            Files.write(s.receipt,s.bytes,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
            Files.setLastModifiedTime(s.receipt,s.modified);
            require(Files.size(s.receipt)==s.size &&
                Files.getLastModifiedTime(s.receipt,LinkOption.NOFOLLOW_LINKS).equals(s.modified),"MUTATION_RESTORED_IDENTITY");
            expectTicket(s,s.meter,s.started,s.receipt,"FINAL_SHA_MISMATCH");
        } else if (s.is("ticket_wrong_meter") || s.is("ticket_wrong_start") || s.is("ticket_wrong_path")) {
            CohortTerminalTelemetryStudy.SetupMeter supplied=s.meter;
            if (s.is("ticket_wrong_meter"))
                supplied=new CohortTerminalTelemetryStudy.SetupMeter(s.meter.parent,s.meter.parent.meter(s.started));
            MeterImage foreign=new MeterImage(supplied);
            expectTicket(s,supplied,s.is("ticket_wrong_start")?s.started+1:s.started,
                s.is("ticket_wrong_path")?s.directory.resolve("wrong.jsonl"):s.receipt,"FINAL_AUTHORITY_MISMATCH");
            foreign.unchanged(supplied);
            expectTicket(s,s.meter,s.started,s.receipt,"FINAL_ATTEMPT_REUSED");
        } else {
            s.ticket.verifyFinal(s.meter,s.started,s.receipt);
            if (s.is("ticket_reuse")) expectTicket(s,s.meter,s.started,s.receipt,"FINAL_ATTEMPT_REUSED");
        }
        closed.unchanged(s.meter); s.plan.pre.verifyReady(s.meter,s.started);
        require(s.plan.pre==sameSnapshot && s.body.get("work")==s.work,"TICKET_IDENTITIES");
        checkWork(s.work,workValues(s,split)); unchangedInput(s);
        if (s.is("snapshot_rejects_later_meter_work")) {
            s.phase="later_meter_work"; s.meter.charge(1);
            try { s.plan.pre.verifyReady(s.meter,s.started); throw new AssertionError("LATER_WORK_ACCEPTED"); }
            catch (IOException rejected) { requireCode(rejected,"R5_METER_CHANGED"); }
            totals(s,s.u+1,s.c,23,2,true);
            require(number(s.meter,"groupUnits")==1,"LATER_WORK_REAL_GROUP");
        }
    }

    private static CohortTerminalTelemetryStudy.R7WorkPlan detached(State s,long started) throws Exception {
        return CohortTerminalTelemetryStudy.captureDetachedR7WorkPlan(s.meter,started,s.body,
            s.su,s.sg,s.sc,s.sk,0,0,0,0,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);
    }
    private static void checkR7Rejection(State s,long before,long metadata) throws Exception {
        require(s.plan==null,"FAILED_PLAN_RETURNED");
        if (s.is("work_slot_nonempty")) {
            require(s.body.get("work")==s.work && s.work.isEmpty(),"OCCUPIED_SLOT_CHANGED");
            totals(s,before+2,512+2L*s.paths,9,1,true);
        } else {
            s.work=work(s.body); checkWork(s.work,new long[34]);
            if (s.is("final_copy_cap")) totals(s,s.u-34,s.c-34,22,1,true);
            else if (s.is("ownership_sibling_alias") || s.is("ownership_prior_alias")) {
                require(number(s.meter,"groups")==1 && flag(s.meter,"open"),"ALIAS_NOT_CLOSED");
                require(number(s.meter,"units")>before && number(s.meter,"units")<s.u-17136,
                    "ALIAS_BEFORE_ACCOUNT");
                require(number(s.meter,"copiedEntries")<=s.c-34,"ALIAS_NO_FINALIZER_COPIES");
                if (s.is("ownership_sibling_alias"))
                    require(s.body.get("originalCapture")==s.body.get("cohort"),"SIBLING_ALIAS_PRESERVED");
                else require(s.body.get("originalCapture")==
                    ((Map<?,?>)s.setup.data.get("prior")).get("priorOriginalReceipt"),"PRIOR_ALIAS_PRESERVED");
            } else {
                totals(s,s.u,s.c,23,s.is("group640_snapshot_reject")?639:2,false);
                if (s.is("group640_snapshot_reject")) {
                    long[] units=new long[639]; units[0]=10007; units[638]=s.u-10007; census(s,units);
                } else census(s,new long[]{10007,s.u-10007});
            }
        }
        checkQualified(s);
        for (Map<String,Object> child:s.child) require(child.isEmpty(),"REJECTION_CHILD_CHANGED");
    }

    private static long[] workValues(State s,boolean split) throws Exception {
        long[] v=new long[34];
        v[0]=1000000; v[2]=split?4:3; v[3]=split?3:2;
        v[4]=number(s.meter,"maxGroupNanos");
        v[5]=split?Math.max(10007,Math.max(18+268+10L*s.paths+6L*s.blocks,18419)):Math.max(10007,s.u-10007);
        v[6]=s.u; v[7]=10007; v[8]=1; v[9]=478; v[10]=3; v[15]=1;
        v[25]=s.u; v[26]=s.u-10007; v[27]=split?2:1; v[28]=s.c-478; v[29]=20;
        v[30]=s.c; v[31]=23; v[32]=4096; v[33]=1048576; return v;
    }
    private static void checkWork(Map<String,Object> work,long[] expected) {
        require(work.getClass()==LinkedHashMap.class && work.size()==34,"WORK_EXACT_SHAPE"); int i=0;
        for (Map.Entry<String,Object> e:work.entrySet()) {
            require(WORK_KEYS[i].equals(e.getKey()),"WORK_LITERAL_ORDER_"+i);
            require(e.getValue() instanceof Long && ((Long)e.getValue()).longValue()==expected[i],
                "WORK_EQUATION_"+WORK_KEYS[i]); i++;
        }
        require(i==34,"WORK_ALL_SLOTS");
    }
    private static void checkRoot(State s) {
        require(s.body.getClass()==LinkedHashMap.class && s.body.size()==13,"ROOT_EXACT_SHAPE"); int i=0;
        for (String key:s.body.keySet()) require(ROOT_KEYS[i++].equals(key),"ROOT_LITERAL_ORDER");
        require("tsj-cohort-terminal-result-v2".equals(s.body.get("schema")) &&
            "OBSERVED_COHORT_TERMINAL_TELEMETRY".equals(s.body.get("mode")) &&
            "COMPLETED_DIAGNOSTIC_ONLY".equals(s.body.get("status")),"ROOT_FIXED_HEADERS");
        require(RUN==s.body.get("runId") && SOURCE==s.body.get("sourceManifestSha256") &&
            CONTEXT==s.body.get("inputContextSha256") &&
            "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed".
                equals(s.body.get("wireContractSha256")),"ROOT_PARAMETER_HEADERS"); checkChildren(s);
    }
    private static void checkChildren(State s) {
        String[] keys={"originalCapture","cohort","arms","cleanup"};
        IdentityHashMap<Object,Boolean> seen=new IdentityHashMap<Object,Boolean>();
        for (int i=0;i<4;i++) require(s.body.get(keys[i])==s.child[i] && s.child[i].isEmpty() &&
            seen.put(s.child[i],Boolean.TRUE)==null,"BORROWED_DISJOINT_EMPTY_CHILDREN");
    }
    private static void checkQualified(State s) {
        Map<?,?> prior=(Map<?,?>)s.setup.data.get("prior");
        Object q=s.body.get("qualifiedPrior"); require(q!=null && q.getClass()==LinkedHashMap.class,"QUALIFIED_CLASS");
        Map<?,?> qualified=(Map<?,?>)q; require(qualified.size()==12,"QUALIFIED_SIZE");
        List<?> keys=new ArrayList<Object>(qualified.keySet());
        require("schema".equals(keys.get(0)) && "inputContextSha256".equals(keys.get(1)) &&
            "tsj-cohort-terminal-qualified-prior-v2".equals(qualified.get("schema")) &&
            CONTEXT==qualified.get("inputContextSha256"),"QUALIFIED_HEADERS");
        for (int i=0;i<10;i++) require(PRIOR_KEYS[i].equals(keys.get(i+2)) &&
            prior.get(PRIOR_KEYS[i])==qualified.get(PRIOR_KEYS[i]),"QUALIFIED_BORROWED_PRIOR");
    }
    private static void unchangedInput(State s) { require(s.inputBefore.equals(json(s.setup.data)),"INPUT_PRESERVED"); }
    private static void totals(State s,long u,long c,long k,long groups,boolean open) throws Exception {
        require(number(s.meter,"units")==u && number(s.meter,"copiedEntries")==c &&
            number(s.meter,"copyChunks")==k && number(s.meter,"groups")==groups &&
            flag(s.meter,"open")==open && number(s.meter,"started")==s.started,"ACTUAL_METER_TOTALS");
    }
    private static void census(State s,long[] units) throws Exception {
        List<?> measures=(List<?>)CohortTerminalTelemetryStudy.field(s.meter.meter,"groupMeasures");
        require(measures.size()==units.length && number(s.meter,"groups")==units.length,"MEASURE_COUNT");
        long sum=0,maxNanos=0,maxUnits=0;
        for (int i=0;i<units.length;i++) {
            long actual=((Number)CohortTerminalTelemetryStudy.field(measures.get(i),"units")).longValue();
            long nanos=((Number)CohortTerminalTelemetryStudy.field(measures.get(i),"nanos")).longValue();
            require(actual==units[i] && nanos>=0 && nanos<=5000000000L,"MEASURE_SEGMENT_"+i);
            sum+=actual; maxNanos=Math.max(maxNanos,nanos); maxUnits=Math.max(maxUnits,actual);
        }
        require(sum==number(s.meter,"units") && maxNanos==number(s.meter,"maxGroupNanos") &&
            maxUnits==number(s.meter,"maxGroupUnits"),"INDEPENDENT_MEASURE_MAXIMA");
    }

    /** Independent observer snapshot: all real native fields and measure element identities. */
    private static final class MeterImage {
        final Object nativeMeter,measureList; final Object[] values,measureObjects;
        final long[] measureUnits,measureNanos;
        final String[] fields={"sessionConstructorEntries","started","units","maxGroupNanos","groupStarted",
            "copiedEntries","copyAllowance","groups","groupUnits","maxGroupUnits","priorGroups","copyChunks",
            "inFlightOwnerGroups","open","originalOwnerStarted","originalOwnerActive","originalOwnerAdopted"};
        MeterImage(CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
            nativeMeter=meter.meter; values=new Object[fields.length];
            for (int i=0;i<fields.length;i++) values[i]=CohortTerminalTelemetryStudy.field(nativeMeter,fields[i]);
            measureList=CohortTerminalTelemetryStudy.field(nativeMeter,"groupMeasures");
            List<?> list=(List<?>)measureList; measureObjects=list.toArray();
            measureUnits=new long[list.size()]; measureNanos=new long[list.size()];
            for (int i=0;i<list.size();i++) {
                measureUnits[i]=((Number)CohortTerminalTelemetryStudy.field(list.get(i),"units")).longValue();
                measureNanos[i]=((Number)CohortTerminalTelemetryStudy.field(list.get(i),"nanos")).longValue();
            }
        }
        long number(String name) {
            for (int i=0;i<fields.length;i++) if (name.equals(fields[i])) return ((Number)values[i]).longValue();
            throw new AssertionError("OBSERVER_FIELD");
        }
        void unchanged(CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
            require(meter.meter==nativeMeter,"OBSERVER_METER_IDENTITY");
            for (int i=0;i<fields.length;i++)
                require(values[i].equals(CohortTerminalTelemetryStudy.field(nativeMeter,fields[i])),"OBSERVER_CHANGED_"+fields[i]);
            require(CohortTerminalTelemetryStudy.field(nativeMeter,"groupMeasures")==measureList,"OBSERVER_MEASURE_LIST");
            List<?> list=(List<?>)measureList; require(list.size()==measureObjects.length,"OBSERVER_MEASURE_SIZE");
            for (int i=0;i<list.size();i++) require(list.get(i)==measureObjects[i] &&
                ((Number)CohortTerminalTelemetryStudy.field(list.get(i),"units")).longValue()==measureUnits[i] &&
                ((Number)CohortTerminalTelemetryStudy.field(list.get(i),"nanos")).longValue()==measureNanos[i],
                "OBSERVER_MEASURE_CHANGED");
        }
    }
    private static void expectTicket(State s,CohortTerminalTelemetryStudy.SetupMeter meter,long started,
            Path path,String code) {
        try { s.ticket.verifyFinal(meter,started,path); throw new AssertionError("TICKET_ACCEPTED_"+code); }
        catch (ReceiptContentBinding.BindingFailure expected) { require(code.equals(expected.code),"TICKET_CODE_"+expected.code); }
    }
    private static void captureMetrics(State s,Map<String,Object> r) throws Exception {
        r.put("pathEntries",Long.valueOf(s.paths)); r.put("hashBlocks",Long.valueOf(s.blocks));
        r.put("normalUnitsOracle",Long.valueOf(s.u)); r.put("normalCopiesOracle",Long.valueOf(s.c));
        r.put("normalChunksOracle",Long.valueOf(s.meter==null?0:23));
        if (s.meter!=null) {
            r.put("units",Long.valueOf(number(s.meter,"units")));
            r.put("copies",Long.valueOf(number(s.meter,"copiedEntries")));
            r.put("chunks",Long.valueOf(number(s.meter,"copyChunks")));
            r.put("groups",Long.valueOf(number(s.meter,"groups")));
            r.put("priorGroups",Long.valueOf(number(s.meter,"priorGroups")));
            r.put("metadataOpen",Boolean.valueOf(flag(s.meter,"open")));
        }
        if (s.body!=null && s.body.get("work") instanceof Map)
            r.put("workSlots",Long.valueOf(((Map<?,?>)s.body.get("work")).size()));
        r.put("finalizedSlots",Long.valueOf(s.finalizedSlots));
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object>[] emptyChildren() {
        Map<String,Object>[] result=(Map<String,Object>[])new Map<?,?>[4];
        for (int i=0;i<4;i++) result[i]=new LinkedHashMap<String,Object>(); return result;
    }
    @SuppressWarnings("unchecked")
    private static Map<String,Object> work(Map<String,Object> body) {
        require(body.get("work") instanceof Map,"WORK_MISSING"); return (Map<String,Object>)body.get("work");
    }
    private static Path emptyRoot(String raw) throws Exception {
        require(java.io.File.separatorChar=='\\',"WINDOWS_REQUIRED"); Path root=Paths.get(raw);
        require(root.isAbsolute() && root.normalize().equals(root) && root.toRealPath().equals(root) &&
            raw.equals(root.toString()) && !Files.isSymbolicLink(root) &&
            Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS),"CANARY_ROOT");
        for (Path p=root;p!=null;p=p.getParent()) identity(p,true);
        requireEmpty(root); return root;
    }
    private static void requireEmpty(Path directory) throws Exception {
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(directory)) {
            require(!entries.iterator().hasNext(),"DIRECTORY_NOT_EMPTY");
        }
    }
    private static CohortTerminalTelemetryStudy.R5FileIdentity identity(Path path,boolean directory) throws Exception {
        return new CohortTerminalTelemetryStudy.R5FileIdentity(path,directory);
    }
    private static void call(CohortTerminalTelemetryStudy.SetupMeter meter,String method) throws Exception {
        meter.method(method,new Class<?>[0]);
    }
    private static long number(CohortTerminalTelemetryStudy.SetupMeter meter,String name) throws Exception {
        return ((Number)CohortTerminalTelemetryStudy.field(meter.meter,name)).longValue();
    }
    private static boolean flag(CohortTerminalTelemetryStudy.SetupMeter meter,String name) throws Exception {
        return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter,name));
    }
    private static void requireCode(IOException error,String code) { require(code.equals(error.getMessage()),"CODE_"+describe(error)); }
    private static void require(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    private static Throwable combine(Throwable first,Throwable next) {
        if (first==null) return next; if (first!=next) first.addSuppressed(next); return first;
    }
    private static String sha256(byte[] value) throws Exception {
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value); StringBuilder out=new StringBuilder();
        for (byte b:digest) out.append("0123456789abcdef".charAt((b&255)>>>4)).append("0123456789abcdef".charAt(b&15));
        return out.toString();
    }
    private static String describe(Throwable failure) {
        String text=failure.getClass().getSimpleName()+":"+String.valueOf(failure.getMessage());
        StringBuilder out=new StringBuilder();
        for (int i=0;i<text.length() && i<240;i++) {
            char c=text.charAt(i); out.append(c>=32 && c<=126?c:'?');
        }
        if (failure.getSuppressed().length!=0) out.append(";suppressed=").append(failure.getSuppressed().length);
        return out.toString();
    }
    /** Bounded test observer/summary serializer, not a production codec or export path. */
    private static String json(Object value) {
        StringBuilder out=new StringBuilder(); write(value,out,new IdentityHashMap<Object,Boolean>(),0);
        require(out.length()<=JSON_LIMIT,"JSON_BOUND"); return out.toString();
    }
    private static void write(Object value,StringBuilder out,IdentityHashMap<Object,Boolean> active,int depth) {
        require(depth<=32 && out.length()<=JSON_LIMIT,"JSON_BOUND");
        if (value==null) out.append("null");
        else if (value instanceof String) {
            out.append('"'); String text=(String)value;
            for (int i=0;i<text.length();i++) {
                char c=text.charAt(i); require(c>=32 && c<=126,"JSON_ASCII");
                if (c=='"' || c=='\\') out.append('\\'); out.append(c);
                require(out.length()<=JSON_LIMIT,"JSON_BOUND");
            }
            out.append('"');
        } else if (value instanceof Integer || value instanceof Long || value instanceof Boolean) out.append(value.toString());
        else {
            require(active.put(value,Boolean.TRUE)==null,"JSON_CYCLE");
            if (value instanceof Map) {
                out.append('{'); boolean first=true;
                for (Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                    require(entry.getKey() instanceof String,"JSON_KEY");
                    if (!first) out.append(','); first=false;
                    write(entry.getKey(),out,active,depth+1); out.append(':'); write(entry.getValue(),out,active,depth+1);
                }
                out.append('}');
            } else if (value instanceof List) {
                out.append('['); boolean first=true;
                for (Object item:(List<?>)value) {
                    if (!first) out.append(','); first=false; write(item,out,active,depth+1);
                }
                out.append(']');
            } else throw new AssertionError("JSON_TYPE");
            active.remove(value);
        }
        require(out.length()<=JSON_LIMIT,"JSON_BOUND");
    }
}
