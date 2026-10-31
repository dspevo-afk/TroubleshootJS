package com.lushprojects.circuitjs1.client;

/** Focused pure-geometry contract for shared isolation-corridor orientation. */
public final class PcbCorridorOrientationContractTest {
    private PcbCorridorOrientationContractTest() { }

    public static void main(String[] args) {
        if (args.length != 0)
            throw new IllegalArgumentException("Expected no arguments");
        Rb100PhysicalDiagnostics.runCorridorOrientationContracts();
    }
}
