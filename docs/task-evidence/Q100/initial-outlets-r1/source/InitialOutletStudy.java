package com.lushprojects.circuitjs1.client;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
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

/** Private read-only metric over the actual first P07 branch of a fresh run. */
public final class InitialOutletStudy {
    private static final Charset UTF8=Charset.forName("UTF-8");
    private static final Pattern UUID=Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA=Pattern.compile("[0-9a-f]{64}");
    private static final String PREFIX_SHA=
        "6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb";
    private static final String NET="A_COIL_LOW",START="QA.C",ROOT="DA.A";
    private static final String OP1_FILE="op-1.jsonl",RESULT_FILE="result.json";
    private static final int PASS=2,RECEIPT_ORDINAL=307,LINEAGE_ORDINAL=77;
    private static final int MAX_VIAS=48,MAX_GROUPS=640,STEP_LIMIT=32768;
    private static final int MAX_COPY_ENTRIES=1048576,COPY_CHUNK=4096;
    private static final int LINE_LIMIT=1048576,FILE_LIMIT=8*1024*1024;
    private static final long GROUP_NANOS=5000000000L;
    private static final long OP_NANOS=90000000000L;
    private static final long CLEANUP_NANOS=120000000000L;
    private static final int[] DX={0,10,0,-10};
    private static final int[] DY={-10,0,10,0};
    private static final String[] DIRECTIONS={"N","E","S","W"};
    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP_OBSERVER=
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    private InitialOutletStudy() { }

    public static void main(String[] args) throws Exception {
        long processStarted=System.nanoTime();
        if(args!=null && args.length==1 && "--canary".equals(args[0])) {
            runCanary(processStarted);
            return;
        }
        if(args==null || args.length!=5 || !"OBSERVE".equals(args[0])) {
            rejectInput();
            return;
        }
        Path data;
        try {
            if(!UUID.matcher(args[1]).matches() || !SHA.matcher(args[2]).matches() ||
                    !SHA.matcher(args[3]).matches()) {
                rejectInput();
                return;
            }
            data=Paths.get(args[4]);
            validateEmptyRealDirectory(data);
        } catch(IllegalArgumentException invalid) {
            rejectInput();
            return;
        } catch(IOException invalid) {
            rejectInput();
            return;
        }
        runObserve(args[1],args[2],args[3],data,processStarted);
    }

    private static void rejectInput() {
        System.err.println("REJECTED_INITIAL_OUTLET_INPUT");
        System.exit(1);
    }

    private static void validateEmptyRealDirectory(Path data) throws IOException {
        if(data==null || !data.isAbsolute() || !data.normalize().equals(data) ||
                !Files.isDirectory(data,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(data) ||
                !data.toRealPath().equals(data.toAbsolutePath().normalize()))
            throw new IllegalArgumentException("data directory must be an existing absolute real directory");
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(data)) {
            if(entries.iterator().hasNext())
                throw new IllegalArgumentException("data directory must be empty");
        }
    }

    private static void runObserve(String runId,String manifest,String wire,Path data,
            long processStarted) throws Exception {
        validateEmptyRealDirectory(data);
        Authenticated actual=new Authenticated();
        Meter meter=new Meter(processStarted,0);
        GroupSummary originalGroups;
        try {
            meter.beginGroup("actual old-study capture bridge setup");
            actual.capture=createOriginalCapture();
            meter.charge(1);
            meter.finishGroup();
            actual.arm=runOriginalArm(runId,manifest,wire,data,actual.capture);
            actual.board=(TroubleshootBoard)field(actual.capture,"board");
            actual.template=(PcbBoardLayout)field(actual.capture,"template");
            meter.priorGroups=intField(actual.arm,"groupCount");
            meter.beginGroup("fresh operation-1 request/reference reconciliation");
            validateActualReference(actual,runId,manifest,wire,data);
            long receiptBytes=Files.size(data.resolve(OP1_FILE));
            meter.charge(1+(receiptBytes+COPY_CHUNK-1)/COPY_CHUNK+
                actual.board.getComponentIds().size()+actual.board.getNetIds().size()+
                actual.template.getPads().size());
            meter.finishGroup();
            originalGroups=readOriginalGroups(data.resolve(OP1_FILE),actual.arm,meter,
                intField(actual.capture,"groupOrdinal"),intField(actual.capture,"callbackUnits"));
        } catch(Throwable failure) {
            releaseAuthenticatedSource(actual);
            rethrow(failure);
            return;
        }

        MetricResult measured=null;
        try {
            measured=measure(actual.board,actual.template,meter,null,false,processStarted);
            meter.beginGroup("authored detached-source identity checks");
            if(!measured.sourceUnchanged)
                throw new IllegalStateException("query-only P07 routers changed the actual source geometry");
            validateAuthoredBoard(actual.board,actual.template,measured);
            if(!measured.sourceGeometryBefore.equals(stringField(actual.capture,"detachedGeometrySha")))
                throw new IllegalStateException("captured detached-source fingerprint differs from query source");
            meter.charge(2+actual.board.getComponentIds().size()+actual.board.getNetIds().size()+
                actual.template.getPads().size());
            meter.finishGroup();
        } catch(Throwable failure) {
            releaseAuthenticatedSource(actual);
            rethrow(failure);
            return;
        }

        Map<String,Object> result=object();
        Map<String,Object> op1;
        Map<String,Object> reference;
        Map<String,Object> source;
        Map<String,Object> measurement;
        Map<String,Object> cleanup=object();
        long sourceCopyStart=System.nanoTime();
        meter.beginGroup("result metadata preparation");
        try {
            op1=operation1Json(runId,manifest,wire,actual,originalGroups);
            reference=referenceJson(actual.capture);
            source=sourceJson(actual,measured);
            measurement=measurementJson(measured,actual.board,actual.template);
            result.put("schema","tsj-initial-outlet-v1");
            result.put("mode","OBSERVE");
            result.put("runId",runId);
            result.put("finalRunManifestSha256",manifest);
            result.put("wireContractSha256",wire);
            result.put("operation1",op1);
            result.put("reference",reference);
            result.put("source",source);
            result.put("measurement",measurement);
            cleanup.put("originalOwnersReleased",Boolean.FALSE);
            cleanup.put("contextRestored",Boolean.FALSE);
            cleanup.put("querySearchReleased",Boolean.FALSE);
            cleanup.put("querySessionReleased",Boolean.FALSE);
            cleanup.put("queryLayoutReleased",Boolean.FALSE);
            cleanup.put("queryRoutersReleased",Boolean.FALSE);
            cleanup.put("sourceTemplateReleased",Boolean.FALSE);
            cleanup.put("sourceBoardReleased",Boolean.FALSE);
            cleanup.put("allReferencesReleased",Boolean.FALSE);
            cleanup.put("originalCleanupNanos",Long.valueOf(0));
            cleanup.put("queryCleanupNanos",Long.valueOf(0));
            cleanup.put("sourceReleaseNanos",Long.valueOf(0));
            cleanup.put("combinedCleanupNanos",Long.valueOf(0));
            result.put("cleanup",cleanup);
            Map<String,Object> work=object();
            work.put("cutoff","PRE_RESULT_SERIALIZATION");
            work.put("elapsedNanos",Long.valueOf(0));
            work.put("units",Long.valueOf(0));
            work.put("groups",Integer.valueOf(0));
            work.put("maxGroupNanos",Long.valueOf(0));
            work.put("maximumUnitsPerGroup",Integer.valueOf(0));
            work.put("originalP07OwnerSteps",Long.valueOf(longField(actual.arm,"p07OwnerSteps")));
            work.put("originalDiagnosticUnits",Long.valueOf(originalGroups.diagnosticUnits));
            work.put("originalCallbackUnits",Integer.valueOf(intField(actual.capture,"callbackUnits")));
            work.put("metricUnits",Long.valueOf(0));
            work.put("preExportUnits",Long.valueOf(0));
            work.put("metricGroups",Integer.valueOf(0));
            work.put("preExportGroups",Integer.valueOf(1));
            work.put("queryCount",Long.valueOf(measured.queryCount));
            result.put("work",work);
            long preExportUnits=countJsonNodes(result);
            meter.charge(preExportUnits);
            meter.finishGroup();
            long preExportNanos=elapsed(sourceCopyStart,System.nanoTime());

            long originalCleanup=((Number)op1.get("cleanupNanos")).longValue();
            boolean originalOwnersReleased=((Boolean)op1.get("ownersReleased")).booleanValue();
            boolean originalContextRestored=((Boolean)op1.get("contextRestored")).booleanValue();
            long originalP07=((Number)op1.get("p07OwnerSteps")).longValue();
            long originalCallback=((Number)op1.get("callbackUnits")).longValue();
            int originalGroupCount=((Number)op1.get("groupCount")).intValue();
            long originalMaxGroup=((Number)op1.get("maxFullGroupNanos")).longValue();
            ReleaseSummary released=releaseAuthenticatedSource(actual);
            long combinedCleanup=add(add(originalCleanup,measured.cleanupNanos),released.elapsedNanos);
            if(combinedCleanup>CLEANUP_NANOS)
                throw new IOException("original plus query cleanup exceeded 120 seconds");
            boolean sourceReleased=released.captureReleased && released.boardReleased &&
                released.templateReleased;
            boolean allReleased=sourceReleased && measured.searchReleased &&
                measured.sessionReleased && measured.layoutReleased && measured.routersReleased;
            cleanup.put("originalOwnersReleased",Boolean.valueOf(originalOwnersReleased));
            cleanup.put("contextRestored",Boolean.valueOf(originalContextRestored));
            cleanup.put("querySearchReleased",Boolean.valueOf(measured.searchReleased));
            cleanup.put("querySessionReleased",Boolean.valueOf(measured.sessionReleased));
            cleanup.put("queryLayoutReleased",Boolean.valueOf(measured.layoutReleased));
            cleanup.put("queryRoutersReleased",Boolean.valueOf(measured.routersReleased));
            cleanup.put("sourceTemplateReleased",Boolean.valueOf(released.templateReleased));
            cleanup.put("sourceBoardReleased",Boolean.valueOf(released.boardReleased));
            cleanup.put("allReferencesReleased",Boolean.valueOf(allReleased));
            cleanup.put("originalCleanupNanos",Long.valueOf(originalCleanup));
            cleanup.put("queryCleanupNanos",Long.valueOf(measured.cleanupNanos));
            cleanup.put("sourceReleaseNanos",Long.valueOf(released.elapsedNanos));
            cleanup.put("combinedCleanupNanos",Long.valueOf(combinedCleanup));
            if(!allReleased || !originalOwnersReleased || !originalContextRestored ||
                    !released.captureReleased || released.failure!=null)
                throw new IllegalStateException("native source/query references or original owners were not released");

            Map<String,Object> workValues=(Map<String,Object>)result.get("work");
            long metricUnits=meter.units-preExportUnits;
            long originalUnits=add(add(originalP07,originalGroups.diagnosticUnits),originalCallback);
            long totalUnits=add(originalUnits,meter.units);
            int groups=originalGroupCount+meter.groups;
            int totalGroups=groups+1;
            long maxGroup=Math.max(originalMaxGroup,meter.maxGroupNanos);
            int maxUnits=Math.max(originalGroups.maximumUnitsPerGroup,meter.maximumUnitsPerGroup);
            if(totalGroups>MAX_GROUPS || maxUnits>STEP_LIMIT || maxGroup>GROUP_NANOS)
                throw new IOException("combined native groups exceeded a frozen group bound");
            long beforeExport=elapsed(processStarted,System.nanoTime());
            long workElapsed=beforeExport-combinedCleanup;
            if(workElapsed<0 || workElapsed>OP_NANOS)
                throw new IOException("measured pre-export work exceeded the 90-second bound");
            workValues.put("elapsedNanos",Long.valueOf(workElapsed));
            workValues.put("units",Long.valueOf(totalUnits));
            workValues.put("groups",Integer.valueOf(groups));
            workValues.put("maxGroupNanos",Long.valueOf(maxGroup));
            workValues.put("maximumUnitsPerGroup",Integer.valueOf(maxUnits));
            workValues.put("metricUnits",Long.valueOf(metricUnits));
            workValues.put("preExportUnits",Long.valueOf(preExportUnits));
            workValues.put("metricGroups",Integer.valueOf(meter.groups-1));
            workValues.put("preExportGroups",Integer.valueOf(1));
            actual.arm=null;
            actual.capture=null;
            actual.board=null;
            actual.template=null;
            exportResult(data,result,processStarted,groups,maxGroup,maxUnits);
        } catch(Throwable failure) {
            releaseAuthenticatedSource(actual);
            rethrow(failure);
        }
    }

