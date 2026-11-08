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
import java.util.ArrayList;
import java.util.List;

/**
 * Source-only guards for the adopted-owner closed snapshot and R5 token seam.
 * The only positive owner state comes from the selected Meter/ArmRun APIs.
 * Token byte demand is explicitly synthetic; this does not run the exporter,
 * create a result/marker, or establish reference, full-D, or H1 evidence.
 */
public final class AdoptedSnapshotCanary {
    private static final String PARENT=
        "com.lushprojects.circuitjs1.client.FailureCohortRoutingStudy";
    private static final String GENERATOR=
        "com.lushprojects.circuitjs1.client.ActualGeneratorBranchOrderStudy";
    private static final String RUN_ID="00000000-0000-0000-0000-000000000001";
    private static final String ZERO_SHA=
        "0000000000000000000000000000000000000000000000000000000000000000";
    private static final int OWNER_GROUPS=640;
    private static final long OPERATION_NANOS=90000000000L;
    private static final long GROUP_NANOS=5000000000L;
    private static final int STEP_LIMIT=32768;
    private static final int COPY_LIMIT=1048576;
    private static final int BLOCK=4096;
    private static final int MARKER_BOUND=666;
    private static final int SNAPSHOT_READS=19;
    private static final int RESULT_BYTES=4097;
    private static final int RESULT_BLOCKS=2;
    private static final int MARKER_BYTES=665;

    private static int assertions;
    private static int snapshotCases;
    private static int tokenCases;
    private static int ownerFixtures;
    private static int meterFixtures;
    private static Path data;
    private static Harness harness;
    private static TokenFrame boundaryFrame;
    private static TokenFrame exactCapacityFrame;

    private AdoptedSnapshotCanary() { }

    private interface Action { void run() throws Exception; }

    private static final class Harness {
        final CohortTerminalTelemetryStudy.Parent parent;
        final Class<?> studyType,armType,modeType;
        final Constructor<?> studyConstructor,armConstructor;
        final Method beginOwner,adoptOwner,newGroup,clearBudget;
        final Object authoredMode;

        @SuppressWarnings({"unchecked","rawtypes"})
        Harness(Path fixtureDirectory) throws Exception {
            parent=new CohortTerminalTelemetryStudy.Parent();
            studyType=Class.forName(GENERATOR+"$Study");
            armType=Class.forName(GENERATOR+"$ArmRun");
            modeType=Class.forName(GENERATOR+"$Mode");
            Class<?> budgetType=Class.forName(GENERATOR+"$SharedOwnerBudget");
            authoredMode=Enum.valueOf((Class)modeType,"AUTHORED");
            studyConstructor=studyType.getDeclaredConstructor(modeType,String.class,
                String.class,String.class,Path.class);
            studyConstructor.setAccessible(true);
            Class<?> graph=Class.forName(
                "com.lushprojects.circuitjs1.client.PcbLayerRoutingPrototype$FixedPrefixGraphObserver");
            Class<?> terminal=Class.forName(GENERATOR+"$TerminalPrefixInspector");
            armConstructor=armType.getDeclaredConstructor(studyType,int.class,
                graph,terminal,budgetType);
            armConstructor.setAccessible(true);

            beginOwner=method(parent.meterType,"beginOriginalOwner");
            adoptOwner=method(parent.meterType,"adoptOriginalOwner",int.class);
            newGroup=method(armType,"newGroup",String.class,long.class);
            clearBudget=method(armType,"clearSharedOwnerBudget");

            check(staticNumber(parent.owner,"MAX_GROUPS")==OWNER_GROUPS &&
                staticNumber(parent.owner,"OP_NANOS")==OPERATION_NANOS,
                "parent limits changed");
            check(staticNumber(parent.owner,"STEP_LIMIT")==STEP_LIMIT &&
                staticNumber(parent.owner,"GROUP_NANOS")==GROUP_NANOS,
                "parent step/group limits changed");
            Class<?> owner=Class.forName(GENERATOR);
            check(staticNumber(owner,"GROUP_LIMIT")==OWNER_GROUPS &&
                staticNumber(owner,"OPERATION_LIMIT_NANOS")==OPERATION_NANOS,
                "selected generator limits changed");
            check(staticNumber(CohortTerminalTelemetryStudy.class,
                    "R5_OPERATION_NANOS")==OPERATION_NANOS &&
                staticNumber(CohortTerminalTelemetryStudy.class,
                    "R5_EXPORT_NANOS")==GROUP_NANOS &&
                staticNumber(CohortTerminalTelemetryStudy.class,"R5_BLOCK")==BLOCK &&
                staticNumber(CohortTerminalTelemetryStudy.class,
                    "R5_RESULT_LIMIT")==COPY_LIMIT &&
                staticNumber(CohortTerminalTelemetryStudy.class,
                    "R5_MARKER_BOUND")==MARKER_BOUND,
                "native R5 limits changed");
            check(fixtureDirectory!=null,"fixture directory missing");
        }

        CohortTerminalTelemetryStudy.SetupMeter newMeter(long started)
                throws Exception {
            meterFixtures++;
            return new CohortTerminalTelemetryStudy.SetupMeter(parent,
                parent.meter(started));
        }

        Object newStudy() throws Exception {
            // AUTHORED initializes only the synthetic guard Study; it never executes
            // a descriptor, OriginalArm, Session, or authored run.
            return studyConstructor.newInstance(authoredMode,RUN_ID,ZERO_SHA,
                ZERO_SHA,data);
        }

        OwnerLease newOwner(CohortTerminalTelemetryStudy.SetupMeter meter)
                throws Exception {
            Object arm=armConstructor.newInstance(newStudy(),Integer.valueOf(1),
                null,null,meter.meter);
            ownerFixtures++;
            assertNoGeneration(arm,"newly constructed ArmRun");
            return new OwnerLease(this,meter,arm);
        }
    }

    private static final class OwnerLease {
        final Harness harness;
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final Object arm;
        final List<Object> admittedGroups=new ArrayList<Object>();
        boolean started,adopted,released;

