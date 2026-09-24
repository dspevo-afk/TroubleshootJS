package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

/** Independent registration and catalog canary for both Q30 relay channels. */
public final class Q30RelayServiceContractTest {
    private static int assertions;

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        for (long seed : new long[] { 0L, 1L, Long.MIN_VALUE,
                Long.MAX_VALUE, 9007199254740993L }) {
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(
                Rb30Plan.resolve(seed));
            Rb30TopologyValidator.require(candidate);
            Rb30RelayService ka = candidate.relayServiceA;
            Rb30RelayService kb = candidate.relayServiceB;
            check(ka != null && kb != null);
            check(ka != kb);
            check(candidate.runtime.getCapability(ka.getCapabilityId()) == ka);
            check(candidate.runtime.getCapability(kb.getCapabilityId()) == kb);
            check(!ka.getCapabilityId().equals(kb.getCapabilityId()));
            check(candidate.runtime.getCapability(ReplaceableRelayCapability.ID) == null);
            check(candidate.runtime.getScopedMutationCapability("KA") == ka);
            check(candidate.runtime.getScopedMutationCapability("KB") == kb);
            check(ka.getMutationInventory() != kb.getMutationInventory());
            check(ka.getMutationSlot() != kb.getMutationSlot());
            check(ka.getMutationSlot().getInstalledPart() ==
                candidate.runtime.getInstalledPart("KA"));
            check(kb.getMutationSlot().getInstalledPart() ==
                candidate.runtime.getInstalledPart("KB"));
            Vector<WorkbenchCatalogEntry> kaEntries = ka.getCatalogEntries();
            Vector<WorkbenchCatalogEntry> kbEntries = kb.getCatalogEntries();
            check(kaEntries.size() == 2 && kbEntries.size() == 2);
            Set<String> catalogIds = new HashSet<String>();
            for (WorkbenchCatalogEntry entry : kaEntries)
                check(catalogIds.add(entry.getId()));
            for (WorkbenchCatalogEntry entry : kbEntries)
                check(catalogIds.add(entry.getId()));
            check(catalogIds.size() == 4);
            check(kaEntries.get(0).getId().equals(ka.getFiveVoltCatalogId()));
            check(kaEntries.get(1).getId().equals(ka.getTwelveVoltCatalogId()));
            check(kbEntries.get(0).getId().equals(kb.getFiveVoltCatalogId()));
            check(kbEntries.get(1).getId().equals(kb.getTwelveVoltCatalogId()));
            System.out.println("PASS: Q30 relay services seed=" + seed +
                " ids=" + ka.getCapabilityId() + "," + kb.getCapabilityId());
        }
        System.out.println("PASS: Q30 relay service contracts " + assertions +
            " assertions");
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition)
            throw new AssertionError("Q30 relay service assertion " + assertions);
    }
}
