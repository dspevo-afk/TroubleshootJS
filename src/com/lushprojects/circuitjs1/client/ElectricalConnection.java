package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

/** Device-owned proposal only; constructing this value performs no net merging. */
final class ElectricalConnection {
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
    ElectricalConnection(String id, Collection<PortRef> ports) {
        this.id = ElectricalContractException.id(id, "connection.id");
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
    }
    String getId() { return id; }
    List<PortRef> getPorts() { return ports; }
}
