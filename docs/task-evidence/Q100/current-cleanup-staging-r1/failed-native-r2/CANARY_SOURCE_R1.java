package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Native-only synthetic cleanup staging canary. No owner, graph, or producer. */
public final class CleanupStagingCanary {
    private static final String RUN = "00000000-0000-0000-0000-000000000001";
    private static final String SOURCE = "2222222222222222222222222222222222222222222222222222222222222222";
    private static final String CONTEXT = "3333333333333333333333333333333333333333333333333333333333333333";
    private static final String GEOMETRY = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String EFFECTS = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SCOPE = "SYNTHETIC_COMPONENT_ONLY";
    private static final String WIRE = "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed";
    private static final String SCHEMA = "TSJ_CLEANUP_STAGING_CANARY_R1";
    private static final String[] SMOKE = {"pending_constructor", "installed_true"};
    private static final String[] SUITE = {"null_alias_false", "observe_alias_false",
        "geometry_false", "scalar_below_sum", "scalar_at_limit", "negative_scalar",
        "over_limit_scalar", "body_type", "slot_missing", "slot_occupied",
        "child_view_missing", "projection_read_error", "projection_copy_guard",
        "replacement_copy_guard", "group_guard_before_read", "write_group_guard",
        "null_arguments", "pending_export_reject", "r7_handoff_true",
        "r7_handoff_below_sum", "r7_handoff_alias_false"};
    private static final String[] ROOT_KEYS = {"schema", "mode", "status", "runId",
        "sourceManifestSha256", "wireContractSha256", "inputContextSha256", "qualifiedPrior",
        "originalCapture", "cohort", "arms", "work", "cleanup"};
    private static final String[] WORK_KEYS = {"elapsedCutoffNanos", "cleanupNanos",
        "groupsBeforeExport", "wrapperGroupsBeforeExport", "maxGroupNanosBeforeExport",
        "maximumUnitsPerGroupBeforeExport", "unitsBeforeExport", "setupUnits", "setupGroups",
        "setupCopiedEntries", "setupCopyChunks", "originalCaptureOwnerUnits",
        "originalCaptureWrapperUnits", "originalCaptureCallbackUnits",
        "originalCaptureDiagnosticUnits", "originalCaptureGroups", "originalCaptureCopiedEntries",
        "originalCaptureCopyChunks", "nullOwnerUnits", "nullWrapperUnits", "nullObserverUnits",
        "observeOwnerUnits", "observeWrapperUnits", "observeObserverUnits", "observerUnits",
        "meterUnits", "metadataUnits", "metadataGroups", "metadataCopiedEntries",
        "metadataCopyChunks", "copiedEntries", "copyChunks", "copyChunkLimit", "copiedEntryLimit"};
    private static final String[] CLEANUP_KEYS = {"schema", "originalCaptureOwnersReleased",
        "originalCaptureContextRestored", "nullArmAliasesDropped", "observeArmAliasesDropped",
        "sourceGeometryUnchanged", "effectsScope", "originalCaptureCopyEffectsUnchanged",
        "contextRestored", "cleanupNanos"};
    private static final String[] ALIAS_KEYS = {"adapterSessionAliasDropped",
        "adapterSearchAliasDropped", "adapterCandidateAliasDropped",
        "adapterRouteResultAliasDropped", "adapterBoardTemplateAliasesDropped"};
    private static final String[] CASE_KEYS = {"name", "status", "phase", "reason",
        "observedError", "units", "copiedEntries", "copyChunks", "groups", "priorGroups",
        "groupUnits", "maxGroupUnits", "maxGroupNanos", "meterOpen", "helperUnits",
        "helperCopies", "helperChunks", "operationNanos", "totalElapsedNanos",
        "openGroupElapsedNanos", "bodyUnchanged", "inputsUnchanged", "cleanupInstalled",
        "observedNestedGets", "semanticDurationGets", "discarded", "finishAfterFailure",
        "payloadJson", "canonicalBytes"};
    private static final int JSON_LIMIT = 1048576;
    private static int parentConstructions;

    private CleanupStagingCanary() { }

    public static void main(String[] args) {
        parentConstructions = 0;
        String mode = args != null && args.length == 1 &&
                ("SMOKE".equals(args[0]) || "SUITE".equals(args[0])) ? args[0] : "INVALID";
        List<String> names = new ArrayList<String>();
        List<Map<String,Object>> cases = new ArrayList<Map<String,Object>>();
        String error = "";
        boolean pass = !"INVALID".equals(mode);
        try {
            if (pass) {
                names.addAll(Arrays.asList("SMOKE".equals(mode) ? SMOKE : SUITE));
                for (String name : names) {
                    Map<String,Object> result = runCase(name);
                    cases.add(result);
                    if (!"PASS".equals(result.get("status"))) pass = false;
                }
                if (!pass) error = "CASE_FAILURES";
            } else {
                error = "CANARY_ARGUMENTS";
            }
        } catch (Throwable failure) {
            pass = false;
            error = describe(failure);
        }
        Map<String,Object> output = new LinkedHashMap<String,Object>();
        output.put("schema", SCHEMA);
        output.put("mode", mode);
        output.put("status", pass ? "PASS" : "FAIL");
        output.put("names", names);
        output.put("cases", cases);
        output.put("parentConstructions", Integer.valueOf(parentConstructions));
        output.put("error", error);
        String encoded;
        try {
            encoded = json(output);
        } catch (Throwable serializationFailure) {
            pass = false;
            encoded = "{\"schema\":\"TSJ_CLEANUP_STAGING_CANARY_R1\",\"mode\":\"" +
                    mode + "\",\"status\":\"FAIL\",\"names\":[],\"cases\":[]," +
                    "\"parentConstructions\":" + parentConstructions +
                    ",\"error\":\"CANARY_REPORT_SERIALIZATION\"}";
        }
        System.out.println(encoded);
        if (!pass) System.exit(1);
    }

    private static final class State {
        final String name;
        final long totalStarted;
        final Map<String,Object> report;
        Fixture fixture;
        InputsImage inputsImage;
        Map<String,Object> body;
        MapImage bodyBefore;
        CohortTerminalTelemetryStudy.Parent parent;
        CohortTerminalTelemetryStudy.SetupMeter meter;
        long started;
        boolean metricsCaptured;
        String phase = "fixture";

        State(String name, long totalStarted, Map<String,Object> report) {
            this.name = name;
            this.totalStarted = totalStarted;
            this.report = report;
        }
    }

