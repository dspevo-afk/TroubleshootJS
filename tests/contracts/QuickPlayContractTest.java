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
        verifyIndicatorLayoutDiversity();
        System.out.println("PASS: Quick Play current seed envelopes and construction");
    }

    private static void verifyIndicatorLayoutDiversity() {
        java.util.HashSet<String> arrangements = new java.util.HashSet<String>();
        for (long seed : new long[] {0, 2, 3, 4}) {
            GeneratedBoardInstance board = QuickPlayFamilyRegistry.generate(
                QuickPlayFamilyRegistry.LED_INDICATOR, seed);
            PcbBoardLayout layout = board.getPcbLayout();
            java.util.Vector<String> ids = board.getBoard().getComponentIds();
            java.util.Collections.sort(ids);
            PcbComponentPlacement origin = layout.getComponent(ids.firstElement());
            StringBuilder fingerprint = new StringBuilder();
            for (String id : ids) {
                PcbComponentPlacement placement = layout.getComponent(id);
                fingerprint.append(id).append('@')
                    .append(placement.getX() - origin.getX()).append(',')
                    .append(placement.getY() - origin.getY()).append(';');
            }
            arrangements.add(fingerprint.toString());
        }
        if (arrangements.size() < 3)
            throw new AssertionError("Indicator Quick Play seeds collapse to the same relative PCB layout: " + arrangements);
    }
}
