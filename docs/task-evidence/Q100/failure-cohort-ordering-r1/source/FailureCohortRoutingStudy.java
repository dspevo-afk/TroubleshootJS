package com.lushprojects.circuitjs1.client;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Pattern;

/** Native-only paired pass-2 ordering diagnostic. */
public final class FailureCohortRoutingStudy {
    private static final Charset UTF8=Charset.forName("UTF-8");
    private static final Pattern UUID=Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA=Pattern.compile("[0-9a-f]{64}");
    private static final String OLD_CAPTURE="com.lushprojects.circuitjs1.client.CopperFreeBranchStudy";
    private static final String RESULT_SCHEMA="tsj-failure-cohort-routing-v1";
    private static final String MARKER_SCHEMA="tsj-failure-cohort-marker-v1";
    private static final String CANARY_SCHEMA="tsj-failure-cohort-canary-v1";
    private static final String PREFIX_SHA=
        "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb";
    private static final long ROOT_SEED=9007199254740993L;
    private static final long PLACE_SEED=-6587958367974952701L;
    private static final long ROUTE_SEED=8445301674744508279L;
    private static final int PASS=2,RECEIPT_ORDINAL=307,LINEAGE_ORDINAL=77;
    private static final int MAX_VIAS=48,MAX_GROUPS=640,STEP_LIMIT=32768;
    private static final int COPY_LIMIT=1048576,COPY_CHUNK=4096;
    private static final long GROUP_NANOS=5000000000L,OP_NANOS=90000000000L;
    private static final long CLEANUP_NANOS=120000000000L;
    private static final long RECEIPT_LIMIT=8L*1024L*1024L;
    private static final int RESULT_LINE_LIMIT=1024*1024,RESULT_FILE_LIMIT=8*1024*1024;
    private static final String NET="A_COIL_LOW",START_PAD="QA.C",ROOT_PAD="DA.A";

    private FailureCohortRoutingStudy() { }

    public static void main(String[] args) {
        if(args!=null && args.length==1 && "--canary".equals(args[0])) {
            try { runCanary(); return; }
            catch(Throwable failure) { failure.printStackTrace(System.err); System.exit(1); return; }
        }
        long started=System.nanoTime();
        try {
            Inputs inputs=parseInputs(args);
            Meter meter=new Meter(started);
            Map<String,Object> result=runObserve(inputs,meter,started);
            exportResult(inputs.data,result,started,meter);
        } catch(InputRejected rejected) {
            System.err.print("FAILURE_COHORT_INPUT_REJECTED:"+rejected.code+"\n");
            System.exit(1);
        } catch(Throwable failure) {
            failure.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Inputs parseInputs(String[] args) throws InputRejected {
        if(args==null || args.length!=5 || !"FAILURE_COHORT".equals(args[0]))
            throw new InputRejected("ARGS");
        if(args[1]==null || !UUID.matcher(args[1]).matches()) throw new InputRejected("UUID");
        if(args[2]==null || !SHA.matcher(args[2]).matches()) throw new InputRejected("MANIFEST_SHA");
        if(args[3]==null || !SHA.matcher(args[3]).matches()) throw new InputRejected("WIRE_SHA");
        final Path data;
        try { data=Paths.get(args[4]); }
        catch(RuntimeException invalid) { throw new InputRejected("DATA_PATH"); }
        try {
            Path absolute=data.toAbsolutePath().normalize();
            if(!data.isAbsolute() || !data.normalize().equals(data) ||
                    !Files.isDirectory(data,LinkOption.NOFOLLOW_LINKS) ||
                    Files.isSymbolicLink(data) || !data.toRealPath().equals(absolute))
                throw new InputRejected("DATA_PATH");
            try(DirectoryStream<Path> entries=Files.newDirectoryStream(data)) {
                if(entries.iterator().hasNext()) throw new InputRejected("DATA_NONEMPTY");
            }
        } catch(InputRejected rejected) { throw rejected; }
        catch(IOException | RuntimeException invalid) { throw new InputRejected("DATA_PATH"); }
        return new Inputs(args[1],args[2],args[3],data);
    }

    private static Map<String,Object> runObserve(Inputs inputs,Meter meter,long started)
            throws Exception {
        OriginalArm original=captureOriginal(inputs,meter);
        meter.priorGroups=original.groupCount;
        meter.copiedEntries=original.copiedEntries;
        meter.copyChunks=original.copyChunks;
        if(meter.copiedEntries>COPY_LIMIT) throw new IOException("original copy ledger exceeds its bound");
        CaptureData c=original.captureData;
        meter.beginGroup();
        for(int copy=0;copy<4;copy++) reserveCopy(null,meter,c.netIds.size());
        verifyAuthoredCapture(original,c);
        meter.charge(40L+c.netIds.size());
        meter.finishGroup();
        if(!original.ownersReleased || !original.contextRestored ||
                !boolField(original.legacyCapture,"ownersReleased") ||
                !boolField(original.legacyCapture,"contextRestored"))
            throw new IllegalStateException("actual operation-1 owner/context proof did not close");

        PcbBoardLayout sourceTemplate=(PcbBoardLayout)field(original.legacyCapture,"template");
        TroubleshootBoard board=(TroubleshootBoard)field(original.legacyCapture,"board");
        if(board==null || sourceTemplate==null || layoutCount(sourceTemplate,"traces")!=0 ||
                layoutCount(sourceTemplate,"holes")!=0)
            throw new IllegalStateException("fresh whole-order source is not the original unrouted template");
        meter.beginGroup();
        reserveFingerprintCopies(null,meter,sourceTemplate);
        String sourceBefore=sourceTemplate.geometryFingerprint();
        String sourceShaBefore=hashString(sourceBefore);
        int sourceCopies=layoutEntryCount(sourceTemplate);
        meter.charge(hashUnits(utf8(sourceBefore).length)+7);
        meter.finishGroup();
        if(sourceCopies>COPY_CHUNK)
            throw new IOException("whole fresh-template copy exceeds one 4096-entry copy chunk");

        meter.beginGroup();
        reserveCopy(null,meter,1);
        List<String> controlCohort=new ArrayList<String>(); controlCohort.add(c.failedNetId);
        meter.charge(1); meter.finishGroup();
        FreshRun control=runFresh(board,sourceTemplate,c,controlCohort,
            "OBSERVED_FAILURE_CONTROL",meter,started,null);
        FreshRun trial=null;
        List<String> trialCohort=null,expectedTrial=null;
        Map<String,Object> decision;
        meter.beginGroup(); meter.charge(8);
        if("FOUND".equals(control.disposition)) {
            decision=trialDisposition("SKIPPED","CONTROL_FOUND",c.failedNetId,null,null,null,false);
        } else if(!"NO_PATH".equals(control.disposition)) {
            decision=trialDisposition("SKIPPED","CONTROL_NON_NO_PATH_TERMINAL",c.failedNetId,
                control.terminalFailure==null?null:control.terminalFailure.failedNetId,null,null,false);
        } else {
            if(control.terminalFailure==null || !control.terminalFailure.searchReleased)
                throw new IllegalStateException("NO_PATH control lacks released actual terminal evidence");
            String second=control.terminalFailure.failedNetId;
            meter.charge(4L+2L*c.netIds.size());
            validateFeedbackIds(c.netIds,c.failedNetId,second);
            reserveCopy(null,meter,2);
            trialCohort=new ArrayList<String>(); trialCohort.add(c.failedNetId); trialCohort.add(second);
            reserveCohortCopies(null,meter,c.netIds.size(),trialCohort.size());
            expectedTrial=checkedStableCohort(c.netIds,trialCohort,c.netIds);
            meter.charge(3L*c.netIds.size()+trialCohort.size());
            boolean changed=!expectedTrial.equals(control.requestedNetIds);
            reserveCopy(null,meter,trialCohort.size()); reserveCopy(null,meter,expectedTrial.size());
            decision=trialDisposition(changed?"RUN":"SKIPPED",changed?"TRIAL_EXECUTED":"NO_ORDER_CHANGE",
                c.failedNetId,second,trialCohort,expectedTrial,changed);
        }
        meter.finishGroup();
        if("RUN".equals(decision.get("status")))
            trial=runFresh(board,sourceTemplate,c,trialCohort,
                "OBSERVED_FAILURE_COHORT_TRIAL",meter,started,null);

        meter.beginGroup();
        reserveFingerprintCopies(null,meter,sourceTemplate);
        String sourceAfter=sourceTemplate.geometryFingerprint();
        String sourceShaAfter=hashString(sourceAfter);
        reservePadLandCopies(meter,sourceTemplate);
        String padAfter=oldPadLandJson(board,sourceTemplate);
        meter.charge(hashUnits(utf8(sourceAfter).length)+hashUnits(utf8(padAfter).length)+
            1L+layoutCount(sourceTemplate,"pads"));
        String expectedPad=(String)field(original.legacyCapture,"padLandJson");
        if(!sourceShaBefore.equals(sourceShaAfter) || !sourceShaBefore.equals(c.detachedGeometrySha) ||
                expectedPad==null || !expectedPad.equals(padAfter) ||
                !boolField(original.legacyCapture,"sourceUnchanged"))
            throw new IllegalStateException("original source template changed during fresh attempts");
        meter.finishGroup();

        meter.beginGroup();
        Map<String,Object> result=object();
        result.put("schema",RESULT_SCHEMA); result.put("mode","OBSERVED_FAILURE_COHORT");
        result.put("runId",inputs.runId);
        result.put("finalRunManifestSha256",inputs.manifestSha);
        result.put("wireContractSha256",inputs.wireSha);
        result.put("operation1",original.operation1);
        result.put("reference",original.reference);
        result.put("source",original.source);
        reserveCopy(null,meter,c.netIds.size());
        result.put("capturedOrder",capturedOrderJson(c));
        result.put("control",control.json);
        result.put("trial",trial==null?null:trial.json);
        result.put("trialDisposition",decision);

        GroupStats groups=original.groupStats;
        long ownerUnits=control.ownerUnits+(trial==null?0:trial.ownerUnits);
        long wrapperUnits=control.wrapperUnits+(trial==null?0:trial.wrapperUnits);
        long cleanup=original.cleanupNanos+control.cleanupNanos+(trial==null?0:trial.cleanupNanos);
        if(cleanup<0 || cleanup>CLEANUP_NANOS)
            throw new IOException("original plus fresh-owner cleanup exceeded 120 seconds");
        Map<String,Object> cleanupJson=object();
        cleanupJson.put("originalOwnersReleased",Boolean.valueOf(original.ownersReleased));
        cleanupJson.put("originalContextRestored",Boolean.valueOf(original.contextRestored));
        cleanupJson.put("controlReferencesDropped",Boolean.valueOf(control.referencesDropped));
        cleanupJson.put("trialReferencesDropped",Boolean.valueOf(trial==null || trial.referencesDropped));
        cleanupJson.put("sourceUnchanged",Boolean.TRUE);
        cleanupJson.put("allReferencesDropped",Boolean.valueOf(original.ownersReleased &&
            original.contextRestored && control.referencesDropped && (trial==null || trial.referencesDropped)));
        Map<String,Object> work=object();
        work.put("elapsedCutoffNanos",Long.valueOf(1));
        work.put("cleanupNanos",Long.valueOf(cleanup));
        work.put("groupsBeforeExport",Integer.valueOf(0));
        work.put("wrapperGroupsBeforeExport",Integer.valueOf(0));
        work.put("maxGroupNanosBeforeExport",Long.valueOf(0));
        work.put("maximumUnitsPerGroupBeforeExport",Integer.valueOf(0));
        work.put("unitsBeforeExport",Long.valueOf(0));
        work.put("originalOwnerSteps",Long.valueOf(original.p07OwnerSteps));
        work.put("originalDiagnosticUnits",Long.valueOf(original.diagnosticUnits));
        work.put("originalCaptureUnits",Integer.valueOf(original.callbackUnits));
        work.put("extraTerminalCallbackUnits",Integer.valueOf(c.extraCallbackUnits));
        work.put("controlOwnerUnits",Long.valueOf(control.ownerUnits));
        work.put("controlWrapperUnits",Long.valueOf(control.wrapperUnits));
        work.put("trialOwnerUnits",Long.valueOf(trial==null?0:trial.ownerUnits));
        work.put("trialWrapperUnits",Long.valueOf(trial==null?0:trial.wrapperUnits));
        meter.charge(4);
        work.put("controlFailureObservationUnits",Long.valueOf(control.failureObservationUnits));
        work.put("trialFailureObservationUnits",Long.valueOf(trial==null?0:trial.failureObservationUnits));
        work.put("controlTerminalEvidenceUnits",Long.valueOf(control.terminalEvidenceUnits));
        work.put("trialTerminalEvidenceUnits",Long.valueOf(trial==null?0:trial.terminalEvidenceUnits));
        work.put("metadataUnits",Integer.valueOf(0));
        work.put("copiedEntries",Long.valueOf(meter.copiedEntries));
        work.put("copyChunks",Integer.valueOf(meter.copyChunks));
        work.put("copyChunkLimit",Integer.valueOf(COPY_CHUNK));
        work.put("copiedEntryLimit",Integer.valueOf(COPY_LIMIT));
        work.put("cleanup",cleanupJson);
        result.put("work",work);
        meter.charge(64L+c.netIds.size()*2L);
        chargeResultTree(result,meter,0,new long[] {0});
        meter.finishGroup();
        int runGroups=control.groups+(trial==null?0:trial.groups);
        int groupCount=original.groupCount+meter.groups;
        int wrapperGroups=groupCount-original.groupCount-runGroups;
        long maxGroup=Math.max(original.maxGroupNanos,meter.maxGroupNanos);
        int maxUnits=Math.max(groups.maxUnits,meter.maxGroupUnits);
        long newRunUnits=ownerUnits+wrapperUnits;
        long measuredMetadata=meter.units-newRunUnits;
        if(measuredMetadata<0 || measuredMetadata>Integer.MAX_VALUE)
            throw new IOException("new wrapper unit ledger does not reconcile");
        int metadataUnits=(int)measuredMetadata;
        long units=original.p07OwnerSteps+original.diagnosticUnits+original.callbackUnits+
            c.extraCallbackUnits+newRunUnits+metadataUnits;
        long copiedEntries=meter.copiedEntries;
        if(groupCount+1>MAX_GROUPS || maxGroup>GROUP_NANOS || maxUnits>STEP_LIMIT ||
                units!=original.p07OwnerSteps+original.diagnosticUnits+original.callbackUnits+
                    c.extraCallbackUnits+meter.units || copiedEntries>COPY_LIMIT)
            throw new IOException("combined original/new work ledger exceeded a frozen bound");
        work.put("elapsedCutoffNanos",Long.valueOf(Math.max(1,elapsed(started,System.nanoTime()))));
        work.put("groupsBeforeExport",Integer.valueOf(groupCount));
        work.put("wrapperGroupsBeforeExport",Integer.valueOf(wrapperGroups));
        work.put("maxGroupNanosBeforeExport",Long.valueOf(maxGroup));
        work.put("maximumUnitsPerGroupBeforeExport",Integer.valueOf(maxUnits));
        work.put("unitsBeforeExport",Long.valueOf(units));
        work.put("metadataUnits",Integer.valueOf(metadataUnits));
        work.put("copiedEntries",Long.valueOf(copiedEntries));
        work.put("copyChunks",Integer.valueOf(meter.copyChunks));
        return result;
    }

    private static OriginalArm captureOriginal(Inputs inputs,Meter meter) throws Exception {
        Object study=null,armRun=null,armResult=null,legacyCapture=null,decorator=null;
        CaptureData data=new CaptureData();
        OriginalArm built=null;
        long armStart=System.nanoTime();
        try {
            meter.beginGroup();
            Class<?> captureType=Class.forName(OLD_CAPTURE+"$Capture");
            Constructor<?> cc=captureType.getDeclaredConstructor(); cc.setAccessible(true);
            legacyCapture=cc.newInstance();
            decorator=new CompositeInspector(legacyCapture,data);
            Class<?> studyType=Class.forName(
                "com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy$Study");
            Class<?> modeType=Class.forName("com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy$Mode");
            Object authored=enumValue(modeType,"AUTHORED");
            Constructor<?> sc=studyType.getDeclaredConstructor(modeType,String.class,String.class,
                String.class,Path.class);
            sc.setAccessible(true);
            study=sc.newInstance(authored,inputs.runId,inputs.manifestSha,inputs.wireSha,inputs.data);
            Class<?> armType=Class.forName("com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy$ArmRun");
            Constructor<?> ac=armType.getDeclaredConstructor(studyType,int.class,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
                ActualGeneratorBranchOrderStudy.TerminalPrefixInspector.class);
            ac.setAccessible(true);
            armRun=ac.newInstance(study,Integer.valueOf(1),null,decorator);
            meter.charge(8);
            meter.finishGroup();
            Method execute=armType.getDeclaredMethod("execute"); execute.setAccessible(true);
            armResult=invoke(execute,armRun);
            long armElapsed=elapsed(armStart,System.nanoTime());
            if(armElapsed<=0 || armElapsed>OP_NANOS)
                throw new IOException("actual operation-1 callback/owner exceeded 90 seconds");
            if(data.callbackCount!=1)
                throw new IllegalStateException("actual selected pass-2 terminal callback was not captured once");
            built=buildOriginalArm(inputs,meter,legacyCapture,armRun,armResult,armType,captureType,data);
            return built;
        } finally {
            long releaseStarted=System.nanoTime();
            armResult=null; armRun=null; study=null; decorator=null;
            if(built!=null) built.cleanupNanos+=elapsed(releaseStarted,System.nanoTime());
        }
    }

    private static OriginalArm buildOriginalArm(Inputs inputs,Meter meter,Object legacyCapture,
            Object armRun,Object armResult,Class<?> armType,Class<?> captureType,CaptureData c)
            throws Exception {
        Path receipt=inputs.data.resolve("op-1.jsonl");
        meter.beginGroup();
        Digest raw=fileDigest(receipt,meter);
        long resultBytes=longField(armResult,"receiptBytes");
        String resultSha=stringField(armResult,"receiptSha256");
        if(raw.bytes!=resultBytes || !raw.sha.equals(resultSha) || raw.bytes>RECEIPT_LIMIT)
            throw new IOException("fresh operation-1 receipt differs from its actual ArmResult or byte cap");
        int groupCount=intField(armRun,"nextGroupOrdinal");
        Object request=field(armRun,"request"),header=field(armRun,"candidateHeader");
        if(request==null || header==null) throw new IllegalStateException("operation-1 omitted owner identity");
        Object context=field(request,"context"),receiptObject=field(request,"receipt");
        if(context==null || receiptObject==null) throw new IllegalStateException("operation-1 omitted request context/receipt");
        int requestIndex=intField(context,"requestIndex"),selected=intField(context,"selectedAttempt");
        int receiptAttempt=intField(receiptObject,"attempt");
        long root=longField(header,"rootSeed"),placement=longField(header,"placementSeed"),routing=longField(header,"routingSeed");
        long ownerSteps=longField(request,"p07OwnerSteps");
        if(requestIndex!=0 || selected!=4 || receiptAttempt!=4 || root!=ROOT_SEED ||
                placement!=PLACE_SEED || routing!=ROUTE_SEED)
            throw new IllegalStateException("actual operation-1 request identity differs from frozen authored capture");
        GroupStats gs=readOriginalGroups(receipt,groupCount,c.groupOrdinal,
            intField(legacyCapture,"callbackUnits"),c.extraCallbackUnits,meter);
        if(gs.p07Steps!=ownerSteps)
            throw new IllegalStateException("actual operation-1 GROUP steps do not reconcile with request owner steps");
        meter.copiedEntries=c.copiedEntries;
        meter.copyChunks=c.copyChunks;
        Class<?> oldArmType=Class.forName(OLD_CAPTURE+"$Arm");
        Constructor<?> oldArmConstructor=oldArmType.getDeclaredConstructor(long.class,String.class,
            long.class,long.class,long.class,boolean.class,boolean.class,int.class,long.class,
            int.class,int.class,int.class,int.class,long.class,long.class,long.class);
        oldArmConstructor.setAccessible(true);
        Object oldArm=oldArmConstructor.newInstance(Long.valueOf(raw.bytes),raw.sha,
            Long.valueOf(longField(armResult,"elapsedToCloseNanos")),
            Long.valueOf(longField(armResult,"maxFullGroupNanos")),
            Long.valueOf(longField(armResult,"cleanupNanos")),
            Boolean.valueOf(boolField(armResult,"ownersReleased")),
            Boolean.valueOf(boolField(armResult,"contextRestored")),Integer.valueOf(groupCount),
            Long.valueOf(ownerSteps),Integer.valueOf(gs.maxUnits),Integer.valueOf(requestIndex),
            Integer.valueOf(selected),Integer.valueOf(receiptAttempt),Long.valueOf(root),
            Long.valueOf(placement),Long.valueOf(routing));
        Map<String,Object> operation1=oldMap("armJson",
            new Class<?>[] {String.class,String.class,String.class,oldArmType,captureType},
            inputs.runId,inputs.manifestSha,inputs.wireSha,oldArm,legacyCapture);
        reserveCopy(null,meter,collectionSize(field(legacyCapture,"reached"),"captured reached"));
        reserveCopy(null,meter,collectionSize(field(legacyCapture,"reachedPth"),"captured reached PTH"));
        Map<String,Object> reference=oldMap("referenceJson",new Class<?>[] {captureType},legacyCapture);
        Object template=field(legacyCapture,"template");
        if(!(template instanceof PcbBoardLayout)) throw new IllegalStateException("old Capture omitted its detached template");
        reserveFingerprintCopies(null,meter,(PcbBoardLayout)template);
        String detachedAfter=oldString("hashFingerprint",new Class<?>[] {PcbBoardLayout.class},template);
        Map<String,Object> source=oldMap("sourceJson",new Class<?>[] {captureType,String.class},legacyCapture,detachedAfter);
        meter.charge(2);
        meter.finishGroup();
        OriginalArm result=new OriginalArm();
        result.legacyCapture=legacyCapture; result.operation1=operation1; result.reference=reference;
        result.source=source; result.captureData=c; result.rawBytes=raw.bytes; result.rawSha=raw.sha;
        result.groupCount=groupCount; result.groupStats=gs; result.cleanupNanos=longField(armResult,"cleanupNanos");
        result.maxGroupNanos=longField(armResult,"maxFullGroupNanos");
        result.ownersReleased=boolField(armResult,"ownersReleased");
        result.contextRestored=boolField(armResult,"contextRestored");
        result.p07OwnerSteps=ownerSteps; result.diagnosticUnits=gs.diagnosticUnits;
        result.callbackUnits=intField(legacyCapture,"callbackUnits");
        result.copiedEntries=(int)meter.copiedEntries;
        result.copyChunks=meter.copyChunks;
        return result;
    }

    private static GroupStats readOriginalGroups(Path receipt,int expectedGroups,int callbackGroup,
            int oldCallbackUnits,int extraCallbackUnits,Meter meter) throws IOException {
        if(expectedGroups<1 || expectedGroups>MAX_GROUPS || callbackGroup<0 ||
                callbackGroup>=expectedGroups || oldCallbackUnits<0 || extraCallbackUnits<0)
            throw new IOException("original callback GROUP accounting is out of range");
        boolean[] seen=new boolean[expectedGroups];
        GroupStats result=new GroupStats();
        try(BufferedReader reader=Files.newBufferedReader(receipt,UTF8)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.length()>65536) throw new IOException("operation-1 JSONL line exceeded 64 KiB");
                int bytes=utf8(line).length+1;
                if(bytes>65536) throw new IOException("operation-1 JSONL byte line exceeded 64 KiB");
                meter.charge(Math.max(1,(bytes+4095)/4096));
                if(!line.contains("\"record\":\"GROUP\"")) continue;
                long operation=jsonLongField(line,"operation",false);
                long ordinal=jsonLongField(line,"ordinal",false);
                long p07=jsonLongField(line,"p07Steps",true);
                long diagnostic=jsonLongField(line,"diagnosticUnits",false);
                if(operation!=1 || ordinal<0 || ordinal>=expectedGroups || seen[(int)ordinal] ||
                        diagnostic<0 || diagnostic>STEP_LIMIT || p07< -1 || p07>STEP_LIMIT)
                    throw new IOException("actual operation-1 GROUP row is invalid or duplicated");
                seen[(int)ordinal]=true; result.groups++;
                long owner=p07<0?0:p07;
                result.p07Steps+=owner; result.diagnosticUnits+=diagnostic;
                long combined=owner+diagnostic;
                if(ordinal==callbackGroup) combined+=oldCallbackUnits+extraCallbackUnits;
                if(combined>STEP_LIMIT) throw new IOException("actual callback plus P07 group exceeded 32768 units");
                result.maxUnits=Math.max(result.maxUnits,(int)combined);
                meter.charge(1);
            }
        }
        if(result.groups!=expectedGroups) throw new IOException("actual operation-1 GROUP count differs from ArmRun");
        for(boolean value:seen) if(!value) throw new IOException("operation-1 GROUP ordinals are incomplete");
        return result;
    }

