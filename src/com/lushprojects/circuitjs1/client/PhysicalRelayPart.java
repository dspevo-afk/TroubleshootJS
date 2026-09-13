package com.lushprojects.circuitjs1.client;
import java.util.Vector;

final class PhysicalRelayPart extends FixedPhysicalPart<RelaySpecification> implements GeneratedFaultOwningPart {
    private final ServiceRelayElm element;
    private final GeneratedFaultBinding fault;
    PhysicalRelayPart(String id,RelaySpecification spec,ServiceRelayElm element,
            GeneratedFaultBinding fault,PhysicalPartProvenance provenance) {
        super(id,spec,new PhysicalNameplate(id,spec.label(),"Markings",spec.label()),
            PhysicalPackages.RELAY_SPDT,terminals(id,element),elements(element),provenance,null,capabilities());
        this.element=element; this.fault=fault;
    }
    ServiceRelayElm getElement() { return element; }
    CircuitMeasurementEndpoint terminal(String id) { return getTerminal(RelaySpecification.terminalIndex(id)).getEndpoint(); }
    public boolean ownsGeneratedFault(GeneratedFaultBinding binding) { return fault!=null && fault==binding; }
    public boolean isFaulted() { return fault!=null && fault.isApplied(); }
    public PhysicalFailureState getFailureState() {
        return new PhysicalFailureState(isFaulted()?PhysicalFailureState.GENERATED_FAULT:PhysicalFailureState.HEALTHY,isFaulted());
    }
    private static Vector<PhysicalPartTerminal> terminals(String id,ServiceRelayElm relay) {
        Vector<PhysicalPartTerminal> out=new Vector<PhysicalPartTerminal>();
        for(int i=0;i<5;i++) out.add(new PhysicalPartTerminal(id,RelaySpecification.TERMINALS[i],
            new CircuitPostMeasurementEndpoint(relay,RelaySpecification.POSTS[i])));
        return out;
    }
    private static Vector<CircuitElm> elements(CircuitElm relay) {
        Vector<CircuitElm> out=new Vector<CircuitElm>(); out.add(relay); return out;
    }
    private static Vector<PhysicalPartCapability> capabilities() {
        Vector<PhysicalPartCapability> out = new Vector<PhysicalPartCapability>();
        out.add(new LoosePartInspectableCapability()); return out;
    }
}
