package com.lushprojects.circuitjs1.client;

/** Focused native contract for test-only P07 shortcut-query diagnostics. */
public final class P07ShortcutQueryContractTest {
    private P07ShortcutQueryContractTest() { }

    public static void main(String[] args) {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("Expected no arguments");
        P07ShortcutQueryDiagnostics.runContracts();
    }
}