    private static void verifyAuthoredCapture(OriginalArm original,CaptureData c) throws Exception {
        Object capture=original.legacyCapture;
        if(intField(capture,"count")!=1 || intField(capture,"receiptOrdinal")!=RECEIPT_ORDINAL ||
                intField(capture,"lineageOrdinal")!=LINEAGE_ORDINAL || intField(capture,"pass")!=PASS ||
                !NET.equals(stringField(capture,"net")) || !START_PAD.equals(stringField(capture,"start")) ||
                !ROOT_PAD.equals(stringField(capture,"root")) ||
                !"NO_PATH".equals(stringField(capture,"terminal")) ||
                !PREFIX_SHA.equals(stringField(capture,"prefixSha")) ||
                intField(capture,"prefixHoleCount")!=48 || intField(capture,"prefixViaCount")!=48 ||
                !c.failedNetId.equals(NET) || !c.netIds.contains(c.failedNetId) ||
                c.ownerPass!=PASS || c.searchPass!=PASS || !c.roleWidths ||
                c.maximumExpansions<1 || c.expandedAtFailure>c.maximumExpansions ||
                c.maximumVias!=48 || c.policy!=PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER ||
                c.policyTransitions!=4 || c.policyVias!=48 || c.viaCost!=c.policy.viaCost ||
                c.netIds.size()!=new HashSet<String>(c.netIds).size())
            throw new IllegalStateException("actual failed pass-2 capture differs from frozen request");
        Vector<String> boardNets=c.board.getNetIds();
        if(boardNets.size()>COPY_CHUNK)
            throw new IllegalStateException("board net set exceeds the independent comparison copy bound");
        if(!new TreeSet<String>(c.netIds).equals(new TreeSet<String>(boardNets)))
            throw new IllegalStateException("captured pass-2 net vector is not the complete board net set");
        if(!original.ownersReleased || !original.contextRestored || !c.sourceUnchanged)
            throw new IllegalStateException("original operation-1 cleanup/source proof is missing");
    }

    private static FreshRun runFresh(TroubleshootBoard board,PcbBoardLayout template,
            CaptureData captured,List<String> cohort,String kind,Meter meter,long processStarted,
            PcbLayerRoutingPrototype.Diagnostics diagnostics) throws Exception {
        FreshRun out=new FreshRun();
        out.kind=kind;
        Object session=null,search=null,beforeSearch=null,routeResult=null,candidate=null;
        PcbBoardLayout returnedLayout=null;
        ArrayList<String> traversed=new ArrayList<String>();
        String lastCurrent=null;
        int searchCount=0,advanceCalls=0;
        long cleanupStart=0;
        boolean dropped=false;
        int startGroupIndex=meter.groupMeasures.size();
        boolean candidateCopyCounted=false,searchArraysCounted=false;
        try {
            int templateEntries=layoutEntryCount(template);
            if(templateEntries>COPY_CHUNK)
                throw new IOException("whole Session template copy exceeds one 4096-entry chunk");
            meter.beforeOperation();
            meter.reserve(templateEntries+128L);
            chargeRunWrapper(out,meter,1);
            reserveCopy(out,meter,templateEntries);
            chargeRunWrapper(out,meter,1); // Actual fresh-constructor entry ledger update.
            session=newFreshSession(board,template,captured,diagnostics,meter);
            if(diagnostics instanceof FaultDiagnostics)
                ((FaultDiagnostics)diagnostics).state.session=session;
            requireFreshInitial(session,template,captured);
            setIntField(session,"pass",PASS);
            setIntField(session,"passes",0);
            setField(session,"previousFailedNet",captured==null?null:captured.priorPromotedNetId);
            chargeRunWrapper(out,meter,1);

            boolean orderPrepared=false;
            while(!boolField(session,"complete")) {
                // One fixed, bounded wrapper-state polling/dispatch operation;
                // the actual owner work is charged separately from lastWork().
                meter.reserve(orderPrepared?COPY_CHUNK:5L*COPY_CHUNK+64L);
                long attemptOwnerStart=out.ownerUnits,attemptWrapperStart=out.wrapperUnits;
                chargeRunWrapper(out,meter,1);
                chargeRunWrapper(out,meter,1); out.failureObservationUnits++;
                advanceCalls++;
                if(elapsed(processStarted,System.nanoTime())>OP_NANOS)
                    throw new IOException("fresh paired P07 owner exceeded the 90-second combined operation clock");
                beforeSearch=field(session,"search");
                int passBefore=intField(session,"pass");
                try {
                if(!candidateCopyCounted) {
                    // Session creates this second layout and Search copies these arrays
                    // inside the next owner call; reserve each before that call.
                    reserveCopy(out,meter,templateEntries);
                    reserveCopy(out,meter,layoutCount(template,"pads"));
                    reserveCopy(out,meter,layoutCount(template,"components"));
                    candidateCopyCounted=true;
                }
                boolean done=false;
                Throwable ownerFailure=null;
                try { done=((PcbLayerRoutingPrototype.Session)session).advanceSlice(1); }
                catch(Throwable failure) { ownerFailure=failure; }
                boolean attemptAccounted=false;
                int last=((PcbLayerRoutingPrototype.Session)session).lastWork();
                try {
                    if(last>0) { meter.charge(last); out.ownerUnits+=last; }
                    else chargeRunWrapper(out,meter,1);
                    attemptAccounted=true;
                } catch(Throwable accountingFailure) {
                    if(ownerFailure!=null) ownerFailure.addSuppressed(accountingFailure);
                    else rethrow(accountingFailure);
                }
                if(ownerFailure!=null) {
                    if(diagnostics instanceof FaultDiagnostics) {
                        FaultDiagnostics fault=(FaultDiagnostics)diagnostics;
                        try { chargeRunWrapper(out,meter,2); fault.recordAbort(session,ownerFailure); }
                        catch(Throwable secondary) { ownerFailure.addSuppressed(secondary); attemptAccounted=false; }
                        fault.state.failedAdvanceCalls++;
                        fault.state.failedFailureObservationUnits=1;
                        fault.state.failedTerminalEvidenceUnits=0;
                        fault.state.failedOwnerUnits=out.ownerUnits-attemptOwnerStart;
                        fault.state.failedWrapperUnits=out.wrapperUnits-attemptWrapperStart;
                        fault.state.attemptWorkAccounted=attemptAccounted;
                    }
                    rethrow(ownerFailure);
                }
                search=field(session,"search");
                if(beforeSearch==null && search!=null) searchCount++;
                if(beforeSearch!=null && search!=null && beforeSearch!=search)
                    throw new IllegalStateException("native Search identity changed without an ordering boundary");
                if(search!=null) {
                    Object current=field(search,"currentNet");
                    candidate=(PcbBoardLayout)field(session,"candidate");
                    if(current instanceof String && !((String)current).equals(lastCurrent)) {
                        String id=(String)current;
                        reserveCopy(out,meter,1);
                        traversed.add(id); lastCurrent=id;
                        chargeRunWrapper(out,meter,1);
                    }
                    if(!orderPrepared && field(search,"nets")!=null) {
                        chargeRunWrapper(out,meter,10L);
                        verifyPristineSearchStart(session,search,template,captured,meter,out);
                        Vector<?> nativeNetVector=(Vector<?>)field(search,"nets");
                        if(nativeNetVector.size()>COPY_CHUNK)
                            throw new IOException("native Search net vector exceeds one copy chunk");
                        reserveCopy(out,meter,nativeNetVector.size());
                        Vector<String> computed=copyStringVector(field(search,"nets"));
                        out.computedNetIds=computed;
                        if(captured!=null && !computed.equals(captured.netIds))
                            throw new IllegalStateException("fresh native pass-2 vector differs from captured actual vector");
                        out.capturedNetIds=captured==null?computed:captured.netIds;
                        Object borrowedNets=field(board,"nets");
                        if(!(borrowedNets instanceof Map))
                            throw new IllegalStateException("board net census is not its actual map");
                        int netCount=collectionSize(borrowedNets,"board net membership");
                        if(netCount>COPY_CHUNK) throw new IOException("board net census exceeds copy chunk");
                        reserveCopy(out,meter,netCount);
                        List<String> known=new ArrayList<String>();
                        for(Object key:((Map<?,?>)borrowedNets).keySet()) {
                            chargeRunWrapper(out,meter,1);
                            if(!(key instanceof String))
                                throw new IllegalStateException("board net identity is not a string");
                            known.add((String)key);
                        }
                        borrowedNets=null;
                        reserveCohortCopies(out,meter,computed.size(),cohort==null?0:cohort.size());
                        List<String> expect=checkedStableCohort(computed,cohort,known);
                        chargeRunWrapper(out,meter,3L*computed.size()+(cohort==null?0:cohort.size())+2);
                        @SuppressWarnings("unchecked") Vector<String> nativeVector=(Vector<String>)field(search,"nets");
                        reserveCopy(out,meter,expect.size()); nativeVector.clear(); nativeVector.addAll(expect);
                        chargeRunWrapper(out,meter,1);
                        reserveCopy(out,meter,nativeVector.size());
                        if(!copyStringVector(nativeVector).equals(expect))
                            throw new IllegalStateException("requested cohort vector replacement did not take effect");
                        reserveCopy(out,meter,cohort.size()); out.requestedCohortNetIds=new ArrayList<String>(cohort);
                        reserveCopy(out,meter,expect.size()); out.requestedNetIds=new ArrayList<String>(expect);
                        out.orderMatchesCapture=captured==null || computed.equals(captured.netIds);
                        orderPrepared=true;
                    }
                    if(field(session,"candidate")!=null && !searchArraysCounted) {
                        int candidateEntries=layoutEntryCount((PcbBoardLayout)field(session,"candidate"));
                        if(candidateEntries>COPY_CHUNK || candidateEntries!=templateEntries)
                            throw new IOException("whole pass-2 candidate copy exceeds one 4096-entry chunk");
                    }
                    if(!searchArraysCounted) {
                        int padCount=physicalCollectionCount(search,"pads");
                        int componentCount=physicalCollectionCount(search,"parts");
                        if(padCount>COPY_CHUNK || componentCount>COPY_CHUNK ||
                                padCount!=layoutCount(template,"pads") ||
                                componentCount!=layoutCount(template,"components"))
                            throw new IOException("native Search arrays differ from the prebounded source or copy cap");
                        searchArraysCounted=true;
                    }
                }
                if(beforeSearch!=null && search==null && passBefore==PASS && intField(session,"pass")==3) {
                    if(out.terminalFailure!=null) throw new IllegalStateException("multiple terminal Search observations");
                    chargeRunWrapper(out,meter,1);
                    Object retiredCurrent=field(beforeSearch,"currentNet");
                    if(retiredCurrent instanceof String && !retiredCurrent.equals(lastCurrent)) {
                        reserveCopy(out,meter,1); traversed.add((String)retiredCurrent);
                        lastCurrent=(String)retiredCurrent; chargeRunWrapper(out,meter,1);
                    }
                    out.terminalFailure=captureTerminal(session,beforeSearch,passBefore,advanceCalls,
                        out.requestedNetIds,traversed,out,meter);
                    beforeSearch=null;
                    if(out.terminalFailure!=null) {
                        terminalEvidenceCharge(out,meter,1);
                        out.terminalFailure.searchReleased=beforeSearch==null && search==null;
                    }
                }
                if(done && !boolField(session,"complete"))
                    throw new IllegalStateException("P07 advanceSlice reported terminal before owner completion");
                if(done) break;
                } finally { beforeSearch=null; search=null; }
            }
            if(!orderPrepared || searchCount!=1)
                throw new IllegalStateException("fresh P07 pass 2 did not initialize exactly one native Search");
            routeResult=((PcbLayerRoutingPrototype.Session)session).result();
            String reason=stringField(routeResult,"outcome");
            returnedLayout=(PcbBoardLayout)field(routeResult,"layout");
            boolean success="SUCCESS".equals(reason) && returnedLayout!=null;
            out.disposition=success?"FOUND":classifyDisposition(reason);
            out.reason=reason;
            if(out.terminalFailure!=null) {
                terminalEvidenceCharge(out,meter,1);
                if(!out.terminalFailure.ownerReason.equals(reason))
                    throw new IllegalStateException("terminal owner reason differs from actual outcome");
                if("OBSERVED_FAILURE_CONTROL".equals(kind) && "NO_PATH".equals(out.disposition)) {
                    terminalEvidenceCharge(out,meter,1);
                    if(!reason.equals(out.terminalFailure.failedNetId+":NO_PATH"))
                        throw new IllegalStateException("control NO_PATH eligibility reason mismatch");
                }
            } else if("NO_PATH".equals(out.disposition))
                throw new IllegalStateException("NO_PATH owner lacks actual terminal Search proof");
            if(success && out.terminalFailure!=null)
                throw new IllegalStateException("FOUND owner has terminal failure evidence");
            long expectedEvidence=out.terminalFailure==null?0:11L+traversed.size()+
                ("OBSERVED_FAILURE_CONTROL".equals(kind) && "NO_PATH".equals(out.disposition)?1:0);
            if(out.failureObservationUnits!=advanceCalls || out.terminalEvidenceUnits!=expectedEvidence ||
                    out.wrapperUnits<advanceCalls+out.failureObservationUnits+out.terminalEvidenceUnits)
                throw new IOException("fresh failure-observation work does not reconcile");
            out.ownerComplete=boolField(session,"complete");
            out.complete=success;
            out.finalPass=intField(session,"pass"); out.finalPasses=intField(session,"passes");
            out.expanded=((PcbLayerRoutingPrototype.Session)session).expansions();
            out.maximumExpansions=intField(field(session,"budget"),"maximum");
            if(out.expanded<0 || out.expanded>out.maximumExpansions)
                throw new IOException("fresh P07 owner expansion count exceeded its captured maximum");
            out.searchCount=searchCount; out.advanceCalls=advanceCalls;
            out.traversedNetIds=traversed;
            out.priorPromotedNetId=captured==null?null:captured.priorPromotedNetId;
            out.maximumVias=intField(session,"maximumVias");
            PcbLayerRoutingPrototype.Policy actualPolicy=(PcbLayerRoutingPrototype.Policy)field(session,"policy");
            out.maximumTransitions=actualPolicy.transitions; out.policyVias=actualPolicy.vias;
            out.policy=((PcbLayerRoutingPrototype.Policy)field(session,"policy")).name();
            out.viaCost=intField(session,"viaCost");
            out.branchStrategy=((PcbLayerRoutingPrototype.BranchOrderStrategy)
                field(session,"branchOrderStrategy")).name();
            out.netStrategy=((PcbLayerRoutingPrototype.NetOrderStrategy)
                field(session,"netOrderStrategy")).name();
            out.roleWidths=boolField(session,"roleWidths");
            out.capturedOrderingStream=captured!=null && captured.orderingStream;
            out.instrumentationModeDifference=out.capturedOrderingStream;
            chargeRunWrapper(out,meter,14);
            out.routingSettingsMatch=captured==null || sameRoutingSettings(session,captured);
            if(!out.routingSettingsMatch)
                throw new IllegalStateException("fresh owner routing settings differ from captured actual request");
            if(out.finalPass!=3 && !success)
                throw new IllegalStateException("failed pass-2 owner did not terminate at ORDERINGS without fallback");
            if(out.finalPasses!=1)
                throw new IllegalStateException("fresh pass-2 request created more or fewer than one ordering");
            if(success) {
                out.finalizers=finalizers("PASS","PASS","PASS","PASS","PASS");
                out.layout=physicalLayout(board,returnedLayout,meter,out);
            } else if("QUALITY_LIMIT".equals(reason)) {
                out.finalizers=finalizers("PASS","PASS","PASS","REJECTED","NOT_RUN");
            } else {
                out.finalizers=finalizers("NOT_RUN","NOT_RUN","NOT_RUN","NOT_RUN","NOT_RUN");
            }
            meter.beginGroup();
            out.groups=meter.groupMeasures.size()-startGroupIndex+1;
            for(int i=startGroupIndex;i<meter.groupMeasures.size();i++) {
                GroupMeasure measure=meter.groupMeasures.get(i);
                out.maxGroupNanos=Math.max(out.maxGroupNanos,measure.nanos);
                out.maximumUnitsPerGroup=Math.max(out.maximumUnitsPerGroup,measure.units);
            }
            chargeRunWrapper(out,meter,24L+out.traversedNetIds.size()+4L*out.groups);
            reserveCopy(out,meter,out.capturedNetIds.size());
            reserveCopy(out,meter,out.computedNetIds.size());
            reserveCopy(out,meter,out.requestedCohortNetIds.size());
            reserveCopy(out,meter,out.requestedNetIds.size());
            if(out.terminalFailure!=null) reserveCopy(out,meter,10);
            reserveCopy(out,meter,out.traversedNetIds.size());
            out.json=runJson(out);
            meter.finishGroup();
            out.groups=meter.groupMeasures.size()-startGroupIndex;
            GroupMeasure summaryGroup=meter.groupMeasures.get(meter.groupMeasures.size()-1);
            out.maxGroupNanos=Math.max(out.maxGroupNanos,summaryGroup.nanos);
            out.maximumUnitsPerGroup=Math.max(out.maximumUnitsPerGroup,summaryGroup.units);
            @SuppressWarnings("unchecked") Map<String,Object> jsonWork=(Map<String,Object>)out.json.get("work");
            jsonWork.put("groups",Integer.valueOf(out.groups));
            jsonWork.put("maxGroupNanos",Long.valueOf(out.maxGroupNanos));
            jsonWork.put("maximumUnitsPerGroup",Integer.valueOf(out.maximumUnitsPerGroup));
        } finally {
            cleanupStart=System.nanoTime();
            search=null; beforeSearch=null; candidate=null; routeResult=null; returnedLayout=null;
            session=null; board=null; template=null;

            if(diagnostics instanceof FaultDiagnostics) {
                FaultDiagnostics.State state=((FaultDiagnostics)diagnostics).state;
                state.session=null;
                state.adapterSearchAliasesReleased=beforeSearch==null && search==null;
                state.recordRelease(session==null,search==null && beforeSearch==null,
                    candidate==null,returnedLayout==null && routeResult==null,
                    board==null && template==null,elapsed(cleanupStart,System.nanoTime()));
            }
            out.cleanupNanos=elapsed(cleanupStart,System.nanoTime());
            dropped=session==null && search==null && beforeSearch==null && candidate==null && routeResult==null && returnedLayout==null && board==null && template==null;
            out.referencesDropped=dropped;
        }
        if(!out.referencesDropped) throw new IllegalStateException("fresh Session/Search references did not drop");
        return out;
    }

