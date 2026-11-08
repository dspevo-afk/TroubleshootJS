/* Nested Java7 members. Prospective R9 transport only; not R5 publication.
 * Requires canonical JSON, accountResultTree, SetupMeter, field,
 * exportCopyBlock, R5BodySink/R5CountSink and the existing r5 helpers. HOLD. */
static final int R9_CANARY_KEYS=11;
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
    case 5:return "complete"; case 6:return "blocked"; case 7:return "nonBranchQuality";
    case 8:return "genericCallbackThrow"; case 9:return "directOwnerAbortReuse";
    case 10:return "sourceReview";
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
            r5Require("tsj-cohort-terminal-canary-v1".equals(value),"R9_CANARY_SCHEMA");
            break;
        case 1:
            meter.charge(1);
            r5Require("OWNER_CANARIES_RECORDED_ONLY".equals(value),"R9_CANARY_STATUS");
            break;
        case 2:r9HeaderText(meter,value,36,true); break;
        case 3:case 4:r9HeaderText(meter,value,64,false); break;
        case 7:
            meter.charge(1);
            if (value==null) break;
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
            break;
        default:
            meter.charge(1);
            r5Require(value instanceof java.util.Map,"R9_CANARY_MEMBER");
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
    writeCanonicalJson(canary,count); // A: actual virtual C blocks, no body array
    count.complete(count.position);
    long bytes=count.position,blocks=(bytes+4095L)/4096L,fee=3*blocks;
    r5Require(bytes>=1 && bytes<=1048576 && blocks>=1 && fee<=32768 &&
        bytes<=pre.allowance-pre.copies && pre.chunks<=Integer.MAX_VALUE-blocks &&
        pre.units<=Long.MAX_VALUE-fee,"R9_PUBLICATION_CAPACITY");
    R9CanaryStdoutSink writer=new R9CanaryStdoutSink(meter,started,exportStarted,bytes,System.out);
    writeCanonicalJson(canary,writer); // B/C: one segment, one real admission/write at a time
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
