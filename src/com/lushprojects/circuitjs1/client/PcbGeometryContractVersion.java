package com.lushprojects.circuitjs1.client;

/** Immutable version of the package-local PCB geometry contract. */
final class PcbGeometryContractVersion {
    /** The geometry contract currently used by production package definitions. */
    static final int CURRENT = 2;
    static final int CURRENT_VALUE = CURRENT;

    private final int value;

    PcbGeometryContractVersion(int value) {
        if (value <= 0)
            throw new IllegalArgumentException("Invalid PCB geometry contract version: " + value);
        this.value = value;
    }

    static PcbGeometryContractVersion current() {
        return new PcbGeometryContractVersion(CURRENT);
    }

    int getValue() { return value; }

    public boolean equals(Object other) {
        return other instanceof PcbGeometryContractVersion &&
            value == ((PcbGeometryContractVersion) other).value;
    }

    public int hashCode() { return value; }

    public String toString() { return String.valueOf(value); }
}