    private static Object newFreshSession(TroubleshootBoard board,PcbBoardLayout template,
            CaptureData captured,PcbLayerRoutingPrototype.Diagnostics diagnostics,Meter meter) throws Exception {
        PcbLayerRoutingPrototype.Policy policy=captured==null?
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER:captured.policy;
        int maximum=captured==null?PcbLayerRoutingPrototype.MAX_EXPANSIONS:captured.maximumExpansions;
        int maximumVias=captured==null?policy.vias:captured.maximumVias;
        PcbLayerRoutingPrototype.BranchOrderStrategy branch=captured==null?
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST:
                PcbLayerRoutingPrototype.BranchOrderStrategy.valueOf(captured.branchStrategy);
        PcbLayerRoutingPrototype.NetOrderStrategy net=captured==null?
            PcbLayerRoutingPrototype.NetOrderStrategy.CANONICAL:
                PcbLayerRoutingPrototype.NetOrderStrategy.valueOf(captured.netStrategy);
        SeededPcbLayoutGenerator.AttemptObserver observer=new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };
        Constructor<?> constructor=PcbLayerRoutingPrototype.Session.class.getDeclaredConstructor(
            TroubleshootBoard.class,PcbBoardLayout.class,PcbLayerRoutingPrototype.Policy.class,
            SeededPcbLayoutGenerator.AttemptObserver.class,int.class,int.class,
            PcbLayerRoutingPrototype.Diagnostics.class,boolean.class,
            PcbLayerRoutingPrototype.FixedPrefixCaptureContext.class,
            PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
            PcbLayerRoutingPrototype.BranchOrderStrategy.class,
            PcbLayerRoutingPrototype.NetOrderStrategy.class);
        constructor.setAccessible(true);
        meter.sessionConstructorEntries++;
        Object session=constructor.newInstance(board,template,policy,observer,Integer.valueOf(maximum),
            Integer.valueOf(maximumVias),diagnostics,Boolean.FALSE,null,null,branch,net);
        return session;
    }

    private static void requireFreshInitial(Object session,PcbBoardLayout template,CaptureData captured)
            throws Exception {
        Object budget=field(session,"budget");
        if(intField(session,"pass")!=0 || intField(session,"passes")!=0 ||
                intField(budget,"expanded")!=0 || field(session,"candidate")!=null ||
                field(session,"search")!=null || field(session,"result")!=null ||
                boolField(session,"complete") || !"FULLER_TWO_LAYER".equals(
                    ((PcbLayerRoutingPrototype.Policy)field(session,"policy")).name()) ||
                !template.getTraces().isEmpty() || !template.getHoles().isEmpty())
            throw new IllegalStateException("fresh Session did not begin with the captured zero-copper request state");
        if(captured!=null && (intField(budget,"maximum")!=captured.maximumExpansions ||
                intField(session,"maximumVias")!=captured.maximumVias ||
                intField(session,"viaCost")!=captured.viaCost ||
                field(session,"policy")!=captured.policy ||
                !((Enum<?>)field(session,"branchOrderStrategy")).name().equals(captured.branchStrategy) ||
                !((Enum<?>)field(session,"netOrderStrategy")).name().equals(captured.netStrategy) ||
                boolField(session,"roleWidths")!=captured.roleWidths))
            throw new IllegalStateException("fresh Session changed the captured request settings");
    }

    private static void verifyPristineSearchStart(Object session,Object search,
            PcbBoardLayout template,CaptureData captured,Meter meter,FreshRun run) throws Exception {
        if(field(session,"search")!=search || intField(session,"pass")!=PASS ||
                intField(search,"pass")!=PASS || intField(session,"passes")!=1 ||
                intField(search,"netIndex")!=0 || field(search,"currentNet")!=null ||
                field(search,"currentPadIds")!=null || field(search,"currentRoot")!=null ||
                field(search,"reachedPadIds")!=null || field(search,"branch")!=null ||
                field(search,"publisher")!=null || field(search,"netOrderPreparation")!=null ||
                boolField(search,"complete") || field(search,"nets")==null)
            throw new IllegalStateException("pass-2 Search was not at the verified pre-net boundary");
        Object candidate=field(session,"candidate");
        if(!(candidate instanceof PcbBoardLayout) ||
                !((PcbBoardLayout)candidate).getTraces().isEmpty() ||
                !((PcbBoardLayout)candidate).getHoles().isEmpty())
            throw new IllegalStateException("native candidate changed before net traversal began");
        reserveFingerprintCopies(run,meter,(PcbBoardLayout)candidate);
        String candidateFingerprint=((PcbBoardLayout)candidate).geometryFingerprint();
        chargeRunWrapper(run,meter,hashUnits(utf8(candidateFingerprint).length));
        reserveFingerprintCopies(run,meter,template);
        String templateFingerprint=template.geometryFingerprint();
        chargeRunWrapper(run,meter,hashUnits(utf8(templateFingerprint).length));
        if(!candidateFingerprint.equals(templateFingerprint))
            throw new IllegalStateException("native candidate geometry differs before net traversal");
        if(captured!=null && !equalsNullable(field(search,"promotedNet"),captured.priorPromotedNetId))
            throw new IllegalStateException("fresh Search did not receive the captured prior promotion owner");
    }

    private static boolean sameRoutingSettings(Object session,CaptureData captured) throws Exception {
        return field(session,"policy")==captured.policy &&
            intField(session,"maximumVias")==captured.maximumVias &&
            intField(session,"viaCost")==captured.viaCost &&
            ((Enum<?>)field(session,"branchOrderStrategy")).name().equals(captured.branchStrategy) &&
            ((Enum<?>)field(session,"netOrderStrategy")).name().equals(captured.netStrategy) &&
            boolField(session,"roleWidths")==captured.roleWidths &&
            intField(field(session,"budget"),"maximum")==captured.maximumExpansions;
    }

    /* Insert inside FailureCohortRoutingStudy. No owner/Search references are stored. */
    private static final class TerminalFailure {
        final String failedNetId, ownerPromotedNetId, ownerReason;
        final int searchIndex, searchPass, passBefore, passAfter, advanceCallOrdinal;
        final boolean ownerSearchCleared;
        boolean searchReleased;

        TerminalFailure(String failedNetId, int searchIndex, int searchPass,
                int passBefore, int passAfter, int advanceCallOrdinal,
                String ownerPromotedNetId, String ownerReason) {
            this.failedNetId=failedNetId;
            this.searchIndex=searchIndex;
            this.searchPass=searchPass;
            this.passBefore=passBefore;
            this.passAfter=passAfter;
            this.advanceCallOrdinal=advanceCallOrdinal;
            this.ownerSearchCleared=true;
            this.searchReleased=false;
            this.ownerPromotedNetId=ownerPromotedNetId;
            this.ownerReason=ownerReason;
        }

        Map<String,Object> json() {
            if(!searchReleased)
                throw new IllegalStateException("terminal Search aliases were not released");
            Map<String,Object> value=object();
            value.put("failedNetId",failedNetId);
            value.put("searchIndex",Integer.valueOf(searchIndex));
            value.put("searchPass",Integer.valueOf(searchPass));
            value.put("passBefore",Integer.valueOf(passBefore));
            value.put("passAfter",Integer.valueOf(passAfter));
            value.put("advanceCallOrdinal",Integer.valueOf(advanceCallOrdinal));
            value.put("ownerSearchCleared",Boolean.valueOf(ownerSearchCleared));
            value.put("searchReleased",Boolean.valueOf(searchReleased));
            value.put("ownerPromotedNetId",ownerPromotedNetId);
            value.put("ownerReason",ownerReason);
            return value;
        }
    }

    /* Caller has already observed this same call clear Search and change pass 2 to 3.
       The pass-after and null-search observations belong to the dispatch charge. */
    private static TerminalFailure captureTerminal(Object session,Object retiredSearch,
            int passBefore,int callOrdinal,List<String> requested,List<String> traversed,
            FreshRun out,Meter meter) throws Exception {
        if(session==null || retiredSearch==null || requested==null || traversed==null ||
                out==null || meter==null || callOrdinal<1 || passBefore!=2)
            throw new IllegalStateException("invalid terminal capture invocation");

        // These two reads are charged before execution. For a genuine exhausted
        // transition they become two of the five terminal-evidence reads. A
        // quality rejection uses ordinary wrapper work and emits no evidence.
        chargeRunWrapper(out,meter,1);
        Object promotedValue=field(session,"previousFailedNet");
        chargeRunWrapper(out,meter,1);
        Object reasonValue=field(session,"reason");
        if(promotedValue==null && "QUALITY_LIMIT".equals(reasonValue)) return null;
        if(!(promotedValue instanceof String) || !(reasonValue instanceof String))
            throw new IllegalStateException("terminal owner promotion or reason is missing");
        out.terminalEvidenceUnits+=2;

        chargeRunWrapper(out,meter,1);
        boolean roleWidths=boolField(session,"roleWidths");
        if(!roleWidths) throw new IllegalStateException("terminal owner lacks role-width promotion");

        terminalEvidenceCharge(out,meter,1);
        int searchPass=intField(retiredSearch,"pass");
        terminalEvidenceCharge(out,meter,1);
        Object failedValue=field(retiredSearch,"currentNet");
        terminalEvidenceCharge(out,meter,1);
        int index=intField(retiredSearch,"netIndex");
        if(!(failedValue instanceof String) || ((String)failedValue).length()==0)
            throw new IllegalStateException("terminal Search has no valid current net");
        String failed=(String)failedValue;
        String promoted=(String)promotedValue;
        String reason=(String)reasonValue;

        terminalEvidenceCharge(out,meter,1);
        if(searchPass!=2 || passBefore!=2)
            throw new IllegalStateException("retired Search pass does not match transition");
        terminalEvidenceCharge(out,meter,1);
        if(index<1 || index>requested.size())
            throw new IllegalStateException("terminal Search cursor is outside requested order");
        terminalEvidenceCharge(out,meter,1);
        if(!failed.equals(requested.get(index-1)))
            throw new IllegalStateException("terminal Search cursor does not identify failed net");
        terminalEvidenceCharge(out,meter,1);
        if(!failed.equals(promoted))
            throw new IllegalStateException("terminal owner promotion does not identify failed net");

        int matches=0;
        for(String id:traversed) {
            terminalEvidenceCharge(out,meter,1);
            if(failed.equals(id)) matches++;
        }
        if(matches!=1)
            throw new IllegalStateException("terminal failed net must occur once in actual traversal");
        // Validate format/classification separately from the wire's later
        // owner-reason versus outcome-reason equality check.
        chargeRunWrapper(out,meter,1);
        if(!reason.startsWith(failed+":"))
            throw new IllegalStateException("terminal reason does not identify failed net");
        chargeRunWrapper(out,meter,1);
        classifyDisposition(reason);

        // Precharge all ten detached evidence fields before constructing the DTO.
        reserveCopy(out,meter,10);
        return new TerminalFailure(failed,index,searchPass,passBefore,3,callOrdinal,promoted,reason);
    }

    private static void terminalEvidenceCharge(FreshRun out,Meter meter,long units)
            throws IOException {
        chargeRunWrapper(out,meter,units);
        out.terminalEvidenceUnits+=units;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if(failure instanceof Exception) throw (Exception)failure;
        if(failure instanceof Error) throw (Error)failure;
        throw new IllegalStateException(failure);
    }

    private static void reserveCohortCopies(FreshRun run,Meter meter,int count,int selected)
            throws IOException {
        // Known/source membership sets, selected set, then output vector.
        reserveCopy(run,meter,count); reserveCopy(run,meter,count);
        reserveCopy(run,meter,selected); reserveCopy(run,meter,count);
    }

    private static List<String> checkedStableCohort(List<String> source,List<String> cohort,
            List<String> known) {
        if(source==null || known==null || source.size()!=known.size())
            throw new IllegalArgumentException("SOURCE_MEMBERSHIP");
        if(cohort==null || cohort.isEmpty() || cohort.size()>2)
            throw new IllegalArgumentException("MISSING_COHORT");
        Set<String> all=new HashSet<String>(),seen=new HashSet<String>(),selected=new HashSet<String>();
        for(String id:known)
            if(id==null || id.length()==0 || !all.add(id))
                throw new IllegalArgumentException("UNKNOWN_SOURCE");
        for(String id:source)
            if(id==null || !all.contains(id) || !seen.add(id))
                throw new IllegalArgumentException("SOURCE_MEMBERSHIP");
        for(String id:cohort) {
            if(id==null || id.length()==0 || !all.contains(id))
                throw new IllegalArgumentException("UNKNOWN_COHORT");
            if(!selected.add(id)) throw new IllegalArgumentException("DUPLICATE_COHORT");
        }
        List<String> result=new ArrayList<String>(); result.addAll(cohort);
        for(String id:source) if(!selected.contains(id)) result.add(id);
        if(result.size()!=source.size()) throw new IllegalArgumentException("SOURCE_MEMBERSHIP");
        return result;
    }

    private static void validateFeedbackIds(List<String> source,String first,String second) {
        if(first==null || first.length()==0 || second==null || second.length()==0)
            throw new IllegalArgumentException("MISSING_FAILURE_ID");
        if(source==null || !source.contains(first) || !source.contains(second))
            throw new IllegalArgumentException("UNKNOWN_FAILURE_ID");
        if(first.equals(second)) throw new IllegalArgumentException("REPEATED_FAILURE_ID");
    }

    private static Map<String,Object> trialDisposition(String status,String reason,String first,
            String second,List<String> cohort,List<String> requested,boolean changed) {
        Map<String,Object> value=object();
        value.put("status",status); value.put("reason",reason);
        value.put("capturedFailedNetId",first); value.put("controlFailedNetId",second);
        value.put("cohortNetIds",cohort==null?null:stringValues(cohort));
        value.put("requestedNetIds",requested==null?null:stringValues(requested));
        value.put("orderChanged",Boolean.valueOf(changed));
        value.put("feedbackValidated",Boolean.TRUE);
        return value;
    }

    private static String classifyDisposition(String reason) {
        if(reason==null) throw new IllegalStateException("completed P07 owner omitted its terminal reason");
        if("NO_PATH".equals(reason) || reason.endsWith(":NO_PATH")) return "NO_PATH";
        if("TOTAL_SEARCH_LIMIT".equals(reason) || reason.endsWith(":TOTAL_SEARCH_LIMIT") ||
                "BRANCH_SEARCH_LIMIT".equals(reason) || reason.endsWith(":BRANCH_SEARCH_LIMIT"))
            return "CAP_REJECTED";
        if("QUALITY_LIMIT".equals(reason) || reason.endsWith(":BRANCH_QUALITY_LIMIT"))
            return "QUALITY_REJECTED";
        throw new IllegalStateException("unexpected P07 terminal disposition: "+reason);
    }

    private static Map<String,Object> finalizers(String geometry,String connectivity,
            String twoLayer,String quality,String vias) {
        Map<String,Object> result=object(); result.put("routingGeometry",geometry);
        result.put("allNetConnectivity",connectivity); result.put("twoLayerRules",twoLayer);
        result.put("routeQuality",quality); result.put("viaCap",vias); return result;
    }

    private static Map<String,Object> runJson(FreshRun run) {
        Map<String,Object> out=object(); out.put("kind",run.kind);
        Map<String,Object> initial=object(); initial.put("pass",Integer.valueOf(PASS));
        initial.put("passes",Integer.valueOf(0)); initial.put("expanded",Integer.valueOf(0));
        initial.put("maximumExpansions",Integer.valueOf(run.maximumExpansions));
        initial.put("maximumVias",Integer.valueOf(run.maximumVias));
        initial.put("priorPromotedNetId",run.priorPromotedNetId);
        initial.put("policy",run.policy); initial.put("viaCost",Integer.valueOf(run.viaCost));
        initial.put("policyTransitions",Integer.valueOf(run.maximumTransitions));
        initial.put("policyVias",Integer.valueOf(run.policyVias));
        initial.put("branchOrderStrategy",run.branchStrategy); initial.put("netOrderStrategy",run.netStrategy);
        initial.put("roleWidths",Boolean.valueOf(run.roleWidths));
        initial.put("capturedOrderingStream",Boolean.valueOf(run.capturedOrderingStream));
        initial.put("sessionOrderingStream",Boolean.FALSE);
        initial.put("instrumentationModeDifference",Boolean.valueOf(run.instrumentationModeDifference));
        initial.put("routingRelevantSettingsMatch",Boolean.valueOf(run.routingSettingsMatch));
        initial.put("traceCount",Integer.valueOf(0)); initial.put("holeCount",Integer.valueOf(0));
        out.put("initial",initial);
        Map<String,Object> ordering=object();
        ordering.put("capturedNetIds",stringValues(run.capturedNetIds));
        ordering.put("computedNetIds",stringValues(run.computedNetIds));
        ordering.put("requestedCohortNetIds",stringValues(run.requestedCohortNetIds));
        ordering.put("requestedNetIds",stringValues(run.requestedNetIds));
        ordering.put("traversedNetIds",stringValues(run.traversedNetIds));
        ordering.put("orderMatchesCapture",Boolean.valueOf(run.orderMatchesCapture));
        ordering.put("searchCount",Integer.valueOf(run.searchCount)); out.put("ordering",ordering);
        Map<String,Object> outcome=object(); outcome.put("disposition",run.disposition);
        outcome.put("reason",run.reason); outcome.put("complete",Boolean.valueOf(run.complete));
        outcome.put("ownerComplete",Boolean.valueOf(run.ownerComplete));
        outcome.put("pass",Integer.valueOf(run.finalPass)); outcome.put("passes",Integer.valueOf(run.finalPasses));
        outcome.put("expanded",Integer.valueOf(run.expanded));
        outcome.put("maximumExpansions",Integer.valueOf(run.maximumExpansions));
        outcome.put("errorClass",null); outcome.put("errorMessage",null); out.put("outcome",outcome);
        out.put("terminalFailure",run.terminalFailure==null?null:run.terminalFailure.json());
        out.put("finalizers",run.finalizers);
        out.put("layout",run.layout);
        Map<String,Object> work=object(); work.put("advanceCalls",Integer.valueOf(run.advanceCalls));
        work.put("failureObservationUnits",Long.valueOf(run.failureObservationUnits));
        work.put("terminalEvidenceUnits",Long.valueOf(run.terminalEvidenceUnits));
        work.put("ownerUnits",Long.valueOf(run.ownerUnits)); work.put("wrapperUnits",Long.valueOf(run.wrapperUnits));
        work.put("copyEntries",Long.valueOf(run.copiedEntries)); work.put("copyChunks",Integer.valueOf(run.copyChunks));
        work.put("groups",Integer.valueOf(run.groups));
        work.put("maxGroupNanos",Long.valueOf(run.maxGroupNanos));
        work.put("maximumUnitsPerGroup",Integer.valueOf(run.maximumUnitsPerGroup));
        out.put("work",work); return out;
    }

    private static void chargeRunWrapper(FreshRun run,Meter meter,long units) throws IOException {
        if(units<0) throw new IOException("negative wrapper work charge");
        meter.charge(units);
        run.wrapperUnits+=units;
    }

    private static void reserveCopy(FreshRun run,Meter meter,int count) throws IOException {
        if(count<0 || count>COPY_CHUNK || count>COPY_LIMIT-meter.copiedEntries)
            throw new IOException("copy exceeds its prebounded chunk or aggregate allowance");
        if(count==0) return;
        meter.reserve(count);
        meter.copiedEntries+=count;
        meter.copyChunks++;
        if(run==null) meter.charge(count);
        else {
            run.copiedEntries+=count;
            run.copyChunks++;
            chargeRunWrapper(run,meter,count);
        }
    }

    private static final class FingerprintCensus {
        long entries;
        int chunks,visits;
        void add(int count,int repetitions) throws IOException {
            if(count<0 || count>COPY_CHUNK || repetitions<0)
                throw new IOException("fingerprint copy exceeds its bounded collection");
            entries+=(long)count*repetitions;
            if(count!=0) chunks+=repetitions;
            if(entries>COPY_LIMIT) throw new IOException("fingerprint copies exceed aggregate allowance");
        }
    }

    private static java.util.Collection<?> borrowedValues(Object owner,String name) {
        Object value=field(owner,name);
        if(value instanceof Map) return ((Map<?,?>)value).values();
        if(value instanceof java.util.Collection) return (java.util.Collection<?>)value;
        throw new IllegalStateException("maintained physical collection changed type");
    }

    /** Read-only census; it allocates no copied geometry collections. */
    private static FingerprintCensus fingerprintCensus(PcbBoardLayout layout) throws IOException {
        FingerprintCensus census=new FingerprintCensus();
        for(String name:new String[] {"components","pads","silkscreenLabels","traces"})
            census.add(layoutCount(layout,name),1);
        for(Object value:borrowedValues(layout,"regions")) {
            census.add(physicalCollectionCount(value,"components"),1); census.visits++;
        }
        for(Object value:borrowedValues(layout,"components")) {
            PcbComponentPlacement component=(PcbComponentPlacement)value;
            int terminals=component.getPhysicalPackage().getTerminalCount();
            // IDs + appendGeometry + (T+1) loop-condition vector clones, then T terminals.
            census.add(terminals,terminals+3);
            census.add(1,terminals);
            census.visits++;
        }
        for(Object value:borrowedValues(layout,"traces")) {
            census.add(physicalTracePointCount((PcbTraceGeometry)value),2); census.visits++;
        }
        return census;
    }

    private static void reserveFingerprintCopies(FreshRun run,Meter meter,PcbBoardLayout layout)
            throws IOException {
        meter.beginGroup();
        FingerprintCensus census=fingerprintCensus(layout);
        long censusUnits=8L+2L*census.visits;
        if(run==null) meter.charge(censusUnits);
        else chargeRunWrapper(run,meter,censusUnits);
        meter.finishGroup();
        if(census.entries>COPY_LIMIT-meter.copiedEntries || census.entries+128>STEP_LIMIT)
            throw new IOException("synchronous fingerprint cannot fit the remaining bounded work");
        meter.beginGroup();
        meter.reserve(census.entries+128);
        for(String name:new String[] {"components","pads","silkscreenLabels","traces"})
            reserveCopy(run,meter,layoutCount(layout,name));
        for(Object value:borrowedValues(layout,"regions"))
            reserveCopy(run,meter,physicalCollectionCount(value,"components"));
        for(Object value:borrowedValues(layout,"components")) {
            int terminals=((PcbComponentPlacement)value).getPhysicalPackage().getTerminalCount();
            for(int copy=0;copy<terminals+3;copy++) reserveCopy(run,meter,terminals);
            for(int terminal=0;terminal<terminals;terminal++) reserveCopy(run,meter,1);
        }
        for(Object value:borrowedValues(layout,"traces")) {
            int points=physicalTracePointCount((PcbTraceGeometry)value);
            reserveCopy(run,meter,points); reserveCopy(run,meter,points);
        }
    }

    private static void reservePadLandCopies(Meter meter,PcbBoardLayout layout) throws IOException {
        int pads=layoutCount(layout,"pads");
        if(pads>COPY_CHUNK || 11L*pads>COPY_LIMIT-meter.copiedEntries || 11L*pads+32>STEP_LIMIT)
            throw new IOException("pad-land export exceeds remaining bounded work");
        meter.reserve(11L*pads+32);
        reserveCopy(null,meter,pads); // getPads Vector
        reserveCopy(null,meter,pads); // sorted ArrayList
        reserveCopy(null,meter,pads); // row List
        for(int i=0;i<pads;i++) {
            reserveCopy(null,meter,4); // bounds int[]
            reserveCopy(null,meter,4); // exported coordinate List
        }
    }

    private static void prepareLegacyCaptureCopies(Object search,PcbBoardLayout live,CaptureData capture)
            throws IOException {
        Object owner=field(search,"owner");
        PcbBoardLayout source=(PcbBoardLayout)field(owner,"template");
        int layoutEntries=layoutEntryCount(source);
        int pads=layoutCount(source,"pads");
        if(layoutEntries>COPY_CHUNK || pads<1 || pads>COPY_CHUNK ||
                layoutCount(source,"traces")!=0 || layoutCount(source,"holes")!=0)
            throw new IOException("legacy capture source exceeds its pristine bounded copy");
        FingerprintCensus fingerprint=fingerprintCensus(source);
        int holes=layoutCount(live,"holes");
        int reached=collectionSize(field(search,"reachedPadIds"),"reached pads");
        Object branch=field(search,"branch");
        int reachedPth=collectionSize(field(branch,"reachedTerminals"),"reached PTH pads");
        int nets=collectionSize(field(search,"nets"),"captured net vector");
        for(int count:new int[] {holes,reached,reachedPth,nets})
            if(count>COPY_CHUNK) throw new IOException("legacy capture collection exceeds4096");
        // Two padLands passes: getPads + sorted list + rows + two 4-entry arrays/lists per pad.
        long entries=2L*layoutEntries+4L*fingerprint.entries+22L*pads+holes+reached+reachedPth+nets;
        if(entries>COPY_LIMIT) throw new IOException("legacy capture exceeds aggregate copy allowance");
        int censusChunks=0;
        for(String name:new String[] {"pads","components","silkscreenLabels","regions","holes"})
            if(layoutCount(source,name)!=0) censusChunks++;
        capture.copiedEntries=(int)entries;
        capture.copyChunks=1+censusChunks+4*fingerprint.chunks+2*(3+2*pads)+
            (holes==0?0:1)+(reached==0?0:1)+(reachedPth==0?0:1)+(nets==0?0:1);
        capture.preCountWork=24+fingerprint.visits;
    }

    private static String oldPadLandJson(TroubleshootBoard board,PcbBoardLayout layout)
            throws Exception {
        Object rows=oldMethod("padLands",new Class<?>[] {TroubleshootBoard.class,PcbBoardLayout.class},
            board,layout);
        return oldString("json",new Class<?>[] {Object.class},rows);
    }

    private static Map<String,Object> capturedOrderJson(CaptureData capture) {
        Map<String,Object> out=object();
        out.put("pass",Integer.valueOf(capture.ownerPass));
        out.put("failedNetId",capture.failedNetId);
        out.put("priorPromotedNetId",capture.priorPromotedNetId);
        out.put("netIds",stringValues(capture.netIds));
        out.put("liveSearchIndex",Integer.valueOf(capture.liveSearchIndex));
        Map<String,Object> owner=object();
        owner.put("policy",capture.policy.name());
        owner.put("maximumExpansions",Integer.valueOf(capture.maximumExpansions));
        owner.put("expandedAtFailure",Integer.valueOf(capture.expandedAtFailure));
        owner.put("maximumVias",Integer.valueOf(capture.maximumVias));
        owner.put("viaCost",Integer.valueOf(capture.viaCost));
        owner.put("policyTransitions",Integer.valueOf(capture.policyTransitions));
        owner.put("policyVias",Integer.valueOf(capture.policyVias));
        owner.put("branchOrderStrategy",capture.branchStrategy);
        owner.put("netOrderStrategy",capture.netStrategy);
        owner.put("roleWidths",Boolean.valueOf(capture.roleWidths));
        owner.put("orderingStream",Boolean.valueOf(capture.orderingStream));
        out.put("owner",owner);
        return out;
    }

    private static final class CompositeInspector
            implements ActualGeneratorBranchOrderStudy.TerminalPrefixInspector {
        final Object target;
        final CaptureData captured;
        CompositeInspector(Object target,CaptureData captured) {
            this.target=target; this.captured=captured;
        }
        public void onTerminalPrefix(Object search,PcbBoardLayout layout,
                PcbLayerRoutingPrototype.BranchSnapshot snapshot,String prefixFingerprint,
                String prefixSha256,int prefixBytes,int receiptBranchOrdinal,int groupOrdinal) {
            int before=intField(target,"count");
            if(snapshot.pass==PASS && NET.equals(snapshot.netId) &&
                    START_PAD.equals(snapshot.startPadId) && ROOT_PAD.equals(snapshot.rootPadId)) {
                try { prepareLegacyCaptureCopies(search,layout,captured); }
                catch(IOException failure) { throw new IllegalStateException("legacy capture copy prebound failed",failure); }
            }
            ((ActualGeneratorBranchOrderStudy.TerminalPrefixInspector)target).onTerminalPrefix(
                search,layout,snapshot,prefixFingerprint,prefixSha256,prefixBytes,
                receiptBranchOrdinal,groupOrdinal);
            int after=intField(target,"count");
            if(after==before) return;
            if(before!=0 || after!=1 || captured.callbackCount!=0)
                throw new IllegalStateException("selected terminal branch callback was repeated");
            Object owner=field(search,"owner");
            Object policy=field(owner,"policy");
            if(!(owner instanceof PcbLayerRoutingPrototype.Session) ||
                    !(policy instanceof PcbLayerRoutingPrototype.Policy))
                throw new IllegalStateException("selected branch omitted actual P07 owner policy");
            captured.ownerPass=intField(owner,"pass");
            captured.searchPass=intField(search,"pass");
            captured.failedNetId=stringField(search,"currentNet");
            Object prior=field(search,"promotedNet");
            if(prior!=null && !(prior instanceof String))
                throw new IllegalStateException("actual prior promoted net is not a string");
            captured.priorPromotedNetId=(String)prior;
            Object rawNets=field(search,"nets");
            if(!(rawNets instanceof Vector) || ((Vector<?>)rawNets).size()>COPY_CHUNK)
                throw new IllegalStateException("captured P07 net vector exceeds one copy chunk");
            Vector<String> actualNets=copyStringVector(rawNets);
            if(actualNets.size()>COPY_CHUNK)
                throw new IllegalStateException("captured P07 net vector exceeds one copy chunk");
            captured.netIds=actualNets;
            captured.liveSearchIndex=intField(search,"netIndex");
            captured.board=(TroubleshootBoard)field(search,"board");
            captured.policy=(PcbLayerRoutingPrototype.Policy)policy;
            Object budget=field(owner,"budget");
            captured.maximumExpansions=intField(budget,"maximum");
            captured.expandedAtFailure=((PcbLayerRoutingPrototype.Session)owner).expansions();
            captured.maximumVias=intField(owner,"maximumVias");
            captured.viaCost=intField(owner,"viaCost");
            captured.policyTransitions=captured.policy.transitions;
            captured.policyVias=captured.policy.vias;
            captured.branchStrategy=((PcbLayerRoutingPrototype.BranchOrderStrategy)
                field(owner,"branchOrderStrategy")).name();
            captured.netStrategy=((PcbLayerRoutingPrototype.NetOrderStrategy)
                field(owner,"netOrderStrategy")).name();
            captured.roleWidths=boolField(owner,"roleWidths");
            captured.orderingStream=boolField(owner,"orderingStream");
            captured.groupOrdinal=groupOrdinal;
            captured.detachedGeometrySha=stringField(target,"detachedGeometrySha");
            captured.sourceUnchanged=boolField(target,"sourceUnchanged");
            PcbBoardLayout detached=(PcbBoardLayout)field(target,"template");
            if(detached==null) throw new IllegalStateException("old Capture did not retain its detached source");
            captured.extraCallbackUnits=32+actualNets.size()+captured.preCountWork;
            captured.callbackCount=1;
        }
        public void afterOwnersClosed(boolean ownersReleased,boolean contextRestored) {
            ((ActualGeneratorBranchOrderStudy.TerminalPrefixInspector)target)
                .afterOwnersClosed(ownersReleased,contextRestored);
        }
    }

    private static final class CaptureData {
        int callbackCount,ownerPass,searchPass,maximumExpansions,expandedAtFailure;
        int maximumVias,viaCost,policyTransitions,policyVias,liveSearchIndex;
        int groupOrdinal,extraCallbackUnits,copiedEntries,copyChunks,preCountWork;
        boolean roleWidths,orderingStream,sourceUnchanged;
        String failedNetId,priorPromotedNetId,branchStrategy,netStrategy,detachedGeometrySha;
        PcbLayerRoutingPrototype.Policy policy;
        TroubleshootBoard board;
        List<String> netIds=Collections.emptyList();
    }

    private static final class OriginalArm {
        Object legacyCapture;
        Map<String,Object> operation1,reference,source;
        CaptureData captureData;
        long rawBytes,cleanupNanos,maxGroupNanos,p07OwnerSteps,diagnosticUnits;
        int groupCount,callbackUnits,copiedEntries,copyChunks;
        GroupStats groupStats;
        String rawSha;
        boolean ownersReleased,contextRestored;
    }

    private static final class GroupStats {
        int groups,maxUnits;
        long p07Steps,diagnosticUnits;
    }

