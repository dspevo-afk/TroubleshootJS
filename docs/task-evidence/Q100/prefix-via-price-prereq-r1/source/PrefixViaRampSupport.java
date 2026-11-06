package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Private native-only support for the frozen-prefix via-price experiment. */
final class PrefixViaRampSupport {
    private static final String MODE = "__MODE__";
    private static final int MAX_ROWS = 4096;
    private static final int MAX_LINE_BYTES = 64 * 1024;
    private static final int MAX_CAPTURE_BYTES = 8 * 1024 * 1024;
    private static final ArrayList<String> rows = new ArrayList<String>();
    private static int captureBytes;
    private static boolean observing;

    private PrefixViaRampSupport() { }

    static String mode() { return MODE; }

    /** Pure integer model; H is clamped to M and multiplication happens in long. */
    static int price(int base, int prefixHoleCount, int maximumVias) {
        requirePriceInputs(base, prefixHoleCount, maximumVias);
        if (maximumVias == 0) return base;
        int occupied = Math.min(prefixHoleCount, maximumVias);
        long surcharge = ((long)base * (long)occupied) / (long)maximumVias;
        long result = (long)base + surcharge;
        if (result > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Prefix VIA price exceeds signed int range");
        return (int)result;
    }

    /** Only normal FULLER base-60 branches take the compile-time RAMP treatment. */
    static int effectivePrice(int base, int prefixHoleCount, int maximumVias) {
        requirePriceInputs(base, prefixHoleCount, maximumVias);
        return "RAMP".equals(MODE) && base == 60 ?
            price(base, prefixHoleCount, maximumVias) : base;
    }

    private static void requirePriceInputs(int base, int prefixHoleCount, int maximumVias) {
        if (base < 0 || prefixHoleCount < 0 || maximumVias < 0)
            throw new IllegalArgumentException("Negative frozen-prefix VIA price input");
    }

    static synchronized void startObservation() {
        if (observing || !rows.isEmpty() || captureBytes != 0)
            throw new IllegalStateException("Prefix VIA price observations must be cleared before start");
        observing = true;
    }

    static synchronized int stopObservation() {
        if (!observing)
            throw new IllegalStateException("Prefix VIA price observations are not active");
        observing = false;
        return rows.size();
    }

    static synchronized String observationAt(int index) {
        if (observing)
            throw new IllegalStateException("Prefix VIA price observations must be stopped before reading");
        if (index < 0 || index >= rows.size())
            throw new IndexOutOfBoundsException("Prefix VIA price observation index is outside the captured rows");
        return rows.get(index);
    }

    static synchronized void clearObservation() {
        if (observing)
            throw new IllegalStateException("Cannot clear active Prefix VIA price observations");
        rows.clear();
        captureBytes = 0;
    }

    /** Called only by the actual BranchSearch constructors in the external overlay. */
    static synchronized void recordBranch(String netId, String startPadId, String rootPadId,
            int pass, int prefixHoleCount, int maximumVias, int baseViaCost,
            int branchViaPrice, boolean copy) {
        if (!observing) return;
        if (netId == null || startPadId == null || rootPadId == null ||
                netId.length() == 0 || startPadId.length() == 0 || rootPadId.length() == 0 ||
                pass < 0 || pass >= PcbLayerRoutingPrototype.ORDERINGS ||
                prefixHoleCount < 0 || maximumVias < 0 || prefixHoleCount > maximumVias ||
                baseViaCost < 0)
            throw new IllegalArgumentException("Invalid actual BranchSearch VIA-price observation");
        int expected = effectivePrice(baseViaCost,prefixHoleCount,maximumVias);
        if (branchViaPrice != expected)
            throw new IllegalStateException("Actual BranchSearch VIA price disagrees with its frozen owner");
        if (rows.size() >= MAX_ROWS)
            throw new IllegalStateException("Prefix VIA price observation row cap exceeded");
        int ordinal = rows.size() + 1;
        String row = "{\"schema\":\"tsj-prefix-via-ramp-price-v1\",\"mode\":" +
            jsonString(MODE) + ",\"ordinal\":" + ordinal + ",\"netId\":" +
            jsonString(netId) + ",\"startPadId\":" + jsonString(startPadId) +
            ",\"rootPadId\":" + jsonString(rootPadId) + ",\"pass\":" + pass +
            ",\"prefixHoles\":" + prefixHoleCount + ",\"maximumVias\":" + maximumVias +
            ",\"baseViaCost\":" + baseViaCost + ",\"branchViaPrice\":" + branchViaPrice +
            ",\"copy\":" + copy + "}";
        int bytes = row.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_LINE_BYTES)
            throw new IllegalStateException("Prefix VIA price observation line exceeds 64 KiB");
        if ((long)captureBytes + (long)bytes > MAX_CAPTURE_BYTES)
            throw new IllegalStateException("Prefix VIA price observation capture exceeds 8 MiB");
        rows.add(row);
        captureBytes += bytes;
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
            case '"': out.append("\\\""); break;
            case '\\': out.append("\\\\"); break;
            case '\b': out.append("\\b"); break;
            case '\f': out.append("\\f"); break;
            case '\n': out.append("\\n"); break;
            case '\r': out.append("\\r"); break;
            case '\t': out.append("\\t"); break;
            default:
                if (ch < 0x20) {
                    out.append("\\u00");
                    out.append(hex((ch >>> 4) & 15));
                    out.append(hex(ch & 15));
                } else if (Character.isHighSurrogate(ch)) {
                    if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1)))
                        throw new IllegalArgumentException("Unpaired high surrogate in actual identity");
                    out.append(ch);
                    out.append(value.charAt(++i));
                } else if (Character.isLowSurrogate(ch)) {
                    throw new IllegalArgumentException("Unpaired low surrogate in actual identity");
                } else {
                    out.append(ch);
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static char hex(int value) {
        return "0123456789abcdef".charAt(value & 15);
    }
}