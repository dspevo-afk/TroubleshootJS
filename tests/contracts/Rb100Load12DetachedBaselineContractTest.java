package com.lushprojects.circuitjs1.client;

/** Actual initial LOAD12 branch copied and replayed at its original VIA cap. */
public final class Rb100Load12DetachedBaselineContractTest {
    private Rb100Load12DetachedBaselineContractTest() { }

    public static void main(String[] args) {
        if(args!=null && args.length!=0)
            throw new IllegalArgumentException("Expected no arguments");
        Rb100PhysicalDiagnostics.runLoad12DetachedBaselineContracts();
    }
}
