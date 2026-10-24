package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ActiveMeasurementSession {
    private final CirSim sim;
    private final GeneratedBoardInstance owner;
    private final Vector<CircuitElm> graph;
    private final Vector<CircuitElm> elements;
    private final GeneratedExternalPowerBindings bindings;
    private final GeneratedExternalPowerBindings.ControlObservation controls;
    private final PhysicalBoardRuntime runtime;
    private final PhysicalMutationReceipt mutation;
    private final ProbeTarget redProbe;
    private final ProbeTarget blackProbe;
    private final CircuitMeasurementEndpoint redEndpoint;
    private final CircuitMeasurementEndpoint blackEndpoint;
    private boolean closed;

    /** A transaction-local identity guard, never a retained solver or reading cache. */
    ActiveMeasurementSession(CirSim sim, ProbeTarget redProbe, ProbeTarget blackProbe) {
        this.sim = sim;
        owner = sim.getGeneratedBoardInstance();
        graph = sim.elmList;
        elements = new Vector<CircuitElm>(graph);
        bindings = owner == null ? null : owner.getExternalPowerBindings();
        controls = bindings == null ? null : bindings.observeControls();
        runtime = owner == null ? null : owner.getPhysicalBoardRuntime();
        mutation = runtime == null ? null : runtime.getLastMutationReceipt();
        this.redProbe = redProbe;
        this.blackProbe = blackProbe;
        redEndpoint = redProbe.getMeasurementEndpoint();
        blackEndpoint = blackProbe.getMeasurementEndpoint();
    }

    boolean hasValidTargets() {
        if (closed || CircuitElm.sim != sim || sim.getGeneratedBoardInstance() != owner ||
                sim.elmList != graph || graph.size() != elements.size() ||
                !sim.getBoardPowerController().isElectricallyUnpowered() ||
                sim.getBoardPowerController().getBindingsForDeveloperVerification() != bindings ||
                redEndpoint == null || blackEndpoint == null ||
                !redProbe.isValid() || !blackProbe.isValid() ||
                !sameEndpoint(redEndpoint, redProbe.getMeasurementEndpoint()) ||
                !sameEndpoint(blackEndpoint, blackProbe.getMeasurementEndpoint())) return false;
        for (int i = 0; i < elements.size(); i++)
            if (graph.get(i) != elements.get(i)) return false;
        return (controls == null || controls.isCurrent()) &&
            (runtime == null || owner.getPhysicalBoardRuntime() == runtime &&
                runtime.getLastMutationReceipt() == mutation &&
                !runtime.isMutationInProgress() && !runtime.isMutationQuarantined());
    }

    CircuitMeasurementEndpoint getRedEndpoint() {
        return redEndpoint;
    }

    CircuitMeasurementEndpoint getBlackEndpoint() {
        return blackEndpoint;
    }

    void close() {
        closed = true;
    }

    private static boolean sameEndpoint(CircuitMeasurementEndpoint first, CircuitMeasurementEndpoint second) {
        if (first == second) return true;
        if (!(first instanceof CircuitPostMeasurementEndpoint) || !(second instanceof CircuitPostMeasurementEndpoint))
            return false;
        CircuitPostMeasurementEndpoint a = (CircuitPostMeasurementEndpoint)first;
        CircuitPostMeasurementEndpoint b = (CircuitPostMeasurementEndpoint)second;
        return a.getElement() == b.getElement() && a.getPostIndex() == b.getPostIndex();
    }
}
