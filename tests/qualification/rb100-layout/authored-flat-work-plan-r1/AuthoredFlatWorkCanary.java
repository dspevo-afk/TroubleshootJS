package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Native, synthetic-only canary for the authored flat R7 work-plan seam.
 * It uses the current SharedOwner Meter through the existing Parent/SetupMeter
 * adapter and supplies only detached scalar demand to the plan. It never runs
 * captureOriginal, a Session, routing, generation, result export, or the host.
 */
public final class AuthoredFlatWorkCanary {
    private static final long R7_OPERATION_NANOS=90000000000L;
    private static final long R7_EXPORT_NANOS=5000000000L;
    private static final long R7_RESULT_LIMIT=1048576L;
    private static final int R7_COPY_CHUNK=4096;
    private static int assertions;

    /* Independent literal R ordering; do not ask the subject seam for keys. */
    private static final String[] WORK_KEYS={
        "elapsedCutoffNanos","cleanupNanos","groupsBeforeExport",
        "wrapperGroupsBeforeExport","maxGroupNanosBeforeExport",
        "maximumUnitsPerGroupBeforeExport","unitsBeforeExport","setupUnits",
        "setupGroups","setupCopiedEntries","setupCopyChunks",
        "originalCaptureOwnerUnits","originalCaptureWrapperUnits",
        "originalCaptureCallbackUnits","originalCaptureDiagnosticUnits",
        "originalCaptureGroups","originalCaptureCopiedEntries",
        "originalCaptureCopyChunks","nullOwnerUnits","nullWrapperUnits",
        "nullObserverUnits","observeOwnerUnits","observeWrapperUnits",
        "observeObserverUnits","observerUnits","meterUnits","metadataUnits",
        "metadataGroups","metadataCopiedEntries","metadataCopyChunks",
        "copiedEntries","copyChunks","copyChunkLimit","copiedEntryLimit"
    };

    private interface Action { void run() throws Exception; }

    private AuthoredFlatWorkCanary() { }

    public static void main(String[] args) throws Exception {
        runCanaries();
        System.out.println("SYNTHETIC_COMPONENT_ONLY; detachedDemand=SYNTHETIC; " +
            "original/session/routing/export/host-proof=NONE; assertions="+assertions);
    }

    private static void runCanaries() throws Exception {
        testFailedFirstFixtureDenials();
        testInputAndStudyEntryDenials();
        testGenericExportDenials();
        testDetachedNumberDomain();
        testPositivePlanAndFinalizer();
        testBoundIdentityFailures();
        testInvalidFinalizerAttempts();
        testReflectiveNotFrozenState();
        testLedgerUnderflowAndLimits();
        testOwnerGroupAdmissionBound();
    }

    private static void testInputAndStudyEntryDenials() throws Exception {
        expectMessage("COHORT_TERMINAL_INPUT_DENIED",new Action() {
            public void run() { CohortTerminalTelemetryInput.descriptorSha256(); }
        });
        expectMessage("COHORT_TERMINAL_INPUT_DENIED",new Action() {
            public void run() { CohortTerminalTelemetryInput.dataEntries(); }
        });
        expectMessage("COHORT_TERMINAL_INPUT_DENIED",new Action() {
            public void run() { CohortTerminalTelemetryInput.data(); }
        });
        expectMessage("ADOPTED_SNAPSHOT_COMPONENT_ONLY",new Action() {
            public void run() throws Exception { CohortTerminalTelemetryStudy.main(new String[0]); }
        });
    }

    private static void testFailedFirstFixtureDenials() throws Exception {
        expectMessage("FAILED_FIRST_FIXTURES_DENIED",new Action() {
            public void run() { FailedFirstFixtures.open(); }
        });
        expectMessage("FAILED_FIRST_FIXTURES_DENIED",new Action() {
            public void run() { FailedFirstFixtures.blocked(); }
        });
        expectMessage("FAILED_FIRST_FIXTURES_DENIED",new Action() {
            public void run() { new FailedFirstFixtures.Fixture(); }
        });
    }