    private static Map<String,Object> runCase(String name) {
        long totalStarted = System.nanoTime();
        Map<String,Object> report = caseRecord(name);
        State state = new State(name, totalStarted, report);
        try {
            state.fixture = fixture(name);
            state.inputsImage = new InputsImage(state.fixture);
            prepareBodyFixture(state);
            parentConstructions++;
            state.parent = new CohortTerminalTelemetryStudy.Parent();
            state.started = System.nanoTime();
            state.meter = new CohortTerminalTelemetryStudy.SetupMeter(state.parent,
                    state.parent.meter(state.started));
            state.meter.method("check", new Class<?>[0]);
            execute(state);
        } catch (Throwable failure) {
            report.put("status", "FAIL");
            report.put("phase", state.phase);
            report.put("reason", "UNEXPECTED_FAILURE");
            report.put("observedError", describe(failure));
        } finally {
            if (state.meter != null && !state.metricsCaptured) {
                try {
                    captureMetrics(state, MeterCounters.read(state.meter), System.nanoTime(), 0L);
                } catch (Throwable failure) {
                    report.put("status", "FAIL");
                    report.put("phase", "snapshot");
                    report.put("reason", "SNAPSHOT_FAILED");
                    report.put("observedError", describe(failure));
                }
            }
            long elapsed = System.nanoTime() - state.totalStarted;
            report.put("totalElapsedNanos", Long.valueOf(elapsed));
            if (elapsed < 0L) {
                report.put("status", "FAIL");
                report.put("reason", "CLOCK_NEGATIVE");
                report.put("observedError", "AssertionError:CLOCK_NEGATIVE");
            }
            if (state.inputsImage != null && !Boolean.TRUE.equals(report.get("inputsUnchanged"))) {
                try {
                    report.put("inputsUnchanged", Boolean.valueOf(state.inputsImage.unchanged()));
                } catch (Throwable ignored) {
                    report.put("inputsUnchanged", Boolean.FALSE);
                }
            }
        }
        return report;
    }

    private static void prepareBodyFixture(State state) {
        String name = state.name;
        if ("pending_constructor".equals(name) || name.startsWith("r7_handoff_") ||
                "null_arguments".equals(name)) return;
        Map<String,Object> body = "body_type".equals(name)
                ? new HashMap<String,Object>() : new LinkedHashMap<String,Object>();
        putRoot(body, state.fixture, null, null, null);
        if ("slot_missing".equals(name)) body.remove("cleanup");
        if ("slot_occupied".equals(name)) {
            Map<String,Object> occupied = new LinkedHashMap<String,Object>();
            occupied.put("fixtureScope", SCOPE);
            body.put("cleanup", occupied);
        }
        if ("pending_export_reject".equals(name)) {
            body.put("qualifiedPrior", state.fixture.prior);
            body.put("work", new LinkedHashMap<String,Object>());
        }
        state.body = body;
        state.bodyBefore = new MapImage(body);
    }

    private static void execute(State state) throws Exception {
        String name = state.name;
        if ("pending_constructor".equals(name)) runConstructor(state);
        else if ("installed_true".equals(name)) runInstallSuccess(state, 123L);
        else if ("null_alias_false".equals(name) || "observe_alias_false".equals(name) ||
                "geometry_false".equals(name)) runInstallSuccess(state, 123L);
        else if ("scalar_below_sum".equals(name)) runInstallSuccess(state, 5L);
        else if ("scalar_at_limit".equals(name)) runInstallSuccess(state, 120000000000L);
        else if ("negative_scalar".equals(name)) runInstallRejected(state, -1L,
                "METADATA_CLEANUP_SCALAR", false);
        else if ("over_limit_scalar".equals(name)) runInstallRejected(state, 120000000001L,
                "METADATA_CLEANUP_SCALAR", false);
        else if ("body_type".equals(name)) runInstallRejected(state, 123L,
                "METADATA_CLEANUP_BODY", false);
        else if ("slot_missing".equals(name)) runInstallRejected(state, 123L,
                "METADATA_CLEANUP_SLOT", false);
        else if ("slot_occupied".equals(name)) runInstallRejected(state, 123L,
                "METADATA_CLEANUP_SLOT", false);
        else if ("child_view_missing".equals(name)) runInstallRejected(state, 123L,
                "METADATA_CLEANUP_VIEW", false);
        else if ("projection_read_error".equals(name)) runInstallRejected(state, 123L,
                "SYNTHETIC_VIEW_READ", true);
        else if ("projection_copy_guard".equals(name)) runInstallRejected(state, 123L,
                "copy exceeds its prebounded chunk or aggregate allowance", true);
        else if ("replacement_copy_guard".equals(name)) runInstallRejected(state, 123L,
                "copy exceeds its prebounded chunk or aggregate allowance", true);
        else if ("group_guard_before_read".equals(name)) runGroupGuardBeforeRead(state);
        else if ("write_group_guard".equals(name)) runWriteGroupGuard(state);
        else if ("null_arguments".equals(name)) runNullArguments(state);
        else if ("pending_export_reject".equals(name)) runPendingExportReject(state);
        else if (name.startsWith("r7_handoff_")) runHandoff(state);
        else throw new IOException("CANARY_CASE_UNKNOWN");
    }

    private static void runConstructor(State state) throws Exception {
        state.phase = "pending_constructor";
        MeterCounters before = MeterCounters.read(state.meter);
        long operationStarted = System.nanoTime();
        Throwable failure = null;
        try {
            state.body = CurrentAssemblyMetadata.pendingCleanupBody(state.meter, RUN, SOURCE,
                    CONTEXT, state.fixture.capture, state.fixture.cohort, state.fixture.arms);
        } catch (Throwable caught) {
            failure = caught;
        }
        MeterCounters helperAfter = MeterCounters.read(state.meter);
        if (failure == null) {
            state.meter.finish();
        }
        failure = checkAfterOperation(state, failure, "METADATA_ARGUMENTS");
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        helperDelta(state.report, before, helperAfter);
        if (failure != null) {
            recordUnexpectedPositiveFailure(state, failure);
            return;
        }
        require(helperAfter.units - before.units == 14L &&
                helperAfter.copiedEntries - before.copiedEntries == 13L &&
                helperAfter.copyChunks - before.copyChunks == 1L, "PENDING_CONSTRUCTOR_DEBIT");
        require(after.groups == 1L && !after.open, "PENDING_CONSTRUCTOR_CALLER_FINISH");
        checkHeaders(state.body, state.fixture, null, null, null);
        require(state.bodyBefore == null || !state.bodyBefore.unchanged(), "CONSTRUCTOR_BODY_CREATED");
        requireInputsUnchanged(state);
        state.report.put("bodyUnchanged", Boolean.FALSE);
        state.report.put("cleanupInstalled", Boolean.FALSE);
        state.report.put("discarded", Boolean.FALSE);
        state.report.put("payloadJson", json(state.body));
        state.report.put("phase", "pending_constructor");
    }

