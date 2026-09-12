package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class BoardNet {
    enum RoutingRole {
        RETURN(0), SUPPLY(1), HIGH_CURRENT(2), CONTROL(3), SIGNAL(4);
        final int priority;
        RoutingRole(int priority) { this.priority=priority; }
    }
    private final String id;
    private final RoutingRole routingRole;
    private final Vector<String> padIds = new Vector<String>();

    BoardNet(String id) {
        this(id,RoutingRole.SIGNAL);
    }
    BoardNet(String id,RoutingRole role) {
        if (role==null) throw new IllegalArgumentException("Missing typed net role");
        this.id=id; this.routingRole=role;
    }
    RoutingRole getRoutingRole() { return routingRole; }

    String getId() {
        return id;
    }

    Vector<String> getPadIds() {
        return new Vector<String>(padIds);
    }

    void addPadId(String padId) {
        if (padIds.contains(padId))
            throw new IllegalArgumentException("Duplicate net pad ID: " + padId);
        padIds.add(padId);
    }
}
