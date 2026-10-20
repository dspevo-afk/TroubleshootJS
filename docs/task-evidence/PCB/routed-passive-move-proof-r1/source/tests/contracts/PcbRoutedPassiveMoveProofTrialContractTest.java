package com.lushprojects.circuitjs1.client;

/** One private candidate after an existing success; original selected output stays exact. */
public final class PcbRoutedPassiveMoveProofTrialContractTest {
    public static void main(String[] args) {
        for (long seed : new long[] {5L, 2L})
            PcbRoutedPassiveMoveProofContractTest.observe(seed, "seed" + seed);
        System.out.println("PASS: PCB routed passive move proof trial contracts " +
            "roots=2 rows=2 evaluator=singlePrivateMove");
    }
}
