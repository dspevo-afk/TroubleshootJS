package com.lushprojects.circuitjs1.client;

/** Fixed informed accepted15 structural census; no geometry mutation. */
public final class PcbRoutedPassiveLegCensusTrialContractTest {
    public static void main(String[] args) {
        for(long seed:new long[]{5L,2L})
            PcbRoutedPassiveLegCensusContractTest.observe(seed,"seed"+seed);
        System.out.println("PASS: PCB routed passive leg census trial contracts "+
            "roots=2 rows=2 evaluator=endpointLegCensus");
    }
}
