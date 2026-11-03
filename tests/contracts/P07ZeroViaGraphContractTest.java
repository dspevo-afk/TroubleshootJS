package com.lushprojects.circuitjs1.client;

/** Focused native source contract for the two-face P07 zero-via graph diagnostic. */
public final class P07ZeroViaGraphContractTest {
    private P07ZeroViaGraphContractTest() { }

    public static void main(String[] args) {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("Expected no arguments");
        P07ZeroViaGraphDiagnostics.runContracts();
    }
}