// Paste-ready members for FailureCohortRoutingStudy; not a standalone Java type.
private static Object enumValue(Class<?> type,String name) {
    if(type==null || !type.isEnum() || name==null)
        throw new IllegalArgumentException("expected enum type and constant");
    Object[] values=type.getEnumConstants();
    for(Object value:values) {
        if(((Enum<?>)value).name().equals(name)) return value;
    }
    throw new IllegalArgumentException("maintained enum constant missing: "+name);
}

private static Field declaredField(Object target,String name) {
    if(target==null) throw new IllegalStateException("cannot read field from null: "+name);
    Class<?> type=target.getClass();
    while(type!=null) {
        try {
            Field result=type.getDeclaredField(name);
            result.setAccessible(true);
            return result;
        } catch(NoSuchFieldException absent) {
            type=type.getSuperclass();
        } catch(RuntimeException inaccessible) {
            throw new IllegalStateException("cannot access maintained field "+name,inaccessible);
        }
    }
    throw new IllegalStateException("maintained owner omitted field "+name);
}

private static Object field(Object target,String name) {
    try {
        return declaredField(target,name).get(target);
    } catch(IllegalAccessException failure) {
        throw new IllegalStateException("cannot read maintained field "+name,failure);
    }
}

private static void setField(Object target,String name,Object value) {
    Field targetField=declaredField(target,name);
    try {
        targetField.set(target,value);
    } catch(IllegalAccessException | IllegalArgumentException failure) {
        throw new IllegalStateException("cannot update maintained reference "+name,failure);
    }
}

private static void setIntField(Object target,String name,int value) {
    Field targetField=declaredField(target,name);
    if(targetField.getType()!=Integer.TYPE)
        throw new IllegalStateException("maintained field is not a primitive int: "+name);
    try {
        targetField.setInt(target,value);
    } catch(IllegalAccessException | IllegalArgumentException failure) {
        throw new IllegalStateException("cannot update maintained int "+name,failure);
    }
}

private static int intField(Object target,String name) {
    Object value=field(target,name);
    if(!(value instanceof Integer))
        throw new IllegalStateException("expected integer field "+name);
    return ((Integer)value).intValue();
}

private static long longField(Object target,String name) {
    Object value=field(target,name);
    if(!(value instanceof Long))
        throw new IllegalStateException("expected long field "+name);
    return ((Long)value).longValue();
}

private static boolean boolField(Object target,String name) {
    Object value=field(target,name);
    if(!(value instanceof Boolean))
        throw new IllegalStateException("expected boolean field "+name);
    return ((Boolean)value).booleanValue();
}

private static String stringField(Object target,String name) {
    Object value=field(target,name);
    if(!(value instanceof String))
        throw new IllegalStateException("expected string field "+name);
    return (String)value;
}

private static Object invoke(Method method,Object receiver,Object... arguments) throws Exception {
    if(method==null) throw new IllegalArgumentException("missing maintained method");
    try {
        return method.invoke(receiver,arguments);
    } catch(InvocationTargetException wrapped) {
        Throwable cause=wrapped.getCause();
        if(cause instanceof Exception) throw (Exception)cause;
        if(cause instanceof Error) throw (Error)cause;
        throw new IllegalStateException("maintained invocation failed",cause);
    }
}

private static Object oldMethod(String name,Class<?>[] parameterTypes,Object... arguments)
        throws Exception {
    if(name==null || parameterTypes==null || arguments==null ||
            parameterTypes.length!=arguments.length)
        throw new IllegalArgumentException("invalid old-helper bridge request");
    Class<?> helper=Class.forName(OLD_CAPTURE);
    Method method=helper.getDeclaredMethod(name,parameterTypes);
    method.setAccessible(true);
    if(!java.lang.reflect.Modifier.isStatic(method.getModifiers()))
        throw new IllegalStateException("old helper method is not static: "+name);
    return invoke(method,null,arguments);
}

@SuppressWarnings("unchecked")
private static Map<String,Object> oldMap(String name,Class<?>[] parameterTypes,Object... arguments)
        throws Exception {
    Object value=oldMethod(name,parameterTypes,arguments);
    if(!(value instanceof Map))
        throw new IllegalStateException("old helper method did not return an object: "+name);
    return (Map<String,Object>)value;
}

private static String oldString(String name,Class<?>[] parameterTypes,Object... arguments)
        throws Exception {
    Object value=oldMethod(name,parameterTypes,arguments);
    if(!(value instanceof String))
        throw new IllegalStateException("old helper method did not return a string: "+name);
    return (String)value;
}

private static boolean equalsNullable(Object first,Object second) {
    return first==second || first!=null && first.equals(second);
}

private static Vector<String> copyStringVector(Object source) {
    if(!(source instanceof Vector))
        throw new IllegalStateException("maintained net order is not a Vector");
    Vector<?> values=(Vector<?>)source;
    Vector<String> copy=new Vector<String>(values.size());
    for(Object value:values) {
        if(!(value instanceof String))
            throw new IllegalStateException("maintained net order contains a non-string");
        copy.add((String)value);
    }
    return copy;
}

private static List<Object> stringValues(List<?> values) {
    if(values==null) throw new IllegalArgumentException("missing string list");
    ArrayList<Object> result=new ArrayList<Object>(values.size());
    for(Object value:values) {
        if(!(value instanceof String))
            throw new IllegalStateException("expected string value");
        result.add(value);
    }
    return result;
}

private static Map<String,Object> object() {
    return new LinkedHashMap<String,Object>();
}

private static String json(Object value) {
    StringBuilder out=new StringBuilder();
    appendJson(out,value);
    return out.toString();
}

private static void appendJson(StringBuilder out,Object value) {
    if(value==null) {
        out.append("null");
    } else if(value instanceof String) {
        quote(out,(String)value);
    } else if(value instanceof Boolean) {
        out.append(value.toString());
    } else if(value instanceof Number) {
        String number=value.toString();
        if(!validJsonNumber(number))
            throw new IllegalArgumentException("non-finite or malformed JSON number");
        out.append(number);
    } else if(value instanceof Map) {
        out.append('{');
        boolean first=true;
        for(Object raw:((Map<?,?>)value).entrySet()) {
            Map.Entry<?,?> entry=(Map.Entry<?,?>)raw;
            if(!(entry.getKey() instanceof String))
                throw new IllegalArgumentException("JSON object key is not a string");
            if(!first) out.append(',');
            first=false;
            quote(out,(String)entry.getKey());
            out.append(':');
            appendJson(out,entry.getValue());
        }
        out.append('}');
    } else if(value instanceof Iterable) {
        out.append('[');
        boolean first=true;
        for(Object item:(Iterable<?>)value) {
            if(!first) out.append(',');
            first=false;
            appendJson(out,item);
        }
        out.append(']');
    } else if(value.getClass().isArray()) {
        out.append('[');
        int length=java.lang.reflect.Array.getLength(value);
        for(int index=0;index<length;index++) {
            if(index>0) out.append(',');
            appendJson(out,java.lang.reflect.Array.get(value,index));
        }
        out.append(']');
    } else {
        throw new IllegalArgumentException("unsupported JSON value type: "+
            value.getClass().getName());
    }
}

private static boolean validJsonNumber(String value) {
    if(value==null || value.length()==0) return false;
    int index=0,length=value.length();
    if(value.charAt(index)=='-') {
        index++;
        if(index==length) return false;
    }
    if(value.charAt(index)=='0') {
        index++;
        if(index<length && value.charAt(index)>='0' && value.charAt(index)<='9')
            return false;
    } else {
        if(value.charAt(index)<'1' || value.charAt(index)>'9') return false;
        while(index<length && value.charAt(index)>='0' && value.charAt(index)<='9') index++;
    }
    if(index<length && value.charAt(index)=='.') {
        index++;
        int digits=index;
        while(index<length && value.charAt(index)>='0' && value.charAt(index)<='9') index++;
        if(index==digits) return false;
    }
    if(index<length && (value.charAt(index)=='e' || value.charAt(index)=='E')) {
        index++;
        if(index<length && (value.charAt(index)=='+' || value.charAt(index)=='-')) index++;
        int digits=index;
        while(index<length && value.charAt(index)>='0' && value.charAt(index)<='9') index++;
        if(index==digits) return false;
    }
    return index==length;
}

