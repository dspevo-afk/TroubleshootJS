package com.lushprojects.circuitjs1.client;

import java.util.Map;

/**
 * Device-owned construction for the bounded canaries.  It owns only external
 * supplies, isolation/connectors, command control, cross-block wires and
 * joins; local device recipes remain in the standard providers.
 */
final class BoundedElectricalDeviceConstructionAdapter {
    private static final double SUPPLY_VOLTAGE = 5.0;

    private BoundedElectricalDeviceConstructionAdapter() { }

    static DeviceJoinReceipt construct(BoundedAssemblyPlan plan,
            ElectricalConstructionContext context,
            Map<String, ContributionConstructionReceipt> contributions) {
        if (plan.isControlledIndicator())
            return constructControlled(plan, context, contributions);
        return constructResistive(context, contributions.get("source"),
            contributions.get("load"));
    }

    private static DeviceJoinReceipt constructResistive(ElectricalConstructionContext context,
            ContributionConstructionReceipt source, ContributionConstructionReceipt load) {
        ElectricalConstructionContext.DeviceScope device = context.deviceScope("device");
        ElectricalConstructionContext.ElementHandle supply = device.voltageSource(
            "SUPPLY", 100, 320, 100, 160, SUPPLY_VOLTAGE);
        ElectricalConstructionContext.ElementHandle isolation = device.switchElement(
            "ISOLATION", 100, 160, 180, 160);
        ElectricalConstructionContext.ElementHandle connector = device.switchElement(
            "CONNECTOR", 180, 160, 220, 160);
        ElectricalConstructionContext.ElementHandle supplyTrace = device.wire(
            "SUPPLY_TRACE", 220, 160, 230, 160);
        ElectricalConstructionContext.ElementHandle outputTrace = device.wire(
            "OUTPUT_TRACE", 420, 160, 500, 160);
        ElectricalConstructionContext.TerminalHandle sourceR1 =
            device.terminal(source.getElement("R1"), "1");
        ElectricalConstructionContext.TerminalHandle sourcePublic =
            device.terminal(source.getElement("R1_SECONDARY"), "2");
        ElectricalConstructionContext.ElementHandle sourceFirst = device.join(
            "SOURCE_FIRST_ATTACHMENT", device.terminal(supplyTrace, "2"), sourceR1);
        ElectricalConstructionContext.ElementHandle sourceSecond = device.join(
            "SOURCE_SECOND_ATTACHMENT", sourcePublic, device.terminal(outputTrace, "1"));
        ElectricalConstructionContext.ElementHandle returnTrace = device.wire(
            "RETURN_TRACE", 740, 160, 740, 320);
        ElectricalConstructionContext.ElementHandle ground = device.ground(
            "GROUND", 740, 320, 740, 352);
        device.wire("RETURN_BOTTOM", 100, 320, 740, 320);
        ElectricalConstructionContext.TerminalHandle loadR1 =
            device.terminal(load.getElement("R1"), "1");
        ElectricalConstructionContext.TerminalHandle loadPublic =
            device.terminal(load.getElement("R1_SECONDARY"), "2");
        ElectricalConstructionContext.ElementHandle loadFirst = device.join(
            "LOAD_FIRST_ATTACHMENT", device.terminal(outputTrace, "2"), loadR1);
        ElectricalConstructionContext.ElementHandle loadSecond = device.join(
            "LOAD_SECOND_ATTACHMENT", loadPublic, device.terminal(returnTrace, "1"));
        device.bindComponent("device", "J1", connector, null);
        device.bindPad("device", "J1.1", device.terminal(connector, "2"));
        device.bindPad("device", "J1.2", device.terminal(ground, "1"));
        device.bindPad("source", "R1.1", device.terminal(supplyTrace, "2"));
        device.bindPad("source", "R1.2", device.terminal(outputTrace, "1"));
        device.bindPad("load", "R1.1", device.terminal(outputTrace, "2"));
        device.bindPad("load", "R1.2", device.terminal(returnTrace, "1"));
        device.bindPower(BoundedGeneratedBoardAssembler.POWER_INPUT_ID, supply, isolation);
        device.bindComponentConnection("source", "R1", "R1.1",
            device.terminal(supplyTrace, "2"), sourceR1, sourceFirst);
        device.bindComponentConnection("source", "R1", "R1.2",
            device.terminal(outputTrace, "1"), sourcePublic, sourceSecond);
        device.bindComponentConnection("load", "R1", "R1.1",
            device.terminal(outputTrace, "2"), loadR1, loadFirst);
        device.bindComponentConnection("load", "R1", "R1.2",
            device.terminal(returnTrace, "1"), loadPublic, loadSecond);
        return device.finish();
    }

