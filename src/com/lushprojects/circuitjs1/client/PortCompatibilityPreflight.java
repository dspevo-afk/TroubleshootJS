package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessProvision;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.AccessRequirement;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.ActiveLevel;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Behavior;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Direction;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Drive;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Loading;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.MergePolicy;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.Role;
import com.lushprojects.circuitjs1.client.ElectricalPortContract.State;

/** Pure declared-policy v1 preflight. A pass is not a simulated operating proof. */
final class PortCompatibilityPreflight {
    static final String POLICY_VERSION = "TSJ-PORT-PREFLIGHT-1";
    enum Decision { COMPATIBLE, INCOMPATIBLE, INSUFFICIENT_INFORMATION, MALFORMED }
    enum Reason {
        MALFORMED_DESCRIPTOR, INVALID_CONNECTION, DUPLICATE_CONNECTION, UNKNOWN_PORT,
        MERGE_FORBIDDEN, UNKNOWN_MERGE_POLICY, ISOLATION_MERGE_FORBIDDEN,
        UNKNOWN_ISOLATION, RETURN_SIGNAL_MIX, REFERENCE_MISMATCH, REFERENCE_UNPROVEN,
        INVALID_DIRECTION_DRIVE, CONFLICTING_DRIVERS, UNSUPPORTED_DRIVE,
        NO_DECLARED_SOURCE, UNSUPPORTED_ROLE_PAIR, VOLTAGE_RANGE_NOT_CONTAINED,
        REQUIRED_INFORMATION_MISSING, REQUIRED_FIELD_NOT_APPLICABLE, UNKNOWN_LOADING,
        LOAD_CAPACITY_EXCEEDED, DIGITAL_LEVEL_MISMATCH, ACTIVE_LEVEL_MISMATCH,
        ACCESSIBILITY_UNMET, UNKNOWN_ACCESSIBILITY, ADAPTER_BYPASS
    }
    static final class Diagnostic {
        private final Decision decision;
        private final Reason code;
        private final List<String> connectionIds, portIds;
        private final String fieldId;
        private Diagnostic(Decision decision, Reason code, Collection<String> connections,
                Collection<String> ports, String fieldId) {
            this.decision = decision; this.code = code;
            this.connectionIds = sorted(connections); this.portIds = sorted(ports); this.fieldId = fieldId;
        }
        Decision getDecision() { return decision; }
        Reason getCode() { return code; }
        List<String> getConnectionIds() { return connectionIds; }
        List<String> getPortIds() { return portIds; }
        String getFieldId() { return fieldId; }
        private String sortKey() {
            return code.name() + "|" + decision.name() + "|" + fieldId + "|" + connectionIds + "|" + portIds;
        }
    }
    static final class Result {
        private final Decision decision;
        private final List<Diagnostic> diagnostics;
        private Result(List<Diagnostic> issues) {
            TreeMap<String, Diagnostic> canonical = new TreeMap<String, Diagnostic>();
            Decision result = Decision.COMPATIBLE;
            for (Diagnostic issue : issues) {
                canonical.put(issue.sortKey(), issue);
                if (priority(issue.decision) > priority(result)) result = issue.decision;
            }
            this.decision = result;
            this.diagnostics = Collections.unmodifiableList(new ArrayList<Diagnostic>(canonical.values()));
        }
        Decision getDecision() { return decision; }
        List<Diagnostic> getDiagnostics() { return diagnostics; }
    }

    private PortCompatibilityPreflight() { }