private static void quote(StringBuilder out,String value) {
    if(value==null) throw new IllegalArgumentException("missing JSON string");
    out.append('"');
    for(int index=0;index<value.length();index++) {
        char ch=value.charAt(index);
        if(ch=='"') out.append("\\\"");
        else if(ch=='\\') out.append("\\\\");
        else if(ch=='\b') out.append("\\b");
        else if(ch=='\f') out.append("\\f");
        else if(ch=='\n') out.append("\\n");
        else if(ch=='\r') out.append("\\r");
        else if(ch=='\t') out.append("\\t");
        else if(ch<32 || Character.isLowSurrogate(ch) ||
                Character.isHighSurrogate(ch) &&
                    (index+1>=value.length() || !Character.isLowSurrogate(value.charAt(index+1)))) {
            appendUnicodeEscape(out,ch);
        } else {
            out.append(ch);
            if(Character.isHighSurrogate(ch)) out.append(value.charAt(++index));
        }
    }
    out.append('"');
}

private static void appendUnicodeEscape(StringBuilder out,char value) {
    final char[] hex="0123456789abcdef".toCharArray();
    out.append("\\u");
    out.append(hex[(value>>>12)&15]);
    out.append(hex[(value>>>8)&15]);
    out.append(hex[(value>>>4)&15]);
    out.append(hex[value&15]);
}

private static long jsonLongField(String line,String name,boolean nullable) throws IOException {
    if(line==null || name==null || name.length()==0)
        throw new IOException("missing JSON row or field name");
    int index=skipJsonWhitespace(line,0);
    if(index>=line.length() || line.charAt(index++)!='{')
        throw new IOException("JSONL row is not an object");
    HashSet<String> keys=new HashSet<String>();
    int found=0;
    long result=0;
    boolean finished=false;
    while(!finished) {
        index=skipJsonWhitespace(line,index);
        if(index<line.length() && line.charAt(index)=='}') {
            index++;
            break;
        }
        if(index>=line.length() || line.charAt(index)!='"')
            throw new IOException("JSON object key is malformed");
        int keyEnd=jsonStringEnd(line,index);
        String key=decodeJsonString(line,index,keyEnd);
        if(!keys.add(key)) throw new IOException("JSON object contains a duplicate key");
        index=skipJsonWhitespace(line,keyEnd);
        if(index>=line.length() || line.charAt(index++)!=':')
            throw new IOException("JSON object key has no value");
        index=skipJsonWhitespace(line,index);
        int valueEnd=jsonValueEnd(line,index);
        if(key.equals(name)) {
            found++;
            String token=line.substring(index,valueEnd);
            if("null".equals(token)) {
                if(!nullable) throw new IOException("JSON integer field is null: "+name);
                result=-1;
            } else {
                if(token.length()==0 || token.length()>1 && token.charAt(0)=='0')
                    throw new IOException("JSON integer field has invalid digits: "+name);
                for(int digit=0;digit<token.length();digit++) {
                    char ch=token.charAt(digit);
                    if(ch<'0' || ch>'9')
                        throw new IOException("JSON integer field is not a nonnegative integer: "+name);
                }
                try {
                    result=Long.parseLong(token);
                } catch(NumberFormatException invalid) {
                    throw new IOException("JSON integer field is out of range: "+name,invalid);
                }
            }
        }
        index=skipJsonWhitespace(line,valueEnd);
        if(index>=line.length()) throw new IOException("JSON object was not closed");
        char delimiter=line.charAt(index++);
        if(delimiter=='}') finished=true;
        else if(delimiter!=',') throw new IOException("JSON object delimiter is malformed");
    }
    if(!finished || skipJsonWhitespace(line,index)!=line.length() || found!=1)
        throw new IOException("JSON integer field is missing, duplicated, or has trailing data: "+name);
    return result;
}

private static int skipJsonWhitespace(String text,int index) {
    while(index<text.length()) {
        char ch=text.charAt(index);
        if(ch!=' ' && ch!='\t' && ch!='\r' && ch!='\n') break;
        index++;
    }
    return index;
}

private static int jsonStringEnd(String text,int start) throws IOException {
    if(start>=text.length() || text.charAt(start)!='"')
        throw new IOException("JSON string is malformed");
    for(int index=start+1;index<text.length();index++) {
        char ch=text.charAt(index);
        if(ch<32) throw new IOException("JSON string contains an unescaped control character");
        if(ch=='"') return index+1;
        if(ch=='\\') {
            if(++index>=text.length()) throw new IOException("JSON string escape is incomplete");
            char escaped=text.charAt(index);
            if(escaped=='u') {
                if(index+4>=text.length()) throw new IOException("JSON Unicode escape is incomplete");
                for(int digit=1;digit<=4;digit++) {
                    if(Character.digit(text.charAt(index+digit),16)<0)
                        throw new IOException("JSON Unicode escape is malformed");
                }
                index+=4;
            } else if(escaped!='"' && escaped!='\\' && escaped!='/' && escaped!='b' &&
                    escaped!='f' && escaped!='n' && escaped!='r' && escaped!='t') {
                throw new IOException("JSON string escape is invalid");
            }
        }
    }
    throw new IOException("JSON string is unterminated");
}

private static String decodeJsonString(String text,int start,int end) throws IOException {
    if(end<=start+1 || text.charAt(start)!='"' || text.charAt(end-1)!='"')
        throw new IOException("JSON string token is malformed");
    StringBuilder result=new StringBuilder(end-start-2);
    for(int index=start+1;index<end-1;index++) {
        char ch=text.charAt(index);
        if(ch!='\\') {
            if(ch<32) throw new IOException("JSON string contains an unescaped control character");
            result.append(ch);
            continue;
        }
        char escaped=text.charAt(++index);
        if(escaped=='"' || escaped=='\\' || escaped=='/') result.append(escaped);
        else if(escaped=='b') result.append('\b');
        else if(escaped=='f') result.append('\f');
        else if(escaped=='n') result.append('\n');
        else if(escaped=='r') result.append('\r');
        else if(escaped=='t') result.append('\t');
        else if(escaped=='u') {
            int code=0;
            for(int digit=1;digit<=4;digit++)
                code=(code<<4)|Character.digit(text.charAt(index+digit),16);
            result.append((char)code);
            index+=4;
        } else throw new IOException("JSON string escape is invalid");
    }
    return result.toString();
}

private static int jsonValueEnd(String text,int start) throws IOException {
    if(start>=text.length()) throw new IOException("JSON value is missing");
    char first=text.charAt(start);
    if(first=='"') return jsonStringEnd(text,start);
    if(first=='{' || first=='[') {
        ArrayList<Character> closers=new ArrayList<Character>();
        closers.add(Character.valueOf(first=='{'?'}':']'));
        for(int index=start+1;index<text.length();index++) {
            char ch=text.charAt(index);
            if(ch=='"') {
                index=jsonStringEnd(text,index)-1;
            } else if(ch=='{' || ch=='[') {
                closers.add(Character.valueOf(ch=='{'?'}':']'));
            } else if(ch=='}' || ch==']') {
                int last=closers.size()-1;
                if(last<0 || closers.get(last).charValue()!=ch)
                    throw new IOException("JSON nested value has mismatched delimiters");
                closers.remove(last);
                if(closers.isEmpty()) return index+1;
            }
        }
        throw new IOException("JSON nested value is unterminated");
    }
    int end=start;
    while(end<text.length()) {
        char ch=text.charAt(end);
        if(ch==',' || ch=='}' || ch==']' || ch==' ' || ch=='\t' || ch=='\r' || ch=='\n')
            break;
        end++;
    }
    if(end==start) throw new IOException("JSON scalar is empty");
    return end;
}

private static Digest fileDigest(Path path) throws IOException {
    return fileDigest(path,null);
}

