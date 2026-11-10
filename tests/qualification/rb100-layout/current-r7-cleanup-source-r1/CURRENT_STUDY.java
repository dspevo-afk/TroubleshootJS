package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Native-only R2 integration. Explicit canary mode and one-shot authored producer. */
public final class CohortTerminalTelemetryStudy {
    private static final Pattern UUID = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA = Pattern.compile("[0-9a-f]{64}");
    private static final String PARENT =
        "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private CohortTerminalTelemetryStudy() { }

    public static void main(String[] args) throws Exception {
        throw new IllegalStateException("ADOPTED_SNAPSHOT_COMPONENT_ONLY");
    }

private static boolean r7CanonicalWindowsOutput(String value) {
    if (value==null || value.length()==0 || java.io.File.separatorChar!='\\' ||
            value.indexOf('/')>=0 || value.startsWith("\\\\?\\") ||
            value.startsWith("\\\\.\\")) return false;
    boolean drive=value.length()>=3 &&
        ((value.charAt(0)>='A' && value.charAt(0)<='Z') ||
         (value.charAt(0)>='a' && value.charAt(0)<='z')) &&
        value.charAt(1)==':' && value.charAt(2)=='\\';
    boolean unc=value.startsWith("\\\\");
    if (!drive && !unc) return false;
    String tail=drive?value.substring(3):value.substring(2);
    if (drive && tail.length()==0) return false;
    String[] components=tail.split("\\\\",-1);
    if (unc && components.length<2) return false;
    for (int i=0;i<components.length;i++) {
        String component=components[i];
        if (component.length()==0 || ".".equals(component) || "..".equals(component) ||
                component.indexOf(':')>=0 || component.endsWith(".") ||
                component.endsWith(" ")) return false;
    }
    return true;
}
static final class GenericR7Arguments {
    static final String MODE="GENERIC_ROUTING_R7_REFERENCE_V1";
    static final String WIRE="4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741";
    static final String ORACLE="5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a";
    final String runId,manifestSha,wireSha,fixtureOracleSha;
    final java.nio.file.Path outputDirectory;
    private GenericR7Arguments(String run,String manifest,String wire,String oracle,
            java.nio.file.Path output) {
        runId=run; manifestSha=manifest; wireSha=wire; fixtureOracleSha=oracle;
        outputDirectory=output;
    }
    static GenericR7Arguments parse(String[] args) throws Exception {
        if (args==null || args.length!=6 || !MODE.equals(args[0]) ||
                args[1]==null || !UUID.matcher(args[1]).matches() ||
                args[2]==null || !SHA.matcher(args[2]).matches() ||
                !WIRE.equals(args[3]) || !ORACLE.equals(args[4]) ||
                args[5]==null || args[5].indexOf('\0')>=0)
            throw new IOException("GENERIC_R7_ARGUMENTS");
        try {
            String rawOutput=args[5];
            if (!r7CanonicalWindowsOutput(rawOutput))
                throw new IOException("GENERIC_R7_OUTPUT_PATH");
            java.nio.file.Path output=Paths.get(rawOutput);
            if (!output.isAbsolute() || !rawOutput.equals(output.toString()) ||
                    !rawOutput.equals(output.normalize().toString()) ||
                    !Files.isDirectory(output,LinkOption.NOFOLLOW_LINKS) ||
                    Files.isSymbolicLink(output) ||
                    !rawOutput.equals(output.toRealPath().toString()))
                throw new IOException("GENERIC_R7_OUTPUT_PATH");
            for (java.nio.file.Path part=output;part!=null;part=part.getParent())
                new R5FileIdentity(part,true);
            try (DirectoryStream<java.nio.file.Path> entries=Files.newDirectoryStream(output)) {
                if (entries.iterator().hasNext()) throw new IOException("GENERIC_R7_OUTPUT_NOT_EMPTY");
            }
            return new GenericR7Arguments(args[1],args[2],args[3],args[4],output);
        } catch (IOException failure) { throw failure; }
        catch (RuntimeException failure) { throw new IOException("GENERIC_R7_OUTPUT_PATH",failure); }
    }
}
    static final class Arguments {
        final String runId, manifestSha, wireSha, descriptorSha;
        final Path data;
        private Arguments(String runId, String manifestSha, String wireSha,
                String descriptorSha, Path data) {
            this.runId=runId; this.manifestSha=manifestSha; this.wireSha=wireSha;
            this.descriptorSha=descriptorSha; this.data=data;
        }
        static Arguments parse(String[] args) throws IOException {
            if (args==null || args.length!=6 ||
                    (!"COHORT_TERMINAL_TELEMETRY".equals(args[0]) &&
                     !"COHORT_TERMINAL_CANARIES".equals(args[0])))
                throw new IOException("ARGS");
            if (args[1]==null || !UUID.matcher(args[1]).matches()) throw new IOException("UUID");
            for (int i=2;i<=4;i++)
                if (args[i]==null || !SHA.matcher(args[i]).matches()) throw new IOException("SHA");
            try {
                Path path=Paths.get(args[5]);
                if (!path.isAbsolute() || !path.normalize().equals(path) ||
                        !Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS) ||
                        Files.isSymbolicLink(path) ||
                        !path.toRealPath().equals(path.toAbsolutePath().normalize()))
                    throw new IOException("DATA_PATH");
                try (DirectoryStream<Path> entries=Files.newDirectoryStream(path)) {
                    if (entries.iterator().hasNext()) throw new IOException("DATA_NONEMPTY");
                }
                return new Arguments(args[1],args[2],args[3],args[4],path);
            } catch (RuntimeException invalid) { throw new IOException("DATA_PATH",invalid); }
        }
    }

    /** Invoke pinned private kernels without swallowing the owner's original exception. */
    static final class Parent {
        final Class<?> owner, inputsType, meterType, originalType, captureType;
        Parent() throws Exception {
            owner=Class.forName(PARENT);
            inputsType=Class.forName(PARENT+"$Inputs");
            meterType=Class.forName(PARENT+"$Meter");
            originalType=Class.forName(PARENT+"$OriginalArm");
            captureType=Class.forName(PARENT+"$CaptureData");
        }
        Object inputs(Arguments args) throws Exception {
            Constructor<?> c=inputsType.getDeclaredConstructor(String.class,String.class,
                String.class,Path.class);
            c.setAccessible(true);
            return c.newInstance(args.runId,args.manifestSha,args.wireSha,args.data);
        }
        Object meter(long started) throws Exception {
            Constructor<?> c=meterType.getDeclaredConstructor(long.class);
            c.setAccessible(true); return c.newInstance(Long.valueOf(started));
        }
        Object original(Object inputs,Object meter) throws Exception {
            return call("captureOriginal",new Class<?>[]{inputsType,meterType},inputs,meter);
        }
        Object fresh(TroubleshootBoard board,PcbBoardLayout layout,Object capture,
                List<String> cohort,Object meter,long started,
                PcbLayerRoutingPrototype.Diagnostics diagnostics) throws Exception {
            return call("runFresh",new Class<?>[]{TroubleshootBoard.class,PcbBoardLayout.class,
                captureType,List.class,String.class,meterType,long.class,
                PcbLayerRoutingPrototype.Diagnostics.class},board,layout,capture,cohort,
                "OBSERVED_FAILURE_COHORT_TRIAL",meter,Long.valueOf(started),diagnostics);
        }
        Object call(String name,Class<?>[] types,Object... args) throws Exception {
            Method m=owner.getDeclaredMethod(name,types); m.setAccessible(true);
            return invoke(m,null,args);
        }
    }

    static Object invoke(Method method,Object receiver,Object... args) throws Exception {
        try { return method.invoke(receiver,args); }
        catch (InvocationTargetException wrapped) {
            Throwable cause=wrapped.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw new IllegalStateException(cause);
        }
    }
    static Object field(Object object,String name) throws Exception {
        Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }

    /** Shared private N meter; all operations occur outside an owner call. */
    static final class SetupMeter {
        final Parent parent;
        final Object meter;
        SetupMeter(Parent parent,Object meter) { this.parent=parent; this.meter=meter; }
        void charge(long n) throws Exception {
            method("reserve",new Class<?>[]{long.class},Long.valueOf(n));
            method("charge",new Class<?>[]{long.class},Long.valueOf(n));
        }
        void copy(int n) throws Exception {
            Class<?> fresh=Class.forName(PARENT+"$FreshRun");
            parent.call("reserveCopy",new Class<?>[]{fresh,parent.meterType,int.class},
                null,meter,Integer.valueOf(n));
        }
        void finish() throws Exception {
            if (Boolean.TRUE.equals(field(meter,"open"))) method("finishGroup",new Class<?>[0]);
        }
        Object method(String name,Class<?>[] types,Object... args) throws Exception {
            Method m=parent.meterType.getDeclaredMethod(name,types); m.setAccessible(true);
            return invoke(m,meter,args);
        }
    }
    /** Admit one immediately following explicit byte copy in the open export group. */
    static void exportCopyBlock(SetupMeter meter,int bytes) throws Exception {
        if (bytes<1 || bytes>4096 || !Boolean.TRUE.equals(field(meter.meter,"open")))
            throw new IOException("EXPORT_COPY_STATE");
        meter.method("check",new Class<?>[0]);
        long allowance=((Number)field(meter.meter,"copyAllowance")).longValue();
        long copied=((Number)field(meter.meter,"copiedEntries")).longValue();
        int chunks=((Number)field(meter.meter,"copyChunks")).intValue();
        int groupUnits=((Number)field(meter.meter,"groupUnits")).intValue();
        if (Boolean.TRUE.equals(field(meter.meter,"originalOwnerActive")) ||
                ((Number)field(meter.meter,"inFlightOwnerGroups")).intValue()!=0 ||
                allowance<0 || allowance>1048576L || copied<0 || copied>allowance ||
                bytes>allowance-copied || chunks<0 || chunks==Integer.MAX_VALUE ||
                groupUnits<0 || groupUnits>=32768)
            throw new IOException("EXPORT_COPY_CAPACITY");
        // This is the existing per-block export fee, not a per-byte charge.
        // Do not reserve here: reservation could silently roll the export group.
        meter.method("charge",new Class<?>[]{long.class},Long.valueOf(1));
        setNumber(meter.meter,"copiedEntries",Long.valueOf(copied+bytes));
        setNumber(meter.meter,"copyChunks",Integer.valueOf(chunks+1));
    }
    static final class SetupResult {
        final Map<String,Object> data;
        final long units, copiedEntries;
        final int groups, copyChunks;
        SetupResult(Map<String,Object> data,long units,long copiedEntries,int groups,int copyChunks) {
            this.data=data; this.units=units; this.copiedEntries=copiedEntries;
            this.groups=groups; this.copyChunks=copyChunks;
        }
    }
    @SuppressWarnings("unchecked")
    static SetupResult readInput(SetupMeter m,String expectedDescriptorSha) throws Exception {
        m.charge(1);
        if (!expectedDescriptorSha.equals(CohortTerminalTelemetryInput.descriptorSha256()))
            throw new IOException("DESCRIPTOR_SHA");
        m.charge(1);
        int entries=CohortTerminalTelemetryInput.dataEntries();
        m.charge(1);
        if (entries<1 || entries>4096) throw new IOException("INPUT_ENTRIES");
        m.copy(entries); // exact precharge before generated tree allocation
        Map<String,Object> data=CohortTerminalTelemetryInput.data();
        // The validator has at most one visit per slot plus bounded key/value text.
        // Count actual inspected values and characters before each inspection.
        m.charge(2); // validator bookkeeping, not generated data copies
        int count=countMetered(data,java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<Object,Boolean>()),0,m);
        m.charge(1); if (count!=entries) throw new IOException("INPUT_SHAPE");
        m.charge(1); if (data.size()!=5) throw new IOException("INPUT_SHAPE");
        m.charge(5); String[] required={"qualifiedPrior","capture","ownerSettings","seedValues","sourceIdentity"};
        for (String key:required) {
            m.charge(1); if (!data.containsKey(key)) throw new IOException("INPUT_SHAPE");
        }
        m.finish();
        return new SetupResult(data,((Number)field(m.meter,"units")).longValue(),
            ((Number)field(m.meter,"copiedEntries")).longValue(),
            ((Number)field(m.meter,"groups")).intValue(),
            ((Number)field(m.meter,"copyChunks")).intValue());
    }
    static int countMetered(Object value,Set<Object> seen,int depth,SetupMeter m) throws Exception {
        m.charge(1);
        if (depth>32) throw new IOException("INPUT_DEPTH");
        if (value instanceof Map) {
            m.charge(1); m.charge(1);
            if (!seen.add(value)) throw new IOException("INPUT_ALIAS");
            int n=1;
            for (Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) {
                m.charge(1);
                if (!(e.getKey() instanceof String)) throw new IOException("INPUT_KEY");
                meteredAscii((String)e.getKey(),m);
                n=add(n,1); n=add(n,countMetered(e.getValue(),seen,depth+1,m));
            }
            return n;
        }
        if (value instanceof List) {
            m.charge(1); m.charge(1);
            if (!seen.add(value)) throw new IOException("INPUT_ALIAS");
            int n=1;
            for (Object item:(List<?>)value) {
                m.charge(1); n=add(n,1); n=add(n,countMetered(item,seen,depth+1,m));
            }
            return n;
        }
        if (value instanceof String) meteredAscii((String)value,m);
        else if (value!=null && !(value instanceof Integer) && !(value instanceof Long) &&
                !(value instanceof Boolean)) throw new IOException("INPUT_VALUE");
        return 0;
    }
    static void meteredAscii(String value,SetupMeter m) throws Exception {
        m.charge(1);
        if (value.length()>256) throw new IOException("INPUT_STRING_LENGTH");
        for (int i=0;i<value.length();i++) {
            m.charge(1);
            if (value.charAt(i)<32 || value.charAt(i)>126) throw new IOException("INPUT_ASCII");
        }
    }
    private static final int COPY_LIMIT=1048576;
    static int beginCaptureAllowance(SetupMeter meter,SetupResult setup) throws Exception {
        if (Boolean.TRUE.equals(field(meter.meter,"open")) ||
                ((Number)field(meter.meter,"priorGroups")).intValue()!=0 ||
                ((Number)field(meter.meter,"inFlightOwnerGroups")).intValue()!=0 ||
                Boolean.TRUE.equals(field(meter.meter,"originalOwnerStarted")) ||
                Boolean.TRUE.equals(field(meter.meter,"originalOwnerActive")) ||
                Boolean.TRUE.equals(field(meter.meter,"originalOwnerAdopted")) ||
                ((Number)field(meter.meter,"copyAllowance")).intValue()!=COPY_LIMIT ||
                ((Number)field(meter.meter,"groups")).intValue()!=setup.groups ||
                ((Number)field(meter.meter,"units")).longValue()!=setup.units ||
                ((Number)field(meter.meter,"copiedEntries")).longValue()!=setup.copiedEntries ||
                ((Number)field(meter.meter,"copyChunks")).intValue()!=setup.copyChunks ||
                setup.copiedEntries<1 || setup.copiedEntries>COPY_LIMIT || setup.copyChunks<0)
            throw new IOException("SETUP_LEDGER_CHANGED");
        int remaining=(int)(COPY_LIMIT-setup.copiedEntries);
        // The setup baseline remains in SetupResult while capture uses local counters.
        // Any failure after this point discards this meter without unsafe restoration.
        setNumber(meter.meter,"copiedEntries",Long.valueOf(0));
        setNumber(meter.meter,"copyChunks",Integer.valueOf(0));
        meter.method("setCopyAllowance",new Class<?>[]{int.class},Integer.valueOf(remaining));
        if (((Number)field(meter.meter,"copyAllowance")).intValue()!=remaining)
            throw new IOException("ORIGINAL_ALLOWANCE");
        return remaining;
    }
    static void mergeCaptureAllowance(SetupMeter meter,SetupResult setup,int remaining,
            long originalCopies,int originalChunks,int originalGroups,int captureAllowance) throws Exception {
        if (Boolean.TRUE.equals(field(meter.meter,"open")) ||
                !Boolean.TRUE.equals(field(meter.meter,"originalOwnerStarted")) ||
                Boolean.TRUE.equals(field(meter.meter,"originalOwnerActive")) ||
                !Boolean.TRUE.equals(field(meter.meter,"originalOwnerAdopted")) ||
                ((Number)field(meter.meter,"inFlightOwnerGroups")).intValue()!=0 ||
                ((Number)field(meter.meter,"priorGroups")).intValue()!=originalGroups ||
                remaining<0 || remaining>COPY_LIMIT ||
                setup.copiedEntries!=COPY_LIMIT-(long)remaining ||
                ((Number)field(meter.meter,"copyAllowance")).intValue()!=remaining ||
                captureAllowance!=remaining ||
                ((Number)field(meter.meter,"copiedEntries")).longValue()!=originalCopies ||
                ((Number)field(meter.meter,"copyChunks")).intValue()!=originalChunks ||
                originalCopies<0 || originalCopies>remaining || originalChunks<0 || originalGroups<1 ||
                ((Number)field(meter.meter,"units")).longValue()<setup.units ||
                ((Number)field(meter.meter,"groups")).intValue()<setup.groups ||
                ((Number)field(meter.meter,"groups")).intValue()<0 ||
                (long)((Number)field(meter.meter,"groups")).intValue()+(long)originalGroups>640L ||
                setup.copiedEntries<1 || setup.copiedEntries>COPY_LIMIT-originalCopies ||
                setup.copyChunks<0 || setup.copyChunks>Integer.MAX_VALUE-originalChunks)
            throw new IOException("ORIGINAL_LEDGER");
        setNumber(meter.meter,"copiedEntries",Long.valueOf(setup.copiedEntries+originalCopies));
        setNumber(meter.meter,"copyChunks",Integer.valueOf(setup.copyChunks+originalChunks));
        meter.method("setCopyAllowance",new Class<?>[]{int.class},Integer.valueOf(COPY_LIMIT));
    }
    static Object captureWithSetup(Parent parent,Object inputs,SetupMeter meter,
            SetupResult setup) throws Exception {
        int remaining=beginCaptureAllowance(meter,setup);
        Object original=parent.original(inputs,meter.meter);
        Object capture=field(original,"captureData");
        mergeCaptureAllowance(meter,setup,remaining,
            ((Number)field(original,"copiedEntries")).longValue(),
            ((Number)field(original,"copyChunks")).intValue(),
            ((Number)field(original,"groupCount")).intValue(),
            ((Number)field(capture,"copyAllowance")).intValue());
        return original;
    }
    static void setNumber(Object object,String name,Number value) throws Exception {
        Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object,value);
    }

    static Map<String,Object> nullObservation(SetupMeter m) throws Exception {
        m.copy(18);
        Map<String,Object> out=new java.util.LinkedHashMap<String,Object>();
        out.put("schema","tsj-cohort-terminal-observation-v2");
        out.put("mode","NULL"); out.put("state","NOT_ENABLED");
        out.put("callbackCount",Integer.valueOf(0));
        out.put("foundCallbackCount",Integer.valueOf(0));
        out.put("nonFoundCallbackCount",Integer.valueOf(0));
        out.put("firstNetOrdinal",null); out.put("lastNetOrdinal",null);
        out.put("lastCallbackTotalExpanded",null); out.put("selectedCallbackOrdinal",null);
        out.put("transientTupleCopies",Integer.valueOf(0));
        out.put("transientFieldCopies",Integer.valueOf(0));
        out.put("validationCount",Integer.valueOf(0));
        out.put("observerUnits",Long.valueOf(0));
        out.put("copiedEntries",Long.valueOf(0)); out.put("copyChunks",Integer.valueOf(0));
        out.put("selectedSnapshot",null); out.put("selectedHead",null);
        return out;
    }

    /** Shared metadata owner; callers seal the observer before materialization. */
    static Map<String,Object> materializeObservation(TerminalSink sink,SetupMeter m)
            throws Exception {
        if (sink==null) return nullObservation(m);
        m.copy(18);
        m.charge(1); // selected-reference inspection, outside observer units
        if (field(sink.observer,"selected")!=null) m.copy(33);
        m.charge(1); // head-reference inspection, outside observer units
        if (field(sink.observer,"selectedHead")!=null) m.copy(10);
        m.charge(1); // sealed result-map operation, outside observer units
        Map<String,Object> observerResult=sink.observer.resultMap();
        m.copy(18);
        m.charge(1); // mutable outer-map construction, outside observer units
        return new java.util.LinkedHashMap<String,Object>(observerResult);
    }

    /** Adapter for the observer's direct, non-reserving in-owner meter port. */
    static final class ObserverMeter implements TerminalObserverMeter {
        final SetupMeter delegate;
        boolean insideOwner;
        int entriesRemaining;
        ObserverMeter(SetupMeter delegate) { this.delegate=delegate; }
        private RuntimeException failure(Exception e) { return new IllegalStateException("OBSERVER_METER",e); }
        public boolean isOpen() {
            try { return Boolean.TRUE.equals(field(delegate.meter,"open")); }
            catch (Exception e) { throw failure(e); }
        }
        public long units() { return number("units"); }
        public long groupUnits() { return number("groupUnits"); }
        public long copiedEntries() { return number("copiedEntries"); }
        public long copyChunks() { return number("copyChunks"); }
        private long number(String name) {
            try { return ((Number)field(delegate.meter,name)).longValue(); }
            catch (Exception e) { throw failure(e); }
        }
        public void charge(long units) {
            try {
                if (insideOwner) delegate.method("charge",new Class<?>[]{long.class},Long.valueOf(units));
                else delegate.charge(units);
            } catch (Exception e) { throw failure(e); }
        }
        public void beginCopyChunk(int entries) {
            try {
                if (!isOpen() || entriesRemaining!=0 || entries<1 || entries>4096 ||
                        copiedEntries()>1048576L-entries || copyChunks()>=Integer.MAX_VALUE)
                    throw new IllegalStateException("OBSERVER_COPY_CHUNK");
                setNumber(delegate.meter,"copyChunks",Integer.valueOf((int)copyChunks()+1));
                entriesRemaining=entries;
            } catch (Exception e) { throw failure(e); }
        }
        public void copyEntry() {
            try {
                if (!isOpen() || entriesRemaining<1 || copiedEntries()>=1048576L)
                    throw new IllegalStateException("OBSERVER_COPY_ENTRY");
                setNumber(delegate.meter,"copiedEntries",Long.valueOf(copiedEntries()+1));
                entriesRemaining--;
            } catch (Exception e) { throw failure(e); }
        }
    }
    static final class TerminalSink implements PcbLayerRoutingPrototype.BranchLimitSelectionDiagnostics {
        final ObserverMeter meter;
        final TerminalTelemetryObserver observer;
        final String requestId;
        TerminalSink(ObserverMeter meter,TerminalTelemetryObserver observer,String requestId) {
            this.meter=meter; this.observer=observer; this.requestId=requestId;
        }
        private void dispatch(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
                PcbLayerRoutingPrototype.BranchLimitSelection selection) {
            if (meter.insideOwner) throw new IllegalStateException("OBSERVER_REENTRANT");
            meter.insideOwner=true;
            try {
                if (selection==null) observer.observe(requestId,snapshot);
                else observer.observeSelected(requestId,snapshot,selection);
            } finally { meter.insideOwner=false; }
        }
        public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
            dispatch(snapshot,null);
        }
        public void onBranchLimitSelected(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
                PcbLayerRoutingPrototype.BranchLimitSelection selection) {
            dispatch(snapshot,selection);
        }
    }

/* Replacement sink factory members for CohortTerminalTelemetryStudy.
 * Source-only prerequisite; root integrates this fragment. */
static TerminalSink createSink(String requestId,PadProof proof,PcbBoardLayout layout,
        List<String> requested,SetupMeter m) throws Exception {
    /* Preserve canonical authored-capture authentication before any meter work. */
    if (proof==null || layout==null || requested==null || m==null ||
            proof.netByPad==null || proof.netPadCounts==null || requested.size()!=60 ||
            proof.netByPad.size()!=246 || proof.netPadCounts.size()!=60)
        throw new IOException("OBSERVER_SPEC_INPUT");
    return createSinkWithCensus(requestId,proof,layout,requested,m,246,60);
}

/** Only the two native prerequisite profiles are representable here. */
static enum R9CanaryProfile {
    FAILED_FIRST_OPEN(4,2,59,49),
    FAILED_FIRST_BLOCKED(8,6,59,49);

    final int padCount,netCount,gridWidth,gridHeight;
    R9CanaryProfile(int pads,int nets,int width,int height) {
        padCount=pads; netCount=nets; gridWidth=width; gridHeight=height;
    }
}

/** Fixed-profile entry for source canaries; callers cannot provide their own census. */
static TerminalSink createCanarySink(String requestId,PadProof proof,PcbBoardLayout layout,
        List<String> requested,SetupMeter m,R9CanaryProfile profile) throws Exception {
    if (profile!=R9CanaryProfile.FAILED_FIRST_OPEN &&
            profile!=R9CanaryProfile.FAILED_FIRST_BLOCKED)
        throw new IOException("OBSERVER_CANARY_PROFILE");
    if (proof==null || layout==null || requested==null || m==null ||
            proof.netByPad==null || proof.netPadCounts==null ||
            requested.size()!=profile.netCount || proof.netByPad.size()!=profile.padCount ||
            proof.netPadCounts.size()!=profile.netCount)
        throw new IOException("OBSERVER_SPEC_INPUT");

    /* Check the real board admission before the caller can construct a Session. */
    GridBinding grid=actualGrid(layout,m);
    m.charge(2); // precharge the two actualGrid binding-field inspections
    if (grid.width!=profile.gridWidth || grid.height!=profile.gridHeight)
        throw new IOException("OBSERVER_CANARY_GRID");
    return createSinkWithCensus(requestId,proof,layout,requested,m,
        profile.padCount,profile.netCount);
}

/* Only fixed entrypoints above may select these supported census pairs. */
private static boolean r9SupportedCensus(int pads,int nets) {
    return (pads==246 && nets==60) || (pads==4 && nets==2) || (pads==8 && nets==6);
}