    private static void runInstallSuccess(State state, long cleanupNanos) throws Exception {
        state.phase = "cleanup_install";
        state.bodyBefore = new MapImage(state.body);
        MeterCounters before = MeterCounters.read(state.meter);
        long operationStarted = System.nanoTime();
        Throwable failure = null;
        try {
            CurrentAssemblyMetadata.installCleanup(state.meter, state.body, cleanupNanos);
        } catch (Throwable caught) {
            failure = caught;
        }
        MeterCounters helperAfter = MeterCounters.read(state.meter);
        if (failure == null) state.meter.finish();
        failure = checkAfterOperation(state, failure, null);
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        helperDelta(state.report, before, helperAfter);
        if (failure != null) {
            recordUnexpectedPositiveFailure(state, failure);
            return;
        }
        require(helperAfter.units - before.units == 67L &&
                helperAfter.copiedEntries - before.copiedEntries == 11L &&
                helperAfter.copyChunks - before.copyChunks == 2L, "INSTALLER_DEBIT");
        require(after.groups == 1L && !after.open, "INSTALLER_CALLER_FINISH");
        Map<String,Object> cleanup = cleanupFrom(state.body);
        Map<String,Object> expected = expectedCleanup(state.name, cleanupNanos);
        checkCleanup(cleanup, expected);
        require(!state.bodyBefore.unchanged(), "CLEANUP_BODY_REPLACED");
        requireNoInputAliases(state, cleanup);
        requireInputsUnchanged(state);
        state.report.put("bodyUnchanged", Boolean.FALSE);
        state.report.put("cleanupInstalled", Boolean.TRUE);
        state.report.put("discarded", Boolean.FALSE);
        state.report.put("payloadJson", json(cleanup));
        state.report.put("phase", "cleanup_install");
    }

    private static void runInstallRejected(State state, long cleanupNanos,
            String expectedCode, boolean copyGuard) throws Exception {
        state.phase = "cleanup_install";
        state.bodyBefore = new MapImage(state.body);
        if ("projection_copy_guard".equals(state.name))
            state.meter.method("setCopyAllowance", new Class<?>[]{int.class}, Integer.valueOf(9));
        if ("replacement_copy_guard".equals(state.name))
            state.meter.method("setCopyAllowance", new Class<?>[]{int.class}, Integer.valueOf(10));
        MeterCounters before = MeterCounters.read(state.meter);
        long operationStarted = System.nanoTime();
        Throwable failure = null;
        try {
            CurrentAssemblyMetadata.installCleanup(state.meter, state.body, cleanupNanos);
        } catch (Throwable caught) {
            failure = caught;
        }
        failure = checkAfterOperation(state, failure, expectedCode);
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        helperDelta(state.report, before, after);
        recordExpectedRejection(state, failure, expectedCode, true);
        require(state.bodyBefore.unchanged(), "REJECTED_BODY_MUTATED");
        requireInputsUnchanged(state);
        if (copyGuard) require(!Boolean.TRUE.equals(state.report.get("cleanupInstalled")),
                "COPY_GUARD_INSTALLED_CLEANUP");
        state.report.put("bodyUnchanged", Boolean.TRUE);
        state.report.put("cleanupInstalled", Boolean.FALSE);
        state.report.put("discarded", Boolean.TRUE);
        state.report.put("payloadJson", "");
    }

    private static void runGroupGuardBeforeRead(State state) throws Exception {
        state.phase = "guard";
        for (int i = 0; i < 640; i++) {
            state.meter.charge(1);
            state.meter.finish();
        }
        state.bodyBefore = new MapImage(state.body);
        MeterCounters before = MeterCounters.read(state.meter);
        long operationStarted = System.nanoTime();
        Throwable failure = null;
        try {
            CurrentAssemblyMetadata.installCleanup(state.meter, state.body, 123L);
        } catch (Throwable caught) {
            failure = caught;
        }
        failure = checkAfterOperation(state, failure,
                "native wrapper exceeded 640 measured groups");
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        helperDelta(state.report, before, after);
        recordExpectedRejection(state, failure,
                "native wrapper exceeded 640 measured groups", true);
        requireFailureType(failure, "IOException");
        require(after.units == 640L && after.groups == 640L && !after.open &&
                after.groupUnits == 1L && after.maxGroupUnits == 1L &&
                after.nestedGets == 0, "GROUP_GUARD_BEFORE_READ_ORACLE");
        require(state.bodyBefore.unchanged(), "GROUP_GUARD_BODY_MUTATED");
        requireInputsUnchanged(state);
        state.report.put("bodyUnchanged", Boolean.TRUE);
        state.report.put("cleanupInstalled", Boolean.FALSE);
        state.report.put("discarded", Boolean.TRUE);
        state.report.put("payloadJson", "");
    }

    private static void runWriteGroupGuard(State state) throws Exception {
        state.phase = "guard";
        for (int i = 0; i < 639; i++) {
            state.meter.charge(1);
            state.meter.finish();
        }
        state.meter.charge(32702L);
        state.bodyBefore = new MapImage(state.body);
        MeterCounters before = MeterCounters.read(state.meter);
        require(before.units == 33341L && before.groups == 640L && before.open &&
                before.groupUnits == 32702L, "WRITE_GROUP_SETUP_ORACLE");
        long operationStarted = System.nanoTime();
        Throwable failure = null;
        try {
            CurrentAssemblyMetadata.installCleanup(state.meter, state.body, 123L);
        } catch (Throwable caught) {
            failure = caught;
        }
        failure = checkAfterOperation(state, failure,
                "native wrapper exceeded 640 measured groups");
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        helperDelta(state.report, before, after);
        recordExpectedRejection(state, failure,
                "native wrapper exceeded 640 measured groups", true);
        requireFailureType(failure, "IOException");
        require(after.units == 33407L && after.groups == 640L && !after.open &&
                after.groupUnits == 32768L && after.maxGroupUnits == 32768L &&
                after.copiedEntries == 11L &&
                after.copyChunks == 2L && after.nestedGets == 27, "WRITE_GROUP_GUARD_ORACLE");
        require(state.bodyBefore.unchanged(), "WRITE_GROUP_BODY_MUTATED");
        requireInputsUnchanged(state);
        state.report.put("bodyUnchanged", Boolean.TRUE);
        state.report.put("cleanupInstalled", Boolean.FALSE);
        state.report.put("discarded", Boolean.TRUE);
        state.report.put("payloadJson", "");
    }

