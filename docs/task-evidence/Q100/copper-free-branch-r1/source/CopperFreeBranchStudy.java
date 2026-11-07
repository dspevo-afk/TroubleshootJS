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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

/** Private bounded native study of one actual P07 first-branch counterfactual. */
public final class CopperFreeBranchStudy {
    private static final Charset UTF8=Charset.forName("UTF-8");
    private static final Pattern UUID=Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA=Pattern.compile("[0-9a-f]{64}");
    private static final String SCHEMA="tsj-copper-free-branch-v1";
    private static final String MARKER="tsj-copper-free-branch-marker-v1";
    private static final String PREFIX_SHA=
        "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb";
    private static final String EXPECTED_FIXTURE_DISCONNECT=
        "PCB net is electrically disconnected: A_COIL_LOW";
    private static final long ROOT_SEED=9007199254740993L;
    private static final long PLACE_SEED=-6587958367974952701L;
    private static final long ROUTE_SEED=8445301674744508279L;
    private static final String NET="A_COIL_LOW",START="QA.C",ROOT="DA.A";
    private static final int PASS=2,RECEIPT_ORDINAL=307,LINEAGE_ORDINAL=77;
    private static final int MAX_VIAS=48,MAX_PATH_NODES=4096,MAX_GROUPS=640;
    private static final int STEP_LIMIT=32768;
    private static final long GROUP_NANOS=5000000000L;
    private static final long OP_NANOS=90000000000L;
    private static final long CLEANUP_NANOS=120000000000L;
    private static final int RESULT_LIMIT=8*1024*1024;

    private CopperFreeBranchStudy() { }