private static TerminalSink createSinkWithCensus(String requestId,PadProof proof,
        PcbBoardLayout layout,List<String> requested,SetupMeter m,int padCount,int netCount)
        throws Exception {
    if (!r9SupportedCensus(padCount,netCount))
        throw new IOException("OBSERVER_CANARY_PROFILE");
    /* Recheck input shape before copy admission; all invalid census paths are free. */
    if (proof==null || layout==null || requested==null || m==null ||
            proof.netByPad==null || proof.netPadCounts==null ||
            requested.size()!=netCount || proof.netByPad.size()!=padCount ||
            proof.netPadCounts.size()!=netCount)
        throw new IOException("OBSERVER_SPEC_INPUT");

    m.copy(padCount);
    Map<String,PadIdentity> pads=new java.util.LinkedHashMap<String,PadIdentity>();
    for (Map.Entry<String,String> e:proof.netByPad.entrySet()) {
        m.charge(4);
        PcbPadPlacement placed=layout.getPad(e.getKey());
        if (placed==null) throw new IOException("OBSERVER_PAD_MISSING");
        m.copy(3);
        pads.put(e.getKey(),new PadIdentity(e.getValue(),placed.getX(),placed.getY()));
    }
    m.copy(netCount);
    Map<String,Integer> ordinals=new java.util.LinkedHashMap<String,Integer>();
    for (int i=0;i<requested.size();i++) {
        m.charge(1);
        if (ordinals.put(requested.get(i),Integer.valueOf(i+1))!=null)
            throw new IOException("OBSERVER_NET_DUPLICATE");
    }
    m.method("reserve",new Class<?>[]{long.class},Long.valueOf(1));
    ObserverMeter port=new ObserverMeter(m);
    ObservationSpec spec=ObservationSpec.create(port,requestId,2,padCount,netCount,
        pads,ordinals,proof.netPadCounts);
    return new TerminalSink(port,new TerminalTelemetryObserver(port,spec),requestId);
}
    static Object observedFresh(Parent parent,TroubleshootBoard board,PcbBoardLayout layout,
            Object capture,List<String> cohort,SetupMeter meter,long started,TerminalSink sink)
            throws Exception {
        try { return parent.fresh(board,layout,capture,cohort,meter.meter,started,sink); }
        finally { sink.observer.seal(); }
    }

    static final class Cohort {
        final List<String> netIds, requested;
        Cohort(List<String> netIds,List<String> requested) {
            this.netIds=netIds; this.requested=requested;
        }
    }
    @SuppressWarnings("unchecked")
    static Cohort deriveCohort(Map<String,Object> input,List<String> original,SetupMeter m)
            throws Exception {
        m.charge(4);
        Object priorValue=input.get("qualifiedPrior");
        if (!(priorValue instanceof Map) || original==null || original.size()!=60)
            throw new IOException("COHORT_INPUT");
        Object dispositionValue=((Map<?,?>)priorValue).get("trialDisposition");
        if (!(dispositionValue instanceof Map)) throw new IOException("COHORT_DISPOSITION");
        Map<String,Object> disposition=(Map<String,Object>)dispositionValue;
        Object idsValue=disposition.get("cohortNetIds");
        if (!(idsValue instanceof List)) throw new IOException("COHORT_IDS");
        List<?> ids=(List<?>)idsValue;
        m.charge(8);
        if (ids.size()!=2 || !(ids.get(0) instanceof String) || !(ids.get(1) instanceof String) ||
                ids.get(0).equals(ids.get(1)) || !"RUN".equals(disposition.get("status")) ||
                !"TRIAL_EXECUTED".equals(disposition.get("reason")) ||
                !Boolean.TRUE.equals(disposition.get("feedbackValidated")) ||
                !Boolean.TRUE.equals(disposition.get("orderChanged")) ||
                !ids.get(0).equals(disposition.get("capturedFailedNetId")) ||
                !ids.get(1).equals(disposition.get("controlFailedNetId")))
            throw new IOException("COHORT_DISPOSITION");
        m.copy(2);
        List<String> cohort=new java.util.ArrayList<String>(2);
        cohort.add((String)ids.get(0)); cohort.add((String)ids.get(1));
        // The requested pair leads in its authenticated order; the remainder is stable.
        m.copy(60);
        List<String> requested=new java.util.ArrayList<String>(60);
        requested.addAll(cohort);
        java.util.Set<String> unique=new java.util.HashSet<String>();
        for (String id:original) {
            m.charge(2); m.copy(1);
            if (!unique.add(id)) throw new IOException("COHORT_VECTOR_DUPLICATE");
            if (!cohort.contains(id)) requested.add(id);
        }
        if (!unique.containsAll(cohort)) throw new IOException("COHORT_NOT_IN_CAPTURE");
        m.charge(1);
        if (requested.size()!=60 || !requested.equals(disposition.get("requestedNetIds")))
            throw new IOException("COHORT_REQUESTED_VECTOR");
        return new Cohort(cohort,requested);
    }

    @SuppressWarnings("unchecked")
    static PadProof verifyFreshOriginal(Object original,Map<String,Object> input,SetupMeter m)
            throws Exception {
        m.charge(12);
        Object c=field(original,"captureData"), legacy=field(original,"legacyCapture");
        Map<String,Object> capture=(Map<String,Object>)input.get("capture");
        Map<String,Object> settings=(Map<String,Object>)input.get("ownerSettings");
        Map<String,Object> source=(Map<String,Object>)input.get("sourceIdentity");
        Map<String,Object> actualSource=(Map<String,Object>)field(original,"source");
        Map<String,Object> seeds=(Map<String,Object>)input.get("seedValues");
        if (c==null || legacy==null || capture==null || settings==null || source==null ||
                actualSource==null || seeds==null) throw new IOException("CAPTURE_INPUT");
        if (!"9007199254740993".equals(seeds.get("rootSeed")) ||
                !"-6587958367974952701".equals(seeds.get("placementSeed")) ||
                !"8445301674744508279".equals(seeds.get("routingSeed")))
            throw new IOException("CAPTURE_SEEDS");
        // captureOriginal itself checks actual request 0, attempt 4 and signed-long seeds.
        if (!Boolean.TRUE.equals(field(original,"ownersReleased")) ||
                !Boolean.TRUE.equals(field(original,"contextRestored")) ||
                !Boolean.TRUE.equals(field(c,"sourceUnchanged")) ||
                !Boolean.TRUE.equals(field(legacy,"ownersReleased")) ||
                !Boolean.TRUE.equals(field(legacy,"contextRestored")) ||
                ((Number)field(legacy,"count")).intValue()!=1 ||
                ((Number)field(legacy,"pass")).intValue()!=2 ||
                ((Number)field(c,"ownerPass")).intValue()!=2 ||
                ((Number)field(c,"searchPass")).intValue()!=2)
            throw new IOException("CAPTURE_CLEANUP_OR_PASS");
        List<String> netIds=(List<String>)field(c,"netIds");
        if (netIds==null || netIds.size()!=60 || !netIds.equals(capture.get("capturedNetIds")) ||
                !field(c,"priorPromotedNetId").equals(capture.get("priorPromotedNetId")) ||
                !"D_SENSE".equals(field(c,"priorPromotedNetId")) ||
                !field(c,"failedNetId").equals(capture.get("failedNetId")))
            throw new IOException("CAPTURE_VECTOR");
        if (!field(c,"policy").toString().equals(settings.get("boardMode")) ||
                !field(c,"maximumExpansions").equals(settings.get("maxExpansions")) ||
                !field(c,"expandedAtFailure").equals(settings.get("expandedAtFailure")) ||
                !field(c,"maximumVias").equals(settings.get("maxVias")) ||
                !field(c,"viaCost").equals(settings.get("viaCost")) ||
                !field(c,"policyTransitions").equals(settings.get("maxTransitions")) ||
                !field(c,"branchStrategy").equals(settings.get("branchStrategy")) ||
                !field(c,"netStrategy").equals(settings.get("netStrategy")) ||
                !field(c,"roleWidths").equals(settings.get("roleWidthsEnabled")) ||
                !field(c,"orderingStream").equals(settings.get("orderingStreamEnabled")))
            throw new IOException("CAPTURE_SETTINGS");
        String[][] pairs={{"geometryBeforeSha256","templateGeometryBeforeSha256"},
            {"geometryAfterSha256","templateGeometryAfterSha256"},
            {"detachedGeometrySha256","detachedGeometrySha256"},
            {"effectsBeforeSha256","templateEffectsBeforeSha256"},
            {"effectsAfterSha256","templateEffectsAfterSha256"},
            {"traceCount","templateTraceCount"},{"holeCount","templateHoleCount"}};
        for (String[] pair:pairs) {
            m.charge(1);
            if (source.get(pair[0])==null || !source.get(pair[0]).equals(actualSource.get(pair[1])))
                throw new IOException("CAPTURE_SOURCE_IDENTITY");
        }
        if (!actualSource.get("templateGeometryBeforeSha256").equals(actualSource.get("templateGeometryAfterSha256")) ||
                !actualSource.get("templateEffectsBeforeSha256").equals(actualSource.get("templateEffectsAfterSha256")) ||
                !actualSource.get("detachedGeometrySha256").equals(
                actualSource.get("detachedGeometryAfterSha256")) ||
                !Integer.valueOf(0).equals(actualSource.get("templateTraceCount")) ||
                !Integer.valueOf(0).equals(actualSource.get("templateHoleCount")))
            throw new IOException("CAPTURE_TEMPLATE");
        Map<String,Object> padIdentity=(Map<String,Object>)source.get("padLandIdentity");
        if (padIdentity==null || !"TSJ_PAD_LAND_ASCII_V1".equals(padIdentity.get("format")) ||
                !Integer.valueOf(246).equals(padIdentity.get("rowCount")))
            throw new IOException("CAPTURE_PAD_IDENTITY");
        return provePads((TroubleshootBoard)field(legacy,"board"),
            (PcbBoardLayout)field(legacy,"template"),netIds,
            (String)padIdentity.get("sha256"),m);
    }

    @SuppressWarnings("unchecked")
    static Map<String,Object> originalDto(Object original,Arguments args,PadProof padProof,
            String captureId,SetupMeter m) throws Exception {
        m.charge(2);
        if (captureId==null || !UUID.matcher(captureId).matches() || captureId.equals(args.runId))
            throw new IOException("CAPTURE_ID");
        Object c=field(original,"captureData"), legacy=field(original,"legacyCapture");
        Map<String,Object> op=(Map<String,Object>)field(original,"operation1");
        Map<String,Object> src=(Map<String,Object>)field(original,"source");
        List<String> netIds=(List<String>)field(c,"netIds");
        m.copy(60);
        List<String> detachedNets=new java.util.ArrayList<String>(netIds);
        m.copy(45); // all map entry slots, precharged before creating DTOs
        Map<String,Object> receipt=new java.util.LinkedHashMap<String,Object>();
        receipt.put("receiptPath",op.get("receiptPath")); receipt.put("receiptBytes",op.get("receiptBytes"));
        receipt.put("receiptSha256",op.get("receiptSha256"));
        receipt.put("receiptOrdinal",field(legacy,"receiptOrdinal"));
        receipt.put("lineageOrdinal",field(legacy,"lineageOrdinal"));
        Map<String,Object> request=new java.util.LinkedHashMap<String,Object>();
        request.put("requestIndex",op.get("requestIndex")); request.put("selectedAttempt",op.get("selectedAttempt"));
        request.put("pass",field(legacy,"pass"));
        Map<String,Object> seeds=new java.util.LinkedHashMap<String,Object>();
        seeds.put("rootSeed",op.get("rootSeed")); seeds.put("placementSeed",op.get("placementSeed"));
        seeds.put("routingSeed",op.get("routingSeed"));
        Map<String,Object> settings=new java.util.LinkedHashMap<String,Object>();
        settings.put("policy",field(c,"policy").toString());
        settings.put("maximumExpansions",field(c,"maximumExpansions"));
        settings.put("maximumVias",field(c,"maximumVias")); settings.put("viaCost",field(c,"viaCost"));
        settings.put("policyTransitions",field(c,"policyTransitions")); settings.put("policyVias",field(c,"policyVias"));
        settings.put("branchOrderStrategy",field(c,"branchStrategy")); settings.put("netOrderStrategy",field(c,"netStrategy"));
        settings.put("roleWidths",field(c,"roleWidths")); settings.put("orderingStream",field(c,"orderingStream"));
        Map<String,Object> template=new java.util.LinkedHashMap<String,Object>();
        template.put("geometryBeforeSha256",src.get("templateGeometryBeforeSha256"));
        template.put("geometryAfterSha256",src.get("templateGeometryAfterSha256"));
        template.put("effectsBeforeSha256",src.get("templateEffectsBeforeSha256"));
        template.put("effectsAfterSha256",src.get("templateEffectsAfterSha256"));
        template.put("traceCount",src.get("templateTraceCount")); template.put("holeCount",src.get("templateHoleCount"));
        Map<String,Object> pad=new java.util.LinkedHashMap<String,Object>();
        pad.put("schema","TSJ_PAD_LAND_ASCII_V1"); pad.put("padCount",Integer.valueOf(246));
        pad.put("padLandSha256",padProof.sha256);
        Map<String,Object> out=new java.util.LinkedHashMap<String,Object>();
        out.put("schema","tsj-cohort-terminal-original-capture-v1"); out.put("captureId",captureId);
        out.put("sourceManifestSha256",args.manifestSha); out.put("operation","AUTHORED");
        out.put("receipt",receipt); out.put("request",request); out.put("seeds",seeds);
        out.put("ownerSettings",settings); out.put("priorPromotedNetId",field(c,"priorPromotedNetId"));
        out.put("netIds",detachedNets); out.put("template",template); out.put("padIdentity",pad);
        out.put("ownersReleased",field(original,"ownersReleased"));
        out.put("contextRestored",field(original,"contextRestored"));
        out.put("cleanupNanos",field(original,"cleanupNanos"));
        return out;
    }

    static final class PadProof {
        final String sha256;
        final Map<String,String> netByPad;
        final Map<String,Integer> netPadCounts;
        PadProof(String sha,Map<String,String> pads,Map<String,Integer> counts) {
            sha256=sha; netByPad=pads; netPadCounts=counts;
        }
    }
    /** Independently census and encode the actual captured template, not prior rows. */
    static PadProof provePads(TroubleshootBoard board,PcbBoardLayout layout,
            List<String> requested, String expectedSha,SetupMeter m) throws Exception {
        if (board==null || layout==null || requested==null || requested.size()!=60 ||
                expectedSha==null || !SHA.matcher(expectedSha).matches())
            throw new IOException("PAD_PROOF_INPUT");
        m.charge(3);
        Object netMap=field(board,"nets"), padMap=field(board,"pads"), layoutMap=field(layout,"pads");
        if (!(netMap instanceof Map) || !(padMap instanceof Map) || !(layoutMap instanceof Map) ||
                ((Map<?,?>)netMap).size()!=60 || ((Map<?,?>)padMap).size()!=246 ||
                ((Map<?,?>)layoutMap).size()!=246) throw new IOException("PAD_CENSUS");
        m.copy(60);
        java.util.Vector<String> boardNets=board.getNetIds();
        m.copy(246);
        java.util.Vector<String> boardPads=board.getPadIds();
        m.copy(246);
        java.util.Vector<PcbPadPlacement> placed=layout.getPads();
        m.charge(3);
        if (boardNets.size()!=60 || boardPads.size()!=246 || placed.size()!=246)
            throw new IOException("PAD_CENSUS_CHANGED");
        java.util.Map<String,String> padNet=new java.util.LinkedHashMap<String,String>();
        java.util.Map<String,Integer> netCounts=new java.util.LinkedHashMap<String,Integer>();
        java.util.Set<String> logicalPads=new java.util.HashSet<String>();
        java.util.Set<String> nets=new java.util.HashSet<String>();
        java.util.Set<String> requestedSet=new java.util.HashSet<String>();
        for (String netId:requested) {
            m.charge(1); meteredAscii(netId,m); m.copy(1);
            if (netId.length()==0 || !requestedSet.add(netId)) throw new IOException("REQUESTED_NETS");
        }
        for (String netId:boardNets) {
            m.charge(2); meteredAscii(netId,m); m.copy(1);
            if (!nets.add(netId) || !requestedSet.contains(netId)) throw new IOException("NET_VECTOR");
            BoardNet net=board.getNet(netId);
            if (net==null) throw new IOException("NET_MISSING");
            Object raw=field(net,"padIds");
            if (!(raw instanceof java.util.Vector)) throw new IOException("NET_PAD_TYPE");
            int size=((java.util.Vector<?>)raw).size();
            if (size<1 || size>246) throw new IOException("NET_PAD_COUNT");
            m.copy(size);
            java.util.Vector<String> ids=net.getPadIds();
            if (ids.size()!=size) throw new IOException("NET_PAD_CHANGED");
            m.copy(1); netCounts.put(netId,Integer.valueOf(size));
            for (String id:ids) {
                m.charge(3); meteredAscii(id,m);
                BoardPad pad=board.getPad(id);
                m.copy(1);
                if (id.length()==0 || pad==null || !netId.equals(pad.getNetId()) || !logicalPads.add(id))
                    throw new IOException("NET_PAD_BIJECTION");
                m.copy(1); padNet.put(id,netId);
            }
        }
        m.charge(1);
        if (logicalPads.size()!=246 || padNet.size()!=246) throw new IOException("NET_PAD_TOTAL");
        for (String id:boardPads) {
            m.charge(1);
            if (!logicalPads.remove(id)) throw new IOException("BOARD_PAD_BIJECTION");
        }
        if (!logicalPads.isEmpty()) throw new IOException("BOARD_PAD_TOTAL");
        final SetupMeter sortMeter=m;
        java.util.Comparator<PcbPadPlacement> cmp=new java.util.Comparator<PcbPadPlacement>() {
            public int compare(PcbPadPlacement a,PcbPadPlacement b) {
                try { sortMeter.charge(1); }
                catch (Exception e) { throw new IllegalStateException("PAD_SORT_METER",e); }
                return a.getPadId().compareTo(b.getPadId());
            }
        };
        // Sorting operates on the precharged detached vector, not the source layout.
        java.util.Collections.sort(placed,cmp);
        java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
        m.charge(23); // 22 header bytes plus digest update, before encoding
        byte[] header="TSJ_PAD_LAND_ASCII_V1\n".getBytes("US-ASCII");
        hash.update(header);
        String previous=null;
        int total=header.length;
        for (PcbPadPlacement pad:placed) {
            m.charge(8);
            String id=pad.getPadId(); String net=padNet.get(id);
            if (id==null || net==null || (previous!=null && previous.compareTo(id)>=0) ||
                    pad.getAttachment()!=PcbTerminalAttachment.PLATED_THROUGH_HOLE ||
                    !PcbCopperAccess.hasCopper(pad,PcbCopperLayer.TOP) ||
                    !PcbCopperAccess.hasCopper(pad,PcbCopperLayer.BOTTOM))
                throw new IOException("PLACED_PAD_BIJECTION");
            previous=id;
            meteredAscii(id,m); meteredAscii(net,m);
            m.copy(1); Rectangle bounds=pad.getPadBounds();
            if (bounds.width<=0 || bounds.height<=0) throw new IOException("PAD_BOUNDS");
            m.copy(6); m.charge(6); // integer conversion before allocation
            String[] numbers={Integer.toString(pad.getX()),Integer.toString(pad.getY()),
                Integer.toString(bounds.x),Integer.toString(bounds.y),
                Integer.toString(bounds.width),Integer.toString(bounds.height)};
            String attachment=pad.getAttachment().name(),layer="TOP+BOTTOM";
            int length=partLength(id)+partLength(net)+partLength(attachment)+partLength(layer)+1;
            for (String number:numbers) length+=number.length()+1;
            if (length>4096 || total>1048576-length) throw new IOException("PAD_ROW_BYTES");
            m.charge(length+1); // emitted ASCII bytes and hash update
            StringBuilder row=new StringBuilder(length);
            appendPart(row,id); appendPart(row,net);
            for (String number:numbers) row.append(number).append(';');
            appendPart(row,attachment); appendPart(row,layer); row.append('\n');
            byte[] bytes=row.toString().getBytes("US-ASCII");
            if (bytes.length!=length) throw new IOException("PAD_ENCODING");
            hash.update(bytes); total+=length;
        }
        m.charge(1);
        byte[] digest=hash.digest();
        StringBuilder hex=new StringBuilder(64);
        final char[] digits="0123456789abcdef".toCharArray();
        for (byte b:digest) { int v=b&255; hex.append(digits[v>>>4]).append(digits[v&15]); }
        String actual=hex.toString();
        m.charge(1);
        if (!expectedSha.equals(actual)) throw new IOException("PAD_IDENTITY");
        return new PadProof(actual,padNet,netCounts);
    }
    static int partLength(String s) { return Integer.toString(s.length()).length()+1+s.length()+1; }
    static void appendPart(StringBuilder b,String s) { b.append(s.length()).append(':').append(s).append(';'); }

    /** Assembled comparison, awaiting separately implemented final export accounting. */
    static final class Comparison {
        final Map<String,Object> result;
        final boolean parity, finiteReference;
        final SetupMeter meter;
        R7WorkPlan workPlan; // assigned once before handoff; detached control/scalars only
        Comparison(Map<String,Object> result,boolean parity,boolean finiteReference,SetupMeter meter) {
            this.result=result; this.parity=parity; this.finiteReference=finiteReference; this.meter=meter;
        }
    }
    static final class GridBinding {
        final int width,height;
        GridBinding(int width,int height) { this.width=width; this.height=height; }
    }
    /** Read the actual template and the same pure admission used by Search. */
    static GridBinding actualGrid(PcbBoardLayout template,SetupMeter m) throws Exception {
        m.charge(1);
        if (template==null) throw new IOException("GRID_TEMPLATE");
        m.copy(4); // getBoardOutline returns a Rectangle copying four scalar fields
        m.charge(1);
        Rectangle outline=template.getBoardOutline();
        m.copy(5);
        m.charge(1);
        PcbLayerRoutingPrototype.GridAdmission admission=PcbLayerRoutingPrototype.gridAdmission(outline);
        m.charge(1);
        if (!admission.eligible) throw new IOException("GRID_INELIGIBLE");
        m.charge(1);
        if (admission.width<1) throw new IOException("GRID_WIDTH");
        m.charge(1);
        if (admission.height<1) throw new IOException("GRID_HEIGHT");
        m.charge(1);
        if (admission.cells!=(long)admission.width*admission.height) throw new IOException("GRID_CELLS");
        m.copy(2);
        return new GridBinding(admission.width,admission.height);
    }
    static void bindSelectedGrid(Map<String,Object> observation,GridBinding grid,SetupMeter m)
            throws Exception {
        m.charge(1); Object selected=observation.get("selectedSnapshot");
        if (selected==null) return;
        m.charge(1);
        if (!(selected instanceof Map)) throw new IOException("GRID_SNAPSHOT");
        Map<?,?> snapshot=(Map<?,?>)selected;
        m.charge(1); Object width=snapshot.get("gridWidth");
        m.charge(1);
        if (!(width instanceof Integer)) throw new IOException("GRID_SNAPSHOT_WIDTH_TYPE");
        m.charge(1);
        if (((Integer)width).intValue()!=grid.width) throw new IOException("GRID_SNAPSHOT_WIDTH");
        m.charge(1); Object height=snapshot.get("gridHeight");
        m.charge(1);
        if (!(height instanceof Integer)) throw new IOException("GRID_SNAPSHOT_HEIGHT_TYPE");
        m.charge(1);
        if (((Integer)height).intValue()!=grid.height) throw new IOException("GRID_SNAPSHOT_HEIGHT");
    }
    static final class ArmResult {
        final Object fresh;
        final Map<String,Object> observation,cleanup;
        ArmResult(Object fresh,Map<String,Object> observation,Map<String,Object> cleanup) {
            this.fresh=fresh; this.observation=observation; this.cleanup=cleanup;
        }
    }
    static String uuidApart(SetupMeter meter,String... excluded) throws Exception {
        for (int attempt=0;attempt<16;attempt++) {
            meter.charge(1);
            String id=java.util.UUID.randomUUID().toString(); boolean duplicate=false;
            for (String old:excluded) { meter.charge(1); if (id.equals(old)) duplicate=true; }
            if (!duplicate) return id;
        }
        throw new IOException("UUID_COLLISIONS");
    }
    static final class ContextAudit {
        final CirSim circuitSim, singleton;
        final Object circuitSnapshot,singletonSnapshot,defaults;
        final Method matches,defaultsMatch;
        final long comparisonBound;
        ContextAudit(SetupMeter m) throws Exception {
            m.charge(2); circuitSim=CircuitElm.sim; singleton=CirSim.theSim;
            Class<?> snapshotType=Class.forName(
                "com.lushprojects.circuitjs1.client.Rb100PhysicalDiagnostics$SimSnapshot");
            Class<?> defaultsType=Class.forName(
                "com.lushprojects.circuitjs1.client.Rb100PhysicalDiagnostics$ModelDefaults");
            Constructor<?> sc=snapshotType.getDeclaredConstructor(CirSim.class); sc.setAccessible(true);
            Constructor<?> dc=defaultsType.getDeclaredConstructor(); dc.setAccessible(true);
            long first=prechargeSnapshot(m,circuitSim),second=prechargeSnapshot(m,singleton);
            circuitSnapshot=sc.newInstance(circuitSim); singletonSnapshot=sc.newInstance(singleton);
            m.charge(8); defaults=dc.newInstance();
            matches=snapshotType.getDeclaredMethod("matches"); matches.setAccessible(true);
            defaultsMatch=defaultsType.getDeclaredMethod("matchesRestored"); defaultsMatch.setAccessible(true);
            comparisonBound=first+second;
        }
        private static long prechargeSnapshot(SetupMeter m,CirSim sim) throws Exception {
            m.charge(1); if (sim==null) return 0;
            m.charge(3);
            int graph=sim.elmList==null?0:sim.elmList.size();
            int scopes=sim.scopes==null?0:sim.scopes.length;
            int columns=sim.scopeColCount==null?0:sim.scopeColCount.length;
            if (graph<0 || scopes<0 || columns<0 ||
                    (long)graph+scopes+columns>1048576L) throw new IOException("CONTEXT_COPY_LIMIT");
            copyChunks(m,graph); copyChunks(m,scopes); copyChunks(m,columns);
            return (long)graph+scopes+columns;
        }
        private static void copyChunks(SetupMeter m,int n) throws Exception {
            while (n>0) { int chunk=Math.min(n,4096); m.copy(chunk); n-=chunk; }
        }
        boolean unchanged(SetupMeter m) throws Exception {
            final boolean a=snapshotMatches(m,circuitSnapshot);
            final boolean b=snapshotMatches(m,singletonSnapshot);
            final boolean d=defaultsMatch(m,defaults);
            return test(m,new Guard() { public boolean evaluate() throws Exception { return a; } }) && test(m,new Guard() { public boolean evaluate() throws Exception { return b; } }) && test(m,new Guard() { public boolean evaluate() throws Exception { return d; } }) &&
                test(m,new Guard() { public boolean evaluate() throws Exception { return CircuitElm.sim==circuitSim; } }) && test(m,new Guard() { public boolean evaluate() throws Exception { return CirSim.theSim==singleton; } });
        }
    }
    interface Guard { boolean evaluate() throws Exception; }
    static boolean test(SetupMeter m,Guard guard) throws Exception {
        m.charge(1); return guard.evaluate();
    }
    static boolean snapshotMatches(SetupMeter m,Object snapshot) throws Exception {
        final CirSim sim=(CirSim)field(snapshot,"sim");
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return sim==null; } })) return true;
        final Object graph=field(snapshot,"graph"), graphElements=field(snapshot,"graphElements");
        final Object scopes=field(snapshot,"scopeElements"), columns=field(snapshot,"scopeColumns");
        final Object columnValues=field(snapshot,"scopeColumnValues");
        final int count=((Number)field(snapshot,"scopeCount")).intValue();
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return sim.elmList!=graph; } }) || test(m,new Guard() { public boolean evaluate() throws Exception { return sim.scopeCount!=count; } }) ||
            test(m,new Guard() { public boolean evaluate() throws Exception { return sim.scopeColCount!=columns; } }) ||
            !arrayEquals(m,sim.scopes,scopes) || !arrayEquals(m,columns,columnValues)) return false;
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return graph==null; } })) return test(m,new Guard() { public boolean evaluate() throws Exception { return graphElements==null; } });
        final java.util.List<?> left=(java.util.List<?>)graph,right=(java.util.List<?>)graphElements;
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return left.size()!=right.size(); } })) return false;
        for (int i=0;i<left.size();i++) {
            final int at=i;
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return left.get(at)!=right.get(at); } })) return false;
        }
        return true;
    }
    static boolean arrayEquals(SetupMeter m,final Object a,final Object b) throws Exception {
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return a==b; } })) return true;
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return a==null || b==null; } })) return false;
        final int n=java.lang.reflect.Array.getLength(a);
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return n!=java.lang.reflect.Array.getLength(b); } })) return false;
        for (int i=0;i<n;i++) {
            final int at=i;
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return !java.util.Objects.equals(java.lang.reflect.Array.get(a,at),
                    java.lang.reflect.Array.get(b,at)); } })) return false;
        }
        return true;
    }
    static boolean defaultsMatch(SetupMeter m,final Object defaults) throws Exception {
        return test(m,new Guard() { public boolean evaluate() throws Exception { return java.util.Objects.equals(field(defaults,"diode"),DiodeElm.lastModelName); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return java.util.Objects.equals(field(defaults,"zener"),ZenerElm.lastZenerModelName); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return java.util.Objects.equals(field(defaults,"transistor"),TransistorElm.lastModelName); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return java.util.Objects.equals(field(defaults,"customLogic"),CustomLogicElm.lastModelName); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return java.util.Objects.equals(field(defaults,"customComposite"),CustomCompositeElm.lastModelName); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return java.util.Objects.equals(field(defaults,"led"),LEDElm.lastLEDModelName); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return Double.doubleToLongBits(((Number)field(defaults,"mosfetBeta")).doubleValue())==
                Double.doubleToLongBits(MosfetElm.lastBeta); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return ((Number)field(defaults,"groundSymbol")).intValue()==GroundElm.lastSymbolType; } });
    }

    static String geometry(Parent p,SetupMeter m,PcbBoardLayout layout) throws Exception {
        Class<?> fresh=Class.forName(PARENT+"$FreshRun");
        p.call("reserveFingerprintCopies",new Class<?>[]{fresh,p.meterType,PcbBoardLayout.class},
            null,m.meter,layout);
        String fingerprint=layout.geometryFingerprint();
        for (int i=0;i<fingerprint.length();i++) {
            m.charge(1);
            if (fingerprint.charAt(i)>127) throw new IOException("GEOMETRY_ASCII");
        }
        m.charge(1L+(fingerprint.length()+4095L)/4096L);
        return (String)p.call("hashString",new Class<?>[]{String.class},fingerprint);
    }
/* Replacement executeArm members and fixed-fixture arm adapter for root integration.
 * Source-only; depends on R9FixtureSink.fragment.java and R9CanaryProfile. */