    private static void runNullArguments(State state) throws Exception {
        state.phase = "guard";
        MeterCounters before = MeterCounters.read(state.meter);
        long operationStarted = System.nanoTime();
        Throwable constructorFailure = null;
        Throwable installerFailure = null;
        try {
            CurrentAssemblyMetadata.pendingCleanupBody(null, null, null, null, null, null, null);
        } catch (Throwable caught) {
            constructorFailure = caught;
        }
        try {
            CurrentAssemblyMetadata.installCleanup(null, null, 0L);
        } catch (Throwable caught) {
            installerFailure = caught;
        }
        constructorFailure = checkAfterOperation(state, constructorFailure, "METADATA_ARGUMENTS");
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        helperDelta(state.report, before, after);
        requireError(constructorFailure, "METADATA_ARGUMENTS");
        requireError(installerFailure, "METADATA_ARGUMENTS");
        require(after.units == 0L && after.copiedEntries == 0L && after.copyChunks == 0L &&
                after.groups == 0L && !after.open, "NULL_ARGUMENTS_ZERO_WORK");
        requireInputsUnchanged(state);
        state.report.put("observedError", describe(constructorFailure));
        state.report.put("reason", "EXPECTED_REJECTION");
        state.report.put("bodyUnchanged", Boolean.TRUE);
        state.report.put("inputsUnchanged", Boolean.TRUE);
        state.report.put("cleanupInstalled", Boolean.FALSE);
        state.report.put("discarded", Boolean.TRUE);
        state.report.put("finishAfterFailure", Boolean.FALSE);
        state.report.put("payloadJson", "");
        state.report.put("phase", "guard");
    }

    private static void runPendingExportReject(State state) throws Exception {
        state.phase = "pending_header";
        state.bodyBefore = new MapImage(state.body);
        MeterCounters before = MeterCounters.read(state.meter);
        long operationStarted = System.nanoTime();
        Throwable failure = null;
        CohortTerminalTelemetryStudy.R5CountSink sink = null;
        try {
            state.meter.charge(1);
            long now = System.nanoTime();
            sink = new CohortTerminalTelemetryStudy.R5CountSink(state.meter, state.started, now);
            CurrentV2CanonicalCodec.writeBody(state.body, sink);
        } catch (Throwable caught) {
            failure = caught;
        }
        failure = checkAfterOperation(state, failure, "BODY_ROOT_CONTAINER");
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        helperDelta(state.report, before, after);
        requireFailureCode(failure, "BODY_ROOT_CONTAINER");
        require(sink != null && sink.position == 0L && after.units == 2L &&
                after.copiedEntries == 0L && after.copyChunks == 0L && after.open,
                "PENDING_EXPORT_REJECTION_ORACLE");
        require(state.bodyBefore.unchanged(), "PENDING_EXPORT_BODY_MUTATED");
        requireInputsUnchanged(state);
        state.report.put("observedError", describe(failure));
        state.report.put("reason", "EXPECTED_REJECTION");
        state.report.put("bodyUnchanged", Boolean.TRUE);
        state.report.put("inputsUnchanged", Boolean.TRUE);
        state.report.put("cleanupInstalled", Boolean.FALSE);
        state.report.put("discarded", Boolean.TRUE);
        state.report.put("finishAfterFailure", Boolean.FALSE);
        state.report.put("payloadJson", "");
        state.report.put("phase", "pending_header");
    }

    private static void runHandoff(State state) throws Exception {
        state.phase = "r7_handoff";
        MeterCounters zero = MeterCounters.read(state.meter);
        long operationStarted = System.nanoTime();
        state.body = CurrentAssemblyMetadata.pendingCleanupBody(state.meter, RUN, SOURCE,
                CONTEXT, state.fixture.capture, state.fixture.cohort, state.fixture.arms);
        MeterCounters afterConstructor = MeterCounters.read(state.meter);
        require(afterConstructor.units - zero.units == 14L &&
                afterConstructor.copiedEntries - zero.copiedEntries == 13L &&
                afterConstructor.copyChunks - zero.copyChunks == 1L,
                "HANDOFF_CONSTRUCTOR_PREFIX");
        state.meter.copy(1);
        state.meter.charge(1);
        state.body.put("qualifiedPrior", state.fixture.prior);
        MeterCounters beforeInstaller = MeterCounters.read(state.meter);
        require(beforeInstaller.units - zero.units == 16L &&
                beforeInstaller.copiedEntries - zero.copiedEntries == 14L &&
                beforeInstaller.copyChunks - zero.copyChunks == 2L,
                "HANDOFF_PRIOR_PREFIX");
        long cleanupNanos = handoffCleanupNanos(state.name);
        CurrentAssemblyMetadata.installCleanup(state.meter, state.body, cleanupNanos);
        MeterCounters afterInstaller = MeterCounters.read(state.meter);
        require(afterInstaller.units - beforeInstaller.units == 67L &&
                afterInstaller.copiedEntries - beforeInstaller.copiedEntries == 11L &&
                afterInstaller.copyChunks - beforeInstaller.copyChunks == 2L,
                "HANDOFF_INSTALLER_ONLY_DEBIT");
        state.report.put("helperUnits", Long.valueOf(afterInstaller.units - beforeInstaller.units));
        state.report.put("helperCopies", Long.valueOf(afterInstaller.copiedEntries - beforeInstaller.copiedEntries));
        state.report.put("helperChunks", Long.valueOf(afterInstaller.copyChunks - beforeInstaller.copyChunks));
        CohortTerminalTelemetryStudy.R7WorkPlan plan =
                CohortTerminalTelemetryStudy.captureDetachedR7WorkPlan(state.meter,
                state.started, state.body,
                0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0,
                0, 0, 0, 0,
                cleanupNanos,
                0, 0, 0, 0, 0);
        @SuppressWarnings("unchecked")
        Map<String,Object> work = (Map<String,Object>)state.body.get("work");
        plan.verifyBound(state.meter, plan.pre, state.body, work);
        plan.finalizeWork(System.nanoTime());
        state.meter.method("check", new Class<?>[0]);
        long operationEnded = System.nanoTime();
        MeterCounters after = MeterCounters.read(state.meter);
        captureMetrics(state, after, operationEnded, operationEnded - operationStarted);
        require(after.units == 18576L && after.copiedEntries == 132L &&
                after.copyChunks == 19L && after.groups == 1L && !after.open &&
                after.priorGroups == 0L && after.groupUnits == 18576L &&
                after.maxGroupUnits == 18576L, "HANDOFF_NATIVE_SOURCE_CENSUS");
        require(after.units - zero.units == 18576L &&
                after.copiedEntries - zero.copiedEntries == 132L &&
                after.copyChunks - zero.copyChunks == 19L, "HANDOFF_TOTALS");
        require(state.report.get("semanticDurationGets").equals(Long.valueOf(0)),
                "HANDOFF_DURATION_READ");
        checkHeaders(state.body, state.fixture, state.fixture.prior, cleanupFrom(state.body), work);
        checkCleanup(cleanupFrom(state.body), expectedCleanup(state.name, cleanupNanos));
        checkWork(work, cleanupNanos, after.maxGroupNanos, after.maxGroupUnits);
        requireNoInputAliases(state, cleanupFrom(state.body));
        requireInputsUnchanged(state);
        TestbenchSink sink = new TestbenchSink();
        CurrentV2CanonicalCodec.writeBody(state.body, sink);
        sink.requireComplete();
        String formatted = sink.text.toString();
        require(formatted.charAt(formatted.length() - 1) == '\n', "HANDOFF_FINAL_LF");
        require(formatted.equals(json(state.body) + "\n"), "HANDOFF_CODEC_TESTBENCH_BYTES");
        String payload = formatted.substring(0, formatted.length() - 1);
        require(payload.indexOf('\n') < 0 && payload.length() + 1 == sink.position,
                "HANDOFF_PAYLOAD_LF_POLICY");
        state.report.put("bodyUnchanged", Boolean.FALSE);
        state.report.put("inputsUnchanged", Boolean.TRUE);
        state.report.put("cleanupInstalled", Boolean.TRUE);
        state.report.put("discarded", Boolean.FALSE);
        state.report.put("payloadJson", payload);
        state.report.put("canonicalBytes", Long.valueOf(sink.position));
        state.report.put("phase", "r7_handoff");
    }

