package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent ordinary-map fixtures for the partial body correlation canary. */
final class BodyCorrelationOracle {
    static final String RUN_ID = "00000000-0000-0000-0000-000000000001";
    static final String SOURCE_SHA =
            "1111111111111111111111111111111111111111111111111111111111111111";
    static final String WIRE_SHA =
            "1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed";
    static final String CONTEXT_SHA =
            "3333333333333333333333333333333333333333333333333333333333333333";
    static final String OTHER_CONTEXT_SHA =
            "4444444444444444444444444444444444444444444444444444444444444444";
    static final long MAX_CLEANUP_NANOS = 120000000000L;
    static final int WIDE_PADDING_COUNT = 27;
    static final int WIDE_PADDING_VALUE_LENGTH = 120;

    /* Literal JSON oracle: only the current header shape and supplied
     * correlations are represented. It is not a full-D or genuine-result oracle. */
    static final String MINIMAL_BODY_JSON =
        "{\"schema\":\"tsj-cohort-terminal-result-v2\",\"mode\":\"OBSERVED_COHORT_TERMINAL_TELEMETRY\",\"status\":\"COMPLETED_DIAGNOSTIC_ONLY\",\"runId\":\"00000000-0000-0000-0000-000000000001\",\"sourceManifestSha256\":\"1111111111111111111111111111111111111111111111111111111111111111\",\"wireContractSha256\":\"1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed\",\"inputContextSha256\":\"3333333333333333333333333333333333333333333333333333333333333333\",\"qualifiedPrior\":{\"inputContextSha256\":\"3333333333333333333333333333333333333333333333333333333333333333\"},\"originalCapture\":{\"contextRestored\":true,\"ownersReleased\":true,\"template\":{\"geometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"geometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"effectsBeforeSha256\":\"6666666666666666666666666666666666666666666666666666666666666666\",\"effectsAfterSha256\":\"6666666666666666666666666666666666666666666666666666666666666666\"}},\"cohort\":{},\"arms\":{\"null\":{\"armRole\":\"NULL\",\"cleanup\":{\"contextRestored\":true,\"adapterSessionAliasDropped\":true,\"adapterSearchAliasDropped\":true,\"adapterCandidateAliasDropped\":true,\"adapterRouteResultAliasDropped\":true,\"adapterBoardTemplateAliasesDropped\":true,\"sourceGeometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"sourceGeometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\"}},\"observe\":{\"armRole\":\"OBSERVE\",\"cleanup\":{\"contextRestored\":true,\"adapterSessionAliasDropped\":true,\"adapterSearchAliasDropped\":true,\"adapterCandidateAliasDropped\":true,\"adapterRouteResultAliasDropped\":true,\"adapterBoardTemplateAliasesDropped\":true,\"sourceGeometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"sourceGeometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\"}}},\"work\":{\"cleanupNanos\":0,\"ordinaryList\":[\"synthetic\"]},\"cleanup\":{\"cleanupNanos\":0,\"originalCaptureContextRestored\":true,\"contextRestored\":true,\"originalCaptureOwnersReleased\":true,\"nullArmAliasesDropped\":true,\"observeArmAliasesDropped\":true,\"sourceGeometryUnchanged\":true,\"effectsScope\":\"ORIGINAL_CAPTURE_COPY_ONLY\",\"originalCaptureCopyEffectsUnchanged\":true}}\n";

    private static final String WIDE_PREFIX =
        "{\"schema\":\"tsj-cohort-terminal-result-v2\",\"mode\":\"OBSERVED_COHORT_TERMINAL_TELEMETRY\",\"status\":\"COMPLETED_DIAGNOSTIC_ONLY\",\"runId\":\"00000000-0000-0000-0000-000000000001\",\"sourceManifestSha256\":\"1111111111111111111111111111111111111111111111111111111111111111\",\"wireContractSha256\":\"1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed\",\"inputContextSha256\":\"3333333333333333333333333333333333333333333333333333333333333333\",\"qualifiedPrior\":{\"inputContextSha256\":\"3333333333333333333333333333333333333333333333333333333333333333\"},\"originalCapture\":{\"contextRestored\":true,\"ownersReleased\":true,\"template\":{\"geometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"geometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"effectsBeforeSha256\":\"6666666666666666666666666666666666666666666666666666666666666666\",\"effectsAfterSha256\":\"6666666666666666666666666666666666666666666666666666666666666666\"}},\"cohort\":{},\"arms\":{\"null\":{\"armRole\":\"NULL\",\"cleanup\":{\"contextRestored\":true,\"adapterSessionAliasDropped\":true,\"adapterSearchAliasDropped\":true,\"adapterCandidateAliasDropped\":true,\"adapterRouteResultAliasDropped\":true,\"adapterBoardTemplateAliasesDropped\":true,\"sourceGeometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"sourceGeometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\"}},\"observe\":{\"armRole\":\"OBSERVE\",\"cleanup\":{\"contextRestored\":true,\"adapterSessionAliasDropped\":true,\"adapterSearchAliasDropped\":true,\"adapterCandidateAliasDropped\":true,\"adapterRouteResultAliasDropped\":true,\"adapterBoardTemplateAliasesDropped\":true,\"sourceGeometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"sourceGeometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\"}}},\"work\":{\"cleanupNanos\":";
    private static final String WIDE_SUFFIX =
        "},\"cleanup\":{\"cleanupNanos\":120000000000,\"originalCaptureContextRestored\":true,\"contextRestored\":true,\"originalCaptureOwnersReleased\":true,\"nullArmAliasesDropped\":true,\"observeArmAliasesDropped\":true,\"sourceGeometryUnchanged\":true,\"effectsScope\":\"ORIGINAL_CAPTURE_COPY_ONLY\",\"originalCaptureCopyEffectsUnchanged\":true}}\n";

