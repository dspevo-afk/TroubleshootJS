package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Component-only projections for current result fields; no producer is enabled. */
final class CurrentResultProjections {
    private static final String CLEANUP_SCHEMA =
            "tsj-cohort-terminal-cleanup-v1";
    private static final String EFFECTS_SCOPE = "ORIGINAL_CAPTURE_COPY_ONLY";

    private CurrentResultProjections() { }

    /** Transfers the actual derived cohort lists under the caller's exclusive ownership. */
    static Map<String,Object> cohort(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.Cohort value) throws Exception {
        if (meter == null || value == null)
            throw new IOException("RESULT_PROJECTION_ARGUMENT");

        meter.charge(1);
        List<String> netIds = value.netIds;
        meter.charge(1);
        List<String> requested = value.requested;

        meter.copy(2);
        meter.charge(1);
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("netIds", netIds);
        result.put("requestedNetIds", requested);
        return result;
    }

    /** Projects cleanup observations without validating or blessing their meaning. */
    static Map<String,Object> cleanup(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<String,Object> capture,
            Map<String,Object> nullCleanup,
            Map<String,Object> observeCleanup,
            long workCleanupNanos) throws Exception {
        if (meter == null || capture == null || nullCleanup == null ||
                observeCleanup == null)
            throw new IOException("RESULT_PROJECTION_ARGUMENT");

        Map<?,?> template = (Map<?,?>)read(meter, capture, "template");
        boolean ownersReleased = ((Boolean)read(meter, capture,
                "ownersReleased")).booleanValue();
        boolean captureContextRestored = ((Boolean)read(meter, capture,
                "contextRestored")).booleanValue();
        String geometryBefore = (String)read(meter, template,
                "geometryBeforeSha256");
        String geometryAfter = (String)read(meter, template,
                "geometryAfterSha256");
        String effectsBefore = (String)read(meter, template,
                "effectsBeforeSha256");
        String effectsAfter = (String)read(meter, template,
                "effectsAfterSha256");

        boolean nullSessionAliasDropped = ((Boolean)read(meter, nullCleanup,
                "adapterSessionAliasDropped")).booleanValue();
        boolean nullSearchAliasDropped = ((Boolean)read(meter, nullCleanup,
                "adapterSearchAliasDropped")).booleanValue();
        boolean nullCandidateAliasDropped = ((Boolean)read(meter, nullCleanup,
                "adapterCandidateAliasDropped")).booleanValue();
        boolean nullRouteResultAliasDropped = ((Boolean)read(meter, nullCleanup,
                "adapterRouteResultAliasDropped")).booleanValue();
        boolean nullBoardTemplateAliasesDropped = ((Boolean)read(meter,
                nullCleanup, "adapterBoardTemplateAliasesDropped")).booleanValue();
        String nullGeometryBefore = (String)read(meter, nullCleanup,
                "sourceGeometryBeforeSha256");
        String nullGeometryAfter = (String)read(meter, nullCleanup,
                "sourceGeometryAfterSha256");
        boolean nullContextRestored = ((Boolean)read(meter, nullCleanup,
                "contextRestored")).booleanValue();

        boolean observeSessionAliasDropped = ((Boolean)read(meter,
                observeCleanup, "adapterSessionAliasDropped")).booleanValue();
        boolean observeSearchAliasDropped = ((Boolean)read(meter,
                observeCleanup, "adapterSearchAliasDropped")).booleanValue();
        boolean observeCandidateAliasDropped = ((Boolean)read(meter,
                observeCleanup, "adapterCandidateAliasDropped")).booleanValue();
        boolean observeRouteResultAliasDropped = ((Boolean)read(meter,
                observeCleanup, "adapterRouteResultAliasDropped")).booleanValue();
        boolean observeBoardTemplateAliasesDropped = ((Boolean)read(meter,
                observeCleanup, "adapterBoardTemplateAliasesDropped")).booleanValue();
        String observeGeometryBefore = (String)read(meter, observeCleanup,
                "sourceGeometryBeforeSha256");
        String observeGeometryAfter = (String)read(meter, observeCleanup,
                "sourceGeometryAfterSha256");
        boolean observeContextRestored = ((Boolean)read(meter, observeCleanup,
                "contextRestored")).booleanValue();

        boolean nullAliasesDropped = and(meter, nullSessionAliasDropped,
                nullSearchAliasDropped);
        nullAliasesDropped = and(meter, nullAliasesDropped,
                nullCandidateAliasDropped);
        nullAliasesDropped = and(meter, nullAliasesDropped,
                nullRouteResultAliasDropped);
        nullAliasesDropped = and(meter, nullAliasesDropped,
                nullBoardTemplateAliasesDropped);

        boolean observeAliasesDropped = and(meter, observeSessionAliasDropped,
                observeSearchAliasDropped);
        observeAliasesDropped = and(meter, observeAliasesDropped,
                observeCandidateAliasDropped);
        observeAliasesDropped = and(meter, observeAliasesDropped,
                observeRouteResultAliasDropped);
        observeAliasesDropped = and(meter, observeAliasesDropped,
                observeBoardTemplateAliasesDropped);

        boolean contextRestored = and(meter, captureContextRestored,
                nullContextRestored);
        contextRestored = and(meter, contextRestored, observeContextRestored);

        boolean geometryMatch1 = equal(meter, geometryBefore, geometryAfter);
        boolean geometryMatch2 = equal(meter, geometryBefore, nullGeometryBefore);
        boolean geometryMatch3 = equal(meter, geometryBefore, nullGeometryAfter);
        boolean geometryMatch4 = equal(meter, geometryBefore, observeGeometryBefore);
        boolean geometryMatch5 = equal(meter, geometryBefore, observeGeometryAfter);
        boolean geometryUnchanged = and(meter, geometryMatch1, geometryMatch2);
        geometryUnchanged = and(meter, geometryUnchanged, geometryMatch3);
        geometryUnchanged = and(meter, geometryUnchanged, geometryMatch4);
        geometryUnchanged = and(meter, geometryUnchanged, geometryMatch5);

        boolean copyEffectsUnchanged = equal(meter, effectsBefore, effectsAfter);

        meter.copy(10);
        meter.charge(1);
        Map<String,Object> result = new LinkedHashMap<String,Object>();
        result.put("schema", CLEANUP_SCHEMA);
        result.put("originalCaptureOwnersReleased", Boolean.valueOf(ownersReleased));
        result.put("originalCaptureContextRestored",
                Boolean.valueOf(captureContextRestored));
        result.put("nullArmAliasesDropped", Boolean.valueOf(nullAliasesDropped));
        result.put("observeArmAliasesDropped", Boolean.valueOf(observeAliasesDropped));
        result.put("sourceGeometryUnchanged", Boolean.valueOf(geometryUnchanged));
        result.put("effectsScope", EFFECTS_SCOPE);
        result.put("originalCaptureCopyEffectsUnchanged",
                Boolean.valueOf(copyEffectsUnchanged));
        result.put("contextRestored", Boolean.valueOf(contextRestored));
        meter.charge(1);
        Long cleanupNanos = Long.valueOf(workCleanupNanos);
        result.put("cleanupNanos", cleanupNanos);
        return result;
    }

    private static Object read(CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<?,?> values, String key) throws Exception {
        meter.charge(1);
        return values.get(key);
    }

    private static boolean and(CohortTerminalTelemetryStudy.SetupMeter meter,
            boolean left, boolean right) throws Exception {
        meter.charge(1);
        return left & right;
    }

    private static boolean equal(CohortTerminalTelemetryStudy.SetupMeter meter,
            String left, String right) throws Exception {
        meter.charge(1);
        return left.equals(right);
    }
}
