package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** A fuse, or two independent metal connector pins, backed by real CircuitJS elements. */
final class PhysicalServicePart extends FixedPhysicalPart<BasicPhysicalSpecification> {
    private final Vector<CircuitElm> elements;

    PhysicalServicePart(String id, BasicPhysicalSpecification spec, PhysicalNameplate label,
            PhysicalPackage physicalPackage, Vector<CircuitElm> elements,
            PhysicalPartProvenance provenance) {
        super(id, spec, label.forPhysicalPartId(id), physicalPackage,
            terminals(id, physicalPackage, elements), elements, provenance, PhysicalPartRenderProbeProviders.SERVICE, serviceCapabilities());
        this.elements = new Vector<CircuitElm>(elements);
        if (!(primary() instanceof ProtectionFuseElm) &&
                !(elements.size() == 2 && primary() instanceof WireElm && secondary() instanceof WireElm))
            throw new IllegalArgumentException("Unsupported service part backing");
    }

    private static Vector<PhysicalPartCapability> serviceCapabilities() {
        Vector<PhysicalPartCapability> capabilities = new Vector<PhysicalPartCapability>();
        capabilities.add(new LoosePartInspectableCapability());
        return capabilities;
    }
    CircuitElm primary() { return elements.get(0); }
    CircuitElm secondary() { return elements.size() > 1 ? elements.get(1) : null; }
    boolean isConnector() { return elements.size() == 2; }
    public boolean isFaulted() { return primary() instanceof ProtectionFuseElm && ((ProtectionFuseElm)primary()).blown; }
    public PhysicalFailureState getFailureState() {
        boolean failed = isFaulted();
        return new PhysicalFailureState(failed ? PhysicalFailureState.SECONDARY_FAILURE : PhysicalFailureState.HEALTHY, failed);
    }

    private static Vector<PhysicalPartTerminal> terminals(String id, PhysicalPackage pkg,
            Vector<CircuitElm> elements) {
        if (pkg.getTerminalCount() != 2 || elements == null || elements.isEmpty())
            throw new IllegalArgumentException("Missing two-terminal service backing");
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        for (int i = 0; i < 2; i++) result.add(new PhysicalPartTerminal(id,
            pkg.getTerminalIds().get(i), new CircuitPostMeasurementEndpoint(
                elements.size() == 2 ? elements.get(i) : elements.get(0), elements.size() == 2 ? 0 : i)));
        return result;
    }
}