    private BodyCorrelationOracle() { }

    static Map<String, Object> body() {
        return body(Integer.valueOf(0), Integer.valueOf(0));
    }

    static Map<String, Object> body(Object workNanos, Object cleanupNanos) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("schema", "tsj-cohort-terminal-result-v2");
        body.put("mode", "OBSERVED_COHORT_TERMINAL_TELEMETRY");
        body.put("status", "COMPLETED_DIAGNOSTIC_ONLY");
        body.put("runId", RUN_ID);
        body.put("sourceManifestSha256", SOURCE_SHA);
        body.put("wireContractSha256", WIRE_SHA);
        body.put("inputContextSha256", CONTEXT_SHA);

        Map<String, Object> prior = new LinkedHashMap<String, Object>();
        prior.put("inputContextSha256", CONTEXT_SHA);
        body.put("qualifiedPrior", prior);
        Map<String, Object> originalCapture = new LinkedHashMap<String, Object>();
        originalCapture.put("contextRestored", Boolean.TRUE);
        originalCapture.put("ownersReleased", Boolean.TRUE);
        Map<String, Object> template = new LinkedHashMap<String, Object>();
        template.put("geometryBeforeSha256", "4444444444444444444444444444444444444444444444444444444444444444");
        template.put("geometryAfterSha256", "4444444444444444444444444444444444444444444444444444444444444444");
        template.put("effectsBeforeSha256", "6666666666666666666666666666666666666666666666666666666666666666");
        template.put("effectsAfterSha256", "6666666666666666666666666666666666666666666666666666666666666666");
        originalCapture.put("template", template);
        body.put("originalCapture", originalCapture);
        body.put("cohort", new LinkedHashMap<String, Object>());

        Map<String, Object> arms = new LinkedHashMap<String, Object>();
        Map<String, Object> nullArm = new LinkedHashMap<String, Object>();
        nullArm.put("armRole", "NULL");
        Map<String, Object> nullCleanup = new LinkedHashMap<String, Object>();
        nullCleanup.put("contextRestored", Boolean.TRUE);
        nullCleanup.put("adapterSessionAliasDropped", Boolean.TRUE);
        nullCleanup.put("adapterSearchAliasDropped", Boolean.TRUE);
        nullCleanup.put("adapterCandidateAliasDropped", Boolean.TRUE);
        nullCleanup.put("adapterRouteResultAliasDropped", Boolean.TRUE);
        nullCleanup.put("adapterBoardTemplateAliasesDropped", Boolean.TRUE);
        nullCleanup.put("sourceGeometryBeforeSha256", "4444444444444444444444444444444444444444444444444444444444444444");
        nullCleanup.put("sourceGeometryAfterSha256", "4444444444444444444444444444444444444444444444444444444444444444");
        nullArm.put("cleanup", nullCleanup);
        arms.put("null", nullArm);
        Map<String, Object> observeArm = new LinkedHashMap<String, Object>();
        observeArm.put("armRole", "OBSERVE");
        Map<String, Object> observeCleanup = new LinkedHashMap<String, Object>();
        observeCleanup.put("contextRestored", Boolean.TRUE);
        observeCleanup.put("adapterSessionAliasDropped", Boolean.TRUE);
        observeCleanup.put("adapterSearchAliasDropped", Boolean.TRUE);
        observeCleanup.put("adapterCandidateAliasDropped", Boolean.TRUE);
        observeCleanup.put("adapterRouteResultAliasDropped", Boolean.TRUE);
        observeCleanup.put("adapterBoardTemplateAliasesDropped", Boolean.TRUE);
        observeCleanup.put("sourceGeometryBeforeSha256", "4444444444444444444444444444444444444444444444444444444444444444");
        observeCleanup.put("sourceGeometryAfterSha256", "4444444444444444444444444444444444444444444444444444444444444444");
        observeArm.put("cleanup", observeCleanup);
        arms.put("observe", observeArm);
        body.put("arms", arms);

        Map<String, Object> work = new LinkedHashMap<String, Object>();
        work.put("cleanupNanos", workNanos);
        List<Object> ordinaryList = new ArrayList<Object>();
        ordinaryList.add("synthetic");
        work.put("ordinaryList", ordinaryList);
        body.put("work", work);

