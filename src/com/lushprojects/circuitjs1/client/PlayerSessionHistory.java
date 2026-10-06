package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Value-only committed player operations. Private qualification never enters this history. */
final class PlayerSessionHistory {
    private final Vector<PlayerSessionSave.Operation> operations = new Vector<PlayerSessionSave.Operation>();
    private boolean active;

    void start() { active = true; }
    void restore(Vector<PlayerSessionSave.Operation> saved) {
        if (active || !operations.isEmpty()) throw new IllegalStateException("History already belongs to a player");
        operations.addAll(saved);
    }
    Vector<PlayerSessionSave.Operation> operations() {
        return new Vector<PlayerSessionSave.Operation>(operations);
    }
    private boolean records(CirSim sim) { return active && !sim.developerVerifierRunning; }

    void record(CirSim sim, String kind, String owner, String argument, String result) {
        if (records(sim)) operations.add(new PlayerSessionSave.Operation(kind, owner, argument, result));
    }

    void committed(CirSim sim, PhysicalMutationIntent intent, PhysicalPart<?> acquired) {
        if (!records(sim)) return;
        String op = intent.getOperation(), component = intent.getComponentId();
        if ("remove".equals(op)) record(sim, "REMOVE", component, intent.getInstalledPart().getId(), "");
        else if ("install".equals(op)) record(sim, "INSTALL", component, intent.getRequestedPart().getId(), "");
        else if ("catalog".equals(op) || "acquire".equals(op))
            record(sim, "catalog".equals(op) ? "CATALOG" : "ACQUIRE", component,
                intent.getCatalogEntryId(), acquired.getId());
        else if ("lead".equals(op)) record(sim, "LEAD", component, intent.getPadId(),
            intent.getModifications().isLeadConnected(component, intent.getPadId()) ? "CONNECTED" : "DISCONNECTED");
        else if ("graph-remove".equals(op)) record(sim, "GRAPH_REMOVE", component, "", "");
        else if ("graph-restore".equals(op)) record(sim, "GRAPH_RESTORE", component, "", "");
        else throw new IllegalStateException("Unsupported committed player operation");
    }

    void sources(CirSim sim, GeneratedBoardInstance owner) {
        if (!records(sim)) return;
        for (String id : owner.getBoard().getPowerInputIds()) {
            ExternalPowerSimulationBinding source = owner.getExternalPowerBindings().getBinding(id);
            LimitedDcSupplyElm supply = source.getLimitedSupply();
            record(sim, "SOURCE", id, source.isConnected() ? "CONNECTED" : "DISCONNECTED",
                supply == null ? "NONE" : Double.toString(supply.getLimitAmps()));
        }
    }
}