    private static long handoffCleanupNanos(String name) {
        if ("r7_handoff_below_sum".equals(name)) return 5L;
        return 123L;
    }

    private static void recordExpectedRejection(State state, Throwable failure,
            String expected, boolean expectedBodyUnchanged) throws Exception {
        requireFailureCode(failure, expected);
        state.report.put("observedError", describe(failure));
        state.report.put("reason", "EXPECTED_REJECTION");
        state.report.put("bodyUnchanged", Boolean.valueOf(expectedBodyUnchanged));
        state.report.put("inputsUnchanged", Boolean.TRUE);
        state.report.put("cleanupInstalled", Boolean.FALSE);
        state.report.put("discarded", Boolean.TRUE);
        state.report.put("finishAfterFailure", Boolean.FALSE);
        state.report.put("payloadJson", "");
        state.report.put("phase", state.phase);
    }

    private static void recordUnexpectedPositiveFailure(State state, Throwable failure) {
        state.report.put("status", "FAIL");
        state.report.put("phase", state.phase);
        state.report.put("reason", "UNEXPECTED_REJECTION");
        state.report.put("observedError", describe(failure));
        state.report.put("bodyUnchanged", Boolean.valueOf(
                state.bodyBefore != null && state.bodyBefore.unchanged()));
        state.report.put("cleanupInstalled", Boolean.valueOf(state.body != null &&
                state.body.get("cleanup") instanceof Map));
        state.report.put("discarded", Boolean.TRUE);
        state.report.put("finishAfterFailure", Boolean.FALSE);
        state.report.put("payloadJson", "");
    }

    private static void requireFailureCode(Throwable failure, String expected) {
        require(failure != null, "EXPECTED_REJECTION_MISSING_" + expected);
        String actual = failure instanceof CurrentV2CanonicalCodec.CodecFailure
                ? ((CurrentV2CanonicalCodec.CodecFailure)failure).code
                : String.valueOf(failure.getMessage());
        require(expected.equals(actual), "EXPECTED_ERROR_" + expected + "_GOT_" + describe(failure));
    }

    private static void requireError(Throwable failure, String expected) {
        requireFailureCode(failure, expected);
    }

    private static void requireFailureType(Throwable failure, String expectedSimpleName) {
        require(failure != null && expectedSimpleName.equals(failure.getClass().getSimpleName()),
                "EXPECTED_ERROR_TYPE_" + expectedSimpleName + "_GOT_" + describe(failure));
    }

    private static Throwable checkAfterOperation(State state, Throwable failure,
            String expectedCode) throws Exception {
        state.meter.method("check", new Class<?>[0]);
        return failure;
    }

    private static final class MeterCounters {
        final long units, copiedEntries, copyChunks, groups, priorGroups, groupUnits;
        final long maxGroupUnits, maxGroupNanos, groupStarted, started;
        final boolean open;
        final int nestedGets, semanticDurationGets;

        MeterCounters(CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
            Object nativeMeter = meter.meter;
            units = number(nativeMeter, "units");
            copiedEntries = number(nativeMeter, "copiedEntries");
            copyChunks = number(nativeMeter, "copyChunks");
            groups = number(nativeMeter, "groups");
            priorGroups = number(nativeMeter, "priorGroups");
            groupUnits = number(nativeMeter, "groupUnits");
            maxGroupUnits = number(nativeMeter, "maxGroupUnits");
            maxGroupNanos = number(nativeMeter, "maxGroupNanos");
            groupStarted = number(nativeMeter, "groupStarted");
            started = number(nativeMeter, "started");
            open = Boolean.TRUE.equals(CohortTerminalTelemetryStudy.field(nativeMeter, "open"));
            Counts counts = Counts.ACTIVE;
            nestedGets = counts == null ? 0 : counts.nestedGets;
            semanticDurationGets = counts == null ? 0 : counts.semanticDurationGets;
        }

        private static long number(Object object, String name) throws Exception {
            return ((Number)CohortTerminalTelemetryStudy.field(object, name)).longValue();
        }

        static MeterCounters read(CohortTerminalTelemetryStudy.SetupMeter meter) throws Exception {
            return new MeterCounters(meter);
        }
    }

    private static void captureMetrics(State state, MeterCounters meter,
            long now, long operationNanos) {
        Map<String,Object> r = state.report;
        r.put("units", Long.valueOf(meter.units));
        r.put("copiedEntries", Long.valueOf(meter.copiedEntries));
        r.put("copyChunks", Long.valueOf(meter.copyChunks));
        r.put("groups", Long.valueOf(meter.groups));
        r.put("priorGroups", Long.valueOf(meter.priorGroups));
        r.put("groupUnits", Long.valueOf(meter.groupUnits));
        r.put("maxGroupUnits", Long.valueOf(meter.maxGroupUnits));
        r.put("maxGroupNanos", Long.valueOf(meter.maxGroupNanos));
        r.put("meterOpen", Boolean.valueOf(meter.open));
        require(operationNanos >= 0L, "OPERATION_CLOCK_NEGATIVE");
        r.put("operationNanos", Long.valueOf(operationNanos));
        long openElapsed = meter.open ? now - meter.groupStarted : 0L;
        require(openElapsed >= 0L, "OPEN_GROUP_CLOCK_NEGATIVE");
        r.put("openGroupElapsedNanos", Long.valueOf(openElapsed));
        r.put("observedNestedGets", Long.valueOf(meter.nestedGets));
        r.put("semanticDurationGets", Long.valueOf(meter.semanticDurationGets));
        state.metricsCaptured = true;
        checkBounds(state, meter, now);
    }

