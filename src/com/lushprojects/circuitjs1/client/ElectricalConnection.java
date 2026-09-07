package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/** Device-owned proposal only; constructing this value performs no net merging. */
final class ElectricalConnection {
    /** Conductive is the source-compatible default for all existing callers. */
    enum Kind { CONDUCTIVE, LOW_SIDE_SWITCHED }
    static final class PortRef {
        private final String blockKey, portId;
        PortRef(String blockKey, String portId) {
            this.blockKey = ElectricalContractException.id(blockKey, "connection.block");
            this.portId = ElectricalContractException.id(portId, "connection.port");
        }
        String getBlockKey() { return blockKey; }
        String getPortId() { return portId; }
        String key() { return blockKey + "/" + portId; }
    }
    private final String id;
    private final List<PortRef> ports;
    private final Kind kind;
    private final SwitchedLowSideContract switchedLowSideContract;
    ElectricalConnection(String id, Collection<PortRef> ports) {
        this(id, ports, Kind.CONDUCTIVE, null);
    }
    ElectricalConnection(String id, Collection<PortRef> ports, Kind kind) {
        this(id, ports, kind, null);
    }
    ElectricalConnection(String id, Collection<PortRef> ports,
            SwitchedLowSideContract switchedLowSideContract) {
        this(id, ports, Kind.LOW_SIDE_SWITCHED, switchedLowSideContract);
    }
    ElectricalConnection(String id, Collection<PortRef> ports, Kind kind,
            SwitchedLowSideContract switchedLowSideContract) {
        this.id = ElectricalContractException.id(id, "connection.id");
        if (kind == null)
            throw new ElectricalContractException(ElectricalContractException.Code.MISSING_FIELD,
                    "connection.kind", id, "Connection kind is required");
        if (kind == Kind.LOW_SIDE_SWITCHED && switchedLowSideContract == null)
            throw new ElectricalContractException(ElectricalContractException.Code.MISSING_FIELD,
                    "connection.switchedLowSide", id, "Typed switched relation is required");
        if (kind == Kind.CONDUCTIVE && switchedLowSideContract != null)
            throw new ElectricalContractException(ElectricalContractException.Code.CONTRADICTORY_FIELD,
                    "connection.switchedLowSide", id, "Conductive connection has a switched relation");
        ElectricalContractException.required(ports, "connection.ports", id);
        TreeMap<String, PortRef> sorted = new TreeMap<String, PortRef>();
        for (PortRef port : ports) {
            ElectricalContractException.required(port, "connection.ports", id);
            if (sorted.put(port.key(), port) != null)
                throw new ElectricalContractException(ElectricalContractException.Code.DUPLICATE_DECLARATION,
                    "connection.ports", id, "Repeated qualified port");
        }
        if (sorted.size() < 2)
            throw new ElectricalContractException(ElectricalContractException.Code.INVALID_CONNECTION,
                "connection.ports", id, "A connection needs at least two distinct ports");
        this.ports = Collections.unmodifiableList(new ArrayList<PortRef>(sorted.values()));
        if (kind == Kind.LOW_SIDE_SWITCHED && this.ports.size() != 2)
            throw new ElectricalContractException(ElectricalContractException.Code.INVALID_CONNECTION,
                    "connection.ports", id, "A switched relation needs exactly two ports");
        this.kind = kind;
        this.switchedLowSideContract = switchedLowSideContract;
    }
    String getId() { return id; }
    List<PortRef> getPorts() { return ports; }
    Kind getKind() { return kind; }
    Kind getConnectionKind() { return kind; }
    boolean isTypedLowSideSwitch() { return kind == Kind.LOW_SIDE_SWITCHED; }
    SwitchedLowSideContract getSwitchedLowSideContract() {
        return switchedLowSideContract;
    }
}
