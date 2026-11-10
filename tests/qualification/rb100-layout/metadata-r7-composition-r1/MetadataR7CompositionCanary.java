package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Synthetic-only N33, cleanup, and detached-R7 composition canary. */
public final class MetadataR7CompositionCanary {
    private static final String RUN_ID = "synthetic-r7-composition";
    private static final String SOURCE_SHA = "synthetic-source-sha";
    private static final String CONTEXT_SHA = "synthetic-context-sha";
    private static final String GEOMETRY = "synthetic-geometry";
    private static final String EFFECTS = "synthetic-effects";
    private static final String[] REQUESTED = {
        "n43", "n48", "n00", "n01", "n02", "n03", "n04", "n05", "n06", "n07",
        "n08", "n09", "n10", "n11", "n12", "n13", "n14", "n15", "n16", "n17",
        "n18", "n19", "n20", "n21", "n22", "n23", "n24", "n25", "n26", "n27",
        "n28", "n29", "n30", "n31", "n32", "n33", "n34", "n35", "n36", "n37",
        "n38", "n39", "n40", "n41", "n42", "n44", "n45", "n46", "n47",
        "n49", "n50", "n51", "n52", "n53", "n54", "n55", "n56", "n57", "n58",
        "n59"
    };
    private static final String[] WORK_KEYS = {
        "elapsedCutoffNanos", "cleanupNanos", "groupsBeforeExport",
        "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
        "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport", "setupUnits",
        "setupGroups", "setupCopiedEntries", "setupCopyChunks",
        "originalCaptureOwnerUnits", "originalCaptureWrapperUnits",
        "originalCaptureCallbackUnits", "originalCaptureDiagnosticUnits",
        "originalCaptureGroups", "originalCaptureCopiedEntries",
        "originalCaptureCopyChunks", "nullOwnerUnits", "nullWrapperUnits",
        "nullObserverUnits", "observeOwnerUnits", "observeWrapperUnits",
        "observeObserverUnits", "observerUnits", "meterUnits", "metadataUnits",
        "metadataGroups", "metadataCopiedEntries", "metadataCopyChunks",
        "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit"
    };

    private static int cases;
    private static int assertions;
    private static Fixture failedAliasFixture;