    private static void checkBounds(State state, MeterCounters meter, long now) {
        long elapsed = now - state.started;
        long openElapsed = meter.open ? now - meter.groupStarted : 0L;
        require(elapsed >= 0L && elapsed <= 90000000000L, "OPERATION_TIME_BOUND");
        require(!meter.open || openElapsed >= 0L && openElapsed <= 5000000000L,
                "OPEN_GROUP_TIME_BOUND");
        require(meter.maxGroupNanos >= 0L && meter.maxGroupNanos <= 5000000000L,
                "MAX_GROUP_TIME_BOUND");
        require(meter.groups >= 0L && meter.groups <= 640L, "GROUP_COUNT_BOUND");
        require(meter.groupUnits >= 0L && meter.groupUnits <= 32768L &&
                meter.maxGroupUnits >= 0L && meter.maxGroupUnits <= 32768L,
                "GROUP_UNIT_BOUND");
        require(meter.copiedEntries >= 0L && meter.copiedEntries <= 1048576L &&
                meter.copyChunks >= 0L, "COPY_BOUND");
        require(meter.units >= 0L && meter.started == state.started, "METER_START_OR_UNITS");
    }

    private static void helperDelta(Map<String,Object> report,
            MeterCounters before, MeterCounters after) {
        report.put("helperUnits", Long.valueOf(after.units - before.units));
        report.put("helperCopies", Long.valueOf(after.copiedEntries - before.copiedEntries));
        report.put("helperChunks", Long.valueOf(after.copyChunks - before.copyChunks));
    }

    private static final class Counts {
        static Counts ACTIVE;
        int nestedGets;
        int semanticDurationGets;
    }

    private static final class TrackingMap extends LinkedHashMap<String,Object> {
        private static final long serialVersionUID = 1L;
        final Counts counts;
        boolean failOwnersReleased;

        TrackingMap(Counts counts) { this.counts = counts; }

        public Object get(Object key) {
            if ("cleanupNanos".equals(key)) {
                counts.semanticDurationGets++;
                throw new IllegalStateException("SYNTHETIC_DURATION_READ");
            }
            counts.nestedGets++;
            if (failOwnersReleased && "ownersReleased".equals(key))
                throw new IllegalStateException("SYNTHETIC_VIEW_READ");
            return super.get(key);
        }
    }

    private static final class Fixture {
        final Counts counts;
        final TrackingMap capture, template, cohort, arms, nullArm, nullCleanup,
                observeArm, observeCleanup;
        final Map<String,Object> prior;
        Fixture(Counts counts, TrackingMap capture, TrackingMap template,
                TrackingMap cohort, TrackingMap arms, TrackingMap nullArm,
                TrackingMap nullCleanup, TrackingMap observeArm, TrackingMap observeCleanup,
                Map<String,Object> prior) {
            this.counts = counts; this.capture = capture; this.template = template;
            this.cohort = cohort; this.arms = arms; this.nullArm = nullArm;
            this.nullCleanup = nullCleanup; this.observeArm = observeArm;
            this.observeCleanup = observeCleanup; this.prior = prior;
        }

        List<Map<?,?>> inputContainers() {
            List<Map<?,?>> maps = new ArrayList<Map<?,?>>();
            maps.add(capture); maps.add(template); maps.add(cohort); maps.add(arms);
            maps.add(nullArm); maps.add(nullCleanup); maps.add(observeArm); maps.add(observeCleanup);
            maps.add(prior);
            return maps;
        }
    }

    private static Fixture fixture(String name) {
        Counts counts = new Counts();
        Counts.ACTIVE = counts;
        TrackingMap capture = new TrackingMap(counts);
        TrackingMap template = new TrackingMap(counts);
        TrackingMap cohort = new TrackingMap(counts);
        TrackingMap arms = new TrackingMap(counts);
        TrackingMap nullArm = new TrackingMap(counts);
        TrackingMap nullCleanup = new TrackingMap(counts);
        TrackingMap observeArm = new TrackingMap(counts);
        TrackingMap observeCleanup = new TrackingMap(counts);

        template.put("geometryBeforeSha256", GEOMETRY);
        template.put("geometryAfterSha256", GEOMETRY);
        template.put("effectsBeforeSha256", EFFECTS);
        template.put("effectsAfterSha256", EFFECTS);

        capture.put("fixtureScope", SCOPE);
        capture.put("template", template);
        capture.put("ownersReleased", Boolean.TRUE);
        capture.put("contextRestored", Boolean.TRUE);
        capture.put("cleanupNanos", Long.valueOf(17L));
        if ("projection_read_error".equals(name)) capture.failOwnersReleased = true;

        cohort.put("fixtureScope", SCOPE);
        putArm(nullArm, nullCleanup, 23L);
        putArm(observeArm, observeCleanup, 31L);
        arms.put("null", nullArm);
        arms.put("observe", observeArm);

        if ("null_alias_false".equals(name))
            nullCleanup.put("adapterCandidateAliasDropped", Boolean.FALSE);
        if ("r7_handoff_alias_false".equals(name))
            nullCleanup.put("adapterSessionAliasDropped", Boolean.FALSE);
        if ("observe_alias_false".equals(name))
            observeCleanup.put("adapterRouteResultAliasDropped", Boolean.FALSE);
        if ("geometry_false".equals(name))
            observeCleanup.put("sourceGeometryAfterSha256",
                    "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc");
        if ("child_view_missing".equals(name)) observeArm.remove("cleanup");

        Map<String,Object> prior = new LinkedHashMap<String,Object>();
        prior.put("fixtureScope", SCOPE);
        return new Fixture(counts, capture, template, cohort, arms, nullArm,
                nullCleanup, observeArm, observeCleanup, prior);
    }

    private static void putArm(TrackingMap arm, TrackingMap cleanup, long duration) {
        arm.put("fixtureScope", SCOPE);
        for (String key : ALIAS_KEYS) cleanup.put(key, Boolean.TRUE);
        cleanup.put("sourceGeometryBeforeSha256", GEOMETRY);
        cleanup.put("sourceGeometryAfterSha256", GEOMETRY);
        cleanup.put("contextRestored", Boolean.TRUE);
        cleanup.put("cleanupNanos", Long.valueOf(duration));
        arm.put("cleanup", cleanup);
    }

