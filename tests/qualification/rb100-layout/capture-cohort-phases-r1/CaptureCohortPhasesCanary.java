package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synthetic-only canary for the capture/cohort association phases. */
public final class CaptureCohortPhasesCanary {
    private static final String RUN_ID = "synthetic-capture-bound-run";
    private static final String SOURCE_SHA = "synthetic-source-sha";
    private static final String CONTEXT_SHA = "synthetic-context-sha";
    private static final String[] EXPECTED_REQUESTED = {
        "n43", "n48", "n00", "n01", "n02", "n03", "n04", "n05", "n06", "n07",
        "n08", "n09", "n10", "n11", "n12", "n13", "n14", "n15", "n16", "n17",
        "n18", "n19", "n20", "n21", "n22", "n23", "n24", "n25", "n26", "n27",
        "n28", "n29", "n30", "n31", "n32", "n33", "n34", "n35", "n36", "n37",
        "n38", "n39", "n40", "n41", "n42", "n44", "n45", "n46", "n47",
        "n49", "n50", "n51", "n52", "n53", "n54", "n55", "n56", "n57", "n58",
        "n59"
    };

    private static int assertions;
    private static int cases;

    private CaptureCohortPhasesCanary() { }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 0)
            throw new IllegalArgumentException("CANARY_ARGUMENTS");

        testOneBorrowRoundTrip();
        testRepeatedBorrowFees();
        testPrepareNullArguments();
        testBorrowNullArguments();
        testFinishNullArguments();
        testCaptureNetIdsRejections();
        testInheritedCohortFailures();
        testThrowingCaptureGet();
        testPrepareClosedGroupGuard();
        testWrongMeterBorrow();
        testCompletedAssociationOneUse();
        testFreshAssociationAfterCompletion();
        testLegacyCohortControl();
        testPrepareAllocationAdmissionFailure();
        testFinishAdmissionFailurePreservesOpenAssociation();

        System.out.println("SYNTHETIC_CAPTURE_COHORT_PHASES_COMPONENT_ONLY cases=" +
                cases + " assertions=" + assertions +
                "; normalHostAcceptance=NONE genuine=UNSPENT_GATED");
    }

    private static void testOneBorrowRoundTrip() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        long[] beforePrepare = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt prepared = prepare(meter, fixture.setup, fixture.capture);
        check(prepared.failure == null &&
                prepared.value instanceof CurrentAssemblyMetadata.CaptureCohort,
                "PREPARE_RETURNS_OPEN_ASSOCIATION");
        CurrentAssemblyMetadata.CaptureCohort association =
                (CurrentAssemblyMetadata.CaptureCohort) prepared.value;
        Object raw = associationField(association, "cohort");
        check(associationOpen(association, meter, fixture.capture, raw),
                "PREPARED_ASSOCIATION_BINDS_METER_CAPTURE_RAW_COHORT");
        check(privateHolderShape(), "ASSOCIATION_IS_PRIVATE_STATE_HOLDER");
        List<?> rawPair = (List<?>) CohortTerminalTelemetryStudy.field(raw, "netIds");
        List<?> rawRequested = (List<?>) CohortTerminalTelemetryStudy.field(raw, "requested");
        check(Arrays.asList("n43", "n48").equals(rawPair) &&
                Arrays.asList(EXPECTED_REQUESTED).equals(rawRequested) &&
                !rawRequested.equals(fixture.decoyIds),
                "RAW_COHORT_USES_NONPREFIX_PAIR_AND_LITERAL_CAPTURE_ORDER");
        check(rawPair != fixture.captureIds && rawPair != fixture.priorPair &&
                rawRequested != fixture.captureIds &&
                rawRequested != fixture.priorRequested,
                "PREPARED_RAW_COHORT_LISTS_ARE_DETACHED_FROM_INPUTS");
        check(fixture.capture.getCount == 1 && fixture.capture.netIdsGets == 1 &&
                fixture.data.getCount == 1, "PREPARE_PERFORMS_ONE_CAPTURE_AND_PRIOR_GET");
        check(debitDelta(beforePrepare, debit(meter), 265L, 127L, 64L),
                "PREPARE_FEE_265_127_64");

        long[] beforeBorrow = debit(meter);
        Attempt borrowed = borrow(meter, association);
        check(borrowed.failure == null && borrowed.value == raw,
                "ONE_PREARM_BORROW_RETURNS_ASSOCIATED_RAW_COHORT");
        check(debitDelta(beforeBorrow, debit(meter), 3L, 0L, 0L),
                "ONE_BORROW_FEE_3_0_0");
        check(associationOpen(association, meter, fixture.capture, raw),
                "BORROW_LEAVES_ASSOCIATION_OPEN");

        long[] beforeFinish = debit(meter);
        Attempt finished = finish(meter, association, fixture.arms);
        check(finished.failure == null && finished.value instanceof Map,
                "FINISH_RETURNS_PENDING_BODY");
        Map<String, Object> body = castMap(finished.value);
        check(body.get("originalCapture") == fixture.capture &&
                body.get("arms") == fixture.arms && body.get("qualifiedPrior") == null &&
                body.get("work") == null && body.get("cleanup") == null,
                "FINISHED_BODY_PRESERVES_CAPTURE_ARMS_AND_PENDING_SLOTS");
        Map<String, Object> projected = child(body, "cohort");
        check(projected.get("netIds") == rawPair &&
                projected.get("requestedNetIds") == rawRequested,
                "FINISH_PROJECTS_SAME_RAW_COHORT_LISTS");
        check(!body.containsValue(association) && !body.containsValue(meter),
                "ASSOCIATION_AND_METER_NOT_IN_WIRE_MAP");
        check(associationCompletedAndCleared(association),
                "SUCCESSFUL_FINISH_CLEARS_ASSOCIATION_ALIASES");
        check(debitDelta(beforeFinish, debit(meter), 31L, 19L, 3L) &&
                debitDelta(beforePrepare, debit(meter), 299L, 146L, 67L),
                "FINISH_AND_ONE_BORROW_TOTAL_FEES");
        check(before.unchanged(), "ROUNDTRIP_INPUT_MAPS_AND_LISTS_UNCHANGED");
        cases++;
    }

    private static void testRepeatedBorrowFees() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        long[] beforePrepare = debit(meter);
        CurrentAssemblyMetadata.CaptureCohort association = prepared(fixture, meter);
        Object raw = associationField(association, "cohort");
        long[] beforeBorrows = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt first = borrow(meter, association);
        Attempt second = borrow(meter, association);
        check(first.failure == null && second.failure == null &&
                first.value == raw && second.value == raw,
                "TWO_METADATA_BORROWS_RETURN_SAME_RAW_COHORT");
        check(debitDelta(beforeBorrows, debit(meter), 6L, 0L, 0L),
                "TWO_BORROWS_FEE_6_0_0");
        check(debitDelta(beforePrepare, debit(meter), 271L, 127L, 64L),
                "PREPARE_PLUS_TWO_BORROWS_FEE_271_127_64");
        check(associationOpen(association, meter, fixture.capture, raw),
                "REPEATED_BORROW_DOES_NOT_COMPLETE_ASSOCIATION");
        check(before.unchanged(), "REPEATED_BORROW_INPUTS_UNCHANGED");
        long[] beforeFinish = debit(meter);
        Attempt finished = finish(meter, association, fixture.arms);
        check(finished.failure == null && finished.value instanceof Map &&
                associationCompletedAndCleared(association),
                "TWO_BORROW_ASSOCIATION_FINISHES_ONCE");
        check(debitDelta(beforeFinish, debit(meter), 31L, 19L, 3L),
                "TWO_BORROW_FINISH_FEE_31_19_3");
        check(debitDelta(beforePrepare, debit(meter), 302L, 146L, 67L),
                "TWO_BORROW_TOTAL_FEE_302_146_67");
        cases++;
    }

    private static void testPrepareNullArguments() throws Exception {
        Fixture fixture = fixture(false);
        for (int slot = 0; slot < 3; slot++) {
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            long[] beforeDebit = debit(meter);
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = prepareNullArgument(slot, meter, fixture);
            assertFailure(attempt, IOException.class, "METADATA_ARGUMENTS",
                    "PREPARE_NULL_ARGUMENT_" + slot, before);
            check(fixture.capture.getCount == 0 && fixture.data.getCount == 0,
                    "PREPARE_NULL_ARGUMENT_" + slot + "_NO_READS");
            if (slot != 0)
                check(debitDelta(beforeDebit, debit(meter), 0L, 0L, 0L),
                        "PREPARE_NULL_ARGUMENT_" + slot + "_ZERO_COST");
            cases++;
        }
    }

    private static void testBorrowNullArguments() throws Exception {
        for (int slot = 0; slot < 2; slot++) {
            Fixture fixture = fixture(false);
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            CurrentAssemblyMetadata.CaptureCohort association = prepared(fixture, meter);
            Object raw = associationField(association, "cohort");
            long[] beforeDebit = debit(meter);
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = slot == 0 ? borrow(null, association) : borrow(meter, null);
            assertFailure(attempt, IOException.class, "METADATA_ARGUMENTS",
                    "BORROW_NULL_ARGUMENT_" + slot, before);
            check(associationOpen(association, meter, fixture.capture, raw),
                    "BORROW_NULL_ARGUMENT_" + slot + "_STATE_OPEN");
            check(debitDelta(beforeDebit, debit(meter), 0L, 0L, 0L) &&
                    fixture.capture.getCount == 1 && fixture.data.getCount == 1,
                    "BORROW_NULL_ARGUMENT_" + slot + "_NO_FEE_OR_READ");
            cases++;
        }
    }

    private static void testFinishNullArguments() throws Exception {
        for (int slot = 0; slot < 6; slot++) {
            Fixture fixture = fixture(false);
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            CurrentAssemblyMetadata.CaptureCohort association = prepared(fixture, meter);
            Object raw = associationField(association, "cohort");
            long[] beforeDebit = debit(meter);
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = finishNullArgument(slot, meter, association, fixture);
            assertFailure(attempt, IOException.class, "METADATA_ARGUMENTS",
                    "FINISH_NULL_ARGUMENT_" + slot, before);
            check(associationOpen(association, meter, fixture.capture, raw),
                    "FINISH_NULL_ARGUMENT_" + slot + "_STATE_OPEN");
            check(debitDelta(beforeDebit, debit(meter), 0L, 0L, 0L) &&
                    fixture.capture.getCount == 1 && fixture.data.getCount == 1,
                    "FINISH_NULL_ARGUMENT_" + slot + "_NO_FEE_OR_READ");
            cases++;
        }
    }

    private static void testCaptureNetIdsRejections() throws Exception {
        for (int kind = 0; kind < 3; kind++) {
            Fixture fixture = fixture(true);
            if (kind == 0) fixture.capture.remove("netIds");
            else if (kind == 1) fixture.capture.put("netIds", null);
            else fixture.capture.put("netIds", "not-a-list");
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            long[] beforeDebit = debit(meter);
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = prepare(meter, fixture.setup, fixture.capture);
            assertFailure(attempt, IOException.class, "METADATA_CAPTURE_NET_IDS",
                    "CAPTURE_NET_IDS_KIND_" + kind, before);
            check(fixture.capture.getCount == 1 && fixture.capture.netIdsGets == 1 &&
                    fixture.data.getCount == 0,
                    "BAD_CAPTURE_PRECEDES_THROWING_SETUP_READ_" + kind);
            check(debitDelta(beforeDebit, debit(meter), 1L, 0L, 0L),
                    "BAD_CAPTURE_VALUE_COSTS_ONE_UNIT_" + kind);
            cases++;
        }
    }

    private static void testInheritedCohortFailures() throws Exception {
        for (int kind = 0; kind < 5; kind++) {
            Fixture fixture = fixture(false);
            String error;
            if (kind == 0) {
                fixture.captureIds.remove(fixture.captureIds.size() - 1);
                error = "COHORT_INPUT";
            } else if (kind == 1) {
                fixture.captureIds.set(59, "n00");
                error = "COHORT_VECTOR_DUPLICATE";
            } else if (kind == 2) {
                fixture.captureIds.set(43, "n60");
                error = "COHORT_NOT_IN_CAPTURE";
            } else if (kind == 3) {
                fixture.priorRequested.set(2, "n60");
                error = "COHORT_REQUESTED_VECTOR";
            } else {
                fixture.disposition.put("feedbackValidated", Boolean.FALSE);
                error = "COHORT_DISPOSITION";
            }
            CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
            InputSnapshot before = new InputSnapshot(fixture);
            Attempt attempt = prepare(meter, fixture.setup, fixture.capture);
            assertFailure(attempt, IOException.class, error,
                    "INHERITED_COHORT_FAILURE_" + kind, before);
            check(fixture.capture.getCount == 1 && fixture.data.getCount == 1,
                    "COHORT_ERROR_AFTER_CAPTURE_AND_PRIOR_READ_" + kind);
            cases++;
        }
    }

    private static void testThrowingCaptureGet() throws Exception {
        Fixture fixture = fixture(false);
        RuntimeException sentinel = new IllegalStateException("SYNTHETIC_NETIDS_GET");
        fixture.capture.getFailure = sentinel;
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        long[] beforeDebit = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = prepare(meter, fixture.setup, fixture.capture);
        check(attempt.value == null && attempt.failure == sentinel,
                "CAPTURE_MAP_GET_EXCEPTION_PROPAGATES_BY_IDENTITY");
        check(before.unchanged(), "THROWING_CAPTURE_INPUTS_UNCHANGED");
        check(fixture.capture.getCount == 1 && fixture.capture.netIdsGets == 1 &&
                fixture.data.getCount == 0, "THROWING_CAPTURE_GET_PRECEDES_SETUP_READ");
        check(debitDelta(beforeDebit, debit(meter), 1L, 0L, 0L),
                "THROWING_CAPTURE_GET_COSTS_ONE_UNIT");
        cases++;
    }

    private static void testPrepareClosedGroupGuard() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        closeOneUnitGroups(meter, 640);
        check(number(meter, "units") == 640L && number(meter, "groups") == 640L &&
                number(meter, "maxGroupUnits") == 1L && isClosed(meter) &&
                groupMeasureCount(meter) == 640,
                "PREPARE_GROUP_GUARD_ARMED_AT_640_CLOSED_GROUPS");
        long[] beforeDebit = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = prepare(meter, fixture.setup, fixture.capture);
        assertFailure(attempt, IOException.class,
                "native wrapper exceeded 640 measured groups",
                "PREPARE_CLOSED_GROUP_GUARD", before);
        check(fixture.capture.getCount == 0 && fixture.data.getCount == 0,
                "PREPARE_GROUP_GUARD_PRECEDES_FIRST_READ");
        check(debitDelta(beforeDebit, debit(meter), 0L, 0L, 0L) &&
                number(meter, "groups") == 640L && isClosed(meter),
                "PREPARE_GROUP_GUARD_NO_DEBIT_OR_OPEN_GROUP");
        cases++;
    }

    private static void testWrongMeterBorrow() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter boundMeter = newMeter();
        CurrentAssemblyMetadata.CaptureCohort association = prepared(fixture, boundMeter);
        Object raw = associationField(association, "cohort");
        CohortTerminalTelemetryStudy.SetupMeter requestingMeter = newMeter();
        long[] beforeBound = debit(boundMeter);
        long[] beforeRequester = debit(requestingMeter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = borrow(requestingMeter, association);
        assertFailure(attempt, IOException.class, "METADATA_CAPTURE_METER",
                "WRONG_METER_BORROW", before);
        check(associationOpen(association, boundMeter, fixture.capture, raw),
                "WRONG_METER_LEAVES_ASSOCIATION_OPEN");
        check(debitDelta(beforeBound, debit(boundMeter), 0L, 0L, 0L),
                "BOUND_METER_UNCHANGED_BY_WRONG_REQUESTER");
        check(debitDelta(beforeRequester, debit(requestingMeter), 2L, 0L, 0L),
                "WRONG_METER_REQUESTER_PAYS_TWO_UNITS");
        cases++;
    }

    private static void testCompletedAssociationOneUse() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        CurrentAssemblyMetadata.CaptureCohort association = prepared(fixture, meter);
        Object raw = associationField(association, "cohort");
        long[] beforeFinish = debit(meter);
        Attempt finished = finish(meter, association, fixture.arms);
        check(finished.failure == null && finished.value instanceof Map,
                "FIRST_FINISH_SUCCEEDS");
        check(associationCompletedAndCleared(association),
                "FIRST_FINISH_COMPLETES_AND_CLEARS");
        check(debitDelta(beforeFinish, debit(meter), 31L, 19L, 3L),
                "FIRST_FINISH_PHASE_FEE");

        CohortTerminalTelemetryStudy.SetupMeter requester = newMeter();
        long[] beforeBorrow = debit(requester);
        InputSnapshot beforeBorrowInput = new InputSnapshot(fixture);
        Attempt borrowed = borrow(requester, association);
        assertFailure(borrowed, IOException.class, "METADATA_CAPTURE_COMPLETED",
                "COMPLETED_ASSOCIATION_BORROW", beforeBorrowInput);
        check(debitDelta(beforeBorrow, debit(requester), 1L, 0L, 0L) &&
                associationCompletedAndCleared(association),
                "COMPLETED_REJECTION_PRECEDES_DIFFERENT_METER_CHECK");

        long[] beforeSecondFinish = debit(requester);
        InputSnapshot beforeSecondInput = new InputSnapshot(fixture);
        Attempt secondFinish = finish(requester, association, fixture.arms);
        assertFailure(secondFinish, IOException.class, "METADATA_CAPTURE_COMPLETED",
                "SECOND_FINISH_REJECTED", beforeSecondInput);
        check(debitDelta(beforeSecondFinish, debit(requester), 1L, 0L, 0L) &&
                associationCompletedAndCleared(association),
                "SECOND_FINISH_CANNOT_REVIVE_OR_RETAIN_ALIASES");
        check(raw != null && fixture.capture.getCount == 1 && fixture.data.getCount == 1,
                "COMPLETED_EXTERNAL_RAW_ALIAS_IS_ONLY_CALLER_BORROW");
        cases++;
    }

    private static void testFreshAssociationAfterCompletion() throws Exception {
        Fixture firstFixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter firstMeter = newMeter();
        CurrentAssemblyMetadata.CaptureCohort completed = prepared(firstFixture, firstMeter);
        Attempt finished = finish(firstMeter, completed, firstFixture.arms);
        check(finished.failure == null && finished.value instanceof Map &&
                associationCompletedAndCleared(completed),
                "OLD_ASSOCIATION_COMPLETED_BEFORE_FRESH_START");

        Fixture freshFixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter freshMeter = newMeter();
        long[] beforeFreshPrepare = debit(freshMeter);
        CurrentAssemblyMetadata.CaptureCohort fresh = prepared(freshFixture, freshMeter);
        Object freshRaw = associationField(fresh, "cohort");
        check(fresh != completed && freshFixture.data != firstFixture.data &&
                freshFixture.capture != firstFixture.capture &&
                associationOpen(fresh, freshMeter, freshFixture.capture, freshRaw) &&
                associationCompletedAndCleared(completed),
                "FRESH_FIXTURE_CREATES_NEW_OPEN_ASSOCIATION");
        check(debitDelta(beforeFreshPrepare, debit(freshMeter), 265L, 127L, 64L),
                "FRESH_ASSOCIATION_CHARGES_NORMAL_PREPARE_FEE");
        check(new InputSnapshot(firstFixture).unchanged() &&
                new InputSnapshot(freshFixture).unchanged(),
                "FRESH_ASSOCIATION_DOES_NOT_REWRITE_EITHER_INPUT");
        cases++;
    }

    private static void testLegacyCohortControl() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        long[] beforeDebit = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = legacyCohort(meter, fixture.setup, fixture.captureIds);
        check(attempt.failure == null && attempt.value instanceof Map,
                "LEGACY_COHORT_PROJECTION_STILL_SUCCEEDS");
        Map<String, Object> cohort = castMap(attempt.value);
        List<?> pair = (List<?>) cohort.get("netIds");
        List<?> requested = (List<?>) cohort.get("requestedNetIds");
        check(Arrays.asList("n43", "n48").equals(pair) &&
                Arrays.asList(EXPECTED_REQUESTED).equals(requested),
                "LEGACY_CONTROL_LITERAL_VECTOR_MATCHES");
        check(pair != fixture.captureIds && requested != fixture.captureIds &&
                pair != fixture.priorPair && requested != fixture.priorRequested,
                "LEGACY_CONTROL_LISTS_REMAIN_DETACHED");
        check(debitDelta(beforeDebit, debit(meter), 264L, 125L, 64L) &&
                fixture.data.getCount == 1 && fixture.capture.getCount == 0,
                "LEGACY_COHORT_CONTROL_FEE_AND_NO_CAPTURE_MAP_READ");
        check(before.unchanged(), "LEGACY_COHORT_INPUTS_UNCHANGED");
        cases++;
    }

    private static void testPrepareAllocationAdmissionFailure() throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        closeOneUnitGroups(meter, 639);
        meter.charge(32504);
        check(number(meter, "units") == 33143L &&
                number(meter, "groupUnits") == 32504L &&
                number(meter, "maxGroupUnits") == 1L &&
                number(meter, "groups") == 639L && !isClosed(meter),
                "PREPARE_ALLOCATION_GATE_ARMED");
        long[] beforePrepare = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = prepare(meter, fixture.setup, fixture.capture);
        assertFailure(attempt, IOException.class,
                "native wrapper exceeded 640 measured groups",
                "PREPARE_ALLOCATION_ADMISSION_FAILURE", before);
        check(fixture.capture.getCount == 1 && fixture.data.getCount == 1,
                "ALLOCATION_GATE_FAILS_AFTER_CAPTURE_AND_SETUP_READS");
        check(debitDelta(beforePrepare, debit(meter), 264L, 127L, 64L) &&
                number(meter, "groups") == 640L &&
                number(meter, "maxGroupUnits") == 32768L && isClosed(meter) &&
                groupMeasureCount(meter) == 640,
                "PREPARE_FAILURE_RETAINS_PARTIAL_264_127_64_AND_CLOSED_640");
        cases++;
    }

    private static void testFinishAdmissionFailurePreservesOpenAssociation()
            throws Exception {
        Fixture fixture = fixture(false);
        CohortTerminalTelemetryStudy.SetupMeter meter = newMeter();
        CurrentAssemblyMetadata.CaptureCohort association = prepared(fixture, meter);
        Object raw = associationField(association, "cohort");
        meter.method("finishGroup", new Class<?>[0]);
        closeOneUnitGroups(meter, 638);
        meter.charge(32741);
        check(number(meter, "units") == 33644L &&
                number(meter, "groupUnits") == 32741L,
                "FINISH_CLEAR_GATE_ARMED");
        long[] beforeFinish = debit(meter);
        InputSnapshot before = new InputSnapshot(fixture);
        Attempt attempt = finish(meter, association, fixture.arms);
        assertFailure(attempt, IOException.class,
                "native wrapper exceeded 640 measured groups",
                "FINISH_CLEAR_ADMISSION_FAILURE", before);
        check(associationOpen(association, meter, fixture.capture, raw),
                "FAILED_FINISH_PRESERVES_OPEN_ASSOCIATION_REFERENCES");
        check(debitDelta(beforeFinish, debit(meter), 27L, 19L, 3L) &&
                number(meter, "units") == 33671L && number(meter, "groups") == 640L &&
                number(meter, "maxGroupUnits") == 32768L && isClosed(meter) &&
                groupMeasureCount(meter) == 640 && fixture.capture.getCount == 1 &&
                fixture.data.getCount == 1,
                "FAILED_FINISH_PARTIAL_27_19_3_CLOSED_GROUP_NO_REPLACEMENT_READS");
        cases++;
    }

    private static CurrentAssemblyMetadata.CaptureCohort prepared(
            Fixture fixture, CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        Attempt attempt = prepare(meter, fixture.setup, fixture.capture);
        check(attempt.failure == null &&
                attempt.value instanceof CurrentAssemblyMetadata.CaptureCohort,
                "PREPARE_HELPER_SUCCEEDED");
        return (CurrentAssemblyMetadata.CaptureCohort) attempt.value;
    }

    private static Fixture fixture(boolean failSetupRead) throws Exception {
        List<String> captured = capturedIds();
        List<String> decoy = new ArrayList<String>();
        for (int index = 59; index >= 0; index--) decoy.add(id(index));
        List<String> pair = new ArrayList<String>();
        pair.add("n43");
        pair.add("n48");
        List<String> requested = new ArrayList<String>(Arrays.asList(EXPECTED_REQUESTED));

        Map<String, Object> disposition = new LinkedHashMap<String, Object>();
        disposition.put("status", "RUN");
        disposition.put("reason", "TRIAL_EXECUTED");
        disposition.put("capturedFailedNetId", "n43");
        disposition.put("controlFailedNetId", "n48");
        disposition.put("cohortNetIds", pair);
        disposition.put("requestedNetIds", requested);
        disposition.put("orderChanged", Boolean.TRUE);
        disposition.put("feedbackValidated", Boolean.TRUE);
        Map<String, Object> prior = new LinkedHashMap<String, Object>();
        prior.put("trialDisposition", disposition);
        TrackingMap data = new TrackingMap();
        data.put("prior", prior);
        data.failAnyGet = failSetupRead;
        CohortTerminalTelemetryStudy.SetupResult setup =
                new CohortTerminalTelemetryStudy.SetupResult(data, 0L, 0L, 0, 0);

        TrackingMap capture = new TrackingMap();
        capture.put("netIds", captured);
        capture.put("decoyNetIds", decoy);
        Map<String, Object> arms = new LinkedHashMap<String, Object>();
        arms.put("synthetic-arm-map", new LinkedHashMap<String, Object>());
        return new Fixture(data, setup, capture, captured, decoy, arms,
                pair, requested, disposition);
    }

    private static List<String> capturedIds() {
        List<String> ids = new ArrayList<String>();
        for (int index = 0; index < 60; index++) ids.add(id(index));
        return ids;
    }

    private static String id(int index) {
        return "n" + (index < 10 ? "0" : "") + Integer.toString(index);
    }

    private static void closeOneUnitGroups(
            CohortTerminalTelemetryStudy.SetupMeter meter, int count) throws Exception {
        for (int index = 0; index < count; index++) {
            meter.charge(1);
            meter.method("finishGroup", new Class<?>[0]);
        }
    }

    private static Attempt prepareNullArgument(int slot,
            CohortTerminalTelemetryStudy.SetupMeter meter, Fixture fixture) {
        if (slot == 0) return prepare(null, fixture.setup, fixture.capture);
        if (slot == 1) return prepare(meter, null, fixture.capture);
        return prepare(meter, fixture.setup, null);
    }

    private static Attempt finishNullArgument(int slot,
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CurrentAssemblyMetadata.CaptureCohort association, Fixture fixture) {
        if (slot == 0) return finish(null, association, fixture.arms);
        if (slot == 1) return finish(meter, null, fixture.arms);
        if (slot == 2) return finish(meter, association, fixture.arms, null, SOURCE_SHA, CONTEXT_SHA);
        if (slot == 3) return finish(meter, association, fixture.arms, RUN_ID, null, CONTEXT_SHA);
        if (slot == 4) return finish(meter, association, fixture.arms, RUN_ID, SOURCE_SHA, null);
        return finish(meter, association, null);
    }

    private static Attempt prepare(CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup, Map<String, Object> capture) {
        try {
            return new Attempt(CurrentAssemblyMetadata.prepareCaptureCohort(
                    meter, setup, capture), null);
        } catch (Throwable caught) {
            return new Attempt(null, unwrap(caught));
        }
    }

    private static Attempt borrow(CohortTerminalTelemetryStudy.SetupMeter meter,
            CurrentAssemblyMetadata.CaptureCohort association) {
        try {
            return new Attempt(CurrentAssemblyMetadata.captureCohortForArms(
                    meter, association), null);
        } catch (Throwable caught) {
            return new Attempt(null, unwrap(caught));
        }
    }

    private static Attempt finish(CohortTerminalTelemetryStudy.SetupMeter meter,
            CurrentAssemblyMetadata.CaptureCohort association, Map<String, Object> arms) {
        return finish(meter, association, arms, RUN_ID, SOURCE_SHA, CONTEXT_SHA);
    }

    private static Attempt finish(CohortTerminalTelemetryStudy.SetupMeter meter,
            CurrentAssemblyMetadata.CaptureCohort association, Map<String, Object> arms,
            String runId, String sourceSha, String contextSha) {
        try {
            return new Attempt(CurrentAssemblyMetadata.finishCaptureBody(meter,
                    association, runId, sourceSha, contextSha, arms), null);
        } catch (Throwable caught) {
            return new Attempt(null, unwrap(caught));
        }
    }

    private static Attempt legacyCohort(CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup, List<String> captureIds) {
        try {
            return new Attempt(CurrentAssemblyMetadata.cohortBeforeBody(
                    meter, setup, captureIds), null);
        } catch (Throwable caught) {
            return new Attempt(null, unwrap(caught));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof InvocationTargetException && failure.getCause() != null)
            failure = failure.getCause();
        return failure;
    }

    private static void assertFailure(Attempt attempt, Class<?> type,
            String message, String label, InputSnapshot before) {
        check(attempt.value == null && attempt.failure != null &&
                attempt.failure.getClass() == type &&
                message.equals(attempt.failure.getMessage()), label + "_EXACT_ERROR_NO_RESULT");
        check(before.unchanged(), label + "_INPUTS_UNCHANGED");
    }

    private static Object associationField(
            CurrentAssemblyMetadata.CaptureCohort association, String name) throws Exception {
        return CohortTerminalTelemetryStudy.field(association, name);
    }

    private static boolean associationOpen(
            CurrentAssemblyMetadata.CaptureCohort association,
            CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<String, Object> capture, Object cohort) throws Exception {
        return association != null && associationField(association, "meter") == meter &&
                associationField(association, "capture") == capture &&
                associationField(association, "cohort") == cohort &&
                Boolean.FALSE.equals(associationField(association, "completed"));
    }

    private static boolean associationCompletedAndCleared(
            CurrentAssemblyMetadata.CaptureCohort association) throws Exception {
        return association != null && associationField(association, "meter") == null &&
                associationField(association, "capture") == null &&
                associationField(association, "cohort") == null &&
                Boolean.TRUE.equals(associationField(association, "completed"));
    }

    private static boolean privateHolderShape() {
        Class<?> type = CurrentAssemblyMetadata.CaptureCohort.class;
        if (!Modifier.isStatic(type.getModifiers()) || !Modifier.isFinal(type.getModifiers()) ||
                Modifier.isPublic(type.getModifiers()) || Modifier.isPrivate(type.getModifiers()) ||
                Modifier.isProtected(type.getModifiers()))
            return false;
        Field[] fields = type.getDeclaredFields();
        if (fields.length != 4) return false;
        Set<String> names = new HashSet<String>();
        for (Field field : fields) {
            if (!Modifier.isPrivate(field.getModifiers()) ||
                    Modifier.isStatic(field.getModifiers()) ||
                    Modifier.isFinal(field.getModifiers())) return false;
            names.add(field.getName());
        }
        try {
            if (type.getDeclaredField("meter").getType() !=
                    CohortTerminalTelemetryStudy.SetupMeter.class ||
                    type.getDeclaredField("capture").getType() != Map.class ||
                    type.getDeclaredField("completed").getType() != Boolean.TYPE ||
                    !"Cohort".equals(type.getDeclaredField("cohort").getType().getSimpleName()))
                return false;
        } catch (NoSuchFieldException absent) {
            return false;
        }
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        int sourceConstructors = 0;
        for (Constructor<?> constructor : constructors) {
            // JDK8/target7 emits an additional synthetic access bridge.
            if (constructor.isSynthetic()) continue;
            sourceConstructors++;
            if (!Modifier.isPrivate(constructor.getModifiers())) return false;
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length != 3 ||
                    parameters[0] != CohortTerminalTelemetryStudy.SetupMeter.class ||
                    parameters[1] != Map.class ||
                    !"Cohort".equals(parameters[2].getSimpleName())) return false;
        }
        return names.contains("meter") && names.contains("capture") &&
                names.contains("cohort") && names.contains("completed") &&
                sourceConstructors == 1;
    }

    private static CohortTerminalTelemetryStudy.SetupMeter newMeter() throws Exception {
        CohortTerminalTelemetryStudy.Parent parent =
                new CohortTerminalTelemetryStudy.Parent();
        return new CohortTerminalTelemetryStudy.SetupMeter(parent,
                parent.meter(System.nanoTime()));
    }

    private static long[] debit(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        return new long[] {number(meter, "units"), number(meter, "copiedEntries"),
                number(meter, "copyChunks")};
    }

    private static long number(CohortTerminalTelemetryStudy.SetupMeter meter,
            String name) throws Exception {
        return ((Number) CohortTerminalTelemetryStudy.field(meter.meter, name)).longValue();
    }

    private static boolean debitDelta(long[] before, long[] after,
            long units, long copies, long chunks) {
        return after[0] - before[0] == units && after[1] - before[1] == copies &&
                after[2] - before[2] == chunks;
    }

    private static boolean isClosed(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        return Boolean.FALSE.equals(CohortTerminalTelemetryStudy.field(meter.meter, "open"));
    }

    private static int groupMeasureCount(CohortTerminalTelemetryStudy.SetupMeter meter)
            throws Exception {
        Object measures = CohortTerminalTelemetryStudy.field(meter.meter, "groupMeasures");
        return measures instanceof List ? ((List<?>) measures).size() : -1;
    }

    private static Map<String, Object> child(Map<?, ?> values, String key) {
        return castMap(values.get(key));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static final class Fixture {
        final TrackingMap data;
        final CohortTerminalTelemetryStudy.SetupResult setup;
        final TrackingMap capture;
        final List<String> captureIds;
        final List<String> decoyIds;
        final Map<String, Object> arms;
        final List<String> priorPair;
        final List<String> priorRequested;
        final Map<String, Object> disposition;

        Fixture(TrackingMap data, CohortTerminalTelemetryStudy.SetupResult setup,
                TrackingMap capture, List<String> captureIds, List<String> decoyIds,
                Map<String, Object> arms, List<String> priorPair,
                List<String> priorRequested, Map<String, Object> disposition) {
            this.data = data;
            this.setup = setup;
            this.capture = capture;
            this.captureIds = captureIds;
            this.decoyIds = decoyIds;
            this.arms = arms;
            this.priorPair = priorPair;
            this.priorRequested = priorRequested;
            this.disposition = disposition;
        }
    }

    private static final class TrackingMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
        int getCount;
        int netIdsGets;
        boolean failAnyGet;
        RuntimeException getFailure;

        public Object get(Object key) {
            getCount++;
            if ("netIds".equals(key)) netIdsGets++;
            if (getFailure != null) throw getFailure;
            if (failAnyGet) throw new IllegalStateException("SETUP_GET_BEFORE_CAPTURE_GUARD");
            return super.get(key);
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

    private static final class InputSnapshot {
        private final TreeSnapshot data;
        private final TreeSnapshot capture;
        private final TreeSnapshot arms;

        InputSnapshot(Fixture fixture) {
            data = new TreeSnapshot(fixture.data);
            capture = new TreeSnapshot(fixture.capture);
            arms = new TreeSnapshot(fixture.arms);
        }

        boolean unchanged() {
            return data.unchanged(data.root) && capture.unchanged(capture.root) &&
                    arms.unchanged(arms.root);
        }
    }

    private static final class TreeSnapshot {
        final Object root;
        private final IdentityHashMap<Object, List<Object[]>> contents =
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
                    entries.add(new Object[]{key, child});
                    capture(child);
                }
            } else if (value instanceof List) {
                if (contents.containsKey(value)) return;
                List<Object[]> entries = new ArrayList<Object[]>();
                contents.put(value, entries);
                for (Object child : (List<?>) value) {
                    entries.add(new Object[]{child});
                    capture(child);
                }
            }
        }

        boolean unchanged(Object currentRoot) { return matches(currentRoot); }

        private boolean matches(Object value) {
            List<Object[]> expected = contents.get(value);
            if (expected == null) return true;
            if (value instanceof Map) {
                Iterator<? extends Map.Entry<?, ?>> actual =
                        ((Map<?, ?>) value).entrySet().iterator();
                if (((Map<?, ?>) value).size() != expected.size()) return false;
                for (int index = 0; index < expected.size(); index++) {
                    if (!actual.hasNext()) return false;
                    Map.Entry<?, ?> entry = actual.next();
                    Object[] pair = expected.get(index);
                    if (entry.getKey() != pair[0] || entry.getValue() != pair[1] ||
                            !matches(pair[1])) return false;
                }
                return !actual.hasNext();
            }
            if (value instanceof List) {
                List<?> actual = (List<?>) value;
                if (actual.size() != expected.size()) return false;
                Iterator<?> iterator = actual.iterator();
                for (int index = 0; index < expected.size(); index++) {
                    if (!iterator.hasNext()) return false;
                    Object child = expected.get(index)[0];
                    if (iterator.next() != child || !matches(child)) return false;
                }
                return !iterator.hasNext();
            }
            return false;
        }
    }
}
