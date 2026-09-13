package com.lushprojects.circuitjs1.client;
import java.util.Vector;

/** Qualified single-pole low-voltage relay envelope; no arcing or mains claim. */
final class RelaySpecification implements PhysicalSpecification {
    static final String[] TERMINALS = { "A1", "A2", "COM", "NC", "NO" };
    static final int[] POSTS = { 3, 4, 0, 1, 2 };
    final double nominalVolts, coilOhms;
    RelaySpecification(double volts) {
        if (volts != 5 && volts != 12) throw new IllegalArgumentException("Unsupported relay coil");
        nominalVolts = volts; coilOhms = volts == 5 ? 125 : 720;
    }
    public String getSpecificationId() { return "RELAY_SPDT_" + (int)nominalVolts + "V"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    String label() { return (int)nominalVolts + " V coil / SPDT / 24 V DC, 0.5 A contacts"; }
    ServiceRelayElm create(int x,int y) {
        ServiceRelayElm relay = new ServiceRelayElm(x,y);
        relay.coilR = coilOhms; relay.inductance = .2;
        relay.ind.setup(.2,0,Inductor.FLAG_BACK_EULER);
        relay.onCurrent = .65 * nominalVolts / coilOhms;
        relay.r_on = .2; relay.r_off = 1e9;
        relay.drag(x+96,y); return relay;
    }
    static int terminalIndex(String id) {
        for (int i=0;i<TERMINALS.length;i++) if(TERMINALS[i].equals(id)) return i;
        throw new IllegalArgumentException("Unknown relay terminal: " + id);
    }
}
