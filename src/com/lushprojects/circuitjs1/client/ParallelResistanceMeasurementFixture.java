package com.lushprojects.circuitjs1.client;

/** Isolated real-resistor network used to prove active OHM behavior on parallel paths. */
class ParallelResistanceMeasurementFixture {
    private final ResistorElm oneKilohm = new ResistorElm(1120, 768);
    private final ResistorElm tenKilohm = new ResistorElm(1120, 768);

    ParallelResistanceMeasurementFixture() {
        oneKilohm.drag(1200, 768);
        tenKilohm.drag(1200, 768);
        oneKilohm.setResistance(1000);
        tenKilohm.setResistance(10000);
    }

    void install(CirSim sim) {
        sim.elmList.add(oneKilohm);
        sim.elmList.add(tenKilohm);
    }

    void remove(CirSim sim) {
        sim.elmList.remove(oneKilohm);
        sim.elmList.remove(tenKilohm);
    }

    void removeOneKilohm(CirSim sim) { sim.elmList.remove(oneKilohm); }
    void removeTenKilohm(CirSim sim) { sim.elmList.remove(tenKilohm); }
    void restoreOneKilohm(CirSim sim) {
        if (!sim.elmList.contains(oneKilohm)) sim.elmList.add(oneKilohm);
    }
    void restoreTenKilohm(CirSim sim) {
        if (!sim.elmList.contains(tenKilohm)) sim.elmList.add(tenKilohm);
    }

    ResistorElm getOneKilohm() { return oneKilohm; }
    ResistorElm getTenKilohm() { return tenKilohm; }
}