    private MetadataR7CompositionCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 1 ||
                !("SMOKE".equals(args[0]) || "SUITE".equals(args[0])))
            throw new IllegalArgumentException("CANARY_ARGUMENTS");
        cases = 0;
        assertions = 0;
        failedAliasFixture = null;
        String mode = args[0];
        if ("SMOKE".equals(mode)) runSmoke();
        else runSuite();
        System.out.println("SYNTHETIC_METADATA_R7_COMPOSITION_ONLY mode=" + mode +
                " cases=" + cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void runSmoke() throws Exception {
        Fixture fixture = stagedFixture(true, false);
        MeterSnapshot beforeR7 = new MeterSnapshot(fixture.meter);
        R7Attempt captured = captureR7(fixture, 5L);
        check(captured.failure == null && captured.plan != null,
                "SMOKE_R7_CAPTURE_RETURNS_PLAN");
        fixture.plan = captured.plan;
        fixture.work = child(fixture.body, "work");

        check(delta(fixture.setupDelta, 1L, 0L, 0L) && fixture.setupGroups == 1L,
                "SMOKE_SYNTHETIC_SETUP_PRICE");
        check(delta(fixture.phaseDelta, 299L, 146L, 67L),
                "SMOKE_N33_ONE_BORROW_PHASE_PRICE");
        check(delta(fixture.priorDelta, 2L, 1L, 1L),
                "SMOKE_FIXTURE_PRIOR_REPLACEMENT_PRICE");
        check(delta(fixture.cleanupDelta, 67L, 11L, 2L) &&
                cleanupMatches(child(fixture.body, "cleanup")),
                "SMOKE_CLEANUP_INSTALL_PRICE_AND_LITERAL_VALUES");
        check(bodyHeaderMatches(fixture.body), "SMOKE_BODY_HEADER_MATCHES_LITERAL");
        check(captureShapeMatches(fixture), "SMOKE_CAPTURE_TEMPLATE_AND_SYNTHETIC_VALUES");
        check(bodyCompositionMatches(fixture),
                "SMOKE_BODY_IDENTITIES_PENDING_SLOTS_AND_COHORT_ORDER");
        check(associationCompletedAndCleared(fixture.association),
                "SMOKE_N33_ASSOCIATION_CONSUMED");
        check(delta(beforeR7, new MeterSnapshot(fixture.meter),
                19046L, 110L, 18L), "SMOKE_R7_FIXED_OWNERSHIP_TREE_PRICE");
        MeterSnapshot closed = new MeterSnapshot(fixture.meter);
        check(closed.units == 19415L && closed.copiedEntries == 268L &&
                closed.copyChunks == 88L && closed.groups == 2L &&
                closed.priorGroups == 1L && closed.maxGroupUnits == 19414L &&
                closed.metadataSnapshotReady(fixture.started) &&
                closed.sessionConstructorEntries == 0L,
                "SMOKE_CLOSED_METER_PRICE_GROUPS_AND_NO_SESSION_ENTRIES");
        check(workKeysAndZeros(fixture.work), "SMOKE_WORK_HAS_LITERAL_34_ZERO_SLOTS");
        check(verify(fixture.plan, fixture.meter, fixture.plan.pre,
                fixture.body, fixture.work) == null, "SMOKE_EXACT_PLAN_BINDING");
        check(verifyReady(fixture.plan, fixture.meter, fixture.started) == null,
                "SMOKE_CLOSED_SNAPSHOT_READY_BEFORE_FINALIZE");

        long exportStart = validExportStart(fixture.started);
        Attempt finalized = finalizePlan(fixture.plan, exportStart);
        check(finalized.failure == null && finalized.value == null,
                "SMOKE_FINALIZE_SUCCEEDS");
        long cutoff = exportStart - fixture.started;
        check(cutoff > 0L && cutoff <= 90000000000L,
                "SMOKE_CUTOFF_FROM_EXPLICIT_TIME_ARGUMENTS");
        long[] expected = expectedWorkValues(cutoff, closed.maxGroupNanos);
        for (int i = 0; i < WORK_KEYS.length; i++) {
            Object value = fixture.work.get(WORK_KEYS[i]);
            check(value instanceof Long && ((Long) value).longValue() == expected[i],
                    "SMOKE_WORK_VALUE_" + i);
        }
        MeterSnapshot afterFinalize = new MeterSnapshot(fixture.meter);
        check(verifyReady(fixture.plan, fixture.meter, fixture.started) == null &&
                closed.sameState(afterFinalize),
                "SMOKE_FINALIZE_PRESERVES_CLOSED_METER_SNAPSHOT");
        check(fixture.inputs.unchanged() && fixture.body.get("work") == fixture.work,
                "SMOKE_INPUTS_AND_WORK_BINDING_STABLE");
        cases++;
    }

    private static void runSuite() throws Exception {
        testWrongBinding(0);
        testWrongBinding(1);
        testWrongBinding(2);
        testWrongBinding(3);
        testFinalizerReuse();
        testCutoffZero();
        testCutoffOverLimit();
        testForeignBodyWork();
        testMissingWorkSlot();
        testOccupiedWorkSlot();
        testNegativeScalar();
        testMissingBudgetAdoption();
        testOwnershipAliasAfterWorkPut();
        testFreshAttemptAfterFailure();
    }

    private static void testWrongBinding(int kind) throws Exception {
        CohortTerminalTelemetryStudy.R5Snapshot wrongSnapshot =
                kind == 1 ? readyFixture().plan.pre : null;
        Map<String, Object> wrongBody = kind == 2 ? new LinkedHashMap<String, Object>() :
                null;
        Map<String, Object> wrongWork = kind == 3 ? new LinkedHashMap<String, Object>() :
                null;
        Fixture fixture = readyFixture();
        MeterSnapshot before = new MeterSnapshot(fixture.meter);
        CohortTerminalTelemetryStudy.SetupMeter wrongMeter = kind == 0 ? newMeter() :
                fixture.meter;
        if (kind != 2) wrongBody = fixture.body;
        if (kind != 3) wrongWork = fixture.work;
        Throwable rejected = verify(fixture.plan,
                kind == 0 ? wrongMeter : fixture.meter,
                kind == 1 ? wrongSnapshot : fixture.plan.pre,
                wrongBody, wrongWork);
        check(exactError(rejected, "R7_FINALIZER_BINDING") &&
                (kind != 1 || (wrongSnapshot != null && wrongSnapshot != fixture.plan.pre)),
                "SUITE_VERIFY_BINDING_REJECTS_" + kind);
        Throwable retryBinding = verify(fixture.plan, fixture.meter, fixture.plan.pre,
                fixture.body, fixture.work);
        Attempt validFinalize = finalizePlan(fixture.plan,
                validExportStart(fixture.started));
        check(retryBinding == null &&
                validFinalize.failure == null,
                "SUITE_VERIFY_REJECTION_DID_NOT_CONSUME_" + kind);
        check(before.sameState(new MeterSnapshot(fixture.meter)),
                "SUITE_VERIFY_REJECTION_AND_FINALIZE_KEEP_METER_" + kind);
        cases++;
    }

    private static void testFinalizerReuse() throws Exception {
        Fixture fixture = readyFixture();
        MeterSnapshot before = new MeterSnapshot(fixture.meter);
        Attempt first = finalizePlan(fixture.plan, validExportStart(fixture.started));
        check(first.failure == null, "SUITE_FIRST_FINALIZE_SUCCEEDS");
        Attempt second = finalizePlan(fixture.plan, validExportStart(fixture.started));
        check(exactError(second.failure, "R7_FINALIZER_REUSED"),
                "SUITE_FINALIZER_REUSE_REJECTED");
        check(before.sameState(new MeterSnapshot(fixture.meter)),
                "SUITE_FINALIZER_REUSE_LEAVES_CLOSED_METER");
        cases++;
    }

    private static void testCutoffZero() throws Exception {
        Fixture fixture = readyFixture();
        MeterSnapshot before = new MeterSnapshot(fixture.meter);
        Attempt first = finalizePlan(fixture.plan, fixture.started);
        check(exactError(first.failure, "R7_ELAPSED_CUTOFF"),
                "SUITE_ZERO_CUTOFF_REJECTED");
        check(workKeysAndZeros(fixture.work), "SUITE_ZERO_CUTOFF_WRITES_NO_WORK_VALUES");
        Attempt retry = finalizePlan(fixture.plan, validExportStart(fixture.started));
        check(exactError(retry.failure, "R7_FINALIZER_REUSED"),
                "SUITE_ZERO_CUTOFF_CONSUMES_PLAN");
        check(before.sameState(new MeterSnapshot(fixture.meter)),
                "SUITE_ZERO_CUTOFF_LEAVES_CLOSED_METER");
        cases++;
    }

    private static void testCutoffOverLimit() throws Exception {
        Fixture fixture = readyFixture();
        MeterSnapshot before = new MeterSnapshot(fixture.meter);
        Attempt first = finalizePlan(fixture.plan, fixture.started + 90000000001L);
        check(exactError(first.failure, "R7_ELAPSED_CUTOFF"),
                "SUITE_OVER_LIMIT_CUTOFF_REJECTED");
        check(workKeysAndZeros(fixture.work), "SUITE_OVER_LIMIT_CUTOFF_WRITES_NO_WORK_VALUES");
        Attempt retry = finalizePlan(fixture.plan, validExportStart(fixture.started));
        check(exactError(retry.failure, "R7_FINALIZER_REUSED"),
                "SUITE_OVER_LIMIT_CUTOFF_CONSUMES_PLAN");
        check(before.sameState(new MeterSnapshot(fixture.meter)),
                "SUITE_OVER_LIMIT_CUTOFF_LEAVES_CLOSED_METER");
        cases++;
    }

    private static void testForeignBodyWork() throws Exception {
        Fixture fixture = readyFixture();
        Map<String, Object> foreign = new LinkedHashMap<String, Object>();
        foreign.put("foreign", "synthetic-only");
        fixture.body.put("work", foreign);
        MeterSnapshot before = new MeterSnapshot(fixture.meter);
        Attempt first = finalizePlan(fixture.plan, validExportStart(fixture.started));
        check(exactError(first.failure, "R7_FINALIZER_BINDING") &&
                fixture.body.get("work") == foreign,
                "SUITE_FOREIGN_BODY_WORK_FAILS_AFTER_CONSUMING_PLAN");
        fixture.body.put("work", fixture.work);
        Attempt retry = finalizePlan(fixture.plan, validExportStart(fixture.started));
        check(exactError(retry.failure, "R7_FINALIZER_REUSED") &&
                workKeysAndZeros(fixture.work),
                "SUITE_FOREIGN_WORK_FAILURE_CANNOT_RETRY");
        check(before.sameState(new MeterSnapshot(fixture.meter)),
                "SUITE_FOREIGN_WORK_FAILURE_LEAVES_CLOSED_METER");
        cases++;
    }

    private static void testMissingWorkSlot() throws Exception {
        Fixture fixture = stagedFixture(true, false);
        fixture.body.remove("work");
        TreeSnapshot beforeBody = new TreeSnapshot(fixture.body);
        MeterSnapshot beforeMeter = new MeterSnapshot(fixture.meter);
        R7Attempt result = captureR7(fixture, 5L);
        check(exactError(result.failure, "R7_WORK_SLOT_MISSING") && result.plan == null,
                "SUITE_MISSING_WORK_SLOT_REJECTED");
        check(delta(beforeMeter, new MeterSnapshot(fixture.meter), 1L, 0L, 0L),
                "SUITE_MISSING_WORK_SLOT_PARTIAL_PRICE_1_0_0");
        check(beforeBody.unchanged(fixture.body) && !fixture.body.containsKey("work"),
                "SUITE_MISSING_WORK_SLOT_BODY_STAYS_AT_INVALID_PRESTATE");
        cases++;
    }

    private static void testOccupiedWorkSlot() throws Exception {
        Fixture fixture = stagedFixture(true, false);
        Map<String, Object> occupied = new LinkedHashMap<String, Object>();
        occupied.put("foreign", "synthetic-only");
        fixture.body.put("work", occupied);
        TreeSnapshot beforeBody = new TreeSnapshot(fixture.body);
        MeterSnapshot beforeMeter = new MeterSnapshot(fixture.meter);
        R7Attempt result = captureR7(fixture, 5L);
        check(exactError(result.failure, "R7_WORK_SLOT_NOT_EMPTY") && result.plan == null,
                "SUITE_OCCUPIED_WORK_SLOT_REJECTED");
        check(delta(beforeMeter, new MeterSnapshot(fixture.meter), 2L, 0L, 0L),
                "SUITE_OCCUPIED_WORK_SLOT_PARTIAL_PRICE_2_0_0");
        check(beforeBody.unchanged(fixture.body) && fixture.body.get("work") == occupied,
                "SUITE_OCCUPIED_WORK_SLOT_RETAINS_INVALID_PRESTATE");
        cases++;
    }

    private static void testNegativeScalar() throws Exception {
        Fixture fixture = stagedFixture(true, false);
        TreeSnapshot beforeBody = new TreeSnapshot(fixture.body);
        MeterSnapshot beforeMeter = new MeterSnapshot(fixture.meter);
        R7Attempt result = captureR7(fixture, -1L);
        check(exactError(result.failure, "R7_SOURCE_NEGATIVE") && result.plan == null,
                "SUITE_NEGATIVE_SCALAR_REJECTED");
        check(delta(beforeMeter, new MeterSnapshot(fixture.meter), 0L, 0L, 0L),
                "SUITE_NEGATIVE_SCALAR_ZERO_PRICE");
        check(beforeBody.unchanged(fixture.body),
                "SUITE_NEGATIVE_SCALAR_BODY_UNCHANGED");
        cases++;
    }

    private static void testMissingBudgetAdoption() throws Exception {
        Fixture fixture = stagedFixture(false, false);
        R7Attempt result = captureR7(fixture, 5L);
        check(exactError(result.failure, "R5_METADATA_SNAPSHOT") && result.plan == null,
                "SUITE_MISSING_BUDGET_ADOPTION_REJECTED_AFTER_CLOSE");
        check(!isOpen(fixture.meter) && fixture.body.get("work") instanceof Map &&
                child(fixture.body, "work").size() == 34,
                "SUITE_MISSING_ADOPTION_CLOSES_METER_AND_LEAVES_WORK_INSTALLED");
        cases++;
    }

    private static void testOwnershipAliasAfterWorkPut() throws Exception {
        Fixture fixture = stagedFixture(true, true);
        MeterSnapshot before = new MeterSnapshot(fixture.meter);
        R7Attempt result = captureR7(fixture, 5L);
        check(exactError(result.failure, "BODY_OWNERSHIP_ALIAS") && result.plan == null,
                "SUITE_TEMPLATE_ALIAS_FAILS_OWNERSHIP_AFTER_WORK_PUT");
        check(delta(before, new MeterSnapshot(fixture.meter), 546L, 66L, 7L),
                "SUITE_TEMPLATE_ALIAS_TARGET_PRICE_546_66_7");
        check(fixture.body.get("qualifiedPrior") == child(fixture.capture, "template") &&
                fixture.body.get("work") instanceof Map && isOpen(fixture.meter),
                "SUITE_TEMPLATE_ALIAS_AND_WORK_REMAIN_WITH_METER_OPEN");
        failedAliasFixture = fixture;
        cases++;
    }

    private static void testFreshAttemptAfterFailure() throws Exception {
        Fixture failed = failedAliasFixture;
        Fixture fresh = stagedFixture(true, false);
        R7Attempt captured = captureR7(fresh, 5L);
        check(failed != null && failed.meter != fresh.meter && failed.body != fresh.body &&
                failed.capture != fresh.capture && isOpen(failed.meter) &&
                failed.body.get("work") instanceof Map,
                "SUITE_FRESH_ATTEMPT_DOES_NOT_REPAIR_FAILED_FIXTURE");
        check(captured.failure == null && captured.plan != null,
                "SUITE_FRESH_ATTEMPT_CAPTURES_INDEPENDENT_PLAN");
        fresh.plan = captured.plan;
        fresh.work = child(fresh.body, "work");
        Attempt finalized = finalizePlan(fresh.plan, validExportStart(fresh.started));
        check(finalized.failure == null && verifyReady(fresh.plan, fresh.meter,
                fresh.started) == null,
                "SUITE_FRESH_ATTEMPT_FINALIZES_WITH_CLOSED_SNAPSHOT");
        cases++;
    }

    private static Fixture readyFixture() throws Exception {
        Fixture fixture = stagedFixture(true, false);
        R7Attempt captured = captureR7(fixture, 5L);
        check(captured.failure == null && captured.plan != null,
                "SUITE_READY_FIXTURE_PLAN");
        fixture.plan = captured.plan;
        fixture.work = child(fixture.body, "work");
        return fixture;
    }

    private static Fixture stagedFixture(boolean adoptBudget, boolean aliasPrior)
            throws Exception {
        Fixture fixture = new Fixture(aliasPrior);

        MeterSnapshot beforeSetup = new MeterSnapshot(fixture.meter);
        fixture.meter.charge(1);
        fixture.meter.method("finishGroup", new Class<?>[0]);
        fixture.setupDelta = debitDelta(beforeSetup, new MeterSnapshot(fixture.meter));
        fixture.setupGroups = new MeterSnapshot(fixture.meter).groups - beforeSetup.groups;
        if (adoptBudget) {
            fixture.meter.method("beginOriginalOwner", new Class<?>[0]);
            fixture.meter.method("admitOwnerGroup", new Class<?>[]{int.class},
                    Integer.valueOf(0));
            fixture.meter.method("adoptOriginalOwner", new Class<?>[]{int.class},
                    Integer.valueOf(1));
        }

        MeterSnapshot beforePhases = new MeterSnapshot(fixture.meter);
        fixture.association = CurrentAssemblyMetadata.prepareCaptureCohort(
                fixture.meter, fixture.setup, fixture.capture);
        CurrentAssemblyMetadata.captureCohortForArms(fixture.meter, fixture.association);
        fixture.body = CurrentAssemblyMetadata.finishCaptureBody(fixture.meter,
                fixture.association, RUN_ID, SOURCE_SHA, CONTEXT_SHA, fixture.arms);
        fixture.phaseDelta = debitDelta(beforePhases, new MeterSnapshot(fixture.meter));
        fixture.pendingSlotsWereNull = fixture.body.containsKey("qualifiedPrior") &&
                fixture.body.get("qualifiedPrior") == null &&
                fixture.body.containsKey("work") && fixture.body.get("work") == null &&
                fixture.body.containsKey("cleanup") && fixture.body.get("cleanup") == null;

        MeterSnapshot beforePrior = new MeterSnapshot(fixture.meter);
        fixture.meter.copy(1);
        fixture.meter.charge(1);
        fixture.body.put("qualifiedPrior", fixture.qualifiedPrior);
        fixture.priorDelta = debitDelta(beforePrior, new MeterSnapshot(fixture.meter));

        MeterSnapshot beforeCleanup = new MeterSnapshot(fixture.meter);
        CurrentAssemblyMetadata.installCleanup(fixture.meter, fixture.body, 5L);
        fixture.cleanupDelta = debitDelta(beforeCleanup, new MeterSnapshot(fixture.meter));
        return fixture;
    }

    private static R7Attempt captureR7(Fixture fixture, long cleanupNanos) {
        return captureR7(fixture, cleanupNanos, 1L, 1L);
    }

    private static R7Attempt captureR7(Fixture fixture, long cleanupNanos,
            long originalGroups, long originalOwnerGroups) {
        try {
            return new R7Attempt(CohortTerminalTelemetryStudy.captureDetachedR7WorkPlan(
                    fixture.meter, fixture.started, fixture.body,
                    1L, 1L, 0L, 0L,
                    0L, 0L, 0L, 0L,
                    originalGroups, originalOwnerGroups, 0L, 0L,
                    0L, 0L, 0L,
                    0L, 0L, 0L, 0L,
                    cleanupNanos,
                    0L, 0L, 0L, 0L, 0L), null);
        } catch (Throwable failure) {
            return new R7Attempt(null, unwrap(failure));
        }
    }

    private static Attempt finalizePlan(CohortTerminalTelemetryStudy.R7WorkPlan plan,
            long exportStart) {
        try {
            plan.finalizeWork(exportStart);
            return new Attempt(null, null);
        } catch (Throwable failure) {
            return new Attempt(null, unwrap(failure));
        }
    }

    private static Throwable verify(CohortTerminalTelemetryStudy.R7WorkPlan plan,
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.R5Snapshot snapshot,
            Map<?, ?> body, Map<?, ?> work) {
        try {
            plan.verifyBound(meter, snapshot, body, work);
            return null;
        } catch (Throwable failure) {
            return unwrap(failure);
        }
    }

    private static Throwable verifyReady(CohortTerminalTelemetryStudy.R7WorkPlan plan,
            CohortTerminalTelemetryStudy.SetupMeter meter, long started) {
        try {
            plan.pre.verifyReady(meter, started);
            return null;
        } catch (Throwable failure) {
            return unwrap(failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof InvocationTargetException && failure.getCause() != null)
            failure = failure.getCause();
        return failure;
    }

    private static boolean exactError(Throwable failure, String code) {
        return failure != null && failure.getClass() == IOException.class &&
                code.equals(failure.getMessage());
    }

    private static long validExportStart(long started) {
        long now = System.nanoTime();
        return now > started ? now : started + 1L;
    }

    private static long[] expectedWorkValues(long cutoff, long maxGroupNanos) {
        return new long[] {
            cutoff, 5L, 3L, 2L, maxGroupNanos, 19414L, 19415L, 1L, 1L, 0L, 0L,
            0L, 0L, 0L, 0L, 1L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
            19415L, 19414L, 1L, 268L, 88L, 268L, 88L, 4096L, 1048576L
        };
    }

    private static boolean bodyCompositionMatches(Fixture fixture) {
        if (!fixture.pendingSlotsWereNull ||
                fixture.body.get("originalCapture") != fixture.capture ||
                fixture.body.get("arms") != fixture.arms ||
                fixture.body.get("qualifiedPrior") != fixture.qualifiedPrior ||
                fixture.body.get("work") == null || fixture.body.get("cleanup") == null)
            return false;
        Map<String, Object> cohort = child(fixture.body, "cohort");
        List<?> pair = (List<?>) cohort.get("netIds");
        List<?> requested = (List<?>) cohort.get("requestedNetIds");
        return Arrays.asList("n43", "n48").equals(pair) &&
                Arrays.asList(REQUESTED).equals(requested) &&
                pair != fixture.captureIds && pair != fixture.priorPair &&
                requested != fixture.captureIds && requested != fixture.priorRequested &&
                pair != requested;
    }

    private static boolean bodyHeaderMatches(Map<String, Object> body) {
        return "tsj-cohort-terminal-result-v2".equals(body.get("schema")) &&
                "OBSERVED_COHORT_TERMINAL_TELEMETRY".equals(body.get("mode")) &&
                "COMPLETED_DIAGNOSTIC_ONLY".equals(body.get("status")) &&
                RUN_ID.equals(body.get("runId")) &&
                SOURCE_SHA.equals(body.get("sourceManifestSha256")) &&
                "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed"
                        .equals(body.get("wireContractSha256")) &&
                CONTEXT_SHA.equals(body.get("inputContextSha256"));
    }

    private static boolean captureShapeMatches(Fixture fixture) {
        if (fixture.captureIds.size() != 60 || !"n00".equals(fixture.captureIds.get(0)) ||
                !"n59".equals(fixture.captureIds.get(59)) ||
                fixture.capture.get("netIds") != fixture.captureIds ||
                !Boolean.TRUE.equals(fixture.capture.get("ownersReleased")) ||
                !Boolean.TRUE.equals(fixture.capture.get("contextRestored")) ||
                !Long.valueOf(2L).equals(fixture.capture.get("cleanupNanos"))) return false;
        Map<String, Object> template = child(fixture.capture, "template");
        List<String> keys = Arrays.asList("geometryBeforeSha256", "geometryAfterSha256",
                "effectsBeforeSha256", "effectsAfterSha256");
        return new ArrayList<String>(template.keySet()).equals(keys) &&
                GEOMETRY.equals(template.get("geometryBeforeSha256")) &&
                GEOMETRY.equals(template.get("geometryAfterSha256")) &&
                EFFECTS.equals(template.get("effectsBeforeSha256")) &&
                EFFECTS.equals(template.get("effectsAfterSha256"));
    }

    private static boolean associationCompletedAndCleared(
            CurrentAssemblyMetadata.CaptureCohort association) throws Exception {
        return CohortTerminalTelemetryStudy.field(association, "meter") == null &&
                CohortTerminalTelemetryStudy.field(association, "capture") == null &&
                CohortTerminalTelemetryStudy.field(association, "cohort") == null &&
                Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(association,
                        "completed"));
    }

    private static boolean cleanupMatches(Map<String, Object> cleanup) {
        Map<String, Object> expected = new LinkedHashMap<String, Object>();
        expected.put("schema", "tsj-cohort-terminal-cleanup-v1");
        expected.put("originalCaptureOwnersReleased", Boolean.TRUE);
        expected.put("originalCaptureContextRestored", Boolean.TRUE);
        expected.put("nullArmAliasesDropped", Boolean.TRUE);
        expected.put("observeArmAliasesDropped", Boolean.TRUE);
        expected.put("sourceGeometryUnchanged", Boolean.TRUE);
        expected.put("effectsScope", "ORIGINAL_CAPTURE_COPY_ONLY");
        expected.put("originalCaptureCopyEffectsUnchanged", Boolean.TRUE);
        expected.put("contextRestored", Boolean.TRUE);
        expected.put("cleanupNanos", Long.valueOf(5L));
        return expected.equals(cleanup) &&
                new ArrayList<String>(expected.keySet()).equals(
                        new ArrayList<String>(cleanup.keySet()));
    }

    private static boolean workKeysAndZeros(Map<String, Object> work) {
        if (work == null || work.size() != WORK_KEYS.length ||
                !new ArrayList<String>(work.keySet()).equals(Arrays.asList(WORK_KEYS)))
            return false;
        for (int i = 0; i < WORK_KEYS.length; i++) {
            Object value = work.get(WORK_KEYS[i]);
            if (!(value instanceof Long) || ((Long) value).longValue() != 0L) return false;
        }
        return true;
    }

    private static Map<String, Object> child(Map<String, Object> map, String key) {
        return castMap(map.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static boolean delta(long[] delta, long units, long copies, long chunks) {
        return delta[0] == units && delta[1] == copies && delta[2] == chunks;
    }

    private static long[] debitDelta(MeterSnapshot before, MeterSnapshot after) {
        return new long[] {after.units - before.units,
                after.copiedEntries - before.copiedEntries,
                after.copyChunks - before.copyChunks};
    }

    private static boolean delta(MeterSnapshot before, MeterSnapshot after,
            long units, long copies, long chunks) {
        return delta(debitDelta(before, after), units, copies, chunks);
    }

    private static boolean isOpen(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        return Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(meter.meter, "open"));
    }

    private static CohortTerminalTelemetryStudy.SetupMeter newMeter() throws Exception {
        CohortTerminalTelemetryStudy.Parent parent =
                new CohortTerminalTelemetryStudy.Parent();
        long started = System.nanoTime();
        return new CohortTerminalTelemetryStudy.SetupMeter(parent, parent.meter(started));
    }

    private static long number(Object object, String field) throws Exception {
        return ((Number) CohortTerminalTelemetryStudy.field(object, field)).longValue();
    }

    private static final class Fixture {
        final Map<String, Object> data;
        final CohortTerminalTelemetryStudy.SetupResult setup;
        final Map<String, Object> capture;
        final List<String> captureIds;
        final List<String> priorPair;
        final List<String> priorRequested;
        final Map<String, Object> arms;
        final Map<String, Object> qualifiedPrior;
        final CohortTerminalTelemetryStudy.Parent parent;
        final long started;
        final CohortTerminalTelemetryStudy.SetupMeter meter;
        final InputSnapshot inputs;
        CurrentAssemblyMetadata.CaptureCohort association;
        Map<String, Object> body;
        Map<String, Object> work;
        CohortTerminalTelemetryStudy.R7WorkPlan plan;
        long[] setupDelta, phaseDelta, priorDelta, cleanupDelta;
        long setupGroups;
        boolean pendingSlotsWereNull;

        Fixture(boolean aliasPrior) throws Exception {
            captureIds = capturedIds();
            priorPair = new ArrayList<String>(Arrays.asList("n43", "n48"));
            priorRequested = new ArrayList<String>(Arrays.asList(REQUESTED));

            Map<String, Object> disposition = new LinkedHashMap<String, Object>();
            disposition.put("status", "RUN");
            disposition.put("reason", "TRIAL_EXECUTED");
            disposition.put("capturedFailedNetId", "n43");
            disposition.put("controlFailedNetId", "n48");
            disposition.put("cohortNetIds", priorPair);
            disposition.put("requestedNetIds", priorRequested);
            disposition.put("orderChanged", Boolean.TRUE);
            disposition.put("feedbackValidated", Boolean.TRUE);
            Map<String, Object> prior = new LinkedHashMap<String, Object>();
            prior.put("trialDisposition", disposition);
            data = new LinkedHashMap<String, Object>();
            data.put("prior", prior);
            setup = new CohortTerminalTelemetryStudy.SetupResult(data, 1L, 1L, 0, 0);

            Map<String, Object> template = new LinkedHashMap<String, Object>();
            template.put("geometryBeforeSha256", GEOMETRY);
            template.put("geometryAfterSha256", GEOMETRY);
            template.put("effectsBeforeSha256", EFFECTS);
            template.put("effectsAfterSha256", EFFECTS);
            capture = new LinkedHashMap<String, Object>();
            capture.put("netIds", captureIds);
            capture.put("template", template);
            capture.put("ownersReleased", Boolean.TRUE);
            capture.put("contextRestored", Boolean.TRUE);
            capture.put("cleanupNanos", Long.valueOf(2L));

            Map<String, Object> nullArm = new LinkedHashMap<String, Object>();
            nullArm.put("cleanup", armCleanup(1L));
            Map<String, Object> observeArm = new LinkedHashMap<String, Object>();
            observeArm.put("cleanup", armCleanup(2L));
            arms = new LinkedHashMap<String, Object>();
            arms.put("null", nullArm);
            arms.put("observe", observeArm);

            Map<String, Object> stagedPrior = new LinkedHashMap<String, Object>();
            stagedPrior.put("fixtureScope", "synthetic-only");
            qualifiedPrior = aliasPrior ? template : stagedPrior;
            inputs = new InputSnapshot(this);
            parent = new CohortTerminalTelemetryStudy.Parent();
            started = System.nanoTime();
            meter = new CohortTerminalTelemetryStudy.SetupMeter(parent,
                    parent.meter(started));
        }
    }

    private static Map<String, Object> armCleanup(long cleanupNanos) {
        Map<String, Object> cleanup = new LinkedHashMap<String, Object>();
        cleanup.put("adapterSessionAliasDropped", Boolean.TRUE);
        cleanup.put("adapterSearchAliasDropped", Boolean.TRUE);
        cleanup.put("adapterCandidateAliasDropped", Boolean.TRUE);
        cleanup.put("adapterRouteResultAliasDropped", Boolean.TRUE);
        cleanup.put("adapterBoardTemplateAliasesDropped", Boolean.TRUE);
        cleanup.put("sourceGeometryBeforeSha256", GEOMETRY);
        cleanup.put("sourceGeometryAfterSha256", GEOMETRY);
        cleanup.put("contextRestored", Boolean.TRUE);
        cleanup.put("cleanupNanos", Long.valueOf(cleanupNanos));
        return cleanup;
    }

    private static List<String> capturedIds() {
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < 60; i++) values.add(id(i));
        return values;
    }

    private static String id(int value) {
        return "n" + (value < 10 ? "0" : "") + Integer.toString(value);
    }

    private static final class R7Attempt {
        final CohortTerminalTelemetryStudy.R7WorkPlan plan;
        final Throwable failure;

        R7Attempt(CohortTerminalTelemetryStudy.R7WorkPlan plan, Throwable failure) {
            this.plan = plan;
            this.failure = failure;
        }
    }

    private static final class Attempt {
        final Object value;
        final Throwable failure;

        Attempt(Object value, Throwable failure) {
            this.value = value;
            this.failure = failure;
        }
    }

    private static final class MeterSnapshot {
        final long units, groups, priorGroups, copiedEntries, copyChunks;
        final long maxGroupNanos, maxGroupUnits, copyAllowance, groupUnits;
        final long groupStarted, started, sessionConstructorEntries, inFlightOwnerGroups;
        final Object groupMeasures;
        final int measureCount;
        final boolean open, ownerActive, ownerStarted, ownerAdopted;

        MeterSnapshot(CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
            Object nativeMeter = meter.meter;
            units = number(nativeMeter, "units");
            groups = number(nativeMeter, "groups");
            priorGroups = number(nativeMeter, "priorGroups");
            copiedEntries = number(nativeMeter, "copiedEntries");
            copyChunks = number(nativeMeter, "copyChunks");
            maxGroupNanos = number(nativeMeter, "maxGroupNanos");
            maxGroupUnits = number(nativeMeter, "maxGroupUnits");
            copyAllowance = number(nativeMeter, "copyAllowance");
            groupUnits = number(nativeMeter, "groupUnits");
            groupStarted = number(nativeMeter, "groupStarted");
            started = number(nativeMeter, "started");
            sessionConstructorEntries = number(nativeMeter, "sessionConstructorEntries");
            inFlightOwnerGroups = number(nativeMeter, "inFlightOwnerGroups");
            groupMeasures = CohortTerminalTelemetryStudy.field(nativeMeter, "groupMeasures");
            measureCount = ((List<?>) groupMeasures).size();
            open = Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(nativeMeter, "open"));
            ownerActive = Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(
                    nativeMeter, "originalOwnerActive"));
            ownerStarted = Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(
                    nativeMeter, "originalOwnerStarted"));
            ownerAdopted = Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(
                    nativeMeter, "originalOwnerAdopted"));
        }

        boolean sameState(MeterSnapshot other) {
            return units == other.units && groups == other.groups &&
                    priorGroups == other.priorGroups && copiedEntries == other.copiedEntries &&
                    copyChunks == other.copyChunks && maxGroupNanos == other.maxGroupNanos &&
                    maxGroupUnits == other.maxGroupUnits && copyAllowance == other.copyAllowance &&
                    groupUnits == other.groupUnits && groupStarted == other.groupStarted &&
                    started == other.started &&
                    sessionConstructorEntries == other.sessionConstructorEntries &&
                    inFlightOwnerGroups == other.inFlightOwnerGroups &&
                    groupMeasures == other.groupMeasures && measureCount == other.measureCount &&
                    open == other.open && ownerActive == other.ownerActive &&
                    ownerStarted == other.ownerStarted && ownerAdopted == other.ownerAdopted;
        }

        boolean metadataSnapshotReady(long expectedStarted) {
            return !open && !ownerActive && ownerStarted && ownerAdopted &&
                    inFlightOwnerGroups == 0L && started == expectedStarted &&
                    groups == measureCount && priorGroups == 1L && groupUnits <= 32768L;
        }
    }

    private static final class InputSnapshot {
        final TreeSnapshot data, capture, arms, qualifiedPrior;

        InputSnapshot(Fixture fixture) {
            data = new TreeSnapshot(fixture.data);
            capture = new TreeSnapshot(fixture.capture);
            arms = new TreeSnapshot(fixture.arms);
            qualifiedPrior = new TreeSnapshot(fixture.qualifiedPrior);
        }

        boolean unchanged() {
            return data.unchanged(data.root) && capture.unchanged(capture.root) &&
                    arms.unchanged(arms.root) &&
                    qualifiedPrior.unchanged(qualifiedPrior.root);
        }
    }

    private static final class TreeSnapshot {
        final Object root;
        final IdentityHashMap<Object, List<Object[]>> contents =
                new IdentityHashMap<Object, List<Object[]>>();

        TreeSnapshot(Object root) {
            this.root = root;
            capture(root);
        }

        private void capture(Object value) {
            if (value instanceof Map) {
                if (contents.containsKey(value)) return;
                List<Object[]> entries = new ArrayList<Object[]>();
                contents.put(value, entries);
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    Object key = entry.getKey();
                    Object child = entry.getValue();
                    entries.add(new Object[] {key, child});
                    capture(child);
                }
            } else if (value instanceof List) {
                if (contents.containsKey(value)) return;
                List<Object[]> entries = new ArrayList<Object[]>();
                contents.put(value, entries);
                for (Object child : (List<?>) value) {
                    entries.add(new Object[] {child});
                    capture(child);
                }
            }
        }

        boolean unchanged(Object candidate) {
            return candidate == root && matches(candidate);
        }

        private boolean matches(Object value) {
            List<Object[]> expected = contents.get(value);
            if (expected == null) return true;
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                if (map.size() != expected.size()) return false;
                Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
                for (int i = 0; i < expected.size(); i++) {
                    if (!it.hasNext()) return false;
                    Map.Entry<?, ?> entry = it.next();
                    Object[] pair = expected.get(i);
                    if (entry.getKey() != pair[0] || entry.getValue() != pair[1] ||
                            !matches(pair[1])) return false;
                }
                return !it.hasNext();
            }
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                if (list.size() != expected.size()) return false;
                Iterator<?> it = list.iterator();
                for (int i = 0; i < expected.size(); i++) {
                    if (!it.hasNext()) return false;
                    Object item = expected.get(i)[0];
                    if (it.next() != item || !matches(item)) return false;
                }
                return !it.hasNext();
            }
            return false;
        }
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