static ArmResult executeArm(Parent p,SetupMeter m,Object capture,Object legacy,
        Cohort cohort,PadProof proof,GridBinding grid,String role,String id,long started)
        throws Exception {
    return executeArmWithProfile(p,m,capture,legacy,cohort,proof,grid,role,id,started,null);
}
@SuppressWarnings("unchecked")
private static ArmResult executeArmWithProfile(Parent p,SetupMeter m,Object capture,Object legacy,
        Cohort cohort,PadProof proof,GridBinding grid,String role,String id,long started,
        R9CanaryProfile profile) throws Exception {
    TroubleshootBoard board=(TroubleshootBoard)field(legacy,"board");
    PcbBoardLayout layout=(PcbBoardLayout)field(legacy,"template");
    String before=geometry(p,m,layout); m.finish();
    ContextAudit context=new ContextAudit(m); m.finish();
    TerminalSink sink=null;
    if ("OBSERVE".equals(role)) sink=profile==null?
        createSink(id,proof,layout,cohort.requested,m):
        createCanarySink(id,proof,layout,cohort.requested,m,profile);
    m.finish();
    Object fresh=null;
    String after=null;
    boolean contextRestored=false;
    long localCleanupStart=0,localCleanup=0;
    try {
        fresh=sink==null?p.fresh(board,layout,capture,cohort.netIds,m.meter,started,null):
            observedFresh(p,board,layout,capture,cohort.netIds,m,started,sink);
        contextRestored=context.unchanged(m);
        after=geometry(p,m,layout); m.finish();
    } finally {
        localCleanupStart=System.nanoTime();
        board=null; layout=null;
        localCleanup=Math.max(1L,System.nanoTime()-localCleanupStart);
    }
    if (fresh==null || !Boolean.TRUE.equals(field(fresh,"referencesDropped")))
        throw new IOException("ARM_ALIASES");
    Map<String,Object> run=(Map<String,Object>)field(fresh,"json");
    Map<String,Object> observation=materializeObservation(sink,m);
    if (sink!=null) {
        Map<String,Object> outcome=(Map<String,Object>)run.get("outcome");
        if ("QUALITY_REJECTED".equals(outcome.get("disposition")) &&
                "NO_FAILURE".equals(observation.get("state")))
            observation.put("state","NON_BRANCH_REJECTION");
        validateObservation(run,observation,cohort.requested,m);
        bindSelectedGrid(observation,grid,m);
    }
    long cleanupNanos=((Number)field(fresh,"cleanupNanos")).longValue()+localCleanup;
    m.copy(10);
    Map<String,Object> cleanup=new java.util.LinkedHashMap<String,Object>();
    cleanup.put("schema","tsj-cohort-terminal-arm-cleanup-v1");
    // N's actual returned conjunction proves each named local owner alias was dropped;
    // this wrapper also dropped its own borrowed board/template references above.
    boolean aliases=Boolean.TRUE.equals(field(fresh,"referencesDropped")) && board==null && layout==null;
    cleanup.put("adapterSessionAliasDropped",Boolean.valueOf(aliases));
    cleanup.put("adapterSearchAliasDropped",Boolean.valueOf(aliases));
    cleanup.put("adapterCandidateAliasDropped",Boolean.valueOf(aliases));
    cleanup.put("adapterRouteResultAliasDropped",Boolean.valueOf(aliases));
    cleanup.put("adapterBoardTemplateAliasesDropped",Boolean.valueOf(aliases));
    cleanup.put("sourceGeometryBeforeSha256",before);
    cleanup.put("sourceGeometryAfterSha256",after);
    cleanup.put("contextRestored",Boolean.valueOf(contextRestored));
    cleanup.put("cleanupNanos",Long.valueOf(cleanupNanos));
    m.finish();
    return new ArmResult(fresh,observation,cleanup);
}

static final class R9FixtureLegacy {
    final TroubleshootBoard board;
    final PcbBoardLayout template;
    R9FixtureLegacy(TroubleshootBoard board,PcbBoardLayout template) {
        this.board=board; this.template=template;
    }
}

/** One observed arm over a newly constructed immutable native fixture. */
static ArmResult runR9FixtureArm(Arguments args,SetupMeter m,R9CanaryProfile profile,
        String id,long started) throws Exception {
    if (m==null) throw new IOException("R9_FIXTURE_INPUT");
    m.charge(3);
    if (args==null || !r9FixtureProfile(profile) || args.runId==null || id==null ||
            !UUID.matcher(args.runId).matches() || !UUID.matcher(id).matches() ||
            args.runId.equals(id))
        throw new IOException("R9_FIXTURE_INPUT");

    Parent parent=null;
    FailedFirstFixtures.Fixture fixture=null;
    TroubleshootBoard board=null;
    PcbBoardLayout layout=null;
    R9FixtureLegacy legacy=null;
    Cohort cohort=null;
    PadProof proof=null;
    GridBinding grid=null;
    ArmResult result=null;
    try {
        /* The bounded fixture constructor is admitted before it allocates board state. */
        m.charge(1);
        fixture=profile==R9CanaryProfile.FAILED_FIRST_OPEN?
            FailedFirstFixtures.open():FailedFirstFixtures.blocked();
        m.charge(2);
        board=fixture.board;
        layout=fixture.layout;
        m.charge(1);
        m.copy(2);
        legacy=new R9FixtureLegacy(board,layout);
        proof=r9FixturePadProof(board,layout,profile,m);
        cohort=r9FixtureCohort(profile,m);
        m.copy(2);
        grid=new GridBinding(profile.gridWidth,profile.gridHeight);
        m.charge(1);
        parent=new Parent();
        result=executeArmWithProfile(parent,m,null,legacy,cohort,proof,grid,
            "OBSERVE",id,started,profile);
    } finally {
        parent=null;
        board=null;
        layout=null;
        legacy=null;
        cohort=null;
        proof=null;
        grid=null;
        fixture=null;
    }
    return result;
}

private static boolean r9FixtureProfile(R9CanaryProfile profile) {
    return profile==R9CanaryProfile.FAILED_FIRST_OPEN ||
        profile==R9CanaryProfile.FAILED_FIRST_BLOCKED;
}

/** Build an observer proof from this fixture's own logical pads and physical layout.
 * The fixture has no qualified 246-pad identity digest; sha256 deliberately stays null. */
private static PadProof r9FixturePadProof(TroubleshootBoard board,PcbBoardLayout layout,
        R9CanaryProfile profile,SetupMeter m) throws Exception {
    if (board==null || layout==null || !r9FixtureProfile(profile) || m==null)
        throw new IOException("R9_FIXTURE_PAD_INPUT");
    final int padCount=profile.padCount,netCount=profile.netCount;
    m.charge(3);
    Object rawNets=field(board,"nets");
    Object rawPads=field(board,"pads");
    Object rawLayoutPads=field(layout,"pads");
    if (!(rawNets instanceof Map) || !(rawPads instanceof Map) ||
            !(rawLayoutPads instanceof Map))
        throw new IOException("R9_FIXTURE_PAD_CENSUS");
    m.charge(3);
    if (((Map<?,?>)rawNets).size()!=netCount ||
            ((Map<?,?>)rawPads).size()!=padCount ||
            ((Map<?,?>)rawLayoutPads).size()!=padCount)
        throw new IOException("R9_FIXTURE_PAD_CENSUS");

    m.copy(netCount);
    java.util.Vector<String> boardNets=board.getNetIds();
    m.copy(padCount);
    java.util.Vector<String> boardPads=board.getPadIds();
    m.copy(padCount);
    java.util.Vector<PcbPadPlacement> placedPads=layout.getPads();
    m.charge(3);
    if (boardNets.size()!=netCount || boardPads.size()!=padCount ||
            placedPads.size()!=padCount)
        throw new IOException("R9_FIXTURE_PAD_VECTOR");

    m.charge(1);
    Map<String,Integer> netPadCounts=new java.util.LinkedHashMap<String,Integer>();
    m.charge(1);
    Map<String,String> netByPad=new java.util.LinkedHashMap<String,String>();
    m.charge(1);
    java.util.Set<String> seenNets=new java.util.HashSet<String>();
    m.charge(1);
    java.util.Set<String> seenLogicalPads=new java.util.HashSet<String>();
    m.charge(1);
    java.util.Set<String> seenBoardPads=new java.util.HashSet<String>();
    m.charge(1);
    java.util.Set<String> seenLayoutPads=new java.util.HashSet<String>();

    for (int ni=0;ni<netCount;ni++) {
        m.charge(1);
        String netId=boardNets.get(ni);
        m.charge(1);
        if (!r9FixtureNetAllowed(profile,netId))
            throw new IOException("R9_FIXTURE_NET_ID");
        m.copy(1);
        if (!seenNets.add(netId)) throw new IOException("R9_FIXTURE_NET_DUPLICATE");
        m.charge(1);
        BoardNet net=board.getNet(netId);
        if (net==null) throw new IOException("R9_FIXTURE_NET_MISSING");
        m.charge(1);
        Object rawIds=field(net,"padIds");
        if (!(rawIds instanceof java.util.Vector))
            throw new IOException("R9_FIXTURE_NET_PADS");
        m.charge(1);
        int rawCount=((java.util.Vector<?>)rawIds).size();
        if (rawCount<1 || rawCount>padCount)
            throw new IOException("R9_FIXTURE_NET_PADS");
        m.copy(rawCount);
        java.util.Vector<String> ids=net.getPadIds();
        m.charge(1);
        if (ids.size()!=rawCount ||
                rawCount!=r9FixtureExpectedNetPadCount(profile,netId))
            throw new IOException("R9_FIXTURE_NET_PADS");
        m.copy(1);
        if (netPadCounts.put(netId,Integer.valueOf(rawCount))!=null)
            throw new IOException("R9_FIXTURE_NET_DUPLICATE");
        for (int pi=0;pi<rawCount;pi++) {
            m.charge(1);
            String padId=ids.get(pi);
            m.charge(4);
            if (padId==null) throw new IOException("R9_FIXTURE_PAD_ID");
            BoardPad logical=board.getPad(padId);
            if (logical==null || !padId.equals(logical.getId()) ||
                    !netId.equals(logical.getNetId()) ||
                    !netId.equals(r9FixtureExpectedPadNet(profile,padId)))
                throw new IOException("R9_FIXTURE_PAD_NET");
            m.copy(1);
            if (!seenLogicalPads.add(padId))
                throw new IOException("R9_FIXTURE_PAD_DUPLICATE");
            m.copy(1);
            if (netByPad.put(padId,netId)!=null)
                throw new IOException("R9_FIXTURE_PAD_DUPLICATE");
        }
    }

    for (int pi=0;pi<padCount;pi++) {
        m.charge(1);
        String padId=boardPads.get(pi);
        m.charge(2);
        if (padId==null || r9FixtureExpectedPadNet(profile,padId)==null ||
                !netByPad.containsKey(padId))
            throw new IOException("R9_FIXTURE_BOARD_PAD");
        m.copy(1);
        if (!seenBoardPads.add(padId))
            throw new IOException("R9_FIXTURE_BOARD_PAD_DUPLICATE");
    }

    for (int pi=0;pi<padCount;pi++) {
        m.charge(1);
        PcbPadPlacement placed=placedPads.get(pi);
        m.charge(5);
        String padId=placed.getPadId();
        String expectedNet=r9FixtureExpectedPadNet(profile,padId);
        String actualNet=netByPad.get(padId);
        int expectedX=r9FixtureExpectedPadX(profile,padId);
        int expectedY=r9FixtureExpectedPadY(profile,padId);
        m.charge(3);
        int actualX=placed.getX();
        int actualY=placed.getY();
        PcbPadPlacement indexed=layout.getPad(padId);
        if (expectedNet==null || !expectedNet.equals(actualNet) ||
                actualX!=expectedX || actualY!=expectedY || indexed!=placed)
            throw new IOException("R9_FIXTURE_PAD_PLACEMENT");
        m.copy(1);
        if (!seenLayoutPads.add(padId))
            throw new IOException("R9_FIXTURE_LAYOUT_PAD_DUPLICATE");
    }

    m.charge(4);
    if (seenNets.size()!=netCount || netPadCounts.size()!=netCount ||
            netByPad.size()!=padCount || seenLogicalPads.size()!=padCount)
        throw new IOException("R9_FIXTURE_PAD_TOTAL");
    m.charge(2);
    if (seenBoardPads.size()!=padCount || seenLayoutPads.size()!=padCount)
        throw new IOException("R9_FIXTURE_PAD_TOTAL");
    m.charge(1);
    return new PadProof(null,netByPad,netPadCounts);
}

private static boolean r9FixtureNetAllowed(R9CanaryProfile profile,String netId) {
    if (!r9FixtureProfile(profile) || netId==null) return false;
    if ("NET_A".equals(netId) || "NET_B".equals(netId)) return true;
    return profile==R9CanaryProfile.FAILED_FIRST_BLOCKED &&
        ("BLOCK_E_NET".equals(netId) || "BLOCK_W_NET".equals(netId) ||
         "BLOCK_N_NET".equals(netId) || "BLOCK_S_NET".equals(netId));
}

private static int r9FixtureExpectedNetPadCount(R9CanaryProfile profile,String netId) {
    if ("NET_A".equals(netId) || "NET_B".equals(netId)) return 2;
    if (profile==R9CanaryProfile.FAILED_FIRST_BLOCKED &&
            ("BLOCK_E_NET".equals(netId) || "BLOCK_W_NET".equals(netId) ||
             "BLOCK_N_NET".equals(netId) || "BLOCK_S_NET".equals(netId))) return 1;
    return -1;
}

private static String r9FixtureExpectedPadNet(R9CanaryProfile profile,String padId) {
    if ("A1.1".equals(padId) || "A2.1".equals(padId)) return "NET_A";
    if ("B1.1".equals(padId) || "B2.1".equals(padId)) return "NET_B";
    if (profile==R9CanaryProfile.FAILED_FIRST_BLOCKED) {
        if ("BLOCK_E.1".equals(padId)) return "BLOCK_E_NET";
        if ("BLOCK_W.1".equals(padId)) return "BLOCK_W_NET";
        if ("BLOCK_N.1".equals(padId)) return "BLOCK_N_NET";
        if ("BLOCK_S.1".equals(padId)) return "BLOCK_S_NET";
    }
    return null;
}

private static int r9FixtureExpectedPadX(R9CanaryProfile profile,String padId) {
    if ("A1.1".equals(padId) || "B1.1".equals(padId)) return 260;
    if ("A2.1".equals(padId) || "B2.1".equals(padId)) return 500;
    if (profile==R9CanaryProfile.FAILED_FIRST_BLOCKED) {
        if ("BLOCK_E.1".equals(padId)) return 520;
        if ("BLOCK_W.1".equals(padId)) return 480;
        if ("BLOCK_N.1".equals(padId) || "BLOCK_S.1".equals(padId)) return 500;
    }
    return -1;
}

private static int r9FixtureExpectedPadY(R9CanaryProfile profile,String padId) {
    if ("A1.1".equals(padId) || "A2.1".equals(padId)) return 300;
    if ("B1.1".equals(padId) || "B2.1".equals(padId)) return 450;
    if (profile==R9CanaryProfile.FAILED_FIRST_BLOCKED) {
        if ("BLOCK_E.1".equals(padId) || "BLOCK_W.1".equals(padId)) return 300;
        if ("BLOCK_N.1".equals(padId)) return 280;
        if ("BLOCK_S.1".equals(padId)) return 320;
    }
    return -1;
}

/** Fixed independent requested-order witnesses; these are not failure selectors. */
private static Cohort r9FixtureCohort(R9CanaryProfile profile,SetupMeter m) throws Exception {
    if (!r9FixtureProfile(profile) || m==null)
        throw new IOException("R9_FIXTURE_COHORT_INPUT");
    m.charge(1);
    java.util.ArrayList<String> cohortIds=new java.util.ArrayList<String>(1);
    m.copy(1);
    cohortIds.add("NET_A");
    m.charge(1);
    java.util.ArrayList<String> requested=new java.util.ArrayList<String>(profile.netCount);
    m.copy(profile.netCount);
    requested.add("NET_A");
    if (profile==R9CanaryProfile.FAILED_FIRST_OPEN) {
        requested.add("NET_B");
    } else {
        requested.add("BLOCK_W_NET");
        requested.add("BLOCK_S_NET");
        requested.add("BLOCK_N_NET");
        requested.add("BLOCK_E_NET");
        requested.add("NET_B");
    }
    m.charge(1);
    return new Cohort(cohortIds,requested);
}

    static boolean terminalReasonEquals(SetupMeter m,String net,String reason,final Object actual) throws Exception {
        m.copy(1); // detached comparison string
        final String combined=net+":"+reason;
        return test(m,new Guard() { public boolean evaluate() throws Exception { return combined.equals(actual); } });
    }
    static boolean containsMetered(SetupMeter m,List<?> values,final Object value) throws Exception {
        for (final Object item:values) if (test(m,new Guard() { public boolean evaluate() throws Exception { return java.util.Objects.equals(item,value); } })) return true;
        return false;
    }
    @SuppressWarnings("unchecked")
    static void validateObservation(Map<String,Object> run,final Map<String,Object> obs,
            final List<String> requested,SetupMeter m) throws Exception {
        if (test(m,new Guard() { public boolean evaluate() throws Exception {
                return obs==null || obs.size()!=18 ||
                    !"tsj-cohort-terminal-observation-v2".equals(obs.get("schema")) ||
                    !obs.containsKey("selectedHead");
            } })) throw new IOException("OBSERVER_V2_SCHEMA");
        final Map<String,Object> outcome=(Map<String,Object>)run.get("outcome");
        final Map<String,Object> work=(Map<String,Object>)run.get("work");
        final Map<String,Object> ordering=(Map<String,Object>)run.get("ordering");
        final int callbacks=((Number)obs.get("callbackCount")).intValue();
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return callbacks>((Number)work.get("advanceCalls")).intValue(); } }) ||
            test(m,new Guard() { public boolean evaluate() throws Exception { return callbacks>247; } })) throw new IOException("OBSERVER_CALLBACK_COUNT");
        final String state=(String)obs.get("state"), disposition=(String)outcome.get("disposition");
        final List<?> traversed=(List<?>)ordering.get("traversedNetIds");
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return callbacks>0; } })) {
            final int first=((Number)obs.get("firstNetOrdinal")).intValue();
            final int last=((Number)obs.get("lastNetOrdinal")).intValue();
            final int total=((Number)obs.get("lastCallbackTotalExpanded")).intValue();
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return first<1; } }) || test(m,new Guard() { public boolean evaluate() throws Exception { return last<first; } }) ||
                test(m,new Guard() { public boolean evaluate() throws Exception { return last>requested.size(); } }) ||
                !containsMetered(m,traversed,requested.get(first-1)) ||
                !containsMetered(m,traversed,requested.get(last-1)) ||
                test(m,new Guard() { public boolean evaluate() throws Exception { return total<0; } }) || test(m,new Guard() { public boolean evaluate() throws Exception { return total>((Number)outcome.get("expanded")).intValue(); } }))
                throw new IOException("OBSERVER_TRAVERSAL_BINDING");
        }
        final Object selected=obs.get("selectedSnapshot");
        m.charge(1); // selected-head reference inspection
        final Object selectedHead=obs.get("selectedHead");
        if (test(m,new Guard() { public boolean evaluate() throws Exception {
                return selectedHead!=null && (!(selectedHead instanceof Map) ||
                    ((Map<?,?>)selectedHead).size()!=10);
            } }) || test(m,new Guard() { public boolean evaluate() throws Exception {
                return (selectedHead!=null)!=
                    ("FIRST_NON_FOUND".equals(state) && selected instanceof Map &&
                     "BRANCH_SEARCH_LIMIT".equals(((Map<?,?>)selected).get("terminalReason")));
            } })) throw new IOException("OBSERVER_SELECTED_HEAD_STATE");
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return "NO_FAILURE".equals(state); } })) {
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return !"FOUND".equals(disposition); } }) || test(m,new Guard() { public boolean evaluate() throws Exception { return selected!=null; } }))
                throw new IOException("OBSERVER_MISSING_FAILURE");
        } else if (test(m,new Guard() { public boolean evaluate() throws Exception { return "NON_BRANCH_REJECTION".equals(state); } })) {
            final String reason=(String)outcome.get("reason");
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return !"QUALITY_REJECTED".equals(disposition); } }) || test(m,new Guard() { public boolean evaluate() throws Exception { return selected!=null; } }) ||
                (!test(m,new Guard() { public boolean evaluate() throws Exception { return "QUALITY_LIMIT".equals(reason); } }) &&
                 !test(m,new Guard() { public boolean evaluate() throws Exception { return reason.endsWith(":BRANCH_QUALITY_LIMIT"); } })))
                throw new IOException("OBSERVER_QUALITY");
            final Map<String,Object> terminal=(Map<String,Object>)run.get("terminalFailure");
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return "QUALITY_LIMIT".equals(reason); } })) {
                if (test(m,new Guard() { public boolean evaluate() throws Exception { return terminal!=null; } })) throw new IOException("OBSERVER_QUALITY_PROOF");
            } else {
                m.copy(1); // detached substring, before construction
                final String net=reason.substring(0,reason.length()-":BRANCH_QUALITY_LIMIT".length());
                if (test(m,new Guard() { public boolean evaluate() throws Exception { return callbacks<1; } }) || test(m,new Guard() { public boolean evaluate() throws Exception { return terminal==null; } }) ||
                    test(m,new Guard() { public boolean evaluate() throws Exception { return !net.equals(terminal.get("failedNetId")); } }) ||
                    test(m,new Guard() { public boolean evaluate() throws Exception { return !reason.equals(terminal.get("ownerReason")); } }) ||
                    test(m,new Guard() { public boolean evaluate() throws Exception { return traversed.isEmpty(); } }) ||
                    test(m,new Guard() { public boolean evaluate() throws Exception { return !net.equals(traversed.get(traversed.size()-1)); } }) ||
                    test(m,new Guard() { public boolean evaluate() throws Exception { return !net.equals(requested.get(((Number)obs.get("lastNetOrdinal")).intValue()-1)); } }) ||
                    test(m,new Guard() { public boolean evaluate() throws Exception { return !obs.get("lastCallbackTotalExpanded").equals(outcome.get("expanded")); } }))
                    throw new IOException("OBSERVER_QUALITY_PROOF");
            }
        } else if (test(m,new Guard() { public boolean evaluate() throws Exception { return "FIRST_NON_FOUND".equals(state); } })) {
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return !(selected instanceof Map); } })) throw new IOException("OBSERVER_SELECTED");
            final Map<String,Object> snapshot=(Map<String,Object>)selected;
            final String net=(String)snapshot.get("netId"), reason=(String)snapshot.get("terminalReason");
            final int ordinal=((Number)obs.get("lastNetOrdinal")).intValue();
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return ordinal<1; } }) || test(m,new Guard() { public boolean evaluate() throws Exception { return ordinal>requested.size(); } }) ||
                test(m,new Guard() { public boolean evaluate() throws Exception { return !requested.get(ordinal-1).equals(net); } }) ||
                test(m,new Guard() { public boolean evaluate() throws Exception { return !Integer.valueOf(2).equals(snapshot.get("pass")); } }) ||
                test(m,new Guard() { public boolean evaluate() throws Exception { return !snapshot.get("totalExpanded").equals(outcome.get("expanded")); } }) ||
                test(m,new Guard() { public boolean evaluate() throws Exception { return !snapshot.get("totalExpanded").equals(obs.get("lastCallbackTotalExpanded")); } }) ||
                !terminalReasonEquals(m,net,reason,outcome.get("reason")) ||
                !containsMetered(m,traversed,net)) throw new IOException("OBSERVER_TERMINAL_BINDING");
            final Map<String,Object> terminal=(Map<String,Object>)run.get("terminalFailure");
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return terminal!=null; } }) &&
                (test(m,new Guard() { public boolean evaluate() throws Exception { return !net.equals(terminal.get("failedNetId")); } }) ||
                 test(m,new Guard() { public boolean evaluate() throws Exception { return !outcome.get("reason").equals(terminal.get("ownerReason")); } })))
                throw new IOException("OBSERVER_TERMINAL_PROOF");
        } else throw new IOException("OBSERVER_STATE");
    }
    static boolean equalMetered(SetupMeter m,final Object a,final Object b,final int depth) throws Exception {
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return depth>64; } })) throw new IOException("PARITY_DEPTH");
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return a==b; } })) return true;
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return a==null || b==null; } })) return false;
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return a instanceof Map && b instanceof Map; } })) {
            final Map<?,?> left=(Map<?,?>)a,right=(Map<?,?>)b;
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return left.size()!=right.size(); } })) return false;
            for (final Map.Entry<?,?> e:left.entrySet()) {
                if (!test(m,new Guard() { public boolean evaluate() throws Exception { return right.containsKey(e.getKey()); } }) ||
                    !equalMetered(m,e.getValue(),right.get(e.getKey()),depth+1)) return false;
            }
            return true;
        }
        if (test(m,new Guard() { public boolean evaluate() throws Exception { return a instanceof List && b instanceof List; } })) {
            final List<?> left=(List<?>)a,right=(List<?>)b;
            if (test(m,new Guard() { public boolean evaluate() throws Exception { return left.size()!=right.size(); } })) return false;
            for (int i=0;i<left.size();i++)
                if (!equalMetered(m,left.get(i),right.get(i),depth+1)) return false;
            return true;
        }
        return test(m,new Guard() { public boolean evaluate() throws Exception { return a.equals(b); } });
    }
    @SuppressWarnings("unchecked")
    static boolean parity(Object nullFresh,Object observeFresh,SetupMeter m) throws Exception {
        Map<String,Object> a=(Map<String,Object>)field(nullFresh,"json");
        Map<String,Object> b=(Map<String,Object>)field(observeFresh,"json");
        m.copy(6);
        String[] keys={"initial","ordering","outcome","terminalFailure","finalizers","layout"};
        for (String key:keys) if (!equalMetered(m,a.get(key),b.get(key),0)) return false;
        final Map<String,Object> aw=(Map<String,Object>)a.get("work"),bw=(Map<String,Object>)b.get("work");
        return test(m,new Guard() { public boolean evaluate() throws Exception { return aw.get("advanceCalls").equals(bw.get("advanceCalls")); } });
    }
    @SuppressWarnings("unchecked")
    static boolean finiteReference(Object fresh,SetupMeter m) throws Exception {
        Map<String,Object> run=(Map<String,Object>)field(fresh,"json");
        final Map<String,Object> outcome=(Map<String,Object>)run.get("outcome");
        final Map<String,Object> ordering=(Map<String,Object>)run.get("ordering");
        return test(m,new Guard() { public boolean evaluate() throws Exception { return "CAP_REJECTED".equals(outcome.get("disposition")); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return "D_COIL_LOW:BRANCH_SEARCH_LIMIT".equals(outcome.get("reason")); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return Integer.valueOf(419905).equals(outcome.get("expanded")); } }) &&
            test(m,new Guard() { public boolean evaluate() throws Exception { return ((List<?>)ordering.get("traversedNetIds")).size()==43; } });
    }
    @SuppressWarnings("unchecked")
    static Comparison assemble(Arguments args,long started) throws Exception {
        throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");
    }

    /** Structural minimum and separately metered logical traversal work. */
    static final class ResultTreeAccounting {
        long minimumUnits, traversalUnits, visits;
    }
    static ResultTreeAccounting accountResultTree(Object value,SetupMeter meter) throws Exception {
        meter.charge(1); // bookkeeping allocation, before creating the ledger
        ResultTreeAccounting accounting=new ResultTreeAccounting();
        accounting.traversalUnits=1;
        accountResultValue(value,meter,accounting,0);
        return accounting;
    }
    static void treeMinimum(SetupMeter m,ResultTreeAccounting accounting,long units) throws Exception {
        while (units>0) {
            long part=Math.min(units,32768L);
            m.charge(part);
            accounting.minimumUnits+=part;
            units-=part;
        }
    }
    static void treeTraversal(SetupMeter m,ResultTreeAccounting accounting) throws Exception {
        m.charge(1);
        accounting.traversalUnits++;
    }
    /** Count UTF-8 bytes without allocating; malformed surrogate code units use '?' (one byte). */
    static long treeUtf8Length(String value,SetupMeter m,ResultTreeAccounting accounting) throws Exception {
        long bytes=0;
        boolean high=false;
        int i=0;
        while (true) {
            treeTraversal(m,accounting);
            if (i>=value.length()) break;
            treeTraversal(m,accounting);
            char c=value.charAt(i++);
            treeTraversal(m,accounting);
            if (high) {
                treeTraversal(m,accounting);
                if (Character.isLowSurrogate(c)) { bytes+=4; high=false; continue; }
                bytes++; high=false;
            }
            treeTraversal(m,accounting);
            if (Character.isHighSurrogate(c)) { high=true; continue; }
            treeTraversal(m,accounting);
            if (Character.isLowSurrogate(c)) { bytes++; continue; }
            treeTraversal(m,accounting);
            if (c<0x80) { bytes++; continue; }
            treeTraversal(m,accounting);
            if (c<0x800) { bytes+=2; continue; }
            bytes+=3;
        }
        treeTraversal(m,accounting);
        if (high) bytes++;
        return bytes;
    }
    static long treeUtf8Chunks(String value,SetupMeter m,ResultTreeAccounting accounting) throws Exception {
        long bytes=treeUtf8Length(value,m,accounting);
        return bytes/4096L+(bytes%4096L==0?0:1);
    }
    static void accountResultValue(Object value,SetupMeter m,ResultTreeAccounting accounting,int depth)
            throws Exception {
        treeTraversal(m,accounting);
        if (depth>64) throw new IOException("RESULT_TREE_DEPTH");
        treeTraversal(m,accounting);
        if (accounting.visits>=1048576L) throw new IOException("RESULT_TREE_VISITS");
        accounting.visits++;
        treeMinimum(m,accounting,1);
        treeTraversal(m,accounting);
        if (value instanceof Map) {
            treeTraversal(m,accounting);
            java.util.Iterator<? extends Map.Entry<?,?>> it=((Map<?,?>)value).entrySet().iterator();
            while (true) {
                treeTraversal(m,accounting);
                if (!it.hasNext()) break;
                treeTraversal(m,accounting);
                Map.Entry<?,?> entry=it.next();
                treeTraversal(m,accounting);
                Object key=entry.getKey();
                treeTraversal(m,accounting);
                if (!(key instanceof String)) throw new IOException("RESULT_TREE_KEY");
                long chunks=treeUtf8Chunks((String)key,m,accounting);
                treeMinimum(m,accounting,1L+chunks);
                treeTraversal(m,accounting);
                Object child=entry.getValue();
                accountResultValue(child,m,accounting,depth+1);
            }
            return;
        }
        treeTraversal(m,accounting);
        if (value instanceof List) {
            treeTraversal(m,accounting);
            java.util.Iterator<?> it=((List<?>)value).iterator();
            while (true) {
                treeTraversal(m,accounting);
                if (!it.hasNext()) break;
                treeTraversal(m,accounting);
                Object child=it.next();
                accountResultValue(child,m,accounting,depth+1);
            }
            return;
        }
        treeTraversal(m,accounting);
        if (value instanceof String) {
            treeMinimum(m,accounting,treeUtf8Chunks((String)value,m,accounting));
            return;
        }
        treeTraversal(m,accounting);
        if (value==null) return;
        treeTraversal(m,accounting);
        if (value instanceof Number) return;
        treeTraversal(m,accounting);
        if (value instanceof Boolean) return;
        throw new IOException("RESULT_TREE_VALUE");
    }

    /** Reject generated trees with unexpected node types, cycles, and slot counts. */
    static int countData(Object value,Set<Object> containers,int depth) throws IOException {
        if (depth>32) throw new IOException("INPUT_DEPTH");
        if (value instanceof Map) {
            if (!containers.add(value)) throw new IOException("INPUT_ALIAS");
            int count=1;
            for (Map.Entry<?,?> e:((Map<?,?>)value).entrySet()) {
                if (!(e.getKey() instanceof String)) throw new IOException("INPUT_KEY");
                ascii((String)e.getKey());
                count=add(count,1); count=add(count,countData(e.getValue(),containers,depth+1));
            }
            return count;
        }
        if (value instanceof List) {
            if (!containers.add(value)) throw new IOException("INPUT_ALIAS");
            int count=1;
            for (Object item:(List<?>)value) {
                count=add(count,1); count=add(count,countData(item,containers,depth+1));
            }
            return count;
        }
        if (value instanceof String) ascii((String)value);
        else if (value!=null && !(value instanceof Integer) && !(value instanceof Long) &&
                !(value instanceof Boolean)) throw new IOException("INPUT_VALUE");
        return 0;
    }
    static int countData(Object value) throws IOException {
        return countData(value,java.util.Collections.newSetFromMap(
            new java.util.IdentityHashMap<Object,Boolean>()),0);
    }
    private static int add(int a,int b) throws IOException {
        if (a>4096-b) throw new IOException("INPUT_ENTRIES"); return a+b;
    }
    private static void ascii(String value) throws IOException {
        if (value.length()>256) throw new IOException("INPUT_STRING_LENGTH");
        for (int i=0;i<value.length();i++)
            if (value.charAt(i)<32 || value.charAt(i)>126) throw new IOException("INPUT_ASCII");
    }