    private static DeviceJoinReceipt constructControlled(BoundedAssemblyPlan plan,
            ElectricalConstructionContext context,
            Map<String, ContributionConstructionReceipt> contributions) {
        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        ElectricalConstructionContext.DeviceScope device = context.deviceScope("device");
        java.util.TreeMap<String, ElectricalConstructionContext.ElementHandle> infrastructure =
                new java.util.TreeMap<String, ElectricalConstructionContext.ElementHandle>();
        ElectricalConstructionContext.ElementHandle ground = device.ground("GROUND", 880, 704, 880, 736);
        infrastructure.put("GROUND", ground);
        int row = 0;
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters()) {
            String key = adapter.getKey();
            int y = 96 + row++ * 192;
            ElectricalConstructionContext.ElementHandle source = device.voltageSource(
                    key + ".SUPPLY", 96, y + 80, 96, y,
                    spec.getElementDeclaration("device", key + ".SUPPLY").getParameter("voltage"));
            ElectricalConstructionContext.ElementHandle isolation = device.switchElement(
                    key + ".ISOLATION", 96, y, 160, y);
            ElectricalConstructionContext.ElementHandle connector = device.switchElement(
                    key + ".CONNECTOR", 160, y, 224, y);
            ElectricalConstructionContext.ElementHandle returned = device.wire(
                    key + ".RETURN", 96, y + 80, 880, 704);
            infrastructure.put(key + ".SUPPLY", source);
            infrastructure.put(key + ".ISOLATION", isolation);
            infrastructure.put(key + ".CONNECTOR", connector);
            infrastructure.put(key + ".RETURN", returned);
            device.bindComponent(key, adapter.getComponentLocalId(), connector, null);
            device.bindPower(adapter.getExternalInputId(), source, isolation);
            if (adapter.isControl()) {
                String commandJoin = null;
                for (ElectricalConnection connection : plan.getRequest().getConnections())
                    for (ElectricalConnection.PortRef port : connection.getPorts())
                        if (key.equals(port.getBlockKey()) && adapter.getOutputPortId().equals(port.getPortId()))
                            commandJoin = connection.getId();
                if (commandJoin == null) throw new IllegalStateException("Control has no declared device join");
                device.command(commandJoin, source);
            }
        }
        for (ElectricalRealizationSpec.BridgeSpec bridge : spec.getBridgeSpecs().values()) {
            ElectricalConstructionContext.ElementHandle wire = device.join(bridge.getBridgeElementId(),
                    terminal(device, bridge.getFirst(), infrastructure, contributions),
                    terminal(device, bridge.getSecond(), infrastructure, contributions));
            infrastructure.put(bridge.getBridgeElementId(), wire);
        }
        for (Map.Entry<String, ElectricalRealizationSpec.BoardEndpointSpec> entry : spec.getBoardEndpoints().entrySet()) {
            ElectricalRealizationSpec.BoardEndpointSpec pad = entry.getValue();
            ElectricalConstructionContext.TerminalHandle boardEndpoint = terminal(device,
                    pad.getEndpoint(), infrastructure, contributions);
            device.bindPad(pad.getOwnerKey(), pad.getLocalPadId(), boardEndpoint);
            if (pad.getAttachmentElementId() != null) {
                ElectricalRealizationSpec.PadBindingSpec binding = spec.getPadBinding(entry.getKey());
                ElectricalRealizationSpec.TerminalMapping mapping = spec.getTerminalMapping(
                        binding.getOwnerKey(), binding.getLocalId(), binding.getTerminalId());
                ElectricalConstructionContext.ElementHandle attachment = contributions.get(pad.getOwnerKey())
                        .getElement(pad.getAttachmentElementId());
                device.bindComponentConnection(binding.getOwnerKey(), binding.getLocalId(), pad.getLocalPadId(),
                        boardEndpoint, terminal(device, mapping.getComponentEndpoint(), infrastructure, contributions), attachment);
            }
        }
        return device.finish();
    }

    private static ElectricalConstructionContext.TerminalHandle terminal(
            ElectricalConstructionContext.DeviceScope device, ElectricalRealizationSpec.EndpointRef ref,
            Map<String, ElectricalConstructionContext.ElementHandle> infrastructure,
            Map<String, ContributionConstructionReceipt> contributions) {
        ElectricalConstructionContext.ElementHandle element;
        if ("device".equals(ref.getOwnerKey())) element = infrastructure.get(ref.getElementId());
        else {
            ContributionConstructionReceipt receipt = contributions.get(ref.getOwnerKey());
            if (receipt == null) throw new IllegalArgumentException("Missing local construction owner");
            element = receipt.getElement(ref.getElementId());
        }
        if (element == null) throw new IllegalArgumentException("Missing declared endpoint element");
        return device.terminal(element, ref.getTerminalId());
    }
}
