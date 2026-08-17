package com.lushprojects.circuitjs1.client;

/** Deterministic injection used only by focused developer verification. */
final class QuickPlayFixedRandomSource implements QuickPlayRandomSource {
    private final long[] values;
    private int index;

    QuickPlayFixedRandomSource(long[] values) {
        if (values == null || values.length == 0)
            throw new IllegalArgumentException("Missing Quick Play verification values");
        this.values = values;
    }

    public long nextLong() {
        if (index >= values.length)
            throw new IllegalStateException("Quick Play verification source was exhausted");
        return values[index++];
    }
}