/*
 * Private nested-class fragment for CohortTerminalTelemetryStudy.
 * The enclosing adapter owns reflection, runFresh wiring, source-bound advance proofs and JSON.
 */
static interface TerminalObserverMeter {
    boolean isOpen();
    long units();
    long groupUnits();
    long copiedEntries();
    long copyChunks();
    void charge(long units);
    void beginCopyChunk(int entries);
    void copyEntry();
}

static final class PadIdentity {
    final String netId;
    final int x, y;
    PadIdentity(String netId, int x, int y) { this.netId = netId; this.x = x; this.y = y; }
}

/** Immutable request limits and precharged defensive lookup copies. */
static final class ObservationSpec {
    static final int PAD_LIMIT = 246, NET_LIMIT = 60, ID_LIMIT = 256;
    final String requestId;
    final int expectedPass, padCount, netCount, maxCallbacks;
    final java.util.Map<String, PadIdentity> padsById;
    final java.util.Map<String, Integer> netOrdinals, netPadCounts;

    private ObservationSpec(String requestId, int pass, int pads, int nets,
            java.util.Map<String, PadIdentity> padMap,
            java.util.Map<String, Integer> ordinals, java.util.Map<String, Integer> counts) {
        this.requestId = requestId; this.expectedPass = pass;
        this.padCount = pads; this.netCount = nets; this.maxCallbacks = pads + 1;
        this.padsById = padMap; this.netOrdinals = ordinals; this.netPadCounts = counts;
    }

    static ObservationSpec create(TerminalObserverMeter meter, String requestId, int pass,
            int padCount, int netCount, java.util.Map<String, PadIdentity> sourcePads,
            java.util.Map<String, Integer> sourceOrdinals,
            java.util.Map<String, Integer> sourceCounts) {
        setup(meter);
        if (requestId == null) throw new IllegalArgumentException("Missing request id");
        auditId(meter, requestId);
        setup(meter);
        if (pass != 2) throw new IllegalArgumentException("Invalid pass");
        setup(meter);
        if (padCount < 1 || padCount > PAD_LIMIT) throw new IllegalArgumentException("Invalid pad census");
        setup(meter);
        if (netCount < 1 || netCount > NET_LIMIT) throw new IllegalArgumentException("Invalid net census");

        java.util.Map<String, PadIdentity> pads = copyPads(meter, sourcePads, padCount);
        boolean[] seen = new boolean[netCount + 1];
        java.util.Map<String, Integer> ordinals =
            copyInts(meter, sourceOrdinals, netCount, netCount, true, seen);
        for (int i = 1; i < seen.length; i++) {
            setup(meter);
            if (!seen[i]) throw new IllegalArgumentException("Net ordinal gap");
        }
        java.util.Map<String, Integer> counts =
            copyInts(meter, sourceCounts, netCount, padCount, false, null);
        verifyCensus(meter, pads, ordinals, counts, padCount, netCount);
        return new ObservationSpec(requestId, pass, padCount, netCount, pads, ordinals, counts);
    }

    private static void setup(TerminalObserverMeter meter) {
        if (meter == null || !meter.isOpen()) throw new IllegalStateException("Setup meter is closed");
        meter.charge(1);
    }

    private static void setupChunk(TerminalObserverMeter meter, int entries) {
        setup(meter);
        long copied=meter.copiedEntries();
        if (entries < 1 || entries > 4096 || copied < 0 || copied > 1048576L-entries)
            throw new IllegalStateException("Setup copy capacity exceeded");
        setup(meter);
        long chunks=meter.copyChunks();
        if (chunks < 0 || chunks == Long.MAX_VALUE) throw new IllegalStateException("Setup chunk overflow");
        meter.beginCopyChunk(entries);
    }
    private static void setupCopyEntry(TerminalObserverMeter meter) {
        setup(meter);
        meter.copyEntry();
    }
    private static void auditId(TerminalObserverMeter meter, String value) {
        setup(meter);
        if (value == null) throw new IllegalArgumentException("Null identity");
        setup(meter);
        int length = value.length();
        if (length < 1 || length > ID_LIMIT) throw new IllegalArgumentException("Identity length");
        setup(meter);
        for (int i = 0; i < length; i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) throw new IllegalArgumentException("Identity is not ASCII");
        }
    }

    private static java.util.Map<String, PadIdentity> copyPads(TerminalObserverMeter meter,
            java.util.Map<String, PadIdentity> source, int expected) {
        setup(meter);
        if (source == null) throw new IllegalArgumentException("Missing pad identities");
        setup(meter);
        int size = source.size();
        if (size != expected) throw new IllegalArgumentException("Pad identity census mismatch");
        setupChunk(meter, size);
        java.util.Map<String, PadIdentity> copy =
            new java.util.LinkedHashMap<String, PadIdentity>(size * 2 + 1);
        java.util.Iterator<java.util.Map.Entry<String, PadIdentity>> it =
            source.entrySet().iterator();
        while (true) {
            setup(meter);
            if (!it.hasNext()) break;
            setup(meter);
            java.util.Map.Entry<String, PadIdentity> e = it.next();
            setup(meter);
            String padId = e.getKey();
            auditId(meter, padId);
            setup(meter);
            PadIdentity p = e.getValue();
            if (p == null) throw new IllegalArgumentException("Missing pad identity value");
            setup(meter);
            String netId = p.netId;
            auditId(meter, netId);
            setup(meter);
            int x = p.x;
            setup(meter);
            int y = p.y;
            setup(meter);
            setupCopyEntry(meter);
            copy.put(padId, new PadIdentity(netId, x, y));
        }
        setup(meter);
        if (copy.size() != expected) throw new IllegalArgumentException("Pad copy mismatch");
        setup(meter);
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static java.util.Map<String, Integer> copyInts(TerminalObserverMeter meter,
            java.util.Map<String, Integer> source, int expected, int upper,
            boolean ordinal, boolean[] seen) {
        setup(meter);
        if (source == null) throw new IllegalArgumentException("Missing integer map");
        setup(meter);
        int size = source.size();
        if (size != expected) throw new IllegalArgumentException("Integer map census mismatch");
        setupChunk(meter, size);
        java.util.Map<String, Integer> copy =
            new java.util.LinkedHashMap<String, Integer>(size * 2 + 1);
        java.util.Iterator<java.util.Map.Entry<String, Integer>> it = source.entrySet().iterator();
        while (true) {
            setup(meter);
            if (!it.hasNext()) break;
            setup(meter);
            java.util.Map.Entry<String, Integer> e = it.next();
            setup(meter);
            String key = e.getKey();
            auditId(meter, key);
            setup(meter);
            Integer boxed = e.getValue();
            if (boxed == null) throw new IllegalArgumentException("Missing integer value");
            setup(meter);
            int value = boxed.intValue();
            if (value < 1 || value > upper)
                throw new IllegalArgumentException("Integer map value out of range");
            if (ordinal) {
                setup(meter);
                if (seen[value]) throw new IllegalArgumentException("Duplicate net ordinal");
                seen[value] = true;
            }
            setup(meter);
            setupCopyEntry(meter);
            copy.put(key, Integer.valueOf(value));
        }
        setup(meter);
        if (copy.size() != expected) throw new IllegalArgumentException("Integer map copy mismatch");
        setup(meter);
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static void verifyCensus(TerminalObserverMeter meter,
            java.util.Map<String, PadIdentity> pads, java.util.Map<String, Integer> ordinals,
            java.util.Map<String, Integer> counts, int padCount, int netCount) {
        setupChunk(meter, netCount + padCount);
        java.util.Map<String, Integer> actual = new java.util.LinkedHashMap<String, Integer>(netCount * 2 + 1);
        java.util.Iterator<String> netIt=counts.keySet().iterator();
        while (true) {
            setup(meter); if (!netIt.hasNext()) break;
            setup(meter); String net=netIt.next();
            setup(meter); if (!ordinals.containsKey(net)) throw new IllegalArgumentException("Count net lacks ordinal");
            setupCopyEntry(meter); actual.put(net, Integer.valueOf(0));
        }
        java.util.Iterator<java.util.Map.Entry<String, PadIdentity>> padIt=pads.entrySet().iterator();
        int seenPads=0;
        while (true) {
            setup(meter); if (!padIt.hasNext()) break;
            setup(meter); java.util.Map.Entry<String, PadIdentity> e=padIt.next();
            setup(meter); PadIdentity p=e.getValue();
            setup(meter); if (ordinals.get(p.netId) == null) throw new IllegalArgumentException("Pad net is not requested");
            setup(meter); Integer old=actual.get(p.netId);
            if (old == null) throw new IllegalArgumentException("Pad net lacks count");
            int next=old.intValue()+1;
            if (next > padCount) throw new IllegalArgumentException("Pad census overflow");
            setupCopyEntry(meter); actual.put(p.netId, Integer.valueOf(next));
            seenPads++;
        }
        setup(meter); if (seenPads != padCount) throw new IllegalArgumentException("Pad total mismatch");
        java.util.Iterator<java.util.Map.Entry<String, Integer>> it=counts.entrySet().iterator();
        int declaredTotal=0;
        while (true) {
            setup(meter); if (!it.hasNext()) break;
            setup(meter); java.util.Map.Entry<String, Integer> e=it.next();
            setup(meter); Integer actualCount=actual.get(e.getKey());
            setup(meter); Integer declared=e.getValue();
            if (actualCount == null || actualCount.intValue() != declared.intValue())
                throw new IllegalArgumentException("Net pad census mismatch");
            declaredTotal += declared.intValue();
        }
        setup(meter); if (declaredTotal != padCount) throw new IllegalArgumentException("Declared pad total mismatch");
    }
}
static final class DetachedTerminal {
    final int pass;
    final String netId, startPadId, rootPadId;
    final int startX, startY, rootX, rootY, gridWidth, gridHeight;
    final int goalsTop, goalsBottom, prefixViaCount, reachedPadCount;
    final int initialManhattanDistance, minimumExploredManhattanDistance;
    final int branchExpanded, totalExpanded, expandedTop, expandedBottom;
    final int frontierCount, bestStateCount;
    final String terminalReason;
    final boolean relaxedBoundStarted, relaxedBoundComplete, relaxedBoundObservationChecked;
    final boolean relaxedBoundLiveLayoutStable, relaxedBoundRouterStateStable;
    final boolean relaxedBoundRouterEffectsStable;
    final int relaxedBoundWorkUnits, relaxedBoundOrdinaryProbes;
    final int relaxedBoundViaProbes, relaxedBoundRefreshes;

    DetachedTerminal(int pass, String netId, String startPadId, String rootPadId,
            int startX, int startY, int rootX, int rootY, int gridWidth, int gridHeight,
            int goalsTop, int goalsBottom, int prefixViaCount, int reachedPadCount,
            int initialDistance, int minimumDistance, int branchExpanded, int totalExpanded,
            int expandedTop, int expandedBottom, int frontierCount, int bestStateCount,
            String reason, boolean started, boolean complete, boolean observed,
            boolean layoutStable, boolean routerStable, boolean effectsStable,
            int work, int ordinaryProbes, int viaProbes, int refreshes) {
        this.pass=pass; this.netId=netId; this.startPadId=startPadId; this.rootPadId=rootPadId;
        this.startX=startX; this.startY=startY; this.rootX=rootX; this.rootY=rootY;
        this.gridWidth=gridWidth; this.gridHeight=gridHeight;
        this.goalsTop=goalsTop; this.goalsBottom=goalsBottom;
        this.prefixViaCount=prefixViaCount; this.reachedPadCount=reachedPadCount;
        this.initialManhattanDistance=initialDistance; this.minimumExploredManhattanDistance=minimumDistance;
        this.branchExpanded=branchExpanded; this.totalExpanded=totalExpanded;
        this.expandedTop=expandedTop; this.expandedBottom=expandedBottom;
        this.frontierCount=frontierCount; this.bestStateCount=bestStateCount;
        this.terminalReason=reason; this.relaxedBoundStarted=started;
        this.relaxedBoundComplete=complete; this.relaxedBoundObservationChecked=observed;
        this.relaxedBoundLiveLayoutStable=layoutStable; this.relaxedBoundRouterStateStable=routerStable;
        this.relaxedBoundRouterEffectsStable=effectsStable; this.relaxedBoundWorkUnits=work;
        this.relaxedBoundOrdinaryProbes=ordinaryProbes; this.relaxedBoundViaProbes=viaProbes;
        this.relaxedBoundRefreshes=refreshes;
    }

    void writeResult(java.util.Map<String, Object> out, String p) {
        out.put(p+"pass",Integer.valueOf(pass));
        out.put(p+"netId",netId);
        out.put(p+"startPadId",startPadId);
        out.put(p+"rootPadId",rootPadId);
        out.put(p+"startX",Integer.valueOf(startX));
        out.put(p+"startY",Integer.valueOf(startY));
        out.put(p+"rootX",Integer.valueOf(rootX));
        out.put(p+"rootY",Integer.valueOf(rootY));
        out.put(p+"gridWidth",Integer.valueOf(gridWidth));
        out.put(p+"gridHeight",Integer.valueOf(gridHeight));
        out.put(p+"goalsTop",Integer.valueOf(goalsTop));
        out.put(p+"goalsBottom",Integer.valueOf(goalsBottom));
        out.put(p+"prefixViaCount",Integer.valueOf(prefixViaCount));
        out.put(p+"reachedPadCount",Integer.valueOf(reachedPadCount));
        out.put(p+"initialManhattanDistance",Integer.valueOf(initialManhattanDistance));
        out.put(p+"minimumExploredManhattanDistance",Integer.valueOf(minimumExploredManhattanDistance));
        out.put(p+"branchExpanded",Integer.valueOf(branchExpanded));
        out.put(p+"totalExpanded",Integer.valueOf(totalExpanded));
        out.put(p+"expandedTop",Integer.valueOf(expandedTop));
        out.put(p+"expandedBottom",Integer.valueOf(expandedBottom));
        out.put(p+"frontierCount",Integer.valueOf(frontierCount));
        out.put(p+"bestStateCount",Integer.valueOf(bestStateCount));
        out.put(p+"terminalReason",terminalReason);
        out.put(p+"relaxedBoundStarted",Boolean.valueOf(relaxedBoundStarted));
        out.put(p+"relaxedBoundComplete",Boolean.valueOf(relaxedBoundComplete));
        out.put(p+"relaxedBoundObservationChecked",Boolean.valueOf(relaxedBoundObservationChecked));
        out.put(p+"relaxedBoundLiveLayoutStable",Boolean.valueOf(relaxedBoundLiveLayoutStable));
        out.put(p+"relaxedBoundRouterStateStable",Boolean.valueOf(relaxedBoundRouterStateStable));
        out.put(p+"relaxedBoundRouterEffectsStable",Boolean.valueOf(relaxedBoundRouterEffectsStable));
        out.put(p+"relaxedBoundWorkUnits",Integer.valueOf(relaxedBoundWorkUnits));
        out.put(p+"relaxedBoundOrdinaryProbes",Integer.valueOf(relaxedBoundOrdinaryProbes));
        out.put(p+"relaxedBoundViaProbes",Integer.valueOf(relaxedBoundViaProbes));
        out.put(p+"relaxedBoundRefreshes",Integer.valueOf(relaxedBoundRefreshes));
    }
}

static final class DetachedHead {
    final int branchOrdinal,cell,layer,direction,transitions,cost,storedHeuristic,
        serial,key,storedPriority;
    DetachedHead(int branchOrdinal,int cell,int layer,int direction,int transitions,int cost,
            int storedHeuristic,int serial,int key,int storedPriority) {
        this.branchOrdinal=branchOrdinal; this.cell=cell; this.layer=layer;
        this.direction=direction; this.transitions=transitions; this.cost=cost;
        this.storedHeuristic=storedHeuristic; this.serial=serial; this.key=key;
        this.storedPriority=storedPriority;
    }
    void writeResult(java.util.Map<String,Object> out) {
        out.put("branchOrdinal",Integer.valueOf(branchOrdinal));
        out.put("cell",Integer.valueOf(cell));
        out.put("layer",Integer.valueOf(layer));
        out.put("direction",Integer.valueOf(direction));
        out.put("transitions",Integer.valueOf(transitions));
        out.put("cost",Integer.valueOf(cost));
        out.put("storedHeuristic",Integer.valueOf(storedHeuristic));
        out.put("serial",Integer.valueOf(serial));
        out.put("key",Integer.valueOf(key));
        out.put("storedPriority",Integer.valueOf(storedPriority));
    }
}

/** No owner references; callbackGroupSamples counts samples, not unique parent groups. */
static final class TerminalTelemetryObserver {
    static final int FIELD_COUNT=33, CALLBACK_CEILING=512, POST_CALL_BOUND=104, GROUP_CAPACITY=32768;
    static final long COPY_CAPACITY=1048576L;
    private final TerminalObserverMeter meter;
    private final ObservationSpec spec;
    private boolean sealed, inCallback, poisoned;
    private DetachedTerminal selected;
    private DetachedHead selectedHead;
    private long dispatches, inspections, copiedSlots, copyChunks, checks, selections, releases;
    private long headCopies, headReleases;
    private long accepted, found, failures, unitsCharged, groupSamples, unitsMeasured;
    private long maxCallbackUnits, maxGroupUnits, lastGroupBefore=-1, lastGroupAfter=-1;
    private int firstOrdinal=-1, selectedCallbackOrdinal=0, lastOrdinal=-1, lastTotal=0;

    TerminalTelemetryObserver(TerminalObserverMeter meter, ObservationSpec spec) {
        if (meter == null || spec == null) throw new IllegalArgumentException("Missing observer input");
        this.meter=meter; this.spec=spec;
    }

    synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        observe(requestId,snapshot,null,null);
    }

    synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            TerminalObserverMeter staleRejectionMeter) {
        observe(requestId,snapshot,null,staleRejectionMeter);
    }

    synchronized void observeSelected(String requestId,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection head) {
        observe(requestId,snapshot,head,null);
    }

    synchronized void observeSelected(String requestId,
            PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection head,
            TerminalObserverMeter staleRejectionMeter) {
        observe(requestId,snapshot,head,staleRejectionMeter);
    }

    private synchronized void observe(String requestId, PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection suppliedHead,
            TerminalObserverMeter staleRejectionMeter) {
        if (sealed) { rejectStale(snapshot,suppliedHead,staleRejectionMeter); return; }
        PcbLayerRoutingPrototype.BranchSnapshot borrowed=snapshot;
        PcbLayerRoutingPrototype.BranchLimitSelection borrowedHead=suppliedHead;
        DetachedTerminal tuple=null, oldSelected=selected;
        DetachedHead headTuple=null, oldSelectedHead=selectedHead;
        Throwable failure=null;
        boolean committed=false, entered=false;
        int requiredFields=FIELD_COUNT+(borrowedHead==null?0:10);
        int requiredChunks=borrowedHead==null?1:2;
        long groupBefore=-1, unitsBefore=-1, oldAccepted=accepted, oldFound=found, oldFailures=failures;
        int oldOrdinal=lastOrdinal, oldTotal=lastTotal;
        int oldFirstOrdinal=firstOrdinal, oldSelectedOrdinal=selectedCallbackOrdinal;
        try {
            if (!meter.isOpen()) throw new IllegalStateException("Observer meter is closed");
            unitsBefore=meter.units(); groupBefore=meter.groupUnits();
            unit(); dispatches++;
            checkOpen();
            validation(); if (sealed) throw new IllegalStateException("Observer is sealed");
            validation(); if (borrowed == null) throw new IllegalArgumentException("Null terminal snapshot");
            validation(); if (countersNearOverflow(requiredFields,requiredChunks))
                throw new IllegalStateException("Observer counter overflow");
            validation(); if (dispatches > spec.maxCallbacks) throw new IllegalStateException("Callback limit exceeded");
            validation();
            long group=groupBefore;
            if (unitsBefore < 0 || group < 0 || group > GROUP_CAPACITY-CALLBACK_CEILING-POST_CALL_BOUND)
                throw new IllegalStateException("Insufficient callback group capacity");
            validation(); if (FIELD_COUNT != 33 || requiredFields > 4096)
                throw new IllegalStateException("Invalid tuple chunk size");
            validation();
            long copies=meter.copiedEntries();
            if (copies < 0 || copies > COPY_CAPACITY-requiredFields)
                throw new IllegalStateException("Aggregate copy capacity exceeded");
            validation();
            long chunks=meter.copyChunks();
            if (chunks < 0 || chunks > Integer.MAX_VALUE-requiredChunks ||
                    copyChunks < 0 || copyChunks > Long.MAX_VALUE-requiredChunks)
                throw new IllegalStateException("Copy chunk counter overflow");
            validation(); if (copiedSlots < 0 || copiedSlots > Long.MAX_VALUE-requiredFields)
                throw new IllegalStateException("Copied slot counter overflow");
            validation(); if (inCallback) throw new IllegalStateException("Nested callback");
            inCallback=true; entered=true;
            copyChunks++; meter.beginCopyChunk(FIELD_COUNT);
            tuple=detach(borrowed);
            if (borrowedHead!=null) {
                copyChunks++; meter.beginCopyChunk(10);
                headTuple=detachHead(borrowedHead);
                headCopies++;
            }
            int ordinal=validateTuple(tuple,requestId);
            validateSelection(tuple,headTuple);
            commit(tuple,ordinal,headTuple); committed=true;
        } catch (Throwable t) {
            failure=t;
        } finally {
            if (borrowed != null) {
                try { releases++; unit(); } catch (Throwable t) { failure=append(failure,t); }
                borrowed=null;
            }
            if (borrowedHead != null) {
                try { headReleases++; unit(); } catch (Throwable t) { failure=append(failure,t); }
                borrowedHead=null;
            }
            tuple=null; headTuple=null;
            if (entered) inCallback=false;
            if (unitsBefore >= 0 && groupBefore >= 0) {
                try {
                    long du=meter.units()-unitsBefore, dg=meter.groupUnits()-groupBefore;
                    if (du < 0 || dg < 0) throw new IllegalStateException("Meter moved backwards");
                    groupSamples++; unitsMeasured+=du;
                    if (du > maxCallbackUnits) maxCallbackUnits=du;
                    if (dg > maxGroupUnits) maxGroupUnits=dg;
                    lastGroupBefore=groupBefore; lastGroupAfter=groupBefore+dg;
                } catch (Throwable t) { failure=append(failure,t); }
            }
            if (failure != null) { poisoned=true; sealed=true; }
            if (failure != null && committed) {
                accepted=oldAccepted; found=oldFound; failures=oldFailures;
                firstOrdinal=oldFirstOrdinal; selectedCallbackOrdinal=oldSelectedOrdinal;
                lastOrdinal=oldOrdinal; lastTotal=oldTotal;
                selected=oldSelected; selectedHead=oldSelectedHead;
            }
        }
        if (failure != null) throwComponent(failure);
    }

    private void checkOpen() {
        validation();
        if (!meter.isOpen()) throw new IllegalStateException("Observer group is closed");
    }

    private void rejectStale(PcbLayerRoutingPrototype.BranchSnapshot snapshot,
            PcbLayerRoutingPrototype.BranchLimitSelection head,
            TerminalObserverMeter rejectionMeter) {
        PcbLayerRoutingPrototype.BranchSnapshot borrowed=snapshot;
        PcbLayerRoutingPrototype.BranchLimitSelection borrowedHead=head;
        Throwable primary=new IllegalStateException("Sealed observer rejects stale callback");
        try {
            if (rejectionMeter == null || !rejectionMeter.isOpen())
                throw new IllegalStateException("Stale callback requires separate rejection meter");
            rejectionMeter.charge(1);
        } catch (Throwable t) { primary=append(primary,t); }
        finally {
            if (borrowed != null) {
                try {
                    if (rejectionMeter == null || !rejectionMeter.isOpen())
                        throw new IllegalStateException("Cannot meter stale snapshot release");
                    rejectionMeter.charge(1);
                } catch (Throwable t) { primary=append(primary,t); }
                borrowed=null;
            }
            if (borrowedHead != null) {
                try {
                    if (rejectionMeter == null || !rejectionMeter.isOpen())
                        throw new IllegalStateException("Cannot meter stale head release");
                    rejectionMeter.charge(1);
                } catch (Throwable t) { primary=append(primary,t); }
                borrowedHead=null;
            }
        }
        throwComponent(primary);
    }

    synchronized void seal() { sealed=true; }

    synchronized java.util.Map<String, Object> resultMap() {
        if (!sealed) throw new IllegalStateException("Seal observer before result access");
        if (poisoned) throw new IllegalStateException("Observer request had an active rejection");
        // headCopies is the sole H counter; tuple copies include both DTOs in V2.
        if (inCallback || dispatches<0 || dispatches>spec.maxCallbacks ||
                accepted!=dispatches || found<0 || failures<0 ||
                found+failures!=dispatches || failures>1 || headCopies<0 || headCopies>1 ||
                copiedSlots!=33L*dispatches+10L*headCopies ||
                copyChunks!=dispatches+headCopies || headReleases!=headCopies ||
                releases!=dispatches || selections!=dispatches || inspections!=copiedSlots ||
                checks<dispatches || checks>94L*dispatches+12L*headCopies ||
                unitsCharged!=69L*dispatches+checks+21L*headCopies ||
                unitsMeasured!=unitsCharged || groupSamples!=dispatches ||
                (failures==0 && selected!=null) || (failures==1 && selected==null) ||
                (headCopies==1)!=(selectedHead!=null) ||
                (selectedHead!=null)!=(selected!=null &&
                    "BRANCH_SEARCH_LIMIT".equals(selected.terminalReason)))
            throw new IllegalStateException("Observer counters or selection are incomplete");
        if ((failures==1 && selectedCallbackOrdinal!=dispatches) ||
                (failures==0 && selectedCallbackOrdinal!=0))
            throw new IllegalStateException("Selected failure is not the final callback");

        java.util.Map<String,Object> out=new java.util.LinkedHashMap<String,Object>();
        out.put("schema","tsj-cohort-terminal-observation-v2");
        out.put("mode","OBSERVE");
        out.put("state",failures==0?"NO_FAILURE":"FIRST_NON_FOUND");
        out.put("callbackCount",Integer.valueOf((int)dispatches));
        out.put("foundCallbackCount",Integer.valueOf((int)found));
        out.put("nonFoundCallbackCount",Integer.valueOf((int)failures));
        out.put("firstNetOrdinal",accepted==0?null:Integer.valueOf(firstOrdinal));
        out.put("lastNetOrdinal",accepted==0?null:Integer.valueOf(lastOrdinal));
        out.put("lastCallbackTotalExpanded",accepted==0?null:Integer.valueOf(lastTotal));
        out.put("selectedCallbackOrdinal",failures==0?null:Integer.valueOf(selectedCallbackOrdinal));
        out.put("transientTupleCopies",Integer.valueOf((int)copyChunks));
        out.put("transientFieldCopies",Integer.valueOf((int)copiedSlots));
        out.put("validationCount",Integer.valueOf((int)checks));
        out.put("observerUnits",Integer.valueOf((int)unitsCharged));
        out.put("copiedEntries",Integer.valueOf((int)copiedSlots));
        out.put("copyChunks",Integer.valueOf((int)copyChunks));
        java.util.Map<String,Object> snapshot=null;
        if (selected!=null) {
            snapshot=new java.util.LinkedHashMap<String,Object>();
            selected.writeResult(snapshot,"");
            snapshot=java.util.Collections.unmodifiableMap(snapshot);
        }
        out.put("selectedSnapshot",snapshot);
        java.util.Map<String,Object> head=null;
        if (selectedHead!=null) {
            head=new java.util.LinkedHashMap<String,Object>();
            selectedHead.writeResult(head);
            head=java.util.Collections.unmodifiableMap(head);
        }
        out.put("selectedHead",head);
        return java.util.Collections.unmodifiableMap(out);
    }

    private void unit() { meter.charge(1); unitsCharged++; }
    private void validation() { unit(); checks++; }
    private boolean countersNearOverflow(int requiredFields,int requiredChunks) {
        return dispatches > Long.MAX_VALUE-512 || inspections > Long.MAX_VALUE-512 ||
            copiedSlots > Long.MAX_VALUE-requiredFields ||
            copyChunks > Long.MAX_VALUE-requiredChunks ||
            checks > Long.MAX_VALUE-512 || selections > Long.MAX_VALUE-512 ||
            releases > Long.MAX_VALUE-512 || headCopies > Long.MAX_VALUE-512 ||
            headReleases > Long.MAX_VALUE-512 || accepted > Long.MAX_VALUE-512 ||
            found > Long.MAX_VALUE-512 || failures > Long.MAX_VALUE-512 ||
            unitsCharged > Long.MAX_VALUE-512;
    }

    private void chargeReadCopy() {
        unit(); inspections++;
        unit(); copiedSlots++;
        meter.copyEntry();
    }

    private int readInt(PcbLayerRoutingPrototype.BranchSnapshot s, int f) {
        chargeReadCopy();
        switch (f) {
        case 0: return s.pass;
        case 4: return s.startX; case 5: return s.startY; case 6: return s.rootX; case 7: return s.rootY;
        case 8: return s.gridWidth; case 9: return s.gridHeight;
        case 10: return s.goalsTop; case 11: return s.goalsBottom;
        case 12: return s.prefixViaCount; case 13: return s.reachedPadCount;
        case 14: return s.initialManhattanDistance; case 15: return s.minimumExploredManhattanDistance;
        case 16: return s.branchExpanded; case 17: return s.totalExpanded;
        case 18: return s.expandedTop; case 19: return s.expandedBottom;
        case 20: return s.frontierCount; case 21: return s.bestStateCount;
        case 29: return s.relaxedBoundWorkUnits; case 30: return s.relaxedBoundOrdinaryProbes;
        case 31: return s.relaxedBoundViaProbes; case 32: return s.relaxedBoundRefreshes;
        default: throw new IllegalArgumentException("Invalid integer field");
        }
    }

    private String readString(PcbLayerRoutingPrototype.BranchSnapshot s, int f) {
        chargeReadCopy();
        switch (f) {
        case 1: return s.netId; case 2: return s.startPadId; case 3: return s.rootPadId;
        case 22: return s.terminalReason;
        default: throw new IllegalArgumentException("Invalid string field");
        }
    }

    private boolean readBoolean(PcbLayerRoutingPrototype.BranchSnapshot s, int f) {
        chargeReadCopy();
        switch (f) {
        case 23: return s.relaxedBoundStarted; case 24: return s.relaxedBoundComplete;
        case 25: return s.relaxedBoundObservationChecked; case 26: return s.relaxedBoundLiveLayoutStable;
        case 27: return s.relaxedBoundRouterStateStable; case 28: return s.relaxedBoundRouterEffectsStable;
        default: throw new IllegalArgumentException("Invalid boolean field");
        }
    }

    private DetachedTerminal detach(PcbLayerRoutingPrototype.BranchSnapshot s) {
        return new DetachedTerminal(readInt(s,0), readString(s,1), readString(s,2), readString(s,3),
            readInt(s,4), readInt(s,5), readInt(s,6), readInt(s,7), readInt(s,8), readInt(s,9),
            readInt(s,10), readInt(s,11), readInt(s,12), readInt(s,13), readInt(s,14), readInt(s,15),
            readInt(s,16), readInt(s,17), readInt(s,18), readInt(s,19), readInt(s,20), readInt(s,21),
            readString(s,22), readBoolean(s,23), readBoolean(s,24), readBoolean(s,25),
            readBoolean(s,26), readBoolean(s,27), readBoolean(s,28), readInt(s,29), readInt(s,30),
            readInt(s,31), readInt(s,32));
    }

    private int readHead(PcbLayerRoutingPrototype.BranchLimitSelection s,int f) {
        chargeReadCopy();
        switch (f) {
        case 0: return s.branchOrdinal;
        case 1: return s.cell;
        case 2: return s.layer;
        case 3: return s.direction;
        case 4: return s.transitions;
        case 5: return s.cost;
        case 6: return s.storedHeuristic;
        case 7: return s.serial;
        case 8: return s.key;
        case 9: return s.storedPriority;
        default: throw new IllegalArgumentException("Invalid selected-head field");
        }
    }

    private DetachedHead detachHead(PcbLayerRoutingPrototype.BranchLimitSelection s) {
        return new DetachedHead(readHead(s,0),readHead(s,1),readHead(s,2),readHead(s,3),
            readHead(s,4),readHead(s,5),readHead(s,6),readHead(s,7),readHead(s,8),readHead(s,9));
    }

    private int validateTuple(DetachedTerminal t, String requestId) {
        validation(); if (t.pass != spec.expectedPass) throw invalid("pass mismatch");
        checkId(t.netId); checkId(t.startPadId); checkId(t.rootPadId);
        validation(); Integer boxedOrdinal=spec.netOrdinals.get(t.netId);
        if (boxedOrdinal == null) throw invalid("net identity mismatch");
        int ordinal=boxedOrdinal.intValue();
        validation(); PadIdentity start=spec.padsById.get(t.startPadId);
        if (start == null) throw invalid("start pad identity mismatch");
        validation(); PadIdentity root=spec.padsById.get(t.rootPadId);
        if (root == null) throw invalid("root pad identity mismatch");
        validation(); if (t.startX != start.x) throw invalid("start x identity mismatch");
        validation(); if (t.startY != start.y) throw invalid("start y identity mismatch");
        validation(); if (t.rootX != root.x) throw invalid("root x identity mismatch");
        validation(); if (t.rootY != root.y) throw invalid("root y identity mismatch");

        validation(); if (t.gridWidth < 1) throw invalid("grid width below range");
        validation(); if (t.gridWidth > 250000) throw invalid("grid width above range");
        validation(); if (t.gridHeight < 1) throw invalid("grid height below range");
        validation(); if (t.gridHeight > 250000) throw invalid("grid height above range");
        long cells=(long)t.gridWidth*(long)t.gridHeight;
        validation(); if (t.goalsTop < 0) throw invalid("top goals below range");
        validation(); if ((long)t.goalsTop > cells) throw invalid("top goals above range");
        validation(); if (t.goalsBottom < 0) throw invalid("bottom goals below range");
        validation(); if ((long)t.goalsBottom > cells) throw invalid("bottom goals above range");
        validation(); if (t.prefixViaCount < 0) throw invalid("prefix vias below range");
        validation(); if (t.prefixViaCount > 48) throw invalid("prefix vias above range");
        validation(); if (t.reachedPadCount < 1) throw invalid("reached pads below range");
        validation(); Integer netPads=spec.netPadCounts.get(t.netId);
        if (netPads == null) throw invalid("net pad count missing");
        validation(); if (t.reachedPadCount > netPads.intValue())
            throw invalid("reached pads exceed net census");

        validation(); if (t.initialManhattanDistance < 0) throw invalid("initial distance below range");
        validation(); if (t.minimumExploredManhattanDistance < -1) throw invalid("minimum distance below range");
        validation(); if (t.minimumExploredManhattanDistance > t.initialManhattanDistance)
            throw invalid("minimum distance above initial");
        validation(); if (t.branchExpanded < 0) throw invalid("branch expansions below range");
        validation(); if (t.branchExpanded > 100000) throw invalid("branch expansions above range");
        validation(); if (t.totalExpanded < 0) throw invalid("total expansions below range");
        validation(); if (t.totalExpanded > 1000000) throw invalid("total expansions above range");
        validation(); if (t.expandedTop < 0) throw invalid("top expansions below range");
        validation(); if (t.expandedTop > t.branchExpanded) throw invalid("top expansions above total");
        validation(); if (t.expandedBottom < 0) throw invalid("bottom expansions below range");
        validation(); if (t.expandedBottom > t.branchExpanded) throw invalid("bottom expansions above total");
        validation(); if (t.frontierCount < 0) throw invalid("frontier below range");
        validation(); if (t.bestStateCount < 0) throw invalid("best states below range");
        checkId(t.terminalReason);
        validation(); int reason=reasonCode(t.terminalReason);
        if (reason == 0) throw invalid("terminal reason is unknown");
        validation(); if (t.relaxedBoundWorkUnits < 0) throw invalid("relaxed work below range");
        validation(); if (t.relaxedBoundOrdinaryProbes < 0) throw invalid("ordinary probes below range");
        validation(); if (t.relaxedBoundViaProbes < 0) throw invalid("via probes below range");
        validation(); if (t.relaxedBoundRefreshes < 0) throw invalid("refreshes below range");

        validation(); if (t.gridWidth*(long)t.gridHeight < 1 || t.gridWidth*(long)t.gridHeight > 250000L)
            throw invalid("grid product out of range");
        validation(); if ((long)t.goalsTop+(long)t.goalsBottom <= 0) throw invalid("terminal has no goals");
        validation(); if (!t.netId.equals(start.netId)) throw invalid("start net mismatch");
        validation(); if (!t.netId.equals(root.netId)) throw invalid("root net mismatch");
        validation(); if (t.totalExpanded < t.branchExpanded) throw invalid("total precedes branch");
        validation(); if ((long)t.expandedTop+t.expandedBottom != t.branchExpanded)
            throw invalid("layer expansion sum mismatch");
        validation(); if (!spec.requestId.equals(requestId)) throw invalid("request identity mismatch");
        validation(); if (ordinal < lastOrdinal) throw invalid("net ordinal moved backwards");
        validation(); if (t.totalExpanded < lastTotal) throw invalid("total expansions moved backwards");
        validation(); if (selected != null) throw invalid("callback followed terminal failure");
        validation(); if (reason == 4) { validation(); if (t.totalExpanded <= lastTotal)
            throw invalid("FOUND total did not increase"); }

        validation(); if (t.branchExpanded == 0) {
            validation(); if (t.minimumExploredManhattanDistance != -1)
                throw invalid("zero-expansion minimum mismatch");
        } else {
            validation(); if (t.minimumExploredManhattanDistance < 0)
                throw invalid("expanded branch lacks minimum distance");
        }
        validation(); boolean started=t.relaxedBoundStarted;
        if (started) { validation(); if (!t.relaxedBoundComplete) throw invalid("started bound incomplete"); }
        validation(); boolean complete=t.relaxedBoundComplete;
        validation(); boolean fullPrefix=t.prefixViaCount == 48;
        validation(); if (t.relaxedBoundObservationChecked != (complete & fullPrefix))
            throw invalid("observation flag mismatch");
        validation(); if (t.relaxedBoundLiveLayoutStable != t.relaxedBoundObservationChecked)
            throw invalid("layout stability mismatch");
        validation(); if (t.relaxedBoundRouterStateStable != t.relaxedBoundObservationChecked)
            throw invalid("router stability mismatch");
        validation(); if (t.relaxedBoundRouterEffectsStable != t.relaxedBoundObservationChecked)
            throw invalid("router effects mismatch");
        validation(); if (!t.relaxedBoundStarted) {
            validation(); if (t.relaxedBoundWorkUnits != 0) throw invalid("unstarted work is nonzero");
            validation(); if (t.relaxedBoundOrdinaryProbes != 0)
                throw invalid("unstarted ordinary probes are nonzero");
            validation(); if (t.relaxedBoundViaProbes != 0)
                throw invalid("unstarted via probes are nonzero");
            validation(); if (t.relaxedBoundRefreshes != 0)
            throw invalid("unstarted refreshes are nonzero"); }
        validation(); if (!reasonConsistent(t, reason)) throw invalid("reason contradicts snapshot");
        return ordinal;
    }

    private void validateSelection(DetachedTerminal t,DetachedHead h) {
        validation(); boolean branchLimit="BRANCH_SEARCH_LIMIT".equals(t.terminalReason);
        validation(); if (branchLimit!=(h!=null)) throw invalid("selected head presence mismatch");
        if (h==null) return;
        validation(); if (h.branchOrdinal<1) throw invalid("selected branch ordinal below range");
        int cells=t.gridWidth*t.gridHeight;
        validation(); if (h.cell<0 || h.cell>=cells) throw invalid("selected cell outside grid");
        validation(); if (h.layer<0 || h.layer>1) throw invalid("selected layer outside range");
        validation(); if (h.direction<0 || h.direction>4) throw invalid("selected direction outside range");
        validation(); if (h.transitions<0 || h.transitions>48-t.prefixViaCount)
            throw invalid("selected transitions outside range");
        validation(); if (h.cost<0) throw invalid("selected cost below range");
        validation(); if (h.storedHeuristic<0) throw invalid("selected heuristic below range");
        validation(); if (h.serial<0) throw invalid("selected serial below range");
        validation(); if (h.key!=(((h.transitions*2+h.layer)*5+h.direction)*cells)+h.cell)
            throw invalid("selected key mismatch");
        validation(); if (h.storedPriority!=h.cost+h.storedHeuristic)
            throw invalid("selected stored priority mismatch");
        validation(); if (t.branchExpanded!=100000 || t.totalExpanded>=1000000)
            throw invalid("selected branch cap counters mismatch");
        validation(); if (t.bestStateCount<1) throw invalid("selected best-state count below range");
    }

    private void checkId(String value) {
        validation(); if (value == null) throw invalid("null identity");
        validation(); if (value.length() == 0) throw invalid("empty identity");
        validation(); if (value.length() > ObservationSpec.ID_LIMIT) throw invalid("identity too long");
    }

    private static int reasonCode(String s) {
        if ("NO_PATH".equals(s)) return 1;
        if ("BRANCH_SEARCH_LIMIT".equals(s)) return 2;
        if ("TOTAL_SEARCH_LIMIT".equals(s)) return 3;
        if ("FOUND".equals(s)) return 4;
        return 0;
    }

    private static boolean reasonConsistent(DetachedTerminal t, int r) {
        switch (r) {
        case 1: return t.frontierCount == 0;
        case 2: return t.branchExpanded == 100000;
        case 3: return t.totalExpanded == 1000000;
        case 4: return t.branchExpanded > 0;
        default: return false;
        }
    }

    private void commit(DetachedTerminal tuple,int ordinal,DetachedHead head) {
        unit(); selections++;
        if (accepted == 0) firstOrdinal=ordinal;
        accepted++; lastOrdinal=ordinal; lastTotal=tuple.totalExpanded;
        if ("FOUND".equals(tuple.terminalReason)) found++;
        else {
            failures++; selected=tuple; selectedHead=head;
            selectedCallbackOrdinal=(int)dispatches;
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid terminal callback: " + message);
    }
    private static Throwable append(Throwable primary, Throwable later) {
        if (primary == null) return later;
        if (later != null && later != primary) primary.addSuppressed(later);
        return primary;
    }
    private static void throwComponent(Throwable t) {
        if (t instanceof RuntimeException) throw (RuntimeException)t;
        if (t instanceof Error) throw (Error)t;
        throw new IllegalStateException("Terminal observer failed", t);
    }
}