        OwnerLease(Harness h,CohortTerminalTelemetryStudy.SetupMeter m,Object a) {
            harness=h; meter=m; arm=a;
        }
        void start() throws Exception {
            call(harness.beginOwner,meter.meter);
            started=true;
            check(flag(meter.meter,"originalOwnerStarted") &&
                flag(meter.meter,"originalOwnerActive") &&
                !flag(meter.meter,"originalOwnerAdopted") &&
                number(meter.meter,"inFlightOwnerGroups")==0,
                "beginOriginalOwner state mismatch");
        }
        void admitOne() throws Exception {
            long before=number(arm,"nextGroupOrdinal");
            Object group=call(harness.newGroup,arm,"GUARD_FIXTURE",
                Long.valueOf(System.nanoTime()));
            admittedGroups.add(group);
            check(number(group,"ordinal")==before &&
                number(arm,"nextGroupOrdinal")==before+1 &&
                number(meter.meter,"inFlightOwnerGroups")==admittedGroups.size(),
                "ArmRun.newGroup did not admit its actual owner ordinal");
            check(flag(meter.meter,"originalOwnerActive") &&
                number(meter.meter,"priorGroups")==0,
                "owner admission changed adopted counters");
            assertNoGeneration(arm,"ArmRun.newGroup guard fixture");
        }
        void admit(int count) throws Exception {
            for (int i=0;i<count;i++) admitOne();
        }
        void adopt(int count) throws Exception {
            call(harness.adoptOwner,meter.meter,Integer.valueOf(count));
            adopted=true;
        }
        void release() throws Exception {
            call(harness.clearBudget,arm);
            call(harness.clearBudget,arm);
            check(field(arm,"sharedOwnerBudget")==null,
                "shared owner budget reference was not cleared");
            assertNoGeneration(arm,"released ArmRun guard fixture");
            released=true;
        }
    }

    private static final class SnapshotFrame {
        final long started;
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final CohortTerminalTelemetryStudy.R5Snapshot snapshot;
        final int ordinaryWrapperGroups;
        final long syntheticPreCopyDemand;

        SnapshotFrame(long s,CohortTerminalTelemetryStudy.SetupMeter m,
                CohortTerminalTelemetryStudy.R5Snapshot p,int wrappers,long demand) {
            started=s; meter=m; snapshot=p;
            ordinaryWrapperGroups=wrappers; syntheticPreCopyDemand=demand;
        }
    }

    private static final class TokenFrame {
        final SnapshotFrame prefix;
        final long exportStarted,fee,blocks,closedNanos;
        final long beforeUnits,beforeGroups,beforePrior,beforeCopies,beforeChunks;
        final long beforeMaxNanos,beforeMaxUnits,allowance;
        final Object closedMeasure;
        final long resultBytes;
        final int markerBytes;
        CohortTerminalTelemetryStudy.R5PublicationToken token;

        TokenFrame(SnapshotFrame p,long e,long f,long c,long nanos,
                long u,long g,long prior,long copies,long chunks,long maxNanos,
                long maxUnits,long cap,Object measure,long result,int marker) {
            prefix=p; exportStarted=e; fee=f; blocks=c; closedNanos=nanos;
            beforeUnits=u; beforeGroups=g; beforePrior=prior;
            beforeCopies=copies; beforeChunks=chunks;
            beforeMaxNanos=maxNanos; beforeMaxUnits=maxUnits;
            allowance=cap; closedMeasure=measure;
            resultBytes=result; markerBytes=marker;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args==null || args.length!=1)
            throw new IOException("ADOPTED_SNAPSHOT_CANARY_ONE_EMPTY_DIRECTORY");
        data=validateEmptyDirectory(args[0]);
        assertNativeMainDenied();
        harness=new Harness(data);

        runZeroActiveAndInflightSnapshotCases();
        runOwnerAdoptionCases();
        SnapshotFrame stateFrame=buildSnapshotFrame(0,1,0,false);
        runSnapshotBindingAndDriftCases(stateFrame);
        runAggregateSnapshotLimitCase();
        runBoundaryTokenCase();
        runTokenOneUseAndStateCases();
        runClosedMeasureDriftCase();
        runCopyCapacityCases();
        runMarkerBoundCase();

        emptyDirectory(data,"guard wrote into caller data directory");
        System.out.println(summaryJson());
    }

