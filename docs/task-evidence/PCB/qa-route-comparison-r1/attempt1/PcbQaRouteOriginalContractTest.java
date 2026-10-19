package com.lushprojects.circuitjs1.client;

/** Exact accepted baseline for the supplied-plan P07 route diagnostic. */
public final class PcbQaRouteOriginalContractTest {
    private PcbQaRouteOriginalContractTest() { }

    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("No arguments are accepted");
        PcbQaRouteComparisonContractTest.run(PcbQaRouteComparisonContractTest.ORIGINAL);
    }
}