    public static void main(String[] args) throws Exception {
        if(args!=null && args.length==1 && "--canary".equals(args[0])) {
            runCanary();
            return;
        }
        if(args==null || args.length!=5 || !"OBSERVE".equals(args[0]))
            throw new IllegalArgumentException(
                "Expected OBSERVE UUID FINAL_RUN_MANIFEST_SHA256 WIRE_CONTRACT_SHA256 EMPTY_ABSOLUTE_DATA_DIR");
        String runId=args[1],manifest=args[2],wire=args[3];
        if(!UUID.matcher(runId).matches()) throw new IllegalArgumentException("invalid lowercase run UUID");
        requireSha(manifest,"final run manifest SHA-256");
        requireSha(wire,"wire contract SHA-256");
        Path data=Paths.get(args[4]);
        if(!data.isAbsolute() || !data.normalize().equals(data) ||
                !Files.isDirectory(data,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(data) ||
                !data.toRealPath().equals(data.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("data directory must be an existing absolute real directory");
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(data)) {
            if(entries.iterator().hasNext()) throw new IllegalArgumentException("data directory must be empty");
        }
        long started=System.nanoTime();
        Map<String,Object> result=runStudy(runId,manifest,wire,data,started);
        long exportStart=System.nanoTime();
        byte[] bytes=utf8(json(result)+"\n");
        if(bytes.length<1 || bytes.length>RESULT_LIMIT)
            throw new IOException("result.json exceeds the 8 MiB bound");
        Path resultPath=data.resolve("result.json");
        try(OutputStream out=new BufferedOutputStream(Files.newOutputStream(resultPath,
                new OpenOption[] {StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE}))) {
            out.write(bytes);
            out.flush();
        }
        Digest actual=fileDigest(resultPath);
        if(actual.bytes!=bytes.length || !actual.sha.equals(sha256(bytes)))
            throw new IOException("result.json changed after close");
        Map<String,Object> marker=object();
        marker.put("schema",MARKER);
        marker.put("status","COMPLETED_DIAGNOSTIC");
        marker.put("resultSha256",actual.sha);
        marker.put("resultBytes",Long.valueOf(actual.bytes));
        String markerLine=json(marker);
        if(elapsed(exportStart,System.nanoTime())>GROUP_NANOS)
            throw new IOException("final result serialization/export/preflight exceeded five seconds");
        if(elapsed(started,System.nanoTime())>OP_NANOS)
            throw new IOException("full native process exceeded 90 seconds");
        System.out.println(markerLine);
    }

    private static void runCanary() throws Exception {
        long started=System.nanoTime();
        Meter positiveMeter=new Meter(),blockedMeter=new Meter(),failureMeter=new Meter();
        Meter metadataMeter=new Meter();
        metadataMeter.beginGroup(); metadataMeter.charge();
        CopperFreeBranchFixtures.Fixture positiveFixture=CopperFreeBranchFixtures.open();
        Capture positiveCapture=canaryCapture(positiveFixture);
        chargeUnits(metadataMeter,positiveCapture.callbackUnits);
        metadataMeter.finishGroup();
        positiveFixture=null;
        Cf positive=runCounterfactual(positiveCapture,positiveMeter,started,new Cf(),null,true);
        if(positiveCapture.board!=null || positiveCapture.template!=null ||
                !adapterReleased(positive) || !"FOUND".equals(positive.disposition) ||
                !"EXPECTED_REJECTION".equals(positive.fixturePreflight) ||
                !"PASS".equals(positive.fullGeometryCanary) ||
                !positive.foundCandidate || positive.nodeChain==null || positive.nodeChain.isEmpty() ||
                positive.publishedTraces==null || positive.publishedTraces.isEmpty() ||
                positive.publishedHoles==null ||
                !Boolean.TRUE.equals(positive.validatorRows.get("endpointsConnected")))
            throw new IllegalStateException("open native fixture did not find and publish a connected route");
        positiveCapture=null;

        metadataMeter.beginGroup(); metadataMeter.charge();
        CopperFreeBranchFixtures.Fixture blockedFixture=CopperFreeBranchFixtures.blocked();
        Capture blockedCapture=canaryCapture(blockedFixture);
        chargeUnits(metadataMeter,blockedCapture.callbackUnits);
        metadataMeter.finishGroup();
        blockedFixture=null;
        Cf blocked=runCounterfactual(blockedCapture,blockedMeter,started,new Cf(),null);
        if(blockedCapture.board!=null || blockedCapture.template!=null ||
                !adapterReleased(blocked) || !"NO_PATH".equals(blocked.disposition) ||
                !"EXPECTED_REJECTION".equals(blocked.fixturePreflight) ||
                !"NOT_RUN".equals(blocked.fullGeometryCanary) ||
                blocked.foundCandidate || blocked.nodeChain!=null ||
                blocked.publishedTraces==null || !blocked.publishedTraces.isEmpty() ||
                blocked.publishedHoles==null || !blocked.publishedHoles.isEmpty())
            throw new IllegalStateException("blocked native fixture did not exhaust without publication");
        blockedCapture=null;

        metadataMeter.beginGroup(); metadataMeter.charge();
        CopperFreeBranchFixtures.Fixture failureFixture=CopperFreeBranchFixtures.open();
        Capture failureCapture=canaryCapture(failureFixture);
        chargeUnits(metadataMeter,failureCapture.callbackUnits);
        metadataMeter.finishGroup();
        failureFixture=null;
        CanaryFault fault=new CanaryFault();
        Cf failed=new Cf();
        Throwable caught=null;
        try { runCounterfactual(failureCapture,failureMeter,started,failed,fault,false); }
        catch(Throwable expected) { caught=expected; }
        if(caught==null || caught!=fault.failure || fault.calls!=1 ||
                !(caught instanceof IllegalStateException) ||
                !"CANARY_INJECTED_CALLBACK".equals(caught.getMessage()) ||
                caught.getSuppressed().length!=0 ||
                !fault.searchPresent || !fault.activeBranchPresent || fault.publisherPresent ||
                !"BRANCH_SEARCH".equals(fault.phase) || fault.branchOrdinal!=1 ||
                !NET.equals(fault.net) || !START.equals(fault.startPadId) ||
                !ROOT.equals(fault.rootPadId) ||
                !"EXPECTED_REJECTION".equals(failed.fixturePreflight) ||
                !"NOT_RUN".equals(failed.fullGeometryCanary) ||
                failureCapture.board!=null || failureCapture.template!=null ||
                !failed.referencesReleased || !failed.branchReleased ||
                !failed.publisherReleased || !failed.searchReleased ||
                !failed.sessionReleased || !failed.layoutReleased ||
                !failed.templateReleased || !failed.boardReleased)
            throw new IllegalStateException("injected observer failure did not preserve exception and cleanup");
        failureCapture=null;

        int groups=positiveMeter.groups+blockedMeter.groups+failureMeter.groups+metadataMeter.groups;
        long cleanup=positive.cleanupNanos+blocked.cleanupNanos+failed.cleanupNanos;
        long units=positiveMeter.units+blockedMeter.units+failureMeter.units+metadataMeter.units;
        long maxGroup=Math.max(metadataMeter.maxGroupNanos,Math.max(positiveMeter.maxGroupNanos,
            Math.max(blockedMeter.maxGroupNanos,failureMeter.maxGroupNanos)));
        int maxUnits=Math.max(metadataMeter.maxGroupUnits,Math.max(positiveMeter.maxGroupUnits,
            Math.max(blockedMeter.maxGroupUnits,failureMeter.maxGroupUnits)));
        long elapsedWork=elapsed(started,System.nanoTime())-cleanup;
        if(groups<1 || groups>MAX_GROUPS || units<0 || cleanup>CLEANUP_NANOS ||
                elapsedWork<0 || elapsedWork>OP_NANOS || maxGroup>GROUP_NANOS ||
                maxUnits>STEP_LIMIT)
            throw new IOException("native canary work or cleanup exceeded its bound");

        long markerStarted=System.nanoTime();
        Map<String,Object> result=object();
        result.put("schema","tsj-copper-free-branch-canary-v1");
        result.put("status","PASS");
        result.put("positive",canaryOutcome(positive));
        result.put("blocked",canaryOutcome(blocked));
        Map<String,Object> injected=object();
        injected.put("observed",Boolean.TRUE); injected.put("callbackCalls",Integer.valueOf(fault.calls));
        injected.put("searchPresent",Boolean.valueOf(fault.searchPresent));
        injected.put("activeBranchPresent",Boolean.valueOf(fault.activeBranchPresent));
        injected.put("publisherPresent",Boolean.valueOf(fault.publisherPresent));
        injected.put("phase",fault.phase);
        injected.put("branchOrdinal",Integer.valueOf(fault.branchOrdinal));
        injected.put("netId",fault.net);
        injected.put("startPadId",fault.startPadId);
        injected.put("rootPadId",fault.rootPadId);
        injected.put("exceptionClass",caught.getClass().getSimpleName());
        injected.put("exceptionMessage",caught.getMessage());
        injected.put("fixturePreflight",failed.fixturePreflight);
        injected.put("fixturePreflightMessage",failed.fixturePreflightMessage);
        injected.put("cleanup",canaryCleanup(failed)); result.put("injectedFailure",injected);
        result.put("originalContext","NOT_APPLICABLE");
        Map<String,Object> work=object();
        work.put("elapsedNanos",Long.valueOf(elapsedWork)); work.put("units",Long.valueOf(units));
        work.put("groups",Integer.valueOf(groups)); work.put("maxGroupNanos",Long.valueOf(maxGroup));
        work.put("maximumUnitsPerGroup",Integer.valueOf(maxUnits));
        work.put("cleanupNanos",Long.valueOf(cleanup)); result.put("work",work);
        String marker=json(result);
        if(elapsed(markerStarted,System.nanoTime())>GROUP_NANOS)
            throw new IOException("native canary marker serialization exceeded five seconds");
        if(elapsed(started,System.nanoTime())>OP_NANOS)
            throw new IOException("native canary process exceeded 90 seconds");
        System.out.println(marker);
    }

    private static Capture canaryCapture(CopperFreeBranchFixtures.Fixture fixture) {
        if(fixture==null || fixture.board==null || fixture.layout==null ||
                !fixture.layout.getTraces().isEmpty() || !fixture.layout.getHoles().isEmpty())
            throw new IllegalStateException("canary fixture must have a copper-free board/layout");
        Capture capture=new Capture();
        capture.board=fixture.board; capture.template=fixture.layout;
        try {
            fixture.layout.validateRoutingGeometry(fixture.board);
            throw new IllegalStateException("full geometry unexpectedly accepted copper-free fixture");
        } catch(RuntimeException invalid) {
            if(!(invalid instanceof IllegalStateException) ||
                    !EXPECTED_FIXTURE_DISCONNECT.equals(invalid.getMessage()))
                throw new IllegalStateException(
                    "fixture full-geometry preflight failed before the expected net disconnect: "+
                    String.valueOf(invalid.getMessage()),invalid);
            capture.fixturePreflight="EXPECTED_REJECTION";
            capture.fixturePreflightMessage=invalid.getMessage();
        }
        String fingerprint=fixture.layout.geometryFingerprint();
        capture.detachedGeometrySha=hashString(fingerprint);
        capture.callbackUnits=fixture.layout.getPads().size()+fixture.layout.getComponents().size()+
            fixture.layout.getSilkscreenLabels().size()+fixture.layout.getRegions().size()+
            fixture.layout.getHoles().size()+hashUnits(utf8(fingerprint).length)+2;
        return capture;
    }

    private static void chargeUnits(Meter meter,int units) throws IOException {
        if(units<0 || units>STEP_LIMIT)
            throw new IOException("canary metadata units are out of range");
        for(int index=0;index<units;index++) meter.charge();
    }

    private static Map<String,Object> canaryOutcome(Cf result) {
        Map<String,Object> out=object();
        out.put("disposition",result.disposition);
        out.put("nodeCount",Integer.valueOf(result.nodeChain==null?0:result.nodeChain.size()));
        out.put("traceCount",Integer.valueOf(result.publishedTraces==null?0:result.publishedTraces.size()));
        out.put("holeCount",Integer.valueOf(result.publishedHoles==null?0:result.publishedHoles.size()));
        out.put("searchCalls",Long.valueOf(result.searchCalls));
        out.put("publisherCalls",Long.valueOf(result.publisherCalls));
        out.put("endpointsConnected",result.validatorRows.get("endpointsConnected"));
        out.put("fixturePreflight",result.fixturePreflight);
        out.put("fixturePreflightMessage",result.fixturePreflightMessage);
        out.put("fullGeometryControl",result.fullGeometryCanary);
        out.put("cleanup",canaryCleanup(result));
        return out;
    }

    private static Map<String,Object> canaryCleanup(Cf result) {
        Map<String,Object> out=object();
        out.put("elapsedNanos",Long.valueOf(result.cleanupNanos));
        out.put("searchReleased",Boolean.valueOf(result.searchReleased));
        out.put("branchReleased",Boolean.valueOf(result.branchReleased));
        out.put("publisherReleased",Boolean.valueOf(result.publisherReleased));
        out.put("sessionReleased",Boolean.valueOf(result.sessionReleased));
        out.put("layoutReleased",Boolean.valueOf(result.layoutReleased));
        out.put("templateReleased",Boolean.valueOf(result.templateReleased));
        out.put("boardReleased",Boolean.valueOf(result.boardReleased));
        out.put("referencesReleased",Boolean.valueOf(result.referencesReleased));
        return out;
    }

    private static boolean adapterReleased(Cf result) {
        return result.searchReleased && result.branchReleased && result.publisherReleased &&
            result.sessionReleased && result.layoutReleased && result.templateReleased &&
            result.boardReleased && result.referencesReleased;
    }

    private static Map<String,Object> runStudy(String runId,String manifest,String wire,
            Path data,long started) throws Exception {
        Capture capture=new Capture();
        Arm arm=runArm(runId,manifest,wire,data,capture);
        if(!arm.ownersReleased || !arm.contextRestored ||
                !capture.ownersReleased || !capture.contextRestored)
            throw new IllegalStateException("original operation-1 owners/context did not close");
        if(arm.request!=0 || arm.selectedAttempt!=4 || arm.receiptAttempt!=4 ||
                arm.rootSeed!=ROOT_SEED || arm.placementSeed!=PLACE_SEED || arm.routingSeed!=ROUTE_SEED)
            throw new IllegalStateException("fresh authored operation-1 request identity changed");
        if(capture.count!=1 || capture.receiptOrdinal!=RECEIPT_ORDINAL ||
                capture.lineageOrdinal!=LINEAGE_ORDINAL || capture.pass!=PASS ||
                !NET.equals(capture.net) || !START.equals(capture.start) || !ROOT.equals(capture.root) ||
                !"NO_PATH".equals(capture.terminal) || !PREFIX_SHA.equals(capture.prefixSha) ||
                capture.prefixHoleCount!=48 || capture.prefixViaCount!=48 ||
                capture.reached.length!=1 || !ROOT.equals(capture.reached[0]) ||
                capture.reachedPth.length!=0)
            throw new IllegalStateException("selected original terminal branch differs from frozen reference");
        if(arm.groupCount<1 || arm.groupCount>MAX_GROUPS ||
                arm.maxGroupNanos<=0 || arm.maxGroupNanos>GROUP_NANOS)
            throw new IOException("actual operation-1 group bounds failed");

        Meter meter=new Meter();
        Cf cf=runCounterfactual(capture,meter,started);
        if(arm.cleanupNanos>CLEANUP_NANOS-cf.cleanupNanos)
            throw new IOException("measured operation-1 plus counterfactual cleanup exceeded 120 seconds");
        meter.beginGroup();
        meter.charge();
        Map<String,Object> op1=armJson(runId,manifest,wire,arm,capture);
        Map<String,Object> reference=referenceJson(capture);
        Map<String,Object> source=sourceJson(capture,cf.detachedAfter);
        Map<String,Object> counterfactual=cf.payload();
        Map<String,Object> cleanup=cf.cleanupJson(arm.contextRestored);
        Map<String,Object> work=object();
        Map<String,Object> out=object();
        out.put("schema",SCHEMA); out.put("mode","OBSERVE"); out.put("runId",runId);
        out.put("finalRunManifestSha256",manifest); out.put("wireContractSha256",wire);
        out.put("operation1",op1); out.put("reference",reference);
        out.put("work",work); out.put("source",source);
        out.put("counterfactual",counterfactual); out.put("cleanup",cleanup);
        meter.charge();
        cf.releaseWrapperSource(capture);
        if(!cf.referencesReleased)
            throw new IllegalStateException("counterfactual references remained live");
        meter.finishGroup();
        int resultMetadataUnits=meter.groupUnits;
        if(resultMetadataUnits!=2)
            throw new IllegalStateException("result metadata work ledger changed");
        int groups=arm.groupCount+meter.groups;
        if(groups>MAX_GROUPS) throw new IOException("combined native groups exceeded 640");
        long maxGroup=Math.max(arm.maxGroupNanos,meter.maxGroupNanos);
        long units=arm.p07OwnerSteps+capture.callbackUnits+meter.units;
        long workElapsed=elapsed(started,System.nanoTime())-arm.cleanupNanos-cf.cleanupNanos;
        if(workElapsed<0 || workElapsed>OP_NANOS)
            throw new IOException("combined measured work exceeded 90 seconds");
        if(!capture.sourceUnchanged) throw new IllegalStateException("source geometry/effects changed");
        work.put("elapsedNanos",Long.valueOf(workElapsed));
        work.put("units",Long.valueOf(units));
        work.put("groups",Integer.valueOf(groups));
        work.put("maxGroupNanos",Long.valueOf(maxGroup));
        work.put("maximumUnitsPerGroup",Integer.valueOf(
            Math.max(arm.maxUnitsPerGroup,meter.maxGroupUnits)));
        work.put("resultMetadataUnits",Integer.valueOf(resultMetadataUnits));
        work.put("resultMetadataGroups",Integer.valueOf(1));
        return out;
    }
    private static Arm runArm(String runId,String manifest,String wire,Path data,
            Capture capture) throws Exception {
        Object study=null,armRun=null,armResult=null;
        try {
            Class<?> studyType=Class.forName(ActualGeneratorBranchOrderStudy.class.getName()+"$Study");
            Class<?> modeType=Class.forName(ActualGeneratorBranchOrderStudy.class.getName()+"$Mode");
            Object authored=enumValue(modeType,"AUTHORED");
            Constructor<?> sc=studyType.getDeclaredConstructor(modeType,String.class,String.class,
                String.class,Path.class);
            sc.setAccessible(true);
            study=sc.newInstance(authored,runId,manifest,wire,data);
            Class<?> armType=Class.forName(ActualGeneratorBranchOrderStudy.class.getName()+"$ArmRun");
            Constructor<?> ac=armType.getDeclaredConstructor(studyType,int.class,
                PcbLayerRoutingPrototype.FixedPrefixGraphObserver.class,
                ActualGeneratorBranchOrderStudy.TerminalPrefixInspector.class);
            ac.setAccessible(true);
            armRun=ac.newInstance(study,Integer.valueOf(1),null,capture);
            Method execute=armType.getDeclaredMethod("execute");
            execute.setAccessible(true);
            armResult=invoke(execute,armRun);
            Object request=field(armRun,"request");
            Object candidateHeader=field(armRun,"candidateHeader");
            if(request==null || candidateHeader==null)
                throw new IllegalStateException("operation-1 omitted request/candidate evidence");
            Object context=field(request,"context"),receipt=field(request,"receipt");
            if(context==null || receipt==null) throw new IllegalStateException("operation-1 omitted receipt");
            Digest raw=fileDigest(data.resolve("op-1.jsonl"));
            if(raw.bytes!=longField(armResult,"receiptBytes") ||
                    !raw.sha.equals(stringField(armResult,"receiptSha256")))
                throw new IOException("fresh op-1 JSONL differs from the actual ArmResult");
            int groupCount=intField(armRun,"nextGroupOrdinal");
            int maxUnits=maxOriginalGroupUnits(data.resolve("op-1.jsonl"),groupCount,
                capture.groupOrdinal,capture.callbackUnits);
            return new Arm(raw.bytes,raw.sha,longField(armResult,"elapsedToCloseNanos"),
                longField(armResult,"maxFullGroupNanos"),longField(armResult,"cleanupNanos"),
                booleanField(armResult,"ownersReleased"),booleanField(armResult,"contextRestored"),
                groupCount,longField(request,"p07OwnerSteps"),maxUnits,
                intField(context,"requestIndex"),intField(context,"selectedAttempt"),
                intField(receipt,"attempt"),longField(candidateHeader,"rootSeed"),
                longField(candidateHeader,"placementSeed"),longField(candidateHeader,"routingSeed"));
        } finally { armResult=null; armRun=null; study=null; }
    }
    private static Cf runCounterfactual(Capture capture,Meter meter,long processStarted)
            throws Exception {
        return runCounterfactual(capture,meter,processStarted,new Cf(),null,false);
    }

    private static Cf runCounterfactual(Capture capture,Meter meter,long processStarted,
            Cf result,final CanaryFault fault) throws Exception {
        return runCounterfactual(capture,meter,processStarted,result,fault,false);
    }

    private static Cf runCounterfactual(Capture capture,Meter meter,long processStarted,
            Cf result,final CanaryFault fault,boolean fullGeometryCanary) throws Exception {
        if(capture==null || result==null || meter==null)
            throw new IllegalArgumentException("counterfactual owner inputs are required");
        result.fixturePreflight=capture.fixturePreflight;
        result.fixturePreflightMessage=capture.fixturePreflightMessage;
        Owner owner=new Owner();
        long phaseStarted=System.nanoTime();
        try {
            meter.beginGroup(); meter.charge();
            final Meter measured=meter;
            final Owner observedOwner=owner;
            SeededPcbLayoutGenerator.AttemptObserver observer=
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int attempt) {
                        measured.check();
                        if(fault!=null && observedOwner.search!=null) {
                            Object branch=field(observedOwner.search,"branch");
                            if(branch==null) return;
                            assertBranch(branch,observedOwner.search);
                            Object publisher=field(observedOwner.search,"publisher");
                            if(!(field(branch,"start") instanceof PcbPadPlacement) ||
                                    !(field(branch,"root") instanceof PcbPadPlacement))
                                throw new IllegalStateException("active canary branch omitted pad identities");
                            fault.searchPresent=true;
                            fault.activeBranchPresent=true;
                            fault.publisherPresent=publisher!=null;
                            fault.phase=fault.publisherPresent?"PUBLISHER":"BRANCH_SEARCH";
                            fault.branchOrdinal=intField(branch,"branchOrdinal");
                            fault.net=stringField(branch,"net");
                            fault.startPadId=((PcbPadPlacement)field(branch,"start")).getPadId();
                            fault.rootPadId=((PcbPadPlacement)field(branch,"root")).getPadId();
                            fault.calls++;
                            if(fault.failure==null)
                                fault.failure=new IllegalStateException("CANARY_INJECTED_CALLBACK");
                            throw (IllegalStateException)fault.failure;
                        }
                    }
                };
            owner.board=capture.board;
            owner.template=capture.template;
            owner.session=new PcbLayerRoutingPrototype.Session(owner.board,owner.template,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,observer,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,MAX_VIAS);
            owner.layout=((PcbBoardLayout)field(owner.session,"template")).copyForRouting();
            if(!owner.layout.getTraces().isEmpty() || !owner.layout.getHoles().isEmpty())
                throw new IllegalStateException("first-branch candidate did not start with zero copper");
            owner.search=createSearch(owner.session,owner.board,owner.layout,observer);
            setField(owner.session,"candidate",owner.layout);
            setField(owner.session,"search",owner.search);
            setIntField(owner.session,"pass",PASS);
            setField(owner.search,"nets",oneNetVector());
            owner.searchMethod=owner.search.getClass().getDeclaredMethod("advanceOne");
            owner.searchMethod.setAccessible(true);
            meter.finishGroup();

            while(result.disposition==null) {
                if(elapsed(processStarted,System.nanoTime())>OP_NANOS)
                    throw new IOException("native operation exceeded the 90-second clock");
                meter.beginGroup();
                while(meter.groupUnits<STEP_LIMIT && result.disposition==null) {
                    meter.charge();
                    boolean publishing=field(owner.search,"publisher")!=null;
                    try { invoke(owner.searchMethod,owner.search); }
                    catch(InvocationTargetException wrapped) {
                        Throwable cause=wrapped.getCause();
                        if(!nativeDisposition(cause)) rethrow(cause);
                        result.disposition=disposition(cause);
                        result.reason=cause.getMessage();
                    }
                    if(publishing) meter.publisherCalls++;
                    else meter.searchCalls++;
                    if(result.disposition!=null) break;
                    Object branch=field(owner.search,"branch");
                    if(branch!=null) assertBranch(branch,owner.search);
                    Object publisher=field(owner.search,"publisher");
                    if(publisher!=null && !result.foundCandidate) {
                        result.foundCandidate=true;
                        result.nodeChain=nodes(publisher,owner.search);
                    }
                    if(publishing && publisher==null) {
                        if(!result.foundCandidate)
                            throw new IllegalStateException("Publisher completed without its Search candidate");
                        result.disposition="FOUND";
                        result.reason=null;
                    } else if(booleanField(owner.search,"complete")) {
                        result.disposition=result.foundCandidate?"FOUND":"NO_PATH";
                        if(!result.foundCandidate) result.reason="Search completed without a candidate";
                    }
                    if(elapsed(meter.groupStarted,System.nanoTime())>=GROUP_NANOS-1000000L)
                        break;
                }
                meter.finishGroup();
            }

            meter.beginGroup();
            meter.charge();
            result.publishedTraces=traces(owner.layout);
            meter.charge();
            result.publishedHoles=holes(owner.layout);
            meter.charge();
            result.validatorRows=validators(owner.board,owner.layout,result);
            if(result.validatorFailure!=null)
                throw new IllegalStateException("original partial-layout validator failed: "+
                    result.validatorFailure,result.validatorCause);
            if(fullGeometryCanary) {
                if(!"FOUND".equals(result.disposition) || !result.foundCandidate)
                    throw new IllegalStateException("full-geometry canary did not publish its route");
                meter.charge();
                owner.layout.validateRoutingGeometry(owner.board);
                result.fullGeometryCanary="PASS";
            }
            meter.charge();
            result.detachedAfter=hashFingerprint(capture.template);
            if(!capture.detachedGeometrySha.equals(result.detachedAfter))
                throw new IllegalStateException("counterfactual changed its detached source template");
            meter.finishGroup();
            result.phaseNanos=elapsed(phaseStarted,System.nanoTime());
            result.units=meter.units;
            result.searchCalls=meter.searchCalls;
            result.publisherCalls=meter.publisherCalls;
            result.expansions=intField(field(owner.session,"budget"),"expanded");
            result.groups=meter.groups;
            result.maxGroupNanos=meter.maxGroupNanos;
        } catch(Throwable failure) {
            if(meter.open) {
                try { meter.finishGroup(); }
                catch(Throwable accountingFailure) { failure.addSuppressed(accountingFailure); }
            }
            rethrow(failure);
        } finally {
            long cleanupStarted=System.nanoTime();
            if(owner.search!=null) {
                setField(owner.search,"branch",null);
                setField(owner.search,"publisher",null);
                setField(owner.search,"currentPadIds",null);
                setField(owner.search,"reachedPadIds",null);
                setField(owner.search,"nets",null);
                Object faces=field(owner.search,"faces");
                if(faces instanceof Object[]) {
                    Object[] values=(Object[])faces;
                    for(int index=0;index<values.length;index++) values[index]=null;
                }
                result.branchReleased=field(owner.search,"branch")==null;
                result.publisherReleased=field(owner.search,"publisher")==null;
            } else { result.branchReleased=true; result.publisherReleased=true; }
            if(owner.session!=null) {
                setField(owner.session,"search",null);
                setField(owner.session,"candidate",null);
            }
            owner.searchMethod=null; owner.search=null; owner.layout=null;
            owner.session=null; owner.template=null; owner.board=null;
            capture.template=null; capture.board=null;
            result.searchReleased=owner.search==null;
            result.sessionReleased=owner.session==null;
            result.layoutReleased=owner.layout==null;
            result.templateReleased=owner.template==null && capture.template==null;
            result.boardReleased=owner.board==null && capture.board==null;
            result.referencesReleased=result.searchReleased && result.sessionReleased &&
                result.layoutReleased && result.templateReleased && result.boardReleased &&
                owner.searchMethod==null;
            result.cleanupNanos=elapsed(cleanupStarted,System.nanoTime());
        }
        if(result.cleanupNanos>CLEANUP_NANOS || !result.referencesReleased ||
                !result.branchReleased || !result.publisherReleased)
            throw new IllegalStateException("counterfactual cleanup/release proof failed");
        if(elapsed(processStarted,System.nanoTime())>OP_NANOS)
            throw new IOException("native operation exceeded the 90-second clock");
        return result;
    }