        Map<String, Object> cleanup = new LinkedHashMap<String, Object>();
        cleanup.put("cleanupNanos", cleanupNanos);
        cleanup.put("originalCaptureContextRestored", Boolean.TRUE);
        cleanup.put("contextRestored", Boolean.TRUE);
        cleanup.put("originalCaptureOwnersReleased", Boolean.TRUE);
        cleanup.put("nullArmAliasesDropped", Boolean.TRUE);
        cleanup.put("observeArmAliasesDropped", Boolean.TRUE);
        cleanup.put("sourceGeometryUnchanged", Boolean.TRUE);
        cleanup.put("effectsScope", "ORIGINAL_CAPTURE_COPY_ONLY");
        cleanup.put("originalCaptureCopyEffectsUnchanged", Boolean.TRUE);
        body.put("cleanup", cleanup);
        return body;
    }

    static String bodyJson(long workNanos, long cleanupNanos) {
        return "{\"schema\":\"tsj-cohort-terminal-result-v2\",\"mode\":\"OBSERVED_COHORT_TERMINAL_TELEMETRY\",\"status\":\"COMPLETED_DIAGNOSTIC_ONLY\",\"runId\":\"" +
            RUN_ID + "\",\"sourceManifestSha256\":\"" + SOURCE_SHA +
            "\",\"wireContractSha256\":\"" + WIRE_SHA +
            "\",\"inputContextSha256\":\"" + CONTEXT_SHA +
            "\",\"qualifiedPrior\":{\"inputContextSha256\":\"" + CONTEXT_SHA +
            "\"},\"originalCapture\":{\"contextRestored\":true,\"ownersReleased\":true,\"template\":{\"geometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"geometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"effectsBeforeSha256\":\"6666666666666666666666666666666666666666666666666666666666666666\",\"effectsAfterSha256\":\"6666666666666666666666666666666666666666666666666666666666666666\"}},\"cohort\":{},\"arms\":{\"null\":{\"armRole\":\"NULL\",\"cleanup\":{\"contextRestored\":true,\"adapterSessionAliasDropped\":true,\"adapterSearchAliasDropped\":true,\"adapterCandidateAliasDropped\":true,\"adapterRouteResultAliasDropped\":true,\"adapterBoardTemplateAliasesDropped\":true,\"sourceGeometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"sourceGeometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\"}},\"observe\":{\"armRole\":\"OBSERVE\",\"cleanup\":{\"contextRestored\":true,\"adapterSessionAliasDropped\":true,\"adapterSearchAliasDropped\":true,\"adapterCandidateAliasDropped\":true,\"adapterRouteResultAliasDropped\":true,\"adapterBoardTemplateAliasesDropped\":true,\"sourceGeometryBeforeSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\",\"sourceGeometryAfterSha256\":\"4444444444444444444444444444444444444444444444444444444444444444\"}}},\"work\":{\"cleanupNanos\":" +
            Long.toString(workNanos) +
            ",\"ordinaryList\":[\"synthetic\"]},\"cleanup\":{\"cleanupNanos\":" +
            Long.toString(cleanupNanos) + ",\"originalCaptureContextRestored\":true,\"contextRestored\":true,\"originalCaptureOwnersReleased\":true,\"nullArmAliasesDropped\":true,\"observeArmAliasesDropped\":true,\"sourceGeometryUnchanged\":true,\"effectsScope\":\"ORIGINAL_CAPTURE_COPY_ONLY\",\"originalCaptureCopyEffectsUnchanged\":true}}\n";
    }

    static Map<String, Object> wideBody() {
        Map<String, Object> body = body(
                Long.valueOf(MAX_CLEANUP_NANOS), Long.valueOf(MAX_CLEANUP_NANOS));
        Map<String, Object> work = work(body);
        for (int index = 0; index < WIDE_PADDING_COUNT; index++)
            work.put(paddingKey(index), paddingValue());
        return body;
    }

    /** Independent builder for a deliberately wide ordinary synthetic fixture. */
    static String wideJson() {
        StringBuilder json = new StringBuilder(5000);
        json.append(WIDE_PREFIX);
        json.append(Long.toString(MAX_CLEANUP_NANOS));
        json.append(",\"ordinaryList\":[\"synthetic\"]");
        String value = paddingValue();
        for (int index = 0; index < WIDE_PADDING_COUNT; index++) {
            json.append(',').append('"').append(paddingKey(index));
            json.append("\":\"").append(value).append('"');
        }
        json.append(WIDE_SUFFIX);
        return json.toString();
    }

    private static String paddingKey(int index) {
        return "padding" + (index < 10 ? "0" : "") + Integer.toString(index);
    }

    private static String paddingValue() {
        StringBuilder value = new StringBuilder(WIDE_PADDING_VALUE_LENGTH);
        for (int index = 0; index < WIDE_PADDING_VALUE_LENGTH; index++)
            value.append('x');
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> work(Map<String, Object> body) {
        return (Map<String, Object>) body.get("work");
    }
}
