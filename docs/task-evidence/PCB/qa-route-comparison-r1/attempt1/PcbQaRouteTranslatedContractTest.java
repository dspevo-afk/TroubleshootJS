package com.lushprojects.circuitjs1.client;

/** Existing-origin QA translation measurement through the supplied-plan P07 route. */
public final class PcbQaRouteTranslatedContractTest {
    private PcbQaRouteTranslatedContractTest() { }

    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("No arguments are accepted");
        PcbQaRouteComparisonContractTest.run(PcbQaRouteComparisonContractTest.TRANSLATED);
    }
}