    private static void testGenericExportDenials() throws Exception {
        /* Both legacy overloads must deny before inspecting arguments or output. */
        expectMessage("R5_EXPORT_DISABLED",new Action() {
            public void run() throws Exception {
                CohortTerminalTelemetryStudy.exportR5(null,null,null,0L,null,null);
            }
        });
        expectMessage("R5_EXPORT_DISABLED",new Action() {
            public void run() throws Exception {
                CohortTerminalTelemetryStudy.exportR5(null,null,null,0L,null,null,null);
            }
        });
    }

    private static void testDetachedNumberDomain() throws Exception {
        check(CohortTerminalTelemetryStudy.r7NonnegativeLong(Integer.valueOf(0))==0L,
            "INT_ZERO_ACCEPTED");
        check(CohortTerminalTelemetryStudy.r7NonnegativeLong(Integer.valueOf(7))==7L,
            "INT_ACCEPTED");
        check(CohortTerminalTelemetryStudy.r7NonnegativeLong(Long.valueOf(9L))==9L,
            "LONG_ACCEPTED");
        expectMessage("R7_SOURCE_NUMBER",new Action() {
            public void run() throws Exception {
                CohortTerminalTelemetryStudy.r7NonnegativeLong(Double.valueOf(1.0));
            }
        });
        expectMessage("R7_SOURCE_NUMBER",new Action() {
            public void run() throws Exception {
                CohortTerminalTelemetryStudy.r7NonnegativeLong(Float.valueOf(1.0f));
            }
        });
        expectMessage("R7_SOURCE_NUMBER",new Action() {
            public void run() throws Exception {
                CohortTerminalTelemetryStudy.r7NonnegativeLong(Boolean.TRUE);
            }
        });
        expectMessage("R7_SOURCE_NUMBER",new Action() {
            public void run() throws Exception {
                CohortTerminalTelemetryStudy.r7NonnegativeLong(null);
            }
        });
        expectMessage("R7_SOURCE_NEGATIVE",new Action() {
            public void run() throws Exception {
                CohortTerminalTelemetryStudy.r7NonnegativeLong(Long.valueOf(-1L));
            }
        });
    }

