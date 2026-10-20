package com.lushprojects.circuitjs1.client;

/** Fixed informed pair; maintained child budget remains unchanged. */
public final class PcbRoutedSlideCohortTrialContractTest {
    public static void main(String[] args) {
        PcbRoutedSlideCohortTrialContractTest.runSeeds(new long[]{-1017L,-1004L},"PCB routed slide cohort trial contracts ");
    }
    static void runSeeds(long[] seeds,String marker) {
        if(seeds.length!=2 || seeds[0]==seeds[1])
            throw new IllegalArgumentException("Two distinct informed roots per JVM");
        for(long seed:seeds)
            PcbRoutedSlideDescentContractTest.observeInformedCohort(seed,"seed"+seed);
        System.out.println("PASS: "+marker+
            "roots=2 rows=2 evaluator=selectedRoutedSlideCohort");
    }
}
