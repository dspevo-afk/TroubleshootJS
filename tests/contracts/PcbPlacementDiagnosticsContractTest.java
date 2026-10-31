package com.lushprojects.circuitjs1.client;

/** Focused current-input placement observation and witness controls. */
public final class PcbPlacementDiagnosticsContractTest {
    private PcbPlacementDiagnosticsContractTest() { }

    public static void main(String[] args) {
        if (args.length != 0)
            throw new IllegalArgumentException("Expected no arguments");
        Rb100PhysicalDiagnostics.runPlacementDiagnosticContracts();
    }
}