    static Result check(String deviceSchemaId, int deviceSchemaVersion,
            Collection<ElectricalBlockContract> blocks, Collection<ElectricalConnection> connections) {
        List<Diagnostic> issues = new ArrayList<Diagnostic>();
        TreeMap<String, ElectricalBlockContract> blockMap = new TreeMap<String, ElectricalBlockContract>();
        TreeMap<String, Node> nodes = new TreeMap<String, Node>();
        BlockNamespace namespace;
        try {
            ElectricalContractException.required(blocks, "blocks", "");
            ElectricalContractException.required(connections, "connections", "");
            List<FunctionalBlockDescriptor> descriptors = new ArrayList<FunctionalBlockDescriptor>();
            for (ElectricalBlockContract block : blocks) {
                ElectricalContractException.required(block, "blocks", "");
                descriptors.add(block.getDescriptor());
                blockMap.put(block.getDescriptor().getInstanceKey(), block);
            }
            namespace = new BlockNamespace(deviceSchemaId, deviceSchemaVersion, descriptors);
            for (ElectricalBlockContract block : blockMap.values())
                for (ElectricalPortContract port : block.getPorts().values()) {
                    Node node = new Node(namespace, block, port);
                    nodes.put(node.key, node);
                }
        } catch (BlockContractException ex) {
            issues.add(new Diagnostic(Decision.MALFORMED, Reason.MALFORMED_DESCRIPTOR,
                Collections.<String>emptyList(), Collections.<String>emptyList(), ex.getFieldId()));
            return new Result(issues);
        } catch (ElectricalContractException ex) {
            issues.add(new Diagnostic(Decision.MALFORMED, Reason.MALFORMED_DESCRIPTOR,
                Collections.<String>emptyList(), Collections.<String>emptyList(), ex.getFieldId()));
            return new Result(issues);
        }

        Union conductors = new Union(), references = new Union(), attemptedReferences = new Union();
        Map<String, String> attachmentOwner = new TreeMap<String, String>();
        for (Node node : nodes.values()) {
            conductors.add(node.key); references.add(node.reference); attemptedReferences.add(node.reference);
            String previous = attachmentOwner.put(node.attachment, node.key);
            if (previous != null) conductors.join(previous, node.key);
        }
        TreeMap<String, ElectricalConnection> proposals = new TreeMap<String, ElectricalConnection>();
        for (ElectricalConnection connection : connections) {
            if (connection == null) {
                issues.add(new Diagnostic(Decision.MALFORMED, Reason.INVALID_CONNECTION,
                    Collections.<String>emptyList(), Collections.<String>emptyList(), "connections"));
                continue;
            }
            if (proposals.put(connection.getId(), connection) != null)
                issues.add(new Diagnostic(Decision.MALFORMED, Reason.DUPLICATE_CONNECTION,
                    Arrays.asList(connection.getId()), Collections.<String>emptyList(), "connection.id"));
            for (ElectricalConnection.PortRef ref : connection.getPorts()) {
                if (!nodes.containsKey(ref.key()))
                    issues.add(new Diagnostic(Decision.MALFORMED, Reason.UNKNOWN_PORT,
                        Arrays.asList(connection.getId()), Arrays.asList(unresolvedPortId(namespace, ref)), "connection.ports"));
            }
        }
        if (!issues.isEmpty()) return new Result(issues);
        for (ElectricalConnection connection : proposals.values()) {
            String first = connection.getPorts().get(0).key();
            for (ElectricalConnection.PortRef ref : connection.getPorts()) conductors.join(first, ref.key());
        }
        TreeMap<String, Group> groups = new TreeMap<String, Group>();
        for (ElectricalConnection connection : proposals.values()) {
            String root = conductors.find(connection.getPorts().get(0).key());
            if (!groups.containsKey(root)) groups.put(root, new Group());
            groups.get(root).connections.add(connection.getId());
        }
        for (Node node : nodes.values()) {
            Group group = groups.get(conductors.find(node.key));
            if (group != null) group.nodes.add(node);
        }

        // Only explicit, permitted RETURN wiring can relate distinct references.
        // Keep attempted joins separately so missing evidence is not misreported
        // as a proven reference mismatch on a dependent signal connection.
        for (Group group : groups.values()) {
            commonChecks(group, issues);
            if (group.onlyReturns()) {
                String first = group.nodes.get(0).reference;
                for (Node node : group.nodes) attemptedReferences.join(first, node.reference);
                int before = issues.size();
                checkReturnDomains(group, issues);
                if (before == issues.size() && !hasIssueFor(group, issues))
                    for (Node node : group.nodes) references.join(first, node.reference);
            }
        }
        for (Group group : groups.values()) {
            if (!group.onlyReturns()) {
                checkReferences(group, references, attemptedReferences, issues);
                if (hasTypedSwitchedConnection(group, proposals))
                    checkSwitchedLowSideGroup(group, proposals, nodes, conductors,
                            references, attemptedReferences, issues);
                else
                    checkSignalGroup(group, issues);
            }
        }
        for (ElectricalBlockContract block : blockMap.values()) {
            for (ElectricalBlockContract.Adapter adapter : block.getAdapters().values()) {
                String prefix = block.getDescriptor().getInstanceKey() + "/";
                String first = prefix + adapter.getInputPortId(), second = prefix + adapter.getOutputPortId();
                if (conductors.find(first).equals(conductors.find(second))) {
                    Group group = groups.get(conductors.find(first));
                    if (group != null) add(group, issues, Decision.INCOMPATIBLE, Reason.ADAPTER_BYPASS,
                        "adapter." + adapter.getId(), Arrays.asList(nodes.get(first).id, nodes.get(second).id));
                }
            }
        }
        return new Result(issues);
    }

