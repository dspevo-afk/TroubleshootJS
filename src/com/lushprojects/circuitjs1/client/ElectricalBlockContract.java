package com.lushprojects.circuitjs1.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;
import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.LocalRef;

/** Binds immutable electrical evidence to the accepted local contribution seam. */
final class ElectricalBlockContract {
    enum AdapterKind { REGULATOR, DIVIDER, LEVEL_SHIFTER, RELAY, ISOLATION_BARRIER }
    static final class Adapter {
        private final String id, inputPortId, outputPortId;
        private final AdapterKind kind;
        private final boolean isolated;
        Adapter(String id, AdapterKind kind, String inputPortId, String outputPortId, boolean isolated) {
            this.id = ElectricalContractException.id(id, "adapter.id");
            this.kind = ElectricalContractException.required(kind, "adapter.kind", id);
            this.inputPortId = ElectricalContractException.id(inputPortId, "adapter.inputPort");
            this.outputPortId = ElectricalContractException.id(outputPortId, "adapter.outputPort");
            this.isolated = isolated;
        }
        String getId() { return id; }
        AdapterKind getKind() { return kind; }
        String getInputPortId() { return inputPortId; }
        String getOutputPortId() { return outputPortId; }
        boolean isIsolated() { return isolated; }
    }

    private final FunctionalBlockDescriptor descriptor;
    private final Map<String, ElectricalPortContract> ports;
    private final Map<String, Adapter> adapters;
    private final Map<String, String> attachmentKeys;

    ElectricalBlockContract(FunctionalBlockDescriptor descriptor, Collection<ElectricalPortContract> ports,
            Collection<Adapter> adapters) {
        this.descriptor = ElectricalContractException.required(descriptor, "descriptor", "");
        ElectricalContractException.required(ports, "ports", descriptor.getInstanceKey());
        ElectricalContractException.required(adapters, "adapters", descriptor.getInstanceKey());
        TreeMap<String, ElectricalPortContract> byId = new TreeMap<String, ElectricalPortContract>();
        TreeMap<String, String> attachments = new TreeMap<String, String>();
        TreeMap<String, String> isolationByReference = new TreeMap<String, String>();
        for (ElectricalPortContract port : ports) {
            ElectricalContractException.required(port, "ports", descriptor.getInstanceKey());
            if (byId.put(port.getId(), port) != null) invalid(ElectricalContractException.Code.DUPLICATE_DECLARATION, "ports", port.getId());
            if (!descriptor.getPorts().containsKey(port.getId())) invalid(ElectricalContractException.Code.INVALID_ATTACHMENT, "port.id", port.getId());
            String reference = port.getDomain().getReferenceNetId();
            if (!descriptor.getNetIds().contains(reference)) invalid(ElectricalContractException.Code.INVALID_REFERENCE, "reference.net", port.getId());
            String isolation = port.getDomain().getIsolationId();
            if (isolationByReference.containsKey(reference)) {
                String previous = isolationByReference.get(reference);
                if (previous == null ? isolation != null : !previous.equals(isolation))
                    invalid(ElectricalContractException.Code.CONTRADICTORY_FIELD, "reference.isolation", port.getId());
            } else isolationByReference.put(reference, isolation);
            String attachment = attachmentKey(descriptor.getPorts().get(port.getId()).getAttachment());
            if (port.getRole() == ElectricalPortContract.Role.RETURN && !attachment.equals("net/" + reference))
                invalid(ElectricalContractException.Code.INVALID_REFERENCE, "return.attachment", port.getId());
            attachments.put(port.getId(), attachment);
        }
        for (String portId : descriptor.getPorts().keySet())
            if (!byId.containsKey(portId)) invalid(ElectricalContractException.Code.MISSING_FIELD, "ports", portId);
        this.ports = Collections.unmodifiableMap(byId);
        this.attachmentKeys = Collections.unmodifiableMap(attachments);
        TreeMap<String, Adapter> byAdapter = new TreeMap<String, Adapter>();
        for (Adapter adapter : adapters) {
            ElectricalContractException.required(adapter, "adapters", descriptor.getInstanceKey());
            if (byAdapter.put(adapter.id, adapter) != null) invalid(ElectricalContractException.Code.DUPLICATE_DECLARATION, "adapters", adapter.id);
            validateAdapter(adapter);
        }
        this.adapters = Collections.unmodifiableMap(byAdapter);
    }
    FunctionalBlockDescriptor getDescriptor() { return descriptor; }
    Map<String, ElectricalPortContract> getPorts() { return ports; }
    Map<String, Adapter> getAdapters() { return adapters; }
    String getAttachmentKey(String portId) { return attachmentKeys.get(portId); }
    String getReferenceKey(String portId) {
        return descriptor.getInstanceKey() + "/" + ports.get(portId).getDomain().getReferenceNetId();
    }

    private String attachmentKey(LocalRef ref) {
        if (ref.getKind() == EntityKind.NET) return "net/" + ref.getId();
        if (ref.getKind() == EntityKind.PAD) return "net/" + descriptor.getPads().get(ref.getId()).getNetId();
        for (FunctionalBlockDescriptor.Pad pad : descriptor.getPads().values())
            if (pad.getEndpointId().equals(ref.getId())) return "net/" + pad.getNetId();
        return "endpoint/" + ref.getId();
    }
    private void validateAdapter(Adapter adapter) {
        ElectricalPortContract input = ports.get(adapter.inputPortId), output = ports.get(adapter.outputPortId);
        if (input == null || output == null || adapter.inputPortId.equals(adapter.outputPortId) ||
                attachmentKeys.get(adapter.inputPortId).equals(attachmentKeys.get(adapter.outputPortId)))
            invalid(ElectricalContractException.Code.INVALID_ADAPTER, "adapter.ports", adapter.id);
        if (input.getDirection() != ElectricalPortContract.Direction.INPUT ||
                output.getDirection() == ElectricalPortContract.Direction.INPUT ||
                input.getRole() == ElectricalPortContract.Role.RETURN || output.getRole() == ElectricalPortContract.Role.RETURN)
            invalid(ElectricalContractException.Code.INVALID_ADAPTER, "adapter.direction", adapter.id);
        String firstRef = input.getDomain().getReferenceNetId(), secondRef = output.getDomain().getReferenceNetId();
        String firstIsolation = input.getDomain().getIsolationId(), secondIsolation = output.getDomain().getIsolationId();
        if (adapter.isolated) {
            if (adapter.kind == AdapterKind.DIVIDER || firstRef.equals(secondRef) || firstIsolation == null ||
                    secondIsolation == null || firstIsolation.equals(secondIsolation))
                invalid(ElectricalContractException.Code.INVALID_ADAPTER, "adapter.isolation", adapter.id);
        } else if (!firstRef.equals(secondRef) || adapter.kind == AdapterKind.RELAY || adapter.kind == AdapterKind.ISOLATION_BARRIER) {
            invalid(ElectricalContractException.Code.INVALID_ADAPTER, "adapter.reference", adapter.id);
        }
    }
    private static void invalid(ElectricalContractException.Code code, String field, String id) {
        throw new ElectricalContractException(code, field, id, "Invalid electrical block declaration");
    }
}