    private static void testPositivePlanAndFinalizer() throws Exception {
        final Fixture f=new Fixture();
        check(f.setupState.units==8L && f.setupState.groups==1 &&
            f.setupState.copiedEntries==3L && f.setupState.copyChunks==1,
            "SETUP_PHASE_METER");
        check(f.originalState.units==17L && f.originalState.groups==2 &&
            f.originalState.priorGroups==1 && f.originalState.copiedEntries==5L &&
            f.originalState.copyChunks==2,"ADOPTED_SYNTHETIC_PHASE_METER");
        check(f.nullState.units==26L && f.nullState.groups==3 &&
            f.nullState.copiedEntries==5L && f.nullState.copyChunks==2,
            "NULL_SYNTHETIC_PHASE_METER");
        check(f.observeState.units==44L && f.observeState.groups==4 &&
            f.observeState.priorGroups==1 && f.observeState.copiedEntries==5L &&
            f.observeState.copyChunks==2,"OBSERVE_SYNTHETIC_PHASE_METER");
        check(f.observeState.ownerStarted && f.observeState.ownerAdopted &&
            !f.observeState.ownerActive,"ADOPTED_OWNER_CHECKPOINT");
        check(f.originalMaxUnits==9L,"ORIGINAL_GROUP_MAXIMUM_UNITS");

        final CohortTerminalTelemetryStudy.R7WorkPlan plan=f.build();
        assertPlanShape(plan);
        final MeterState beforeFinalize=MeterState.capture(f.meter.meter);
        check(f.body.workGets==1,"SLOT_INSPECTION_READS_WORK_ONCE");
        check(beforeFinalize.units==44L+expectedPlanUnits(),"PREPAYMENT_METER_UNITS");
        check(beforeFinalize.groups==5 && beforeFinalize.priorGroups==1,
            "PREPAYMENT_GROUP_COUNTS");
        check(beforeFinalize.groupMeasuresCount==5 &&
            beforeFinalize.groupMeasures==f.observeState.groupMeasures,
            "ADOPTED_SNAPSHOT_GROUP_MEASURES");
        check(beforeFinalize.copiedEntries==100L && beforeFinalize.copyChunks==5,
            "PREPAYMENT_METER_COPIES");
        check(beforeFinalize.groupUnits==expectedPlanUnits() &&
            beforeFinalize.maxGroupUnits==expectedPlanUnits(),
            "PREPAYMENT_SINGLE_METADATA_GROUP");
        check(beforeFinalize.maxGroupNanos>=f.originalMaxNanos &&
            beforeFinalize.maxGroupNanos<=R7_EXPORT_NANOS,
            "PREPAYMENT_GROUP_TIME_BOUND");
        check(beforeFinalize.copyAllowance==R7_RESULT_LIMIT,"COPY_ALLOWANCE");

        plan.pre.verifyReady(f.meter,f.started);
        final Map<String,Object> work=f.workWithoutBodyGet();
        check(work!=null && work.size()==34,"FLAT_WORK_MAP_CREATED");
        assertPlaceholderWork(work);
        final int readsBeforeVerify=f.body.workGets;
        final MeterState beforeVerify=MeterState.capture(f.meter.meter);
        plan.verifyBound(f.meter,plan.pre,f.body,work);
        check(f.body.workGets==readsBeforeVerify,"VERIFY_BOUND_DOES_NOT_FETCH_BODY_WORK");
        check(beforeVerify.same(MeterState.capture(f.meter.meter)),
            "VERIFY_BOUND_DOES_NOT_MUTATE_METER");

        final MapSnapshot placeholders=MapSnapshot.capture(work);
        final long cutoff=4200000000L;
        plan.finalizeWork(f.started+cutoff);
        check(f.body.workGets==readsBeforeVerify+1,"FINALIZER_READS_BODY_WORK_ONCE");
        check(beforeFinalize.same(MeterState.capture(f.meter.meter)),
            "FINALIZER_DOES_NOT_MUTATE_METER");
        check(placeholders.sameKeys(MapSnapshot.capture(work)),
            "FINALIZER_REPLACES_EXISTING_SLOTS_ONLY");
        check(f.body.size()==1 && f.body.onlyWorkValueIs(work),"BODY_WORK_IDENTITY_RETAINED");
        plan.pre.verifyReady(f.meter,f.started);
        assertPositiveWorkValues(f,work,cutoff,beforeFinalize);

        final MapSnapshot finalized=MapSnapshot.capture(work);
        expectMessage("R7_FINALIZER_REUSED",new Action() {
            public void run() throws Exception { plan.finalizeWork(f.started+cutoff+1L); }
        });
        check(finalized.same(MapSnapshot.capture(work)),"REUSE_DOES_NOT_REWRITE_WORK");
        check(beforeFinalize.same(MeterState.capture(f.meter.meter)),
            "REUSE_DOES_NOT_MUTATE_METER");
        check(f.body.workGets==readsBeforeVerify+1,"REUSE_DOES_NOT_REFETCH_BODY_WORK");
    }

    private static void assertPlanShape(CohortTerminalTelemetryStudy.R7WorkPlan plan) {
        Field[] fields=plan.getClass().getDeclaredFields();
        int instanceFields=0,referenceFields=0,longFields=0,booleanFields=0;
        Set<String> referenceTypes=new HashSet<String>();
        for (int i=0;i<fields.length;i++) {
            Field field=fields[i];
            if (Modifier.isStatic(field.getModifiers())) continue;
            instanceFields++;
            Class<?> type=field.getType();
            if (type==Long.TYPE) longFields++;
            else if (type==Boolean.TYPE) booleanFields++;
            else {
                referenceFields++;
                referenceTypes.add(type.getName());
            }
        }
        check(instanceFields==44,"PLAN_INSTANCE_FIELD_COUNT");
        check(referenceFields==4 && longFields==38 && booleanFields==2,
            "PLAN_FIELD_CATEGORIES");
        check(referenceTypes.size()==3 &&
            referenceTypes.contains(CohortTerminalTelemetryStudy.SetupMeter.class.getName()) &&
            referenceTypes.contains(CohortTerminalTelemetryStudy.R5Snapshot.class.getName()) &&
            referenceTypes.contains(Map.class.getName()),"PLAN_HAS_ONLY_BOUND_CONTROL_REFERENCES");
    }

