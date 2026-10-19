package com.lushprojects.circuitjs1.client;

/** Read-only successful route pool at one fixed informed root. */
public final class PcbSuccessfulRoutePoolSeed2ContractTest {
    public static void main(String[] args) {
        for(int version:new int[]{15,22})
            PcbSuccessfulRoutePoolObserverContractTest.observe(2L,version,"seed2");
        System.out.println("PASS: PCB successful route pool seed2 contracts "+
            "roots=1 pairs=1 rows=2 evaluator=finalizedRoutePool");
    }
}
