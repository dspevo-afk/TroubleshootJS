package com.lushprojects.circuitjs1.client;

/** Thin native entry point for the fixed-candidate authored P07 branch observation. */
public final class Rb100P07BranchDiagnosticContractTest {
    private Rb100P07BranchDiagnosticContractTest() { }

    public static void main(String[] args) {
        if (args.length != 0)
            throw new IllegalArgumentException("Expected no arguments");
        Rb100PhysicalDiagnostics.runP07BranchDiagnosticContracts();
    }
}