    private static void assertPositiveWorkValues(Fixture f,Map<String,Object> work,
            long cutoff,MeterState pre) {
        long expectedMetadataUnits=expectedPlanUnits();
        long expectedUnitsBeforeExport=pre.units+f.originalOwnerUnits+
            f.originalCallbackUnits+f.originalDiagnosticUnits;
        long expectedMetadataGroups=pre.groups-f.setupGroups-
            (f.originalGroups-f.originalOwnerGroups)-f.freshGroups;
        long expectedMetadataCopies=pre.copiedEntries-f.setupCopies-f.originalCopies-f.freshCopies;
        long expectedMetadataChunks=pre.copyChunks-f.setupChunks-f.originalChunks-f.freshChunks;
        long[] expected={
            cutoff,f.cleanupNanos,(long)pre.groups+pre.priorGroups,pre.groups,
            Math.max(pre.maxGroupNanos,f.originalMaxNanos),
            Math.max((long)pre.maxGroupUnits,f.originalMaxUnits),expectedUnitsBeforeExport,
            f.setupUnits,f.setupGroups,f.setupCopies,f.setupChunks,
            f.originalOwnerUnits,f.originalWrapperUnits,f.originalCallbackUnits,
            f.originalDiagnosticUnits,f.originalGroups,f.originalCopies,f.originalChunks,
            f.nullOwnerUnits,f.nullWrapperUnits,f.nullObserverUnits,
            f.observeOwnerUnits,f.observeWrapperUnits,f.observeObserverUnits,f.observerUnits,
            expectedUnitsBeforeExport,expectedMetadataUnits,expectedMetadataGroups,
            expectedMetadataCopies,expectedMetadataChunks,pre.copiedEntries,pre.copyChunks,
            R7_COPY_CHUNK,R7_RESULT_LIMIT
        };
        check(expected.length==WORK_KEYS.length,"EXPECTED_WORK_VECTOR_LENGTH");
        check(expectedUnitsBeforeExport==pre.units+49L,"OWNER_OFFSET_ADDED_ONCE");
        check(expectedMetadataUnits==pre.units-44L,"METADATA_UNITS_SUBTRACTED_ONCE");
        check(expectedMetadataGroups==3L,"METADATA_GROUP_RESIDUAL");
        check(expectedMetadataCopies==95L && expectedMetadataChunks==3L,
            "METADATA_COPY_RESIDUAL");
        check(pre.copiedEntries==100L && pre.copyChunks==5L,"COPIES_ALREADY_MERGED_ONCE");
        Iterator<?> entries=work.entrySet().iterator();
        for (int i=0;i<WORK_KEYS.length;i++) {
            check(entries.hasNext(),"WORK_ENTRY_MISSING_"+i);
            Map.Entry<?,?> entry=(Map.Entry<?,?>)entries.next();
            check(WORK_KEYS[i].equals(entry.getKey()),"WORK_KEY_ORDER_"+i);
            Object value=entry.getValue();
            check(value instanceof Long,"WORK_VALUE_LONG_"+i);
            check(((Long)value).longValue()==expected[i],"WORK_VALUE_"+WORK_KEYS[i]);
        }
        check(!entries.hasNext(),"WORK_ENTRY_EXTRA");
    }

    /**
     * The fixed 624 accounts for traversal and node admissions for the body,
     * work map and 34 Long leaves. Each literal ASCII key contributes six
     * charged UTF-8 inspection units per character. The common seam adds 184
     * units beyond this single full-tree walk and 95 copy entries.
     */
    private static long expectedTreeUnits() {
        long keyCharacters=0L;
        for (int i=0;i<WORK_KEYS.length;i++) keyCharacters+=WORK_KEYS[i].length();
        return 624L+6L*keyCharacters;
    }

    private static long expectedPlanUnits() {
        /* 2 slot reads + 4 alloc/put units + 19 snapshot + 26 freeze +
         * 38 finalization + 95 copies (35 + 26 + 34). */
        return expectedTreeUnits()+2L+4L+19L+26L+38L+95L;
    }

    private static void assertPlaceholderWork(Map<String,Object> work) {
        Iterator<?> entries=work.entrySet().iterator();
        for (int i=0;i<WORK_KEYS.length;i++) {
            check(entries.hasNext(),"PLACEHOLDER_ENTRY_MISSING_"+i);
            Map.Entry<?,?> entry=(Map.Entry<?,?>)entries.next();
            check(WORK_KEYS[i].equals(entry.getKey()),"PLACEHOLDER_ORDER_"+i);
            Object value=entry.getValue();
            check(value instanceof Integer || value instanceof Long,"PLACEHOLDER_INTEGER_"+i);
            check(((Number)value).longValue()==0L,"PLACEHOLDER_ZERO_"+i);
        }
        check(!entries.hasNext(),"PLACEHOLDER_ENTRY_EXTRA");
    }