    private static void validateAuthoredBoard(TroubleshootBoard board,PcbBoardLayout template,
            MetricResult measured) {
        if(board==null || template==null || board.getComponentIds().size()!=100 ||
                board.getNetIds().size()!=60 || template.getPads().size()!=246 ||
                template.getTraces().size()!=0 || template.getHoles().size()!=0 ||
                board.getPlacementConstraints().getTraceWidthProfile()!=PcbTraceWidthProfile.ROLE_WIDTHS ||
                measured.rows.size()!=60 || !measured.sourceUnchanged)
            throw new IllegalStateException("actual detached source differs from the frozen 100/246/60 copper-free board");
    }

    private static Object createOriginalCapture() throws Exception {
        Class<?> owner=Class.forName("com.lushprojects.circuitjs1.client.CopperFreeBranchStudy");
        Class<?> captureType=Class.forName(owner.getName()+"$Capture");
        Constructor<?> constructor=captureType.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object runOriginalArm(String runId,String manifest,String wire,Path data,
            Object capture) throws Exception {
        if(capture==null) throw new IllegalArgumentException("missing actual source capture");
        Class<?> owner=Class.forName("com.lushprojects.circuitjs1.client.CopperFreeBranchStudy");
        Method run=owner.getDeclaredMethod("runArm",String.class,String.class,String.class,
            Path.class,capture.getClass());
        run.setAccessible(true);
        return invoke(run,null,runId,manifest,wire,data,capture);
    }

    private static void validateActualReference(Authenticated actual,String runId,
            String manifest,String wire,Path data) throws Exception {
        Object arm=actual.arm,capture=actual.capture;
        if(arm==null || capture==null || actual.board==null || actual.template==null)
            throw new IllegalStateException("actual operation-1 adapter omitted detached source");
        if(!booleanField(arm,"ownersReleased") || !booleanField(arm,"contextRestored") ||
                !booleanField(capture,"ownersReleased") || !booleanField(capture,"contextRestored"))
            throw new IllegalStateException("actual operation-1 owners/context did not close");
        if(intField(arm,"request")!=0 || intField(arm,"selectedAttempt")!=4 ||
                intField(arm,"receiptAttempt")!=4 ||
                longField(arm,"rootSeed")!=9007199254740993L ||
                longField(arm,"placementSeed")!=-6587958367974952701L ||
                longField(arm,"routingSeed")!=8445301674744508279L)
            throw new IllegalStateException("fresh actual operation-1 request identity changed");
        if(intField(capture,"count")!=1 || intField(capture,"receiptOrdinal")!=RECEIPT_ORDINAL ||
                intField(capture,"lineageOrdinal")!=LINEAGE_ORDINAL || intField(capture,"pass")!=PASS ||
                !NET.equals(stringField(capture,"net")) || !START.equals(stringField(capture,"start")) ||
                !ROOT.equals(stringField(capture,"root")) || !"NO_PATH".equals(stringField(capture,"terminal")) ||
                !PREFIX_SHA.equals(stringField(capture,"prefixSha")) ||
                intField(capture,"prefixHoleCount")!=48 || intField(capture,"prefixViaCount")!=48 ||
                !booleanField(capture,"sourceUnchanged"))
            throw new IllegalStateException("fresh actual operation-1 branch/prefix differs from the frozen reference");
        String[] reached=(String[])field(capture,"reached");
        String[] reachedPth=(String[])field(capture,"reachedPth");
        if(reached==null || reached.length!=1 || !ROOT.equals(reached[0]) ||
                reachedPth==null || reachedPth.length!=0)
            throw new IllegalStateException("actual first-branch reached targets are not root-only");
        if(actual.template.getTraces().size()!=0 || actual.template.getHoles().size()!=0)
            throw new IllegalStateException("actual detached placement is not copper-free");
        Path receipt=data.resolve(OP1_FILE);
        Digest digest=fileDigest(receipt,FILE_LIMIT);
        if(digest.bytes!=longField(arm,"bytes") || !digest.sha.equals(stringField(arm,"sha")))
            throw new IOException("fresh operation-1 JSONL differs from the actual old-study ArmResult");
        if(intField(arm,"groupCount")<1 || intField(arm,"groupCount")>MAX_GROUPS ||
                longField(arm,"maxGroupNanos")<=0 || longField(arm,"maxGroupNanos")>GROUP_NANOS)
            throw new IOException("actual operation-1 group bounds failed");
        if(runId.length()!=36 || !SHA.matcher(manifest).matches() || !SHA.matcher(wire).matches())
            throw new IllegalArgumentException("actual operation-1 identity fields are malformed");
    }

    private static GroupSummary readOriginalGroups(Path path,Object arm,Meter meter,
            int callbackGroup,int callbackUnits) throws Exception {
        GroupSummary result=new GroupSummary();
        long size=Files.size(path);
        if(size<1 || size>FILE_LIMIT) throw new IOException("operation-1 receipt exceeds 8 MiB");
        meter.beginGroup("operation-1 GROUP audit");
        boolean[] seen=new boolean[intField(arm,"groupCount")];
        try(BufferedReader reader=Files.newBufferedReader(path,UTF8)) {
            String line;
            while((line=reader.readLine())!=null) {
                if(line.length()>LINE_LIMIT) throw new IOException("operation-1 line exceeded 1 MiB");
                meter.charge(1);
                if(!line.contains("\"record\":\"GROUP\"")) continue;
                long operation=jsonLongField(line,"operation",false);
                long ordinal=jsonLongField(line,"ordinal",false);
                long p07=jsonLongField(line,"p07Steps",true);
                long diagnostic=jsonLongField(line,"diagnosticUnits",false);
                meter.charge(4);
                if(operation!=1 || ordinal<0 || ordinal>=seen.length || seen[(int)ordinal] ||
                        diagnostic<0 || diagnostic>STEP_LIMIT || p07< -1 || p07>STEP_LIMIT)
                    throw new IOException("operation-1 GROUP row is invalid or duplicated");
                seen[(int)ordinal]=true;
                result.groupCount++;
                result.diagnosticUnits=add(result.diagnosticUnits,diagnostic);
                if(p07>=0) result.p07OwnerStepsSum=add(result.p07OwnerStepsSum,p07);
                long units=(p07<0?0:p07)+diagnostic;
                if(ordinal==callbackGroup) units=add(units,callbackUnits);
                if(units>STEP_LIMIT) throw new IOException("actual operation-1 GROUP exceeded 32768 units");
                if(units>result.maximumUnitsPerGroup) result.maximumUnitsPerGroup=(int)units;
            }
        }
        for(boolean value:seen) if(!value) throw new IOException("operation-1 GROUP ordinals are incomplete");
        if(result.groupCount!=seen.length || callbackGroup<0 || callbackGroup>=seen.length ||
                result.maximumUnitsPerGroup!=intField(arm,"maxUnitsPerGroup") ||
                result.p07OwnerStepsSum!=longField(arm,"p07OwnerSteps"))
            throw new IOException("operation-1 GROUP audit differs from the live ArmRun ledger");
        meter.finishGroup();
        return result;
    }

    private static long jsonLongField(String line,String name,boolean nullable) throws IOException {
        String key="\""+name+"\":";
        int at=line.indexOf(key);
        if(at<0 || line.indexOf(key,at+key.length())>=0)
            throw new IOException("operation-1 GROUP omitted or duplicated "+name);
        int start=at+key.length(),end=start;
        if(nullable && line.startsWith("null",start)) return -1;
        while(end<line.length() && line.charAt(end)>='0' && line.charAt(end)<='9') end++;
        if(end==start || (end-start>1 && line.charAt(start)=='0') || end>=line.length() ||
                (line.charAt(end)!=',' && line.charAt(end)!='}'))
            throw new IOException("operation-1 GROUP has invalid integer "+name);
        try { return Long.parseLong(line.substring(start,end)); }
        catch(NumberFormatException invalid) { throw new IOException("GROUP integer out of range",invalid); }
    }

    private static Map<String,Object> operation1Json(String runId,String manifest,String wire,
            Authenticated actual,GroupSummary groups) throws Exception {
        Object arm=actual.arm,capture=actual.capture;
        Map<String,Object> out=object();
        out.put("operation",Integer.valueOf(1)); out.put("mode","AUTHORED");
        out.put("runId",runId); out.put("sourceManifestSha256",manifest); out.put("wireSha256",wire);
        out.put("receiptPath",OP1_FILE); out.put("receiptBytes",Long.valueOf(longField(arm,"bytes")));
        out.put("receiptSha256",stringField(arm,"sha"));
        out.put("elapsedToCloseNanos",Long.valueOf(longField(arm,"elapsedNanos")));
        out.put("maxFullGroupNanos",Long.valueOf(longField(arm,"maxGroupNanos")));
        out.put("cleanupNanos",Long.valueOf(longField(arm,"cleanupNanos")));
        out.put("ownersReleased",Boolean.valueOf(booleanField(arm,"ownersReleased")));
        out.put("contextRestored",Boolean.valueOf(booleanField(arm,"contextRestored")));
        out.put("groupCount",Integer.valueOf(intField(arm,"groupCount")));
        out.put("p07OwnerSteps",Long.valueOf(longField(arm,"p07OwnerSteps")));
        out.put("diagnosticUnits",Long.valueOf(groups.diagnosticUnits));
        out.put("groupP07StepsSum",Long.valueOf(groups.p07OwnerStepsSum));
        out.put("callbackUnits",Integer.valueOf(intField(capture,"callbackUnits")));
        out.put("maximumUnitsPerGroup",Integer.valueOf(groups.maximumUnitsPerGroup));
        out.put("requestIndex",Integer.valueOf(intField(arm,"request")));
        out.put("selectedAttempt",Integer.valueOf(intField(arm,"selectedAttempt")));
        out.put("receiptAttempt",Integer.valueOf(intField(arm,"receiptAttempt")));
        out.put("rootSeed",Long.toString(longField(arm,"rootSeed")));
        out.put("placementSeed",Long.toString(longField(arm,"placementSeed")));
        out.put("routingSeed",Long.toString(longField(arm,"routingSeed")));
        return out;
    }

    private static Map<String,Object> referenceJson(Object capture) throws Exception {
        Map<String,Object> out=object();
        out.put("receiptOrdinal",Integer.valueOf(intField(capture,"receiptOrdinal")));
        out.put("groupOrdinal",Integer.valueOf(intField(capture,"groupOrdinal")));
        out.put("lineageOrdinal",Integer.valueOf(intField(capture,"lineageOrdinal")));
        out.put("pass",Integer.valueOf(intField(capture,"pass")));
        out.put("netId",stringField(capture,"net"));
        out.put("startPadId",stringField(capture,"start"));
        out.put("rootPadId",stringField(capture,"root"));
        out.put("terminalReason",stringField(capture,"terminal"));
        out.put("prefixGeometrySha256",stringField(capture,"prefixSha"));
        out.put("prefixGeometryBytes",Integer.valueOf(intField(capture,"prefixBytes")));
        out.put("prefixHoleCount",Integer.valueOf(intField(capture,"prefixHoleCount")));
        out.put("prefixViaCount",Integer.valueOf(intField(capture,"prefixViaCount")));
        String[] reached=(String[])field(capture,"reached");
        String[] reachedPth=(String[])field(capture,"reachedPth");
        out.put("reachedPadIds",strings(reached)); out.put("reachedPthPadIds",strings(reachedPth));
        out.put("reachedPadCount",Integer.valueOf(reached.length));
        out.put("reachedPthPadCount",Integer.valueOf(reachedPth.length));
        return out;
    }

    private static Map<String,Object> sourceJson(Authenticated actual,MetricResult metric)
            throws Exception {
        Object capture=actual.capture;
        Map<String,Object> out=object();
        out.put("componentCount",Integer.valueOf(actual.board.getComponentIds().size()));
        out.put("netCount",Integer.valueOf(actual.board.getNetIds().size()));
        out.put("templateGeometryBeforeSha256",stringField(capture,"geometryBefore"));
        out.put("templateGeometryAfterSha256",stringField(capture,"geometryAfter"));
        out.put("templateEffectsBeforeSha256",stringField(capture,"effectsBefore"));
        out.put("templateEffectsAfterSha256",stringField(capture,"effectsAfter"));
        out.put("detachedGeometrySha256",stringField(capture,"detachedGeometrySha"));
        out.put("detachedGeometryAfterSha256",metric.sourceGeometryAfter);
        out.put("queryRouterEffectsBeforeSha256",metric.queryEffectsBefore);
        out.put("queryRouterEffectsAfterSha256",metric.queryEffectsAfter);
        out.put("templateTraceCount",Integer.valueOf(intField(capture,"templateTraces")));
        out.put("templateHoleCount",Integer.valueOf(intField(capture,"templateHoles")));
        out.put("sourceUnchanged",Boolean.valueOf(metric.sourceUnchanged));
        out.put("padLands",copyPadLands((List<?>)field(capture,"padLands")));
        return out;
    }

    private static List<Object> copyPadLands(List<?> rows) throws Exception {
        if(rows==null || rows.size()!=246 || rows.size()>MAX_COPY_ENTRIES)
            throw new IllegalStateException("actual source pad table is incomplete or over the copy limit");
        ArrayList<Object> copied=new ArrayList<Object>(rows.size());
        String previous=null;
        for(Object value:rows) {
            if(!(value instanceof Map)) throw new IllegalStateException("actual pad row is not an object");
            Map<?,?> source=(Map<?,?>)value;
            Map<String,Object> row=object();
            String padId=(String)source.get("padId");
            if(padId==null || previous!=null && previous.compareTo(padId)>=0)
                throw new IllegalStateException("actual source pad rows are not strictly sorted");
            previous=padId;
            row.put("padId",padId); row.put("netId",source.get("netId"));
            row.put("x",source.get("x")); row.put("y",source.get("y"));
            row.put("bounds",new ArrayList<Object>((List<?>)source.get("bounds")));
            row.put("attachment",source.get("attachment")); row.put("layer",source.get("layer"));
            copied.add(row);
        }
        return copied;
    }

    private static Map<String,Object> measurementJson(MetricResult metric,TroubleshootBoard board,
            PcbBoardLayout template) throws Exception {
        PcbCopperLayer primary=board.getPlacementConstraints().routingLayer;
        PcbCopperLayer opposite=primary==PcbCopperLayer.TOP?PcbCopperLayer.BOTTOM:PcbCopperLayer.TOP;
        Map<String,Object> out=object();
        out.put("policy",PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.name());
        out.put("pass",Integer.valueOf(PASS)); out.put("widthProfile","ROLE_WIDTHS");
        out.put("grid",Integer.valueOf(PcbLayerRoutingPrototype.GRID));
        out.put("primaryFace",primary.name()); out.put("oppositeFace",opposite.name());
        out.put("faceOrder",strings(new String[] {"TOP","BOTTOM"}));
        out.put("directionOrder",strings(DIRECTIONS));
        out.put("directionBits",directionBits()); out.put("queriesPerPair",Integer.valueOf(16));
        out.put("queryCount",Long.valueOf(metric.queryCount));
        out.put("singletonNetCount",Integer.valueOf(metric.singletonNetCount));
        out.put("pairedNetCount",Integer.valueOf(metric.pairedNetCount));
        ArrayList<Object> rows=new ArrayList<Object>(metric.rows.size());
        LinkedHashMap<String,Object> histogram=new LinkedHashMap<String,Object>();
        int[] counts=new int[9];
        for(PairMetric row:metric.rows) {
            rows.add(row.json());
            if(row.paired) counts[row.value]++;
        }
        int distinct=0;
        Map<String,Object> values=object();
        for(int value=0;value<=8;value++) {
            values.put(Integer.toString(value),Integer.valueOf(counts[value]));
            if(counts[value]>0) distinct++;
        }
        histogram.putAll(values);
        Map<String,Object> distribution=object();
        distribution.put("pairedValueHistogram",histogram);
        distribution.put("distinctValueCount",Integer.valueOf(distinct));
        distribution.put("varies",Boolean.valueOf(distinct>1));
        out.put("rows",rows); out.put("distribution",distribution);
        out.put("sourceGeometryBeforeSha256",metric.sourceGeometryBefore);
        out.put("sourceGeometryAfterSha256",metric.sourceGeometryAfter);
        out.put("coldReplayMatches",metric.coldReplayPerformed?
            Boolean.valueOf(metric.coldReplayMatches):null);
        if(template.getTraces().size()!=0 || template.getHoles().size()!=0)
            throw new IllegalStateException("source placement acquired copper during metric export");
        return out;
    }

    private static Map<String,Object> directionBits() {
        Map<String,Object> out=object();
        for(int index=0;index<DIRECTIONS.length;index++)
            out.put(DIRECTIONS[index],Integer.valueOf(1<<index));
        return out;
    }

    private static MetricResult measure(TroubleshootBoard sourceBoard,PcbBoardLayout sourceTemplate,
            Meter meter,Fault fault,boolean replay,long processStarted) throws Exception {
        if(sourceBoard==null || sourceTemplate==null || meter==null)
            throw new IllegalArgumentException("metric source and meter are required");
        MetricResult result=new MetricResult();
        QueryOwner owner=new QueryOwner();
        long unitsBefore=meter.units;
        int groupsBefore=meter.groups;
        Throwable problem=null;
        long metricStarted=System.nanoTime();
        owner.board=sourceBoard; owner.sourceTemplate=sourceTemplate;
        try {
            meter.beginGroup("fresh query-only P07 Search setup");
            if(sourceTemplate.getTraces().size()!=0 || sourceTemplate.getHoles().size()!=0)
                throw new IllegalStateException("metric template must be copper-free");
            String beforeText=sourceTemplate.geometryFingerprint();
            byte[] beforeBytes=utf8(beforeText);
            result.sourceGeometryBefore=sha256(beforeBytes);
            meter.charge(1+hashUnits(beforeBytes.length));
            long firstCopyEntries=copyEntryCount(sourceTemplate);
            requireOneCopyChunk(firstCopyEntries,"source-to-session template");
            meter.charge(5);
            owner.session=new PcbLayerRoutingPrototype.Session(sourceBoard,sourceTemplate,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,NOOP_OBSERVER,
                PcbLayerRoutingPrototype.MAX_EXPANSIONS,MAX_VIAS);
            meter.charge(firstCopyEntries);
            PcbBoardLayout sessionTemplate=(PcbBoardLayout)field(owner.session,"template");
            long secondCopyEntries=copyEntryCount(sessionTemplate);
            requireOneCopyChunk(secondCopyEntries,"session-to-query template");
            if(secondCopyEntries!=firstCopyEntries)
                throw new IllegalStateException("P07 session copy changed source copy-entry counts");
            meter.charge(5);
            owner.layout=sessionTemplate.copyForRouting();
            meter.charge(secondCopyEntries);
            owner.search=createSearch(owner.session,sourceBoard,owner.layout,NOOP_OBSERVER);
            setField(owner.session,"candidate",owner.layout);
            setField(owner.session,"search",owner.search);
            setIntField(owner.session,"pass",PASS);
            Object faceValue=field(owner.search,"faces");
            if(!(faceValue instanceof Object[]) || ((Object[])faceValue).length!=2)
                throw new IllegalStateException("fresh P07 Search omitted its two face routers");
            owner.faces=(Object[])faceValue;
            if(owner.faces[0]==null || owner.faces[1]==null ||
                    field(owner.search,"branch")!=null || field(owner.search,"publisher")!=null ||
                    booleanField(owner.search,"complete"))
                throw new IllegalStateException("query Search advanced beyond router initialization");
            for(int index=0;index<owner.faces.length;index++)
                if(!(owner.faces[index] instanceof PcbNetRouter.Router) ||
                        PcbCopperLayer.values()[index].name().length()==0)
                    throw new IllegalStateException("query Search face identity is invalid");
            meter.charge(1+3L*owner.layout.getPads().size()+
                2L*owner.layout.getComponents().size());
            String beforeEffects=routerEffects(owner.faces);
            byte[] beforeEffectBytes=utf8(beforeEffects);
            result.queryEffectsBefore=sha256(beforeEffectBytes);
            meter.charge(1+hashUnits(beforeEffectBytes.length));
            Vector<String> actualNets=sourceBoard.getNetIds();
            Collections.sort(actualNets);
            meter.charge(1+actualNets.size());
            meter.finishGroup();
            if(actualNets.size()>MAX_GROUPS-1)
                throw new IOException("source net count exceeds the bounded per-net group ledger");
            PcbCopperLayer primary=sourceBoard.getPlacementConstraints().routingLayer;
            result.primaryFace=primary.name();
            result.oppositeFace=(primary==PcbCopperLayer.TOP?PcbCopperLayer.BOTTOM:PcbCopperLayer.TOP).name();
            for(String netId:actualNets) {
                checkOperationClock(processStarted);
                meter.beginGroup("canonical net outlet measurements");
                PairMetric row=preparePair(sourceBoard,owner.layout,netId,meter);
                result.rows.add(row);
                if(row.paired) {
                    result.pairedNetCount++;
                    meter.chargeAndCheck(row.padIds.size(),processStarted);
                    PairMetric probed=probePair(owner.faces,owner.layout,row,meter,fault,processStarted);
                    row.copyMasks(probed);
                    result.queryCount+=16L;
                } else result.singletonNetCount++;
                meter.finishGroup();
            }
            if(replay) {
                boolean same=true;
                for(PairMetric row:result.rows) {
                    if(!row.paired) continue;
                    checkOperationClock(processStarted);
                    meter.beginGroup("same-router cold/replay comparison");
                    PairMetric repeated=probePair(owner.faces,owner.layout,row,meter,null,processStarted);
                    if(!row.sameMasks(repeated)) same=false;
                    result.queryCount+=16L;
                    meter.finishGroup();
                }
                result.coldReplayPerformed=true;
                result.coldReplayMatches=same;
            }
            meter.beginGroup("final source and query-only effects verification");
            String afterText=sourceTemplate.geometryFingerprint();
            byte[] afterBytes=utf8(afterText);
            result.sourceGeometryAfter=sha256(afterBytes);
            meter.charge(1+hashUnits(afterBytes.length));
            String afterEffects=routerEffects(owner.faces);
            byte[] afterEffectBytes=utf8(afterEffects);
            result.queryEffectsAfter=sha256(afterEffectBytes);
            meter.charge(1+hashUnits(afterEffectBytes.length)+3);
            result.sourceUnchanged=result.sourceGeometryBefore.equals(result.sourceGeometryAfter) &&
                sourceTemplate.getTraces().isEmpty() && sourceTemplate.getHoles().isEmpty() &&
                owner.layout.getTraces().isEmpty() && owner.layout.getHoles().isEmpty();
            meter.finishGroup();
            result.metricElapsedNanos=elapsed(metricStarted,System.nanoTime());
            if(!result.sourceUnchanged)
                throw new IllegalStateException("query-only P07 metric changed source or copper-free layout");
            if(replay && !result.coldReplayMatches)
                throw new IllegalStateException("same-router metric replay changed literal masks");
        } catch(Throwable failure) {
            problem=failure;
            if(meter.open) {
                try { meter.finishGroup(); }
                catch(Throwable accounting) { problem.addSuppressed(accounting); }
            }
        } finally {
            long cleanupStarted=System.nanoTime();
            try { releaseQueryOwner(owner,result); }
            catch(Throwable cleanupFailure) {
                if(problem==null) problem=cleanupFailure;
                else problem.addSuppressed(cleanupFailure);
            }
            result.cleanupNanos=elapsed(cleanupStarted,System.nanoTime());
            result.searchReleased=owner.search==null;
            result.sessionReleased=owner.session==null;
            result.layoutReleased=owner.layout==null;
            result.routersReleased=owner.faces==null;
            result.allQueryReferencesReleased=result.searchReleased && result.sessionReleased &&
                result.layoutReleased && result.routersReleased && owner.board==null &&
                owner.sourceTemplate==null;
            if(fault!=null) {
                fault.cleanupNanos=result.cleanupNanos;
                fault.searchReleased=result.searchReleased;
                fault.sessionReleased=result.sessionReleased;
                fault.layoutReleased=result.layoutReleased;
                fault.routersReleased=result.routersReleased;
                fault.allReferencesReleased=result.allQueryReferencesReleased;
                fault.groups=meter.groups-groupsBefore; fault.units=meter.units-unitsBefore;
                fault.maxGroupNanos=meter.maxGroupNanos;
                fault.maximumUnitsPerGroup=meter.maximumUnitsPerGroup;
                fault.nativeElapsedNanos=elapsed(processStarted,System.nanoTime());
            }
        }
        if(result.cleanupNanos>CLEANUP_NANOS || !result.allQueryReferencesReleased) {
            IllegalStateException releaseFailure=new IllegalStateException("query-only P07 references were not released");
            if(problem==null) problem=releaseFailure; else problem.addSuppressed(releaseFailure);
        }
        if(problem!=null) rethrow(problem);
        if(meter.groups>MAX_GROUPS || meter.maximumUnitsPerGroup>STEP_LIMIT ||
                meter.maxGroupNanos>GROUP_NANOS)
            throw new IOException("query-only metric exceeded a frozen group bound");
        result.metricUnits=meter.units; result.metricGroups=meter.groups;
        result.metricMaxGroupNanos=meter.maxGroupNanos;
        result.metricMaximumUnitsPerGroup=meter.maximumUnitsPerGroup;
        return result;
    }

    private static PairMetric preparePair(TroubleshootBoard board,PcbBoardLayout layout,
            String netId,Meter meter) throws Exception {
        BoardNet net=board.getNet(netId);
        if(net==null) throw new IllegalStateException("actual board net disappeared: "+netId);
        Vector<String> padIds=net.getPadIds();
        Collections.sort(padIds);
        if(padIds.size()==0) throw new IllegalStateException("source net has no physical pad: "+netId);
        for(int index=0;index<padIds.size();index++) {
            if(index>0 && padIds.get(index-1).equals(padIds.get(index)))
                throw new IllegalStateException("source net has duplicate pad id: "+netId);
            PcbPadPlacement pad=layout.getPad(padIds.get(index));
            BoardPad logical=board.getPad(padIds.get(index));
            if(pad==null || logical==null || !netId.equals(logical.getNetId()))
                throw new IllegalStateException("source net/pad identity differs: "+netId);
        }
        PairMetric row=new PairMetric();
        row.netId=netId; row.padIds=new ArrayList<String>(padIds);
        row.role=net.getRoutingRole().name();
        row.traceWidth=PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(net.getRoutingRole());
        if(padIds.size()==1) {
            meter.charge(1L+padIds.size());
            return row;
        }
        row.paired=true; row.rootPadId=padIds.get(0);
        Vector<String> remaining=new Vector<String>();
        for(int index=1;index<padIds.size();index++) remaining.add(padIds.get(index));
        Vector<String> reached=new Vector<String>(); reached.add(row.rootPadId);
        if(board.getPlacementConstraints().getTraceWidthProfile()!=PcbTraceWidthProfile.ROLE_WIDTHS)
            throw new IllegalStateException("metric source is not ROLE_WIDTHS");
        row.startPadId=PcbLayerRoutingPrototype.chooseBranchPad(layout,remaining,reached,PASS,true,
            PcbLayerRoutingPrototype.BranchOrderStrategy.CANONICAL_NEAREST);
        if(row.startPadId==null || !remaining.contains(row.startPadId) ||
                row.startPadId.equals(row.rootPadId))
            throw new IllegalStateException("normal pass-2 chooser did not select a distinct remaining pad");
        meter.charge(2+padIds.size());
        return row;
    }

    private static PairMetric probePair(Object[] faces,PcbBoardLayout layout,PairMetric pair,
            Meter meter,Fault fault,long processStarted) throws Exception {
        if(pair==null || !pair.paired || faces==null || faces.length!=2)
            throw new IllegalArgumentException("a selected multi-pad pair and two actual routers are required");
        PcbPadPlacement start=layout.getPad(pair.startPadId);
        PcbPadPlacement root=layout.getPad(pair.rootPadId);
        if(start==null || root==null || start==root ||
                start.getX()==root.getX() && start.getY()==root.getY())
            throw new IllegalStateException("pair query endpoints must be distinct actual pads");
        int[] forward={0,0},reverse={0,0};
        for(int face=0;face<2;face++) {
            for(int direction=0;direction<4;direction++) {
                int x=start.getX()+DX[direction],y=start.getY()+DY[direction];
                boolean allowed=((PcbNetRouter.Router)faces[face]).permitsLayerStep(
                    start.getX(),start.getY(),x,y,pair.netId,start,root);
                meter.chargeAndCheck(1,processStarted);
                if(allowed) forward[face]|=1<<direction;
                afterQuery(fault,faces);
            }
        }
        for(int face=0;face<2;face++) {
            for(int direction=0;direction<4;direction++) {
                int x=root.getX()+DX[direction],y=root.getY()+DY[direction];
                boolean allowed=((PcbNetRouter.Router)faces[face]).permitsLayerStep(
                    root.getX(),root.getY(),x,y,pair.netId,root,start);
                meter.chargeAndCheck(1,processStarted);
                if(allowed) reverse[face]|=1<<direction;
                afterQuery(fault,faces);
            }
        }
        PairMetric measured=new PairMetric();
        measured.netId=pair.netId; measured.padIds=pair.padIds;
        measured.role=pair.role; measured.traceWidth=pair.traceWidth;
        measured.paired=true; measured.rootPadId=pair.rootPadId; measured.startPadId=pair.startPadId;
        measured.forwardTopMask=forward[0]; measured.forwardBottomMask=forward[1];
        measured.reverseTopMask=reverse[0]; measured.reverseBottomMask=reverse[1];
        measured.forwardTopCount=Integer.bitCount(forward[0]);
        measured.forwardBottomCount=Integer.bitCount(forward[1]);
        measured.reverseTopCount=Integer.bitCount(reverse[0]);
        measured.reverseBottomCount=Integer.bitCount(reverse[1]);
        measured.forwardTotal=measured.forwardTopCount+measured.forwardBottomCount;
        measured.reverseTotal=measured.reverseTopCount+measured.reverseBottomCount;
        measured.value=Math.min(measured.forwardTotal,measured.reverseTotal);
        return measured;
    }

    private static void afterQuery(Fault fault,Object[] faces) {
        if(fault==null) return;
        fault.completedQueries++;
        if(fault.completedQueries!=1) return;
        fault.routersInstalled=faces!=null && faces.length==2 && faces[0]!=null && faces[1]!=null;
        if(!fault.routersInstalled)
            throw new IllegalStateException("failure canary did not observe both installed query routers");
        if(fault.failure==null) fault.failure=new IllegalStateException("CANARY_INJECTED_AFTER_FIRST_QUERY");
        throw (IllegalStateException)fault.failure;
    }

    private static Object createSearch(PcbLayerRoutingPrototype.Session session,
            TroubleshootBoard board,PcbBoardLayout layout,
            SeededPcbLayoutGenerator.AttemptObserver observer) throws Exception {
        Class<?> type=Class.forName(PcbLayerRoutingPrototype.class.getName()+"$Search");
        Object budget=field(session,"budget");
        Constructor<?> constructor=type.getDeclaredConstructor(PcbLayerRoutingPrototype.Session.class,
            TroubleshootBoard.class,PcbBoardLayout.class,PcbLayerRoutingPrototype.Policy.class,
            SeededPcbLayoutGenerator.AttemptObserver.class,budget.getClass(),int.class,String.class,int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(session,board,layout,PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            observer,budget,Integer.valueOf(PASS),null,Integer.valueOf(MAX_VIAS));
    }

    private static String routerEffects(Object[] faces) throws Exception {
        StringBuilder value=new StringBuilder();
        for(int index=0;index<faces.length;index++) {
            PcbNetRouter.Router router=(PcbNetRouter.Router)faces[index];
            value.append(index).append(':').append(router.fixedGraphObservationEffects()).append('\n');
        }
        return value.toString();
    }

    private static void releaseQueryOwner(QueryOwner owner,MetricResult result) throws Exception {
        if(owner.search!=null) {
            setField(owner.search,"branch",null);
            setField(owner.search,"publisher",null);
            setField(owner.search,"currentPadIds",null);
            setField(owner.search,"reachedPadIds",null);
            setField(owner.search,"nets",null);
            setField(owner.search,"currentRoot",null);
            setField(owner.search,"netOrderPreparation",null);
            if(owner.faces!=null) for(int index=0;index<owner.faces.length;index++) owner.faces[index]=null;
            Object actualFaces=field(owner.search,"faces");
            result.routersReleased=actualFaces instanceof Object[] &&
                ((Object[])actualFaces).length==2 &&
                ((Object[])actualFaces)[0]==null && ((Object[])actualFaces)[1]==null;
        } else result.routersReleased=true;
        if(owner.session!=null) {
            setField(owner.session,"search",null);
            setField(owner.session,"candidate",null);
        }
        owner.faces=null; owner.search=null; owner.session=null; owner.layout=null;
        owner.sourceTemplate=null; owner.board=null;
    }

    private static ReleaseSummary releaseAuthenticatedSource(Authenticated actual) {
        ReleaseSummary out=new ReleaseSummary();
        long started=System.nanoTime();
        Object capture=actual.capture;
        try {
            if(capture!=null) {
                setField(capture,"board",null); setField(capture,"template",null);
                setField(capture,"padLands",null); setField(capture,"padLandJson",null);
                setField(capture,"reached",null); setField(capture,"reachedPth",null);
                out.captureReleased=field(capture,"board")==null && field(capture,"template")==null &&
                    field(capture,"padLands")==null && field(capture,"reached")==null &&
                    field(capture,"reachedPth")==null;
            } else out.captureReleased=true;
            actual.capture=null; actual.arm=null; actual.board=null; actual.template=null;
            out.boardReleased=actual.board==null;
            out.templateReleased=actual.template==null;
        } catch(Exception failure) {
            out.captureReleased=false; out.boardReleased=false; out.templateReleased=false;
            out.failure=failure;
        } finally {
            actual.capture=null; actual.arm=null; actual.board=null; actual.template=null;
            out.boardReleased=actual.board==null;
            out.templateReleased=actual.template==null;
            capture=null;
        }
        out.elapsedNanos=elapsed(started,System.nanoTime());
        return out;
    }

    private static void runCanary(long processStarted) throws Exception {
        Meter setup=new Meter(processStarted,0);
        CopperFreeBranchFixtures.Fixture open=fixture("open",setup,true);
        MetricResult openResult=measure(open.board,open.layout,setup,null,true,processStarted);
        PairMetric openPair=findPair(openResult,NET);
        if(openPair==null || !openPair.paired || openResult.pairedNetCount!=1 ||
                openPair.padIds.size()!=2 || !"DA.A".equals(openPair.padIds.get(0)) ||
                !"QA.C".equals(openPair.padIds.get(1)) || !ROOT.equals(openPair.rootPadId) ||
                !START.equals(openPair.startPadId) || !"HIGH_CURRENT".equals(openPair.role) ||
                openPair.traceWidth!=13 ||
                (openPair.forwardBottomMask&(1<<2))==0 ||
                (openPair.reverseBottomMask&(1<<3))==0 || !openResult.coldReplayMatches ||
                !openResult.sourceUnchanged || !openResult.allQueryReferencesReleased)
            throw new IllegalStateException("OPEN metric canary lost its literal bottom-face witnesses");
        open=null;

        CopperFreeBranchFixtures.Fixture blocked=fixture("blocked",setup,false);
        MetricResult blockedResult=measure(blocked.board,blocked.layout,setup,null,false,processStarted);
        PairMetric blockedPair=findPair(blockedResult,NET);
        if(blockedPair==null || !blockedPair.paired || blockedResult.pairedNetCount!=1 ||
                blockedPair.padIds.size()!=2 || !"DA.A".equals(blockedPair.padIds.get(0)) ||
                !"QA.C".equals(blockedPair.padIds.get(1)) || !ROOT.equals(blockedPair.rootPadId) ||
                !START.equals(blockedPair.startPadId) || !"HIGH_CURRENT".equals(blockedPair.role) ||
                blockedPair.traceWidth!=13 || blockedPair.forwardTopMask!=0 || blockedPair.forwardBottomMask!=0 ||
                !blockedResult.sourceUnchanged || !blockedResult.allQueryReferencesReleased)
            throw new IllegalStateException("BLOCKED metric canary admitted a first-step exit");
        blocked=null;

        CopperFreeBranchFixtures.Fixture failureFixture=fixture("open-failure",setup,true);
        Fault fault=new Fault();
        Throwable caught=null;
        try { measure(failureFixture.board,failureFixture.layout,setup,fault,false,processStarted); }
        catch(Throwable failure) { caught=failure; }
        if(caught==null || caught!=fault.failure || !(caught instanceof IllegalStateException) ||
                !"CANARY_INJECTED_AFTER_FIRST_QUERY".equals(caught.getMessage()) ||
                caught.getSuppressed().length!=0 || fault.completedQueries!=1 ||
                !fault.routersInstalled || !fault.searchReleased || !fault.sessionReleased ||
                !fault.layoutReleased || !fault.routersReleased || !fault.allReferencesReleased)
            throw new IllegalStateException("after-query failure did not preserve exception/owner cleanup");
        failureFixture=null;

        long cleanup=openResult.cleanupNanos+blockedResult.cleanupNanos+fault.cleanupNanos;
        if(cleanup>CLEANUP_NANOS)
            throw new IOException("canary cleanup exceeded its frozen bound");
        setup.beginGroup("canary result preparation");
        Map<String,Object> result=object();
        result.put("schema","tsj-initial-outlet-canary-v1"); result.put("status","PASS");
        result.put("open",canaryMetric(openResult,openPair));
        result.put("blocked",canaryMetric(blockedResult,blockedPair));
        Map<String,Object> replay=object();
        replay.put("matches",Boolean.valueOf(openResult.coldReplayMatches));
        replay.put("callsPerScan",Long.valueOf(openResult.queryCount/2));
        replay.put("totalCalls",Long.valueOf(openResult.queryCount));
        replay.put("sourceGeometryUnchanged",Boolean.valueOf(openResult.sourceUnchanged));
        replay.put("cleanup",cleanupJson(openResult)); result.put("coldReplay",replay);
        Map<String,Object> failure=object();
        failure.put("exceptionClass",caught.getClass().getSimpleName());
        failure.put("exceptionMessage",caught.getMessage());
        failure.put("sameThrowable",Boolean.valueOf(caught==fault.failure));
        failure.put("suppressedCount",Integer.valueOf(caught.getSuppressed().length));
        failure.put("completedQueries",Integer.valueOf(fault.completedQueries));
        failure.put("routersInstalledBeforeThrow",Boolean.valueOf(fault.routersInstalled));
        failure.put("cleanup",faultCleanupJson(fault)); result.put("injectedFailure",failure);
        Map<String,Object> work=object();
        work.put("cutoff","PRE_MARKER_SERIALIZATION");
        work.put("elapsedNanos",Long.valueOf(0)); work.put("units",Long.valueOf(0));
        work.put("groups",Integer.valueOf(0)); work.put("maxGroupNanos",Long.valueOf(0));
        work.put("maximumUnitsPerGroup",Integer.valueOf(0));
        work.put("cleanupNanos",Long.valueOf(cleanup)); result.put("work",work);
        long preMarkerUnits=countJsonNodes(result);
        setup.charge(preMarkerUnits);
        setup.finishGroup();
        long elapsedWork=elapsed(processStarted,System.nanoTime())-cleanup;
        if(setup.groups>MAX_GROUPS || setup.maxGroupNanos>GROUP_NANOS ||
                setup.maximumUnitsPerGroup>STEP_LIMIT || elapsedWork<0 || elapsedWork>OP_NANOS)
            throw new IOException("canary work exceeded a frozen bound");
        work.put("elapsedNanos",Long.valueOf(elapsedWork));
        work.put("units",Long.valueOf(setup.units));
        work.put("groups",Integer.valueOf(setup.groups));
        work.put("maxGroupNanos",Long.valueOf(setup.maxGroupNanos));
        work.put("maximumUnitsPerGroup",Integer.valueOf(setup.maximumUnitsPerGroup));
        long markerStarted=System.nanoTime();
        String line=json(result)+"\n";
        byte[] bytes=utf8(line);
        if(bytes.length>FILE_LIMIT || elapsed(markerStarted,System.nanoTime())>GROUP_NANOS ||
                elapsed(processStarted,System.nanoTime())>OP_NANOS)
            throw new IOException("canary output or process exceeded a frozen bound");
        System.out.print(line);
    }

    private static CopperFreeBranchFixtures.Fixture fixture(String name,Meter meter,
            boolean open) throws Exception {
        meter.beginGroup("literal fixture "+name);
        CopperFreeBranchFixtures.Fixture fixture=open?
            CopperFreeBranchFixtures.open():CopperFreeBranchFixtures.blocked();
        meter.charge(1+fixture.board.getComponentIds().size()+fixture.layout.getPads().size());
        meter.finishGroup();
        return fixture;
    }

    private static PairMetric findPair(MetricResult result,String netId) {
        for(PairMetric row:result.rows) if(row.netId.equals(netId)) return row;
        return null;
    }

    private static Map<String,Object> canaryMetric(MetricResult result,PairMetric pair) {
        Map<String,Object> out=object();
        out.put("netId",pair.netId); out.put("padIds",new ArrayList<String>(pair.padIds));
        out.put("role",pair.role); out.put("traceWidth",Integer.valueOf(pair.traceWidth));
        out.put("rootPadId",pair.rootPadId); out.put("startPadId",pair.startPadId);
        out.put("forward",pair.orientationJson(true)); out.put("reverse",pair.orientationJson(false));
        out.put("queryCount",Long.valueOf(result.queryCount));
        out.put("primaryFace",result.primaryFace); out.put("oppositeFace",result.oppositeFace);
        out.put("sourceGeometryUnchanged",Boolean.valueOf(result.sourceUnchanged));
        out.put("cleanup",cleanupJson(result));
        return out;
    }

    private static Map<String,Object> cleanupJson(MetricResult result) {
        Map<String,Object> out=object();
        out.put("elapsedNanos",Long.valueOf(result.cleanupNanos));
        out.put("searchReleased",Boolean.valueOf(result.searchReleased));
        out.put("sessionReleased",Boolean.valueOf(result.sessionReleased));
        out.put("layoutReleased",Boolean.valueOf(result.layoutReleased));
        out.put("routersReleased",Boolean.valueOf(result.routersReleased));
        out.put("allReferencesReleased",Boolean.valueOf(result.allQueryReferencesReleased));
        return out;
    }

    private static Map<String,Object> faultCleanupJson(Fault fault) {
        Map<String,Object> out=object();
        out.put("elapsedNanos",Long.valueOf(fault.cleanupNanos));
        out.put("searchReleased",Boolean.valueOf(fault.searchReleased));
        out.put("sessionReleased",Boolean.valueOf(fault.sessionReleased));
        out.put("layoutReleased",Boolean.valueOf(fault.layoutReleased));
        out.put("routersReleased",Boolean.valueOf(fault.routersReleased));
        out.put("allReferencesReleased",Boolean.valueOf(fault.allReferencesReleased));
        return out;
    }

    private static void exportResult(Path data,Map<String,Object> result,long processStarted,
            int preExportGroups,long preExportMaxGroup,int preExportMaxUnits) throws Exception {
        long exportStart=System.nanoTime();
        validateEmptyExceptReceipt(data);
        byte[] bytes=utf8(json(result)+"\n");
        if(bytes.length<1 || bytes.length>FILE_LIMIT)
            throw new IOException("result.json exceeds the 8 MiB bound");
        Path resultPath=data.resolve(RESULT_FILE);
        try(OutputStream output=new BufferedOutputStream(Files.newOutputStream(resultPath,
                new OpenOption[] {StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE}))) {
            output.write(bytes); output.flush();
        }
        Digest actual=fileDigest(resultPath,FILE_LIMIT);
        if(actual.bytes!=bytes.length || !actual.sha.equals(sha256(bytes)))
            throw new IOException("result.json changed after close");
        validateExactFiles(data);
        int exportUnits=(bytes.length+COPY_CHUNK-1)/COPY_CHUNK+3;
        Map<String,Object> marker=object();
        marker.put("schema","tsj-initial-outlet-marker-v1");
        marker.put("status","COMPLETED_DIAGNOSTIC");
        marker.put("resultSha256",actual.sha); marker.put("resultBytes",Long.valueOf(actual.bytes));
        marker.put("exportElapsedNanos",Long.valueOf(0));
        marker.put("exportUnits",Integer.valueOf(exportUnits));
        marker.put("exportGroupCount",Integer.valueOf(1));
        marker.put("exportMaxGroupNanos",Long.valueOf(0));
        marker.put("exportMaximumUnitsPerGroup",Integer.valueOf(exportUnits));
        marker.put("nativeElapsedNanos",Long.valueOf(0));
        long exportElapsed=elapsed(exportStart,System.nanoTime());
        long totalElapsed=elapsed(processStarted,System.nanoTime());
        long maxGroup=Math.max(preExportMaxGroup,exportElapsed);
        int maxUnits=Math.max(preExportMaxUnits,exportUnits);
        if(exportElapsed>GROUP_NANOS || maxGroup>GROUP_NANOS || maxUnits>STEP_LIMIT ||
                preExportGroups+1>MAX_GROUPS || totalElapsed>OP_NANOS)
            throw new IOException("final serialization/export exceeded a frozen group or process bound");
        marker.put("exportElapsedNanos",Long.valueOf(exportElapsed));
        marker.put("exportMaxGroupNanos",Long.valueOf(exportElapsed));
        marker.put("nativeElapsedNanos",Long.valueOf(totalElapsed));
        String markerLine=json(marker);
        if(elapsed(exportStart,System.nanoTime())>GROUP_NANOS ||
                elapsed(processStarted,System.nanoTime())>OP_NANOS)
            throw new IOException("final marker preflight exceeded a frozen bound");
        System.out.println(markerLine);
    }

    private static void validateEmptyExceptReceipt(Path data) throws IOException {
        validateRealDirectory(data);
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(data)) {
            for(Path entry:entries) if(!OP1_FILE.equals(entry.getFileName().toString()))
                throw new IOException("native data directory contains an unexpected file");
        }
        if(!Files.isRegularFile(data.resolve(OP1_FILE),LinkOption.NOFOLLOW_LINKS))
            throw new IOException("fresh actual operation-1 receipt is missing");
    }

    private static void validateExactFiles(Path data) throws IOException {
        int count=0;
        try(DirectoryStream<Path> entries=Files.newDirectoryStream(data)) {
            for(Path entry:entries) {
                String name=entry.getFileName().toString();
                if(!OP1_FILE.equals(name) && !RESULT_FILE.equals(name))
                    throw new IOException("native data directory contains an unexpected file");
                count++;
            }
        }
        if(count!=2) throw new IOException("native output does not contain exactly two data files");
    }

    private static void validateRealDirectory(Path data) throws IOException {
        if(data==null || !data.isAbsolute() || !data.normalize().equals(data) ||
                !Files.isDirectory(data,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(data) ||
                !data.toRealPath().equals(data.toAbsolutePath().normalize()))
            throw new IOException("data directory stopped being an existing real directory");
    }

    private static long countJsonNodes(Object value) throws IOException {
        if(value instanceof Map) {
            long count=0;
            for(Object entryObject:((Map<?,?>)value).entrySet()) {
                Map.Entry<?,?> entry=(Map.Entry<?,?>)entryObject;
                count=add(count,1+countJsonNodes(entry.getValue()));
            }
            if(count>MAX_COPY_ENTRIES) throw new IOException("result object exceeds the copy-entry bound");
            return count;
        }
        if(value instanceof List) {
            long count=0;
            for(Object item:(List<?>)value) count=add(count,1+countJsonNodes(item));
            if(count>MAX_COPY_ENTRIES) throw new IOException("result array exceeds the copy-entry bound");
            return count;
        }
        return 1;
    }

    private static long copyEntryCount(PcbBoardLayout layout) throws IOException {
        if(layout==null) throw new IOException("missing layout for bounded template copy");
        return add(add(layout.getPads().size(),layout.getComponents().size()),
            add(layout.getSilkscreenLabels().size(),
                add(layout.getRegions().size(),layout.getHoles().size())));
    }

    private static void requireOneCopyChunk(long entries,String label) throws IOException {
        if(entries<0 || entries>MAX_COPY_ENTRIES || entries>COPY_CHUNK)
            throw new IOException(label+" exceeds the single-chunk diagnostic copy bound");
    }

    private static int hashUnits(int bytes) throws IOException {
        if(bytes<0 || bytes>FILE_LIMIT) throw new IOException("hash input exceeds its byte bound");
        return (bytes+COPY_CHUNK-1)/COPY_CHUNK;
    }

    private static String json(Object value) throws IOException {
        StringBuilder out=new StringBuilder(); appendJson(out,value); return out.toString();
    }

    private static void appendJson(StringBuilder out,Object value) throws IOException {
        if(value==null) { out.append("null"); return; }
        if(value instanceof String) { quote(out,(String)value); return; }
        if(value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value.toString()); return;
        }
        if(value instanceof Map) {
            out.append('{'); boolean first=true;
            for(Object entryObject:((Map<?,?>)value).entrySet()) {
                Map.Entry<?,?> entry=(Map.Entry<?,?>)entryObject;
                if(!(entry.getKey() instanceof String)) throw new IOException("JSON object key is not a string");
                if(!first) out.append(','); first=false;
                quote(out,(String)entry.getKey()); out.append(':'); appendJson(out,entry.getValue());
            }
            out.append('}'); return;
        }
        if(value instanceof List) {
            out.append('['); boolean first=true;
            for(Object item:(List<?>)value) { if(!first) out.append(','); first=false; appendJson(out,item); }
            out.append(']'); return;
        }
        throw new IOException("unsupported JSON value type: "+value.getClass().getName());
    }

    private static void quote(StringBuilder out,String value) {
        out.append('"');
        for(int index=0;index<value.length();index++) {
            char ch=value.charAt(index);
            if(ch=='"' || ch=='\\') { out.append('\\').append(ch); }
            else if(ch=='\b') out.append("\\b");
            else if(ch=='\f') out.append("\\f");
            else if(ch=='\n') out.append("\\n");
            else if(ch=='\r') out.append("\\r");
            else if(ch=='\t') out.append("\\t");
            else if(ch<0x20) {
                String hex=Integer.toHexString(ch);
                out.append("\\u");
                for(int pad=hex.length();pad<4;pad++) out.append('0');
                out.append(hex);
            } else out.append(ch);
        }
        out.append('"');
    }

    private static List<Object> strings(String[] values) {
        ArrayList<Object> out=new ArrayList<Object>();
        if(values!=null) for(String value:values) out.add(value);
        return out;
    }

    private static Map<String,Object> object() { return new LinkedHashMap<String,Object>(); }

    private static byte[] utf8(String value) { return value.getBytes(UTF8); }

    private static String hashFingerprint(PcbBoardLayout layout) throws IOException {
        return sha256(utf8(layout.geometryFingerprint()));
    }

    private static String hashString(String value) throws IOException { return sha256(utf8(value)); }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result=new StringBuilder(64);
            for(byte value:digest) {
                int current=value&255;
                if(current<16) result.append('0');
                result.append(Integer.toHexString(current));
            }
            return result.toString();
        } catch(NoSuchAlgorithmException impossible) { throw new IOException("SHA-256 is unavailable",impossible); }
    }

    private static Digest fileDigest(Path path,int maximum) throws IOException {
        long size=Files.size(path);
        if(size<1 || size>maximum) throw new IOException("file exceeds its byte bound");
        byte[] bytes=Files.readAllBytes(path);
        return new Digest(bytes.length,sha256(bytes));
    }

    private static void requireSha(String value,String label) {
        if(value==null || !SHA.matcher(value).matches()) throw new IllegalArgumentException("invalid "+label);
    }

    private static void checkOperationClock(long started) throws IOException {
        if(elapsed(started,System.nanoTime())>OP_NANOS)
            throw new IOException("native operation exceeded the 90-second bound");
    }

    private static long elapsed(long start,long end) {
        long value=end-start; return value<0?Long.MAX_VALUE:value;
    }

    private static long add(long first,long second) throws IOException {
        if(first<0 || second<0 || first>Long.MAX_VALUE-second) throw new IOException("work counter overflow");
        return first+second;
    }

    private static Object invoke(Method method,Object target,Object... args) throws Exception {
        try { return method.invoke(target,args); }
        catch(InvocationTargetException wrapped) { rethrow(wrapped.getCause()); return null; }
    }

    private static void rethrow(Throwable failure) throws Exception {
        if(failure instanceof Exception) throw (Exception)failure;
        if(failure instanceof Error) throw (Error)failure;
        throw new RuntimeException(failure);
    }

    private static Object field(Object target,String name) throws Exception {
        if(target==null) throw new IllegalStateException("missing owner for field "+name);
        Class<?> type=target.getClass();
        while(type!=null) {
            try {
                Field value=type.getDeclaredField(name); value.setAccessible(true); return value.get(target);
            } catch(NoSuchFieldException absent) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(target.getClass().getName()+"."+name);
    }

    private static void setField(Object target,String name,Object value) throws Exception {
        if(target==null) throw new IllegalStateException("missing owner while releasing "+name);
        Class<?> type=target.getClass();
        while(type!=null) {
            try {
                Field field=type.getDeclaredField(name); field.setAccessible(true); field.set(target,value); return;
            } catch(NoSuchFieldException absent) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(target.getClass().getName()+"."+name);
    }

    private static void setIntField(Object target,String name,int value) throws Exception {
        if(target==null) throw new IllegalStateException("missing owner while setting "+name);
        Class<?> type=target.getClass();
        while(type!=null) {
            try {
                Field field=type.getDeclaredField(name); field.setAccessible(true); field.setInt(target,value); return;
            } catch(NoSuchFieldException absent) { type=type.getSuperclass(); }
        }
        throw new NoSuchFieldException(target.getClass().getName()+"."+name);
    }

    private static String stringField(Object target,String name) throws Exception {
        Object value=field(target,name);
        if(!(value instanceof String)) throw new IllegalStateException("missing string field "+name);
        return (String)value;
    }

    private static int intField(Object target,String name) throws Exception {
        Object value=field(target,name);
        if(!(value instanceof Number)) throw new IllegalStateException("missing integer field "+name);
        return ((Number)value).intValue();
    }

    private static long longField(Object target,String name) throws Exception {
        Object value=field(target,name);
        if(!(value instanceof Number)) throw new IllegalStateException("missing long field "+name);
        return ((Number)value).longValue();
    }

    private static boolean booleanField(Object target,String name) throws Exception {
        Object value=field(target,name);
        if(!(value instanceof Boolean)) throw new IllegalStateException("missing boolean field "+name);
        return ((Boolean)value).booleanValue();
    }

    private static final class Meter {
        final long processStarted;
        int priorGroups;
        long units,maxGroupNanos,groupStarted;
        int groups,groupUnits,maximumUnitsPerGroup;
        boolean open;
        Meter(long started,int prior) { processStarted=started; priorGroups=prior; }
        void beginGroup(String label) throws IOException {
            if(open) throw new IOException("nested work groups are not permitted");
            if(priorGroups+groups+1>MAX_GROUPS) throw new IOException("native work exceeded 640 groups");
            open=true; groups++; groupUnits=0; groupStarted=System.nanoTime();
        }
        void charge(long count) throws IOException {
            if(!open || count<0 || count>STEP_LIMIT || groupUnits>STEP_LIMIT-count)
                throw new IOException("work unit group exceeded 32768 units");
            units=add(units,count); groupUnits+=(int)count;
        }
        void chargeAndCheck(long count,long started) throws IOException {
            charge(count); checkOperationClock(started);
        }
        void finishGroup() throws IOException {
            if(!open) throw new IOException("work group is not open");
            long nanos=elapsed(groupStarted,System.nanoTime()); open=false;
            if(nanos>GROUP_NANOS) throw new IOException("one native work group exceeded five seconds");
            if(nanos>maxGroupNanos) maxGroupNanos=nanos;
            if(groupUnits>maximumUnitsPerGroup) maximumUnitsPerGroup=groupUnits;
        }
    }

    private static final class GroupSummary {
        long diagnosticUnits,p07OwnerStepsSum;
        int groupCount,maximumUnitsPerGroup;
    }

    private static final class Authenticated {
        Object arm,capture;
        TroubleshootBoard board;
        PcbBoardLayout template;
    }

    private static final class QueryOwner {
        TroubleshootBoard board;
        PcbBoardLayout sourceTemplate,layout;
        PcbLayerRoutingPrototype.Session session;
        Object search;
        Object[] faces;
    }

    private static final class ReleaseSummary {
        long elapsedNanos;
        boolean captureReleased,boardReleased,templateReleased;
        Throwable failure;
    }

    private static final class MetricResult {
        final ArrayList<PairMetric> rows=new ArrayList<PairMetric>();
        int singletonNetCount,pairedNetCount;
        long queryCount,cleanupNanos,metricElapsedNanos,metricUnits,metricMaxGroupNanos;
        int metricGroups,metricMaximumUnitsPerGroup;
        String sourceGeometryBefore,sourceGeometryAfter,queryEffectsBefore,queryEffectsAfter;
        String primaryFace,oppositeFace;
        boolean sourceUnchanged,coldReplayPerformed,coldReplayMatches;
        boolean searchReleased,sessionReleased,layoutReleased,routersReleased,allQueryReferencesReleased;
    }

    private static final class PairMetric {
        String netId,rootPadId,startPadId,role;
        int traceWidth;
        ArrayList<String> padIds;
        boolean paired;
        int forwardTopMask,forwardBottomMask,reverseTopMask,reverseBottomMask;
        int forwardTopCount,forwardBottomCount,reverseTopCount,reverseBottomCount;
        int forwardTotal,reverseTotal,value;
        void copyMasks(PairMetric other) {
            forwardTopMask=other.forwardTopMask; forwardBottomMask=other.forwardBottomMask;
            reverseTopMask=other.reverseTopMask; reverseBottomMask=other.reverseBottomMask;
            forwardTopCount=other.forwardTopCount; forwardBottomCount=other.forwardBottomCount;
            reverseTopCount=other.reverseTopCount; reverseBottomCount=other.reverseBottomCount;
            forwardTotal=other.forwardTotal; reverseTotal=other.reverseTotal; value=other.value;
        }
        boolean sameMasks(PairMetric other) {
            return other!=null && netId.equals(other.netId) && rootPadId.equals(other.rootPadId) &&
                startPadId.equals(other.startPadId) && forwardTopMask==other.forwardTopMask &&
                forwardBottomMask==other.forwardBottomMask && reverseTopMask==other.reverseTopMask &&
                reverseBottomMask==other.reverseBottomMask;
        }
        Map<String,Object> json() {
            Map<String,Object> out=object();
            out.put("netId",netId); out.put("padIds",new ArrayList<String>(padIds));
            out.put("role",role); out.put("traceWidth",Integer.valueOf(traceWidth));
            out.put("paired",Boolean.valueOf(paired));
            out.put("rootPadId",rootPadId); out.put("startPadId",startPadId);
            if(paired) {
                out.put("forward",orientationJson(true)); out.put("reverse",orientationJson(false));
                out.put("value",Integer.valueOf(value));
            } else {
                out.put("forward",null); out.put("reverse",null); out.put("value",null);
            }
            return out;
        }
        Map<String,Object> orientationJson(boolean forward) {
            Map<String,Object> out=object();
            int top=forward?forwardTopMask:reverseTopMask;
            int bottom=forward?forwardBottomMask:reverseBottomMask;
            int topCount=forward?forwardTopCount:reverseTopCount;
            int bottomCount=forward?forwardBottomCount:reverseBottomCount;
            int total=forward?forwardTotal:reverseTotal;
            out.put("topMask",Integer.valueOf(top)); out.put("bottomMask",Integer.valueOf(bottom));
            out.put("topCount",Integer.valueOf(topCount)); out.put("bottomCount",Integer.valueOf(bottomCount));
            out.put("totalCount",Integer.valueOf(total));
            return out;
        }
    }

    private static final class Fault {
        Throwable failure;
        int completedQueries,groups,maximumUnitsPerGroup;
        long units,maxGroupNanos,cleanupNanos,nativeElapsedNanos;
        boolean routersInstalled,searchReleased,sessionReleased,layoutReleased,routersReleased,allReferencesReleased;
    }

    private static final class Digest {
        final long bytes;
        final String sha;
        Digest(long bytes,String sha) { this.bytes=bytes; this.sha=sha; }
    }
}
