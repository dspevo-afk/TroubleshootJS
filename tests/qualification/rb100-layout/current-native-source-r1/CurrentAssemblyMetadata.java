package com.lushprojects.circuitjs1.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Source-only result metadata staging; the normal assembly remains closed.
 *
 * The caller exclusively owns the unmutated, validated CurrentV2InputSetup
 * result and the body. The two nested prior objects are borrowed from that
 * input, without another output alias. This seam checks compatibility and
 * prepares the existing receipt authority; it does not authenticate a host.
 */
final class CurrentAssemblyMetadata {
    static final String QUALIFIED_PRIOR_SCHEMA =
            "tsj-cohort-terminal-qualified-prior-v2";

    private CurrentAssemblyMetadata() { }

    /**
     * Derives the cohort before createBody needs its child map. The caller owns
     * the validated, unchanged setup and captured IDs exclusively throughout.
     * The temporary view borrows prior only for this synchronous call; the
     * returned projection contains only freshly derived lists.
     */
    static Map<String,Object> cohortBeforeBody(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup,
            List<String> actualCapturedIds) throws Exception {
        return CurrentResultProjections.cohort(meter,
                deriveSetupCohort(meter, setup, actualCapturedIds));
    }

    /**
     * Explicit staging entry point: prior, work, and cleanup are pending.
     * Headers and the three disjoint borrowed maps are caller-prevalidated;
     * the caller owns them exclusively until preparation, R7, and export end.
     * This delegates the unchanged fourteen-unit constructor.
     */
    static Map<String,Object> pendingCleanupBody(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            String runId, String sourceSha, String contextSha,
            Map<String,Object> originalCapture, Map<String,Object> cohort,
            Map<String,Object> arms) throws Exception {
        if (meter == null || runId == null || sourceSha == null || contextSha == null ||
                originalCapture == null || cohort == null || arms == null)
            throw new IOException("METADATA_ARGUMENTS");
        return CurrentV2CanonicalCodec.createBody(meter, runId, sourceSha, contextSha,
                originalCapture, cohort, arms, null);
    }

    private static CohortTerminalTelemetryStudy.Cohort deriveSetupCohort(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup,
            List<String> actualCapturedIds) throws Exception {
        if (meter == null || setup == null || actualCapturedIds == null)
            throw new IOException("METADATA_ARGUMENTS");

        meter.charge(1);
        Map<String,Object> data = setup.data;
        meter.charge(1);
        Object prior = data.get("prior");
        meter.charge(1);
        Map<String,Object> view = new LinkedHashMap<String,Object>();
        meter.copy(1);
        view.put("qualifiedPrior", prior);
        return CohortTerminalTelemetryStudy.deriveCohort(view, actualCapturedIds, meter);
    }

    /** Caller-owned metadata association, never wire data or receipt authority. */
    static final class CaptureCohort {
        private CohortTerminalTelemetryStudy.SetupMeter meter;
        private Map<String,Object> capture;
        private CohortTerminalTelemetryStudy.Cohort cohort;
        private boolean completed;

        private CaptureCohort(CohortTerminalTelemetryStudy.SetupMeter meter,
                Map<String,Object> capture, CohortTerminalTelemetryStudy.Cohort cohort) {
            this.meter = meter;
            this.capture = capture;
            this.cohort = cohort;
            this.completed = false;
        }
    }

    /**
     * Derive once before the exact two future arms. Caller owns stable metadata
     * and all borrowed lists exclusively through preparation/export, and drops
     * the raw cohort borrows after arms. No actual DTO or authority is validated.
     */
    @SuppressWarnings("unchecked")
    static CaptureCohort prepareCaptureCohort(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            CohortTerminalTelemetryStudy.SetupResult setup,
            Map<String,Object> originalCapture) throws Exception {
        if (meter == null || setup == null || originalCapture == null)
            throw new IOException("METADATA_ARGUMENTS");
        meter.charge(1);
        Object ids = originalCapture.get("netIds");
        if (!(ids instanceof List)) throw new IOException("METADATA_CAPTURE_NET_IDS");
        CohortTerminalTelemetryStudy.Cohort cohort =
                deriveSetupCohort(meter, setup, (List<String>)ids);
        meter.copy(4);
        meter.charge(1);
        return new CaptureCohort(meter, originalCapture, cohort);
    }

    /** Paid borrow; the future caller passes this same raw Cohort to both arms. */
    static CohortTerminalTelemetryStudy.Cohort captureCohortForArms(
            CohortTerminalTelemetryStudy.SetupMeter meter, CaptureCohort association)
            throws Exception {
        if (meter == null || association == null) throw new IOException("METADATA_ARGUMENTS");
        meter.charge(1);
        if (association.completed) throw new IOException("METADATA_CAPTURE_COMPLETED");
        meter.charge(1);
        if (association.meter != meter) throw new IOException("METADATA_CAPTURE_METER");
        meter.charge(1);
        return association.cohort;
    }