/* Nested members for the disconnected compact ASCII JSON formatter. */
interface CanonicalJsonSink {
    void beforeDataOperation() throws Exception;
    void writeByte(int ascii) throws Exception;
}
static final class CanonicalJsonVisits {
    long count;
}
/* Pure shared path policy; metadata validator meters these calls separately. */
static final int R9_PATH_ROOT=0, R9_PATH_ARM=1, R9_PATH_REFERENCES=2,
    R9_PATH_FRESH=3, R9_PATH_LAYOUT=4, R9_PATH_COMPONENTS=5,
    R9_PATH_COMPONENT=6, R9_PATH_GEOMETRY=7, R9_PATH_OTHER=8;
static int r9PathMapChild(int state,String key) {
    switch (state) {
    case R9_PATH_ROOT:
        return "references".equals(key)?R9_PATH_REFERENCES:R9_PATH_OTHER;
    case R9_PATH_REFERENCES:
        return "OPEN".equals(key) || "BLOCKED".equals(key)?R9_PATH_ARM:R9_PATH_OTHER;
    case R9_PATH_ARM:
        return "run".equals(key)?R9_PATH_FRESH:R9_PATH_OTHER;
    case R9_PATH_FRESH:
        return "layout".equals(key)?R9_PATH_LAYOUT:R9_PATH_OTHER;
    case R9_PATH_LAYOUT:
        return "components".equals(key)?R9_PATH_COMPONENTS:R9_PATH_OTHER;
    case R9_PATH_COMPONENT:
        return "geometryCanonical".equals(key)?R9_PATH_GEOMETRY:R9_PATH_OTHER;
    default:return R9_PATH_OTHER;
    }
}
static int r9PathListChild(int state) {
    return state==R9_PATH_COMPONENTS?R9_PATH_COMPONENT:R9_PATH_OTHER;
}
static boolean r9PathTypeValid(int state,Object value) {
    switch (state) {
    case R9_PATH_ROOT:case R9_PATH_REFERENCES:case R9_PATH_ARM:case R9_PATH_FRESH:case R9_PATH_COMPONENT:
        return value instanceof java.util.Map;
    case R9_PATH_LAYOUT:
        return value==null || value instanceof java.util.Map;
    case R9_PATH_COMPONENTS:return value instanceof java.util.List;
    case R9_PATH_GEOMETRY:return value instanceof String;
    case R9_PATH_OTHER:return true;
    default:return false;
    }
}
static int r9PathStringLimit(int state) { return state==R9_PATH_GEOMETRY?4096:256; }
static void writeCanonicalJson(Object value,CanonicalJsonSink sink) throws Exception {
    writeCanonicalJsonWithValueLimit(value,sink,false);
}
/* Caller must validate exact permitted long-value paths and own the body exclusively. */
static void writeCanonicalJsonCanaryValues(Object value,CanonicalJsonSink sink) throws Exception {
    writeCanonicalJsonWithValueLimit(value,sink,true);
}
private static void writeCanonicalJsonWithValueLimit(Object value,CanonicalJsonSink sink,boolean canary) throws Exception {
    if (sink==null) throw new IllegalArgumentException("missing JSON sink");
    sink.beforeDataOperation();
    CanonicalJsonVisits visits=new CanonicalJsonVisits();
    writeCanonicalValueWithLimit(value,sink,visits,0,canary,R9_PATH_ROOT);
    sink.writeByte('\n');
}
static void writeCanonicalValue(Object value,CanonicalJsonSink sink,
        CanonicalJsonVisits visits,int depth) throws Exception {
    writeCanonicalValueWithLimit(value,sink,visits,depth,false,R9_PATH_OTHER);
}
private static void writeCanonicalValueWithLimit(Object value,CanonicalJsonSink sink,
        CanonicalJsonVisits visits,int depth,boolean canary,int pathState) throws Exception {
    sink.beforeDataOperation();
    if (depth>64) throw new IllegalArgumentException("JSON depth");
    sink.beforeDataOperation();
    if (visits.count>=1048576L) throw new IllegalArgumentException("JSON visits");
    sink.beforeDataOperation();
    visits.count++;
    if (canary) {
        sink.beforeDataOperation();
        if (!r9PathTypeValid(pathState,value)) throw new IllegalArgumentException("JSON canary path type");
    }
    sink.beforeDataOperation();
    if (value==null) { writeCanonicalLiteral(sink,"null"); return; }
    sink.beforeDataOperation();
    if (value instanceof String) {
        int limit=256;
        if (canary) { sink.beforeDataOperation(); limit=r9PathStringLimit(pathState); }
        writeCanonicalStringWithLimit((String)value,sink,limit); return;
    }
    sink.beforeDataOperation();
    if (value instanceof Integer || value instanceof Long) {
        sink.beforeDataOperation();
        writeCanonicalInteger(((Number)value).longValue(),sink);
        return;
    }
    sink.beforeDataOperation();
    if (value instanceof Boolean) {
        sink.beforeDataOperation();
        if (((Boolean)value).booleanValue()) writeCanonicalLiteral(sink,"true");
        else writeCanonicalLiteral(sink,"false");
        return;
    }
    sink.beforeDataOperation();
    if (value instanceof java.util.Map) {
        sink.writeByte('{');
        sink.beforeDataOperation();
        java.util.Iterator<? extends java.util.Map.Entry<?,?>> it=
            ((java.util.Map<?,?>)value).entrySet().iterator();
        boolean first=true;
        while (true) {
            sink.beforeDataOperation();
            if (!it.hasNext()) break;
            sink.beforeDataOperation();
            java.util.Map.Entry<?,?> entry=it.next();
            sink.beforeDataOperation();
            Object key=entry.getKey();
            sink.beforeDataOperation();
            if (!(key instanceof String)) throw new IllegalArgumentException("JSON key");
            int childState=R9_PATH_OTHER;
            if (canary) { sink.beforeDataOperation(); childState=r9PathMapChild(pathState,(String)key); }
            if (!first) sink.writeByte(',');
            first=false;
            writeCanonicalString((String)key,sink);
            sink.writeByte(':');
            sink.beforeDataOperation();
            Object child=entry.getValue();
            sink.beforeDataOperation();
            writeCanonicalValueWithLimit(child,sink,visits,depth+1,canary,childState);
        }
        sink.writeByte('}');
        return;
    }
    sink.beforeDataOperation();
    if (value instanceof java.util.List) {
        sink.writeByte('[');
        sink.beforeDataOperation();
        java.util.Iterator<?> it=((java.util.List<?>)value).iterator();
        boolean first=true;
        while (true) {
            sink.beforeDataOperation();
            if (!it.hasNext()) break;
            sink.beforeDataOperation();
            Object child=it.next();
            if (!first) sink.writeByte(',');
            first=false;
            sink.beforeDataOperation();
            int childState=R9_PATH_OTHER;
            if (canary) { sink.beforeDataOperation(); childState=r9PathListChild(pathState); }
            writeCanonicalValueWithLimit(child,sink,visits,depth+1,canary,childState);
        }
        sink.writeByte(']');
        return;
    }
    throw new IllegalArgumentException("JSON value");
}
static void writeCanonicalString(String value,CanonicalJsonSink sink) throws Exception {
    writeCanonicalStringWithLimit(value,sink,256);
}
private static void writeCanonicalStringWithLimit(String value,CanonicalJsonSink sink,int limit) throws Exception {
    sink.beforeDataOperation();
    int length=value.length();
    sink.beforeDataOperation();
    if (length>limit) throw new IllegalArgumentException("JSON string length");
    sink.writeByte('"');
    int i=0;
    while (true) {
        sink.beforeDataOperation();
        if (i>=length) break;
        sink.beforeDataOperation();
        char c=value.charAt(i++);
        sink.beforeDataOperation();
        if (c<32 || c>126) throw new IllegalArgumentException("JSON string ASCII");
        sink.beforeDataOperation();
        if (c=='"' || c=='\\') sink.writeByte('\\');
        sink.writeByte(c);
    }
    sink.writeByte('"');
}
static void writeCanonicalLiteral(CanonicalJsonSink sink,String literal) throws Exception {
    sink.beforeDataOperation();
    int length=literal.length();
    int i=0;
    while (true) {
        sink.beforeDataOperation();
        if (i>=length) break;
        sink.beforeDataOperation();
        sink.writeByte(literal.charAt(i++));
    }
}
static void writeCanonicalInteger(long value,CanonicalJsonSink sink) throws Exception {
    sink.beforeDataOperation();
    boolean negative=value<0;
    sink.beforeDataOperation();
    long remaining=negative?value:-value;
    if (negative) sink.writeByte('-');
    long divisor=1;
    while (true) {
        sink.beforeDataOperation();
        if (remaining/divisor>-10) break;
        sink.beforeDataOperation();
        divisor*=10;
    }
    while (true) {
        sink.beforeDataOperation();
        int digit=(int)(-(remaining/divisor));
        sink.writeByte('0'+digit);
        sink.beforeDataOperation();
        remaining%=divisor;
        sink.beforeDataOperation();
        if (divisor==1) break;
        sink.beforeDataOperation();
        divisor/=10;
    }
}


/* Nested Java 7 members. Selected Windows JDK8 internal ABI only.
 * Caller must already own the paid bounded file-check operation. Source HOLD. */