    private static void testBoundIdentityFailures() throws Exception {
        final Fixture f=new Fixture();
        final Fixture foreign=new Fixture();
        final CohortTerminalTelemetryStudy.R7WorkPlan plan=f.build();
        final CohortTerminalTelemetryStudy.R7WorkPlan other=foreign.build();
        final Map<String,Object> work=f.workWithoutBodyGet();
        final Map<String,Object> foreignBody=new LinkedHashMap<String,Object>();
        final Map<String,Object> foreignWork=new LinkedHashMap<String,Object>();
        final MeterState before=MeterState.capture(f.meter.meter);
        final int reads=f.body.workGets;

        expectMessage("R7_FINALIZER_BINDING",new Action() {
            public void run() throws Exception { plan.verifyBound(foreign.meter,plan.pre,f.body,work); }
        });
        expectMessage("R7_FINALIZER_BINDING",new Action() {
            public void run() throws Exception { plan.verifyBound(f.meter,null,f.body,work); }
        });
        expectMessage("R7_FINALIZER_BINDING",new Action() {
            public void run() throws Exception { plan.verifyBound(f.meter,other.pre,f.body,work); }
        });
        expectMessage("R7_FINALIZER_BINDING",new Action() {
            public void run() throws Exception { plan.verifyBound(f.meter,plan.pre,foreignBody,work); }
        });
        expectMessage("R7_FINALIZER_BINDING",new Action() {
            public void run() throws Exception { plan.verifyBound(f.meter,plan.pre,f.body,foreignWork); }
        });
        plan.verifyBound(f.meter,plan.pre,f.body,work);
        check(f.body.workGets==reads,"BOUND_CHECKS_DO_NOT_FETCH_BODY_WORK");
        check(before.same(MeterState.capture(f.meter.meter)),"BOUND_CHECKS_DO_NOT_MUTATE_METER");
    }

    private static void testInvalidFinalizerAttempts() throws Exception {
        assertInvalidCutoff("ZERO",false);
        assertInvalidCutoff("OVER_90_SECONDS",true);
    }

    private static void assertInvalidCutoff(String label,final boolean overLimit) throws Exception {
        final Fixture f=new Fixture();
        final CohortTerminalTelemetryStudy.R7WorkPlan plan=f.build();
        final Map<String,Object> work=f.workWithoutBodyGet();
        final MapSnapshot beforeWork=MapSnapshot.capture(work);
        final MeterState beforeMeter=MeterState.capture(f.meter.meter);
        final int beforeReads=f.body.workGets;
        final long invalidExportStart=overLimit ? f.started+R7_OPERATION_NANOS+1L : f.started;
        expectMessage("R7_ELAPSED_CUTOFF",new Action() {
            public void run() throws Exception { plan.finalizeWork(invalidExportStart); }
        });
        check(beforeWork.same(MapSnapshot.capture(work)),"INVALID_CUTOFF_NO_WORK_WRITE_"+label);
        check(beforeMeter.same(MeterState.capture(f.meter.meter)),"INVALID_CUTOFF_NO_METER_CHANGE_"+label);
        check(f.body.workGets>=beforeReads && f.body.workGets<=beforeReads+1,
            "INVALID_CUTOFF_BOUNDED_BODY_READ_"+label);
        expectMessage("R7_FINALIZER_REUSED",new Action() {
            public void run() throws Exception { plan.finalizeWork(f.started+1L); }
        });
        check(beforeWork.same(MapSnapshot.capture(work)),"INVALID_CUTOFF_REUSE_NO_WRITE_"+label);
        check(beforeMeter.same(MeterState.capture(f.meter.meter)),"INVALID_CUTOFF_REUSE_NO_METER_"+label);
    }

