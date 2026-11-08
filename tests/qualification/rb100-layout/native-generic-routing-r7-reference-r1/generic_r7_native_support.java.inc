/* Source-only fragments consumed by generate_generic_r7_export_overlay.py.
 * Root must pin this reviewed input before generation; this file is never
 * compiled or executed directly. All fragments preserve the T Study owners.
 */
/*<GENERIC_ARGUMENTS>*/
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
/*</GENERIC_ARGUMENTS>*/

/*<OUTPUT_CLASS>*/
static final class R5Output {
    final java.nio.file.Path directory,result;
    final R5FileIdentity directoryIdentity;
    R5FileIdentity resultIdentity;
    R5Output(java.nio.file.Path directory) throws Exception {
        this.directory=directory; result=directory.resolve("result.json");
        /* Check every ancestor without following a reparse path. */
        java.nio.file.Path part=directory;
        while (part!=null) { new R5FileIdentity(part,true); part=part.getParent(); }
        directoryIdentity=new R5FileIdentity(directory,true);
        closure(false,-1);
    }
    void closure(boolean hasResult,long resultBytes) throws Exception {
        directoryIdentity.verify(directory,true,false);
        int count=0;
        try (java.nio.file.DirectoryStream<java.nio.file.Path> entries=
                java.nio.file.Files.newDirectoryStream(directory)) {
            for (java.nio.file.Path entry:entries) {
                count++;
                r5Require(count<=(hasResult?1:0) && hasResult && entry.equals(result),
                    "R5_OUTPUT_EXTRA_FILE");
                r5Require("result.json".equals(entry.getFileName().toString()),
                    "R5_OUTPUT_NAME_ALIAS");
            }
        }
        r5Require(count==(hasResult?1:0),"R5_OUTPUT_CLOSURE");
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
/*</OUTPUT_CLASS>*/

/*<WORK_VALIDATION>*/
private static String r7RootKey(int i) {
    switch(i) {
    case 0:return "setup"; case 1:return "open"; case 2:return "blocked";
    case 3:return "metadata"; case 4:return "preExport"; case 5:return "limits";
    default:throw new IllegalArgumentException("R7_WORK_INDEX");
    }
}
private static String r7PhaseKey(int i) {
    switch(i) {
    case 0:return "units"; case 1:return "groups";
    case 2:return "copiedEntries"; case 3:return "copyChunks";
    default:throw new IllegalArgumentException("R7_PHASE_INDEX");
    }
}
private static String r7PreExportKey(int i) {
    switch(i) {
    case 0:return "units"; case 1:return "groups"; case 2:return "copiedEntries";
    case 3:return "copyChunks"; case 4:return "maxGroupNanos";
    case 5:return "maximumUnitsPerGroup"; case 6:return "sessionConstructorEntries";
    case 7:return "elapsedNanos";
    default:throw new IllegalArgumentException("R7_PREEXPORT_INDEX");
    }
}
private static String r7LimitKey(int i) {
    switch(i) {
    case 0:return "producerNanos"; case 1:return "maximumGroups";
    case 2:return "maximumGroupNanos"; case 3:return "maximumUnitsPerGroup";
    case 4:return "maximumCopiedEntries"; case 5:return "copyChunkSize";
    default:throw new IllegalArgumentException("R7_LIMIT_INDEX");
    }
}
private static void r7OrderedShape(java.util.Map<?,?> map,int kind,int count) throws Exception {
    r5Require(map!=null && map.size()==count,"R7_WORK_SHAPE");
    java.util.Iterator<? extends java.util.Map.Entry<?,?>> it=map.entrySet().iterator();
    for (int i=0;i<count;i++) {
        r5Require(it.hasNext(),"R7_WORK_SHAPE");
        java.util.Map.Entry<?,?> entry=it.next();
        String expected=kind==0?r7RootKey(i):kind==1?r7PhaseKey(i):
            kind==2?r7PreExportKey(i):r7LimitKey(i);
        r5Require(expected.equals(entry.getKey()),"R7_WORK_ORDER");
        if (kind==0) r5Require(entry.getValue() instanceof java.util.Map,"R7_WORK_MAP");
        else {
            Object value=entry.getValue();
            r5Require((value instanceof Integer || value instanceof Long) &&
                ((Number)value).longValue()>=0,"R7_WORK_INTEGER");
        }
    }
    r5Require(!it.hasNext(),"R7_WORK_SHAPE");
}
static void r5WorkShape(java.util.Map<?,?> work) throws Exception {
    r7OrderedShape(work,0,6);
    r7OrderedShape((java.util.Map<?,?>)work.get("setup"),1,4);
    r7OrderedShape((java.util.Map<?,?>)work.get("open"),1,4);
    r7OrderedShape((java.util.Map<?,?>)work.get("blocked"),1,4);
    r7OrderedShape((java.util.Map<?,?>)work.get("metadata"),1,4);
    r7OrderedShape((java.util.Map<?,?>)work.get("preExport"),2,8);
    r7OrderedShape((java.util.Map<?,?>)work.get("limits"),3,6);
}
private static long r5WorkNumber(java.util.Map<?,?> work,String key) throws Exception {
    Object value=work.get(key);
    r5Require(value instanceof Integer || value instanceof Long,"R7_WORK_INTEGER");
    long n=((Number)value).longValue();
    r5Require(n>=0,"R7_WORK_INTEGER");
    return n;
}
private static long r5Add(long a,long b) throws Exception {
    r5Require(a>=0 && b>=0 && a<=Long.MAX_VALUE-b,"R5_INTEGER_OVERFLOW");
    return a+b;
}
private static long r7PhaseValue(java.util.Map<?,?> work,String phase,String key)
        throws Exception {
    Object value=work.get(phase);
    r5Require(value instanceof java.util.Map,"R7_WORK_MAP");
    return r5WorkNumber((java.util.Map<?,?>)value,key);
}
static void r5ValidateWork(java.util.Map<?,?> work,R5Snapshot pre,long exportStarted)
        throws Exception {
    r5WorkShape(work);
    java.util.Map<?,?> preExport=(java.util.Map<?,?>)work.get("preExport");
    java.util.Map<?,?> limits=(java.util.Map<?,?>)work.get("limits");
    long setupUnits=r7PhaseValue(work,"setup","units");
    long openUnits=r7PhaseValue(work,"open","units");
    long blockedUnits=r7PhaseValue(work,"blocked","units");
    long metadataUnits=r7PhaseValue(work,"metadata","units");
    long setupGroups=r7PhaseValue(work,"setup","groups");
    long openGroups=r7PhaseValue(work,"open","groups");
    long blockedGroups=r7PhaseValue(work,"blocked","groups");
    long metadataGroups=r7PhaseValue(work,"metadata","groups");
    long setupCopies=r7PhaseValue(work,"setup","copiedEntries");
    long openCopies=r7PhaseValue(work,"open","copiedEntries");
    long blockedCopies=r7PhaseValue(work,"blocked","copiedEntries");
    long metadataCopies=r7PhaseValue(work,"metadata","copiedEntries");
    long setupChunks=r7PhaseValue(work,"setup","copyChunks");
    long openChunks=r7PhaseValue(work,"open","copyChunks");
    long blockedChunks=r7PhaseValue(work,"blocked","copyChunks");
    long metadataChunks=r7PhaseValue(work,"metadata","copyChunks");
    long units=r5WorkNumber(preExport,"units"),groups=r5WorkNumber(preExport,"groups");
    long copies=r5WorkNumber(preExport,"copiedEntries"),chunks=r5WorkNumber(preExport,"copyChunks");
    long maxNanos=r5WorkNumber(preExport,"maxGroupNanos");
    long maxUnits=r5WorkNumber(preExport,"maximumUnitsPerGroup");
    long constructors=r5WorkNumber(preExport,"sessionConstructorEntries");
    long elapsed=r5WorkNumber(preExport,"elapsedNanos");
    r5Require(units==pre.units && groups==pre.groups && pre.prior==0 &&
        copies==pre.copies && chunks==pre.chunks && maxNanos==pre.maxNanos &&
        maxUnits==pre.maxUnits && constructors==pre.constructorEntries && constructors==2 &&
        elapsed==r5Elapsed(pre.started,exportStarted),"R7_PREEXPORT_METER_MISMATCH");
    r5Require(r5Add(r5Add(r5Add(setupUnits,openUnits),blockedUnits),metadataUnits)==units &&
        r5Add(r5Add(r5Add(setupGroups,openGroups),blockedGroups),metadataGroups)==groups &&
        r5Add(r5Add(r5Add(setupCopies,openCopies),blockedCopies),metadataCopies)==copies &&
        r5Add(r5Add(r5Add(setupChunks,openChunks),blockedChunks),metadataChunks)==chunks,
        "R7_PHASE_EQUATIONS");
    r5Require(r5WorkNumber(limits,"producerNanos")==R5_OPERATION_NANOS &&
        r5WorkNumber(limits,"maximumGroups")==640 &&
        r5WorkNumber(limits,"maximumGroupNanos")==R5_EXPORT_NANOS &&
        r5WorkNumber(limits,"maximumUnitsPerGroup")==32768 &&
        r5WorkNumber(limits,"maximumCopiedEntries")==R5_RESULT_LIMIT &&
        r5WorkNumber(limits,"copyChunkSize")==R5_BLOCK,"R7_FIXED_LIMITS");
    r5Require(units>0 && units<=640L*32768L && groups<640 &&
        copies<=R5_RESULT_LIMIT && chunks>=0 && chunks<Integer.MAX_VALUE &&
        maxNanos>0 && maxNanos<=R5_EXPORT_NANOS && maxUnits>0 && maxUnits<=32768 &&
        elapsed>0 && elapsed<=R5_OPERATION_NANOS,"R7_LEDGER_BOUND");
}
/*</WORK_VALIDATION>*/

/*<MARKER_METHODS>*/
static void r5Marker(R5MarkerSink sink,GenericR7Arguments args,byte[] digest,long resultBytes,
        long elapsed,long exportNanos,long groups,long units,long maxNanos,long maxUnits)
        throws Exception {
    r5Require(digest!=null && digest.length==32,"R5_DIGEST_LENGTH");
    sink.writeByte('{');
    r5MarkerString(sink,"schema","tsj-generic-routing-r7-marker-v1",true);
    r5MarkerString(sink,"status","GENERIC_NATURAL_R7_REFERENCE_EXPORTED",false);
    r5MarkerString(sink,"runId",args.runId,false);
    r5MarkerString(sink,"sourceManifestSha256",args.manifestSha,false);
    r5MarkerString(sink,"wireContractSha256",args.wireSha,false);
    r5MarkerString(sink,"fixtureOracleSha256",args.fixtureOracleSha,false);
    r5MarkerKey(sink,"resultSha256",false); sink.writeByte('"');
    for (int i=0;i<32;i++) {
        int b=digest[i]&255,hi=b>>>4,lo=b&15;
        sink.writeByte(hi<10?'0'+hi:'a'+hi-10);
        sink.writeByte(lo<10?'0'+lo:'a'+lo-10);
    }
    sink.writeByte('"');
    r5MarkerNumber(sink,"resultBytes",resultBytes);
    r5MarkerNumber(sink,"elapsedNanos",elapsed);
    r5MarkerNumber(sink,"finalExportNanos",exportNanos);
    r5MarkerNumber(sink,"totalGroups",groups);
    r5MarkerNumber(sink,"totalUnits",units);
    r5MarkerNumber(sink,"maxGroupNanos",maxNanos);
    r5MarkerNumber(sink,"maximumUnitsPerGroup",maxUnits);
    sink.writeByte('}'); sink.writeByte(10);
}
static void r5MarkerPreflight(GenericR7Arguments args) throws Exception {
    r5Require(args!=null && args.runId!=null && args.runId.length()==36 &&
        args.runId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") &&
        args.manifestSha!=null && args.manifestSha.matches("[0-9a-f]{64}") &&
        GenericR7Arguments.WIRE.equals(args.wireSha) &&
        GenericR7Arguments.ORACLE.equals(args.fixtureOracleSha),"R7_MARKER_STRING_BOUND");
    /* Reproved canonical upper line: quoted keys 207 + punctuation/LF 30;
     * fixed quoted values 375; seven decimal maxima 7+11+10+3+8+10+5=54. */
    r5Require(207+30+375+54==R5_MARKER_BOUND && R5_MARKER_BOUND==666 &&
        R5_MARKER_BOUND<=R5_BLOCK,"R7_MARKER_BOUND_PROOF");
}
/*</MARKER_METHODS>*/

/*<R7_CORE>*/
/* Generic successful-run ledger. Each of four immediate points retains 19
 * raw values, plus its bound Meter, expected start, and captured-state slot.
 * Its exact reads are 18 reflected fields x four units, one List.size read,
 * and three bounded guards: 76 units per point. Five objects cost five units;
 * 4 x 22 point slots plus 21 ledger slots cost 109 copies. Per run, seven
 * FreshRun reads cost 28 prepaid units and seven stored scalars cost seven
 * copies. Closed pre-export inspection is independently rederived below. */
private static final int R7_PHASE_COUNTER_READS=19;
private static final int R7_PHASE_READ_UNITS=18*4+1+3;
private static final int R7_PHASE_POINT_EXTRA_SLOTS=3;
private static final int R7_PHASE_LEDGER_SLOTS=21;
private static final int R7_CLOSED_SNAPSHOT_READS=19;
static final class R7MeterPoint {
    private final Object boundMeter;
    private final long expectedStarted;
    private boolean captured,open,ownerStarted,ownerActive,ownerAdopted;
    long units,groups,prior,copies,chunks,maxNanos,maxUnits,allowance;
    long groupUnits,groupStarted,started,constructors;
    long inFlight;
    int measureCount;
    Object measureList;
    R7MeterPoint(Object meter,long start) { boundMeter=meter; expectedStarted=start; }
    void capture(SetupMeter m) throws Exception {
        r5Require(!captured && m!=null && m.meter==boundMeter,"R7_POINT_BINDING");
        Object nativeMeter=m.meter;
        open=r5Flag(nativeMeter,"open");
        ownerActive=r5Flag(nativeMeter,"originalOwnerActive");
        inFlight=r5Number(nativeMeter,"inFlightOwnerGroups");
        units=r5Number(nativeMeter,"units"); groups=r5Number(nativeMeter,"groups");
        prior=r5Number(nativeMeter,"priorGroups"); copies=r5Number(nativeMeter,"copiedEntries");
        chunks=r5Number(nativeMeter,"copyChunks"); maxNanos=r5Number(nativeMeter,"maxGroupNanos");
        maxUnits=r5Number(nativeMeter,"maxGroupUnits"); allowance=r5Number(nativeMeter,"copyAllowance");
        groupUnits=r5Number(nativeMeter,"groupUnits"); groupStarted=r5Number(nativeMeter,"groupStarted");
        started=r5Number(nativeMeter,"started"); measureList=field(nativeMeter,"groupMeasures");
        r5Require(measureList instanceof java.util.List,"R7_MEASURE_LIST");
        measureCount=((java.util.List<?>)measureList).size();
        constructors=r5Number(nativeMeter,"sessionConstructorEntries");
        ownerStarted=r5Flag(nativeMeter,"originalOwnerStarted");
        ownerAdopted=r5Flag(nativeMeter,"originalOwnerAdopted");
        r5Require(!open && !ownerStarted && !ownerActive && !ownerAdopted &&
            inFlight==0 && prior==0 && units>=0 && groups>=0 && copies>=0 &&
            chunks>=0 && allowance>=0 && allowance<=R5_RESULT_LIMIT && copies<=allowance &&
            maxNanos>=0 && maxNanos<=R5_EXPORT_NANOS && maxUnits>=0 && maxUnits<=32768 &&
            groupUnits>=0 && groupUnits<=32768 &&
            groups<640 && measureCount==groups && constructors>=0 && started==expectedStarted,
            "R7_CLOSED_PHASE_POINT");
        captured=true;
    }
}
static final class R7MeterLedger {
    private final SetupMeter meter;
    private final R7MeterPoint beforeOpen,afterOpen,beforeBlocked,afterBlocked;
    private boolean openRecorded,blockedRecorded;
    private long openOwnerUnits,openWrapperUnits,openGroups,openCopies,openChunks,openMaxNanos,openMaxUnits;
    private long blockedOwnerUnits,blockedWrapperUnits,blockedGroups,blockedCopies,blockedChunks,blockedMaxNanos,blockedMaxUnits;
    private R7MeterLedger(SetupMeter m,long started) {
        meter=m; beforeOpen=new R7MeterPoint(m.meter,started); afterOpen=new R7MeterPoint(m.meter,started);
        beforeBlocked=new R7MeterPoint(m.meter,started); afterBlocked=new R7MeterPoint(m.meter,started);
    }
    static R7MeterLedger prepare(SetupMeter m,long started) throws Exception {
        if (m==null) throw new java.io.IOException("R7_LEDGER_METER");
        m.charge(1); // meter/parent binding guard
        r5Require(m!=null && m.parent!=null && m.meter!=null,"R7_LEDGER_METER");
        /* Five object allocations; 4 x (19 snapshots + Meter/start/captured)
         * plus 21 ledger scalar/reference slots; 4 x 76 raw/control units. */
        m.charge(5);
        m.copy(4*(R7_PHASE_COUNTER_READS+R7_PHASE_POINT_EXTRA_SLOTS)+R7_PHASE_LEDGER_SLOTS);
        m.charge(4*R7_PHASE_READ_UNITS);
        return new R7MeterLedger(m,started);
    }
    void beforeOpen(SetupMeter m) throws Exception { beforeOpen.capture(m); }
    void afterOpen(SetupMeter m) throws Exception { afterOpen.capture(m); }
    void beforeBlocked(SetupMeter m) throws Exception { beforeBlocked.capture(m); }
    void afterBlocked(SetupMeter m) throws Exception { afterBlocked.capture(m); }
    void recordRun(SetupMeter m,Object fresh,boolean blocked,Object ownerValue,
            Object wrapperValue,Object groupsValue,Object copiesValue,Object chunksValue,
            Object maxNanosValue,Object maxUnitsValue) throws Exception {
        if (m==null) throw new java.io.IOException("R7_RUN_BOUNDARY");
        m.charge(16); // meter/use guards, seven field types, overflow/delta checks
        r5Require(m==meter && fresh!=null && (blocked?afterBlocked.captured:afterOpen.captured),
            "R7_RUN_BOUNDARY");
        R7MeterPoint before=blocked?beforeBlocked:beforeOpen;
        R7MeterPoint after=blocked?afterBlocked:afterOpen;
        r7SameMeter(before,after);
        long owner=r7FreshLong(ownerValue);
        long wrapper=r7FreshLong(wrapperValue);
        long groups=r7FreshLong(groupsValue);
        long copies=r7FreshLong(copiesValue);
        long chunks=r7FreshLong(chunksValue);
        long maxNanos=r7FreshLong(maxNanosValue);
        long maxUnits=r7FreshLong(maxUnitsValue);
        long expectedUnits=r5Add(owner,wrapper);
        r5Require(r7Subtract(after.units,before.units)==expectedUnits &&
            r7Subtract(after.groups,before.groups)==groups &&
            r7Subtract(after.copies,before.copies)==copies &&
            r7Subtract(after.chunks,before.chunks)==chunks &&
            after.constructors==before.constructors+1 &&
            after.measureCount-before.measureCount==groups && groups>0 &&
            after.maxNanos==Math.max(before.maxNanos,maxNanos) &&
            after.maxUnits==Math.max(before.maxUnits,maxUnits) && maxNanos>=0 &&
            maxNanos<=R5_EXPORT_NANOS && maxUnits>0 && maxUnits<=32768,
            "R7_FRESH_METER_DELTA");
        if (blocked) {
            r5Require(!blockedRecorded && before.constructors==1 && after.constructors==2,
                "R7_BLOCKED_CONSTRUCTOR_DELTA");
            blockedOwnerUnits=owner; blockedWrapperUnits=wrapper; blockedGroups=groups;
            blockedCopies=copies; blockedChunks=chunks; blockedMaxNanos=maxNanos; blockedMaxUnits=maxUnits;
            blockedRecorded=true;
        } else {
            r5Require(!openRecorded && before.constructors==0 && after.constructors==1,
                "R7_OPEN_CONSTRUCTOR_DELTA");
            openOwnerUnits=owner; openWrapperUnits=wrapper; openGroups=groups;
            openCopies=copies; openChunks=chunks; openMaxNanos=maxNanos; openMaxUnits=maxUnits;
            openRecorded=true;
        }
    }
    long metric(boolean blocked,int slot) {
        switch(slot) {
        case 0:return blocked?blockedOwnerUnits:openOwnerUnits;
        case 1:return blocked?blockedWrapperUnits:openWrapperUnits;
        case 2:return blocked?blockedGroups:openGroups;
        case 3:return blocked?blockedCopies:openCopies;
        case 4:return blocked?blockedChunks:openChunks;
        case 5:return blocked?blockedMaxNanos:openMaxNanos;
        case 6:return blocked?blockedMaxUnits:openMaxUnits;
        default:throw new IllegalArgumentException("R7_METRIC_SLOT");
        }
    }
    private void verifyForFreeze(R5Snapshot pre,long started) throws Exception {
        r5Require(openRecorded && blockedRecorded && pre!=null && pre.boundMeter==meter &&
            pre.started==started && beforeOpen.captured && afterOpen.captured &&
            beforeBlocked.captured && afterBlocked.captured && pre.prior==0 &&
            !pre.ownerStarted && !pre.ownerAdopted &&
            beforeOpen.measureList==pre.measureList && afterOpen.measureList==pre.measureList &&
            beforeBlocked.measureList==pre.measureList && afterBlocked.measureList==pre.measureList &&
            pre.constructorEntries==2 && afterBlocked.constructors==2 &&
            pre.units>=afterBlocked.units && pre.groups>=afterBlocked.groups &&
            pre.copies>=afterBlocked.copies && pre.chunks>=afterBlocked.chunks &&
            beforeBlocked.units>=afterOpen.units && beforeBlocked.groups>=afterOpen.groups &&
            beforeBlocked.copies>=afterOpen.copies && beforeBlocked.chunks>=afterOpen.chunks &&
            beforeOpen.allowance==afterOpen.allowance && beforeOpen.allowance==beforeBlocked.allowance &&
            beforeOpen.allowance==afterBlocked.allowance && beforeOpen.maxNanos<=afterOpen.maxNanos &&
            afterOpen.maxNanos<=beforeBlocked.maxNanos && beforeBlocked.maxNanos<=afterBlocked.maxNanos &&
            afterBlocked.maxNanos<=pre.maxNanos && beforeOpen.maxUnits<=afterOpen.maxUnits &&
            afterOpen.maxUnits<=beforeBlocked.maxUnits && beforeBlocked.maxUnits<=afterBlocked.maxUnits &&
            afterBlocked.maxUnits<=pre.maxUnits,
            "R7_PHASE_FREEZE_BINDING");
    }
    private static void r7SameMeter(R7MeterPoint a,R7MeterPoint b) throws Exception {
        r5Require(a.captured && b.captured && a.boundMeter==b.boundMeter &&
            a.started==b.started && a.measureList==b.measureList && a.prior==b.prior &&
            a.allowance==b.allowance && !a.open && !b.open && !a.ownerStarted &&
            !b.ownerStarted && !a.ownerActive && !b.ownerActive && !a.ownerAdopted &&
            !b.ownerAdopted && a.inFlight==0 && b.inFlight==0 &&
            a.units<=b.units && a.groups<=b.groups && a.copies<=b.copies && a.chunks<=b.chunks,
            "R7_PHASE_IDENTITY_OR_ORDER");
    }
}
private static long r7FreshLong(Object value) throws Exception {
    r5Require((value instanceof Integer || value instanceof Long) &&
        ((Number)value).longValue()>=0,"R7_FRESH_FIELD");
    return ((Number)value).longValue();
}
private static long r7Subtract(long value,long amount) throws java.io.IOException {
    if (value<0 || amount<0 || value<amount) throw new java.io.IOException("R7_LEDGER_UNDERFLOW");
    return value-amount;
}
private static void r7PutNumber(java.util.Map<String,Object> map,String key,long value) {
    map.put(key,Long.valueOf(value));
}
static final class R7WorkPlan implements R5WorkFinalizer {
    private final SetupMeter meter;
    final R5Snapshot pre;
    private final R7MeterLedger ledger;
    private final java.util.Map<String,Object> work,setup,open,blocked,metadata,preExport,limits,result;
    private final long started;
    private boolean frozen,finalized;
    private R7WorkPlan(SetupMeter m,R5Snapshot p,R7MeterLedger l,
            java.util.Map<String,Object> w,java.util.Map<String,Object> s,
            java.util.Map<String,Object> o,java.util.Map<String,Object> b,
            java.util.Map<String,Object> md,java.util.Map<String,Object> pe,
            java.util.Map<String,Object> lim,java.util.Map<String,Object> body,long start) {
        meter=m; pre=p; ledger=l; work=w; setup=s; open=o; blocked=b;
        metadata=md; preExport=pe; limits=lim; result=body; started=start;
    }
    public void verifyBound(SetupMeter m,R5Snapshot p,java.util.Map<?,?> body,
            java.util.Map<?,?> w) throws Exception {
        r5Require(!finalized && frozen && m==meter && p==pre && body==result && w==work &&
            body.get("work")==work && work.get("setup")==setup && work.get("open")==open &&
            work.get("blocked")==blocked && work.get("metadata")==metadata &&
            work.get("preExport")==preExport && work.get("limits")==limits,
            "R7_FINALIZER_BINDING");
    }
    private void freezeAfterCapture() throws Exception {
        ledger.verifyForFreeze(pre,started);
        long setupUnits=ledger.beforeOpen.units,setupGroups=ledger.beforeOpen.groups;
        long setupCopies=ledger.beforeOpen.copies,setupChunks=ledger.beforeOpen.chunks;
        long openUnits=r7Subtract(ledger.afterOpen.units,ledger.beforeOpen.units);
        long openGroups=r7Subtract(ledger.afterOpen.groups,ledger.beforeOpen.groups);
        long openCopies=r7Subtract(ledger.afterOpen.copies,ledger.beforeOpen.copies);
        long openChunks=r7Subtract(ledger.afterOpen.chunks,ledger.beforeOpen.chunks);
        long blockedUnits=r7Subtract(ledger.afterBlocked.units,ledger.beforeBlocked.units);
        long blockedGroups=r7Subtract(ledger.afterBlocked.groups,ledger.beforeBlocked.groups);
        long blockedCopies=r7Subtract(ledger.afterBlocked.copies,ledger.beforeBlocked.copies);
        long blockedChunks=r7Subtract(ledger.afterBlocked.chunks,ledger.beforeBlocked.chunks);
        long metadataUnits=r7Subtract(r7Subtract(r7Subtract(pre.units,setupUnits),openUnits),blockedUnits);
        long metadataGroups=r7Subtract(r7Subtract(r7Subtract(pre.groups,setupGroups),openGroups),blockedGroups);
        long metadataCopies=r7Subtract(r7Subtract(r7Subtract(pre.copies,setupCopies),openCopies),blockedCopies);
        long metadataChunks=r7Subtract(r7Subtract(r7Subtract(pre.chunks,setupChunks),openChunks),blockedChunks);
        r7PutNumber(setup,"units",setupUnits); r7PutNumber(setup,"groups",setupGroups);
        r7PutNumber(setup,"copiedEntries",setupCopies); r7PutNumber(setup,"copyChunks",setupChunks);
        r7PutNumber(open,"units",openUnits); r7PutNumber(open,"groups",openGroups);
        r7PutNumber(open,"copiedEntries",openCopies); r7PutNumber(open,"copyChunks",openChunks);
        r7PutNumber(blocked,"units",blockedUnits); r7PutNumber(blocked,"groups",blockedGroups);
        r7PutNumber(blocked,"copiedEntries",blockedCopies); r7PutNumber(blocked,"copyChunks",blockedChunks);
        r7PutNumber(metadata,"units",metadataUnits); r7PutNumber(metadata,"groups",metadataGroups);
        r7PutNumber(metadata,"copiedEntries",metadataCopies); r7PutNumber(metadata,"copyChunks",metadataChunks);
        r7PutNumber(preExport,"units",pre.units); r7PutNumber(preExport,"groups",pre.groups);
        r7PutNumber(preExport,"copiedEntries",pre.copies); r7PutNumber(preExport,"copyChunks",pre.chunks);
        r7PutNumber(preExport,"maxGroupNanos",pre.maxNanos);
        r7PutNumber(preExport,"maximumUnitsPerGroup",pre.maxUnits);
        r7PutNumber(preExport,"sessionConstructorEntries",pre.constructorEntries);
        r5Require(pre.units>0 && pre.groups<640 && pre.copies<=R5_RESULT_LIMIT &&
            pre.maxNanos>0 && pre.maxNanos<=R5_EXPORT_NANOS &&
            pre.maxUnits>0 && pre.maxUnits<=32768 && pre.constructorEntries==2,
            "R7_PREEXPORT_BOUND");
        frozen=true;
    }
    public void finalizeWork(long exportStartNanos) throws Exception {
        if (finalized) throw new java.io.IOException("R7_FINALIZER_REUSED");
        finalized=true;
        if (!frozen) throw new java.io.IOException("R7_NOT_FROZEN");
        long cutoff=r5Elapsed(started,exportStartNanos);
        if (cutoff<=0 || cutoff>R5_OPERATION_NANOS)
            throw new java.io.IOException("R7_ELAPSED_CUTOFF");
        /* Only an already-accounted numeric slot is changed; no meter, graph,
         * collection topology, iterator, allocation plan, or new key is read. */
        preExport.put("elapsedNanos",Long.valueOf(cutoff));
    }
}
static R7WorkPlan captureR7WorkPlan(R7MeterLedger ledger,SetupMeter meter,long started,
        java.util.Map<String,Object> result) throws Exception {
    if (ledger==null || meter==null || result==null) throw new java.io.IOException("R7_ARGUMENT");
    meter.charge(1); // validate the three bound plan inputs
    r5Require(ledger!=null && meter!=null && result!=null,"R7_ARGUMENT");
    meter.charge(2); // inspect the existing work slot and its null value
    r5Require(result.containsKey("work") && result.get("work")==null,"R7_WORK_SLOT");
    /* Exact tree: 6 root + (4 phases x 4) + 8 preExport + 6 limits = 36
     * ordered entries; one additional accounted replacement of body.work. */
    meter.copy(37); meter.charge(7); // seven LinkedHashMap allocations
    java.util.LinkedHashMap<String,Object> work=new java.util.LinkedHashMap<String,Object>(16);
    java.util.LinkedHashMap<String,Object> setup=new java.util.LinkedHashMap<String,Object>(8);
    java.util.LinkedHashMap<String,Object> open=new java.util.LinkedHashMap<String,Object>(8);
    java.util.LinkedHashMap<String,Object> blocked=new java.util.LinkedHashMap<String,Object>(8);
    java.util.LinkedHashMap<String,Object> metadata=new java.util.LinkedHashMap<String,Object>(8);
    java.util.LinkedHashMap<String,Object> preExport=new java.util.LinkedHashMap<String,Object>(16);
    java.util.LinkedHashMap<String,Object> limits=new java.util.LinkedHashMap<String,Object>(8);
    for (int i=0;i<4;i++) {
        setup.put(r7PhaseKey(i),Long.valueOf(0)); open.put(r7PhaseKey(i),Long.valueOf(0));
        blocked.put(r7PhaseKey(i),Long.valueOf(0)); metadata.put(r7PhaseKey(i),Long.valueOf(0));
    }
    for (int i=0;i<8;i++) preExport.put(r7PreExportKey(i),Long.valueOf(0));
    meter.copy(6); meter.charge(6); // six noncached fixed-limit Long wrappers
    limits.put("producerNanos",Long.valueOf(R5_OPERATION_NANOS));
    limits.put("maximumGroups",Long.valueOf(640));
    limits.put("maximumGroupNanos",Long.valueOf(R5_EXPORT_NANOS));
    limits.put("maximumUnitsPerGroup",Long.valueOf(32768));
    limits.put("maximumCopiedEntries",Long.valueOf(R5_RESULT_LIMIT));
    limits.put("copyChunkSize",Long.valueOf(R5_BLOCK));
    work.put("setup",setup); work.put("open",open); work.put("blocked",blocked);
    work.put("metadata",metadata); work.put("preExport",preExport); work.put("limits",limits);
    meter.charge(1); result.put("work",work);
    R5Snapshot pre=allocateR5Snapshot(meter); // one unit precedes its allocation
    meter.copy(14); meter.charge(1); // 11 refs, start scalar, and two lifecycle slots
    R7WorkPlan plan=new R7WorkPlan(meter,pre,ledger,work,setup,open,blocked,
        metadata,preExport,limits,result,started);
    validateR9CanaryTextPolicy(result,meter); // same exact geometry path policy as all sink passes
    accountResultTree(result,meter); // full placeholder body, including all 36 work entries
    meter.copy(24); // Long wrappers for 23 frozen slots plus the final elapsed slot
    meter.charge(22); // 20 checked subtractions, ledger binding, and frozen bounds
    meter.charge(23+1+8); // frozen/finalizer writes and seven map reads plus binding
    /* Re-audited R5Snapshot.captureClosed reads exactly these 19 fields:
     * open, originalOwnerActive, inFlightOwnerGroups, units, groups, priorGroups,
     * copiedEntries, copyChunks, maxGroupNanos, maxGroupUnits, copyAllowance,
     * groupUnits, groupStarted, started, groupMeasures, list.size,
     * sessionConstructorEntries, originalOwnerStarted, originalOwnerAdopted. */
    meter.charge(R7_CLOSED_SNAPSHOT_READS);
    meter.finish();
    pre.captureClosed(meter,started);
    plan.freezeAfterCapture();
    return plan;
}
/*</R7_CORE>*/