private static Digest fileDigest(Path path,Meter meter) throws IOException {
    if(path==null || !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(path))
        throw new IOException("expected a regular non-symlink file");
    long expectedSize=Files.size(path);
    if(expectedSize>RECEIPT_LIMIT) throw new IOException("receipt exceeds 8 MiB");
    MessageDigest digest;
    try {
        digest=MessageDigest.getInstance("SHA-256");
    } catch(NoSuchAlgorithmException impossible) {
        throw new IllegalStateException("SHA-256 unavailable",impossible);
    }
    long count=0;
    byte[] buffer=new byte[8192];
    try(InputStream input=new BufferedInputStream(Files.newInputStream(path))) {
        int amount;
        while((amount=input.read(buffer))>=0) {
            if(amount==0) continue;
            if(count>Long.MAX_VALUE-amount) throw new IOException("file byte count overflow");
            if(amount>RECEIPT_LIMIT-count) throw new IOException("receipt exceeds 8 MiB while hashing");
            count+=amount;
            digest.update(buffer,0,amount);
            if(meter!=null) meter.charge(hashUnits(amount));
        }
    }
    if(count!=expectedSize || Files.size(path)!=count ||
            !Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
        throw new IOException("file changed while its digest was measured");
    return new Digest(count,hexDigest(digest.digest()));
}

private static String hashString(String value) {
    return sha256(utf8(value));
}

private static String sha256(byte[] bytes) {
    if(bytes==null) throw new IllegalArgumentException("missing SHA-256 input");
    try {
        return hexDigest(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch(NoSuchAlgorithmException impossible) {
        throw new IllegalStateException("SHA-256 unavailable",impossible);
    }
}

private static String hexDigest(byte[] bytes) {
    StringBuilder result=new StringBuilder(bytes.length*2);
    final char[] hex="0123456789abcdef".toCharArray();
    for(byte value:bytes) {
        int unsigned=value&255;
        result.append(hex[unsigned>>>4]);
        result.append(hex[unsigned&15]);
    }
    return result.toString();
}

private static byte[] utf8(String value) {
    if(value==null) throw new IllegalArgumentException("missing UTF-8 input");
    return value.getBytes(UTF8);
}

private static int hashUnits(int bytes) {
    if(bytes<0) throw new IllegalArgumentException("negative hash input");
    return bytes/4096+(bytes%4096==0?0:1);
}

private static long elapsed(long start,long end) {
    long value=end-start;
    return value<0?Long.MAX_VALUE:value;
}

private static int layoutCount(PcbBoardLayout layout,String fieldName) {
    if(layout==null || fieldName==null)
        throw new IllegalArgumentException("missing PCB layout collection");
    if(!"pads".equals(fieldName) && !"components".equals(fieldName) &&
            !"traces".equals(fieldName) && !"holes".equals(fieldName) &&
            !"silkscreenLabels".equals(fieldName) && !"regions".equals(fieldName))
        throw new IllegalArgumentException("unknown PCB layout collection: "+fieldName);
    return collectionSize(field(layout,fieldName),"PcbBoardLayout."+fieldName);
}

private static int physicalCollectionCount(Object owner,String fieldName) {
    return collectionSize(field(owner,fieldName),fieldName);
}

private static int collectionSize(Object value,String label) {
    if(value==null) throw new IllegalStateException("maintained collection is null: "+label);
    long size;
    if(value instanceof Map) size=((Map<?,?>)value).size();
    else if(value instanceof java.util.Collection) size=((java.util.Collection<?>)value).size();
    else if(value.getClass().isArray()) size=java.lang.reflect.Array.getLength(value);
    else throw new IllegalStateException("maintained value is not a collection: "+label);
    if(size<0 || size>Integer.MAX_VALUE)
        throw new IllegalStateException("maintained collection size is out of range: "+label);
    return (int)size;
}

private static int physicalTracePointCount(PcbTraceGeometry trace) {
    if(trace==null) throw new IllegalArgumentException("missing physical trace");
    Object xValue=field(trace,"xPoints");
    Object yValue=field(trace,"yPoints");
    if(!(xValue instanceof int[]) || !(yValue instanceof int[]))
        throw new IllegalStateException("maintained trace coordinate arrays changed type");
    int[] x=(int[])xValue;
    int[] y=(int[])yValue;
    if(x.length!=y.length)
        throw new IllegalStateException("maintained trace coordinate arrays have different lengths");
    return x.length;
}

private static int layoutEntryCount(PcbBoardLayout layout) {
    if(layout==null) throw new IllegalArgumentException("missing PCB layout");
    long total=0;
    String[] fields={"pads","components","traces","holes","silkscreenLabels","regions"};
    for(String name:fields) {
        total+=layoutCount(layout,name);
        if(total>Integer.MAX_VALUE)
            throw new IllegalStateException("PCB layout entry count overflow");
    }
    return (int)total;
}

private static final class Digest {
    final long bytes;
    final String sha;
    Digest(long bytes,String sha) {
        if(bytes<0 || sha==null || !SHA.matcher(sha).matches())
            throw new IllegalArgumentException("invalid digest result");
        this.bytes=bytes;
        this.sha=sha;
    }
}

private static final class GroupMeasure {
    final long nanos;
    final int units;
    GroupMeasure(long nanos,int units) {
        if(nanos<0 || units<0 || units>STEP_LIMIT)
            throw new IllegalArgumentException("invalid measured group");
        this.nanos=nanos;
        this.units=units;
    }
}

private static final class Meter {
    int sessionConstructorEntries;
    final long started;
    final ArrayList<GroupMeasure> groupMeasures=new ArrayList<GroupMeasure>();
    long units,maxGroupNanos,groupStarted,copiedEntries;
    int groups,groupUnits,maxGroupUnits,priorGroups,copyChunks;
    boolean open;

    Meter(long started) {
        this.started=started;
    }

    void beginGroup() throws IOException {
        if(open) finishGroup();
        check();
        if(groups+priorGroups>=MAX_GROUPS)
            throw new IOException("native wrapper exceeded 640 measured groups");
        groupStarted=System.nanoTime();
        groupUnits=0;
        open=true;
    }

    void beforeOperation() throws IOException { reserve(COPY_CHUNK); }

    void reserve(long amount) throws IOException {
        if(amount<0 || amount>STEP_LIMIT) throw new IOException("atomic work exceeds group capacity");
        if(open && amount>STEP_LIMIT-groupUnits) finishGroup();
        if(!open) beginGroup();
        check();
    }

    void charge(long amount) throws IOException {
        if(!open) throw new IllegalStateException("work unit outside a measured group");
        if(amount<0) throw new IllegalArgumentException("negative measured work");
        check();
        if(amount>STEP_LIMIT-groupUnits)
            throw new IOException("native wrapper exceeded 32768 units in one group");
        if(units>Long.MAX_VALUE-amount)
            throw new IOException("native wrapper work counter overflow");
        groupUnits+=(int)amount;
        units+=amount;
        check();
    }

    void check() {
        long now=System.nanoTime();
        if(elapsed(started,now)>OP_NANOS)
            throw new IllegalStateException("failed-first process exceeded 90 seconds");
        if(open && elapsed(groupStarted,now)>GROUP_NANOS)
            throw new IllegalStateException("native wrapper group exceeded five seconds");
        if(groups+priorGroups>MAX_GROUPS || groupUnits>STEP_LIMIT)
            throw new IllegalStateException("native wrapper exceeded a frozen work bound");
    }

    void finishGroup() throws IOException {
        if(!open) throw new IllegalStateException("no measured group is open");
        long duration=elapsed(groupStarted,System.nanoTime());
        int measuredUnits=groupUnits;
        open=false;
        groups++;
        groupMeasures.add(new GroupMeasure(duration,measuredUnits));
        maxGroupNanos=Math.max(maxGroupNanos,duration);
        maxGroupUnits=Math.max(maxGroupUnits,measuredUnits);
        if(duration>GROUP_NANOS)
            throw new IOException("native wrapper group exceeded five seconds");
        if(elapsed(started,System.nanoTime())>OP_NANOS)
            throw new IOException("failed-first process exceeded 90 seconds");
        if(groups+priorGroups>MAX_GROUPS)
            throw new IOException("native wrapper exceeded 640 measured groups");
    }
}

/* Paste-ready members for FailureCohortRoutingStudy. Keep this as the isolated
 * source fragment so the native owner can integrate it without editing the
 * other bounded inputs. */
private static Map<String,Object> physicalLayout(TroubleshootBoard board,
        PcbBoardLayout layout,Meter meter,FreshRun run) throws Exception {
    if(board==null || layout==null || meter==null || run==null)
        throw new IOException("missing physical layout snapshot input");
    meter.beginGroup();
    Throwable primary=null;
    try {
        return physicalLayoutBody(board,layout,meter,run);
    } catch(Exception failure) {
        primary=failure;
        throw failure;
    } catch(Error failure) {
        primary=failure;
        throw failure;
    } finally {
        if(meter.open) {
            try {
                meter.finishGroup();
            } catch(RuntimeException closeFailure) {
                if(primary!=null) primary.addSuppressed(closeFailure);
                else throw closeFailure;
            } catch(Error closeFailure) {
                if(primary!=null) primary.addSuppressed(closeFailure);
                else throw closeFailure;
            }
        }
    }
}

private static Map<String,Object> physicalLayoutBody(TroubleshootBoard board,
        PcbBoardLayout layout,Meter meter,FreshRun run) throws Exception {
    int padCount=layoutCount(layout,"pads");
    int componentCount=layoutCount(layout,"components");
    int traceCount=layoutCount(layout,"traces");
    int labelCount=layoutCount(layout,"silkscreenLabels");
    int regionCount=layoutCount(layout,"regions");
    int holeCount=layoutCount(layout,"holes");
    long rootEntries=(long)padCount+componentCount+traceCount+labelCount+regionCount+holeCount;
    if(rootEntries!=layoutEntryCount(layout) || rootEntries>COPY_CHUNK)
        throw new IOException("whole physical layout snapshot exceeds one 4096-entry copy chunk");
    physicalRequireCount(padCount,"pads");
    physicalRequireCount(componentCount,"components");
    physicalRequireCount(traceCount,"traces");
    physicalRequireCount(labelCount,"silkscreen labels");
    physicalRequireCount(regionCount,"regions");
    physicalRequireCount(holeCount,"holes");

    PcbPlacementConstraints constraints=board.getPlacementConstraints();
    if(constraints==null) throw new IOException("returned board has no placement constraints");
    int partCount=physicalCollectionCount(constraints,"parts");
    int barrierCount=physicalCollectionCount(constraints,"barriers");
    physicalRequireCount(partCount,"placement parts");
    physicalRequireCount(barrierCount,"placement barriers");
    if(partCount!=componentCount)
        throw new IOException("returned placement constraints do not cover every component");

    physicalReserveCopy(meter,run,padCount);
    Vector<PcbPadPlacement> pads=layout.getPads();
    physicalRequireActualCount(pads.size(),padCount,"pads");
    physicalReserveCopy(meter,run,componentCount);
    Vector<PcbComponentPlacement> components=layout.getComponents();
    physicalRequireActualCount(components.size(),componentCount,"components");
    physicalReserveCopy(meter,run,traceCount);
    Vector<PcbTraceGeometry> traces=layout.getTraces();
    physicalRequireActualCount(traces.size(),traceCount,"traces");
    physicalReserveCopy(meter,run,holeCount);
    Vector<PcbBoardHole> holes=layout.getHoles();
    physicalRequireActualCount(holes.size(),holeCount,"holes");
    physicalReserveCopy(meter,run,labelCount);
    Vector<PcbSilkscreenLabel> labels=layout.getSilkscreenLabels();
    physicalRequireActualCount(labels.size(),labelCount,"silkscreen labels");
    physicalReserveCopy(meter,run,regionCount);
    Vector<PcbLayoutRegion> regions=layout.getRegions();
    physicalRequireActualCount(regions.size(),regionCount,"regions");
    physicalReserveCopy(meter,run,partCount);
    Vector<PcbPlacementConstraints.Part> parts=constraints.getParts();
    physicalRequireActualCount(parts.size(),partCount,"placement parts");
    physicalReserveCopy(meter,run,barrierCount);
    Vector<PcbPlacementConstraints.Barrier> barriers=constraints.getBarriers();
    physicalRequireActualCount(barriers.size(),barrierCount,"placement barriers");

    reserveFingerprintCopies(run,meter,layout);
    String geometryCanonical=layout.geometryFingerprint();
    if(geometryCanonical==null) throw new IOException("returned layout omitted its canonical geometry");
    String geometrySha256=physicalSha256(geometryCanonical,meter,run);

    ArrayList<Map<String,Object>> padRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,padCount);
    for(PcbPadPlacement pad:pads) {
        physicalRecord(meter,run);
        BoardPad logical=board.getPad(pad.getPadId());
        if(logical==null || !pad.getPadId().equals(logical.getId()))
            throw new IOException("returned physical pad is absent from the actual board");
        Map<String,Object> row=object();
        physicalPut(row,"padId",pad.getPadId(),meter,run);
        physicalPut(row,"netId",logical.getNetId(),meter,run);
        physicalPut(row,"componentId",logical.getComponentId(),meter,run);
        physicalPut(row,"terminalId",logical.getTerminalId(),meter,run);
        physicalPut(row,"x",Integer.valueOf(pad.getX()),meter,run);
        physicalPut(row,"y",Integer.valueOf(pad.getY()),meter,run);
        physicalPut(row,"padBounds",physicalRectangle(pad.getPadBounds(),meter,run),meter,run);
        physicalPut(row,"probeBounds",physicalRectangle(pad.getProbeBounds(),meter,run),meter,run);
        physicalPut(row,"escapeDirection",physicalPair(pad.getEscapeDx(),pad.getEscapeDy(),meter,run),meter,run);
        physicalPut(row,"escapeLength",Integer.valueOf(pad.getEscapeLength()),meter,run);
        physicalPut(row,"attachment",pad.getAttachment().name(),meter,run);
        physicalPut(row,"mountingSide",pad.getMountingSide().name(),meter,run);
        physicalPut(row,"exposure",pad.getExposure().name(),meter,run);
        physicalPut(row,"topCopper",Boolean.valueOf(PcbCopperAccess.hasCopper(pad,PcbCopperLayer.TOP)),meter,run);
        physicalPut(row,"bottomCopper",Boolean.valueOf(PcbCopperAccess.hasCopper(pad,PcbCopperLayer.BOTTOM)),meter,run);
        padRows.add(row);
    }

    ArrayList<Map<String,Object>> componentRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,componentCount);
    for(PcbComponentPlacement component:components) {
        PhysicalPackage physical=component.getPhysicalPackage();
        PhysicalPackageGeometry geometry=component.getPhysicalGeometry();
        int terminalCount=physical.getTerminalCount();
        physicalRequireCount(terminalCount,"component terminal IDs");
        physicalReserveCopy(meter,run,terminalCount);
        Vector<String> packageTerminalIds=physical.getTerminalIds();
        physicalRequireActualCount(packageTerminalIds.size(),terminalCount,"component terminal IDs");
        physicalReserveCopy(meter,run,terminalCount);
        Vector<PhysicalPackageGeometry.Terminal> terminals=geometry.getTerminals();
        physicalRequireActualCount(terminals.size(),terminalCount,"physical terminal geometry");
        PcbPlacementConstraints.Part part=constraints.get(component.getComponentId());
        if(part==null) throw new IOException("returned component is missing its placement constraint");

        /* PcbComponentPlacement.geometryFingerprint() copies terminal IDs,
         * terminal geometry, a terminal count vector, then each terminal.
         * Pre-account those native-owned copies before invoking the API. */
        meter.reserve((long)terminalCount*terminalCount+4L*terminalCount+128);
        for(int copy=0;copy<terminalCount+3;copy++) physicalReserveCopy(meter,run,terminalCount);
        for(int terminal=0;terminal<terminalCount;terminal++) physicalReserveCopy(meter,run,1);
        String componentGeometry=component.geometryFingerprint();
        if(componentGeometry==null) throw new IOException("component geometry fingerprint is missing");

        ArrayList<Map<String,Object>> terminalRows=new ArrayList<Map<String,Object>>();
        physicalReserveCopy(meter,run,terminalCount);
        for(int index=0;index<terminalCount;index++) {
            PhysicalPackageGeometry.Terminal terminal=terminals.get(index);
            String terminalId=terminal.getTerminalId();
            if(!terminalId.equals(packageTerminalIds.get(index)))
                throw new IOException("package and physical geometry terminal order differ");
            Point escapeDirection=component.getPose().toBoardDirection(
                terminal.getEscapeDx(),terminal.getEscapeDy());
            physicalRecord(meter,run);
            Map<String,Object> row=object();
            physicalPut(row,"terminalId",terminalId,meter,run);
            physicalPut(row,"terminalDomain",part.terminalDomain(terminalId),meter,run);
            physicalPut(row,"padPoint",physicalPoint(component.getPadPoint(index),meter,run),meter,run);
            physicalPut(row,"padBounds",physicalRectangle(component.getPadBounds(index),meter,run),meter,run);
            physicalPut(row,"boardPadProbeCenter",physicalPoint(component.getBoardPadProbeCenter(index),meter,run),meter,run);
            physicalPut(row,"boardPadProbeBounds",physicalRectangle(component.getBoardPadProbeBounds(index),meter,run),meter,run);
            physicalPut(row,"connectedLeadEnd",physicalPoint(component.getConnectedBoardEndPoint(index),meter,run),meter,run);
            physicalPut(row,"connectedLeadBody",physicalPoint(component.getLeadBodyPoint(index),meter,run),meter,run);
            physicalPut(row,"connectedLeadBounds",physicalRectangle(component.getLeadBounds(index),meter,run),meter,run);
            physicalPut(row,"connectedProbeCenter",physicalPoint(component.getComponentLeadProbeCenter(index),meter,run),meter,run);
            physicalPut(row,"connectedProbeBounds",physicalRectangle(component.getComponentLeadProbeBounds(index),meter,run),meter,run);
            physicalPut(row,"liftedLeadEnd",physicalPoint(component.getLiftedFreeEndPoint(index),meter,run),meter,run);
            physicalPut(row,"liftedLeadBody",physicalPoint(component.getLeadBodyPoint(index,true),meter,run),meter,run);
            physicalPut(row,"liftedLeadBounds",physicalRectangle(component.getLeadBounds(index,true),meter,run),meter,run);
            physicalPut(row,"liftedProbeCenter",physicalPoint(component.getComponentLeadProbeCenter(index,true),meter,run),meter,run);
            physicalPut(row,"liftedProbeBounds",physicalRectangle(component.getComponentLeadProbeBounds(index,true),meter,run),meter,run);
            physicalPut(row,"escapeDirection",physicalPoint(escapeDirection,meter,run),meter,run);
            physicalPut(row,"escapeLength",Integer.valueOf(terminal.getEscapeLength()),meter,run);
            physicalPut(row,"attachment",terminal.getAttachment().name(),meter,run);
            terminalRows.add(row);
        }
        Map<String,Object> row=object();
        physicalPut(row,"componentId",component.getComponentId(),meter,run);
        physicalPut(row,"packageId",physical.getId(),meter,run);
        physicalPut(row,"poseFingerprint",component.getPose().fingerprint(),meter,run);
        physicalPut(row,"x",Integer.valueOf(component.getX()),meter,run);
        physicalPut(row,"y",Integer.valueOf(component.getY()),meter,run);
        physicalPut(row,"width",Integer.valueOf(component.getWidth()),meter,run);
        physicalPut(row,"height",Integer.valueOf(component.getHeight()),meter,run);
        physicalPut(row,"rotation",component.getRotation().name(),meter,run);
        physicalPut(row,"mountingSide",component.getMountingSide().name(),meter,run);
        physicalPut(row,"bounds",physicalRectangle(component.getBounds(),meter,run),meter,run);
        physicalPut(row,"bodyBounds",physicalRectangle(component.getBodyBounds(),meter,run),meter,run);
        physicalPut(row,"keepOut",physicalRectangle(component.getKeepOut(),meter,run),meter,run);
        physicalPut(row,"routingCourtyard",physicalRectangle(component.getRoutingCourtyard(),meter,run),meter,run);
        physicalPut(row,"selectionEnvelope",physicalRectangle(component.getSelectionEnvelope(),meter,run),meter,run);
        physicalPut(row,"dragEnvelope",physicalRectangle(component.getDragEnvelope(),meter,run),meter,run);
        physicalPut(row,"geometryContractVersion",Integer.valueOf(component.getGeometryContractVersionValue()),meter,run);
        physicalPut(row,"geometryVariantKey",component.getGeometryVariantKey(),meter,run);
        physicalPut(row,"geometryTransformKey",component.getGeometryTransformKey(),meter,run);
        physicalPut(row,"geometryCanonical",componentGeometry,meter,run);
        physicalPut(row,"underpass",physicalRectangle(component.getUnderpass(),meter,run),meter,run);
        RaisedCrossoverGeometry raised=geometry.getRaisedCrossover();
        physicalPut(row,"raisedCrossover",raised==null?null:raised.fingerprint(),meter,run);
        PhysicalPackageGeometry.IsolationBody isolation=geometry.getIsolationBody();
        physicalPut(row,"isolationBodyCanonical",isolation==null?null:isolation.canonical(),meter,run);
        physicalPut(row,"terminals",terminalRows,meter,run);
        componentRows.add(row);
    }

    ArrayList<Map<String,Object>> regionRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,regionCount);
    for(PcbLayoutRegion region:regions) {
        int count=physicalCollectionCount(region,"components");
        physicalRequireCount(count,"region component IDs");
        physicalReserveCopy(meter,run,count);
        Vector<String> componentIds=region.getComponentIds();
        physicalRequireActualCount(componentIds.size(),count,"region component IDs");
        physicalChargeStringValues(meter,run,componentIds);
        physicalRecord(meter,run);
        Map<String,Object> row=object();
        physicalPut(row,"id",region.id,meter,run);
        physicalPut(row,"label",region.label,meter,run);
        physicalPut(row,"componentIds",componentIds,meter,run);
        physicalPut(row,"bounds",physicalRectangle(region.bounds(layout),meter,run),meter,run);
        regionRows.add(row);
    }

    ArrayList<Map<String,Object>> labelRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,labelCount);
    for(PcbSilkscreenLabel label:labels) {
        physicalRecord(meter,run);
        Map<String,Object> row=object();
        physicalPut(row,"id",label.getId(),meter,run);
        physicalPut(row,"text",label.getText(),meter,run);
        physicalPut(row,"bounds",physicalRectangle(label.getBounds(),meter,run),meter,run);
        physicalPut(row,"baselineX",Integer.valueOf(label.getBaselineX()),meter,run);
        physicalPut(row,"baselineY",Integer.valueOf(label.getBaselineY()),meter,run);
        physicalPut(row,"fontSize",Integer.valueOf(label.getFontSize()),meter,run);
        physicalPut(row,"bold",Boolean.valueOf(label.isBold()),meter,run);
        physicalPut(row,"targetPadId",label.getTargetPadId(),meter,run);
        labelRows.add(row);
    }

    ArrayList<Map<String,Object>> partRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,partCount);
    for(PcbPlacementConstraints.Part part:parts) {
        int terminalDomainCount=physicalCollectionCount(part,"terminalDomains");
        physicalRequireCount(terminalDomainCount,"declared placement terminal domains");
        physicalReserveCopy(meter,run,terminalDomainCount);
        Vector<PcbPlacementConstraints.TerminalDomain> declaredDomains=part.getTerminalDomains();
        physicalRequireActualCount(declaredDomains.size(),terminalDomainCount,
            "declared placement terminal domains");
        physicalReserveCopy(meter,run,terminalDomainCount);
        ArrayList<Map<String,Object>> terminalDomainRows=new ArrayList<Map<String,Object>>();
        for(PcbPlacementConstraints.TerminalDomain declaration:declaredDomains) {
            physicalRecord(meter,run);
            Map<String,Object> terminalDomain=object();
            physicalPut(terminalDomain,"terminalId",declaration.terminalId,meter,run);
            physicalPut(terminalDomain,"domainId",declaration.domainId,meter,run);
            terminalDomainRows.add(terminalDomain);
        }
        PcbComponentPlacement component=layout.getComponent(part.componentId);
        if(component==null) throw new IOException("placement constraint references a missing component");
        int courtyardCount=component.getPhysicalGeometry().getIsolationBody()==null?1:2;
        physicalReserveCopy(meter,run,courtyardCount);
        TreeMap<String,Rectangle> courtyards=constraints.domainCourtyards(component);
        physicalRequireActualCount(courtyards.size(),courtyardCount,"domain courtyards");
        physicalReserveCopy(meter,run,courtyardCount);
        ArrayList<Map<String,Object>> courtyardRows=new ArrayList<Map<String,Object>>();
        for(Map.Entry<String,Rectangle> entry:courtyards.entrySet()) {
            physicalRecord(meter,run);
            Map<String,Object> courtyard=object();
            physicalPut(courtyard,"domainId",entry.getKey(),meter,run);
            physicalPut(courtyard,"bounds",physicalRectangle(entry.getValue(),meter,run),meter,run);
            courtyardRows.add(courtyard);
        }
        physicalRecord(meter,run);
        Map<String,Object> row=object();
        physicalPut(row,"componentId",part.componentId,meter,run);
        physicalPut(row,"regionId",part.regionId,meter,run);
        physicalPut(row,"regionLabel",part.regionLabel,meter,run);
        physicalPut(row,"domainId",part.domainId,meter,run);
        physicalPut(row,"anchor",part.anchor.name(),meter,run);
        physicalPut(row,"accessMargin",Integer.valueOf(part.accessMargin),meter,run);
        physicalPut(row,"terminalDomains",terminalDomainRows,meter,run);
        physicalPut(row,"domainCourtyards",courtyardRows,meter,run);
        partRows.add(row);
    }

    ArrayList<Map<String,Object>> barrierRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,barrierCount);
    for(PcbPlacementConstraints.Barrier barrier:barriers) {
        physicalRecord(meter,run);
        Map<String,Object> row=object();
        physicalPut(row,"firstDomain",barrier.firstDomain,meter,run);
        physicalPut(row,"secondDomain",barrier.secondDomain,meter,run);
        physicalPut(row,"clearance",Integer.valueOf(barrier.clearance),meter,run);
        barrierRows.add(row);
    }

    PcbTraceWidthProfile widthProfile=constraints.getTraceWidthProfile();
    if(widthProfile==null) throw new IOException("returned board omitted its actual trace width profile");
    BoardNet.RoutingRole[] roles=BoardNet.RoutingRole.values();
    physicalReserveCopy(meter,run,roles.length);
    Map<String,Object> roleWidths=object();
    for(BoardNet.RoutingRole role:roles)
        physicalPut(roleWidths,role.name(),Integer.valueOf(widthProfile.widthFor(role)),meter,run);

    Map<String,Object> placement=object();
    physicalPut(placement,"physicalPolicyId",constraints.getPhysicalPolicyId(),meter,run);
    physicalPut(placement,"physicalPolicyVersion",Integer.valueOf(constraints.getPhysicalPolicyVersion()),meter,run);
    physicalPut(placement,"placementDensityVersion",Integer.valueOf(constraints.placementDensityVersion),meter,run);
    physicalPut(placement,"placementPackingVersion",Integer.valueOf(constraints.getPlacementPackingVersion()),meter,run);
    physicalPut(placement,"requiresTwoLayerRouting",Boolean.valueOf(constraints.requiresTwoLayerRouting),meter,run);
    physicalPut(placement,"routingLayer",constraints.routingLayer.name(),meter,run);
    physicalPut(placement,"traceWidthProfileIdentity",widthProfile.getIdentity(),meter,run);
    physicalPut(placement,"traceWidthProfileCanonical",widthProfile.canonical(),meter,run);
    physicalPut(placement,"parts",partRows,meter,run);
    physicalPut(placement,"barriers",barrierRows,meter,run);

    Map<String,Object> clearance=object();
    physicalPut(clearance,"traceWidth",Integer.valueOf(PcbTraceRules.TRACE_WIDTH),meter,run);
    physicalPut(clearance,"copperHeight",Integer.valueOf(PcbTraceRules.COPPER_HEIGHT),meter,run);
    physicalPut(clearance,"minimumVisibleClearance",Integer.valueOf(PcbTraceRules.MIN_VISIBLE_CLEARANCE),meter,run);
    physicalPut(clearance,"minimumCenterlineClearance",Integer.valueOf(PcbTraceRules.MIN_CENTERLINE_CLEARANCE),meter,run);
    physicalPut(clearance,"routingGridClearanceCells",Integer.valueOf(PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS),meter,run);
    physicalPut(clearance,"roleWidths",roleWidths,meter,run);
    physicalPut(clearance,"maximumTransitions",Integer.valueOf(run.maximumTransitions),meter,run);
    physicalPut(clearance,"maximumVias",Integer.valueOf(run.maximumVias),meter,run);

    ArrayList<Map<String,Object>> traceRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,traceCount);
    for(PcbTraceGeometry trace:traces) {
        int pointCount=physicalTracePointCount(trace);
        physicalRequireCount(pointCount,"trace point arrays");
        physicalReserveCopy(meter,run,pointCount);
        physicalReserveCopy(meter,run,pointCount);
        int[] x=trace.getXPoints();
        int[] y=trace.getYPoints();
        physicalRequireActualCount(x.length,pointCount,"trace x points");
        physicalRequireActualCount(y.length,pointCount,"trace y points");
        physicalReserveCopy(meter,run,pointCount);
        physicalReserveCopy(meter,run,pointCount);
        ArrayList<Integer> xs=new ArrayList<Integer>(pointCount);
        ArrayList<Integer> ys=new ArrayList<Integer>(pointCount);
        for(int index=0;index<pointCount;index++) {
            xs.add(Integer.valueOf(x[index]));
            ys.add(Integer.valueOf(y[index]));
        }
        physicalRecord(meter,run);
        Map<String,Object> row=object();
        physicalPut(row,"sourceId",trace.getSourceId(),meter,run);
        physicalPut(row,"netId",trace.getNetId(),meter,run);
        physicalPut(row,"startPadId",trace.getStartPadId(),meter,run);
        physicalPut(row,"endPadId",trace.getEndPadId(),meter,run);
        physicalPut(row,"layer",trace.getLayer().name(),meter,run);
        physicalPut(row,"exposure",trace.getExposure().name(),meter,run);
        physicalPut(row,"width",Integer.valueOf(trace.getWidth()),meter,run);
        physicalPut(row,"x",xs,meter,run);
        physicalPut(row,"y",ys,meter,run);
        traceRows.add(row);
    }

    int viaCount=0;
    ArrayList<Map<String,Object>> holeRows=new ArrayList<Map<String,Object>>();
    physicalReserveCopy(meter,run,holeCount);
    for(PcbBoardHole hole:holes) {
        if(hole.kind==PcbBoardHole.Kind.VIA) viaCount++;
        physicalRecord(meter,run);
        Map<String,Object> row=object();
        physicalPut(row,"id",hole.id,meter,run);
        physicalPut(row,"netId",hole.netId,meter,run);
        physicalPut(row,"kind",hole.kind.name(),meter,run);
        physicalPut(row,"exposure",hole.exposure.name(),meter,run);
        physicalPut(row,"x",Integer.valueOf(hole.x),meter,run);
        physicalPut(row,"y",Integer.valueOf(hole.y),meter,run);
        physicalPut(row,"drillRadius",Integer.valueOf(hole.drillRadius),meter,run);
        physicalPut(row,"landRadius",Integer.valueOf(hole.landRadius),meter,run);
        holeRows.add(row);
    }
    if(layout.getHoleCount()!=holeCount)
        throw new IOException("returned hole count changed during physical snapshot");

    Map<String,Object> result=object();
    physicalPut(result,"geometrySha256",geometrySha256,meter,run);
    physicalPut(result,"width",Integer.valueOf(layout.getWidth()),meter,run);
    physicalPut(result,"height",Integer.valueOf(layout.getHeight()),meter,run);
    physicalPut(result,"outline",physicalRectangle(layout.getBoardOutline(),meter,run),meter,run);
    physicalPut(result,"partsTray",physicalRectangle(layout.getPartsTray(),meter,run),meter,run);
    physicalPut(result,"pads",padRows,meter,run);
    physicalPut(result,"components",componentRows,meter,run);
    physicalPut(result,"regions",regionRows,meter,run);
    physicalPut(result,"labels",labelRows,meter,run);
    physicalPut(result,"placementConstraints",placement,meter,run);
    physicalPut(result,"clearanceRules",clearance,meter,run);
    physicalPut(result,"traceCount",Integer.valueOf(traceCount),meter,run);
    physicalPut(result,"holeCount",Integer.valueOf(holeCount),meter,run);
    physicalPut(result,"viaCount",Integer.valueOf(viaCount),meter,run);
    physicalPut(result,"traces",traceRows,meter,run);
    physicalPut(result,"holes",holeRows,meter,run);
    return result;
}

private static void physicalRequireCount(int count,String what) throws IOException {
    if(count<0 || count>COPY_CHUNK)
        throw new IOException("physical snapshot copy exceeds one bounded chunk: "+what);
}

private static void physicalRequireActualCount(int actual,int expected,String what) throws IOException {
    if(actual!=expected) throw new IOException("physical snapshot count changed: "+what);
}

private static void physicalReserveCopy(Meter meter,FreshRun run,int count) throws IOException {
    physicalRequireCount(count,"copy entries");
    reserveCopy(run,meter,count);
}

private static void physicalRecord(Meter meter,FreshRun run) throws IOException {
    physicalCharge(meter,run,1L);
}

private static void physicalChargeStringValues(Meter meter,FreshRun run,
        Iterable<String> values) throws IOException {
    long units=0;
    for(String value:values) {
        if(value==null) throw new IOException("physical snapshot contains a null string entry");
        units+=physicalUtf8Units(value);
        if(units>Integer.MAX_VALUE) throw new IOException("physical snapshot string work overflow");
    }
    if(units>0) physicalCharge(meter,run,units);
}

private static long physicalUtf8Units(String value) {
    if(value==null || value.length()==0) return 0L;
    int bytes=value.getBytes(UTF8).length;
    return (bytes+4095L)/4096L;
}

private static void physicalCharge(Meter meter,FreshRun run,long units) throws IOException {
    if(units<0 || units>Integer.MAX_VALUE) throw new IOException("physical snapshot work overflow");
    if(units==0) return;
    long total=run.wrapperUnits+units;
    if(total<run.wrapperUnits) throw new IOException("physical snapshot wrapper work overflow");
    meter.reserve(units);
    meter.charge((int)units);
    run.wrapperUnits=total;
}