    private static void testReflectiveNotFrozenState() throws Exception {
        final Fixture f=new Fixture();
        final CohortTerminalTelemetryStudy.R7WorkPlan plan=f.build();
        final Map<String,Object> work=f.workWithoutBodyGet();
        final MapSnapshot beforeWork=MapSnapshot.capture(work);
        final MeterState beforeMeter=MeterState.capture(f.meter.meter);
        final int beforeReads=f.body.workGets;

        /* Private invalid-state instrumentation only; the public seam returns frozen plans. */
        Field frozen=plan.getClass().getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(plan,false);
        expectMessage("R7_NOT_FROZEN",new Action() {
            public void run() throws Exception { plan.finalizeWork(f.started+1L); }
        });
        check(beforeWork.same(MapSnapshot.capture(work)),"NOT_FROZEN_NO_WORK_WRITE");
        check(beforeMeter.same(MeterState.capture(f.meter.meter)),"NOT_FROZEN_NO_METER_CHANGE");
        check(f.body.workGets==beforeReads,"NOT_FROZEN_FAILS_BEFORE_BODY_READ");
        expectMessage("R7_FINALIZER_REUSED",new Action() {
            public void run() throws Exception { plan.finalizeWork(f.started+2L); }
        });
    }

    private static void testLedgerUnderflowAndLimits() throws Exception {
        Fixture unitUnderflow=new Fixture();
        unitUnderflow.setupUnits=Long.MAX_VALUE;
        expectBuildMessage("R7_LEDGER_UNDERFLOW",unitUnderflow);

        Fixture copyUnderflow=new Fixture();
        copyUnderflow.setupCopies=101L;
        expectBuildMessage("R7_LEDGER_UNDERFLOW",copyUnderflow);

        Fixture foreignOwnerSnapshot=new Fixture();
        foreignOwnerSnapshot.originalOwnerGroups=2L;
        expectBuildMessage("R7_SNAPSHOT_BINDING",foreignOwnerSnapshot);

        Fixture cleanupBound=new Fixture();
        cleanupBound.cleanupNanos=120000000001L;
        expectBuildMessage("R7_LEDGER_BOUND",cleanupBound);

        Fixture originalTimeBound=new Fixture();
        originalTimeBound.originalMaxNanos=R7_EXPORT_NANOS+1L;
        expectBuildMessage("R7_LEDGER_BOUND",originalTimeBound);

        Fixture originalUnitsBound=new Fixture();
        originalUnitsBound.originalMaxUnits=32769L;
        expectBuildMessage("R7_LEDGER_BOUND",originalUnitsBound);
    }

    private static void expectBuildMessage(final String expected,final Fixture fixture)
            throws Exception {
        expectMessage(expected,new Action() {
            public void run() throws Exception { fixture.build(); }
        });
    }