    private static void commonChecks(Group group, List<Diagnostic> issues) {
        for (Node node : group.nodes) {
            ElectricalPortContract port = node.port;
            if (port.getMergePolicy() == MergePolicy.FORBID)
                add(group, issues, Decision.INCOMPATIBLE, Reason.MERGE_FORBIDDEN, "mergePolicy", node);
            else if (port.getMergePolicy() == MergePolicy.UNKNOWN)
                add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNKNOWN_MERGE_POLICY, "mergePolicy", node);
            checkAccessibility(group, node, issues);
            if (port.getDrive() == Drive.UNKNOWN) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNSUPPORTED_DRIVE, "drive", node);
                continue;
            }
            boolean shape;
            if (port.getRole() == Role.RETURN) {
                shape = port.getDirection() == Direction.BIDIRECTIONAL && port.getBehavior() == Behavior.PASSIVE &&
                    port.getDrive() == Drive.NONE && port.getLoading() == Loading.NONE;
            } else if (isVoltageDriver(port)) {
                shape = port.getDirection() == Direction.OUTPUT &&
                    (port.getBehavior() == Behavior.SOURCE || port.getBehavior() == Behavior.SOURCE_AND_SINK);
            } else if (port.getDrive() == Drive.OPEN_DRAIN) {
                shape = port.getDirection() == Direction.OUTPUT && port.getBehavior() == Behavior.SINK && port.getRole() != Role.RAIL;
            } else {
                shape = port.getDirection() != Direction.OUTPUT &&
                    (port.getBehavior() == Behavior.SINK || port.getBehavior() == Behavior.PASSIVE);
            }
            if (!shape) add(group, issues, Decision.INCOMPATIBLE, Reason.INVALID_DIRECTION_DRIVE, "direction.drive.behavior", node);
        }
    }

    private static void checkAccessibility(Group group, Node node, List<Diagnostic> issues) {
        AccessRequirement required = node.port.getAccessRequirement();
        AccessProvision provided = node.port.getAccessProvision();
        if (required == AccessRequirement.NONE) return;
        if (provided == AccessProvision.UNKNOWN) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNKNOWN_ACCESSIBILITY, "accessProvision", node);
        } else if (provided == AccessProvision.NOT_APPLICABLE) {
            add(group, issues, Decision.MALFORMED, Reason.REQUIRED_FIELD_NOT_APPLICABLE, "accessProvision", node);
        } else if (provided != AccessProvision.BOTH &&
                !(required == AccessRequirement.PROBEABLE && provided == AccessProvision.PROBEABLE) &&
                !(required == AccessRequirement.CONNECTABLE && provided == AccessProvision.CONNECTABLE)) {
            add(group, issues, Decision.INCOMPATIBLE, Reason.ACCESSIBILITY_UNMET, "accessProvision", node);
        }
    }

    private static void checkReturnDomains(Group group, List<Diagnostic> issues) {
        TreeSet<String> refs = new TreeSet<String>(), isolationIds = new TreeSet<String>();
        boolean unknown = false;
        for (Node node : group.nodes) {
            refs.add(node.reference);
            String isolation = node.port.getDomain().getIsolationId();
            if (isolation == null) unknown = true; else isolationIds.add(isolation);
        }
        if (refs.size() == 1) return;
        if (isolationIds.size() > 1)
            add(group, issues, Decision.INCOMPATIBLE, Reason.ISOLATION_MERGE_FORBIDDEN, "reference.isolation", group.ids());
        else if (unknown)
            add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNKNOWN_ISOLATION, "reference.isolation", group.ids());
    }

    private static void checkReferences(Group group, Union references, Union attemptedReferences, List<Diagnostic> issues) {
        Node first = group.nodes.get(0);
        for (Node node : group.nodes) {
            if (references.find(first.reference).equals(references.find(node.reference))) continue;
            String firstIsolation = first.port.getDomain().getIsolationId(), secondIsolation = node.port.getDomain().getIsolationId();
            if (firstIsolation != null && secondIsolation != null && !firstIsolation.equals(secondIsolation)) {
                add(group, issues, Decision.INCOMPATIBLE, Reason.ISOLATION_MERGE_FORBIDDEN, "reference.isolation", Arrays.asList(first.id, node.id));
            } else if (attemptedReferences.find(first.reference).equals(attemptedReferences.find(node.reference))) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.REFERENCE_UNPROVEN, "reference.net", Arrays.asList(first.id, node.id));
            } else {
                add(group, issues, Decision.INCOMPATIBLE, Reason.REFERENCE_MISMATCH, "reference.net", Arrays.asList(first.id, node.id));
            }
        }
    }

    private static void checkSignalGroup(Group group, List<Diagnostic> issues) {
        List<Node> drivers = new ArrayList<Node>(), sinks = new ArrayList<Node>();
        boolean hasReturn = false, unknownDrive = false;
        for (Node node : group.nodes) {
            hasReturn |= node.port.getRole() == Role.RETURN;
            unknownDrive |= node.port.getDrive() == Drive.UNKNOWN;
            if (isVoltageDriver(node.port)) drivers.add(node);
            if (node.port.getDrive() == Drive.OPEN_DRAIN) sinks.add(node);
        }
        if (hasReturn) {
            add(group, issues, Decision.INCOMPATIBLE, Reason.RETURN_SIGNAL_MIX, "role", group.ids());
            return;
        }
        if (drivers.size() > 1) {
            add(group, issues, Decision.INCOMPATIBLE, Reason.CONFLICTING_DRIVERS, "drive", group.ids());
            return;
        }
        if (!sinks.isEmpty()) {
            boolean railReceiver = false;
            for (Node node : group.nodes)
                railReceiver |= node.port.getRole() == Role.RAIL && node.port.getDirection() == Direction.INPUT;
            if (!drivers.isEmpty() || railReceiver)
                add(group, issues, Decision.INCOMPATIBLE, Reason.INVALID_DIRECTION_DRIVE, "drive.lowSideSink", group.ids());
            else add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNSUPPORTED_DRIVE, "drive.lowSideSink", group.ids());
            return;
        }
        if (drivers.isEmpty()) {
            if (!unknownDrive) add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.NO_DECLARED_SOURCE, "drive", group.ids());
            return;
        }
        Node driver = drivers.get(0);
        boolean voltageKnown = required(group, driver, driver.port.getGuaranteedVoltage().getState(), "guaranteedVoltage", issues);
        boolean capacityKnown = required(group, driver, driver.port.getCapacityAmps().getState(), "capacityAmps", issues);
        double demand = 0;
        boolean demandKnown = true;
        for (Node receiver : group.nodes) {
            if (receiver == driver) continue; // Local traversal identity, never a durable or owner ID.
            if (receiver.port.getDrive() != Drive.NONE) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNSUPPORTED_DRIVE, "drive", receiver);
                continue;
            }
            boolean limitsKnown = required(group, receiver, receiver.port.getAllowedVoltage().getState(), "allowedVoltage", issues);
            if (voltageKnown && limitsKnown && !receiver.port.getAllowedVoltage().contains(driver.port.getGuaranteedVoltage()))
                add(group, issues, Decision.INCOMPATIBLE, Reason.VOLTAGE_RANGE_NOT_CONTAINED, "allowedVoltage", Arrays.asList(driver.id, receiver.id));
            if (receiver.port.getLoading() == Loading.UNKNOWN) {
                demandKnown = false;
                add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNKNOWN_LOADING, "loading", receiver);
            } else if (receiver.port.getLoading() != Loading.BOUNDED_CURRENT) {
                demandKnown = false;
                add(group, issues, Decision.MALFORMED, Reason.REQUIRED_FIELD_NOT_APPLICABLE, "loading", receiver);
            } else if (required(group, receiver, receiver.port.getDemandAmps().getState(), "demandAmps", issues)) {
                demand += receiver.port.getDemandAmps().getValue();
            } else demandKnown = false;
            if (isLogic(receiver.port.getRole())) checkDigital(group, driver, receiver, issues);
            else if (receiver.port.getRole() == Role.ANALOG && isLogic(driver.port.getRole()))
                add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNSUPPORTED_ROLE_PAIR,
                    "role.analog", Arrays.asList(driver.id, receiver.id));
        }
        if (capacityKnown && demandKnown && demand > driver.port.getCapacityAmps().getValue())
            add(group, issues, Decision.INCOMPATIBLE, Reason.LOAD_CAPACITY_EXCEEDED, "capacityAmps.demandAmps", group.ids());
    }

    private static boolean hasTypedSwitchedConnection(Group group,
            Map<String, ElectricalConnection> proposals) {
        for (String id : group.connections) {
            ElectricalConnection connection = proposals.get(id);
            if (connection != null && connection.getKind()
                    == ElectricalConnection.Kind.LOW_SIDE_SWITCHED)
                return true;
        }
        return false;
    }

    /**
     * Validate the explicit low-side relation.  An untyped open-drain net
     * continues through checkSignalGroup and remains fail-closed.
     */
    private static void checkSwitchedLowSideGroup(Group group,
            Map<String, ElectricalConnection> proposals,
            Map<String, Node> nodes, Union conductors, Union references,
            Union attemptedReferences, List<Diagnostic> issues) {
        ElectricalConnection switched = null;
        for (String id : group.connections) {
            ElectricalConnection candidate = proposals.get(id);
            if (candidate != null && candidate.getKind()
                    == ElectricalConnection.Kind.LOW_SIDE_SWITCHED) {
                if (switched != null) {
                    add(group, issues, Decision.INCOMPATIBLE,
                            Reason.CONFLICTING_DRIVERS, "connection.kind", group.ids());
                    return;
                }
                switched = candidate;
            }
        }
        if (switched == null || switched.getSwitchedLowSideContract() == null) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                    Reason.UNSUPPORTED_DRIVE, "connection.kind", group.ids());
            return;
        }
        SwitchedLowSideContract relation = switched.getSwitchedLowSideContract();
        if (!samePortSet(switched.getPorts(), Arrays.asList(
                relation.getSinkPort(), relation.getLoadPort()))) {
            add(group, issues, Decision.MALFORMED, Reason.INVALID_CONNECTION,
                    "connection.switchedLowSide.ports", group.ids());
            return;
        }
        Node sink = nodes.get(relation.getSinkPort().key());
        Node load = nodes.get(relation.getLoadPort().key());
        Node supply = nodes.get(relation.getSupplyPort().key());
        Node control = nodes.get(relation.getControlPort().key());
        Node loadSupply = nodes.get("load/SUPPLY");
        Node driverControl = nodes.get("driver/CONTROL");
        if (sink == null || load == null || supply == null || control == null
                || loadSupply == null || driverControl == null) {
            add(group, issues, Decision.MALFORMED, Reason.UNKNOWN_PORT,
                    "connection.switchedLowSide.references", group.ids());
            return;
        }
        TreeSet<String> expectedSwitched = new TreeSet<String>(Arrays.asList(
                sink.key, load.key));
        TreeSet<String> actualSwitched = new TreeSet<String>();
        for (Node node : group.nodes) actualSwitched.add(node.key);
        if (!expectedSwitched.equals(actualSwitched)) {
            add(group, issues, Decision.INCOMPATIBLE, Reason.INVALID_CONNECTION,
                    "connection.switchedLowSide.transitivePorts", group.ids());
            return;
        }
        if (!"driver".equals(relation.getSinkPort().getBlockKey())
                || !"SWITCHED_SINK".equals(relation.getSinkPort().getPortId())
                || !"load".equals(relation.getLoadPort().getBlockKey())
                || !"SWITCHED_LOAD".equals(relation.getLoadPort().getPortId())) {
            add(group, issues, Decision.INCOMPATIBLE, Reason.UNSUPPORTED_ROLE_PAIR,
                    "connection.switchedLowSide.fixedPorts", group.ids());
            return;
        }
        ElectricalPortContract sinkPort = sink.port, loadPort = load.port;
        if (sinkPort.getRole() != Role.LOAD
                || sinkPort.getDirection() != Direction.OUTPUT
                || sinkPort.getBehavior() != Behavior.SINK
                || sinkPort.getDrive() != Drive.OPEN_DRAIN
                || loadPort.getRole() != Role.LOAD
                || loadPort.getDirection() == Direction.OUTPUT
                || loadPort.getBehavior() != Behavior.SINK
                || loadPort.getDrive() != Drive.NONE) {
            add(group, issues, Decision.INCOMPATIBLE,
                    Reason.INVALID_DIRECTION_DRIVE, "connection.switchedLowSide.shape", group.ids());
            return;
        }
        if (!relation.isActiveHigh()) {
            add(group, issues, Decision.INCOMPATIBLE,
                    Reason.ACTIVE_LEVEL_MISMATCH,
                    "connection.switchedLowSide.activeHigh", group.ids());
        }
        if (relation.getSinkCapacityAmps() < SwitchedLowSideContract.MIN_SINK_CAPACITY_AMPS
                || relation.getLoadDemandAmps() > SwitchedLowSideContract.MAX_LOAD_DEMAND_AMPS) {
            add(group, issues, Decision.INCOMPATIBLE,
                    Reason.LOAD_CAPACITY_EXCEEDED,
                    "connection.switchedLowSide.contractCapacityDemand", group.ids());
        }
        if (sinkPort.getCapacityAmps().getState() != State.KNOWN
                || loadPort.getLoading() != Loading.BOUNDED_CURRENT
                || loadPort.getDemandAmps().getState() != State.KNOWN) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                    Reason.REQUIRED_INFORMATION_MISSING,
                    "connection.switchedLowSide.capacityDemand", group.ids());
            return;
        }
        if (sinkPort.getCapacityAmps().getValue() < relation.getSinkCapacityAmps()
                || loadPort.getDemandAmps().getValue() > relation.getLoadDemandAmps()
                || loadPort.getDemandAmps().getValue() > sinkPort.getCapacityAmps().getValue()) {
            add(group, issues, Decision.INCOMPATIBLE,
                    Reason.LOAD_CAPACITY_EXCEEDED,
                    "connection.switchedLowSide.capacityDemand", group.ids());
        }
        if (!knownRange(sinkPort.getGuaranteedVoltage())
                || !knownRange(sinkPort.getAllowedVoltage())
                || !knownRange(loadPort.getAllowedVoltage())
                || !knownRange(supply.port.getGuaranteedVoltage())
                || !knownRange(supply.port.getAllowedVoltage())) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                    Reason.REQUIRED_INFORMATION_MISSING,
                    "connection.switchedLowSide.voltageBounds", group.ids());
        } else {
            if (sinkPort.getGuaranteedVoltage().getMinimum()
                        < relation.getOnClampMinimumVolts()
                    || sinkPort.getGuaranteedVoltage().getMaximum()
                        > relation.getOnClampMaximumVolts()
                    || !loadPort.getAllowedVoltage().contains(
                            relation.getOnClampMinimumVolts())
                    || !loadPort.getAllowedVoltage().contains(
                            relation.getOnClampMaximumVolts())
                    || !sinkPort.getAllowedVoltage().contains(
                            relation.getOffDrainMinimumVolts())
                    || !sinkPort.getAllowedVoltage().contains(
                            relation.getOffDrainMaximumVolts())
                    || !loadPort.getAllowedVoltage().contains(
                            relation.getOffDrainMinimumVolts())
                    || !loadPort.getAllowedVoltage().contains(
                            relation.getOffDrainMaximumVolts())) {
                add(group, issues, Decision.INCOMPATIBLE,
                        Reason.VOLTAGE_RANGE_NOT_CONTAINED,
                        "connection.switchedLowSide.onOffVoltage", group.ids());
            }
            if (supply.port.getGuaranteedVoltage().getMinimum()
                        < relation.getSupplyGuaranteedMinimumVolts()
                    || supply.port.getGuaranteedVoltage().getMaximum()
                        > relation.getSupplyGuaranteedMaximumVolts()
                    || !loadPort.getAllowedVoltage().contains(
                            relation.getLoadAllowedMinimumVolts())
                    || !loadPort.getAllowedVoltage().contains(
                            relation.getLoadAllowedMaximumVolts())) {
                add(group, issues, Decision.INCOMPATIBLE,
                        Reason.VOLTAGE_RANGE_NOT_CONTAINED,
                        "connection.switchedLowSide.supply", group.ids());
            }
        }
        if (!sameConductor(conductors, supply, loadSupply)) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                    Reason.REFERENCE_UNPROVEN,
                    "connection.switchedLowSide.supplyJoin",
                    Arrays.asList(supply.id, loadSupply.id));
        }
        if (!sameConductor(conductors, control, driverControl)) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                    Reason.REFERENCE_UNPROVEN,
                    "connection.switchedLowSide.controlJoin",
                    Arrays.asList(control.id, driverControl.id));
        }
        checkReferenceEvidence(group, relation, sink, load, supply, control,
                nodes, conductors, references, attemptedReferences, proposals,
                issues);
        if (control.port.getRole() != Role.CONTROL
                || control.port.getDirection() != Direction.OUTPUT
                || control.port.getBehavior() != Behavior.SOURCE
                || control.port.getDrive() != Drive.PUSH_PULL
                || control.port.getDigital().getActiveLevel() != ActiveLevel.HIGH) {
                add(group, issues, Decision.INCOMPATIBLE,
                    Reason.INVALID_DIRECTION_DRIVE,
                    "connection.switchedLowSide.control", control);
        }
        checkControlEvidence(group, relation, control, driverControl, issues);
        checkSupplyLoadEvidence(group, relation, supply, loadSupply, issues);
    }

    private static void checkReferenceEvidence(Group group,
            SwitchedLowSideContract relation, Node sink, Node load,
            Node supply, Node control, Map<String, Node> allNodes,
            Union conductors, Union references, Union attemptedReferences,
            Map<String, ElectricalConnection> proposals, List<Diagnostic> issues) {
        List<Node> nodes = Arrays.asList(sink, load, supply, control);
        for (Node node : nodes) {
            String reference = node.port.getDomain().getReferenceNetId();
            String isolation = node.port.getDomain().getIsolationId();
            if (isolation == null) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                        Reason.UNKNOWN_ISOLATION,
                        "connection.switchedLowSide.reference", node);
            } else if (!relation.getReferenceNetId().equals(reference)
                    || !relation.getIsolationId().equals(isolation)) {
                add(group, issues, Decision.INCOMPATIBLE,
                        Reason.REFERENCE_MISMATCH,
                        "connection.switchedLowSide.reference", node);
            }
        }
        for (ElectricalConnection.PortRef ref : relation.getReturnPorts()) {
            Node node = ref == null ? null : allNodes.get(ref.key());
            if (node == null) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                        Reason.REFERENCE_UNPROVEN,
                        "connection.switchedLowSide.return", group.ids());
            } else if (node.port.getDomain().getIsolationId() == null) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                        Reason.UNKNOWN_ISOLATION,
                        "connection.switchedLowSide.return", node);
            } else if (node.port.getRole() != Role.RETURN
                    || !relation.getReferenceNetId().equals(
                            node.port.getDomain().getReferenceNetId())
                    || !relation.getIsolationId().equals(
                            node.port.getDomain().getIsolationId())) {
                add(group, issues, Decision.INCOMPATIBLE,
                        Reason.REFERENCE_MISMATCH,
                        "connection.switchedLowSide.return", node);
            }
        }
        TreeSet<String> expected = new TreeSet<String>();
        boolean duplicate = false;
        String returnRoot = null;
        for (ElectricalConnection.PortRef ref : relation.getReturnPorts()) {
            Node node = ref == null ? null : allNodes.get(ref.key());
            if (node == null) continue;
            if (!expected.add(node.key)) duplicate = true;
            String root = conductors.find(node.key);
            if (returnRoot == null) returnRoot = root;
            else if (!returnRoot.equals(root)) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                        Reason.REFERENCE_UNPROVEN,
                        "connection.switchedLowSide.returnJoin", group.ids());
            }
        }
        if (duplicate) {
            add(group, issues, Decision.MALFORMED, Reason.INVALID_CONNECTION,
                    "connection.switchedLowSide.returnList", group.ids());
            return;
        }
        if (returnRoot != null) {
            TreeSet<String> actual = new TreeSet<String>();
            for (Node node : allNodes.values())
                if (returnRoot.equals(conductors.find(node.key))) actual.add(node.key);
            if (!expected.containsAll(actual)) {
                add(group, issues, Decision.INCOMPATIBLE,
                        Reason.INVALID_CONNECTION,
                        "connection.switchedLowSide.returnList", group.ids());
            }
            if (!actual.containsAll(expected)) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                        Reason.REFERENCE_UNPROVEN,
                        "connection.switchedLowSide.returnJoin", group.ids());
            }
            boolean explicitJoin = false;
            for (ElectricalConnection connection : proposals.values()) {
                if (connection.getKind() != ElectricalConnection.Kind.CONDUCTIVE
                        || connection.getPorts().size() < 2) continue;
                boolean touches = false, allExpected = true;
                for (ElectricalConnection.PortRef ref : connection.getPorts()) {
                    Node node = allNodes.get(ref.key());
                    if (node == null) { allExpected = false; break; }
                    if (returnRoot.equals(conductors.find(node.key))) touches = true;
                    if (!expected.contains(node.key)) allExpected = false;
                }
                if (touches && allExpected) explicitJoin = true;
            }
            if (!explicitJoin) {
                add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                        Reason.REFERENCE_UNPROVEN,
                        "connection.switchedLowSide.returnJoin", group.ids());
            }
            for (Node node : allNodes.values()) {
                if (!expected.contains(node.key)) continue;
                if (node.port.getRole() != Role.RETURN) {
                    add(group, issues, Decision.INCOMPATIBLE,
                            Reason.REFERENCE_MISMATCH,
                            "connection.switchedLowSide.returnRole", node);
                }
            }
            Node first = allNodes.get(relation.getReturnPorts().get(0).key());
            if (first != null) {
                for (Node node : Arrays.asList(sink, load, supply, control))
                    checkReferenceUnion(group, first, node, references,
                            attemptedReferences, issues);
                for (String key : expected) {
                    Node node = allNodes.get(key);
                    if (node != null) checkReferenceUnion(group, first, node,
                            references, attemptedReferences, issues);
                }
            }
        }
    }

    private static void checkReferenceUnion(Group group, Node first, Node node,
            Union references, Union attemptedReferences, List<Diagnostic> issues) {
        if (references.find(first.reference).equals(references.find(node.reference))) return;
        if (attemptedReferences.find(first.reference).equals(
                attemptedReferences.find(node.reference))) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                    Reason.REFERENCE_UNPROVEN, "connection.switchedLowSide.referenceJoin",
                    Arrays.asList(first.id, node.id));
        } else {
            add(group, issues, Decision.INCOMPATIBLE,
                    Reason.REFERENCE_MISMATCH, "connection.switchedLowSide.referenceJoin",
                    Arrays.asList(first.id, node.id));
        }
    }

    private static boolean sameConductor(Union conductors, Node first, Node second) {
        return conductors.find(first.key).equals(conductors.find(second.key));
    }

    private static void checkControlEvidence(Group group,
            SwitchedLowSideContract relation, Node control, Node driverControl,
            List<Diagnostic> issues) {
        ElectricalPortContract.Digital output = control.port.getDigital();
        ElectricalPortContract.Digital input = driverControl.port.getDigital();
        boolean outputLow = required(group, control,
                output.getLowMaximum().getState(), "digital.lowMaximum", issues);
        boolean outputHigh = required(group, control,
                output.getHighMinimum().getState(), "digital.highMinimum", issues);
        boolean inputLow = required(group, driverControl,
                input.getInputLowMaximum().getState(), "digital.inputLowMaximum", issues);
        boolean inputHigh = required(group, driverControl,
                input.getInputHighMinimum().getState(), "digital.inputHighMinimum", issues);
        if (outputLow && output.getLowMaximum().getValue()
                > relation.getControlLowMaximumVolts())
            add(group, issues, Decision.INCOMPATIBLE, Reason.DIGITAL_LEVEL_MISMATCH,
                    "connection.switchedLowSide.controlLow", control);
        if (outputHigh && output.getHighMinimum().getValue()
                < relation.getControlHighMinimumVolts())
            add(group, issues, Decision.INCOMPATIBLE, Reason.DIGITAL_LEVEL_MISMATCH,
                    "connection.switchedLowSide.controlHigh", control);
        if (inputLow && input.getInputLowMaximum().getValue()
                > relation.getInputLowMaximumVolts())
            add(group, issues, Decision.INCOMPATIBLE, Reason.DIGITAL_LEVEL_MISMATCH,
                    "connection.switchedLowSide.inputLow", driverControl);
        if (inputHigh && input.getInputHighMinimum().getValue()
                < relation.getInputHighMinimumVolts())
            add(group, issues, Decision.INCOMPATIBLE, Reason.DIGITAL_LEVEL_MISMATCH,
                    "connection.switchedLowSide.inputHigh", driverControl);
        if (output.getActiveLevel() != ActiveLevel.HIGH
                || input.getActiveLevel() != ActiveLevel.HIGH) {
            add(group, issues, Decision.INCOMPATIBLE, Reason.ACTIVE_LEVEL_MISMATCH,
                    "connection.switchedLowSide.activeHigh", Arrays.asList(
                            control.id, driverControl.id));
        }
    }

    private static void checkSupplyLoadEvidence(Group group,
            SwitchedLowSideContract relation, Node supply, Node loadSupply,
            List<Diagnostic> issues) {
        ElectricalPortContract source = supply.port;
        ElectricalPortContract load = loadSupply.port;
        if (source.getRole() != Role.RAIL
                || source.getDirection() != Direction.OUTPUT
                || source.getBehavior() != Behavior.SOURCE
                || source.getDrive() != Drive.STIFF_VOLTAGE
                || load.getRole() != Role.LOAD
                || load.getDirection() != Direction.INPUT
                || load.getBehavior() != Behavior.SINK
                || load.getDrive() != Drive.NONE) {
            add(group, issues, Decision.INCOMPATIBLE,
                    Reason.INVALID_DIRECTION_DRIVE,
                    "connection.switchedLowSide.supplyShape",
                    Arrays.asList(supply.id, loadSupply.id));
        }
        if (load.getLoading() != Loading.BOUNDED_CURRENT
                || load.getDemandAmps().getState() != State.KNOWN) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION,
                    Reason.REQUIRED_INFORMATION_MISSING,
                    "connection.switchedLowSide.supplyDemand", loadSupply);
        } else if (load.getDemandAmps().getValue()
                > relation.getLoadDemandAmps()) {
            add(group, issues, Decision.INCOMPATIBLE,
                    Reason.LOAD_CAPACITY_EXCEEDED,
                    "connection.switchedLowSide.supplyDemand", loadSupply);
        }
    }

    private static boolean samePortSet(Collection<ElectricalConnection.PortRef> first,
            Collection<ElectricalConnection.PortRef> second) {
        TreeSet<String> a = new TreeSet<String>(), b = new TreeSet<String>();
        for (ElectricalConnection.PortRef ref : first) a.add(ref.key());
        for (ElectricalConnection.PortRef ref : second) b.add(ref.key());
        return a.equals(b);
    }

    private static boolean knownRange(ElectricalPortContract.Range range) {
        return range != null && range.getState() == State.KNOWN;
    }

    private static void checkDigital(Group group, Node driver, Node receiver, List<Diagnostic> issues) {
        if (!isLogic(driver.port.getRole())) {
            add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.UNSUPPORTED_ROLE_PAIR, "role.digital", Arrays.asList(driver.id, receiver.id));
            return;
        }
        ElectricalPortContract.Digital output = driver.port.getDigital(), input = receiver.port.getDigital();
        boolean low = required(group, driver, output.getLowMaximum().getState(), "digital.lowMaximum", issues);
        boolean high = required(group, driver, output.getHighMinimum().getState(), "digital.highMinimum", issues);
        boolean lowThreshold = required(group, receiver, input.getInputLowMaximum().getState(), "digital.inputLowMaximum", issues);
        boolean highThreshold = required(group, receiver, input.getInputHighMinimum().getState(), "digital.inputHighMinimum", issues);
        if (low && lowThreshold && output.getLowMaximum().getValue() > input.getInputLowMaximum().getValue())
            add(group, issues, Decision.INCOMPATIBLE, Reason.DIGITAL_LEVEL_MISMATCH, "digital.lowMaximum.inputLowMaximum", Arrays.asList(driver.id, receiver.id));
        if (high && highThreshold && output.getHighMinimum().getValue() < input.getInputHighMinimum().getValue())
            add(group, issues, Decision.INCOMPATIBLE, Reason.DIGITAL_LEVEL_MISMATCH, "digital.highMinimum.inputHighMinimum", Arrays.asList(driver.id, receiver.id));
        ActiveLevel a = output.getActiveLevel(), b = input.getActiveLevel();
        if (a == ActiveLevel.NOT_APPLICABLE || b == ActiveLevel.NOT_APPLICABLE)
            add(group, issues, Decision.MALFORMED, Reason.REQUIRED_FIELD_NOT_APPLICABLE, "digital.activeLevel", Arrays.asList(driver.id, receiver.id));
        else if (a == ActiveLevel.UNKNOWN || b == ActiveLevel.UNKNOWN)
            add(group, issues, Decision.INSUFFICIENT_INFORMATION, Reason.REQUIRED_INFORMATION_MISSING, "digital.activeLevel", Arrays.asList(driver.id, receiver.id));
        else if (a != b)
            add(group, issues, Decision.INCOMPATIBLE, Reason.ACTIVE_LEVEL_MISMATCH, "digital.activeLevel", Arrays.asList(driver.id, receiver.id));
    }

    private static boolean required(Group group, Node node, State state, String field, List<Diagnostic> issues) {
        if (state == State.KNOWN) return true;
        add(group, issues, state == State.UNKNOWN ? Decision.INSUFFICIENT_INFORMATION : Decision.MALFORMED,
            state == State.UNKNOWN ? Reason.REQUIRED_INFORMATION_MISSING : Reason.REQUIRED_FIELD_NOT_APPLICABLE, field, node);
        return false;
    }
    private static boolean isLogic(Role role) { return role == Role.CONTROL || role == Role.DIGITAL; }
    private static String unresolvedPortId(BlockNamespace namespace, ElectricalConnection.PortRef ref) {
        // Diagnostic address for a syntactically valid but undeclared tuple.
        // idFor intentionally rejects this tuple; no declared entity is created.
        // Keep the literal v1 address covered by the independent namespace oracle.
        return "tsj-preflight-v1/" + namespace.getDeviceSchemaId() + "@" +
            namespace.getDeviceSchemaVersion() + "/" + ref.getBlockKey() + "/port/" + ref.getPortId();
    }
    private static boolean isVoltageDriver(ElectricalPortContract port) {
        return port.getDrive() == Drive.STIFF_VOLTAGE || port.getDrive() == Drive.PUSH_PULL || port.getDrive() == Drive.RESISTIVE_SOURCE;
    }
    private static boolean hasIssueFor(Group group, List<Diagnostic> issues) {
        for (Diagnostic issue : issues) for (String connection : issue.connectionIds)
            if (group.connections.contains(connection)) return true;
        return false;
    }
    private static void add(Group group, List<Diagnostic> issues, Decision decision, Reason code, String field, Node node) {
        add(group, issues, decision, code, field, Arrays.asList(node.id));
    }
    private static void add(Group group, List<Diagnostic> issues, Decision decision, Reason code, String field, Collection<String> ports) {
        issues.add(new Diagnostic(decision, code, group.connections, ports, field));
    }
    private static List<String> sorted(Collection<String> items) {
        return Collections.unmodifiableList(new ArrayList<String>(new TreeSet<String>(items)));
    }
    private static int priority(Decision decision) {
        switch (decision) {
        case MALFORMED: return 3;
        case INCOMPATIBLE: return 2;
        case INSUFFICIENT_INFORMATION: return 1;
        default: return 0;
        }
    }
    private static final class Node {
        final ElectricalBlockContract block;
        final ElectricalPortContract port;
        final String key, id, reference, attachment;
        Node(BlockNamespace namespace, ElectricalBlockContract block, ElectricalPortContract port) {
            this.block = block; this.port = port;
            String instance = block.getDescriptor().getInstanceKey();
            key = instance + "/" + port.getId();
            id = namespace.idFor(instance, FunctionalBlockDescriptor.EntityKind.PORT, port.getId());
            reference = block.getReferenceKey(port.getId());
            attachment = instance + "/" + block.getAttachmentKey(port.getId());
        }
    }
    private static final class Group {
        final List<Node> nodes = new ArrayList<Node>();
        final TreeSet<String> connections = new TreeSet<String>();
        boolean onlyReturns() {
            for (Node node : nodes) if (node.port.getRole() != Role.RETURN) return false;
            return true;
        }
        List<String> ids() {
            List<String> result = new ArrayList<String>();
            for (Node node : nodes) result.add(node.id);
            return result;
        }
    }
    /** Per-invocation semantic equivalence only, never runtime allocation. */
    private static final class Union {
        private final Map<String, String> parents = new TreeMap<String, String>();
        void add(String key) { if (!parents.containsKey(key)) parents.put(key, key); }
        String find(String key) {
            String root = key;
            while (!parents.get(root).equals(root)) root = parents.get(root);
            while (!key.equals(root)) { String next = parents.get(key); parents.put(key, root); key = next; }
            return root;
        }
        void join(String first, String second) {
            String a = find(first), b = find(second);
            if (!a.equals(b)) {
                if (a.compareTo(b) < 0) parents.put(b, a); else parents.put(a, b);
            }
        }
    }
}