private static void physicalPut(Map<String,Object> target,String key,Object value,
        Meter meter,FreshRun run) throws IOException {
    long units=1L+physicalUtf8Units(key);
    if(value instanceof String) units+=physicalUtf8Units((String)value);
    physicalCharge(meter,run,units);
    target.put(key,value);
}

private static List<Integer> physicalPair(int first,int second,Meter meter,FreshRun run)
        throws IOException {
    physicalReserveCopy(meter,run,2);
    ArrayList<Integer> pair=new ArrayList<Integer>(2);
    pair.add(Integer.valueOf(first)); pair.add(Integer.valueOf(second));
    return pair;
}

private static List<Integer> physicalPoint(Point point,Meter meter,FreshRun run)
        throws IOException {
    return point==null?null:physicalPair(point.x,point.y,meter,run);
}

private static List<Integer> physicalRectangle(Rectangle rectangle,Meter meter,FreshRun run)
        throws IOException {
    if(rectangle==null) return null;
    physicalReserveCopy(meter,run,4);
    ArrayList<Integer> bounds=new ArrayList<Integer>(4);
    bounds.add(Integer.valueOf(rectangle.x)); bounds.add(Integer.valueOf(rectangle.y));
    bounds.add(Integer.valueOf(rectangle.width)); bounds.add(Integer.valueOf(rectangle.height));
    return bounds;
}