    private static void testOwnerGroupAdmissionBound() throws Exception {
        final long started=System.nanoTime();
        CohortTerminalTelemetryStudy.Parent parent=new CohortTerminalTelemetryStudy.Parent();
        final CohortTerminalTelemetryStudy.SetupMeter meter=
            new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
        meter.method("beginOriginalOwner",new Class<?>[0]);
        for (int i=0;i<640;i++)
            meter.method("admitOwnerGroup",new Class<?>[]{Integer.TYPE},Integer.valueOf(i));
        expectMessage("native owner exceeded 640 aggregate measured groups",new Action() {
            public void run() throws Exception {
                meter.method("admitOwnerGroup",new Class<?>[]{Integer.TYPE},Integer.valueOf(640));
            }
        });
        check(((Number)CohortTerminalTelemetryStudy.field(meter.meter,"inFlightOwnerGroups"))
            .intValue()==640,"OWNER_GROUP_LIMIT_RETAINS_ADMITTED_COUNT");
        check(!Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter,"open")),
            "OWNER_GROUP_LIMIT_USES_NO_WRAPPER_GROUP");
    }

    private static void check(boolean ok,String code) {
        assertions++;
        if (!ok) throw new AssertionError(code);
    }

    private static void expectMessage(String message,Action action) throws Exception {
        try {
            action.run();
        } catch (Throwable failure) {
            if (message.equals(failure.getMessage())) {
                assertions++;
                return;
            }
            throw new AssertionError("EXPECTED_"+message+"_GOT_"+
                failure.getClass().getName()+"_"+failure.getMessage(),failure);
        }
        throw new AssertionError("MISSING_"+message);
    }

    private static final class Fixture {
        final long started;
        final CohortTerminalTelemetryStudy.Parent parent;
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final CountingBody body=new CountingBody();
        final MeterState setupState,originalState,nullState,observeState;
        long originalMaxNanos;
        long originalMaxUnits;

        long setupUnits=8L,setupGroups=1L,setupCopies=3L,setupChunks=1L;
        long originalOwnerUnits=13L,originalWrapperUnits=9L;
        long originalCallbackUnits=17L,originalDiagnosticUnits=19L;
        long originalGroups=2L,originalOwnerGroups=1L,originalCopies=2L,originalChunks=1L;
        long nullOwnerUnits=3L,nullWrapperUnits=4L,nullObserverUnits=2L;
        long observeOwnerUnits=5L,observeWrapperUnits=6L,observeObserverUnits=7L;
        long observerUnits=0L,cleanupNanos=23L;
        long freshGroups=0L,freshCopies=0L,freshChunks=0L;
        CohortTerminalTelemetryStudy.R7WorkPlan plan;

        Fixture() throws Exception {
            started=System.nanoTime();
            parent=new CohortTerminalTelemetryStudy.Parent();
            meter=new CohortTerminalTelemetryStudy.SetupMeter(parent,parent.meter(started));
            body.put("work",null);

            /* Actual setup wrapper demand: five units plus a three-entry copy. */
            meter.charge(5L);
            meter.copy(3);
            meter.finish();
            setupState=MeterState.capture(meter.meter);
            phase(setupState,8L,1,0,3L,1,"SETUP_PHASE");

            /* Synthetic detached original attribution through the real owner lifecycle. */
            meter.method("beginOriginalOwner",new Class<?>[0]);
            meter.method("admitOwnerGroup",new Class<?>[]{Integer.TYPE},Integer.valueOf(0));
            meter.charge(7L);
            meter.copy(2);
            meter.finish();
            meter.method("adoptOriginalOwner",new Class<?>[]{Integer.TYPE},Integer.valueOf(1));
            originalState=MeterState.capture(meter.meter);
            phase(originalState,17L,2,1,5L,2,"ORIGINAL_SYNTHETIC_PHASE");
            originalMaxNanos=originalState.maxGroupNanos;
            originalMaxUnits=originalState.maxGroupUnits;

            /* Detached NULL and OBSERVE counters are synthetic, metered groups. */
            meter.charge(nullOwnerUnits+nullWrapperUnits+nullObserverUnits);
            meter.finish();
            nullState=MeterState.capture(meter.meter);
            phase(nullState,26L,3,1,5L,2,"NULL_SYNTHETIC_PHASE");

            meter.charge(observeOwnerUnits+observeWrapperUnits+observeObserverUnits);
            meter.finish();
            observeState=MeterState.capture(meter.meter);
            phase(observeState,44L,4,1,5L,2,"OBSERVE_SYNTHETIC_PHASE");
        }

        CohortTerminalTelemetryStudy.R7WorkPlan build() throws Exception {
            if (plan!=null) throw new AssertionError("FIXTURE_PLAN_REUSED");
            /* Exact 25 detached longs in constructor order; started is separate. */
            plan=CohortTerminalTelemetryStudy.captureDetachedR7WorkPlan(
                meter,started,body,
                setupUnits,setupGroups,setupCopies,setupChunks,
                originalOwnerUnits,originalWrapperUnits,originalCallbackUnits,
                originalDiagnosticUnits,originalGroups,originalOwnerGroups,
                originalCopies,originalChunks,
                nullOwnerUnits,nullWrapperUnits,nullObserverUnits,
                observeOwnerUnits,observeWrapperUnits,observeObserverUnits,
                observerUnits,cleanupNanos,freshGroups,freshCopies,freshChunks,
                originalMaxNanos,originalMaxUnits);
            check(body.workGets==1,"DETACHED_SEAM_SLOT_GET_COUNT");
            check(body.size()==1,"DETACHED_SEAM_BODY_KEY_CLOSURE");
            return plan;
        }

        @SuppressWarnings("unchecked")
        Map<String,Object> workWithoutBodyGet() {
            if (body.size()!=1) throw new AssertionError("BODY_SHAPE");
            Map.Entry<String,Object> entry=body.entrySet().iterator().next();
            if (!"work".equals(entry.getKey()) || !(entry.getValue() instanceof Map))
                throw new AssertionError("BODY_WORK_SHAPE");
            return (Map<String,Object>)entry.getValue();
        }
    }

    private static void phase(MeterState state,long units,int groups,int prior,
            long copies,int chunks,String label) {
        check(state.units==units,label+"_UNITS");
        check(state.groups==groups && state.priorGroups==prior,label+"_GROUPS");
        check(state.copiedEntries==copies && state.copyChunks==chunks,label+"_COPIES");
        check(state.groupMeasuresCount==groups,label+"_MEASURE_LIST");
    }

    private static final class CountingBody extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID=1L;
        int workGets;
        public Object get(Object key) {
            if ("work".equals(key)) workGets++;
            return super.get(key);
        }
        boolean onlyWorkValueIs(Object value) {
            if (size()!=1) return false;
            Map.Entry<String,Object> entry=entrySet().iterator().next();
            return "work".equals(entry.getKey()) && entry.getValue()==value;
        }
    }

    private static final class MeterState {
        final long units,priorGroups,copiedEntries,maxGroupNanos,started,groupStarted;
        final long groupUnits;
        final int groups,copyChunks,maxGroupUnits,copyAllowance,sessionConstructorEntries;
        final int groupMeasuresCount;
        final Object groupMeasures;
        final boolean open,ownerStarted,ownerActive,ownerAdopted;

        private MeterState(Object meter) throws Exception {
            units=number(meter,"units"); groups=(int)number(meter,"groups");
            priorGroups=(int)number(meter,"priorGroups");
            copiedEntries=number(meter,"copiedEntries");
            copyChunks=(int)number(meter,"copyChunks");
            maxGroupNanos=number(meter,"maxGroupNanos");
            maxGroupUnits=(int)number(meter,"maxGroupUnits");
            copyAllowance=(int)number(meter,"copyAllowance");
            groupUnits=number(meter,"groupUnits");
            groupStarted=number(meter,"groupStarted");
            started=number(meter,"started");
            sessionConstructorEntries=(int)number(meter,"sessionConstructorEntries");
            groupMeasures=CohortTerminalTelemetryStudy.field(meter,"groupMeasures");
            groupMeasuresCount=((List<?>)groupMeasures).size();
            open=flag(meter,"open");
            ownerStarted=flag(meter,"originalOwnerStarted");
            ownerActive=flag(meter,"originalOwnerActive");
            ownerAdopted=flag(meter,"originalOwnerAdopted");
        }

        static MeterState capture(Object meter) throws Exception { return new MeterState(meter); }

        boolean same(MeterState other) {
            return units==other.units && groups==other.groups && priorGroups==other.priorGroups &&
                copiedEntries==other.copiedEntries && copyChunks==other.copyChunks &&
                maxGroupNanos==other.maxGroupNanos && maxGroupUnits==other.maxGroupUnits &&
                copyAllowance==other.copyAllowance && groupUnits==other.groupUnits &&
                groupStarted==other.groupStarted && started==other.started &&
                sessionConstructorEntries==other.sessionConstructorEntries &&
                groupMeasures==other.groupMeasures && groupMeasuresCount==other.groupMeasuresCount &&
                open==other.open && ownerStarted==other.ownerStarted &&
                ownerActive==other.ownerActive && ownerAdopted==other.ownerAdopted;
        }

        private static long number(Object meter,String name) throws Exception {
            return ((Number)CohortTerminalTelemetryStudy.field(meter,name)).longValue();
        }
        private static boolean flag(Object meter,String name) throws Exception {
            return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter,name));
        }
    }

    private static final class MapSnapshot {
        final List<Object> keys=new ArrayList<Object>();
        final List<Object> values=new ArrayList<Object>();
        static MapSnapshot capture(Map<?,?> map) {
            MapSnapshot snapshot=new MapSnapshot();
            for (Map.Entry<?,?> entry:map.entrySet()) {
                snapshot.keys.add(entry.getKey());
                snapshot.values.add(entry.getValue());
            }
            return snapshot;
        }
        boolean sameKeys(MapSnapshot other) { return keys.equals(other.keys); }
        boolean same(MapSnapshot other) {
            return sameKeys(other) && values.equals(other.values);
        }
    }
}
