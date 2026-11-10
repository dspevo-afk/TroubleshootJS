/* Authored replacement members for the frozen W study. Java 7 source only.
 * Markers are consumed by the pure byte overlay; they are not emitted.
 */
// @@R5_FLAT_SHAPE_BEGIN@@
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
// @@R5_FLAT_SHAPE_END@@

// @@R7_FLAT_PLAN_BEGIN@@
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

    CurrentAssemblyMetadata.installCleanup(meter,result,cleanupNanos);
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
// @@R7_FLAT_PLAN_END@@
