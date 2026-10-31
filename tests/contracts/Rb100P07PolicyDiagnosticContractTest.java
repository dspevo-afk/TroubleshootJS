package com.lushprojects.circuitjs1.client;

/** Thin native entry for the private MEDIUM_BOARD/P07 policy observation. */
public final class Rb100P07PolicyDiagnosticContractTest {
    private Rb100P07PolicyDiagnosticContractTest() { }

    public static void main(String[] args) {
        if (args.length != 0)
            throw new IllegalArgumentException("Expected no arguments");
        Rb100PhysicalDiagnostics.runP07PolicyDiagnosticContracts();
    }
}
