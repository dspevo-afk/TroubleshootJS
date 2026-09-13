package com.lushprojects.circuitjs1.client;

/** Native registry/construction canaries. Compiled session/solver qualification is separate. */
public final class QuickPlayContractTest {
    public static void main(String[] args) throws Exception {
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        for (String name : new String[] {"verifyEligibleFamilies", "verifyDeterministicFamilySelection",
                "verifySelectionEnvelopes", "verifyNaturalLedSeedEnvelope", "verifyNaturalNpnSeedEnvelope", "verifyNaturalNmosSeedEnvelope"}) {
            java.lang.reflect.Method method = QuickPlayDeveloperVerifier.class.getDeclaredMethod(name);
            method.setAccessible(true); long started = System.nanoTime(); method.invoke(null);
            System.out.println("QUICKPLAY_NATIVE method="+name+" millis="+(System.nanoTime()-started)/1000000);
        }
        System.out.println("PASS: Quick Play current seed envelopes and construction");
    }
}