    /**
     * Project once after complete arms exist; finish this association once.
     * Failed admission preserves it and the partial failed Meter for inspection.
     * On abort the caller drops its association/borrows; no live resource is held.
     */
    static Map<String,Object> finishCaptureBody(
            CohortTerminalTelemetryStudy.SetupMeter meter, CaptureCohort association,
            String runId, String sourceSha, String contextSha, Map<String,Object> arms)
            throws Exception {
        if (meter == null || association == null || runId == null || sourceSha == null ||
                contextSha == null || arms == null) throw new IOException("METADATA_ARGUMENTS");
        CohortTerminalTelemetryStudy.Cohort raw = captureCohortForArms(meter, association);
        meter.charge(1);
        Map<String,Object> capture = association.capture;
        Map<String,Object> cohort = CurrentResultProjections.cohort(meter, raw);
        Map<String,Object> body = pendingCleanupBody(meter, runId, sourceSha, contextSha,
                capture, cohort, arms);
        // Prepay every replacement before clearing the first alias.
        meter.copy(4);
        meter.charge(4);
        association.meter = null;
        association.capture = null;
        association.cohort = null;
        association.completed = true;
        return body;
    }

    /** Install from this body's stable DTO views and the R7-owned cleanup scalar. */
    static void installCleanup(CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<String,Object> body, long cleanupNanos) throws Exception {
        if (meter == null || body == null) throw new IOException("METADATA_ARGUMENTS");

        // Preflight the existing R7 limit before any body observation or mutation.
        meter.charge(1);
        if (cleanupNanos < 0 || cleanupNanos > 120000000000L)
            throw new IOException("METADATA_CLEANUP_SCALAR");
        meter.charge(1);
        if (body.getClass() != LinkedHashMap.class)
            throw new IOException("METADATA_CLEANUP_BODY");
        meter.charge(1);
        if (!body.containsKey("cleanup")) throw new IOException("METADATA_CLEANUP_SLOT");
        meter.charge(1);
        if (body.get("cleanup") != null) throw new IOException("METADATA_CLEANUP_SLOT");

        Map<String,Object> capture = cleanupView(meter, body, "originalCapture");
        Map<String,Object> arms = cleanupView(meter, body, "arms");
        Map<String,Object> nullArm = cleanupView(meter, arms, "null");
        Map<String,Object> nullCleanup = cleanupView(meter, nullArm, "cleanup");
        Map<String,Object> observeArm = cleanupView(meter, arms, "observe");
        Map<String,Object> observeCleanup = cleanupView(meter, observeArm, "cleanup");
        Map<String,Object> cleanup = CurrentResultProjections.cleanup(meter, capture,
                nullCleanup, observeCleanup, cleanupNanos);

        // The sole mutation follows complete projection and replacement admission.
        meter.copy(1);
        meter.charge(1);
        body.put("cleanup", cleanup);
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> cleanupView(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<String,Object> parent, String key) throws Exception {
        meter.charge(1);
        Object value = parent.get(key);
        if (!(value instanceof Map)) throw new IOException("METADATA_CLEANUP_VIEW");
        return (Map<String,Object>)value;
    }

    static ReceiptContentBinding prepare(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            long started,
            CohortTerminalTelemetryStudy.SetupResult setup,
            String currentContextSha,
            Path receipt,
            long expectedBytes,
            String expectedSha,
            Map<String,Object> body) throws Exception {
        // Basic argument null guards precede every source observation.
        if (meter == null || setup == null || currentContextSha == null ||
                receipt == null || expectedSha == null || body == null)
            throw new IOException("METADATA_ARGUMENTS");

        // Four bounded operations; reject before input or receipt work.
        meter.charge(1);
        if (body.getClass() != LinkedHashMap.class)
            throw new IOException("METADATA_BODY_SLOT");
        meter.charge(1);
        boolean hasSlot = body.containsKey("qualifiedPrior");
        meter.charge(1);
        Object previous = body.get("qualifiedPrior");
        meter.charge(1);
        if (!hasSlot || previous != null)
            throw new IOException("METADATA_BODY_SLOT");

        // One length control and 64 charged lowercase-hex controls.
        meter.charge(1);
        if (currentContextSha.length() != 64)
            throw new IOException("METADATA_CONTEXT_SHA");
        for (int i = 0; i < 64; i++) {
            meter.charge(1);
            char c = currentContextSha.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f'))
                throw new IOException("METADATA_CONTEXT_SHA");
        }

        // One bounded data observation/type guard, then one actual prior read.
        meter.charge(1);
        Map<String,Object> data = setup.data;
        if (data == null || data.getClass() != LinkedHashMap.class)
            throw new IOException("METADATA_INPUT_DATA");
        meter.charge(1);
        Object priorValue = data.get("prior");
        Map<?,?> prior = priorShape(meter, priorValue);
        validateReceiptTuple(meter, prior, expectedBytes, expectedSha);

        // copy(12) adds 12 actual units and covers all 12 entry stores.
        meter.charge(1);
        meter.copy(12);
        Map<String,Object> qualified = new LinkedHashMap<String,Object>();
        qualified.put("schema", QUALIFIED_PRIOR_SCHEMA);
        qualified.put("inputContextSha256", currentContextSha);
        for (int i = 0; i < 10; i++) {
            String key = priorKey(i);
            meter.charge(1);
            Object value = prior.get(key);
            qualified.put(key, value);
        }

        // Prepay the deferred outer replacement while metadata is open.
        meter.copy(1);
        meter.charge(1);
        ReceiptContentBinding ticket = ReceiptContentBinding.prepare(
                meter, started, receipt, expectedBytes, expectedSha);
        body.put("qualifiedPrior", qualified);
        return ticket;
    }

    /** Compare borrowed receipt metadata before copying or binding a file. */
    static void validateReceiptTuple(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            Map<?,?> prior, long expectedBytes, String expectedSha) throws Exception {
        if (meter == null) throw new IOException("METADATA_ARGUMENTS");
        meter.charge(1);
        if (prior == null) throw new IOException("METADATA_RECEIPT_PRIOR");
        meter.charge(1);
        Object rawReceipt = prior.get("priorOriginalReceipt");
        meter.charge(1);
        if (rawReceipt == null || rawReceipt.getClass() != LinkedHashMap.class)
            throw new IOException("METADATA_RECEIPT_MAP");
        Map<?,?> receipt = (Map<?,?>)rawReceipt;
        meter.charge(1);
        Object path = receipt.get("receiptPath");
        meter.charge(1);
        if (!"op-1.jsonl".equals(path))
            throw new IOException("METADATA_RECEIPT_PATH");
        meter.charge(1);
        Object rawBytes = receipt.get("receiptBytes");
        meter.charge(1);
        if (!(rawBytes instanceof Integer))
            throw new IOException("METADATA_RECEIPT_BYTES");
        meter.charge(1);
        int bytes = ((Integer)rawBytes).intValue();
        meter.charge(1);
        if (bytes < 1 || bytes > 8388608)
            throw new IOException("METADATA_RECEIPT_BYTES");
        meter.charge(1);
        if (bytes != expectedBytes)
            throw new IOException("METADATA_RECEIPT_BYTES");
        meter.charge(1);
        Object rawSha = receipt.get("receiptSha256");
        meter.charge(1);
        if (!(rawSha instanceof String))
            throw new IOException("METADATA_RECEIPT_SHA");
        String sha = (String)rawSha;
        meter.charge(1);
        int length = sha.length();
        meter.charge(1);
        if (length != 64) throw new IOException("METADATA_RECEIPT_SHA");
        for (int i = 0; i < 64; i++) {
            meter.charge(1);
            char c = sha.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f'))
                throw new IOException("METADATA_RECEIPT_SHA");
        }
        meter.charge(1);
        if (!sha.equals(expectedSha)) throw new IOException("METADATA_RECEIPT_SHA");
        meter.charge(1);
        Object operationSha = prior.get("operation1Sha256");
        meter.charge(1);
        if (!sha.equals(operationSha))
            throw new IOException("METADATA_RECEIPT_OPERATION");
    }

    /** Same 6 + 5n ordered-shape census as the validated input helper. */
    private static Map<?,?> priorShape(
            CohortTerminalTelemetryStudy.SetupMeter meter,
            Object value) throws Exception {
        meter.charge(1);
        if (value == null || value.getClass() != LinkedHashMap.class)
            throw new IOException("METADATA_PRIOR_SHAPE");
        Map<?,?> prior = (Map<?,?>)value;
        meter.charge(1);
        int size = prior.size();
        meter.charge(1);
        if (size != 10) throw new IOException("METADATA_PRIOR_SHAPE");
        meter.charge(1);
        Set<? extends Map.Entry<?,?>> entries = prior.entrySet();
        meter.charge(1);
        Iterator<? extends Map.Entry<?,?>> iterator = entries.iterator();
        int index = 0;
        while (true) {
            meter.charge(1);
            if (!iterator.hasNext()) break;
            meter.charge(1);
            Map.Entry<?,?> entry = iterator.next();
            meter.charge(1);
            Object key = entry.getKey();
            meter.charge(1);
            if (!(key instanceof String))
                throw new IOException("METADATA_PRIOR_SHAPE");
            meter.charge(1);
            if (!priorKey(index).equals(key))
                throw new IOException("METADATA_PRIOR_SHAPE");
            index++;
        }
        return prior;
    }

    /** Fixed application metadata; no allocated key array or input key reads. */
    private static String priorKey(int index) {
        switch (index) {
            case 0: return "sourceParent";
            case 1: return "manifestSha256";
            case 2: return "sourceManifestSha256";
            case 3: return "readerSha256";
            case 4: return "resultSha256";
            case 5: return "operation1Sha256";
            case 6: return "parentReceiptSha256";
            case 7: return "priorRunId";
            case 8: return "priorOriginalReceipt";
            case 9: return "trialDisposition";
            default: throw new AssertionError("prior key selector");
        }
    }
}
