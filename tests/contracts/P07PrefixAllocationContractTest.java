package com.lushprojects.circuitjs1.client;

/** Focused native source contract for exact-prefix P07 hole allocation diagnostics. */
public final class P07PrefixAllocationContractTest {
    private P07PrefixAllocationContractTest() { }

    public static void main(String[] args) {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("Expected no arguments");
        P07PrefixAllocationDiagnostics.runContracts();
    }
}