    private static final class MapImage {
        final Map<?,?> map;
        final Object[] keys;
        final Object[] values;
        MapImage(Map<?,?> map) {
            this.map = map;
            List<Object> keyList = new ArrayList<Object>();
            List<Object> valueList = new ArrayList<Object>();
            for (Map.Entry<?,?> entry : map.entrySet()) {
                keyList.add(entry.getKey());
                valueList.add(entry.getValue());
            }
            keys = keyList.toArray();
            values = valueList.toArray();
        }
        boolean unchanged() {
            if (map.size() != keys.length) return false;
            int i = 0;
            for (Map.Entry<?,?> entry : map.entrySet()) {
                if (i >= keys.length || !same(keys[i], entry.getKey()) || values[i] != entry.getValue())
                    return false;
                i++;
            }
            return i == keys.length;
        }
        private static boolean same(Object left, Object right) {
            return left == right || left != null && left.equals(right);
        }
    }

    private static final class InputsImage {
        final List<MapImage> images = new ArrayList<MapImage>();
        final IdentityHashMap<Map<?,?>,Boolean> identities = new IdentityHashMap<Map<?,?>,Boolean>();
        InputsImage(Fixture fixture) {
            for (Map<?,?> map : fixture.inputContainers()) {
                require(identities.put(map, Boolean.TRUE) == null, "FIXTURE_MAP_ALIAS");
                images.add(new MapImage(map));
            }
        }
        boolean unchanged() {
            for (MapImage image : images) if (!image.unchanged()) return false;
            return true;
        }
    }

    private static void requireInputsUnchanged(State state) {
        require(state.inputsImage.unchanged(), "INPUTS_CHANGED");
        state.report.put("inputsUnchanged", Boolean.TRUE);
    }

    private static void requireNoInputAliases(State state, Map<String,Object> cleanup) {
        IdentityHashMap<Map<?,?>,Boolean> inputs = new IdentityHashMap<Map<?,?>,Boolean>();
        for (Map<?,?> map : state.fixture.inputContainers()) inputs.put(map, Boolean.TRUE);
        for (Map.Entry<String,Object> entry : cleanup.entrySet()) {
            require(!(entry.getValue() instanceof Map) && !(entry.getValue() instanceof List),
                    "CLEANUP_CONTAINS_CONTAINER");
            require(!inputs.containsKey(entry.getValue()), "CLEANUP_INPUT_ALIAS");
        }
    }

    private static void putRoot(Map<String,Object> body, Fixture f,
            Map<String,Object> qualifiedPrior, Map<String,Object> work,
            Map<String,Object> cleanup) {
        body.put("schema", "tsj-cohort-terminal-result-v2");
        body.put("mode", "OBSERVED_COHORT_TERMINAL_TELEMETRY");
        body.put("status", "COMPLETED_DIAGNOSTIC_ONLY");
        body.put("runId", RUN);
        body.put("sourceManifestSha256", SOURCE);
        body.put("wireContractSha256", WIRE);
        body.put("inputContextSha256", CONTEXT);
        body.put("qualifiedPrior", qualifiedPrior);
        body.put("originalCapture", f.capture);
        body.put("cohort", f.cohort);
        body.put("arms", f.arms);
        body.put("work", work);
        body.put("cleanup", cleanup);
    }

    private static void checkHeaders(Map<String,Object> body, Fixture f,
            Map<String,Object> qualifiedPrior, Map<String,Object> cleanup,
            Map<String,Object> work) {
        require(body != null && body.getClass() == LinkedHashMap.class && body.size() == 13,
                "ROOT_EXACT_SHAPE");
        int index = 0;
        for (String key : body.keySet()) require(ROOT_KEYS[index++].equals(key), "ROOT_ORDER");
        require("tsj-cohort-terminal-result-v2".equals(body.get("schema")) &&
                "OBSERVED_COHORT_TERMINAL_TELEMETRY".equals(body.get("mode")) &&
                "COMPLETED_DIAGNOSTIC_ONLY".equals(body.get("status")), "ROOT_HEADERS");
        require(RUN.equals(body.get("runId")) && SOURCE.equals(body.get("sourceManifestSha256")) &&
                WIRE.equals(body.get("wireContractSha256")) &&
                CONTEXT.equals(body.get("inputContextSha256")), "ROOT_IDENTIFIERS");
        require(body.get("originalCapture") == f.capture && body.get("cohort") == f.cohort &&
                body.get("arms") == f.arms, "ROOT_INPUT_IDENTITIES");
        if (qualifiedPrior == null) require(body.get("qualifiedPrior") == null, "ROOT_PENDING_PRIOR");
        else require(body.get("qualifiedPrior") == qualifiedPrior, "ROOT_PRIOR_IDENTITY");
        if (cleanup == null) require(body.get("cleanup") == null, "ROOT_PENDING_CLEANUP");
        else require(body.get("cleanup") == cleanup, "ROOT_CLEANUP_IDENTITY");
        if (work == null) require(body.get("work") == null, "ROOT_PENDING_WORK");
        else require(body.get("work") == work, "ROOT_WORK_IDENTITY");
    }

    private static Map<String,Object> expectedCleanup(String name, long cleanupNanos) {
        boolean nullAliases = !"null_alias_false".equals(name) &&
                !"r7_handoff_alias_false".equals(name);
        boolean observeAliases = !"observe_alias_false".equals(name);
        boolean geometryUnchanged = !"geometry_false".equals(name);
        Map<String,Object> expected = new LinkedHashMap<String,Object>();
        expected.put("schema", "tsj-cohort-terminal-cleanup-v1");
        expected.put("originalCaptureOwnersReleased", Boolean.TRUE);
        expected.put("originalCaptureContextRestored", Boolean.TRUE);
        expected.put("nullArmAliasesDropped", Boolean.valueOf(nullAliases));
        expected.put("observeArmAliasesDropped", Boolean.valueOf(observeAliases));
        expected.put("sourceGeometryUnchanged", Boolean.valueOf(geometryUnchanged));
        expected.put("effectsScope", "ORIGINAL_CAPTURE_COPY_ONLY");
        expected.put("originalCaptureCopyEffectsUnchanged", Boolean.TRUE);
        expected.put("contextRestored", Boolean.TRUE);
        expected.put("cleanupNanos", Long.valueOf(cleanupNanos));
        return expected;
    }

    private static Map<String,Object> cleanupFrom(Map<String,Object> body) {
        Object value = body.get("cleanup");
        require(value instanceof Map, "CLEANUP_MISSING");
        @SuppressWarnings("unchecked")
        Map<String,Object> cleanup = (Map<String,Object>)value;
        return cleanup;
    }

