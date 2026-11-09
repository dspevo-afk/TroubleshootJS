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
        return CurrentResultProjections.cohort(meter,
                CohortTerminalTelemetryStudy.deriveCohort(view, actualCapturedIds, meter));
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
