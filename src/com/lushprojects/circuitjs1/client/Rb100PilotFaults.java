package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Frozen electrical-reference population, without player serviceability authority. */
final class Rb100PilotFaults {
    static final int POPULATION_VERSION = 1;
    enum Kind { RESISTANCE, COIL_OPEN, SHUNT }
    static final class Fault {
        final String id, owner, witness;
        final Kind kind;
        final double value;
        final boolean overload, above;
        final double threshold;
        Fault(String owner, Kind kind, double value, String witness,
                boolean overload, boolean above, double threshold) {
            this.owner = owner; this.kind = kind; this.value = value;
            this.witness = witness; this.overload = overload; this.above = above;
            this.threshold = threshold;
            id = owner + "_" + kind.name();
        }
        String canonical() {
            return id + ":" + Double.toString(value) + ":" + witness + ":" +
                (overload ? "OVERLOAD" : "HIGH") + ":" + (above ? ">" : "<") + threshold;
        }
    }
    private Rb100PilotFaults() { }
    static Vector<Fault> population() {
        Vector<Fault> rows = new Vector<Fault>();
        resistance(rows, "RENAC", 1e9, "RAIL12", false, false, 1);
        resistance(rows, "REN", 1e9, "RAIL5", false, false, 1);
        resistance(rows, "RSA", 1e9, "OUT_A", false, false, .5);
        resistance(rows, "RDA", 1e9, "OUT_A", false, false, .5);
        coil(rows, "KB", "OUT_B");
        resistance(rows, "RSC", 1e9, "OUT_C", false, false, .5);
        resistance(rows, "RSD", 1e9, "OUT_D", false, false, .5);
        resistance(rows, "RDC", 1e9, "OUT_C", false, false, .5);
        resistance(rows, "RDD", 1e9, "OUT_D", false, false, .5);
        coil(rows, "KC", "OUT_C"); coil(rows, "KD", "OUT_D");
        resistance(rows, "RDE", 1e9, "OUT_E", false, false, .5);
        resistance(rows, "RDF", 1e9, "OUT_F", false, false, .5);
        resistance(rows, "RSH_E", 1e9, "OUT_E", false, false, .5);
        resistance(rows, "RSH_F", 1e9, "OUT_F", false, false, .5);
        shunt(rows, "DE", "OUT_E", false, false, .5);
        shunt(rows, "DF", "OUT_F", false, false, .5);
        shunt(rows, "CSTORE_E", "OUT_E", false, false, .5);
        shunt(rows, "CSTORE_F", "OUT_F", false, false, .5);
        shunt(rows, "C33", "RAIL3V3", false, false, 1);
        shunt(rows, "CIN33", "RAIL5", false, false, 1);
        resistance(rows, "RMON", 10, "OUT_C", false, false, .5);
        resistance(rows, "RMON_H", 1e9, "OUT_C", true, true, 9);
        resistance(rows, "RMON_L", 1e9, "OUT_C", true, true, 9);
        shunt(rows, "CMON", "OUT_C", true, true, 9);
        resistance(rows, "RBAUX", 1e9, "OUT_C", true, true, 9);
        resistance(rows, "RPUAUX", 10, "AUX_GATE", true, true, 1);
        shunt(rows, "QAUX", "OUT_C", true, true, 9);
        shunt(rows, "CFLT_C", "OUT_C", false, false, .5);
        shunt(rows, "CFLT_D", "OUT_D", false, false, .5);
        resistance(rows, "RMON_IN", 1e9, "OUT_C", true, true, 9);
        return rows;
    }
    static Fault find(String id) {
        for (Fault fault : population()) if (fault.id.equals(id)) return fault;
        throw new IllegalArgumentException("Unknown RB100 electrical-reference fault");
    }
    private static void resistance(Vector<Fault> rows, String owner, double value,
            String witness, boolean overload, boolean above, double threshold) {
        rows.add(new Fault(owner, Kind.RESISTANCE, value, witness, overload, above, threshold));
    }
    private static void coil(Vector<Fault> rows, String owner, String witness) {
        rows.add(new Fault(owner, Kind.COIL_OPEN, 1e9, witness, false, false, .5));
    }
    private static void shunt(Vector<Fault> rows, String owner, String witness,
            boolean overload, boolean above, double threshold) {
        rows.add(new Fault(owner, Kind.SHUNT, .1, witness, overload, above, threshold));
    }
    /** Call before installation: added infrastructure belongs only to this private graph. */
    static GeneratedFaultEffect bind(Rb100ElectricalPilot.Candidate candidate, Fault fault) {
        if (candidate == null || fault == null) throw new IllegalArgumentException("Missing RB100 fault owner");
        Rb100Plan.Part declaration = candidate.plan.part(fault.owner);
        CircuitElm actual = candidate.backing.get(fault.owner);
        if (actual == null || !candidate.elements().contains(actual))
            throw new IllegalArgumentException("Foreign RB100 fault backing");
        if (fault.kind == Kind.RESISTANCE) {
            if (!(actual instanceof ResistorElm) || ((ResistorElm)actual).getResistance() != declaration.value)
                throw new IllegalArgumentException("RB100 resistor declaration mismatch");
            return new ResistorIncorrectValueFaultEffect((ResistorElm)actual, declaration.value, fault.value);
        }
        if (fault.kind == Kind.COIL_OPEN) {
            if (!(actual instanceof ServiceRelayElm)) throw new IllegalArgumentException("RB100 coil owner mismatch");
            return new RelayFaultEffect((ServiceRelayElm)actual, true);
        }
        if (!(actual instanceof DiodeElm) && !(actual instanceof CapacitorElm) && !"QAUX".equals(fault.owner))
            throw new IllegalArgumentException("Unsupported RB100 reference shunt owner");
        int first = "QAUX".equals(fault.owner) ? 2 : 0;
        int second = 1;
        Point a = actual.getPost(first), b = actual.getPost(second);
        ResistorElm shunt = new ResistorElm(a.x, a.y);
        shunt.x2 = b.x; shunt.y2 = b.y; shunt.setPoints(); shunt.setResistance(1e12);
        candidate.assembly.elements.add(shunt);
        // The existing bounded live-value effect owns the test shunt, not a
        // claimed physical replacement or player mutation of the original part.
        return new ResistorIncorrectValueFaultEffect(shunt, 1e12, .1);
    }
}
