package com.lushprojects.circuitjs1.client;

/** Actual initial LOAD12 branch copied and replayed at its original VIA cap. */
public final class Rb100Load12DetachedBaselineContractTest {
    private Rb100Load12DetachedBaselineContractTest() { }

    public static void main(String[] args) {
        if(args==null || args.length==0) {
            Rb100PhysicalDiagnostics.runLoad12DetachedBaselineContracts();
            return;
        }
        if(args.length==1 && "--cap17".equals(args[0])) {
            Rb100PhysicalDiagnostics.runLoad12DetachedCap17Contracts();
            return;
        }
        if(args.length==1 && "--cost240".equals(args[0])) {
            Rb100PhysicalDiagnostics.runLoad12DetachedCost240Contracts();
            return;
        }
        if(args.length==1 && "--cost240-remainder".equals(args[0])) {
            Rb100PhysicalDiagnostics.runLoad12DetachedCost240RemainderContracts();
            return;
        }
        throw new IllegalArgumentException(
            "Expected no arguments, exactly --cap17, --cost240, or --cost240-remainder");
    }
}
