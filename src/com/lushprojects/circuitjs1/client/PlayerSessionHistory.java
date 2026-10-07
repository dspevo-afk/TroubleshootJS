package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Value-only committed player operations. Private qualification never enters this history. */
final class PlayerSessionHistory {
    static final int PAGE_SIZE = 50;

    /** A bounded copy for presentation; the complete journal remains authoritative. */
    static final class Page {
        final int offset, total;
        final Vector<PlayerSessionSave.Operation> operations;
        Page(int offset, int total, Vector<PlayerSessionSave.Operation> operations) {
            this.offset = offset; this.total = total; this.operations = operations;
        }
        int previousOffset() { return offset == 0 ? -1 : offset - PAGE_SIZE; }
        int nextOffset() { return total - offset > PAGE_SIZE ? offset + PAGE_SIZE : -1; }
    }

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
    int size() { return operations.size(); }

    /** -1 selects the latest page. Other offsets name fixed chronological pages. */
    Page page(int offset) {
        int total = operations.size();
        int last = total == 0 ? 0 : ((total - 1) / PAGE_SIZE) * PAGE_SIZE;
        if (offset == -1) offset = last;
        if (offset < 0 || offset > last || offset % PAGE_SIZE != 0)
            throw new IllegalArgumentException("This history page is unavailable.");
        int count = Math.min(PAGE_SIZE, total - offset);
        Vector<PlayerSessionSave.Operation> entries = new Vector<PlayerSessionSave.Operation>(count);
        for (int i = 0; i < count; i++) entries.add(operations.get(offset + i));
        return new Page(offset, total, entries);
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
