package com.lushprojects.circuitjs1.client;
import java.util.Vector;

/** The original relay itself retains coil/contact failure through removal and reset. */
final class RelayFaultEffect implements GeneratedFaultEffect {
    private final ServiceRelayElm relay;
    private final boolean coil;
    private final double healthyOhms;
    private boolean applied;
    RelayFaultEffect(ServiceRelayElm relay,boolean coil) {
        this.relay=relay; this.coil=coil; healthyOhms=relay.coilR;
    }
    public void setApplied(boolean value) {
        applied=value;
        if(coil) relay.coilR=value?1e9:healthyOhms;
        else relay.contactOpen=value;
    }
    public boolean isApplied() { return applied; }
    public CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backing,int terminal) {
        if(backing!=relay || terminal<0 || terminal>=5) throw new IllegalArgumentException("Foreign relay fault endpoint");
        return new CircuitPostMeasurementEndpoint(relay,terminal);
    }
    public Vector<CircuitElm> getPrivateSimulationElements() { return new Vector<CircuitElm>(); }
    public CircuitElm getValueMutationTarget() { return relay; }
}