    private static Object createSearch(PcbLayerRoutingPrototype.Session session,
            TroubleshootBoard board,PcbBoardLayout layout,
            SeededPcbLayoutGenerator.AttemptObserver observer) throws Exception {
        Class<?> type=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$Search");
        Object budget=field(session,"budget");
        Constructor<?> constructor=type.getDeclaredConstructor(
            PcbLayerRoutingPrototype.Session.class,TroubleshootBoard.class,
            PcbBoardLayout.class,PcbLayerRoutingPrototype.Policy.class,
            SeededPcbLayoutGenerator.AttemptObserver.class,budget.getClass(),
            int.class,String.class,int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(session,board,layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,observer,budget,
            Integer.valueOf(PASS),null,Integer.valueOf(MAX_VIAS));
    }

    private static Vector<String> oneNetVector() {
        Vector<String> result=new Vector<String>(); result.add(NET); return result;
    }

    private static void assertBranch(Object branch,Object search) {
        Object start=field(branch,"start"),root=field(branch,"root");
        if(!(start instanceof PcbPadPlacement) || !(root instanceof PcbPadPlacement))
            throw new IllegalStateException("actual P07 branch omitted physical pad identities");
        if(!NET.equals(stringField(branch,"net")) ||
                !START.equals(((PcbPadPlacement)start).getPadId()) ||
                !ROOT.equals(((PcbPadPlacement)root).getPadId()) ||
                intField(branch,"branchOrdinal")!=1 ||
                !NET.equals(stringField(search,"currentNet")))
            throw new IllegalStateException("actual P07 chooser changed the selected first branch");
    }

    private static int maxOriginalGroupUnits(Path path,int expectedGroups,
            int callbackGroup,int callbackUnits) throws IOException {
        if(expectedGroups<1 || expectedGroups>MAX_GROUPS || callbackGroup<0 ||
                callbackGroup>=expectedGroups || callbackUnits<0 || callbackUnits>STEP_LIMIT)
            throw new IOException("operation-1 callback GROUP accounting is out of range");
        boolean[] seen=new boolean[expectedGroups];
        int groups=0,maximum=0;
        try(BufferedReader reader=Files.newBufferedReader(path,UTF8)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.length()>1048576)
                    throw new IOException("operation-1 JSONL line exceeded the native read bound");
                if(!line.contains("\"record\":\"GROUP\"")) continue;
                long operation=jsonLongField(line,"operation",false);
                long ordinal=jsonLongField(line,"ordinal",false);
                long p07Steps=jsonLongField(line,"p07Steps",true);
                long diagnosticUnits=jsonLongField(line,"diagnosticUnits",false);
                if(operation!=1 || ordinal<0 || ordinal>=expectedGroups ||
                        seen[(int)ordinal] || diagnosticUnits<0 || diagnosticUnits>STEP_LIMIT ||
                        p07Steps< -1 || p07Steps>STEP_LIMIT)
                    throw new IOException("operation-1 GROUP row is invalid or duplicated");
                seen[(int)ordinal]=true;
                groups++;
                long units=(p07Steps<0?0:p07Steps)+diagnosticUnits;
                if(ordinal==callbackGroup) units+=callbackUnits;
                if(units>STEP_LIMIT)
                    throw new IOException("operation-1 GROUP plus terminal callback exceeded 32768 units");
                if(units>maximum) maximum=(int)units;
            }
        }
        if(groups!=expectedGroups)
            throw new IOException("operation-1 GROUP count differs from its live ArmRun ledger");
        for(boolean found:seen) if(!found)
            throw new IOException("operation-1 GROUP ordinals are not complete");
        return maximum;
    }

    private static long jsonLongField(String line,String name,boolean nullable) throws IOException {
        String key="\""+name+"\":";
        int at=line.indexOf(key);
        if(at<0 || line.indexOf(key,at+key.length())>=0)
            throw new IOException("operation-1 GROUP row omitted or duplicated "+name);
        int start=at+key.length(),end=start;
        if(nullable && line.startsWith("null",start)) return -1;
        while(end<line.length()) {
            char value=line.charAt(end);
            if(value<'0' || value>'9') break;
            end++;
        }
        if(end==start || (end-start>1 && line.charAt(start)=='0') ||
                end>=line.length() || (line.charAt(end)!=',' && line.charAt(end)!='}'))
            throw new IOException("operation-1 GROUP row has invalid integer "+name);
        try { return Long.parseLong(line.substring(start,end)); }
        catch(NumberFormatException invalid) {
            throw new IOException("operation-1 GROUP integer is out of range: "+name,invalid);
        }
    }

    private static List<Object> nodes(Object publisher,Object search) throws Exception {
        Object node=field(publisher,"end");
        ArrayList<Object> reverse=new ArrayList<Object>();
        int minX=intField(search,"minX"),minY=intField(search,"minY");
        int width=intField(search,"width");
        while(node!=null) {
            if(reverse.size()>=MAX_PATH_NODES) throw new IOException("P07 node chain exceeded 4096 nodes");
            int cell=intField(node,"cell"),layer=intField(node,"layer");
            Map<String,Object> row=object();
            row.put("x",Integer.valueOf(minX+(cell%width)*PcbLayerRoutingPrototype.GRID));
            row.put("y",Integer.valueOf(minY+(cell/width)*PcbLayerRoutingPrototype.GRID));
            row.put("layer",PcbCopperLayer.values()[layer].name());
            row.put("direction",Integer.valueOf(intField(node,"direction")));
            row.put("transitions",Integer.valueOf(intField(node,"transitions")));
            row.put("cost",Integer.valueOf(intField(node,"cost")));
            reverse.add(row); node=field(node,"previous");
        }
        if(reverse.isEmpty()) throw new IllegalStateException("P07 candidate has no actual node chain");
        Collections.reverse(reverse); return reverse;
    }

    private static Map<String,Object> validators(final TroubleshootBoard board,
            final PcbBoardLayout layout,Cf result) {
        Map<String,Object> values=object();
        values.put("allNetContinuity","NOT_RUN");
        if(!"FOUND".equals(result.disposition)) {
            values.put("routingGeometry","NOT_RUN");
            values.put("twoLayerRules","NOT_RUN");
            values.put("routeQuality","NOT_RUN");
            values.put("copperGraph","NOT_RUN");
            values.put("endpointsConnected",null);
            return values;
        }
        values.put("routingGeometry","NOT_APPLICABLE");
        values.put("twoLayerRules",validate(new Action(){public void run(){
            new PcbTwoLayerRules(board,layout).validate(layout);
        }},result,"twoLayerRules"));
        values.put("routeQuality",validate(new Action(){public void run(){
            layout.validateRouteQuality();
        }},result,"routeQuality"));
        try {
            PcbConductorGraph graph=PcbConductorBuilder.capture(board,layout);
            values.put("copperGraph","PASS");
            boolean connected=graph.pristine().padsConnected(START,ROOT);
            values.put("endpointsConnected",Boolean.valueOf(connected));
            if(!connected)
                recordValidatorFailure(result,"endpointsConnected",
                    new IllegalStateException("FOUND partial route does not connect selected endpoints"));
        } catch(RuntimeException invalid) {
            values.put("copperGraph","FAIL"); values.put("endpointsConnected",null);
            recordValidatorFailure(result,"copperGraph",invalid);
        }
        return values;
    }

    private interface Action { void run(); }
    private static String validate(Action action,Cf result,String name) {
        try { action.run(); return "PASS"; }
        catch(RuntimeException invalid) {
            recordValidatorFailure(result,name,invalid);
            return "FAIL";
        }
    }

    private static void recordValidatorFailure(Cf result,String name,RuntimeException cause) {
        if(result.validatorCause==null) {
            result.validatorCause=cause;
            result.validatorFailure=name+":"+cause.getClass().getName()+":"+
                String.valueOf(cause.getMessage());
        }
    }

    private static List<Object> traces(PcbBoardLayout layout) {
        ArrayList<Object> rows=new ArrayList<Object>();
        for(PcbTraceGeometry trace:layout.getTraces()) {
            Map<String,Object> row=object();
            row.put("sourceId",trace.getSourceId()); row.put("netId",trace.getNetId());
            row.put("startPadId",trace.getStartPadId()); row.put("endPadId",trace.getEndPadId());
            row.put("layer",trace.getLayer().name()); row.put("width",Integer.valueOf(trace.getWidth()));
            row.put("x",intValues(trace.getXPoints())); row.put("y",intValues(trace.getYPoints()));
            rows.add(row);
        }
        return rows;
    }

    private static List<Object> holes(PcbBoardLayout layout) {
        ArrayList<Object> rows=new ArrayList<Object>();
        for(PcbBoardHole hole:layout.getHoles()) {
            Map<String,Object> row=object();
            row.put("id",hole.id); row.put("netId",hole.netId); row.put("kind",hole.kind.name());
            row.put("x",Integer.valueOf(hole.x)); row.put("y",Integer.valueOf(hole.y));
            row.put("drillRadius",Integer.valueOf(hole.drillRadius));
            row.put("landRadius",Integer.valueOf(hole.landRadius)); rows.add(row);
        }
        return rows;
    }
    private static final class Capture
            implements ActualGeneratorBranchOrderStudy.TerminalPrefixInspector {
        int count,receiptOrdinal,groupOrdinal,lineageOrdinal,pass;
        int prefixBytes,prefixHoleCount,prefixViaCount,callbackUnits;
        String net,start,root,terminal,prefixSha;
        String geometryBefore,geometryAfter,effectsBefore,effectsAfter,detachedGeometrySha;
        String fixturePreflight,fixturePreflightMessage;
        int templateTraces,templateHoles;
        String[] reached,reachedPth;
        List<Object> padLands;
        String padLandJson;
        boolean sourceUnchanged,ownersReleased,contextRestored;
        TroubleshootBoard board;
        PcbBoardLayout template;

        public void onTerminalPrefix(Object search,PcbBoardLayout liveLayout,
                PcbLayerRoutingPrototype.BranchSnapshot snapshot,String prefixFingerprint,
                String prefixSha256,int prefixBytes,int receiptBranchOrdinal,int groupOrdinal) {
            if(snapshot.pass!=PASS || !NET.equals(snapshot.netId) ||
                    !START.equals(snapshot.startPadId) || !ROOT.equals(snapshot.rootPadId)) return;
            if(++count!=1) throw new IllegalStateException("selected terminal branch repeated");
            if(!"NO_PATH".equals(snapshot.terminalReason))
                throw new IllegalStateException("selected terminal branch is no longer NO_PATH");
            byte[] prefix=utf8(prefixFingerprint);
            String actualPrefixSha=sha256(prefix);
            if(!actualPrefixSha.equals(prefixSha256) || prefix.length!=prefixBytes ||
                    !PREFIX_SHA.equals(actualPrefixSha))
                throw new IllegalStateException("actual selected prefix digest differs");
            Object branch=field(search,"branch");
            if(branch==null) throw new IllegalStateException("terminal callback omitted BranchSearch");
            int actualLineage=intField(branch,"branchOrdinal");
            if(receiptBranchOrdinal!=RECEIPT_ORDINAL || actualLineage!=LINEAGE_ORDINAL)
                throw new IllegalStateException("receipt/lineage ordinal differs from frozen branch");

            Vector<?> actualReached=(Vector<?>)field(search,"reachedPadIds");
            if(actualReached==null) throw new IllegalStateException("actual reached vector is missing");
            reached=vectorStrings(actualReached);
            Object pth=field(branch,"reachedTerminals");
            if(!(pth instanceof PcbPadPlacement[]))
                throw new IllegalStateException("actual reached-PTH vector is missing");
            reachedPth=padIds((PcbPadPlacement[])pth);
            if(reached.length!=1 || !ROOT.equals(reached[0]) ||
                    reachedPth.length!=0 || snapshot.reachedPadCount!=1)
                throw new IllegalStateException("actual reached set is not root-only");

            Object owner=field(search,"owner");
            Object boardValue=field(search,"board");
            Object templateValue=field(owner,"template");
            if(!(boardValue instanceof TroubleshootBoard) ||
                    !(templateValue instanceof PcbBoardLayout))
                throw new IllegalStateException("actual Search owner omitted board/template");
            board=(TroubleshootBoard)boardValue;
            PcbBoardLayout original=(PcbBoardLayout)templateValue;
            templateTraces=original.getTraces().size();
            templateHoles=original.getHoles().size();
            if(templateTraces!=0 || templateHoles!=0)
                throw new IllegalStateException("owner template is not copper-free");

            String geometryA=original.geometryFingerprint();
            String effectsA=routerEffects(search);
            List<Object> originalPads=padLands(board,original);
            PcbBoardLayout detached=original.copyForRouting();
            String geometryB=original.geometryFingerprint();
            String effectsB=routerEffects(search);
            List<Object> detachedPads=padLands(board,detached);
            String originalJson=json(originalPads),detachedJson=json(detachedPads);
            if(!geometryA.equals(geometryB) || !effectsA.equals(effectsB) ||
                    !originalJson.equals(detachedJson) ||
                    !detached.getTraces().isEmpty() || !detached.getHoles().isEmpty())
                throw new IllegalStateException("template copy changed source geometry/effects");
            template=detached;
            geometryBefore=hashString(geometryA);
            geometryAfter=hashString(geometryB);
            effectsBefore=hashString(effectsA);
            effectsAfter=hashString(effectsB);
            detachedGeometrySha=hashFingerprint(detached);
            padLands=detachedPads;
            padLandJson=detachedJson;
            sourceUnchanged=true;
            net=snapshot.netId; start=snapshot.startPadId; root=snapshot.rootPadId;
            pass=snapshot.pass; terminal=snapshot.terminalReason;
            prefixSha=actualPrefixSha; this.prefixBytes=prefixBytes;
            prefixHoleCount=liveLayout.getHoles().size();
            prefixViaCount=snapshot.prefixViaCount;
            if(prefixHoleCount!=48 || prefixViaCount!=48)
                throw new IllegalStateException("reference prefix is not the frozen 48-VIA/48-hole prefix");
            receiptOrdinal=receiptBranchOrdinal; this.groupOrdinal=groupOrdinal;
            lineageOrdinal=actualLineage;
            int copyEntries=original.getPads().size()+original.getComponents().size()+
                original.getSilkscreenLabels().size()+original.getRegions().size()+original.getHoles().size();
            callbackUnits=copyEntries+originalPads.size()+detachedPads.size()+
                hashUnits(prefix.length)+hashUnits(utf8(geometryA).length)+hashUnits(utf8(effectsA).length)+
                hashUnits(utf8(geometryB).length)+hashUnits(utf8(effectsB).length)+
                hashUnits(utf8(detached.geometryFingerprint()).length)+5;
        }

        public void afterOwnersClosed(boolean ownersReleased,boolean contextRestored) {
            this.ownersReleased=ownersReleased; this.contextRestored=contextRestored;
        }
    }

    private static String routerEffects(Object search) {
        Object value=field(search,"faces");
        if(!(value instanceof Object[]) || ((Object[])value).length!=2)
            throw new IllegalStateException("actual Search has no two face routers");
        StringBuilder result=new StringBuilder();
        Object[] faces=(Object[])value;
        for(int index=0;index<faces.length;index++) {
            if(!(faces[index] instanceof PcbNetRouter.Router))
                throw new IllegalStateException("actual Search face is missing");
            result.append(index).append(':')
                .append(((PcbNetRouter.Router)faces[index]).fixedGraphObservationEffects()).append('\n');
        }
        return result.toString();
    }

    private static List<Object> padLands(TroubleshootBoard board,PcbBoardLayout layout) {
        ArrayList<PcbPadPlacement> pads=new ArrayList<PcbPadPlacement>(layout.getPads());
        Collections.sort(pads,new Comparator<PcbPadPlacement>() {
            public int compare(PcbPadPlacement a,PcbPadPlacement b) {
                return a.getPadId().compareTo(b.getPadId());
            }
        });
        ArrayList<Object> rows=new ArrayList<Object>();
        for(PcbPadPlacement pad:pads) {
            BoardPad logical=board.getPad(pad.getPadId());
            if(logical==null || pad.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                    !PcbCopperAccess.hasCopper(pad,PcbCopperLayer.TOP) ||
                    !PcbCopperAccess.hasCopper(pad,PcbCopperLayer.BOTTOM))
                throw new IllegalStateException("authored PTH pad export changed");
            Rectangle bounds=pad.getPadBounds();
            Map<String,Object> row=object();
            row.put("padId",pad.getPadId()); row.put("netId",logical.getNetId());
            row.put("x",Integer.valueOf(pad.getX())); row.put("y",Integer.valueOf(pad.getY()));
            row.put("bounds",intValues(new int[] {bounds.x,bounds.y,bounds.width,bounds.height}));
            row.put("attachment",pad.getAttachment().name()); row.put("layer","TOP+BOTTOM");
            rows.add(row);
        }
        if(rows.size()!=246) throw new IllegalStateException("placed pad count differs from 246");
        return rows;
    }

    private static final class Arm {
        final long bytes,elapsedNanos,maxGroupNanos,cleanupNanos,p07OwnerSteps;
        final String sha;
        final boolean ownersReleased,contextRestored;
        final int groupCount,maxUnitsPerGroup,request,selectedAttempt,receiptAttempt;
        final long rootSeed,placementSeed,routingSeed;
        Arm(long bytes,String sha,long elapsed,long maxGroup,long cleanup,boolean released,
                boolean restored,int groups,long ownerSteps,int maxUnits,int request,int selected,int receipt,
                long root,long placement,long routing) {
            this.bytes=bytes; this.sha=sha; elapsedNanos=elapsed; maxGroupNanos=maxGroup;
            cleanupNanos=cleanup; ownersReleased=released; contextRestored=restored;
            groupCount=groups; p07OwnerSteps=ownerSteps; maxUnitsPerGroup=maxUnits; this.request=request;
            selectedAttempt=selected; receiptAttempt=receipt; rootSeed=root;
            placementSeed=placement; routingSeed=routing;
        }
    }

    private static final class Owner {
        TroubleshootBoard board;
        PcbBoardLayout template,layout;
        PcbLayerRoutingPrototype.Session session;
        Object search;
        Method searchMethod;
    }

    private static final class CanaryFault {
        Throwable failure;
        int calls;
        boolean searchPresent,activeBranchPresent,publisherPresent;
        String phase,net,startPadId,rootPadId;
        int branchOrdinal;
    }

    private static final class Cf {
        String disposition,reason,validatorFailure,detachedAfter;
        String fixturePreflight,fixturePreflightMessage;
        String fullGeometryCanary="NOT_RUN";
        Throwable validatorCause;
        long phaseNanos,cleanupNanos,units,searchCalls,publisherCalls,maxGroupNanos;
        int expansions,groups;
        boolean foundCandidate,searchReleased,branchReleased,publisherReleased;
        boolean sessionReleased,layoutReleased,templateReleased,boardReleased,referencesReleased;
        List<Object> nodeChain,publishedTraces,publishedHoles;
        Map<String,Object> validatorRows;

        Map<String,Object> payload() {
            Map<String,Object> out=object();
            out.put("disposition",disposition); out.put("reason",reason);
            out.put("policy",PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.name());
            out.put("pass",Integer.valueOf(PASS)); out.put("netId",NET);
            out.put("startPadId",START); out.put("rootPadId",ROOT);
            out.put("initialTraceCount",Integer.valueOf(0)); out.put("initialHoleCount",Integer.valueOf(0));
            out.put("maximumVias",Integer.valueOf(MAX_VIAS));
            out.put("maximumTransitions",Integer.valueOf(
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.transitions));
            out.put("maximumRequestExpansions",Integer.valueOf(PcbLayerRoutingPrototype.MAX_EXPANSIONS));
            out.put("maximumBranchExpansions",Integer.valueOf(PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS));
            Map<String,Object> work=object();
            work.put("elapsedNanos",Long.valueOf(phaseNanos)); work.put("units",Long.valueOf(units));
            work.put("searchCalls",Long.valueOf(searchCalls));
            work.put("publisherCalls",Long.valueOf(publisherCalls));
            work.put("expansions",Integer.valueOf(expansions)); work.put("groups",Integer.valueOf(groups));
            work.put("maxGroupNanos",Long.valueOf(maxGroupNanos)); out.put("work",work);
            out.put("nodeChain",nodeChain); out.put("publishedTraces",publishedTraces);
            out.put("publishedHoles",publishedHoles); out.put("validators",validatorRows);
            return out;
        }

        Map<String,Object> cleanupJson(boolean contextRestored) {
            Map<String,Object> out=object(); out.put("elapsedNanos",Long.valueOf(cleanupNanos));
            out.put("searchReleased",Boolean.valueOf(searchReleased));
            out.put("branchReleased",Boolean.valueOf(branchReleased));
            out.put("publisherReleased",Boolean.valueOf(publisherReleased));
            out.put("sessionReleased",Boolean.valueOf(sessionReleased));
            out.put("layoutReleased",Boolean.valueOf(layoutReleased));
            out.put("templateReleased",Boolean.valueOf(templateReleased));
            out.put("boardReleased",Boolean.valueOf(boardReleased));
            out.put("referencesReleased",Boolean.valueOf(referencesReleased));
            out.put("contextRestored",Boolean.valueOf(contextRestored)); return out;
        }

        void releaseWrapperSource(Capture capture) {
            if(foundCandidate && nodeChain==null)
                throw new IllegalStateException("found candidate lost its actual node chain");
            if(!capture.sourceUnchanged || !capture.ownersReleased || !capture.contextRestored)
                throw new IllegalStateException("source or original-owner proof is missing");
            nodeChain=null; publishedTraces=null; publishedHoles=null; validatorRows=null;
        }
    }
    private static Map<String,Object> armJson(String runId,String manifest,String wire,Arm a,Capture c) {
        Map<String,Object> out=object();
        out.put("operation",Integer.valueOf(1)); out.put("mode","AUTHORED");
        out.put("runId",runId); out.put("sourceManifestSha256",manifest); out.put("wireSha256",wire);
        out.put("receiptPath","op-1.jsonl"); out.put("receiptBytes",Long.valueOf(a.bytes));
        out.put("receiptSha256",a.sha); out.put("elapsedToCloseNanos",Long.valueOf(a.elapsedNanos));
        out.put("maxFullGroupNanos",Long.valueOf(a.maxGroupNanos));
        out.put("cleanupNanos",Long.valueOf(a.cleanupNanos));
        out.put("ownersReleased",Boolean.valueOf(a.ownersReleased));
        out.put("contextRestored",Boolean.valueOf(a.contextRestored));
        out.put("groupCount",Integer.valueOf(a.groupCount));
        out.put("p07OwnerSteps",Long.valueOf(a.p07OwnerSteps));
        out.put("callbackUnits",Integer.valueOf(c.callbackUnits));
        out.put("requestIndex",Integer.valueOf(a.request));
        out.put("selectedAttempt",Integer.valueOf(a.selectedAttempt));
        out.put("rootSeed",Long.toString(a.rootSeed));
        out.put("placementSeed",Long.toString(a.placementSeed));
        out.put("routingSeed",Long.toString(a.routingSeed));
        return out;
    }

    private static Map<String,Object> referenceJson(Capture c) {
        Map<String,Object> out=object();
        out.put("receiptOrdinal",Integer.valueOf(c.receiptOrdinal));
        out.put("groupOrdinal",Integer.valueOf(c.groupOrdinal));
        out.put("lineageOrdinal",Integer.valueOf(c.lineageOrdinal));
        out.put("pass",Integer.valueOf(c.pass)); out.put("netId",c.net);
        out.put("startPadId",c.start); out.put("rootPadId",c.root);
        out.put("terminalReason",c.terminal); out.put("prefixGeometrySha256",c.prefixSha);
        out.put("prefixGeometryBytes",Integer.valueOf(c.prefixBytes));
        out.put("prefixHoleCount",Integer.valueOf(c.prefixHoleCount));
        out.put("prefixViaCount",Integer.valueOf(c.prefixViaCount));
        out.put("reachedPadIds",stringValues(c.reached));
        out.put("reachedPthPadIds",stringValues(c.reachedPth));
        out.put("reachedPadCount",Integer.valueOf(c.reached.length));
        out.put("reachedPthPadCount",Integer.valueOf(c.reachedPth.length));
        return out;
    }

    private static Map<String,Object> sourceJson(Capture c,String detachedAfter) {
        Map<String,Object> out=object();
        out.put("templateGeometryBeforeSha256",c.geometryBefore);
        out.put("templateGeometryAfterSha256",c.geometryAfter);
        out.put("templateEffectsBeforeSha256",c.effectsBefore);
        out.put("templateEffectsAfterSha256",c.effectsAfter);
        out.put("detachedGeometrySha256",c.detachedGeometrySha);
        out.put("detachedGeometryAfterSha256",detachedAfter);
        out.put("templateTraceCount",Integer.valueOf(c.templateTraces));
        out.put("templateHoleCount",Integer.valueOf(c.templateHoles));
        out.put("padLands",c.padLands);
        out.put("sourceUnchanged",Boolean.valueOf(c.sourceUnchanged));
        return out;
    }

    private static boolean nativeDisposition(Throwable cause) {
        if(cause==null) return false;
        return "Exhausted".equals(cause.getClass().getSimpleName()) ||
            cause instanceof PcbBoardLayout.RouteQualityRejectedException;
    }

    private static String disposition(Throwable cause) {
        if(cause instanceof PcbBoardLayout.RouteQualityRejectedException) return "QUALITY_REJECTED";
        String message=cause==null?null:cause.getMessage();
        if(message==null) return "NO_PATH";
        if(message.contains("TOTAL_SEARCH_LIMIT") || message.contains("BRANCH_SEARCH_LIMIT"))
            return "CAP_REJECTED";
        if(message.contains("QUALITY_LIMIT")) return "QUALITY_REJECTED";
        return "NO_PATH";
    }

    private static Object enumValue(Class<?> type,String name) {
        Object[] constants=type.getEnumConstants();
        if(constants==null) throw new IllegalArgumentException("expected an enum");
        for(Object value:constants) if(((Enum<?>)value).name().equals(name)) return value;
        throw new IllegalArgumentException("maintained enum constant missing: "+name);
    }

    private static Object field(Object target,String name) {
        if(target==null) throw new IllegalStateException("cannot read field from null: "+name);
        Class<?> type=target.getClass();
        while(type!=null) {
            try {
                Field f=type.getDeclaredField(name); f.setAccessible(true); return f.get(target);
            } catch(NoSuchFieldException absent) { type=type.getSuperclass(); }
            catch(IllegalAccessException failure) {
                throw new IllegalStateException("cannot read maintained field "+name,failure);
            }
        }
        throw new IllegalStateException("maintained owner omitted field "+name);
    }

    private static void setField(Object target,String name,Object value) {
        if(target==null) throw new IllegalStateException("cannot clear field on null: "+name);
        try {
            Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target,value);
        } catch(Exception failure) {
            throw new IllegalStateException("cannot clear maintained reference "+name,failure);
        }
    }

    private static void setIntField(Object target,String name,int value) {
        if(target==null) throw new IllegalStateException("cannot update null owner: "+name);
        try {
            Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); f.setInt(target,value);
        } catch(Exception failure) {
            throw new IllegalStateException("cannot update maintained int "+name,failure);
        }
    }

    private static int intField(Object target,String name) {
        Object value=field(target,name);
        if(!(value instanceof Integer)) throw new IllegalStateException("expected integer field "+name);
        return ((Integer)value).intValue();
    }

    private static long longField(Object target,String name) {
        Object value=field(target,name);
        if(!(value instanceof Long)) throw new IllegalStateException("expected long field "+name);
        return ((Long)value).longValue();
    }

    private static boolean booleanField(Object target,String name) {
        Object value=field(target,name);
        if(!(value instanceof Boolean)) throw new IllegalStateException("expected boolean field "+name);
        return ((Boolean)value).booleanValue();
    }

    private static String stringField(Object target,String name) {
        Object value=field(target,name);
        if(!(value instanceof String)) throw new IllegalStateException("expected string field "+name);
        return (String)value;
    }

    private static Object invoke(Method method,Object receiver,Object... arguments) throws Exception {
        return method.invoke(receiver,arguments);
    }

    private static void rethrow(Throwable failure) throws Exception {
        if(failure instanceof Exception) throw (Exception)failure;
        if(failure instanceof Error) throw (Error)failure;
        throw new IllegalStateException("maintained P07 invocation failed",failure);
    }

    private static String[] vectorStrings(Vector<?> values) {
        String[] result=new String[values.size()];
        for(int i=0;i<values.size();i++) {
            Object value=values.get(i);
            if(!(value instanceof String)) throw new IllegalStateException("non-string reached pad");
            result[i]=(String)value;
        }
        return result;
    }

    private static String[] padIds(PcbPadPlacement[] values) {
        String[] result=new String[values.length];
        for(int i=0;i<values.length;i++) result[i]=values[i].getPadId();
        return result;
    }

    private static List<Object> intValues(int[] values) {
        ArrayList<Object> result=new ArrayList<Object>();
        for(int value:values) result.add(Integer.valueOf(value));
        return result;
    }

    private static List<Object> stringValues(String[] values) {
        ArrayList<Object> result=new ArrayList<Object>();
        for(String value:values) result.add(value);
        return result;
    }

    private static Map<String,Object> object() { return new LinkedHashMap<String,Object>(); }

    private static final class Meter {
        long units,searchCalls,publisherCalls,maxGroupNanos,groupStarted;
        int groups,groupUnits,maxGroupUnits;
        boolean open;

        void beginGroup() throws IOException {
            if(open) throw new IllegalStateException("wrapper group already open");
            if(groups>=MAX_GROUPS) throw new IOException("wrapper exceeded 640 groups");
            groupStarted=System.nanoTime(); groupUnits=0; open=true;
        }

        void charge() throws IOException {
            if(!open) throw new IllegalStateException("work unit outside a wrapper group");
            if(groupUnits>=STEP_LIMIT) throw new IOException("wrapper exceeded 32768 units per group");
            groupUnits++; units++;
            if(elapsed(groupStarted,System.nanoTime())>GROUP_NANOS)
                throw new IOException("wrapper group exceeded five seconds");
        }

        void check() {
            if(open && elapsed(groupStarted,System.nanoTime())>GROUP_NANOS)
                throw new IllegalStateException("AttemptObserver exceeded wrapper group clock");
        }

        void finishGroup() throws IOException {
            if(!open) throw new IllegalStateException("no wrapper group to finish");
            long duration=elapsed(groupStarted,System.nanoTime()); open=false; groups++;
            if(duration>GROUP_NANOS) throw new IOException("wrapper group exceeded five seconds");
            if(duration>maxGroupNanos) maxGroupNanos=duration;
            if(groupUnits>maxGroupUnits) maxGroupUnits=groupUnits;
        }
    }

    private static void requireSha(String value,String label) {
        if(value==null || !SHA.matcher(value).matches())
            throw new IllegalArgumentException(label+" must be lowercase hexadecimal SHA-256");
    }

    private static int hashUnits(int bytes) {
        if(bytes<0) throw new IllegalArgumentException("negative hash input");
        return (bytes+4095)/4096;
    }

    private static long elapsed(long start,long end) {
        long value=end-start; return value<0?Long.MAX_VALUE:value;
    }

    private static byte[] utf8(String value) { return value.getBytes(UTF8); }

    private static String hashFingerprint(PcbBoardLayout layout) {
        return hashString(layout.geometryFingerprint());
    }

    private static String hashString(String value) { return sha256(utf8(value)); }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out=new StringBuilder(hash.length*2);
            for(byte value:hash) {
                int v=value&255; out.append("0123456789abcdef".charAt(v>>>4));
                out.append("0123456789abcdef".charAt(v&15));
            }
            return out.toString();
        } catch(NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable",impossible);
        }
    }

    private static final class Digest {
        final long bytes; final String sha;
        Digest(long bytes,String sha) { this.bytes=bytes; this.sha=sha; }
    }

    private static Digest fileDigest(Path path) throws IOException {
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))
            throw new IOException("expected a regular non-symlink file");
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        long count=0; byte[] buffer=new byte[8192];
        try(InputStream in=new BufferedInputStream(Files.newInputStream(path))) {
            int n;
            while((n=in.read(buffer))>=0) {
                if(n==0) continue;
                count+=n; digest.update(buffer,0,n);
            }
        }
        byte[] hash=digest.digest();
        StringBuilder out=new StringBuilder(hash.length*2);
        for(byte value:hash) {
            int v=value&255; out.append("0123456789abcdef".charAt(v>>>4));
            out.append("0123456789abcdef".charAt(v&15));
        }
        return new Digest(count,out.toString());
    }

    private static String json(Object value) {
        StringBuilder out=new StringBuilder(); appendJson(out,value); return out.toString();
    }

    private static void appendJson(StringBuilder out,Object value) {
        if(value==null) out.append("null");
        else if(value instanceof String) quote(out,(String)value);
        else if(value instanceof Number || value instanceof Boolean) out.append(value.toString());
        else if(value instanceof Map) {
            out.append('{'); boolean first=true;
            for(Object raw:((Map<?,?>)value).entrySet()) {
                Map.Entry<?,?> entry=(Map.Entry<?,?>)raw;
                if(!first) out.append(','); first=false;
                quote(out,String.valueOf(entry.getKey())); out.append(':');
                appendJson(out,entry.getValue());
            }
            out.append('}');
        } else if(value instanceof Iterable) {
            out.append('['); boolean first=true;
            for(Object item:(Iterable<?>)value) {
                if(!first) out.append(','); first=false; appendJson(out,item);
            }
            out.append(']');
        } else throw new IllegalArgumentException("unsupported JSON value");
    }

    private static void quote(StringBuilder out,String value) {
        out.append('"');
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='"') out.append("\\\"");
            else if(c=='\\') out.append("\\\\");
            else if(c=='\b') out.append("\\b");
            else if(c=='\f') out.append("\\f");
            else if(c=='\n') out.append("\\n");
            else if(c=='\r') out.append("\\r");
            else if(c=='\t') out.append("\\t");
            else if(c<32) {
                out.append("\\u00");
                out.append("0123456789abcdef".charAt((c>>>4)&15));
                out.append("0123456789abcdef".charAt(c&15));
            } else out.append(c);
        }
        out.append('"');
    }
}