static final class R5WindowsIdentity {
    private final java.nio.file.attribute.BasicFileAttributes attributes;
    final int volumeSerialNumber,fileIndexHigh,fileIndexLow;
    private R5WindowsIdentity(java.nio.file.attribute.BasicFileAttributes attributes,
            int volume,int high,int low) {
        this.attributes=attributes;
        volumeSerialNumber=volume; fileIndexHigh=high; fileIndexLow=low;
    }
    java.nio.file.attribute.BasicFileAttributes basicAttributes() { return attributes; }
    boolean sameIdentity(R5WindowsIdentity other) {
        return other!=null && volumeSerialNumber==other.volumeSerialNumber &&
            fileIndexHigh==other.fileIndexHigh && fileIndexLow==other.fileIndexLow;
    }
}
static void r5WindowsIdentityRequire(boolean ok,String reason) throws java.io.IOException {
    if (!ok) throw new java.io.IOException(reason);
}
static Object r5WindowsIdentityInvoke(java.lang.reflect.Method method,Object receiver,
        Object... arguments) throws Exception {
    try { return method.invoke(receiver,arguments); }
    catch (java.lang.reflect.InvocationTargetException wrapped) {
        Throwable cause=wrapped.getCause();
        if (cause instanceof Exception) throw (Exception)cause;
        if (cause instanceof Error) throw (Error)cause;
        throw new IllegalStateException("R5_WINDOWS_IDENTITY_INVOKE",cause);
    }
}
static java.lang.reflect.Field r5WindowsIdentityField(Class<?> owner,String name) throws Exception {
    java.lang.reflect.Field f=owner.getDeclaredField(name);
    r5WindowsIdentityRequire(f.getType()==int.class &&
        !java.lang.reflect.Modifier.isStatic(f.getModifiers()) &&
        java.lang.reflect.Modifier.isFinal(f.getModifiers()),"R5_WINDOWS_IDENTITY_ABI");
    f.setAccessible(true);
    return f;
}
static R5WindowsIdentity readR5WindowsIdentity(java.nio.file.Path path) throws Exception {
    r5WindowsIdentityRequire(path!=null,"R5_WINDOWS_IDENTITY_PATH");
    Class<?> pathType=Class.forName("sun.nio.fs.WindowsPath");
    Class<?> fileSystemType=Class.forName("sun.nio.fs.WindowsFileSystem");
    Class<?> attributesType=Class.forName("sun.nio.fs.WindowsFileAttributes");
    Class<?> dispatcherType=Class.forName("sun.nio.fs.WindowsNativeDispatcher");
    r5WindowsIdentityRequire(pathType.isInstance(path) && pathType.getClassLoader()==null &&
        fileSystemType.isInstance(path.getFileSystem()) && fileSystemType.getClassLoader()==null &&
        attributesType.getClassLoader()==null && dispatcherType.getClassLoader()==null &&
        java.nio.file.attribute.BasicFileAttributes.class.isAssignableFrom(attributesType),
        "R5_WINDOWS_IDENTITY_ABI");

    /* Resolve and validate the complete ABI, including cleanup, before opening
     * any handle. Never read get(path)'s zero-identity fast-path attributes. */
    java.lang.reflect.Method open=pathType.getDeclaredMethod(
        "openForReadAttributeAccess",boolean.class);
    java.lang.reflect.Method read=attributesType.getDeclaredMethod("readAttributes",long.class);
    java.lang.reflect.Method close=dispatcherType.getDeclaredMethod("CloseHandle",long.class);
    java.lang.reflect.Method reparse=attributesType.getDeclaredMethod("isReparsePoint");
    java.lang.reflect.Method supportsLinks=fileSystemType.getDeclaredMethod("supportsLinks");
    r5WindowsIdentityRequire(open.getReturnType()==long.class &&
        !java.lang.reflect.Modifier.isStatic(open.getModifiers()) &&
        read.getReturnType()==attributesType && java.lang.reflect.Modifier.isStatic(read.getModifiers()) &&
        close.getReturnType()==void.class && java.lang.reflect.Modifier.isStatic(close.getModifiers()) &&
        java.lang.reflect.Modifier.isNative(close.getModifiers()) &&
        reparse.getReturnType()==boolean.class &&
        !java.lang.reflect.Modifier.isStatic(reparse.getModifiers()) &&
        supportsLinks.getReturnType()==boolean.class &&
        !java.lang.reflect.Modifier.isStatic(supportsLinks.getModifiers()),"R5_WINDOWS_IDENTITY_ABI");
    open.setAccessible(true); read.setAccessible(true); close.setAccessible(true);
    reparse.setAccessible(true); supportsLinks.setAccessible(true);
    java.lang.reflect.Field volume=r5WindowsIdentityField(attributesType,"volSerialNumber");
    java.lang.reflect.Field high=r5WindowsIdentityField(attributesType,"fileIndexHigh");
    java.lang.reflect.Field low=r5WindowsIdentityField(attributesType,"fileIndexLow");
    /* open(false) only sets OPEN_REPARSE_POINT when this capability is true. */
    Object links=r5WindowsIdentityInvoke(supportsLinks,path.getFileSystem());
    r5WindowsIdentityRequire(links instanceof Boolean && ((Boolean)links).booleanValue(),
        "R5_WINDOWS_IDENTITY_NOFOLLOW_UNAVAILABLE");

    /* The validated primitive long return is reflectively boxed as Long.
     * false requests FILE_FLAG_OPEN_REPARSE_POINT on the selected provider. */
    Object opened=r5WindowsIdentityInvoke(open,path,Boolean.FALSE);
    r5WindowsIdentityRequire(opened instanceof Long,"R5_WINDOWS_IDENTITY_HANDLE_TYPE");
    long handle=((Long)opened).longValue();
    r5WindowsIdentityRequire(handle!=0L && handle!=-1L,"R5_WINDOWS_IDENTITY_HANDLE");
    Throwable primary=null;
    try {
        Object raw=r5WindowsIdentityInvoke(read,null,Long.valueOf(handle));
        r5WindowsIdentityRequire(raw!=null && raw.getClass()==attributesType,
            "R5_WINDOWS_IDENTITY_ATTRIBUTES_TYPE");
        java.nio.file.attribute.BasicFileAttributes basic=
            (java.nio.file.attribute.BasicFileAttributes)raw;
        Object isReparse=r5WindowsIdentityInvoke(reparse,raw);
        r5WindowsIdentityRequire(isReparse instanceof Boolean,"R5_WINDOWS_IDENTITY_REPARSE_TYPE");
        r5WindowsIdentityRequire(!((Boolean)isReparse).booleanValue() &&
            !basic.isSymbolicLink() && !basic.isOther(),"R5_WINDOWS_IDENTITY_REPARSE_OR_OTHER");
        int volumeValue=volume.getInt(raw),highValue=high.getInt(raw),lowValue=low.getInt(raw);
        /* Zero file index cannot establish identity on a provider that omits it.
         * Signed int bit patterns are preserved; negative values are valid. */
        r5WindowsIdentityRequire(highValue!=0 || lowValue!=0,"R5_WINDOWS_IDENTITY_ZERO_INDEX");
        return new R5WindowsIdentity(basic,volumeValue,highValue,lowValue);
    } catch (Exception failure) {
        primary=failure; throw failure;
    } catch (Error failure) {
        primary=failure; throw failure;
    } finally {
        try { r5WindowsIdentityInvoke(close,null,Long.valueOf(handle)); }
        catch (Exception closeFailure) {
            if (primary==null) throw closeFailure;
            if (primary!=closeFailure) primary.addSuppressed(closeFailure);
        } catch (Error closeFailure) {
            if (primary==null) throw closeFailure;
            if (primary!=closeFailure) primary.addSuppressed(closeFailure);
        }
    }
}


/* Nested Java 7 members. Requires the existing SetupMeter/Arguments/field/
 * setNumber/exportCopyBlock and CanonicalJson.fragment.java. Source-only HOLD. */
interface R5WorkFinalizer {
    /* Binding rejects stale/foreign work plans before any slot is changed. */
    void verifyBound(SetupMeter meter,R5Snapshot pre,java.util.Map<?,?> body,
            java.util.Map<?,?> work) throws Exception;
    /* Replace only existing numeric slots from frozen scalars; no meter,
     * graph, key insertion, map replacement, or reopened work is permitted. */
    void finalizeWork(long exportStartNanos) throws Exception;
}
interface R5AfterResultWrite {
    /* Test-only, normally null; not exposed by arguments or the player UI. */
    void afterResultWrite(java.nio.file.Path resultFile) throws Exception;
}
static final long R5_OPERATION_NANOS=90000000000L;
static final long R5_EXPORT_NANOS=5000000000L;
static final int R5_BLOCK=4096, R5_RESULT_LIMIT=1048576, R5_MARKER_BOUND=656;

static final class R5ExportMetrics {
    final long resultBytes,markerBytes,exportUnits,finalExportNanos,elapsedNanos;
    final long totalUnits,totalGroups,maxGroupNanos,maximumUnitsPerGroup;
    final long copiedEntriesDelta,copyChunksDelta;
    R5ExportMetrics(long r,long marker,long fee,long duration,long elapsed,
            long units,long groups,long maxNanos,long maxUnits,long copies,long chunks) {
        resultBytes=r; markerBytes=marker; exportUnits=fee; finalExportNanos=duration;
        elapsedNanos=elapsed; totalUnits=units; totalGroups=groups;
        maxGroupNanos=maxNanos; maximumUnitsPerGroup=maxUnits;
        copiedEntriesDelta=copies; copyChunksDelta=chunks;
    }
}
static long r5Number(Object object,String name) throws Exception {
    return ((Number)field(object,name)).longValue();
}
static boolean r5Flag(Object object,String name) throws Exception {
    return Boolean.TRUE.equals(field(object,name));
}
static void r5Require(boolean ok,String reason) throws java.io.IOException {
    if (!ok) throw new java.io.IOException(reason);
}
static long r5Elapsed(long start,long now) throws java.io.IOException {
    long elapsed=now-start;
    r5Require(elapsed>=0,"R5_CLOCK");
    return elapsed;
}
static void r5Deadline(SetupMeter meter,long started,long exportStarted) throws Exception {
    meter.method("check",new Class<?>[0]);
    long now=System.nanoTime();
    r5Require(r5Elapsed(started,now)<=R5_OPERATION_NANOS,"R5_OPERATION_DEADLINE");
    r5Require(r5Elapsed(exportStarted,now)<=R5_EXPORT_NANOS,"R5_EXPORT_DEADLINE");
}
static void r5Charge(SetupMeter meter,long started,long exportStarted) throws Exception {
    r5Deadline(meter,started,exportStarted);
    meter.method("charge",new Class<?>[]{long.class},Long.valueOf(1));
}

/* Allocate during measured metadata. Capture only after all metadata closes;
 * no export-time allocation or implicit metadata group is permitted. */
static R5Snapshot allocateR5Snapshot(SetupMeter meter) throws Exception {
    r5Require(meter!=null,"R5_SNAPSHOT_METER");
    meter.charge(1);
    return new R5Snapshot(meter);
}
/* Scalar closed-metadata checkpoint; no graph or copied collection. */
static final class R5Snapshot {
    private final SetupMeter boundMeter;
    private long units,groups,prior,copies,chunks,maxNanos,maxUnits,allowance,measures;
    private long groupUnits,groupStarted,started,constructorEntries;
    private Object measureList;
    private boolean ownerStarted,ownerAdopted,captureAttempted,captured;
    private R5Snapshot(SetupMeter m) { boundMeter=m; }
    void captureClosed(SetupMeter m,long operationStarted) throws Exception {
        r5Require(m==boundMeter && !captureAttempted,"R5_SNAPSHOT_CAPTURE_STATE");
        captureAttempted=true; // a failed capture is not reusable
        Object nativeMeter=m.meter;
        r5Require(!r5Flag(nativeMeter,"open") &&
            !r5Flag(nativeMeter,"originalOwnerActive") &&
            r5Number(nativeMeter,"inFlightOwnerGroups")==0,"R5_METADATA_NOT_CLOSED");
        units=r5Number(nativeMeter,"units"); groups=r5Number(nativeMeter,"groups");
        prior=r5Number(nativeMeter,"priorGroups"); copies=r5Number(nativeMeter,"copiedEntries");
        chunks=r5Number(nativeMeter,"copyChunks"); maxNanos=r5Number(nativeMeter,"maxGroupNanos");
        maxUnits=r5Number(nativeMeter,"maxGroupUnits"); allowance=r5Number(nativeMeter,"copyAllowance");
        groupUnits=r5Number(nativeMeter,"groupUnits"); groupStarted=r5Number(nativeMeter,"groupStarted");
        started=r5Number(nativeMeter,"started");
        measureList=field(nativeMeter,"groupMeasures");
        measures=((java.util.List<?>)measureList).size();
        constructorEntries=r5Number(nativeMeter,"sessionConstructorEntries");
        ownerStarted=r5Flag(nativeMeter,"originalOwnerStarted");
        ownerAdopted=r5Flag(nativeMeter,"originalOwnerAdopted");
        r5Require(started==operationStarted && units>=0 && groups>=0 && prior>=0 &&
            groups+prior<640 && measures==groups && allowance>=0 && allowance<=R5_RESULT_LIMIT &&
            copies>=0 && copies<=allowance && chunks>=0 && chunks<Integer.MAX_VALUE &&
            maxNanos>=0 && maxNanos<=R5_EXPORT_NANOS && maxUnits>=0 && maxUnits<=32768 &&
            groupUnits>=0 && groupUnits<=32768 && constructorEntries>=0 && prior>=1 &&
            ownerStarted && ownerAdopted,
            "R5_METADATA_SNAPSHOT");
        captured=true;
    }
    void verifyReady(SetupMeter m,long operationStarted) throws Exception {
        r5Require(captured && m==boundMeter && started==operationStarted,
            "R5_SNAPSHOT_NOT_READY");
        unchanged(m,0,0,false,groupStarted,copies,chunks);
        r5Require(r5Number(m.meter,"groupUnits")==groupUnits &&
            r5Number(m.meter,"maxGroupNanos")==maxNanos &&
            r5Number(m.meter,"maxGroupUnits")==maxUnits,"R5_STALE_SNAPSHOT");
    }
    void unchanged(SetupMeter m,long extraUnits,long extraGroups,boolean open,
            long activeStart,long expectedCopies,long expectedChunks) throws Exception {
        r5Require(captured && m==boundMeter,"R5_SNAPSHOT_NOT_READY");
        Object n=m.meter;
        r5Require(r5Number(n,"units")==units+extraUnits &&
            r5Number(n,"groups")==groups+extraGroups && r5Number(n,"priorGroups")==prior &&
            r5Number(n,"copiedEntries")==expectedCopies && r5Number(n,"copyChunks")==expectedChunks &&
            r5Number(n,"copyAllowance")==allowance && r5Number(n,"started")==started &&
            r5Flag(n,"open")==open && !r5Flag(n,"originalOwnerActive") &&
            r5Number(n,"inFlightOwnerGroups")==0 &&
            r5Flag(n,"originalOwnerStarted")==ownerStarted &&
            r5Flag(n,"originalOwnerAdopted")==ownerAdopted &&
            field(n,"groupMeasures")==measureList &&
            ((java.util.List<?>)measureList).size()==measures+extraGroups &&
            r5Number(n,"sessionConstructorEntries")==constructorEntries &&
            r5Number(n,"groupStarted")==activeStart,"R5_METER_CHANGED");
    }
}

/* Fixed order, without an application-owned key array or copied map. */
static String r5WorkKey(int i) {
    switch(i) {
    case 0:return "elapsedCutoffNanos"; case 1:return "cleanupNanos";
    case 2:return "groupsBeforeExport"; case 3:return "wrapperGroupsBeforeExport";
    case 4:return "maxGroupNanosBeforeExport"; case 5:return "maximumUnitsPerGroupBeforeExport";
    case 6:return "unitsBeforeExport"; case 7:return "setupUnits";
    case 8:return "setupGroups"; case 9:return "setupCopiedEntries";
    case 10:return "setupCopyChunks"; case 11:return "originalCaptureOwnerUnits";
    case 12:return "originalCaptureWrapperUnits"; case 13:return "originalCaptureCallbackUnits";
    case 14:return "originalCaptureDiagnosticUnits"; case 15:return "originalCaptureGroups";
    case 16:return "originalCaptureCopiedEntries"; case 17:return "originalCaptureCopyChunks";
    case 18:return "nullOwnerUnits"; case 19:return "nullWrapperUnits";
    case 20:return "nullObserverUnits"; case 21:return "observeOwnerUnits";
    case 22:return "observeWrapperUnits"; case 23:return "observeObserverUnits";
    case 24:return "observerUnits"; case 25:return "meterUnits";
    case 26:return "metadataUnits"; case 27:return "metadataGroups";
    case 28:return "metadataCopiedEntries"; case 29:return "metadataCopyChunks";
    case 30:return "copiedEntries"; case 31:return "copyChunks";
    case 32:return "copyChunkLimit"; case 33:return "copiedEntryLimit";
    default:throw new IllegalArgumentException("R5_WORK_KEY");
    }
}
static void r5WorkShape(java.util.Map<?,?> work) throws Exception {
    r5Require(work.size()==34,"R5_WORK_SHAPE");
    java.util.Iterator<? extends java.util.Map.Entry<?,?>> it=work.entrySet().iterator();
    for (int i=0;i<34;i++) {
        r5Require(it.hasNext(),"R5_WORK_SHAPE");
        java.util.Map.Entry<?,?> entry=it.next();
        r5Require(r5WorkKey(i).equals(entry.getKey()),"R5_WORK_ORDER");
        Object value=entry.getValue();
        r5Require((value instanceof Integer || value instanceof Long) &&
            ((Number)value).longValue()>=0,"R5_WORK_INTEGER");
    }
    r5Require(!it.hasNext(),"R5_WORK_SHAPE");
}
static long r5WorkNumber(java.util.Map<?,?> work,String key) throws Exception {
    Object value=work.get(key);
    r5Require(value instanceof Integer || value instanceof Long,"R5_WORK_INTEGER");
    long n=((Number)value).longValue();
    r5Require(n>=0,"R5_WORK_INTEGER");
    return n;
}
static long r5Add(long a,long b) throws Exception {
    r5Require(a>=0 && b>=0 && a<=Long.MAX_VALUE-b,"R5_INTEGER_OVERFLOW");
    return a+b;
}
static void r5ValidateWork(java.util.Map<?,?> work,R5Snapshot pre,long exportStarted) throws Exception {
    r5WorkShape(work);
    long offset=r5Add(r5WorkNumber(work,"originalCaptureOwnerUnits"),
        r5Add(r5WorkNumber(work,"originalCaptureCallbackUnits"),
            r5WorkNumber(work,"originalCaptureDiagnosticUnits")));
    long aggregate=r5Add(pre.units,offset);
    r5Require(r5WorkNumber(work,"meterUnits")==aggregate &&
        r5WorkNumber(work,"unitsBeforeExport")==aggregate &&
        r5WorkNumber(work,"groupsBeforeExport")==pre.groups+pre.prior &&
        r5WorkNumber(work,"wrapperGroupsBeforeExport")==pre.groups &&
        r5WorkNumber(work,"originalCaptureGroups")>=pre.prior &&
        r5WorkNumber(work,"originalCaptureGroups")<=pre.groups+pre.prior &&
        r5WorkNumber(work,"copiedEntries")==pre.copies &&
        r5WorkNumber(work,"copyChunks")==pre.chunks &&
        r5WorkNumber(work,"copyChunkLimit")==R5_BLOCK &&
        r5WorkNumber(work,"copiedEntryLimit")==R5_RESULT_LIMIT &&
        r5WorkNumber(work,"maxGroupNanosBeforeExport")>=pre.maxNanos &&
        r5WorkNumber(work,"maxGroupNanosBeforeExport")<=R5_EXPORT_NANOS &&
        r5WorkNumber(work,"maximumUnitsPerGroupBeforeExport")>=pre.maxUnits &&
        r5WorkNumber(work,"maximumUnitsPerGroupBeforeExport")<=32768 &&
        r5WorkNumber(work,"elapsedCutoffNanos")==r5Elapsed(pre.started,exportStarted),
        "R5_WORK_PRE_NATIVE_MISMATCH");
}

/* Java 7 NOFOLLOW basic attributes, canonical paths, and stable file keys.
 * Reject providers without an identity key rather than weaken identity checks. */
static final class R5FileIdentity {
    final R5WindowsIdentity key;
    final long size;
    final java.nio.file.attribute.FileTime modified,created;
    R5FileIdentity(java.nio.file.Path path,boolean directory) throws Exception {
        r5Require(path.isAbsolute() && path.normalize().equals(path) &&
            path.toRealPath().equals(path),"R5_NONCANONICAL_PATH");
        R5WindowsIdentity identity=readR5WindowsIdentity(path);
        java.nio.file.attribute.BasicFileAttributes a=identity.basicAttributes();
        r5Require(!a.isSymbolicLink() && !a.isOther() &&
            (directory?a.isDirectory():a.isRegularFile()),
            "R5_FILE_TYPE_OR_IDENTITY");
        key=identity; size=a.size(); modified=a.lastModifiedTime(); created=a.creationTime();
    }
    void verify(java.nio.file.Path path,boolean directory,boolean unchangedContents) throws Exception {
        R5FileIdentity actual=new R5FileIdentity(path,directory);
        r5Require(key.sameIdentity(actual.key) && created.equals(actual.created),"R5_FILE_REPLACED");
        if (unchangedContents) r5Require(size==actual.size && modified.equals(actual.modified),
            "R5_FILE_CHANGED");
    }
}
static final class R5Output {
    final java.nio.file.Path directory,operation,result;
    final R5FileIdentity directoryIdentity,operationIdentity;
    R5FileIdentity resultIdentity;
    R5Output(java.nio.file.Path directory) throws Exception {
        this.directory=directory; operation=directory.resolve("op-1.jsonl");
        result=directory.resolve("result.json");
        /* Check every ancestor without following a reparse path. */
        java.nio.file.Path part=directory;
        while (part!=null) { new R5FileIdentity(part,true); part=part.getParent(); }
        directoryIdentity=new R5FileIdentity(directory,true);
        operationIdentity=new R5FileIdentity(operation,false);
        closure(false,-1);
    }
    void closure(boolean hasResult,long resultBytes) throws Exception {
        directoryIdentity.verify(directory,true,false);
        operationIdentity.verify(operation,false,true);
        int count=0;
        boolean operationSeen=false,resultSeen=false;
        try (java.nio.file.DirectoryStream<java.nio.file.Path> entries=
                java.nio.file.Files.newDirectoryStream(directory)) {
            for (java.nio.file.Path entry:entries) {
                count++;
                if (entry.equals(operation)) {
                    r5Require(!operationSeen &&
                        "op-1.jsonl".equals(entry.getFileName().toString()),
                        "R5_OUTPUT_OPERATION");
                    operationSeen=true;
                } else if (entry.equals(result)) {
                    r5Require(hasResult && !resultSeen &&
                        "result.json".equals(entry.getFileName().toString()),
                        "R5_OUTPUT_RESULT");
                    resultSeen=true;
                } else {
                    throw new java.io.IOException("R5_OUTPUT_EXTRA_FILE");
                }
            }
        }
        r5Require(operationSeen && count==(hasResult?2:1) &&
            resultSeen==hasResult,"R5_OUTPUT_CLOSURE");
        if (!hasResult) r5Require(!java.nio.file.Files.exists(result,
            java.nio.file.LinkOption.NOFOLLOW_LINKS),"R5_RESULT_EXISTS");
        else {
            r5Require(resultIdentity!=null,"R5_RESULT_IDENTITY_MISSING");
            resultIdentity.verify(result,false,true);
            r5Require(resultIdentity.size==resultBytes && java.nio.file.Files.size(result)==resultBytes,
                "R5_RESULT_SIZE");
        }
    }
}
/* A credit is installed before the formatter inspects data in the block.
 * LF terminates the sink, so no speculative post-LF admission is possible. */
static abstract class R5BodySink implements CanonicalJsonSink {
    final SetupMeter meter;
    final long started,exportStarted;
    long position;
    boolean ended;
    R5BodySink(SetupMeter m,long started,long exportStarted) {
        meter=m; this.started=started; this.exportStarted=exportStarted;
    }
    public final void beforeDataOperation() throws Exception {
        r5Require(!ended,"R5_AFTER_FINAL_LF");
        ensureBlock();
    }
    public final void writeByte(int ascii) throws Exception {
        beforeDataOperation();
        r5Require(position<R5_RESULT_LIMIT && (ascii==10 || ascii>=32 && ascii<=126),
            "R5_RESULT_BYTE_OR_LENGTH");
        accept(ascii);
        position++;
        if (ascii==10) ended=true;
        afterByte();
    }
    abstract void ensureBlock() throws Exception;
    abstract void accept(int ascii) throws Exception;
    abstract void afterByte() throws Exception;
    void complete(long expected) throws Exception {
        r5Require(ended && position==expected,"R5_CANONICAL_LENGTH");
    }
}
static final class R5CountSink extends R5BodySink {
    long creditedBlock=-1;
    R5CountSink(SetupMeter m,long s,long e) { super(m,s,e); }
    void ensureBlock() throws Exception {
        r5Require(position<R5_RESULT_LIMIT,"R5_RESULT_LENGTH");
        long block=position/R5_BLOCK;
        if (creditedBlock!=block) {
            r5Charge(meter,started,exportStarted); creditedBlock=block;
        }
    }
    void accept(int ascii) { }
    void afterByte() { }
}
static final class R5WriteSink extends R5BodySink implements AutoCloseable {
    final long length;
    final R5Output output;
    byte[] block;
    int used;
    java.io.OutputStream stream;
    R5FileIdentity openedIdentity;
    R5WriteSink(SetupMeter m,long s,long e,long n,R5Output output) {
        super(m,s,e); length=n; this.output=output;
    }
    void ensureBlock() throws Exception {
        r5Require(position<length,"R5_EMIT_LENGTH");
        if (block==null) {
            int n=(int)Math.min((long)R5_BLOCK,length-position);
            exportCopyBlock(meter,n); // B: before new[] and every subsequent fill
            block=new byte[n]; used=0;
        }
    }
    void accept(int ascii) { block[used++]=(byte)ascii; }
    void afterByte() throws Exception {
        if (used!=block.length) return;
        r5Charge(meter,started,exportStarted); // C: before open or write
        if (stream==null) {
            output.closure(false,-1);
            stream=java.nio.file.Files.newOutputStream(output.result,
                java.nio.file.StandardOpenOption.CREATE_NEW,java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
            openedIdentity=new R5FileIdentity(output.result,false);
        }
        stream.write(block,0,used);
        block=null; used=0;
        if (position==length) {
            r5Require(ended,"R5_MISSING_FINAL_LF");
            stream.flush(); stream.close(); stream=null;
            openedIdentity.verify(output.result,false,false);
            output.resultIdentity=new R5FileIdentity(output.result,false);
            output.closure(true,length);
        }
    }
    public void close() throws java.io.IOException {
        if (stream!=null) { try { stream.close(); } finally { stream=null; } }
        block=null;
    }
}
static final class R5CompareSink extends R5BodySink implements AutoCloseable {
    final long length;
    final R5Output output;
    java.security.MessageDigest digest;
    byte[] block;
    int used;
    java.io.DataInputStream stream;
    R5CompareSink(SetupMeter m,long s,long e,long n,R5Output output) {
        super(m,s,e); length=n; this.output=output;
    }
    void ensureBlock() throws Exception {
        r5Require(position<length,"R5_COMPARE_LENGTH");
        if (block==null) {
            int n=(int)Math.min((long)R5_BLOCK,length-position);
            exportCopyBlock(meter,n); // D: exact entries before allocation/readFully
            block=new byte[n]; used=0;
            if (stream==null) {
                output.closure(true,length);
                stream=new java.io.DataInputStream(java.nio.file.Files.newInputStream(
                    output.result,java.nio.file.StandardOpenOption.READ,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS));
                output.resultIdentity.verify(output.result,false,true);
            }
            stream.readFully(block,0,n);
            r5Charge(meter,started,exportStarted); // E: before expected-data inspections
            if (digest==null) digest=java.security.MessageDigest.getInstance("SHA-256");
        }
    }
    void accept(int ascii) throws Exception {
        r5Require((block[used++]&255)==ascii,"R5_READBACK_MISMATCH");
    }
    void afterByte() throws Exception {
        if (used!=block.length) return;
        digest.update(block,0,used);
        block=null; used=0;
        if (position==length) {
            r5Require(ended && stream.read()==-1,"R5_READBACK_EOF");
            stream.close(); stream=null;
            output.closure(true,length);
        }
    }
    public void close() throws java.io.IOException {
        if (stream!=null) { try { stream.close(); } finally { stream=null; } }
        block=null;
    }
}

/* V2 marker output is delegated to CurrentV2CanonicalCodec. */
/* This token carries lifecycle, not a second budget. It can be constructed
 * only after the exporter has checked the actual closed measured group.
 * Failed admission consumes the token too; the whole failed Meter is discarded. */
static final class R5PublicationToken {
    private final SetupMeter meter;
    private final R5Snapshot pre;
    private final long started,exportStarted,fee,resultBytes,blocks,closedNanos,closedMaxNanos,closedMaxUnits;
    private final Object closedMeasure;
    private boolean retired;
    private R5PublicationToken(SetupMeter m,R5Snapshot pre,long s,long e,long fee,
            long bytes,long blocks,Object measure,long nanos) throws Exception {
        meter=m; this.pre=pre; started=s; exportStarted=e; this.fee=fee;
        resultBytes=bytes; this.blocks=blocks; closedMeasure=measure; closedNanos=nanos;
        closedMaxNanos=r5Number(m.meter,"maxGroupNanos");
        closedMaxUnits=r5Number(m.meter,"maxGroupUnits");
        verifyClosed(pre.copies+2*resultBytes+32,pre.chunks+2*blocks+1);
    }
    void verifyClosed(long copies,long chunks) throws Exception {
        pre.unchanged(meter,fee,1,false,exportStarted,copies,chunks);
        java.util.List<?> measures=(java.util.List<?>)field(meter.meter,"groupMeasures");
        r5Require(measures.get((int)pre.measures)==closedMeasure &&
            r5Number(closedMeasure,"units")==fee && r5Number(closedMeasure,"nanos")==closedNanos &&
            r5Number(meter.meter,"groupUnits")==fee &&
            r5Number(meter.meter,"maxGroupNanos")==closedMaxNanos &&
            r5Number(meter.meter,"maxGroupUnits")==closedMaxUnits &&
            closedMaxNanos==Math.max(pre.maxNanos,closedNanos) &&
            closedMaxUnits==Math.max(pre.maxUnits,fee),"R5_CLOSED_GROUP_CHANGED");
    }
    void admit(int actualMarkerBytes) throws Exception {
        r5Require(!retired,"R5_PUBLICATION_TOKEN_USED");
        retired=true; // retire before capacity check, ledger mutation, or copy
        r5Require(actualMarkerBytes>=1 && actualMarkerBytes<=R5_MARKER_BOUND,
            "R5_MARKER_BOUND");
        verifyClosed(pre.copies+2*resultBytes+32,pre.chunks+2*blocks+1);
        r5Deadline(meter,started,exportStarted);
        long copied=r5Number(meter.meter,"copiedEntries");
        long chunks=r5Number(meter.meter,"copyChunks");
        r5Require(copied>=0 && copied<=pre.allowance && actualMarkerBytes<=pre.allowance-copied &&
            chunks>=0 && chunks<Integer.MAX_VALUE,"R5_MARKER_COPY_CAPACITY");
        /* The already-paid third fixed operation owns exactly this one copy.
         * Neither generic reserveCopy nor open-group exportCopyBlock applies. */
        setNumber(meter.meter,"copiedEntries",Long.valueOf(copied+actualMarkerBytes));
        setNumber(meter.meter,"copyChunks",Integer.valueOf((int)chunks+1));
    }
    void afterPublication(int markerBytes) throws Exception {
        r5Require(retired,"R5_PUBLICATION_NOT_ADMITTED");
        verifyClosed(pre.copies+2*resultBytes+32+markerBytes,pre.chunks+2*blocks+2);
        r5Deadline(meter,started,exportStarted);
    }
}

static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,
        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer) throws Exception { throw new java.io.IOException("R5_EXPORT_DISABLED"); }
static R5ExportMetrics exportR5(GenericR7Arguments args,java.util.Map<String,Object> result,
        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,
        R5AfterResultWrite hook) throws Exception { throw new java.io.IOException("R5_EXPORT_DISABLED"); }


