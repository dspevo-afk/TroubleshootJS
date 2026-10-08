package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** A serviceable package owning all ordinary electrical backing and its actual exposed posts. */
final class PhysicalServicePart extends FixedPhysicalPart<PhysicalSpecification>
        implements PhysicalPartDetachmentReadiness {
    private final Vector<CircuitElm> elements;

    PhysicalServicePart(String id, PhysicalSpecification spec, PhysicalNameplate label,
            PhysicalPackage physicalPackage, Vector<CircuitElm> elements,
            PhysicalPartProvenance provenance) {
        super(id, spec, label.forPhysicalPartId(id), physicalPackage,
            terminals(id, spec, physicalPackage, elements), elements, provenance,
            PhysicalPartRenderProbeProviders.SERVICE, serviceCapabilities());
        this.elements = new Vector<CircuitElm>(elements);
    }

    private static Vector<PhysicalPartCapability> serviceCapabilities() {
        Vector<PhysicalPartCapability> capabilities = new Vector<PhysicalPartCapability>();
        capabilities.add(new LoosePartInspectableCapability());
        return capabilities;
    }
    CircuitElm primary() { return elements.get(0); }
    CircuitElm secondary() { return elements.size() > 1 ? elements.get(1) : null; }
    Vector<CircuitElm> elements() { return new Vector<CircuitElm>(elements); }
    Vector<CircuitElm> auxiliaryElements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        for (int i = 1; i < elements.size(); i++) result.add(elements.get(i));
        return result;
    }
    boolean isConnector() { return isWirePair(elements); }
    boolean isFactoryLink() { return getSpecification() instanceof FactoryLinkSpecification; }
    boolean hasTypedPowerRecipe() {
        return getSpecification() instanceof InductorSpecification || getSpecification() instanceof ZenerSpecification ||
            getSpecification() instanceof OptocouplerSpecification;
    }
    public boolean isDetachmentReady(CirSim sim, GeneratedBoardInstance owner) {
        // Loose parts have no board attachment to cut. Other service recipes retain their behavior.
        if (!(getSpecification() instanceof InductorSpecification) || !isInstalled()) return true;
        if (sim == null || owner == null || CircuitElm.sim != sim ||
                sim.getGeneratedBoardInstance() != owner || sim.elmList == null) return false;
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        PhysicalBoardSlot slot = getBoardSlot();
        if (slot == null || slot.getRuntime() != runtime || runtime.getSlot(slot.getComponentId()) != slot ||
                slot.getInstalledPart() != this || runtime.getPart(getId()) != this ||
                !owner.getComponentBindings().hasComponentBinding(slot.getComponentId()) ||
                owner.getComponentBindings().getElements(slot.getComponentId()).size() != 1 ||
                owner.getComponentBindings().getSingleElement(slot.getComponentId()) != primary() ||
                !owner.getComponentBindings().getAuxiliaryElements(slot.getComponentId()).equals(auxiliaryElements()))
            return false;
        for (CircuitElm element : elements)
            if (!owner.ownsRuntimeSimulationElement(element) || !sim.elmList.contains(element)) return false;
        // This observation rejects stale owner/graph/time, changed source controls and trial-only state.
        if (sim.solverExecutor.observation(owner) == null) return false;
        InductorElm winding = (InductorElm)primary();
        return winding.ind != null && PowerDomainContract.finite(winding.current) &&
            PowerDomainContract.finite(winding.ind.current) &&
            Math.abs(winding.current) < InductorSpecification.DETACH_CURRENT_LIMIT_AMPS &&
            Math.abs(winding.ind.current) < InductorSpecification.DETACH_CURRENT_LIMIT_AMPS;
    }
    public boolean isFaulted() { return primary() instanceof ProtectionFuseElm && ((ProtectionFuseElm)primary()).blown; }
    public PhysicalFailureState getFailureState() {
        boolean failed = isFaulted();
        return new PhysicalFailureState(failed ? PhysicalFailureState.SECONDARY_FAILURE : PhysicalFailureState.HEALTHY, failed);
    }

    /** Allocate one bundle, then translate its complete actual posts away from all retained graph endpoints. */
    Vector<CircuitElm> createCatalogBacking(Vector<CircuitElm> active) {
        if (!hasTypedPowerRecipe() || active == null)
            throw new IllegalArgumentException("Missing typed power recipe or occupied graph");
        Vector<CircuitElm> backing = createTypedBacking(getSpecification(), 48000, 48000);
        boolean complete = false;
        try {
            for (int trial = 0; trial < 8192; trial++) {
                int dx = trial * 256;
                boolean free = true;
                for (CircuitElm owned : backing) for (int i = 0; i < owned.getPostCount(); i++) {
                    Point post = owned.getPost(i);
                    for (CircuitElm occupied : active) for (int j = 0; j < occupied.getPostCount(); j++) {
                        Point other = occupied.getPost(j);
                        if (other.x == post.x + dx && other.y == post.y) free = false;
                    }
                }
                if (free) {
                    for (CircuitElm owned : backing) if (dx != 0) owned.move(dx, 0);
                    requireBacking(getSpecification(), getPackage(), backing);
                    complete = true; return backing;
                }
            }
            throw new IllegalStateException("No free typed service backing coordinates");
        } finally { if (!complete) for (CircuitElm element : backing) element.delete(); }
    }

    static Vector<CircuitElm> createTypedBacking(PhysicalSpecification spec, int x, int y) {
        if (spec instanceof InductorSpecification) return ((InductorSpecification)spec).createBacking(x, y);
        if (spec instanceof ZenerSpecification) return ((ZenerSpecification)spec).createBacking(x, y);
        if (spec instanceof OptocouplerSpecification) return ((OptocouplerSpecification)spec).createBacking(x, y);
        throw new IllegalArgumentException("Unknown typed power service recipe");
    }

    static void requireBacking(PhysicalSpecification spec, PhysicalPackage pkg, Vector<CircuitElm> elements) {
        if (spec == null || pkg == null || elements == null || elements.isEmpty())
            throw new IllegalArgumentException("Missing physical service recipe or backing");
        if (spec instanceof InductorSpecification || pkg == PhysicalPackages.RADIAL_INDUCTOR_2)
            InductorSpecification.requireBacking(spec, pkg, elements);
        else if (spec instanceof ZenerSpecification || elements != null && !elements.isEmpty() && elements.get(0) instanceof ZenerElm)
            ZenerSpecification.requireBacking(spec, pkg, elements);
        else if (spec instanceof OptocouplerSpecification || pkg == PhysicalPackages.OPTOCOUPLER_4)
            OptocouplerSpecification.requireBacking(spec, pkg, elements);
        else if (spec instanceof FuseSpecification)
            FuseSpecification.requireBacking(spec, pkg, elements);
        else if (spec instanceof FactoryLinkSpecification || pkg.getGeometry().getRaisedCrossover() != null)
            FactoryLinkSpecification.requireBacking(spec, pkg, elements);
        else if (pkg.getTerminalCount() != 2 || elements == null || elements.isEmpty() ||
                !(elements.size() == 1 && elements.get(0) instanceof ProtectionFuseElm) && !isWirePair(elements))
            throw new IllegalArgumentException("Unsupported service part backing");
    }

    private static boolean isWirePair(Vector<CircuitElm> elements) {
        return elements != null && elements.size() == 2 && elements.get(0) instanceof WireElm && elements.get(1) instanceof WireElm;
    }
    private static Vector<PhysicalPartTerminal> terminals(String id, PhysicalSpecification spec, PhysicalPackage pkg,
            Vector<CircuitElm> elements) {
        requireBacking(spec, pkg, elements);
        Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
        boolean connector = isWirePair(elements);
        for (int i = 0; i < pkg.getTerminalCount(); i++) {
            CircuitElm element = connector ? elements.get(i) :
                spec instanceof InductorSpecification && i == 1 ? elements.get(1) : elements.get(0);
            int post = connector ? 0 : spec instanceof InductorSpecification && i == 1 ? 1 : i;
            result.add(new PhysicalPartTerminal(id, pkg.getTerminalIds().get(i),
                new CircuitPostMeasurementEndpoint(element, post)));
        }
        return result;
    }
}