private static String physicalSha256(String value,Meter meter,FreshRun run) throws Exception {
    byte[] bytes=value.getBytes(UTF8);
    physicalCharge(meter,run,(bytes.length+4095L)/4096L);
    byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
    final char[] hex="0123456789abcdef".toCharArray();
    char[] encoded=new char[digest.length*2];
    for(int index=0;index<digest.length;index++) {
        int unsigned=digest[index]&255;
        encoded[index*2]=hex[unsigned>>>4];
        encoded[index*2+1]=hex[unsigned&15];
    }
    return new String(encoded);
}

    private static final class Inputs {
        final String runId,manifestSha,wireSha;
        final Path data;
        Inputs(String runId,String manifestSha,String wireSha,Path data) {
            this.runId=runId; this.manifestSha=manifestSha; this.wireSha=wireSha; this.data=data;
        }
    }
    private static final class InputRejected extends Exception {
        final String code;
        InputRejected(String code) { super(code); this.code=code; }
    }
    private static final class FreshRun {
        String kind,disposition,reason,priorPromotedNetId,policy,branchStrategy,netStrategy;
        int maximumExpansions,maximumVias,maximumTransitions,policyVias,viaCost;
        int finalPass,finalPasses,expanded,searchCount,advanceCalls,copyChunks,groups,maximumUnitsPerGroup;
        long ownerUnits,wrapperUnits,copiedEntries,cleanupNanos,maxGroupNanos;
        boolean complete,ownerComplete,roleWidths,capturedOrderingStream,instrumentationModeDifference;
        boolean routingSettingsMatch,orderMatchesCapture,referencesDropped;
        long failureObservationUnits,terminalEvidenceUnits;
        TerminalFailure terminalFailure;
        List<String> capturedNetIds,computedNetIds,requestedCohortNetIds,requestedNetIds,traversedNetIds;
        Map<String,Object> layout,finalizers,json;
    }

    /** Count/charge every visited value, map key and UTF-8 string chunk before export. */
    private static void chargeResultTree(Object value,Meter meter,int depth,long[] visits)
            throws IOException {
        if(depth>64 || ++visits[0]>COPY_LIMIT)
            throw new IOException("result tree exceeds its bounded traversal");
        meter.reserve(1);
        meter.charge(1);
        if(value instanceof Map) {
            for(Map.Entry<?,?> entry:((Map<?,?>)value).entrySet()) {
                if(!(entry.getKey() instanceof String)) throw new IOException("non-string result key");
                String key=(String)entry.getKey();
                long keyUnits=1L+hashUnits(utf8(key).length);
                meter.reserve(keyUnits); meter.charge(keyUnits);
                chargeResultTree(entry.getValue(),meter,depth+1,visits);
            }
        } else if(value instanceof List) {
            for(Object item:(List<?>)value) chargeResultTree(item,meter,depth+1,visits);
        } else if(value instanceof String) {
            int units=hashUnits(utf8((String)value).length);
            meter.reserve(units); meter.charge(units);
        } else if(value!=null && !(value instanceof Number) && !(value instanceof Boolean)) {
            throw new IOException("unsupported result tree value");
        }
    }

    private static void validateOutputFiles(Path data,boolean resultPresent) throws IOException {
        if(data==null || !data.isAbsolute() || !data.normalize().equals(data) ||
                !Files.isDirectory(data,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(data) ||
                !data.toRealPath().equals(data.toAbsolutePath().normalize()))
            throw new IOException("native data directory changed identity");
        int count=0;
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(data)) {
            for(Path entry:entries) {
                String name=entry.getFileName().toString();
                if(!"op-1.jsonl".equals(name) && !(resultPresent && "result.json".equals(name)))
                    throw new IOException("unexpected native output file");
                if(!Files.isRegularFile(entry,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(entry))
                    throw new IOException("native output is not a regular owned file");
                count++;
            }
        }
        if(count!=(resultPresent?2:1)) throw new IOException("native output file census changed");
    }

    private static void exportResult(Path data,Map<String,Object> result,long started,Meter meter)
            throws Exception {
        @SuppressWarnings("unchecked") Map<String,Object> work=(Map<String,Object>)result.get("work");
        int preGroups=((Number)work.get("groupsBeforeExport")).intValue();
        long preUnits=((Number)work.get("unitsBeforeExport")).longValue();
        long preMax=((Number)work.get("maxGroupNanosBeforeExport")).longValue();
        int preMaxUnits=((Number)work.get("maximumUnitsPerGroupBeforeExport")).intValue();
        long exportStart=System.nanoTime();
        meter.beginGroup();
        work.put("elapsedCutoffNanos",Long.valueOf(elapsed(started,exportStart)));
        validateOutputFiles(data,false);
        byte[] raw=utf8(json(result)+"\n");
        if(raw.length<2 || raw.length>RESULT_LINE_LIMIT || raw.length>RESULT_FILE_LIMIT)
            throw new IOException("result.json exceeds its unchanged byte/line bound");
        int exportUnits=5*hashUnits(raw.length)+3;
        meter.charge(exportUnits);
        Path path=data.resolve("result.json");
        try(OutputStream output=new BufferedOutputStream(Files.newOutputStream(path,
                new OpenOption[] {StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE}))) {
            output.write(raw); output.flush();
        }
        if(Files.size(path)!=raw.length) throw new IOException("result size changed after close");
        byte[] reread=Files.readAllBytes(path);
        if(!java.util.Arrays.equals(raw,reread)) throw new IOException("result changed after close");
        String digest=sha256(reread);
        validateOutputFiles(data,true);
        Map<String,Object> marker=object();
        marker.put("schema",MARKER_SCHEMA); marker.put("status","COMPLETED_DIAGNOSTIC");
        marker.put("resultSha256",digest); marker.put("resultBytes",Integer.valueOf(raw.length));
        marker.put("elapsedNanos",Long.valueOf(0)); marker.put("finalExportNanos",Long.valueOf(0));
        marker.put("totalGroups",Integer.valueOf(preGroups+1));
        marker.put("totalUnits",Long.valueOf(preUnits+exportUnits));
        marker.put("maxGroupNanos",Long.valueOf(0));
        marker.put("maximumUnitsPerGroup",Integer.valueOf(Math.max(preMaxUnits,exportUnits)));
        // Preflight the complete marker shape while the final measured group is open.
        if(utf8(json(marker)+"\n").length>65536) throw new IOException("marker line exceeds bound");
        meter.finishGroup();
        GroupMeasure last=meter.groupMeasures.get(meter.groupMeasures.size()-1);
        long exportElapsed=elapsed(exportStart,System.nanoTime());
        long allElapsed=elapsed(started,System.nanoTime());
        if(preGroups+1>MAX_GROUPS || exportElapsed>GROUP_NANOS ||
                Math.max(preMax,exportElapsed)>GROUP_NANOS || allElapsed>OP_NANOS ||
                last.units!=exportUnits || exportUnits>STEP_LIMIT)
            throw new IOException("complete final export exceeds a frozen work bound");
        marker.put("elapsedNanos",Long.valueOf(allElapsed));
        marker.put("finalExportNanos",Long.valueOf(exportElapsed));
        marker.put("maxGroupNanos",Long.valueOf(Math.max(preMax,exportElapsed)));
        String line=json(marker);
        if(elapsed(exportStart,System.nanoTime())>GROUP_NANOS || elapsed(started,System.nanoTime())>OP_NANOS)
            throw new IOException("final marker preflight exceeds frozen bound");
        System.out.println(line);
    }

/*
 * CanaryMethods.fragment.java
 * Paste these methods into FailureCohortRoutingStudy. Shared contract helpers
 * (checkedStableCohort, validateFeedbackIds, trialDisposition) are root-owned.
 */
private static void runCanary() throws Exception {
    final long started=System.nanoTime();
    final Meter meter=new Meter(started);

    meter.beginGroup();
    FailedFirstFixtures.Fixture openFixture=FailedFirstFixtures.open();
    if(layoutEntryCount(openFixture.layout)>COPY_CHUNK)
        throw new IOException("open canary fixture exceeded the one-copy prebound");
    meter.charge(1);
    meter.finishGroup();
    long openSessionStart=meter.sessionConstructorEntries;
    FreshRun openRun=runFresh(openFixture.board,openFixture.layout,null,
        java.util.Arrays.asList("NET_A"),"OBSERVED_FAILURE_CONTROL",meter,started,null);
    long openSessionEntries=meter.sessionConstructorEntries-openSessionStart;
    if(openSessionEntries!=1)
        throw new IllegalStateException("open canary did not enter exactly one native Session constructor");
    meter.beginGroup();
    requireCanaryOpen(openRun);
    Map<String,Object> openDisposition=trialDisposition("SKIPPED","CONTROL_FOUND",
        "NET_A",null,null,null,false);
    requireCanaryTrialDisposition(openDisposition,"SKIPPED","CONTROL_FOUND",
        "NET_A",null,null,null,false);
    meter.charge(36L+openRun.traversedNetIds.size());
    meter.finishGroup();
    Map<String,Object> openPositive=object();
    openPositive.put("control",openRun.json);
    openPositive.put("trialDisposition",openDisposition);
    requireCanaryKeys(openPositive,"control","trialDisposition");

    meter.beginGroup();
    FailedFirstFixtures.Fixture blockedFixture=FailedFirstFixtures.blocked();
    if(layoutEntryCount(blockedFixture.layout)>COPY_CHUNK)
        throw new IOException("blocked canary fixture exceeded the one-copy prebound");
    meter.charge(1);
    meter.finishGroup();
    long blockedSessionStart=meter.sessionConstructorEntries;
    FreshRun blockedRun=runFresh(blockedFixture.board,blockedFixture.layout,null,
        java.util.Arrays.asList("NET_A"),"OBSERVED_FAILURE_CONTROL",meter,started,null);
    long blockedControlEntries=meter.sessionConstructorEntries-blockedSessionStart;
    if(blockedControlEntries!=1)
        throw new IllegalStateException("blocked canary did not enter exactly one native control Session");
    meter.beginGroup();
    String blockedNet=requireCanaryBlocked(blockedRun);
    Map<String,Object> blockedOrdering=canaryMap(blockedRun.json.get("ordering"),"blocked ordering");
    List<String> blockedSource=canaryStringList(blockedOrdering.get("computedNetIds"),
        "blocked computed vector");
    long beforeFeedback=meter.sessionConstructorEntries;
    boolean repeatedRejected=false;
    String feedbackCode=null;
    try {
        validateFeedbackIds(blockedSource,"NET_A",blockedNet);
    } catch(IllegalArgumentException expected) {
        feedbackCode=expected.getMessage();
        repeatedRejected="REPEATED_FAILURE_ID".equals(feedbackCode);
    }
    long rejectedTrialSessions=meter.sessionConstructorEntries-beforeFeedback;
    if(!repeatedRejected || rejectedTrialSessions!=0)
        throw new IllegalStateException("repeated blocked feedback was not rejected before trial Session construction");
    Map<String,Object> repeatedFeedback=object();
    repeatedFeedback.put("capturedFailedNetId","NET_A");
    repeatedFeedback.put("controlFailedNetId",blockedNet);
    repeatedFeedback.put("status","REJECTED_BEFORE_TRIAL");
    repeatedFeedback.put("reason",feedbackCode);
    repeatedFeedback.put("trialSessionCreations",Long.valueOf(rejectedTrialSessions));
    requireCanaryKeys(repeatedFeedback,"capturedFailedNetId","controlFailedNetId",
        "status","reason","trialSessionCreations");
    meter.charge(38L+blockedRun.traversedNetIds.size());
    meter.finishGroup();
    Map<String,Object> blockedNoFallback=object();
    blockedNoFallback.put("control",blockedRun.json);
    blockedNoFallback.put("repeatedFeedback",repeatedFeedback);
    blockedNoFallback.put("noFallback",Boolean.TRUE);
    requireCanaryKeys(blockedNoFallback,"control","repeatedFeedback","noFallback");

    meter.beginGroup();
    FailedFirstFixtures.Fixture faultFixture=FailedFirstFixtures.open();
    if(layoutEntryCount(faultFixture.layout)>COPY_CHUNK)
        throw new IOException("fault canary fixture exceeded the one-copy prebound");
    meter.charge(1);
    meter.finishGroup();
    FaultDiagnostics diagnostics=new FaultDiagnostics();
    Throwable thrown=null;
    long faultSessionStart=meter.sessionConstructorEntries;
    try {
        runFresh(faultFixture.board,faultFixture.layout,null,
            java.util.Arrays.asList("NET_A"),"OBSERVED_FAILURE_CONTROL",meter,started,diagnostics);
    } catch(Throwable failure) {
        thrown=failure;
    }
    long faultSessionEntries=meter.sessionConstructorEntries-faultSessionStart;
    if(meter.open) meter.finishGroup();
    meter.beginGroup();
    FaultDiagnostics.State fault=diagnostics.state;
    boolean sameThrowable=thrown!=null && thrown==fault.failure;
    if(!(thrown instanceof IllegalStateException) || !sameThrowable ||
            !"FAILED_FIRST_ACTIVE_BRANCH_CANARY".equals(thrown.getMessage()) ||
            fault.callbackCalls!=1 || fault.expandedAtThrow<1 ||
            !fault.searchPresent || !fault.branchPresent ||
            !fault.ownerDiagnosticFailureSame || fault.abortCalls!=1 ||
            !fault.sessionReleased || !fault.searchReleased ||
            !fault.candidateReleased || !fault.layoutReleased ||
            !fault.boardReleased || !fault.adapterSearchAliasesReleased ||
            !fault.referencesReleased || !fault.attemptWorkAccounted ||
            fault.failedAdvanceCalls!=1 || fault.failedFailureObservationUnits!=1 ||
            fault.failedTerminalEvidenceUnits!=0 || fault.failedOwnerUnits<0 ||
            fault.failedWrapperUnits<2L+(fault.failedOwnerUnits==0?1L:0L) ||
            fault.cleanupNanos<=0 || fault.cleanupNanos>CLEANUP_NANOS ||
            fault.session!=null || faultSessionEntries!=1)
        throw new IllegalStateException("active-branch exception cleanup/accounting canary failed");
    Map<String,Object> exception=object();
    exception.put("exceptionClass",thrown.getClass().getName());
    exception.put("message",thrown.getMessage());
    exception.put("sameThrowable",Boolean.valueOf(sameThrowable));
    exception.put("callbackCalls",Integer.valueOf(fault.callbackCalls));
    exception.put("expandedAtThrow",Integer.valueOf(fault.expandedAtThrow));
    exception.put("searchPresent",Boolean.valueOf(fault.searchPresent));
    exception.put("branchPresent",Boolean.valueOf(fault.branchPresent));
    exception.put("ownerDiagnosticFailureSame",Boolean.valueOf(fault.ownerDiagnosticFailureSame));
    exception.put("sessionReleased",Boolean.valueOf(fault.sessionReleased));
    exception.put("searchReleased",Boolean.valueOf(fault.searchReleased));
    exception.put("candidateReleased",Boolean.valueOf(fault.candidateReleased));
    exception.put("layoutReleased",Boolean.valueOf(fault.layoutReleased));
    exception.put("boardReleased",Boolean.valueOf(fault.boardReleased));
    exception.put("adapterSearchAliasesReleased",Boolean.valueOf(fault.adapterSearchAliasesReleased));
    exception.put("referencesReleased",Boolean.valueOf(fault.referencesReleased));
    exception.put("advanceCalls",Integer.valueOf(fault.failedAdvanceCalls));
    exception.put("failureObservationUnits",Long.valueOf(fault.failedFailureObservationUnits));
    exception.put("terminalEvidenceUnits",Long.valueOf(fault.failedTerminalEvidenceUnits));
    exception.put("ownerUnits",Long.valueOf(fault.failedOwnerUnits));
    exception.put("wrapperUnits",Long.valueOf(fault.failedWrapperUnits));
    exception.put("attemptWorkAccounted",Boolean.valueOf(fault.attemptWorkAccounted));
    exception.put("cleanupNanos",Long.valueOf(fault.cleanupNanos));
    requireCanaryKeys(exception,"exceptionClass","message","sameThrowable","callbackCalls",
        "expandedAtThrow","searchPresent","branchPresent","ownerDiagnosticFailureSame",
        "sessionReleased","searchReleased","candidateReleased","layoutReleased",
        "boardReleased","adapterSearchAliasesReleased","referencesReleased","advanceCalls",
        "failureObservationUnits","terminalEvidenceUnits","ownerUnits","wrapperUnits",
        "attemptWorkAccounted","cleanupNanos");
    meter.charge(56L);
    meter.finishGroup();

    meter.beginGroup();
    Map<String,Object> stable=stableTransformCanary(meter);
    meter.finishGroup();

    meter.beginGroup();
    Map<String,Object> result=object();
    result.put("schema",CANARY_SCHEMA);
    result.put("status","PASS");
    result.put("openPositive",openPositive);
    result.put("blockedNoFallback",blockedNoFallback);
    result.put("activeExceptionCleanup",exception);
    result.put("stableTransform",stable);
    requireCanaryKeys(result,"schema","status","openPositive","blockedNoFallback",
        "activeExceptionCleanup","stableTransform");

    String encoded=json(result);
    byte[] encodedBytes=utf8(encoded);
    if(encodedBytes.length>RESULT_LINE_LIMIT)
        throw new IOException("canary JSON line exceeded the one-megabyte output bound");
    meter.charge(3L+hashUnits(encodedBytes.length));
    meter.finishGroup();
    meter.check();
    if(elapsed(started,System.nanoTime())>OP_NANOS ||
            meter.groups>MAX_GROUPS || meter.maxGroupNanos>GROUP_NANOS ||
            meter.maxGroupUnits>STEP_LIMIT)
        throw new IOException("failure-cohort canary exceeded an unchanged work bound");
    System.out.println(encoded);
}

private static void requireCanaryOpen(FreshRun run) throws IOException {
    if(run==null || !"OBSERVED_FAILURE_CONTROL".equals(run.kind) ||
            !"FOUND".equals(run.disposition) || !run.complete || !run.ownerComplete ||
            run.finalPass!=PASS || run.finalPasses!=1 || run.searchCount!=1 ||
            !run.roleWidths || run.traversedNetIds==null || run.traversedNetIds.size()!=2 ||
            !new java.util.HashSet<String>(run.traversedNetIds).equals(
                new java.util.HashSet<String>(java.util.Arrays.asList("NET_A","NET_B"))) ||
            run.layout==null || run.json==null || run.finalizers==null ||
            !run.referencesDropped)
        throw new IllegalStateException("open literal did not complete one native two-net A-front control");
    requireCanaryKeys(run.json,"kind","initial","ordering","outcome","terminalFailure",
        "finalizers","layout","work");
    if(run.json.get("terminalFailure")!=null)
        throw new IllegalStateException("FOUND open control unexpectedly has terminal failure feedback");
    Map<String,Object> initial=canaryMap(run.json.get("initial"),"open initial state");
    Map<String,Object> ordering=canaryMap(run.json.get("ordering"),"open ordering");
    Map<String,Object> outcome=canaryMap(run.json.get("outcome"),"open outcome");
    Map<String,Object> runWork=canaryMap(run.json.get("work"),"open work");
    List<String> captured=canaryStringList(ordering.get("capturedNetIds"),"open captured order");
    List<String> computed=canaryStringList(ordering.get("computedNetIds"),"open computed order");
    List<String> requested=canaryStringList(ordering.get("requestedNetIds"),"open requested order");
    List<String> jsonTraversal=canaryStringList(ordering.get("traversedNetIds"),"open traversal");
    requireCanaryKeys(run.finalizers,"routingGeometry","allNetConnectivity","twoLayerRules",
        "routeQuality","viaCap");
    requireCanaryKeys(layoutMapFromRun(run),"geometrySha256","width","height","outline",
        "partsTray","pads","components","regions","labels","placementConstraints",
        "clearanceRules","traceCount","holeCount","viaCount","traces","holes");
    requireCanaryKeys(outcome,"disposition","reason","complete","ownerComplete","pass",
        "passes","expanded","maximumExpansions","errorClass","errorMessage");
    requireCanaryKeys(runWork,"advanceCalls","ownerUnits","wrapperUnits","copyEntries",
        "copyChunks","groups","maxGroupNanos","maximumUnitsPerGroup",
        "failureObservationUnits","terminalEvidenceUnits");
    requireCanaryKeys(initial,"pass","passes","expanded","maximumExpansions","maximumVias",
        "priorPromotedNetId","policy","viaCost","policyTransitions","policyVias",
        "branchOrderStrategy","netOrderStrategy","roleWidths","capturedOrderingStream",
        "sessionOrderingStream","instrumentationModeDifference","routingRelevantSettingsMatch",
        "traceCount","holeCount");
    requireCanaryKeys(ordering,"capturedNetIds","computedNetIds","requestedCohortNetIds",
        "requestedNetIds","traversedNetIds","orderMatchesCapture","searchCount");
    if(captured.isEmpty() || !captured.equals(computed) || requested.isEmpty() ||
            !"NET_A".equals(requested.get(0)) || !jsonTraversal.equals(run.traversedNetIds) ||
            canaryInt(initial.get("pass"),"open initial pass")!=PASS ||
            canaryInt(initial.get("passes"),"open initial passes")!=0 ||
            canaryInt(initial.get("expanded"),"open initial expanded")!=0 ||
            !canaryBoolean(ordering.get("orderMatchesCapture"),"open order match") ||
            !canaryStringsEqual(ordering.get("requestedCohortNetIds"),
                java.util.Arrays.asList("NET_A"),"open cohort") ||
            !canaryIntEquals(ordering.get("searchCount"),1) ||
            !"FOUND".equals(outcome.get("disposition")) ||
            !canaryBoolean(outcome.get("complete"),"open complete") ||
            !canaryBoolean(outcome.get("ownerComplete"),"open owner complete") ||
            !canaryIntEquals(runWork.get("terminalEvidenceUnits"),0) ||
            canaryInt(runWork.get("failureObservationUnits"),"open observations")!=
                canaryInt(runWork.get("advanceCalls"),"open calls") ||
            canaryInt(runWork.get("wrapperUnits"),"open wrapper units")<
                2*canaryInt(runWork.get("advanceCalls"),"open calls"))
        throw new IllegalStateException("open canary result does not satisfy the frozen run contract");
    Map<String,Object> layout=run.layout;
    Object traces=layout.get("traces"),holes=layout.get("holes");
    if(!(traces instanceof List) || ((List<?>)traces).size()!=2 ||
            !(holes instanceof List) || !((List<?>)holes).isEmpty() ||
            canaryInt(layout.get("traceCount"),"open trace count")!=2 ||
            canaryInt(layout.get("holeCount"),"open hole count")!=0 ||
            canaryInt(layout.get("viaCount"),"open via count")!=0 ||
            !"PASS".equals(run.finalizers.get("routingGeometry")) ||
            !"PASS".equals(run.finalizers.get("allNetConnectivity")) ||
            !"PASS".equals(run.finalizers.get("twoLayerRules")) ||
            !"PASS".equals(run.finalizers.get("routeQuality")) ||
            !"PASS".equals(run.finalizers.get("viaCap")))
        throw new IllegalStateException("open fixture lacks complete two-trace native finalizer evidence");
}

private static String requireCanaryBlocked(FreshRun run) throws IOException {
    if(run==null || !"OBSERVED_FAILURE_CONTROL".equals(run.kind) ||
            !"NO_PATH".equals(run.disposition) || run.complete || !run.ownerComplete ||
            run.finalPass!=PASS+1 || run.finalPasses!=1 || run.searchCount!=1 ||
            run.layout!=null || !run.roleWidths || run.json==null || run.finalizers==null ||
            !run.referencesDropped)
        throw new IllegalStateException("blocked literal did not reject after one pass-2 Search without fallback");
    requireCanaryKeys(run.json,"kind","initial","ordering","outcome","terminalFailure",
        "finalizers","layout","work");
    Map<String,Object> initial=canaryMap(run.json.get("initial"),"blocked initial state");
    Map<String,Object> ordering=canaryMap(run.json.get("ordering"),"blocked ordering");
    Map<String,Object> outcome=canaryMap(run.json.get("outcome"),"blocked outcome");
    Map<String,Object> work=canaryMap(run.json.get("work"),"blocked work");
    requireCanaryKeys(run.finalizers,"routingGeometry","allNetConnectivity","twoLayerRules",
        "routeQuality","viaCap");
    if(run.json.get("layout")!=null)
        throw new IllegalStateException("blocked control unexpectedly returned a layout");
    requireCanaryKeys(initial,"pass","passes","expanded","maximumExpansions","maximumVias",
        "priorPromotedNetId","policy","viaCost","policyTransitions","policyVias",
        "branchOrderStrategy","netOrderStrategy","roleWidths","capturedOrderingStream",
        "sessionOrderingStream","instrumentationModeDifference","routingRelevantSettingsMatch",
        "traceCount","holeCount");
    requireCanaryKeys(ordering,"capturedNetIds","computedNetIds","requestedCohortNetIds",
        "requestedNetIds","traversedNetIds","orderMatchesCapture","searchCount");
    requireCanaryKeys(outcome,"disposition","reason","complete","ownerComplete","pass",
        "passes","expanded","maximumExpansions","errorClass","errorMessage");
    requireCanaryKeys(work,"advanceCalls","ownerUnits","wrapperUnits","copyEntries",
        "copyChunks","groups","maxGroupNanos","maximumUnitsPerGroup",
        "failureObservationUnits","terminalEvidenceUnits");
    if(canaryInt(initial.get("pass"),"blocked initial pass")!=PASS ||
            canaryInt(initial.get("passes"),"blocked initial passes")!=0 ||
            canaryInt(initial.get("expanded"),"blocked initial expanded")!=0 ||
            canaryInt(work.get("failureObservationUnits"),"blocked observations")!=
                canaryInt(work.get("advanceCalls"),"blocked calls") ||
            canaryInt(work.get("wrapperUnits"),"blocked wrapper units")<
                2*canaryInt(work.get("advanceCalls"),"blocked calls")+
                    canaryInt(work.get("terminalEvidenceUnits"),"blocked terminal evidence") ||
            !"NET_A:NO_PATH".equals(outcome.get("reason")) ||
            canaryInt(outcome.get("pass"),"blocked final pass")!=PASS+1 ||
            canaryInt(outcome.get("passes"),"blocked final passes")!=1 ||
            !canaryBoolean(ordering.get("orderMatchesCapture"),"blocked order match") ||
            !canaryStringsEqual(ordering.get("requestedCohortNetIds"),
                java.util.Arrays.asList("NET_A"),"blocked cohort") ||
            !canaryIntEquals(ordering.get("searchCount"),1))
        throw new IllegalStateException("blocked control lost its one-search NO_PATH boundary");
    List<String> captured=canaryStringList(ordering.get("capturedNetIds"),"blocked captured order");
    List<String> computed=canaryStringList(ordering.get("computedNetIds"),"blocked computed order");
    List<String> requested=canaryStringList(ordering.get("requestedNetIds"),"blocked requested order");
    List<String> traversed=canaryStringList(ordering.get("traversedNetIds"),"blocked traversal");
    Object rawFailure=run.json.get("terminalFailure");
    Map<String,Object> failure=canaryMap(rawFailure,"blocked terminal failure");
    requireCanaryKeys(failure,"failedNetId","searchIndex","searchPass","passBefore","passAfter",
        "advanceCallOrdinal","ownerSearchCleared","searchReleased","ownerPromotedNetId","ownerReason");
    if(captured.isEmpty() || !captured.equals(computed) || requested.isEmpty() ||
            !"NET_A".equals(requested.get(0)) ||
            !"NET_A".equals(failure.get("failedNetId")) ||
            canaryInt(failure.get("searchIndex"),"blocked cursor")!=1 ||
            canaryInt(failure.get("searchPass"),"blocked search pass")!=PASS ||
            canaryInt(failure.get("passBefore"),"blocked pass before")!=PASS ||
            canaryInt(failure.get("passAfter"),"blocked pass after")!=PASS+1 ||
            canaryInt(failure.get("advanceCallOrdinal"),"blocked advance ordinal")<1 ||
            canaryInt(failure.get("advanceCallOrdinal"),"blocked advance ordinal")>
                canaryInt(work.get("advanceCalls"),"blocked advance calls") ||
            !canaryBoolean(failure.get("ownerSearchCleared"),"blocked owner search clear") ||
            !canaryBoolean(failure.get("searchReleased"),"blocked adapter search release") ||
            !"NET_A".equals(failure.get("ownerPromotedNetId")) ||
            !"NET_A:NO_PATH".equals(failure.get("ownerReason")) ||
            !canaryIntEquals(work.get("terminalEvidenceUnits"),
                11+traversed.size()+1))
        throw new IllegalStateException("blocked terminal proof is not bound to the retired Search and work ledger");
    int seen=0;
    for(String id:traversed) if("NET_A".equals(id)) seen++;
    if(seen!=1 || traversed.size()!=1)
        throw new IllegalStateException("blocked terminal failure is missing or repeated in actual traversal");
    for(String key:new String[] {"routingGeometry","allNetConnectivity","twoLayerRules","routeQuality","viaCap"})
        if(!"NOT_RUN".equals(run.finalizers.get(key)))
            throw new IllegalStateException("blocked owner reported an unrun finalizer");
    return (String)failure.get("failedNetId");
}

private static Map<String,Object> stableTransformCanary(Meter meter) throws IOException {
    List<String> source=java.util.Arrays.asList("NET_A","NET_B","NET_C","NET_D");
    List<String> cohort=java.util.Arrays.asList("NET_C","NET_A");
    List<String> requested=checkedStableCohort(source,cohort,source);
    List<String> noChangeSource=java.util.Arrays.asList("NET_C","NET_A","NET_B","NET_D");
    List<String> noChangeCohort=java.util.Arrays.asList("NET_C","NET_A");
    validateFeedbackIds(source,"NET_C","NET_A");
    long beforeNoChange=meter.sessionConstructorEntries;
    List<String> noChangeRequested=checkedStableCohort(noChangeSource,noChangeCohort,source);
    boolean noChangeSkipped=noChangeSource.equals(noChangeRequested);
    Map<String,Object> noChange=trialDisposition("SKIPPED","NO_ORDER_CHANGE",
        "NET_C","NET_A",noChangeCohort,noChangeRequested,false);
    requireCanaryTrialDisposition(noChange,"SKIPPED","NO_ORDER_CHANGE","NET_C","NET_A",
        noChangeCohort,noChangeRequested,false);
    long noChangeTrialSessions=meter.sessionConstructorEntries-beforeNoChange;

    List<String> duplicateCohort=java.util.Arrays.asList("NET_C","NET_C");
    List<String> missingSource=java.util.Arrays.asList("NET_A","NET_B","NET_C");
    List<String> unknownSource=java.util.Arrays.asList("NET_A","NET_B","NET_C","NET_X");
    long beforeRejected=meter.sessionConstructorEntries;
    boolean duplicateCohortRejected=cohortRejected(source,duplicateCohort,source);
    boolean missingCohortRejected=cohortRejected(missingSource,cohort,source);
    boolean unknownSourceRejected=cohortRejected(unknownSource,cohort,source);
    boolean missingFeedbackRejected=feedbackRejected(source,"NET_A",null,"MISSING_FAILURE_ID");
    boolean unknownFeedbackRejected=feedbackRejected(source,"NET_A","NET_X","UNKNOWN_FAILURE_ID");
    boolean repeatedFeedbackRejected=feedbackRejected(source,"NET_A","NET_A","REPEATED_FAILURE_ID");
    long rejectedFeedbackTrialSessions=meter.sessionConstructorEntries-beforeRejected;

    List<String> sourceRemainder=withoutCohort(source,cohort);
    List<String> requestedRemainder=withoutCohort(requested,cohort);
    boolean unrelatedOrderPreserved=sourceRemainder.equals(requestedRemainder);
    if(!requested.equals(java.util.Arrays.asList("NET_C","NET_A","NET_B","NET_D")) ||
            !noChangeSkipped || !duplicateCohortRejected || !missingCohortRejected ||
            !unknownSourceRejected || !missingFeedbackRejected || !unknownFeedbackRejected ||
            !repeatedFeedbackRejected || rejectedFeedbackTrialSessions!=0 ||
            noChangeTrialSessions!=0 || !unrelatedOrderPreserved)
        throw new IllegalStateException("stable cohort partition or pretrial rejection canary failed");

    Map<String,Object> result=object();
    result.put("source",new java.util.ArrayList<String>(source));
    result.put("cohort",new java.util.ArrayList<String>(cohort));
    result.put("requested",new java.util.ArrayList<String>(requested));
    result.put("noChangeSource",new java.util.ArrayList<String>(noChangeSource));
    result.put("noChangeCohort",new java.util.ArrayList<String>(noChangeCohort));
    result.put("noChangeRequested",new java.util.ArrayList<String>(noChangeRequested));
    result.put("unrelatedOrderPreserved",Boolean.valueOf(unrelatedOrderPreserved));
    result.put("noChangeSkipped",Boolean.valueOf(noChangeSkipped));
    result.put("duplicateCohortRejected",Boolean.valueOf(duplicateCohortRejected));
    result.put("missingCohortRejected",Boolean.valueOf(missingCohortRejected));
    result.put("unknownSourceRejected",Boolean.valueOf(unknownSourceRejected));
    result.put("missingFeedbackRejected",Boolean.valueOf(missingFeedbackRejected));
    result.put("unknownFeedbackRejected",Boolean.valueOf(unknownFeedbackRejected));
    result.put("repeatedFeedbackRejected",Boolean.valueOf(repeatedFeedbackRejected));
    result.put("rejectedFeedbackTrialSessions",Long.valueOf(rejectedFeedbackTrialSessions));
    result.put("noChangeTrialSessions",Long.valueOf(noChangeTrialSessions));
    requireCanaryKeys(result,"source","cohort","requested","noChangeSource","noChangeCohort",
        "noChangeRequested","unrelatedOrderPreserved","noChangeSkipped",
        "duplicateCohortRejected","missingCohortRejected","unknownSourceRejected",
        "missingFeedbackRejected","unknownFeedbackRejected","repeatedFeedbackRejected",
        "rejectedFeedbackTrialSessions","noChangeTrialSessions");
    meter.charge(48L);
    return result;
}

private static void requireCanaryTrialDisposition(Map<String,Object> value,String status,
        String reason,String first,String second,List<String> cohort,List<String> requested,
        boolean changed) throws IOException {
    requireCanaryKeys(value,"status","reason","capturedFailedNetId","controlFailedNetId",
        "cohortNetIds","requestedNetIds","orderChanged","feedbackValidated");
    if(!status.equals(value.get("status")) || !reason.equals(value.get("reason")) ||
            !first.equals(value.get("capturedFailedNetId")) ||
            !sameNullableString(second,value.get("controlFailedNetId")) ||
            !sameNullableStringList(cohort,value.get("cohortNetIds")) ||
            !sameNullableStringList(requested,value.get("requestedNetIds")) ||
            canaryBoolean(value.get("orderChanged"),"trial disposition orderChanged")!=changed ||
            !canaryBoolean(value.get("feedbackValidated"),"trial disposition feedbackValidated"))
        throw new IllegalStateException("trial disposition DTO differs from the frozen canary contract");
}

private static Map<String,Object> layoutMapFromRun(FreshRun run) throws IOException {
    return canaryMap(run.json.get("layout"),"run layout");
}
private static boolean cohortRejected(List<String> source,List<String> cohort,List<String> known) {
    try {
        checkedStableCohort(source,cohort,known);
        return false;
    } catch(IllegalArgumentException expected) {
        return true;
    }
}

private static boolean feedbackRejected(List<String> source,String first,String second,
        String expectedCode) {
    try {
        validateFeedbackIds(source,first,second);
        return false;
    } catch(IllegalArgumentException expected) {
        return expectedCode.equals(expected.getMessage());
    }
}

private static List<String> withoutCohort(List<String> source,List<String> cohort) {
    java.util.ArrayList<String> result=new java.util.ArrayList<String>();
    for(String id:source) if(!cohort.contains(id)) result.add(id);
    return result;
}

private static boolean sameNullableString(String expected,Object actual) {
    return expected==null?actual==null:expected.equals(actual);
}

private static boolean sameNullableStringList(List<String> expected,Object actual)
        throws IOException {
    return expected==null?actual==null:expected.equals(canaryStringList(actual,"trial disposition vector"));
}

private static boolean canaryStringsEqual(Object actual,List<String> expected,String what)
        throws IOException {
    return expected.equals(canaryStringList(actual,what));
}

private static List<String> canaryStringList(Object value,String what) throws IOException {
    if(!(value instanceof List)) throw new IOException(what+" is not an array");
    java.util.ArrayList<String> result=new java.util.ArrayList<String>();
    for(Object item:(List<?>)value) {
        if(!(item instanceof String)) throw new IOException(what+" contains a non-string");
        result.add((String)item);
    }
    return result;
}

@SuppressWarnings("unchecked")
private static Map<String,Object> canaryMap(Object value,String what) throws IOException {
    if(!(value instanceof Map)) throw new IOException(what+" is not an object");
    Map<?,?> raw=(Map<?,?>)value;
    for(Object key:raw.keySet()) if(!(key instanceof String))
        throw new IOException(what+" contains a non-string key");
    return (Map<String,Object>)raw;
}

private static void requireCanaryKeys(Map<String,Object> value,String... expected)
        throws IOException {
    if(value==null || !value.keySet().equals(
            new java.util.HashSet<String>(java.util.Arrays.asList(expected))))
        throw new IOException("canary DTO keys differ from the frozen wire contract");
}

private static int canaryInt(Object value,String what) throws IOException {
    if(!(value instanceof Number)) throw new IOException(what+" is not numeric");
    long number=((Number)value).longValue();
    if(number<Integer.MIN_VALUE || number>Integer.MAX_VALUE)
        throw new IOException(what+" is out of integer range");
    return (int)number;
}

private static boolean canaryIntEquals(Object value,int expected) throws IOException {
    return canaryInt(value,"canary integer") == expected;
}

private static boolean canaryBoolean(Object value,String what) throws IOException {
    if(!(value instanceof Boolean)) throw new IOException(what+" is not boolean");
    return ((Boolean)value).booleanValue();
}

private static final class FaultDiagnostics implements PcbLayerRoutingPrototype.Diagnostics {
    final State state=new State();

    public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        if(snapshot==null) throw new IllegalStateException("diagnostic branch snapshot is missing");
        state.callbackCalls++;
        Object owner=state.session;
        Object search=owner==null?null:field(owner,"search");
        Object branch=search==null?null:field(search,"branch");
        state.searchPresent=search!=null;
        state.branchPresent=branch!=null;
        state.expandedAtThrow=intField(snapshot,"branchExpanded");
        if(owner==null || search==null || branch==null ||
                intField(owner,"pass")!=PASS || state.expandedAtThrow<1 ||
                !stringField(search,"currentNet").equals(stringField(snapshot,"netId")) ||
                !stringField(branch,"net").equals(stringField(snapshot,"netId")))
            throw new IllegalStateException("active branch precondition was not met");
        if(state.callbackCalls!=1)
            throw new IllegalStateException("fault diagnostic was invoked more than once");
        state.failure=new IllegalStateException("FAILED_FIRST_ACTIVE_BRANCH_CANARY");
        throw (IllegalStateException)state.failure;
    }

    void recordAbort(Object owner,Throwable caught) {
        state.abortCalls++;
        try {
            state.ownerDiagnosticFailureSame=owner!=null &&
                field(owner,"diagnosticFailure")==caught &&
                caught==state.failure;
        } catch(Throwable unavailable) {
            state.ownerDiagnosticFailureSame=false;
        }
    }

    static final class State {
        Object session;
        Throwable failure;
        int callbackCalls,expandedAtThrow,abortCalls,failedAdvanceCalls;
        long failedFailureObservationUnits,failedTerminalEvidenceUnits,failedOwnerUnits,failedWrapperUnits;
        boolean adapterSearchAliasesReleased,attemptWorkAccounted;
        boolean searchPresent,branchPresent,ownerDiagnosticFailureSame;
        boolean sessionReleased,searchReleased,candidateReleased,layoutReleased,
            boardReleased,referencesReleased;
        long cleanupNanos;

        void recordRelease(boolean releasedSession,boolean releasedSearch,
                boolean releasedCandidate,boolean releasedLayout,boolean releasedBoard,
                long elapsedCleanup) {
            sessionReleased=releasedSession;
            searchReleased=releasedSearch;
            candidateReleased=releasedCandidate;
            layoutReleased=releasedLayout;
            boardReleased=releasedBoard;
            referencesReleased=releasedSession && releasedSearch && releasedCandidate &&
                releasedLayout && releasedBoard && adapterSearchAliasesReleased;
            cleanupNanos=elapsedCleanup;
        }
    }
}

}