    private static Path validateEmptyDirectory(String raw) throws Exception {
        if (raw==null || raw.length()==0 || raw.indexOf('\0')>=0)
            throw new IOException("ADOPTED_SNAPSHOT_CANARY_PATH");
        Path path;
        try { path=Paths.get(raw); }
        catch (RuntimeException invalid) {
            throw new IOException("ADOPTED_SNAPSHOT_CANARY_PATH",invalid);
        }
        if (!path.isAbsolute() || !path.normalize().equals(path) ||
                !raw.equals(path.toString()) ||
                !Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS) ||
                Files.isSymbolicLink(path) || !path.toRealPath().equals(path))
            throw new IOException("ADOPTED_SNAPSHOT_CANARY_PATH");
        for (Path part=path;part!=null;part=part.getParent())
            new CohortTerminalTelemetryStudy.R5FileIdentity(part,true);
        emptyDirectory(path,"guard input directory must start empty");
        return path;
    }

    private static void assertNativeMainDenied() throws Exception {
        expectType("study-main-null-args",IllegalStateException.class,
            "ADOPTED_SNAPSHOT_COMPONENT_ONLY",new Action() {
                public void run() throws Exception {
                    CohortTerminalTelemetryStudy.main(null);
                }
            });
        expectType("study-main-empty-args",IllegalStateException.class,
            "ADOPTED_SNAPSHOT_COMPONENT_ONLY",new Action() {
                public void run() throws Exception {
                    CohortTerminalTelemetryStudy.main(new String[0]);
                }
            });
        expectType("study-main-generic-profile",IllegalStateException.class,
            "ADOPTED_SNAPSHOT_COMPONENT_ONLY",new Action() {
                public void run() throws Exception {
                    CohortTerminalTelemetryStudy.main(new String[]{
                        "GENERIC_ROUTING_R7_REFERENCE_V1",RUN_ID,ZERO_SHA,
                        "4142410d8719f2e3b73edc9481ba9a2a40f0476f19f242844e97b42bde366741",
                        "5d2b62c98bd229a73be0e54e4b6adcde4d5080fb87046d8bd770455f80c1224a",
                        data.toString()});
                }
            });
        emptyDirectory(data,"denied study main touched data directory");
    }

    /** Build only closed wrapper Meter groups; never invent owner measurements. */
    private static void wrapperGroups(
            CohortTerminalTelemetryStudy.SetupMeter meter,int count) throws Exception {
        for (int i=0;i<count;i++) {
            meter.method("beginGroup",new Class<?>[0]);
            meter.charge(1);
            meter.method("finishGroup",new Class<?>[0]);
        }
    }

    /**
     * Synthetic byte demand goes only through the real block admission method.
     * No byte arrays or result/marker output are created for this token fixture.
     */
    private static void syntheticCopyDemand(
            CohortTerminalTelemetryStudy.SetupMeter meter,long bytes) throws Exception {
        long remaining=bytes;
        while (remaining>0) {
            int block=(int)Math.min((long)BLOCK,remaining);
            CohortTerminalTelemetryStudy.exportCopyBlock(meter,block);
            remaining-=block;
        }
    }

    private static void finishGroup(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        meter.method("finishGroup",new Class<?>[0]);
    }

    private static CohortTerminalTelemetryStudy.R5Snapshot prepayClosedSnapshot(
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        meter.method("beginGroup",new Class<?>[0]);
        meter.charge(SNAPSHOT_READS);
        CohortTerminalTelemetryStudy.R5Snapshot snapshot=
            CohortTerminalTelemetryStudy.allocateR5Snapshot(meter);
        finishGroup(meter);
        List<?> measures=measureList(meter.meter);
        Object metadataMeasure=measures.get(measures.size()-1);
        check(number(metadataMeasure,"units")==SNAPSHOT_READS+1,
            "snapshot inspection prepayment/allocation units drifted");
        check(number(meter.meter,"groups")==measures.size(),
            "wrapper groupMeasures identity/count mismatch before capture");
        return snapshot;
    }

    /**
     * Set up actual wrapper and optional adopted-owner state, then account the
     * 19 closed-snapshot inspections before close. Allocation charges its own
     * additional unit through allocateR5Snapshot.
     */
    private static SnapshotFrame buildSnapshotFrame(int wrappers,int ownerGroups,
            long preCopyDemand,boolean capture) throws Exception {
        long started=System.nanoTime();
        CohortTerminalTelemetryStudy.SetupMeter meter=harness.newMeter(started);
        check(!flag(meter.meter,"originalOwnerStarted") &&
            !flag(meter.meter,"originalOwnerActive") &&
            !flag(meter.meter,"originalOwnerAdopted") &&
            number(meter.meter,"priorGroups")==0 &&
            number(meter.meter,"inFlightOwnerGroups")==0 &&
            number(meter.meter,"sessionConstructorEntries")==0,
            "fresh Meter did not start at zero-owner zero-session state");

        if (preCopyDemand>0) {
            meter.method("beginGroup",new Class<?>[0]);
            syntheticCopyDemand(meter,preCopyDemand);
            finishGroup(meter);
        }
        wrapperGroups(meter,wrappers);

        if (ownerGroups>0) {
            OwnerLease owner=harness.newOwner(meter);
            owner.start();
            owner.admit(ownerGroups);
            check(number(meter.meter,"inFlightOwnerGroups")==ownerGroups &&
                number(meter.meter,"priorGroups")==0,
                "owner fixture did not retain actual in-flight ordinals");
            owner.adopt(ownerGroups);
            check(number(meter.meter,"priorGroups")==ownerGroups &&
                number(meter.meter,"inFlightOwnerGroups")==0 &&
                flag(meter.meter,"originalOwnerStarted") &&
                flag(meter.meter,"originalOwnerAdopted") &&
                !flag(meter.meter,"originalOwnerActive"),
                "adoptOriginalOwner did not produce closed adopted state");
            owner.release();
            checkSessionZero(meter,"adopted owner fixture");
        }

        CohortTerminalTelemetryStudy.R5Snapshot snapshot=prepayClosedSnapshot(meter);
        checkSessionZero(meter,"snapshot capture fixture");

        if (capture) {
            snapshot.captureClosed(meter,started);
            checkSnapshotAdopted(snapshot,meter);
        }
        SnapshotFrame frame=new SnapshotFrame(started,meter,snapshot,wrappers,
            preCopyDemand);
        snapshotCases++;
        return frame;
    }

    private static void runZeroActiveAndInflightSnapshotCases() throws Exception {
        final SnapshotFrame zero=buildSnapshotFrame(0,0,0,false);
        expectType("snapshot-zero-prior",IOException.class,"R5_METADATA_SNAPSHOT",
            new Action() {
                public void run() throws Exception {
                    zero.snapshot.captureClosed(zero.meter,zero.started);
                }
            });
        check(!flag(zero.meter.meter,"originalOwnerStarted") &&
            !flag(zero.meter.meter,"originalOwnerAdopted") &&
            number(zero.meter.meter,"priorGroups")==0,
            "zero-prior rejection mutated owner state");
        expectType("snapshot-failed-capture-spent",IOException.class,
            "R5_SNAPSHOT_CAPTURE_STATE",new Action() {
                public void run() throws Exception {
                    zero.snapshot.captureClosed(zero.meter,zero.started);
                }
            });
        checkSessionZero(zero.meter,"zero-prior rejection");
        snapshotCases++;

        final SnapshotFrame active=buildSnapshotFrame(0,0,0,false);
        final OwnerLease activeOwner=harness.newOwner(active.meter);
        activeOwner.start();
        expectType("snapshot-active-owner",IOException.class,"R5_METADATA_NOT_CLOSED",
            new Action() {
                public void run() throws Exception {
                    active.snapshot.captureClosed(active.meter,active.started);
                }
            });
        check(flag(active.meter.meter,"originalOwnerActive") &&
            number(active.meter.meter,"inFlightOwnerGroups")==0 &&
            !flag(active.meter.meter,"originalOwnerAdopted"),
            "active owner rejection mutated lifecycle");
        activeOwner.adopt(0);
        activeOwner.release();
        check(flag(active.meter.meter,"originalOwnerStarted") &&
            flag(active.meter.meter,"originalOwnerAdopted") &&
            !flag(active.meter.meter,"originalOwnerActive") &&
            number(active.meter.meter,"priorGroups")==0 &&
            number(active.meter.meter,"inFlightOwnerGroups")==0,
            "adoptOriginalOwner(0) did not isolate the prior-group predicate");
        final CohortTerminalTelemetryStudy.R5Snapshot adoptedZeroPrior=
            prepayClosedSnapshot(active.meter);
        expectType("snapshot-adopted-zero-prior",IOException.class,
            "R5_METADATA_SNAPSHOT",new Action() {
                public void run() throws Exception {
                    adoptedZeroPrior.captureClosed(active.meter,active.started);
                }
            });
        expectType("snapshot-adopted-zero-prior-capture-spent",IOException.class,
            "R5_SNAPSHOT_CAPTURE_STATE",new Action() {
                public void run() throws Exception {
                    adoptedZeroPrior.captureClosed(active.meter,active.started);
                }
            });
        checkSessionZero(active.meter,"active-owner rejection");
        snapshotCases++;

        final SnapshotFrame inflight=buildSnapshotFrame(0,0,0,false);
        final OwnerLease inflightOwner=harness.newOwner(inflight.meter);
        inflightOwner.start();
        inflightOwner.admitOne();
        expectType("snapshot-inflight-owner",IOException.class,"R5_METADATA_NOT_CLOSED",
            new Action() {
                public void run() throws Exception {
                    inflight.snapshot.captureClosed(inflight.meter,inflight.started);
                }
            });
        check(flag(inflight.meter.meter,"originalOwnerActive") &&
            number(inflight.meter.meter,"inFlightOwnerGroups")==1 &&
            number(inflight.meter.meter,"priorGroups")==0,
            "in-flight owner rejection mutated lifecycle");
        inflightOwner.adopt(1);
        inflightOwner.release();
        checkSessionZero(inflight.meter,"in-flight rejection");
        snapshotCases++;
    }

    private static void runOwnerAdoptionCases() throws Exception {
        long started=System.nanoTime();
        final CohortTerminalTelemetryStudy.SetupMeter meter=harness.newMeter(started);
        final OwnerLease owner=harness.newOwner(meter);
        owner.start();
        owner.admit(2);
        final long ordinal=number(owner.arm,"nextGroupOrdinal");
        final long inflight=number(meter.meter,"inFlightOwnerGroups");
        expectType("adoption-count-mismatch",IllegalStateException.class,null,
            new Action() {
                public void run() throws Exception { owner.adopt(1); }
            });
        check(number(meter.meter,"priorGroups")==0 &&
            number(meter.meter,"inFlightOwnerGroups")==inflight &&
            flag(meter.meter,"originalOwnerActive") &&
            number(owner.arm,"nextGroupOrdinal")==ordinal,
            "mismatched adoption changed actual owner state");
        owner.adopt(2);
        check(number(meter.meter,"priorGroups")==2 &&
            number(meter.meter,"inFlightOwnerGroups")==0 &&
            !flag(meter.meter,"originalOwnerActive") &&
            flag(meter.meter,"originalOwnerStarted") &&
            flag(meter.meter,"originalOwnerAdopted"),
            "actual adoption did not transfer exactly two ordinals");
        expectType("duplicate-adoption",IllegalStateException.class,null,
            new Action() {
                public void run() throws Exception { owner.adopt(2); }
            });
        expectType("owner-group-after-adoption",IllegalStateException.class,null,
            new Action() {
                public void run() throws Exception { owner.admitOne(); }
            });
        check(number(meter.meter,"priorGroups")==2 &&
            number(meter.meter,"inFlightOwnerGroups")==0 &&
            number(owner.arm,"nextGroupOrdinal")==ordinal,
            "rejected duplicate/follow-up adoption changed counters");
        owner.release();
        checkSessionZero(meter,"adoption mismatch/duplicate fixture");
        snapshotCases++;
    }

    private static void runSnapshotBindingAndDriftCases(final SnapshotFrame frame)
            throws Exception {
        final CohortTerminalTelemetryStudy.SetupMeter foreign=
            harness.newMeter(frame.started);
        expectType("snapshot-foreign-capture",IOException.class,
            "R5_SNAPSHOT_CAPTURE_STATE",new Action() {
                public void run() throws Exception {
                    frame.snapshot.captureClosed(foreign,frame.started);
                }
            });
        check(!flag(frame.snapshot,"captureAttempted"),
            "foreign capture consumed bound snapshot");
        frame.snapshot.captureClosed(frame.meter,frame.started);
        checkSnapshotAdopted(frame.snapshot,frame.meter);
        expectType("snapshot-foreign-meter",IOException.class,
            "R5_SNAPSHOT_NOT_READY",new Action() {
                public void run() throws Exception {
                    frame.snapshot.verifyReady(foreign,frame.started);
                }
            });
        expectType("snapshot-wrong-operation-start",IOException.class,
            "R5_SNAPSHOT_NOT_READY",new Action() {
                public void run() throws Exception {
                    frame.snapshot.verifyReady(frame.meter,frame.started+1);
                }
            });
        expectType("snapshot-reused-capture",IOException.class,
            "R5_SNAPSHOT_CAPTURE_STATE",new Action() {
                public void run() throws Exception {
                    frame.snapshot.captureClosed(frame.meter,frame.started);
                }
            });
        checkSnapshotAdopted(frame.snapshot,frame.meter);

        String[] driftFields={"units","groups","priorGroups","copiedEntries",
            "copyChunks","copyAllowance","started","groupStarted","open",
            "originalOwnerActive","inFlightOwnerGroups","originalOwnerStarted",
            "originalOwnerAdopted","sessionConstructorEntries"};
        for (int i=0;i<driftFields.length;i++) {
            final String name=driftFields[i];
            final Object original=field(frame.meter.meter,name);
            try {
                /* The only constructor-counter write is a labelled negative
                 * corruption probe, restored immediately; positive fixtures
                 * always observe the actual counter at zero. */
                setField(frame.meter.meter,name,increment(original));
                expectType("snapshot-meter-drift-"+name,IOException.class,
                    "R5_METER_CHANGED",new Action() {
                        public void run() throws Exception {
                            frame.snapshot.unchanged(frame.meter,0,0,false,
                                number(frame.snapshot,"groupStarted"),
                                number(frame.snapshot,"copies"),
                                number(frame.snapshot,"chunks"));
                        }
                    });
            } finally { setField(frame.meter.meter,name,original); }
            frame.snapshot.verifyReady(frame.meter,frame.started);
            check(number(frame.meter.meter,"sessionConstructorEntries")==0,
                "negative constructor-counter probe was not restored");
            snapshotCases++;
        }

        String[] staleFields={"groupUnits","maxGroupNanos","maxGroupUnits"};
        for (int i=0;i<staleFields.length;i++) {
            final String name=staleFields[i];
            final Object original=field(frame.meter.meter,name);
            try {
                setField(frame.meter.meter,name,increment(original));
                expectType("snapshot-stale-"+name,IOException.class,
                    "R5_STALE_SNAPSHOT",new Action() {
                        public void run() throws Exception {
                            frame.snapshot.verifyReady(frame.meter,frame.started);
                        }
                    });
            } finally { setField(frame.meter.meter,name,original); }
            frame.snapshot.verifyReady(frame.meter,frame.started);
            snapshotCases++;
        }

        final Object originalList=field(frame.meter.meter,"groupMeasures");
        final List<?> copiedList=new ArrayList<Object>((List<?>)originalList);
        try {
            setField(frame.meter.meter,"groupMeasures",copiedList);
            expectType("snapshot-measure-list-identity",IOException.class,
                "R5_METER_CHANGED",new Action() {
                    public void run() throws Exception {
                        frame.snapshot.verifyReady(frame.meter,frame.started);
                    }
                });
        } finally { setField(frame.meter.meter,"groupMeasures",originalList); }
        frame.snapshot.verifyReady(frame.meter,frame.started);
        snapshotCases++;

        @SuppressWarnings("unchecked")
        final List<Object> measures=(List<Object>)originalList;
        try {
            measures.add(null); // explicitly corrupt list count; never a positive measure
            expectType("snapshot-measure-list-count",IOException.class,
                "R5_METER_CHANGED",new Action() {
                    public void run() throws Exception {
                        frame.snapshot.verifyReady(frame.meter,frame.started);
                    }
                });
        } finally { measures.remove(measures.size()-1); }
        frame.snapshot.verifyReady(frame.meter,frame.started);
        checkSessionZero(frame.meter,"snapshot binding/drift guards");
        snapshotCases++;
    }

    private static void runAggregateSnapshotLimitCase() throws Exception {
        /* 637 wrapper groups + 2 actual adopted groups + metadata group = 640. */
        final SnapshotFrame over=buildSnapshotFrame(637,2,0,false);
        check(number(over.meter.meter,"groups")==638 &&
            number(over.meter.meter,"priorGroups")==2 &&
            number(over.meter.meter,"groups")+
                number(over.meter.meter,"priorGroups")==640,
            "aggregate-one-over fixture did not reach 640");
        expectType("snapshot-aggregate-640",IOException.class,
            "R5_METADATA_SNAPSHOT",new Action() {
                public void run() throws Exception {
                    over.snapshot.captureClosed(over.meter,over.started);
                }
            });
        check(number(over.meter.meter,"sessionConstructorEntries")==0,
            "aggregate boundary fixture changed constructor counter");
        snapshotCases++;
    }

    private static TokenFrame buildTokenFrame(int ordinaryWrappers,
            long syntheticPreCopyDemand) throws Exception {
        SnapshotFrame prefix=buildSnapshotFrame(ordinaryWrappers,1,
            syntheticPreCopyDemand,true);
        prefix.snapshot.verifyReady(prefix.meter,prefix.started);
        long beforeUnits=number(prefix.meter.meter,"units");
        long beforeGroups=number(prefix.meter.meter,"groups");
        long beforePrior=number(prefix.meter.meter,"priorGroups");
        long beforeCopies=number(prefix.meter.meter,"copiedEntries");
        long beforeChunks=number(prefix.meter.meter,"copyChunks");
        long beforeMaxNanos=number(prefix.meter.meter,"maxGroupNanos");
        long beforeMaxUnits=number(prefix.meter.meter,"maxGroupUnits");
        long allowance=number(prefix.meter.meter,"copyAllowance");
        long blocks=(RESULT_BYTES+(long)BLOCK-1)/BLOCK;
        long fee=5*blocks+3;
        check(RESULT_BYTES==4097 && blocks==RESULT_BLOCKS && blocks==2 &&
            MARKER_BYTES>0 && MARKER_BYTES<=MARKER_BOUND,
            "synthetic token frame constants changed");

        prefix.meter.method("beginGroup",new Class<?>[0]);
        long exportStarted=number(prefix.meter.meter,"groupStarted");
        syntheticCopyDemand(prefix.meter,RESULT_BYTES);
        syntheticCopyDemand(prefix.meter,RESULT_BYTES);
        syntheticCopyDemand(prefix.meter,32);
        long copyUnits=2*blocks+1;
        check(fee>=copyUnits,"synthetic fee underflow");
        prefix.meter.charge(fee-copyUnits);
        finishGroup(prefix.meter);

        List<?> measures=measureList(prefix.meter.meter);
        Object measure=measures.get((int)number(prefix.snapshot,"measures"));
        long closedNanos=number(measure,"nanos");
        check(number(measure,"units")==fee &&
            number(prefix.meter.meter,"units")==beforeUnits+fee &&
            number(prefix.meter.meter,"groups")==beforeGroups+1 &&
            number(prefix.meter.meter,"priorGroups")==beforePrior &&
            number(prefix.meter.meter,"copiedEntries")==beforeCopies+2L*RESULT_BYTES+32 &&
            number(prefix.meter.meter,"copyChunks")==beforeChunks+2*blocks+1,
            "real synthetic-demand export group deltas mismatch");
        check(field(prefix.snapshot,"measureList")==measures &&
            measures.size()==number(prefix.snapshot,"measures")+1 &&
            measures.size()==beforeGroups+1 &&
            number(prefix.meter.meter,"maxGroupNanos")==Math.max(beforeMaxNanos,closedNanos) &&
            number(prefix.meter.meter,"maxGroupUnits")==Math.max(beforeMaxUnits,fee) &&
            number(prefix.meter.meter,"sessionConstructorEntries")==0,
            "actual closed group identity/maxima/session state mismatch");
        check(closedNanos>=0 && closedNanos<=GROUP_NANOS &&
            fee<=STEP_LIMIT && allowance<=COPY_LIMIT,
            "actual token fixture exceeded unchanged caps");

        TokenFrame frame=new TokenFrame(prefix,exportStarted,fee,blocks,closedNanos,
            beforeUnits,beforeGroups,beforePrior,beforeCopies,beforeChunks,
            beforeMaxNanos,beforeMaxUnits,allowance,measure,RESULT_BYTES,MARKER_BYTES);
        tokenCases++;
        return frame;
    }

    private static CohortTerminalTelemetryStudy.R5PublicationToken makeToken(
            TokenFrame frame,CohortTerminalTelemetryStudy.R5Snapshot snapshot)
            throws Exception {
        Constructor<?> constructor=
            CohortTerminalTelemetryStudy.R5PublicationToken.class.getDeclaredConstructor(
                CohortTerminalTelemetryStudy.SetupMeter.class,
                CohortTerminalTelemetryStudy.R5Snapshot.class,
                long.class,long.class,long.class,long.class,long.class,Object.class,long.class);
        constructor.setAccessible(true);
        try {
            return (CohortTerminalTelemetryStudy.R5PublicationToken)constructor.newInstance(
                frame.prefix.meter,snapshot,Long.valueOf(frame.prefix.started),
                Long.valueOf(frame.exportStarted),Long.valueOf(frame.fee),
                Long.valueOf(frame.resultBytes),Long.valueOf(frame.blocks),
                frame.closedMeasure,Long.valueOf(frame.closedNanos));
        } catch (InvocationTargetException wrapped) {
            throwAsException(wrapped.getCause());
            throw new AssertionError("unreachable");
        }
    }

    private static void runBoundaryTokenCase() throws Exception {
        final TokenFrame frame=buildTokenFrame(637,0);
        frame.token=makeToken(frame,frame.prefix.snapshot);
        expectType("token-before-admit",IOException.class,
            "R5_PUBLICATION_NOT_ADMITTED",new Action() {
                public void run() throws Exception {
                    frame.token.afterPublication(frame.markerBytes);
                }
            });
        frame.token.admit(frame.markerBytes);
        assertTokenPost(frame,frame.markerBytes,"boundary token admission");
        frame.token.afterPublication(frame.markerBytes);

        expectType("token-duplicate-admission",IOException.class,
            "R5_PUBLICATION_TOKEN_USED",new Action() {
                public void run() throws Exception {
                    frame.token.admit(frame.markerBytes);
                }
            });
        assertTokenPost(frame,frame.markerBytes,"duplicate admission guard");

        final long beforeCopies=number(frame.prefix.meter.meter,"copiedEntries");
        try {
            setField(frame.prefix.meter.meter,"copiedEntries",Long.valueOf(beforeCopies+1));
            expectType("token-after-publication-meter-drift",IOException.class,
                "R5_METER_CHANGED",new Action() {
                    public void run() throws Exception {
                        frame.token.afterPublication(frame.markerBytes);
                    }
                });
        } finally {
            setField(frame.prefix.meter.meter,"copiedEntries",Long.valueOf(beforeCopies));
        }
        frame.token.afterPublication(frame.markerBytes);

        check(number(frame.prefix.meter.meter,"groups")+
            number(frame.prefix.meter.meter,"priorGroups")==640,
            "export close did not move aggregate 639 to 640");
        final long groups=number(frame.prefix.meter.meter,"groups");
        final long priors=number(frame.prefix.meter.meter,"priorGroups");
        expectType("wrapper-group-aggregate-one-over",IOException.class,null,
            new Action() {
                public void run() throws Exception {
                    frame.prefix.meter.method("beginGroup",new Class<?>[0]);
                }
            });
        check(!flag(frame.prefix.meter.meter,"open") &&
            number(frame.prefix.meter.meter,"groups")==groups &&
            number(frame.prefix.meter.meter,"priorGroups")==priors,
            "one-over wrapper group rejection mutated Meter");
        frame.token.afterPublication(frame.markerBytes);
        checkSessionZero(frame.prefix.meter,"639-to-640 token boundary");
        boundaryFrame=frame;
        tokenCases++;
    }

    private static void runTokenOneUseAndStateCases() throws Exception {
        final TokenFrame frame=buildTokenFrame(0,0);
        frame.token=makeToken(frame,frame.prefix.snapshot);
        final long copies=number(frame.prefix.meter.meter,"copiedEntries");
        expectType("token-foreign-snapshot",IOException.class,
            "R5_SNAPSHOT_NOT_READY",new Action() {
                public void run() throws Exception {
                    TokenFrame foreignFrame=buildTokenFrame(0,0);
                    try {
                        makeToken(foreignFrame,frame.prefix.snapshot);
                    } catch (Exception failure) {
                        throw failure;
                    }
                }
            });
        check(number(frame.prefix.meter.meter,"copiedEntries")==copies,
            "foreign token construction changed bound Meter");

        frame.token.admit(frame.markerBytes);
        assertTokenPost(frame,frame.markerBytes,"valid one-use token");
        frame.token.afterPublication(frame.markerBytes);

        final String[] driftFields={"maxGroupNanos","maxGroupUnits"};
        for (int i=0;i<driftFields.length;i++) {
            final String name=driftFields[i];
            final Object saved=field(frame.prefix.meter.meter,name);
            try {
                setField(frame.prefix.meter.meter,name,increment(saved));
                expectType("token-closed-max-drift-"+name,IOException.class,
                    "R5_CLOSED_GROUP_CHANGED",new Action() {
                        public void run() throws Exception {
                            frame.token.afterPublication(frame.markerBytes);
                        }
                    });
            } finally { setField(frame.prefix.meter.meter,name,saved); }
            frame.token.afterPublication(frame.markerBytes);
            tokenCases++;
        }

        final List<?> measures=measureList(frame.prefix.meter.meter);
        final int measureIndex=(int)number(frame.prefix.snapshot,"measures");
        final Object previous=measures.get(measureIndex-1);
        final Object closed=measures.get(measureIndex);
        @SuppressWarnings("unchecked")
        final List<Object> mutable=(List<Object>)measures;
        try {
            mutable.set(measureIndex,previous); // swap only in this negative guard
            expectType("token-closed-measure-identity-drift",IOException.class,
                "R5_CLOSED_GROUP_CHANGED",new Action() {
                    public void run() throws Exception {
                        frame.token.afterPublication(frame.markerBytes);
                    }
                });
        } finally { mutable.set(measureIndex,closed); }
        frame.token.afterPublication(frame.markerBytes);
        checkSessionZero(frame.prefix.meter,"token closed-state guards");
        tokenCases++;
    }

    private static void runClosedMeasureDriftCase() throws Exception {
        final TokenFrame frame=buildTokenFrame(0,0);
        frame.token=makeToken(frame,frame.prefix.snapshot);
        final Object original=field(frame.closedMeasure,"units");
        try {
            setField(frame.closedMeasure,"units",increment(original));
            expectType("token-closed-measure-units-drift",IOException.class,
                "R5_CLOSED_GROUP_CHANGED",new Action() {
                    public void run() throws Exception {
                        frame.token.admit(frame.markerBytes);
                    }
                });
        } finally { setField(frame.closedMeasure,"units",original); }
        check(field(frame.token,"retired").equals(Boolean.TRUE),
            "failed token admission did not retire single-use token");
        check(number(frame.prefix.meter.meter,"copiedEntries")==
            frame.beforeCopies+2*frame.resultBytes+32 &&
            number(frame.prefix.meter.meter,"copyChunks")==
                frame.beforeChunks+2*frame.blocks+1,
            "closed measure failure mutated copy counters");
        checkSessionZero(frame.prefix.meter,"closed measure drift fixture");
        tokenCases++;
    }

    private static void runCopyCapacityCases() throws Exception {
        long base=2L*RESULT_BYTES+32;
        TokenFrame probe=buildTokenFrame(0,0);
        long allowance=probe.allowance;
        check(allowance>=base+MARKER_BYTES && allowance<=COPY_LIMIT,
            "actual Meter copy allowance cannot cover synthetic token demand");
        long preCopyDemand=allowance-base-MARKER_BYTES;

        TokenFrame exact=buildTokenFrame(636,preCopyDemand);
        exact.token=makeToken(exact,exact.prefix.snapshot);
        exact.token.admit(MARKER_BYTES);
        assertTokenPost(exact,MARKER_BYTES,"exact copy capacity");
        check(number(exact.prefix.meter.meter,"copiedEntries")==allowance,
            "exact capacity token did not consume allowance exactly");
        exact.token.afterPublication(MARKER_BYTES);
        exactCapacityFrame=exact;
        tokenCases++;

        final TokenFrame over=buildTokenFrame(636,preCopyDemand);
        over.token=makeToken(over,over.prefix.snapshot);
        final long beforeCopies=number(over.prefix.meter.meter,"copiedEntries");
        final long beforeChunks=number(over.prefix.meter.meter,"copyChunks");
        expectType("token-copy-capacity-one-over",IOException.class,
            "R5_MARKER_COPY_CAPACITY",new Action() {
                public void run() throws Exception {
                    over.token.admit(MARKER_BYTES+1);
                }
            });
        check(number(over.prefix.meter.meter,"copiedEntries")==beforeCopies &&
            number(over.prefix.meter.meter,"copyChunks")==beforeChunks &&
            field(over.token,"retired").equals(Boolean.TRUE),
            "one-over copy admission mutated Meter or retained token");
        checkSessionZero(over.prefix.meter,"one-over copy capacity fixture");
        tokenCases++;
    }

    private static void runMarkerBoundCase() throws Exception {
        final TokenFrame frame=buildTokenFrame(0,0);
        frame.token=makeToken(frame,frame.prefix.snapshot);
        final long copies=number(frame.prefix.meter.meter,"copiedEntries");
        final long chunks=number(frame.prefix.meter.meter,"copyChunks");
        expectType("token-marker-one-over-bound",IOException.class,
            "R5_MARKER_BOUND",new Action() {
                public void run() throws Exception {
                    frame.token.admit(MARKER_BOUND+1);
                }
            });
        check(number(frame.prefix.meter.meter,"copiedEntries")==copies &&
            number(frame.prefix.meter.meter,"copyChunks")==chunks &&
            field(frame.token,"retired").equals(Boolean.TRUE),
            "marker-bound failure mutated Meter or retained token");
        checkSessionZero(frame.prefix.meter,"marker bound fixture");
        tokenCases++;
    }

    private static void assertTokenPost(TokenFrame frame,long marker,
            String label) throws Exception {
        CohortTerminalTelemetryStudy.SetupMeter meter=frame.prefix.meter;
        check(number(meter.meter,"units")==frame.beforeUnits+frame.fee &&
            number(meter.meter,"groups")==frame.beforeGroups+1 &&
            number(meter.meter,"priorGroups")==frame.beforePrior &&
            number(meter.meter,"copiedEntries")==frame.beforeCopies+
                2*frame.resultBytes+32+marker &&
            number(meter.meter,"copyChunks")==frame.beforeChunks+2*frame.blocks+2,
            label+" exact post-publication equations");
        check(number(meter.meter,"maxGroupNanos")==
                Math.max(frame.beforeMaxNanos,frame.closedNanos) &&
            number(meter.meter,"maxGroupUnits")==
                Math.max(frame.beforeMaxUnits,frame.fee) &&
            number(meter.meter,"sessionConstructorEntries")==0 &&
            flag(meter.meter,"originalOwnerStarted") &&
            flag(meter.meter,"originalOwnerAdopted") &&
            !flag(meter.meter,"originalOwnerActive") &&
            number(meter.meter,"inFlightOwnerGroups")==0,
            label+" maxima/lifecycle/session state");
        check(number(frame.token,"fee")==frame.fee &&
            number(frame.token,"resultBytes")==frame.resultBytes &&
            number(frame.token,"blocks")==frame.blocks &&
            field(frame.token,"closedMeasure")==frame.closedMeasure,
            label+" token identity and synthetic demand binding");
        checkSessionZero(meter,label);
    }

    private static void checkSnapshotAdopted(
            CohortTerminalTelemetryStudy.R5Snapshot snapshot,
            CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
        check(number(snapshot,"prior")==number(meter.meter,"priorGroups") &&
            number(snapshot,"prior")>=1 &&
            flag(snapshot,"ownerStarted") && flag(snapshot,"ownerAdopted") &&
            flag(meter.meter,"originalOwnerStarted") &&
            flag(meter.meter,"originalOwnerAdopted") &&
            !flag(meter.meter,"originalOwnerActive") &&
            number(meter.meter,"inFlightOwnerGroups")==0,
            "snapshot did not bind actual adopted-owner state");
        check(number(snapshot,"constructorEntries")==0 &&
            number(meter.meter,"sessionConstructorEntries")==0,
            "snapshot fixture constructed a Session");
    }

    private static void checkSessionZero(
            CohortTerminalTelemetryStudy.SetupMeter meter,String label) throws Exception {
        check(number(meter.meter,"sessionConstructorEntries")==0,
            label+" changed sessionConstructorEntries");
    }

    private static List<?> measureList(Object meter) throws Exception {
        Object value=field(meter,"groupMeasures");
        if (!(value instanceof List)) throw new IOException("CANARY_MEASURE_LIST_TYPE");
        return (List<?>)value;
    }

    private static void assertNoGeneration(Object arm,String label) throws Exception {
        String[] fields={"sink","nativeContext","electricalCandidate","board",
            "generator","ownerSession"};
        for (int i=0;i<fields.length;i++)
            check(field(arm,fields[i])==null,label+" unexpectedly populated "+fields[i]);
    }

    private static String summaryJson() throws Exception {
        if (boundaryFrame==null || exactCapacityFrame==null)
            throw new IOException("CANARY_POSITIVE_FRAMES_MISSING");
        return "{\"canary\":\"ADOPTED_SNAPSHOT_CANARY_PASS\",\"profile\":\"ADOPTED_SNAPSHOT_COMPONENT_ONLY\","+
            "\"assertions\":"+assertions+",\"snapshotCases\":"+snapshotCases+
            ",\"tokenCases\":"+tokenCases+",\"meterFixtures\":"+meterFixtures+
            ",\"ownerFixtures\":"+ownerFixtures+
            ",\"actualCaptureOriginalCalls\":0,\"actualRunFreshCalls\":0,"+
            "\"authoredSessionCalls\":0,\"actualSessionConstructorEntries\":0,"+
            "\"actualExporterOrResultMarker\":false,\"syntheticByteDemandOnly\":true,"+
            "\"studyModeInitialization\":\"AUTHORED_GUARD_ONLY\",\"fullDProof\":false,\"H1Proof\":false,"+
            "\"snapshotReadPrepayment\":"+SNAPSHOT_READS+
            ",\"snapshotAllocationUnits\":1,\"limits\":{\"groups\":"+OWNER_GROUPS+
            ",\"groupNanos\":"+GROUP_NANOS+",\"unitsPerGroup\":"+STEP_LIMIT+
            ",\"copiedEntries\":"+COPY_LIMIT+",\"copyChunkSize\":"+BLOCK+
            ",\"markerBound\":"+MARKER_BOUND+"},\"positiveFrames\":["+
            frameJson("aggregate_639_to_640",boundaryFrame)+","+
            frameJson("exact_copy_capacity",exactCapacityFrame)+"]}";
    }

    private static String frameJson(String name,TokenFrame f) throws Exception {
        long postUnits=number(f.prefix.meter.meter,"units");
        long postGroups=number(f.prefix.meter.meter,"groups");
        long postPrior=number(f.prefix.meter.meter,"priorGroups");
        long postCopies=number(f.prefix.meter.meter,"copiedEntries");
        long postChunks=number(f.prefix.meter.meter,"copyChunks");
        long postMaxNanos=number(f.prefix.meter.meter,"maxGroupNanos");
        long postMaxUnits=number(f.prefix.meter.meter,"maxGroupUnits");
        long preGroups=number(f.prefix.snapshot,"groups");
        long prePrior=number(f.prefix.snapshot,"prior");
        long preUnits=number(f.prefix.snapshot,"units");
        long preCopies=number(f.prefix.snapshot,"copies");
        long preChunks=number(f.prefix.snapshot,"chunks");
        long preMeasures=number(f.prefix.snapshot,"measures");
        long preMaxNanos=number(f.prefix.snapshot,"maxNanos");
        long preMaxUnits=number(f.prefix.snapshot,"maxUnits");
        return "{\"name\":\""+name+"\",\"R\":"+f.resultBytes+
            ",\"C\":"+f.blocks+",\"M\":"+f.markerBytes+
            ",\"syntheticByteDemandOnly\":true,\"preUnits\":"+preUnits+
            ",\"postUnits\":"+postUnits+",\"preGroups\":"+preGroups+
            ",\"postGroups\":"+postGroups+",\"prePriorGroups\":"+prePrior+
            ",\"postPriorGroups\":"+postPrior+",\"preAggregateGroups\":"+
            (preGroups+prePrior)+",\"postAggregateGroups\":"+(postGroups+postPrior)+
            ",\"preCopiedEntries\":"+preCopies+",\"postCopiedEntries\":"+postCopies+
            ",\"preCopyChunks\":"+preChunks+",\"postCopyChunks\":"+postChunks+
            ",\"preMeasureCount\":"+preMeasures+",\"postMeasureCount\":"+
            measureList(f.prefix.meter.meter).size()+
            ",\"copyAllowance\":"+f.allowance+",\"preMaxGroupNanos\":"+
            preMaxNanos+",\"postMaxGroupNanos\":"+postMaxNanos+
            ",\"preMaxGroupUnits\":"+preMaxUnits+",\"postMaxGroupUnits\":"+
            postMaxUnits+",\"sessionConstructorEntries\":"+
            number(f.prefix.meter.meter,"sessionConstructorEntries")+
            ",\"actualExportUnits\":"+f.fee+",\"actualExportNanos\":"+
            f.closedNanos+",\"actualMeasureCount\":"+
            measureList(f.prefix.meter.meter).size()+",\"originalOwnerStarted\":"+
            flag(f.prefix.meter.meter,"originalOwnerStarted")+
            ",\"originalOwnerAdopted\":"+
            flag(f.prefix.meter.meter,"originalOwnerAdopted")+
            ",\"originalOwnerActive\":"+
            flag(f.prefix.meter.meter,"originalOwnerActive")+
            ",\"inFlightOwnerGroups\":"+
            number(f.prefix.meter.meter,"inFlightOwnerGroups")+"}";
    }

    private static Method method(Class<?> type,String name,Class<?>... types)
            throws Exception {
        Method result=type.getDeclaredMethod(name,types);
        result.setAccessible(true);
        return result;
    }

    private static Object call(Method method,Object receiver,Object... args)
            throws Exception {
        try { return method.invoke(receiver,args); }
        catch (InvocationTargetException wrapped) {
            throwAsException(wrapped.getCause());
            throw new AssertionError("unreachable");
        }
    }

    private static void throwAsException(Throwable cause) throws Exception {
        if (cause instanceof Exception) throw (Exception)cause;
        if (cause instanceof Error) throw (Error)cause;
        throw new IllegalStateException(cause);
    }

    private static Throwable failure(Action action) throws Exception {
        try { action.run(); return null; }
        catch (Throwable caught) { return unwrap(caught); }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result=failure;
        while (result instanceof InvocationTargetException &&
                ((InvocationTargetException)result).getCause()!=null)
            result=((InvocationTargetException)result).getCause();
        return result;
    }

    private static void expectType(String label,Class<?> type,String message,
            Action action) throws Exception {
        Throwable caught=failure(action);
        if (caught==null || !type.isInstance(caught) ||
                (message!=null && !message.equals(caught.getMessage())))
            throw new AssertionError(label+": expected "+type.getName()+
                (message==null?"":" / "+message)+", got "+describe(caught));
        assertions++;
    }

    private static String describe(Throwable failure) {
        return failure==null?"no exception":
            failure.getClass().getName()+": "+failure.getMessage();
    }

    private static void check(boolean condition,String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static Object field(Object value,String name) throws Exception {
        Field f=value.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(value);
    }

    private static void setField(Object value,String name,Object replacement)
            throws Exception {
        Field f=value.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(value,replacement);
    }

    private static long number(Object value,String name) throws Exception {
        Object field=field(value,name);
        if (!(field instanceof Number)) throw new IOException("CANARY_FIELD_TYPE_"+name);
        return ((Number)field).longValue();
    }

    private static boolean flag(Object value,String name) throws Exception {
        Object field=field(value,name);
        if (!(field instanceof Boolean)) throw new IOException("CANARY_FIELD_TYPE_"+name);
        return ((Boolean)field).booleanValue();
    }

    private static Object increment(Object value) throws IOException {
        if (value instanceof Long) return Long.valueOf(((Long)value).longValue()+1);
        if (value instanceof Integer) return Integer.valueOf(((Integer)value).intValue()+1);
        if (value instanceof Boolean) return Boolean.valueOf(!((Boolean)value).booleanValue());
        throw new IOException("CANARY_CANNOT_INCREMENT_FIELD");
    }

    private static long staticNumber(Class<?> type,String name) throws Exception {
        Field f=type.getDeclaredField(name);
        f.setAccessible(true);
        return ((Number)f.get(null)).longValue();
    }

    private static void emptyDirectory(Path path,String message) throws Exception {
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(path)) {
            check(!entries.iterator().hasNext(),message);
        }
    }
}