    private static void checkCleanup(Map<String,Object> actual, Map<String,Object> expected) {
        require(actual.getClass() == LinkedHashMap.class && actual.size() == CLEANUP_KEYS.length,
                "CLEANUP_SHAPE");
        int index = 0;
        for (String key : actual.keySet()) require(CLEANUP_KEYS[index++].equals(key), "CLEANUP_ORDER");
        require(actual.equals(expected), "CLEANUP_EXPECTED_VALUES");
    }

    private static void checkWork(Map<String,Object> work, long cleanupNanos,
            long maxGroupNanos, long maxGroupUnits) {
        require(work != null && work.getClass() == LinkedHashMap.class &&
                work.size() == WORK_KEYS.length, "WORK_EXACT_SHAPE");
        long[] expected = new long[WORK_KEYS.length];
        Object rawCutoff = work.get("elapsedCutoffNanos");
        require(rawCutoff instanceof Long && ((Long)rawCutoff).longValue() > 0L &&
                ((Long)rawCutoff).longValue() <= 90000000000L, "WORK_CUTOFF_BOUND");
        expected[0] = ((Long)rawCutoff).longValue();
        expected[1] = cleanupNanos;
        expected[2] = 1L;
        expected[3] = 1L;
        expected[4] = maxGroupNanos;
        expected[5] = maxGroupUnits;
        expected[6] = 18576L;
        expected[25] = 18576L;
        expected[26] = 18576L;
        expected[27] = 1L;
        expected[28] = 132L;
        expected[29] = 19L;
        expected[30] = 132L;
        expected[31] = 19L;
        expected[32] = 4096L;
        expected[33] = 1048576L;
        int index = 0;
        for (Map.Entry<String,Object> entry : work.entrySet()) {
            require(WORK_KEYS[index].equals(entry.getKey()), "WORK_ORDER_" + index);
            require(entry.getValue() instanceof Long &&
                    ((Long)entry.getValue()).longValue() == expected[index],
                    "WORK_VALUE_" + WORK_KEYS[index]);
            index++;
        }
        require(index == WORK_KEYS.length, "WORK_CUTOFF");
    }

    private static final class TestbenchSink implements CohortTerminalTelemetryStudy.CanonicalJsonSink {
        final StringBuilder text = new StringBuilder();
        long position;
        public void beforeDataOperation() throws Exception {
            if (position >= JSON_LIMIT) throw new IOException("TESTBENCH_BODY_LIMIT");
        }
        public void writeByte(int ascii) throws Exception {
            beforeDataOperation();
            if (ascii != 10 && (ascii < 32 || ascii > 126))
                throw new IOException("TESTBENCH_BODY_ASCII");
            text.append((char)ascii);
            position++;
        }
        void requireComplete() {
            require(position >= 2L && position <= JSON_LIMIT &&
                    text.length() == position && text.charAt(text.length() - 1) == '\n',
                    "TESTBENCH_BODY_COMPLETE");
        }
    }

    private static Map<String,Object> caseRecord(String name) {
        Map<String,Object> r = new LinkedHashMap<String,Object>();
        r.put("name", name);
        r.put("status", "PASS");
        r.put("phase", "fixture");
        r.put("reason", "OK");
        r.put("observedError", "");
        String[] firstNumbers = {"units", "copiedEntries", "copyChunks", "groups", "priorGroups",
            "groupUnits", "maxGroupUnits", "maxGroupNanos"};
        for (String key : firstNumbers) r.put(key, Long.valueOf(0L));
        r.put("meterOpen", Boolean.FALSE);
        r.put("helperUnits", Long.valueOf(0L));
        r.put("helperCopies", Long.valueOf(0L));
        r.put("helperChunks", Long.valueOf(0L));
        r.put("operationNanos", Long.valueOf(0L));
        r.put("totalElapsedNanos", Long.valueOf(0L));
        r.put("openGroupElapsedNanos", Long.valueOf(0L));
        r.put("bodyUnchanged", Boolean.FALSE);
        r.put("inputsUnchanged", Boolean.FALSE);
        r.put("cleanupInstalled", Boolean.FALSE);
        r.put("observedNestedGets", Long.valueOf(0L));
        r.put("semanticDurationGets", Long.valueOf(0L));
        r.put("discarded", Boolean.FALSE);
        r.put("finishAfterFailure", Boolean.FALSE);
        r.put("payloadJson", "");
        r.put("canonicalBytes", Long.valueOf(0L));
        require(r.keySet().size() == CASE_KEYS.length, "CASE_RECORD_KEY_COUNT");
        int index = 0;
        for (String key : r.keySet()) require(CASE_KEYS[index++].equals(key), "CASE_RECORD_KEY_ORDER");
        return r;
    }

    private static String json(Object value) {
        StringBuilder out = new StringBuilder();
        writeJson(value, out, new IdentityHashMap<Object,Boolean>(), 0);
        require(out.length() <= JSON_LIMIT, "JSON_REPORT_BOUND");
        return out.toString();
    }

    private static void writeJson(Object value, StringBuilder out,
            IdentityHashMap<Object,Boolean> active, int depth) {
        require(depth <= 64 && out.length() <= JSON_LIMIT, "JSON_BOUND");
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            appendString((String)value, out);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value.toString());
        } else if (value instanceof Map) {
            require(active.put(value, Boolean.TRUE) == null, "JSON_CYCLE");
            out.append('{');
            boolean first = true;
            for (Map.Entry<?,?> entry : ((Map<?,?>)value).entrySet()) {
                require(entry.getKey() instanceof String, "JSON_KEY");
                if (!first) out.append(',');
                first = false;
                appendString((String)entry.getKey(), out);
                out.append(':');
                writeJson(entry.getValue(), out, active, depth + 1);
            }
            out.append('}');
            active.remove(value);
        } else if (value instanceof List) {
            require(active.put(value, Boolean.TRUE) == null, "JSON_CYCLE");
            out.append('[');
            boolean first = true;
            for (Object item : (List<?>)value) {
                if (!first) out.append(',');
                first = false;
                writeJson(item, out, active, depth + 1);
            }
            out.append(']');
            active.remove(value);
        } else {
            throw new AssertionError("JSON_TYPE_" + value.getClass().getSimpleName());
        }
        require(out.length() <= JSON_LIMIT, "JSON_BOUND");
    }

    private static void appendString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            require(c >= 32 && c <= 126, "JSON_ASCII");
            if (c == '"' || c == '\\') out.append('\\');
            out.append(c);
            require(out.length() <= JSON_LIMIT, "JSON_BOUND");
        }
        out.append('"');
    }

    private static String describe(Throwable failure) {
        if (failure == null) return "";
        String message = String.valueOf(failure.getMessage());
        String text = failure.getClass().getSimpleName() + ":" + message;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && i < 240; i++) {
            char c = text.charAt(i);
            out.append(c >= 32 && c <= 126 ? c : '?');
        }
        return out.toString();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
