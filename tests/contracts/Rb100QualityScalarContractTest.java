package com.lushprojects.circuitjs1.client;

/** Actual-generator null/probe pair and scalar-only P07 quality observation for RB100. */
public final class Rb100QualityScalarContractTest {
    private Rb100QualityScalarContractTest() { }

    public static void main(String[] args) {
        if(args!=null && args.length!=0)
            throw new IllegalArgumentException("Expected no arguments");
        Rb100PhysicalDiagnostics.runQualityScalarContracts();
    }
}