/* Caller keeps the borrowed result tree and marker digest exclusively stable through all passes and publication. */
static R5ExportMetrics exportV2(String runId,String sourceSha,String wireSha,String contextSha,
        java.nio.file.Path outputDirectory,java.util.Map<String,Object> result,
        SetupMeter meter,long started,R5Snapshot pre,R5WorkFinalizer finalizer,
        ReceiptContentBinding receipt,R5AfterResultWrite hook) throws Exception {
    r5Require(result!=null && meter!=null && pre!=null && finalizer!=null &&
        outputDirectory!=null && receipt!=null,"R5_ARGUMENT");
    pre.verifyReady(meter,started); // reject stale/foreign state before beginGroup
    meter.method("beginGroup",new Class<?>[0]);
    long exportStarted=r5Number(meter.meter,"groupStarted");
    /* No finally-finish: any partial failure discards this Meter and output.
     * Stream resources alone are closed by try-with-resources. */
    r5Charge(meter,started,exportStarted); // fixed 1: bounded header/output admission
    r5Require(runId!=null && runId.length()==36 && sourceSha!=null && sourceSha.length()==64 &&
        wireSha!=null && wireSha.length()==64 && contextSha!=null && contextSha.length()==64 &&
        runId.equals(result.get("runId")) &&
        sourceSha.equals(result.get("sourceManifestSha256")) &&
        wireSha.equals(result.get("wireContractSha256")) &&
        contextSha.equals(result.get("inputContextSha256")),"R5_HEADER_IDENTITY");
    R5Output output=new R5Output(outputDirectory);
    Object existingWork=result.get("work");
    r5Require(existingWork instanceof java.util.Map,"R5_WORK_MISSING");
    java.util.Map<?,?> work=(java.util.Map<?,?>)existingWork;
    r5WorkShape(work);
    finalizer.verifyBound(meter,pre,result,work);
    finalizer.finalizeWork(exportStarted);
    r5Require(result.get("work")==work,"R5_WORK_REPLACED");
    pre.unchanged(meter,1,0,true,exportStarted,pre.copies,pre.chunks);
    r5Require(r5Number(meter.meter,"groupUnits")==1 &&
        r5Number(meter.meter,"maxGroupNanos")==pre.maxNanos &&
        r5Number(meter.meter,"maxGroupUnits")==pre.maxUnits,"R5_FINALIZER_CHANGED_METER");
    r5ValidateWork(work,pre,exportStarted);
    long bodyPreUnits=r5WorkNumber(work,"unitsBeforeExport");
    long bodyPreMaxNanos=r5WorkNumber(work,"maxGroupNanosBeforeExport");
    long bodyPreMaxUnits=r5WorkNumber(work,"maximumUnitsPerGroupBeforeExport");
    long bodyCutoff=r5WorkNumber(work,"elapsedCutoffNanos");
    R5CountSink count=new R5CountSink(meter,started,exportStarted);
    CurrentV2CanonicalCodec.writeBody(result,count); // A: virtual bytes, no encoded body
    count.complete(count.position);
    long resultBytes=count.position,blocks=(resultBytes+R5_BLOCK-1)/R5_BLOCK;
    r5Require(resultBytes>=1 && resultBytes<=R5_RESULT_LIMIT && blocks>=1,"R5_RESULT_LENGTH");
    /* Known body/digest demand can fail before the first output allocation.
     * Marker capacity remains an exact post-close M check, with no 722-byte
     * headroom requirement. Counting paid only its actual C blocks. */
    long fee=5*blocks+3;
    r5Require(2*resultBytes+32<=pre.allowance-pre.copies &&
        pre.chunks<=Integer.MAX_VALUE-(2*blocks+2) && fee<=32768,
        "R5_KNOWN_EXPORT_CAPACITY");
    try (R5WriteSink writer=new R5WriteSink(meter,started,exportStarted,resultBytes,output)) {
        CurrentV2CanonicalCodec.writeBody(result,writer); // B/C interleaved
        writer.complete(resultBytes);
        r5Require(writer.stream==null && writer.block==null,"R5_WRITE_NOT_CLOSED");
    }
    if (hook!=null) hook.afterResultWrite(output.result);
    java.security.MessageDigest digest;
    try (R5CompareSink reader=new R5CompareSink(meter,started,exportStarted,resultBytes,output)) {
        CurrentV2CanonicalCodec.writeBody(result,reader); // D/E interleaved; expected bytes virtual
        reader.complete(resultBytes);
        r5Require(reader.stream==null && reader.block==null,"R5_READ_NOT_CLOSED");
        digest=reader.digest;
    }
    r5Deadline(meter,started,exportStarted);
    receipt.verifyFinal(meter,started,output.operation);
    r5Deadline(meter,started,exportStarted);
    exportCopyBlock(meter,32); // fixed 2, before digest's returned byte array
    byte[] sha=digest.digest();
    r5Require(sha.length==32,"R5_DIGEST_LENGTH");
    r5Charge(meter,started,exportStarted); // fixed 3: bounded marker finalization
    pre.unchanged(meter,fee,0,true,exportStarted,pre.copies+2*resultBytes+32,
        pre.chunks+2*blocks+1);
    r5Require(r5Number(meter.meter,"groupUnits")==fee &&
        r5Number(meter.meter,"maxGroupNanos")==pre.maxNanos &&
        r5Number(meter.meter,"maxGroupUnits")==pre.maxUnits,"R5_EXPORT_FEE");
    r5Deadline(meter,started,exportStarted);
    meter.method("finishGroup",new Class<?>[0]); // exactly one actual close
    pre.unchanged(meter,fee,1,false,exportStarted,pre.copies+2*resultBytes+32,
        pre.chunks+2*blocks+1);
    Object measured=((java.util.List<?>)field(meter.meter,"groupMeasures")).get((int)pre.measures);
    long finalExportNanos=r5Number(measured,"nanos");
    r5Require(r5Number(measured,"units")==fee && finalExportNanos>=0 &&
        finalExportNanos<=R5_EXPORT_NANOS,"R5_CLOSED_EXPORT_MEASURE");
    R5PublicationToken token=new R5PublicationToken(meter,pre,started,exportStarted,
        fee,resultBytes,blocks,measured,finalExportNanos);
    long elapsed=r5Elapsed(started,System.nanoTime()); // actual closed prefix only
    long totalUnits=r5Add(bodyPreUnits,fee),totalGroups=pre.groups+pre.prior+1;
    long maxNanos=Math.max(bodyPreMaxNanos,finalExportNanos),maxUnits=Math.max(bodyPreMaxUnits,fee);
    r5Require(elapsed>=r5Add(bodyCutoff,finalExportNanos) && elapsed<=R5_OPERATION_NANOS &&
        totalGroups<=640 && maxNanos<=R5_EXPORT_NANOS && maxUnits<=32768,"R5_FINAL_MARKER_METRICS");
    CurrentV2CanonicalCodec.Marker markerValue=new CurrentV2CanonicalCodec.Marker(
        runId,sourceSha,wireSha,contextSha,sha,resultBytes,elapsed,finalExportNanos,
        totalGroups,totalUnits,maxNanos,maxUnits);
    int markerBytes=CurrentV2CanonicalCodec.countMarker(markerValue);
    r5Require(markerBytes>=1 && markerBytes<=R5_MARKER_BOUND,"R5_MARKER_BOUND");
    token.admit(markerBytes);
    byte[] marker=new byte[markerBytes]; // exact-size target after same-Meter admission
    CurrentV2CanonicalCodec.writeMarker(markerValue,marker);
    r5Deadline(meter,started,exportStarted);
    java.io.PrintStream stdout=System.out;
    r5Require(!stdout.checkError(),"R5_STDOUT_PRIOR_ERROR");
    stdout.write(marker,0,markerBytes); stdout.flush();
    r5Require(!stdout.checkError(),"R5_STDOUT_ERROR");
    token.afterPublication(markerBytes); // failure propagates; never a second marker
    return new R5ExportMetrics(resultBytes,markerBytes,fee,finalExportNanos,elapsed,
        totalUnits,totalGroups,maxNanos,maxUnits,2*resultBytes+32+markerBytes,2*blocks+2);
}

private static final int R7_PLAN_FIELD_COPIES=26;
private static final int R7_CLOSED_SNAPSHOT_READS=19;
private static final int R7_WORK_SLOTS=34;
private static final int R7_CAPTURE_ADDITION_UNITS=11;
private static final int R7_WORK_ENTRY_COPIES=35;
private static final int R7_FREEZE_UNITS=26;
private static final int R7_FINALIZE_UNITS=38;
private static final int R7_FINALIZE_ENTRY_COPIES=34;

private static Object r7ReadObject(SetupMeter meter,Object source,String name) throws Exception {
    if (source==null) throw new java.io.IOException("R7_SOURCE_MISSING");
    meter.charge(1); // before each reflected source-field inspection
    return field(source,name);
}
private static long r7ReadLong(SetupMeter meter,Object source,String name) throws Exception {
    return r7NonnegativeLong(r7ReadObject(meter,source,name));
}
private static long r7ReadMapLong(SetupMeter meter,java.util.Map<?,?> source,String name)
        throws Exception {
    if (source==null) throw new java.io.IOException("R7_SOURCE_MAP_MISSING");
    meter.charge(1); // before each observation/cleanup map lookup
    return r7NonnegativeLong(source.get(name));
}
static long r7NonnegativeLong(Object value) throws java.io.IOException {
    if (!(value instanceof Integer || value instanceof Long))
        throw new java.io.IOException("R7_SOURCE_NUMBER");
    long number=((Number)value).longValue();
    if (number<0) throw new java.io.IOException("R7_SOURCE_NEGATIVE");
    return number;
}
private static long r7Subtract(long value,long amount) throws java.io.IOException {
    if (value<0 || amount<0 || value<amount)
        throw new java.io.IOException("R7_LEDGER_UNDERFLOW");
    return value-amount;
}

/** Real source detachment; its 40 charged inspections and 11 checked additions
 * remain on the original SetupResult/OriginalArm/ArmResult path. */
static R7WorkPlan captureR7WorkPlan(SetupResult setup,Object original,long originalWrapper,
        int originalGroups,ArmResult n,ArmResult o,long started,SetupMeter meter,
        java.util.Map<String,Object> result) throws Exception {
    if (setup==null || original==null || n==null || o==null || meter==null || result==null)
        throw new java.io.IOException("R7_ARGUMENT");
    if (originalWrapper<0 || originalGroups<1)
        throw new java.io.IOException("R7_ORIGINAL_BOUNDARY");

    /* Every read below is charged immediately before inspecting its source. */
    long setupUnits=r7ReadLong(meter,setup,"units");
    long setupGroups=r7ReadLong(meter,setup,"groups");
    long setupCopies=r7ReadLong(meter,setup,"copiedEntries");
    long setupChunks=r7ReadLong(meter,setup,"copyChunks");

    Object captureData=r7ReadObject(meter,original,"captureData");
    long originalOwnerUnits=r7ReadLong(meter,original,"p07OwnerSteps");
    meter.charge(R7_CAPTURE_ADDITION_UNITS); // prepay every checked capture addition before the first one
    long originalCallbackUnits=r5Add(r7ReadLong(meter,original,"callbackUnits"),
        r7ReadLong(meter,captureData,"extraCallbackUnits"));
    long originalDiagnosticUnits=r7ReadLong(meter,original,"diagnosticUnits");
    long originalOwnerGroups=r7ReadLong(meter,original,"groupCount");
    long originalCopies=r7ReadLong(meter,original,"copiedEntries");
    long originalChunks=r7ReadLong(meter,original,"copyChunks");
    long originalMaxNanos=r7ReadLong(meter,original,"maxGroupNanos");
    long originalCleanup=r7ReadLong(meter,original,"cleanupNanos");
    Object originalStats=r7ReadObject(meter,original,"groupStats");
    long originalMaxUnits=r7ReadLong(meter,originalStats,"maxUnits");

    Object nullFresh=r7ReadObject(meter,n,"fresh");
    long nullOwnerUnits=r7ReadLong(meter,nullFresh,"ownerUnits");
    long nullWrapperUnits=r7ReadLong(meter,nullFresh,"wrapperUnits");
    long nullGroups=r7ReadLong(meter,nullFresh,"groups");
    long nullFreshCopies=r7ReadLong(meter,nullFresh,"copiedEntries");
    long nullFreshChunks=r7ReadLong(meter,nullFresh,"copyChunks");
    java.util.Map<?,?> nullObservation=(java.util.Map<?,?>)r7ReadObject(meter,n,"observation");
    long nullObserverUnits=r7ReadMapLong(meter,nullObservation,"observerUnits");
    long nullObserverCopies=r7ReadMapLong(meter,nullObservation,"copiedEntries");
    long nullObserverChunks=r7ReadMapLong(meter,nullObservation,"copyChunks");
    java.util.Map<?,?> nullCleanup=(java.util.Map<?,?>)r7ReadObject(meter,n,"cleanup");
    long nullCleanupNanos=r7ReadMapLong(meter,nullCleanup,"cleanupNanos");

    Object observeFresh=r7ReadObject(meter,o,"fresh");
    long observeOwnerUnits=r7ReadLong(meter,observeFresh,"ownerUnits");
    long observeWrapperUnits=r7ReadLong(meter,observeFresh,"wrapperUnits");
    long observeGroups=r7ReadLong(meter,observeFresh,"groups");
    long observeFreshCopies=r7ReadLong(meter,observeFresh,"copiedEntries");
    long observeFreshChunks=r7ReadLong(meter,observeFresh,"copyChunks");
    java.util.Map<?,?> observeObservation=(java.util.Map<?,?>)r7ReadObject(meter,o,"observation");
    long observeObserverUnits=r7ReadMapLong(meter,observeObservation,"observerUnits");
    long observeObserverCopies=r7ReadMapLong(meter,observeObservation,"copiedEntries");
    long observeObserverChunks=r7ReadMapLong(meter,observeObservation,"copyChunks");
    java.util.Map<?,?> observeCleanup=(java.util.Map<?,?>)r7ReadObject(meter,o,"cleanup");
    long observeCleanupNanos=r7ReadMapLong(meter,observeCleanup,"cleanupNanos");

    /* These totals contain only detached scalar values; no owner/map is retained. */
    long observerUnits=r5Add(nullObserverUnits,observeObserverUnits);
    long cleanupNanos=r5Add(originalCleanup,r5Add(nullCleanupNanos,observeCleanupNanos));
    long freshGroups=r5Add(nullGroups,observeGroups);
    long freshCopies=r5Add(r5Add(nullFreshCopies,observeFreshCopies),
        r5Add(nullObserverCopies,observeObserverCopies));
    long freshChunks=r5Add(r5Add(nullFreshChunks,observeFreshChunks),
        r5Add(nullObserverChunks,observeObserverChunks));

    return captureDetachedR7WorkPlan(meter,started,result,
        setupUnits,setupGroups,setupCopies,setupChunks,
        originalOwnerUnits,originalWrapper,originalCallbackUnits,originalDiagnosticUnits,
        originalGroups,originalOwnerGroups,originalCopies,originalChunks,
        nullOwnerUnits,nullWrapperUnits,nullObserverUnits,
        observeOwnerUnits,observeWrapperUnits,observeObserverUnits,observerUnits,
        cleanupNanos,freshGroups,freshCopies,freshChunks,originalMaxNanos,originalMaxUnits);
}

/** Common plan owner for real detached values and the synthetic scalar canary. */
static R7WorkPlan captureDetachedR7WorkPlan(SetupMeter meter,long started,
        java.util.Map<String,Object> body,
        long setupUnits,long setupGroups,long setupCopies,long setupChunks,
        long originalOwnerUnits,long originalWrapperUnits,long originalCallbackUnits,
        long originalDiagnosticUnits,long originalGroups,long originalOwnerGroups,
        long originalCopies,long originalChunks,long nullOwnerUnits,long nullWrapperUnits,
        long nullObserverUnits,long observeOwnerUnits,long observeWrapperUnits,
        long observeObserverUnits,long observerUnits,long cleanupNanos,long freshGroups,
        long freshCopies,long freshChunks,long originalMaxNanos,long originalMaxUnits)
        throws Exception {
    if (meter==null || body==null) throw new java.io.IOException("R7_ARGUMENT");
    if (setupUnits<0 || setupGroups<0 || setupCopies<0 || setupChunks<0 ||
            originalOwnerUnits<0 || originalWrapperUnits<0 || originalCallbackUnits<0 ||
            originalDiagnosticUnits<0 || originalGroups<0 || originalOwnerGroups<0 ||
            originalCopies<0 || originalChunks<0 || nullOwnerUnits<0 || nullWrapperUnits<0 ||
            nullObserverUnits<0 || observeOwnerUnits<0 || observeWrapperUnits<0 ||
            observeObserverUnits<0 || observerUnits<0 || cleanupNanos<0 || freshGroups<0 ||
            freshCopies<0 || freshChunks<0 || originalMaxNanos<0 || originalMaxUnits<0)
        throw new java.io.IOException("R7_SOURCE_NEGATIVE");

    /* Prepay each lookup before touching the constructor's existing null slot. */
    meter.charge(1);
    if (!body.containsKey("work")) throw new java.io.IOException("R7_WORK_SLOT_MISSING");
    meter.charge(1);
    if (body.get("work")!=null) throw new java.io.IOException("R7_WORK_SLOT_NOT_EMPTY");

    meter.copy(R7_WORK_ENTRY_COPIES); // 34 work slots and one existing body.work replacement
    meter.charge(1); // LinkedHashMap allocation
    java.util.LinkedHashMap<String,Object> work=new java.util.LinkedHashMap<String,Object>(64);
    for (int i=0;i<R7_WORK_SLOTS;i++) work.put(r5WorkKey(i),Long.valueOf(0L));
    meter.charge(1); // replacement of the pre-existing null body.work slot
    body.put("work",work);

    R5Snapshot pre=allocateR5Snapshot(meter); // one unit precedes its allocation
    meter.charge(1); // R7WorkPlan allocation
    meter.copy(R7_PLAN_FIELD_COPIES); // started and 25 detached constructor scalars
    R7WorkPlan plan=new R7WorkPlan(meter,pre,body,work,started,
        setupUnits,setupGroups,setupCopies,setupChunks,
        originalOwnerUnits,originalWrapperUnits,originalCallbackUnits,originalDiagnosticUnits,
        originalGroups,originalOwnerGroups,originalCopies,originalChunks,
        nullOwnerUnits,nullWrapperUnits,nullObserverUnits,
        observeOwnerUnits,observeWrapperUnits,observeObserverUnits,observerUnits,
        cleanupNanos,freshGroups,freshCopies,freshChunks,originalMaxNanos,originalMaxUnits);

    CurrentBodyOwnership.validate(meter,body);
    /* One complete placeholder-body accounting pass before the Meter closes. */
    meter.charge(R7_CLOSED_SNAPSHOT_READS);
    accountResultTree(body,meter);
    meter.charge(R7_FREEZE_UNITS); // 18 subtractions, 4 adds, 2 maxima, 2 controls
    meter.charge(R7_FINALIZE_UNITS); // finalizer controls/checks before close
    meter.copy(R7_FINALIZE_ENTRY_COPIES); // 34 numeric slot replacements
    meter.finish();
    pre.captureClosed(meter,started);
    plan.freezeAfterCapture();
    return plan;
}

/** Flat detached counters, the current Meter/checkpoint, body and 34-slot map. */
static final class R7WorkPlan implements R5WorkFinalizer {
    private final SetupMeter meter;
    final R5Snapshot pre;
    private final java.util.Map<String,Object> body;
    private final java.util.Map<String,Object> work;
    private final long started;

    private final long setupUnits,setupGroups,setupCopies,setupChunks;
    private final long originalOwnerUnits,originalWrapperUnits,originalCallbackUnits;
    private final long originalDiagnosticUnits,originalGroups,originalOwnerGroups;
    private final long originalCopies,originalChunks;
    private final long nullOwnerUnits,nullWrapperUnits,nullObserverUnits;
    private final long observeOwnerUnits,observeWrapperUnits,observeObserverUnits,observerUnits;
    private final long cleanupNanos,freshGroups,freshCopies,freshChunks;
    private final long originalMaxNanos,originalMaxUnits;

    /* Filled after captureClosed, with no allocation or live-meter read. */
    private long unitsBeforeExport,groupsBeforeExport,wrapperGroupsBeforeExport;
    private long maxNanosBeforeExport,maxUnitsBeforeExport,meterUnits;
    private long metadataUnits,metadataGroups,metadataCopies,metadataChunks;
    private long copiedEntries,copyChunks;
    private boolean frozen,finalized;

    private R7WorkPlan(SetupMeter meter,R5Snapshot pre,java.util.Map<String,Object> body,
            java.util.Map<String,Object> work,long started,
            long setupUnits,long setupGroups,long setupCopies,long setupChunks,
            long originalOwnerUnits,long originalWrapperUnits,long originalCallbackUnits,
            long originalDiagnosticUnits,long originalGroups,long originalOwnerGroups,
            long originalCopies,long originalChunks,long nullOwnerUnits,long nullWrapperUnits,
            long nullObserverUnits,long observeOwnerUnits,long observeWrapperUnits,
            long observeObserverUnits,long observerUnits,long cleanupNanos,long freshGroups,
            long freshCopies,long freshChunks,long originalMaxNanos,long originalMaxUnits) {
        this.meter=meter; this.pre=pre; this.body=body; this.work=work; this.started=started;
        this.setupUnits=setupUnits; this.setupGroups=setupGroups;
        this.setupCopies=setupCopies; this.setupChunks=setupChunks;
        this.originalOwnerUnits=originalOwnerUnits; this.originalWrapperUnits=originalWrapperUnits;
        this.originalCallbackUnits=originalCallbackUnits;
        this.originalDiagnosticUnits=originalDiagnosticUnits; this.originalGroups=originalGroups;
        this.originalOwnerGroups=originalOwnerGroups; this.originalCopies=originalCopies;
        this.originalChunks=originalChunks;
        this.nullOwnerUnits=nullOwnerUnits; this.nullWrapperUnits=nullWrapperUnits;
        this.nullObserverUnits=nullObserverUnits; this.observeOwnerUnits=observeOwnerUnits;
        this.observeWrapperUnits=observeWrapperUnits; this.observeObserverUnits=observeObserverUnits;
        this.observerUnits=observerUnits; this.cleanupNanos=cleanupNanos;
        this.freshGroups=freshGroups; this.freshCopies=freshCopies; this.freshChunks=freshChunks;
        this.originalMaxNanos=originalMaxNanos; this.originalMaxUnits=originalMaxUnits;
    }

    public void verifyBound(SetupMeter m,R5Snapshot p,java.util.Map<?,?> suppliedBody,
            java.util.Map<?,?> suppliedWork) throws Exception {
        r5Require(!finalized && frozen && m==meter && p==pre &&
            suppliedBody==body && suppliedWork==work,"R7_FINALIZER_BINDING");
    }

    private void freezeAfterCapture() throws Exception {
        if (frozen || !pre.captureAttempted || !pre.captured || pre.boundMeter!=meter ||
                pre.started!=started || pre.prior!=originalOwnerGroups ||
                originalGroups<originalOwnerGroups || originalGroups<pre.prior)
            throw new java.io.IOException("R7_SNAPSHOT_BINDING");

        unitsBeforeExport=r5Add(pre.units,
            r5Add(originalOwnerUnits,r5Add(originalCallbackUnits,originalDiagnosticUnits)));
        meterUnits=unitsBeforeExport;
        groupsBeforeExport=r5Add(pre.groups,pre.prior);
        wrapperGroupsBeforeExport=pre.groups;
        maxNanosBeforeExport=Math.max(pre.maxNanos,originalMaxNanos);
        maxUnitsBeforeExport=Math.max(pre.maxUnits,originalMaxUnits);
        copiedEntries=pre.copies;
        copyChunks=pre.chunks;

        metadataUnits=r7Subtract(pre.units,setupUnits);
        metadataUnits=r7Subtract(metadataUnits,originalWrapperUnits);
        metadataUnits=r7Subtract(metadataUnits,nullOwnerUnits);
        metadataUnits=r7Subtract(metadataUnits,nullWrapperUnits);
        metadataUnits=r7Subtract(metadataUnits,nullObserverUnits);
        metadataUnits=r7Subtract(metadataUnits,observeOwnerUnits);
        metadataUnits=r7Subtract(metadataUnits,observeWrapperUnits);
        metadataUnits=r7Subtract(metadataUnits,observeObserverUnits);

        metadataGroups=r7Subtract(pre.groups,setupGroups);
        metadataGroups=r7Subtract(metadataGroups,r7Subtract(originalGroups,originalOwnerGroups));
        metadataGroups=r7Subtract(metadataGroups,freshGroups);

        metadataCopies=r7Subtract(pre.copies,setupCopies);
        metadataCopies=r7Subtract(metadataCopies,originalCopies);
        metadataCopies=r7Subtract(metadataCopies,freshCopies);
        metadataChunks=r7Subtract(pre.chunks,setupChunks);
        metadataChunks=r7Subtract(metadataChunks,originalChunks);
        metadataChunks=r7Subtract(metadataChunks,freshChunks);

        if (groupsBeforeExport>640L || originalGroups>groupsBeforeExport ||
                maxNanosBeforeExport>R5_EXPORT_NANOS || maxUnitsBeforeExport>32768L ||
                copiedEntries>1048576L || copyChunks>=Integer.MAX_VALUE ||
                cleanupNanos>120000000000L || originalMaxNanos>R5_EXPORT_NANOS ||
                originalMaxUnits>32768L)
            throw new java.io.IOException("R7_LEDGER_BOUND");
        frozen=true;
    }

    public void finalizeWork(long exportStartNanos) throws Exception {
        if (finalized) throw new java.io.IOException("R7_FINALIZER_REUSED");
        finalized=true; // consume before any check or slot replacement
        if (!frozen) throw new java.io.IOException("R7_NOT_FROZEN");
        long cutoff=r5Elapsed(started,exportStartNanos);
        if (cutoff<=0 || cutoff>R5_OPERATION_NANOS)
            throw new java.io.IOException("R7_ELAPSED_CUTOFF");
        Object boundWork=body.get("work"); // the sole body.work inspection in finalization
        if (boundWork!=work) throw new java.io.IOException("R7_FINALIZER_BINDING");
        for (int i=0;i<R7_WORK_SLOTS;i++)
            work.put(r5WorkKey(i),Long.valueOf(workValue(i,cutoff)));
    }

    private long workValue(int i,long cutoff) {
        switch(i) {
        case 0:return cutoff;
        case 1:return cleanupNanos;
        case 2:return groupsBeforeExport;
        case 3:return wrapperGroupsBeforeExport;
        case 4:return maxNanosBeforeExport;
        case 5:return maxUnitsBeforeExport;
        case 6:return unitsBeforeExport;
        case 7:return setupUnits;
        case 8:return setupGroups;
        case 9:return setupCopies;
        case 10:return setupChunks;
        case 11:return originalOwnerUnits;
        case 12:return originalWrapperUnits;
        case 13:return originalCallbackUnits;
        case 14:return originalDiagnosticUnits;
        case 15:return originalGroups;
        case 16:return originalCopies;
        case 17:return originalChunks;
        case 18:return nullOwnerUnits;
        case 19:return nullWrapperUnits;
        case 20:return nullObserverUnits;
        case 21:return observeOwnerUnits;
        case 22:return observeWrapperUnits;
        case 23:return observeObserverUnits;
        case 24:return observerUnits;
        case 25:return meterUnits;
        case 26:return metadataUnits;
        case 27:return metadataGroups;
        case 28:return metadataCopies;
        case 29:return metadataChunks;
        case 30:return copiedEntries;
        case 31:return copyChunks;
        case 32:return R5_BLOCK;
        case 33:return R5_RESULT_LIMIT;
        default:throw new IllegalArgumentException("R7_WORK_INDEX");
        }
    }
}
/* Nested Java7 members. Prospective R9 transport only; not R5 publication.
 * Requires canonical JSON, accountResultTree, SetupMeter, field,
 * exportCopyBlock, R5BodySink/R5CountSink and the existing r5 helpers. HOLD. */
static final int R9_CANARY_KEYS=7;
/* Three exact readNative invocations (capture, ready, closed) each inspect 19
 * native scalar/list values. Publication also reads groupStarted once and
 * the appended measure through get/nanos/units once: 3*19+1+3 = 61.
 * These fixed control inspections are prepaid before metadata closes. */
static final int R9_CLOSED_CONTROL_INSPECTIONS=61;
/* CountSink, stdout sink and scalar result metrics: allocated later, each
 * explicitly prepaid once in metadata. No additional export unit or ledger. */
static final int R9_FUTURE_CONTROL_ALLOCATIONS=3;

