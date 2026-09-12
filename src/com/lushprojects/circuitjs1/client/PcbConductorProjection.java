package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Vector;

/** Audits physical islands against real CircuitJS post/wire connectivity, not voltages or net names. */
final class PcbConductorProjection {
    static final class Report {
        final int pads, sharedPostAliases, modeledComponentElements;
        Report(int pads, int aliases, int units) {
            this.pads=pads; this.sharedPostAliases=aliases; this.modeledComponentElements=units;
        }
    }
    static Report audit(GeneratedBoardInstance instance, Vector<CircuitElm> active) {
        return audit(instance,active,instance.getCurrentConductorSnapshot());
    }
    static Report audit(GeneratedBoardInstance instance, Vector<CircuitElm> active,
            PcbConductorGraph.Snapshot snapshot) {
        if (snapshot == null) return new Report(0,0,0);
        if (snapshot.getGraph() != instance.getPristineConductorGraph())
            throw new IllegalArgumentException("Foreign copper projection");
        Set<CircuitElm> excluded = new HashSet<CircuitElm>();
        int units=0;
        GeneratedComponentBindings bindings=instance.getComponentBindings();
        bindings.validateElementsAreOwnedBy(instance.getSimulationElements());
        for (String component : instance.getBoard().getComponentIds()) if (bindings.hasComponentBinding(component)) {
            Vector<CircuitElm> primary=bindings.getElements(component);
            units += primary.size(); excluded.addAll(primary);
            excluded.addAll(bindings.getAuxiliaryElements(component));
        }
        for (GeneratedFaultCandidate candidate : instance.getFaultCandidates())
            excluded.addAll(candidate.getPrivateSimulationElements());
        // Detachable leads and external sources are not permanent board copper.
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getAll())
            excluded.add(binding.getConnectionElement());
        Map<Integer,Integer> parent=new HashMap<Integer,Integer>();
        for (CircuitElm element : active) {
            if (excluded.contains(element) || instance.getExternalPowerBindings().isBackingElement(element)) continue;
            if (element instanceof WireElm && element.getPostCount() == 2)
                union(parent,element.getNode(0),element.getNode(1));
            else if (element instanceof GroundElm) union(parent,0,element.getNode(0));
        }
        TreeMap<String,Integer> observed=new TreeMap<String,Integer>();
        List<CircuitMeasurementEndpoint> endpoints=new ArrayList<CircuitMeasurementEndpoint>();
        int aliases=0;
        for (String pad : snapshot.getGraph().getTerminalJunctions().keySet()) {
            CircuitMeasurementEndpoint endpoint=instance.getSimulationBindings().getEndpoint(pad);
            if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
                throw new IllegalStateException("Missing CircuitJS copper projection endpoint");
            CircuitPostMeasurementEndpoint post=(CircuitPostMeasurementEndpoint)endpoint;
            CircuitElm element=post.getElement(); int index=post.getPostIndex();
            if (element == null || !active.contains(element) || index < 0 || index >= element.getPostCount())
                throw new IllegalStateException("Foreign or invalid CircuitJS copper projection post");
            observed.put(pad,Integer.valueOf(find(parent,element.getNode(index))));
            for (CircuitMeasurementEndpoint prior : endpoints)
                if (GeneratedComponentConnectionBindings.sameEndpoint(prior,endpoint)) aliases++;
            endpoints.add(endpoint);
        }
        requireObservation(snapshot,observed);
        return new Report(observed.size(),aliases,units);
    }
    /** Independent caller observations are ephemeral equivalence classes, never saved node identities. */
    static void requireObservation(PcbConductorGraph.Snapshot snapshot, Map<String,Integer> observed) {
        List<String> pads=new ArrayList<String>(snapshot.getGraph().getTerminalJunctions().keySet());
        if (observed == null || !observed.keySet().equals(snapshot.getGraph().getTerminalJunctions().keySet()))
            throw new IllegalStateException("Incomplete copper/solver correspondence");
        for (String pad : pads) if (observed.get(pad) == null)
            throw new IllegalStateException("Missing copper/solver observation");
        for (int i=0; i<pads.size(); i++) for (int j=i+1; j<pads.size(); j++) {
            String a=pads.get(i), b=pads.get(j);
            if (snapshot.padsConnected(a,b) != observed.get(a).equals(observed.get(b)))
                throw new IllegalStateException("Physical copper and solver islands disagree: " + a + " / " + b);
        }
    }
    private static int find(Map<Integer,Integer> parents, int node) {
        if (node < 0) throw new IllegalStateException("Unanalyzed CircuitJS copper post");
        Integer key=Integer.valueOf(node), next=parents.get(key);
        if (next == null) { parents.put(key,key); return node; }
        int current=node;
        while (parents.get(Integer.valueOf(current)).intValue() != current)
            current=parents.get(Integer.valueOf(current)).intValue();
        while (node != current) {
            int following=parents.get(Integer.valueOf(node)).intValue();
            parents.put(Integer.valueOf(node),Integer.valueOf(current)); node=following;
        }
        return current;
    }
    private static void union(Map<Integer,Integer> parents, int first, int second) {
        int a=find(parents,first), b=find(parents,second);
        if (a != b) parents.put(Integer.valueOf(Math.max(a,b)),Integer.valueOf(Math.min(a,b)));
    }
    private PcbConductorProjection() { }
}
