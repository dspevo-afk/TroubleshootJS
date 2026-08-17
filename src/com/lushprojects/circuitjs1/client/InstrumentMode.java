package com.lushprojects.circuitjs1.client;

/**
 * Stable value identity for an instrument provider.
 *
 * This is deliberately not an enum.  A provider contributes its own identity
 * and can therefore be registered without changing a central closed type.
 */
final class InstrumentMode {
    private final String id;

    InstrumentMode(String id) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Instrument mode identity is required");
        this.id = id;
    }

    String getId() {
        return id;
    }

    public int hashCode() {
        return id.hashCode();
    }

    public boolean equals(Object other) {
        return other instanceof InstrumentMode && id.equals(((InstrumentMode) other).id);
    }

    public String toString() {
        return id;
    }
}