static String r9CanaryKey(int index) {
    switch(index) {
    case 0:return "schema"; case 1:return "status"; case 2:return "runId";
    case 3:return "sourceManifestSha256"; case 4:return "wireContractSha256";
    case 5:return "references"; case 6:return "sourceReview";
    default:throw new IllegalArgumentException("R9_CANARY_KEY");
    }
}
static void r9HeaderText(SetupMeter meter,Object value,int length,boolean uuid) throws Exception {
    meter.charge(1);
    r5Require(value instanceof String,"R9_HEADER_STRING");
    String text=(String)value;
    meter.charge(1);
    r5Require(text.length()==length,"R9_HEADER_LENGTH");
    int i=0;
    while (true) {
        meter.charge(1);
        if (i==length) break;
        meter.charge(1);
        char c=text.charAt(i);
        meter.charge(1);
        boolean hyphen=uuid && (i==8 || i==13 || i==18 || i==23);
        meter.charge(1);
        r5Require(hyphen?c=='-':(c>='0' && c<='9') || (c>='a' && c<='f'),
            "R9_HEADER_CANONICAL");
        i++;
    }
}
static void r9ValidateCanaryHeader(java.util.Map<String,Object> canary,SetupMeter meter)
        throws Exception {
    meter.charge(1);
    r5Require(canary!=null,"R9_CANARY_MISSING");
    meter.charge(1);
    r5Require(canary.size()==R9_CANARY_KEYS,"R9_CANARY_KEYS");
    meter.charge(1);
    java.util.Iterator<? extends java.util.Map.Entry<?,?>> it=canary.entrySet().iterator();
    int i=0;
    while (true) {
        meter.charge(1);
        if (i==R9_CANARY_KEYS) break;
        meter.charge(1); r5Require(it.hasNext(),"R9_CANARY_KEYS");
        meter.charge(1); java.util.Map.Entry<?,?> entry=it.next();
        meter.charge(1); Object key=entry.getKey();
        meter.charge(1); r5Require(r9CanaryKey(i).equals(key),"R9_CANARY_KEY_ORDER");
        meter.charge(1); Object value=entry.getValue();
        switch(i) {
        case 0:
            meter.charge(1);
            r5Require("tsj-generic-routing-reference-v1".equals(value),"R9_CANARY_SCHEMA");
            break;
        case 1:
            meter.charge(1);
            r5Require("GENERIC_NATURAL_REFERENCES_RECORDED_ONLY".equals(value),"R9_CANARY_STATUS");
            break;
        case 2:r9HeaderText(meter,value,36,true); break;
        case 3:case 4:r9HeaderText(meter,value,64,false); break;
        case 5:
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
            meter.charge(1);
            r5Require(((java.util.Map<?,?>)value).size()==2,"R9_REFERENCE_KEYS");
            break;
        case 6:
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
            meter.charge(1);
            r5Require(((java.util.Map<?,?>)value).size()==7,"R9_SOURCE_REVIEW_KEYS");
            break;
        }
        i++;
    }
    meter.charge(1); r5Require(!it.hasNext(),"R9_CANARY_KEYS");
}

static final class R9CanaryPublication {
    private final SetupMeter boundMeter;
    private final java.util.Map<String,Object> body;
    private final long started;
    private boolean captured,retired;
    private Object measures;
    private long units,groups,prior,copies,chunks,maxNanos,maxUnits,allowance;
    private long groupUnits,groupStarted,constructorEntries,measureCount;
    private long actualClosedNanos,actualMaxNanos,actualMaxUnits;
    private R9CanaryPublication(java.util.Map<String,Object> body,SetupMeter meter,long started) {
        this.body=body; boundMeter=meter; this.started=started;
    }
    /* Exactly 19 native scalar/list inspections on every successful invocation.
     * No temporary checkpoint, collection, or byte array is allocated here. */
    private void readNative(boolean capture,boolean after,long fee,long bytes,long blocks,
            long exportStarted) throws Exception {
        Object n=boundMeter.meter;
        boolean open=r5Flag(n,"open");                         // 1
        boolean ownerStarted=r5Flag(n,"originalOwnerStarted"); // 2
        boolean ownerActive=r5Flag(n,"originalOwnerActive");   // 3
        boolean ownerAdopted=r5Flag(n,"originalOwnerAdopted"); // 4
        long inFlight=r5Number(n,"inFlightOwnerGroups");       // 5
        long u=r5Number(n,"units");                            // 6
        long g=r5Number(n,"groups");                           // 7
        long p=r5Number(n,"priorGroups");                      // 8
        long e=r5Number(n,"copiedEntries");                    // 9
        long c=r5Number(n,"copyChunks");                       // 10
        long mn=r5Number(n,"maxGroupNanos");                   // 11
        long mu=r5Number(n,"maxGroupUnits");                   // 12
        long a=r5Number(n,"copyAllowance");                    // 13
        long gu=r5Number(n,"groupUnits");                      // 14
        long gs=r5Number(n,"groupStarted");                    // 15
        long s=r5Number(n,"started");                          // 16
        long ce=r5Number(n,"sessionConstructorEntries");       // 17
        Object list=field(n,"groupMeasures");                  // 18
        long count=((java.util.List<?>)list).size();            // 19
        r5Require(!open && !ownerStarted && !ownerActive && !ownerAdopted && inFlight==0 && p==0,
            "R9_OWNER_OR_OPEN_STATE");
        r5Require(s==started && u>=0 && g>=0 && g<=640 && count==g && e>=0 && e<=a &&
            a>=0 && a<=1048576 && c>=0 && c<=Integer.MAX_VALUE && mn>=0 && mn<=5000000000L &&
            mu>=0 && mu<=32768 && gu>=0 && gu<=32768 && ce>=0,"R9_NATIVE_BOUNDS");
        if (capture) {
            r5Require(!captured && !retired && g<640,"R9_CAPTURE_STATE");
            units=u; groups=g; prior=p; copies=e; chunks=c; maxNanos=mn; maxUnits=mu;
            allowance=a; groupUnits=gu; groupStarted=gs; constructorEntries=ce;
            measures=list; measureCount=count; captured=true;
            return;
        }
        r5Require(captured && list==measures && p==prior && a==allowance && ce==constructorEntries,
            "R9_STALE_CHECKPOINT");
        if (!after) {
            r5Require(u==units && g==groups && e==copies && c==chunks && mn==maxNanos &&
                mu==maxUnits && gu==groupUnits && gs==groupStarted && count==measureCount,
                "R9_STALE_CHECKPOINT");
        } else {
            r5Require(units<=Long.MAX_VALUE-fee && u==units+fee && g==groups+1 &&
                e==copies+bytes && c==chunks+blocks && gu==fee && gs==exportStarted &&
                count==measureCount+1 && mn==Math.max(maxNanos,actualClosedNanos) &&
                mu==Math.max(maxUnits,fee),"R9_PUBLICATION_LEDGER");
            actualMaxNanos=mn; actualMaxUnits=mu;
        }
    }
    private void retire(java.util.Map<String,Object> canary,SetupMeter meter,long operationStarted)
            throws Exception {
        r5Require(!retired,"R9_PUBLICATION_REUSED");
        retired=true; // any attempt, including a stale/foreign one, consumes it
        r5Require(captured && canary==body && meter==boundMeter && operationStarted==started,
            "R9_PUBLICATION_BINDING");
        readNative(false,false,0,0,0,0);
    }
    private void verifyClosed(long fee,long bytes,long blocks,long exportStarted) throws Exception {
        r5Require(retired && captured,"R9_PUBLICATION_STATE");
        Object measure=((java.util.List<?>)measures).get((int)measureCount); // +1
        actualClosedNanos=r5Number(measure,"nanos");                       // +1
        long measuredUnits=r5Number(measure,"units");                     // +1
        r5Require(measuredUnits==fee && actualClosedNanos>=0 && actualClosedNanos<=5000000000L,
            "R9_MEASURED_PUBLICATION");
        readNative(false,true,fee,bytes,blocks,exportStarted);
    }
}

static R9CanaryPublication prepareR9CanaryPublication(java.util.Map<String,Object> canary,
        SetupMeter meter,long started) throws Exception {
    if (meter==null) throw new java.io.IOException("R9_METER_MISSING");
    meter.charge(1); // checkpoint allocation, before construction
    R9CanaryPublication pre=new R9CanaryPublication(canary,meter,started);
    r9ValidateCanaryHeader(canary,meter);
    validateR9CanaryTextPolicy(canary,meter); // exact paths and text, charged metadata
    accountResultTree(canary,meter); // once over this complete body; no fake work slots
    meter.charge(R9_FUTURE_CONTROL_ALLOCATIONS);
    meter.charge(R9_CLOSED_CONTROL_INSPECTIONS);
    meter.finish();
    pre.readNative(true,false,0,0,0,0);
    return pre;
}

static final class R9CanaryStdoutSink extends R5BodySink {
    private final long length;
    private final java.io.PrintStream stdout;
    private byte[] block;
    private int used;
    private boolean wrote,completed;
    private R9CanaryStdoutSink(SetupMeter m,long started,long exportStarted,long length,
            java.io.PrintStream stdout) {
        super(m,started,exportStarted); this.length=length; this.stdout=stdout;
    }
    void ensureBlock() throws Exception {
        r5Require(position<length,"R9_EMIT_LENGTH");
        if (block==null) {
            int n=(int)Math.min(4096L,length-position);
            exportCopyBlock(meter,n); // B: charge/admit exact n before new[]/fill
            block=new byte[n]; used=0;
        }
    }
    void accept(int ascii) { block[used++]=(byte)ascii; }
    void afterByte() throws Exception {
        if (used!=block.length) return;
        r5Charge(meter,started,exportStarted); // C: before each actual segment write
        if (!wrote) r5Require(!stdout.checkError(),"R9_STDOUT_PRIOR_ERROR");
        stdout.write(block,0,used); wrote=true;
        block=null; used=0;
        if (position==length) {
            r5Require(ended,"R9_MISSING_FINAL_LF");
            stdout.flush();
        } else r5Require(!ended,"R9_EARLY_LF");
        r5Require(!stdout.checkError(),"R9_STDOUT_ERROR");
        if (position==length) completed=true;
    }
}
static final class R9CanaryPublicationMetrics {
    final long bytes,blocks,exportUnits,exportNanos,elapsedNanos;
    final long unitsBeforeExport,groupsBeforeExport,totalUnits,totalGroups;
    final long copiedEntriesDelta,copyChunksDelta,maxGroupNanos,maximumUnitsPerGroup;
    private R9CanaryPublicationMetrics(R9CanaryPublication pre,long bytes,long blocks,
            long fee,long elapsed) {
        this.bytes=bytes; this.blocks=blocks; exportUnits=fee; elapsedNanos=elapsed;
        exportNanos=pre.actualClosedNanos; unitsBeforeExport=pre.units; groupsBeforeExport=pre.groups;
        totalUnits=pre.units+fee; totalGroups=pre.groups+1;
        copiedEntriesDelta=bytes; copyChunksDelta=blocks;
        maxGroupNanos=pre.actualMaxNanos; maximumUnitsPerGroup=pre.actualMaxUnits;
    }
}
static R9CanaryPublicationMetrics publishR9CanaryStdout(java.util.Map<String,Object> canary,
        SetupMeter meter,long started,R9CanaryPublication pre) throws Exception {
    r5Require(pre!=null,"R9_PUBLICATION_MISSING");
    pre.retire(canary,meter,started);
    meter.method("beginGroup",new Class<?>[0]);
    long exportStarted=r5Number(meter.meter,"groupStarted"); // prepaid inspection +1
    // Both following objects and final scalar metrics were explicitly prepaid
    // in metadata. CanonicalJsonVisits uses the formatter's first block credit.
    R5CountSink count=new R5CountSink(meter,started,exportStarted);
    writeCanonicalJsonCanaryValues(canary,count); // A: actual virtual C blocks, no body array
    count.complete(count.position);
    long bytes=count.position,blocks=(bytes+4095L)/4096L,fee=3*blocks;
    r5Require(bytes>=1 && bytes<=1048576 && blocks>=1 && fee<=32768 &&
        bytes<=pre.allowance-pre.copies && pre.chunks<=Integer.MAX_VALUE-blocks &&
        pre.units<=Long.MAX_VALUE-fee,"R9_PUBLICATION_CAPACITY");
    R9CanaryStdoutSink writer=new R9CanaryStdoutSink(meter,started,exportStarted,bytes,System.out);
    writeCanonicalJsonCanaryValues(canary,writer); // B/C: one segment, one real admission/write at a time
    writer.complete(bytes);
    r5Require(writer.completed && writer.block==null,"R9_PUBLICATION_INCOMPLETE");
    r5Deadline(meter,started,exportStarted); // group still open through LF/flush/error checks
    meter.method("finishGroup",new Class<?>[0]);
    pre.verifyClosed(fee,bytes,blocks,exportStarted);
    r5Deadline(meter,started,exportStarted); // retain both deadlines after actual close
    long now=System.nanoTime(),elapsed=r5Elapsed(started,now);
    r5Require(elapsed<=90000000000L && r5Elapsed(exportStarted,now)<=5000000000L,
        "R9_PUBLICATION_DEADLINE");
    return new R9CanaryPublicationMetrics(pre,bytes,blocks,fee,elapsed);
}


/* Nested Java7 members. Prospective R9 canary-only text policy, metadata use.
 * Uses R9CanonicalJson's shared pure path helpers; no duplicate whitelist,
 * default formatter change, copied tree, path String, or retained body. */
private static final class R9TextPolicyLedger { long visits; }
private static void r9TextPolicyRequire(boolean ok,String reason) throws java.io.IOException {
    if (!ok) throw new java.io.IOException(reason);
}
static void validateR9CanaryTextPolicy(Object body,SetupMeter meter) throws Exception {
    if (meter==null) throw new java.io.IOException("R9_TEXT_POLICY_METER_MISSING");
    meter.charge(1); // before control ledger allocation
    R9TextPolicyLedger ledger=new R9TextPolicyLedger();
    r9ValidateTextNode(body,R9_PATH_ROOT,0,ledger,meter);
}
private static void r9ValidatePolicyString(String text,int state,SetupMeter meter)
        throws Exception {
    meter.charge(1); int limit=r9PathStringLimit(state);
    meter.charge(1); int length=text.length();
    meter.charge(1); r9TextPolicyRequire(length<=limit,"R9_TEXT_POLICY_LENGTH");
    int i=0;
    while (true) {
        meter.charge(1); if (i==length) break;
        meter.charge(1); char c=text.charAt(i++);
        meter.charge(1);
        r9TextPolicyRequire(c>=32 && c<=126,"R9_TEXT_POLICY_ASCII");
    }
}
private static void r9ValidateTextNode(Object value,int state,int depth,
        R9TextPolicyLedger ledger,SetupMeter meter) throws Exception {
    meter.charge(1); r9TextPolicyRequire(depth<=64,"R9_TEXT_POLICY_DEPTH");
    meter.charge(1); r9TextPolicyRequire(ledger.visits<1048576L,"R9_TEXT_POLICY_VISITS");
    meter.charge(1); ledger.visits++;
    // Fixed R9 metadata operation: shared dispatch + optional null/type tests.
    meter.charge(3);
    r9TextPolicyRequire(r9PathTypeValid(state,value),"R9_TEXT_POLICY_STRUCTURE");
    meter.charge(1);
    if (value instanceof java.util.Map) {
        meter.charge(1);
        java.util.Iterator<? extends java.util.Map.Entry<?,?>> entries=
            ((java.util.Map<?,?>)value).entrySet().iterator();
        while (true) {
            meter.charge(1); if (!entries.hasNext()) break;
            meter.charge(1); java.util.Map.Entry<?,?> entry=entries.next();
            meter.charge(1); Object key=entry.getKey();
            meter.charge(1); r9TextPolicyRequire(key instanceof String,"R9_TEXT_POLICY_KEY_TYPE");
            r9ValidatePolicyString((String)key,R9_PATH_OTHER,meter); // keys never inherit value exemption
            // Fixed R9 metadata operation: shared dispatch + at most 3 equalities.
            meter.charge(4);
            int childState=r9PathMapChild(state,(String)key);
            meter.charge(1); Object child=entry.getValue();
            r9ValidateTextNode(child,childState,depth+1,ledger,meter);
        }
        return;
    }
    meter.charge(1);
    if (value instanceof java.util.List) {
        meter.charge(1);
        java.util.Iterator<?> entries=((java.util.List<?>)value).iterator();
        meter.charge(1);
        int childState=r9PathListChild(state);
        while (true) {
            meter.charge(1); if (!entries.hasNext()) break;
            meter.charge(1); Object child=entries.next();
            // Only an actual components List iteration creates COMPONENT state.
            r9ValidateTextNode(child,childState,depth+1,ledger,meter);
        }
        return;
    }
    meter.charge(1);
    if (value instanceof String) {
        r9ValidatePolicyString((String)value,state,meter);
        return;
    }
    meter.charge(1); if (value instanceof Integer) return;
    meter.charge(1); if (value instanceof Long) return;
    meter.charge(1); if (value instanceof Boolean) return;
    meter.charge(1); if (value==null) return;
    throw new java.io.IOException("R9_TEXT_POLICY_TYPE");
}


/* R9 generic callback-throw and direct-owner abort/reuse canaries.
 * Root integrates this source fragment; it contains no executed evidence. */

static final class R9ThrowingTerminalDiagnostics
        implements PcbLayerRoutingPrototype.TerminalOnlyDiagnostics {
    final Throwable sentinel;
    final Object meter;
    final java.lang.reflect.Field unitsField;
    int callbackCount;
    long callbackExitMeterUnits=-1L;

    R9ThrowingTerminalDiagnostics(Throwable sentinel,Object meter,
            java.lang.reflect.Field unitsField) {
        this.sentinel=sentinel;
        this.meter=meter;
        this.unitsField=unitsField;
    }

    public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
        callbackCount++;
        try {
            if (sentinel instanceof RuntimeException)
                throw (RuntimeException)sentinel;
            if (sentinel instanceof Error) throw (Error)sentinel;
            throw new IllegalStateException("R9 observer requires an unchecked sentinel");
        } catch (RuntimeException failure) {
            sampleCallbackExit();
            throw failure;
        } catch (Error failure) {
            sampleCallbackExit();
            throw failure;
        }
    }

    private void sampleCallbackExit() {
        if (unitsField==null) return;
        try {
            callbackExitMeterUnits=unitsField.getLong(meter);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("R9 callback Meter read failed",failure);
        }
    }
}

/** Actual generic parent runFresh exception path; this is not FaultDiagnostics. */
static Map<String,Object> runR9GenericCallbackThrow(SetupMeter m,long started)
        throws Exception {
    if (m==null || m.parent==null || m.meter==null)
        throw new IOException("R9_GENERIC_THROW_INPUT");
    Parent parent=m.parent;
    FailedFirstFixtures.Fixture fixture=null;
    TroubleshootBoard board=null;
    PcbBoardLayout layout=null;
    Cohort cohort=null;
    Throwable sentinel=null,caught=null;
    R9ThrowingTerminalDiagnostics observer=null;
    Map<String,Object> result=null;
    try {
        m.charge(1); // bounded immutable fixture factory call, before construction
        fixture=FailedFirstFixtures.open();
        m.charge(2);
        m.copy(2);
        board=fixture.board;
        layout=fixture.layout;
        cohort=r9FixtureCohort(R9CanaryProfile.FAILED_FIRST_OPEN,m);
        m.charge(1);
        sentinel=new IllegalStateException("R9 generic terminal callback sentinel");

        /* Prepay field discovery and all four Meter reads before entering runFresh. */
        m.charge(6);
        java.lang.reflect.Field unitsField=r9AccessibleMeterField(m.meter,"units");
        java.lang.reflect.Field constructorField=
            r9AccessibleMeterField(m.meter,"sessionConstructorEntries");
        m.charge(1);
        m.copy(3);
        observer=new R9ThrowingTerminalDiagnostics(sentinel,m.meter,unitsField);
        m.charge(6); // four Meter reads plus the two stored observer-field reads
        int constructorEntriesBefore=constructorField.getInt(m.meter);

        try {
            parent.fresh(board,layout,null,cohort.netIds,m.meter,started,observer);
        } catch (Throwable failure) {
            caught=failure;
        }
        /* Immediate after-invoke read: no meter charge or validation precedes it. */
        long afterInvokeMeterUnits=unitsField.getLong(m.meter);
        int constructorEntriesAfter=constructorField.getInt(m.meter);
        long callbackExitMeterUnits=observer.callbackExitMeterUnits;
        if (observer.callbackCount!=1 || callbackExitMeterUnits<0L)
            throw new IOException("R9_GENERIC_THROW_CALLBACK_MISSING");

        m.charge(5);
        m.copy(5);
        result=new java.util.LinkedHashMap<String,Object>();
        result.put("caughtSameThrowable",Boolean.valueOf(caught==sentinel));
        result.put("constructorEntriesBefore",Integer.valueOf(constructorEntriesBefore));
        result.put("constructorEntriesAfter",Integer.valueOf(constructorEntriesAfter));
        result.put("callbackExitMeterUnits",Long.valueOf(callbackExitMeterUnits));
        result.put("afterInvokeMeterUnits",Long.valueOf(afterInvokeMeterUnits));
    } finally {
        fixture=null;
        board=null;
        layout=null;
        cohort=null;
        observer=null;
        sentinel=null;
        caught=null;
        parent=null;
    }
    return result;
}

private static java.lang.reflect.Field r9AccessibleMeterField(Object meter,String name)
        throws Exception {
    java.lang.reflect.Field result=meter.getClass().getDeclaredField(name);
    result.setAccessible(true);
    return result;
}

/** Direct F Session abort/retry against a fresh immutable N OPEN fixture. */
static Map<String,Object> runR9DirectOwnerAbortReuse(SetupMeter m,long started)
        throws Exception {
    if (m==null || m.parent==null || m.meter==null)
        throw new IOException("R9_DIRECT_ABORT_INPUT");
    Parent parent=m.parent;
    FailedFirstFixtures.Fixture fixture=null;
    TroubleshootBoard board=null;
    PcbBoardLayout layout=null;
    PcbLayerRoutingPrototype.Session session=null;
    R9ThrowingTerminalDiagnostics observer=null;
    Throwable sentinel=null,caught=null,advanceRetry=null,resultRetry=null;
    String sourceBefore=null,sourceAfter=null;
    String sentinelKind=null;
    int lastWork=-1;
    boolean caughtSameThrowable=false;
    boolean diagnosticFailureSameThrowable=false;
    boolean searchCleared=false,candidateCleared=false,resultCleared=false;
    boolean retrySameThrowable=false,sourcePlacementUnchanged=false;
    boolean noRoutesBefore=false,noRoutesAfter=false,harnessAliasesDropped=false;

    try {
        m.charge(1);
        sentinel=new IllegalStateException("R9 direct Session callback sentinel");
        m.charge(1);
        sentinelKind=sentinel.getClass().getName();
        m.charge(1); // bounded immutable fixture factory call, before construction
        fixture=FailedFirstFixtures.open();
        m.charge(2);
        m.copy(2);
        board=fixture.board;
        layout=fixture.layout;
        sourceBefore=geometry(parent,m,layout);
        noRoutesBefore=r9FixtureNoRoutes(layout,m);
        m.charge(1);
        m.copy(1);
        observer=new R9ThrowingTerminalDiagnostics(sentinel,null,null);
        m.charge(1);
        m.copy(5); // direct Session retained board/policy/observer/layout references
        session=PcbLayerRoutingPrototype.begin(board,layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
            P06FactoryLinkFixtures.OBSERVER,PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,observer);

        boolean done=false;
        for (int call=0;call<1000 && caught==null && !done;call++) {
            m.charge(2); // direct advance dispatch and following lastWork read
            try {
                done=session.advanceSlice(1);
            } catch (Throwable failure) {
                caught=failure;
            }
            lastWork=session.lastWork();
            if (lastWork>0) m.charge(lastWork);
            else m.charge(1);
        }
        m.charge(1);
        if (observer.callbackCount!=1)
            throw new IOException("R9_DIRECT_ABORT_CALLBACK_COUNT");
        caughtSameThrowable=(caught==sentinel);
        if (caught!=null) {
            m.charge(1);
            try { session.advanceSlice(1); }
            catch (Throwable failure) { advanceRetry=failure; }
            m.charge(1);
            try { session.result(); }
            catch (Throwable failure) { resultRetry=failure; }
        }
        retrySameThrowable=caught==sentinel && advanceRetry==sentinel &&
            resultRetry==sentinel;

        /* Read the owner latch and cleared fields from the actual failed Session. */
        m.charge(4);
        diagnosticFailureSameThrowable=field(session,"diagnosticFailure")==sentinel &&
            caught==sentinel;
        searchCleared=field(session,"search")==null;
        candidateCleared=field(session,"candidate")==null;
        resultCleared=field(session,"result")==null;

        sourceAfter=geometry(parent,m,layout);
        noRoutesAfter=r9FixtureNoRoutes(layout,m);
        m.charge(1);
        sourcePlacementUnchanged=sourceBefore.equals(sourceAfter) &&
            noRoutesBefore && noRoutesAfter;
    } finally {
        session=null;
        fixture=null;
        board=null;
        layout=null;
        observer=null;
        sentinel=null;
        caught=null;
        advanceRetry=null;
        resultRetry=null;
        sourceBefore=null;
        sourceAfter=null;
        parent=null;
        harnessAliasesDropped=session==null && fixture==null && board==null &&
            layout==null && observer==null && sentinel==null && caught==null &&
            advanceRetry==null && resultRetry==null && sourceBefore==null &&
            sourceAfter==null && parent==null;
    }

    m.charge(10);
    m.copy(10);
    Map<String,Object> result=new java.util.LinkedHashMap<String,Object>();
    result.put("sentinelKind",sentinelKind);
    result.put("caughtSameThrowable",Boolean.valueOf(caughtSameThrowable));
    result.put("lastWork",Integer.valueOf(lastWork));
    result.put("diagnosticFailureSameThrowable",
        Boolean.valueOf(diagnosticFailureSameThrowable));
    result.put("searchCleared",Boolean.valueOf(searchCleared));
    result.put("candidateCleared",Boolean.valueOf(candidateCleared));
    result.put("resultCleared",Boolean.valueOf(resultCleared));
    result.put("retrySameThrowable",Boolean.valueOf(retrySameThrowable));
    result.put("sourcePlacementUnchanged",Boolean.valueOf(sourcePlacementUnchanged));
    result.put("harnessAliasesDropped",Boolean.valueOf(harnessAliasesDropped));
    return result;
}

private static boolean r9FixtureNoRoutes(PcbBoardLayout layout,SetupMeter m)
        throws Exception {
    if (layout==null || m==null) throw new IOException("R9_DIRECT_LAYOUT");
    m.charge(2);
    Object rawTraces=field(layout,"traces");
    Object rawHoles=field(layout,"holes");
    if (!(rawTraces instanceof java.util.Vector) || !(rawHoles instanceof Map))
        throw new IOException("R9_DIRECT_SOURCE_SHAPE");
    m.charge(2);
    int traceCount=((java.util.Vector<?>)rawTraces).size();
    int holeCount=((Map<?,?>)rawHoles).size();
    if (traceCount<0 || holeCount<0 || traceCount>4096 || holeCount>4096)
        throw new IOException("R9_DIRECT_SOURCE_SIZE");
    m.charge(2); // returned trace and hole vector allocations
    if (traceCount>0) m.copy(traceCount);
    if (holeCount>0) m.copy(holeCount);
    java.util.Vector<PcbTraceGeometry> traces=layout.getTraces();
    java.util.Vector<PcbBoardHole> holes=layout.getHoles();
    m.charge(2);
    return traces.size()==0 && holes.size()==0;
}

/* Native R9 mode assembly. All returned DTOs belong exclusively to this stack.
 * No original capture, authored board, listener, or publisher callback receives
 * the body. The caller discards it after its one synchronous publication. */
private static Map<String,Object> r9CanaryArmDto(Arguments args,SetupMeter m,
        R9CanaryProfile profile,String id,long started) throws Exception {
    ArmResult arm=null;
    try {
        m.charge(2); // the before/after constructor counter reads
        long before=r5Number(m.meter,"sessionConstructorEntries");
        m.finish();
        arm=runR9FixtureArm(args,m,profile,id,started);
        long after=r5Number(m.meter,"sessionConstructorEntries");
        m.charge(1);
        r5Require(after-before==1,"R9_FIXTURE_CONSTRUCTOR_COUNT");
        m.charge(1); Object run=field(arm.fresh,"json");
        m.copy(5);
        Map<String,Object> dto=new java.util.LinkedHashMap<String,Object>();
        dto.put("armRole","OBSERVE"); dto.put("armId",id);
        dto.put("freshRun",run); dto.put("observation",arm.observation);
        dto.put("cleanup",arm.cleanup);
        m.finish();
        return dto;
    } finally {
        arm=null; // FreshRun's graph aliases were dropped by the parent, then ours.
    }
}
static void runR9OwnerCanaries(Arguments args,long started) throws Exception {
    throw new IllegalStateException("SELECTED_WIRE_CANARY_ONLY");
}


/* Checked original attribution scalar controls. The caller prepays all9 units. */
private static long r7BoundaryLong(Object value) throws java.io.IOException {
    if (value == null || value.getClass() != Long.class)
        throw new java.io.IOException("R7_BOUNDARY_LONG");
    long number = ((Long)value).longValue();
    if (number < 0L) throw new java.io.IOException("R7_BOUNDARY_NEGATIVE");
    return number;
}
private static int r7BoundaryInt(Object value) throws java.io.IOException {
    if (value == null || value.getClass() != Integer.class)
        throw new java.io.IOException("R7_BOUNDARY_INT");
    int number = ((Integer)value).intValue();
    if (number < 0) throw new java.io.IOException("R7_BOUNDARY_NEGATIVE");
    return number;
}

}
