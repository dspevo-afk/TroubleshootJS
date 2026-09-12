package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lushprojects.circuitjs1.client.FunctionalBlockDescriptor.EntityKind;

/**
 * Device-owned bounded PCB realization for the composed controlled indicator.
 *
 * <p>The electrical plan owns all logical identities.  This factory only
 * realizes those identities as one deterministic two-channel board.  The
 * placement lanes are bounded to this device profile; terminal and net
 * selection still comes from the resolved descriptors and package map, so a
 * channel may use either supported low-side driver family without a layout
 * branch for that family.</p>
 */
final class ControlledIndicatorPcbLayoutFactory {
    private static final String POWER_ADAPTER_KEY = DeviceAdapterContract.POWER_ADAPTER_KEY;

    private static final int CANVAS_WIDTH = 1660;
    private static final int CANVAS_HEIGHT = 1150;
    private static final int BOARD_X = 20;
    private static final int BOARD_Y = 20;
    private static final int BOARD_WIDTH = 1400;
    private static final int BOARD_HEIGHT = 1100;
    private static final int SUPPLY_BRANCH_X = 260;
    private static final int RETURN_LEFT_X = 200;
    private static final int RETURN_OUTER_X = 1360;
    private static final int RETURN_TRUNK_Y = 1050;

    private ControlledIndicatorPcbLayoutFactory() { }

    /**
     * Create and validate the complete bounded device board.
     *
     * <p>The controlled profile has exactly two repeated channels and one
     * always-connected support indicator.  Geometry is deliberately fixed by
     * role and channel index; identity, package geometry, and copper nets are
     * resolved from the plan.</p>
     */
    static PcbBoardLayout create(TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, BoundedAssemblyPlan plan) {
        if (board == null)
            throw new IllegalArgumentException("Missing controlled-indicator board");
        if (specifications == null)
            throw new IllegalArgumentException(
                "Missing controlled-indicator physical specifications");
        if (plan == null || !plan.isControlledIndicator())
            throw new IllegalArgumentException("Controlled-indicator assembly plan is required");

        ElectricalRealizationSpec spec = plan.getElectricalRealizationSpec();
        if (spec == null)
            throw new IllegalArgumentException("Controlled-indicator electrical spec is required");
        List<ControlledIndicatorChannel> channels = plan.getChannels();
        if (channels.size() != 2)
            throw new IllegalArgumentException(
                "Controlled-indicator PCB requires exactly two channels");

        BoardModel model = resolveModel(plan, spec, channels);
        requireDeclaredJoins(spec, model);

        PcbBoardLayout layout = new PcbBoardLayout(CANVAS_WIDTH, CANVAS_HEIGHT,
            new Rectangle(BOARD_X, BOARD_Y, BOARD_WIDTH, BOARD_HEIGHT),
            new Rectangle(1450, 100, 180, 300),
            SeededPcbLayoutGenerator.CURRENT_VERSION);

        addComponents(layout, board, spec, plan, model);
        addNavigationRegions(layout, plan, model);
        addTraces(layout, model);
        addLabels(layout, board, specifications, plan, model);

        layout.compactToContent(40, 30, 26);
        layout.positionPartsTrayDisjointFromBoard();
        layout.validateGeometry(board);
        return layout;
    }

    private static BoardModel resolveModel(BoundedAssemblyPlan plan,
            ElectricalRealizationSpec spec, List<ControlledIndicatorChannel> channels) {
        DeviceAdapterContract powerAdapter = findAdapter(plan, POWER_ADAPTER_KEY);
        AdapterNodes power = adapter(plan, powerAdapter);
        ArrayList<ChannelNodes> channelNodes = new ArrayList<ChannelNodes>();
        for (int index = 0; index < channels.size(); index++) {
            ControlledIndicatorChannel channel = channels.get(index);
            DeviceAdapterContract controlAdapter = findAdapter(plan,
                channel.getControlAdapterKey());
            channelNodes.add(new ChannelNodes(channel, index,
                driver(plan, channel.getDriverKey()),
                load(plan, channel.getLoadKey()),
                adapter(plan, controlAdapter)));
        }
        SupportNodes support = support(plan, plan.getSupportBlockKey());
        return new BoardModel(power, channelNodes, support,
            plan.netFor(POWER_ADAPTER_KEY, "OUTPUT"),
            plan.netFor(POWER_ADAPTER_KEY, "RETURN"));
    }

    private static void requireDeclaredJoins(ElectricalRealizationSpec spec,
            BoardModel model) {
        requireJoin(spec, ControlledIndicatorBlockContributions.POWER_CONNECTION_ID);
        requireJoin(spec, ControlledIndicatorBlockContributions.RETURN_CONNECTION_ID);
        for (ChannelNodes channel : model.channels) {
            requireJoin(spec, channel.channel.getControlJoinId());
            requireJoin(spec, channel.channel.getSwitchedJoinId());
        }
    }

    private static void requireJoin(ElectricalRealizationSpec spec, String joinId) {
        if (!spec.getDeviceJoins().containsKey(joinId))
            throw new IllegalArgumentException("Controlled-indicator layout requires declared join " +
                joinId);
    }

    private static void addComponents(PcbBoardLayout layout, TroubleshootBoard board,
            ElectricalRealizationSpec spec, BoundedAssemblyPlan plan, BoardModel model) {
        addComponent(layout, board, spec, plan, POWER_ADAPTER_KEY,
            model.power.componentLocalId, 60, 80);

        for (ChannelNodes channel : model.channels) {
            int base = channelOriginY(channel);

            addComponent(layout, board, spec, plan, channel.load.owner,
                channel.load.resistorComponent, 280, base + 220);
            addComponent(layout, board, spec, plan, channel.load.owner,
                channel.load.ledComponent, 560, base + 200);
            addComponent(layout, board, spec, plan, channel.driver.owner,
                channel.driver.controlResistorComponent, 600, base + 100);
            addComponent(layout, board, spec, plan, channel.driver.owner,
                channel.driver.pullDownComponent, 850, base + 20);
            addComponent(layout, board, spec, plan, channel.driver.owner,
                channel.driver.transistorComponent, 1000, base + 100);
            addComponent(layout, board, spec, plan, channel.control.owner,
                channel.control.componentLocalId, 1200, base + 100);
        }

        addComponent(layout, board, spec, plan, model.support.owner,
            model.support.resistorComponent, 280, 900);
        addComponent(layout, board, spec, plan, model.support.owner,
            model.support.ledComponent, 560, 880);
    }

    private static int channelOriginY(ChannelNodes channel) {
        return 80 + channel.index * 380;
    }

    private static void addNavigationRegions(PcbBoardLayout layout, BoundedAssemblyPlan plan, BoardModel model) {
        java.util.Vector<String> power = new java.util.Vector<String>();
        power.add(plan.idFor(POWER_ADAPTER_KEY, EntityKind.COMPONENT, model.power.componentLocalId));
        layout.addRegion(new PcbLayoutRegion("input", "Power input", power));
        for (ChannelNodes channel : model.channels) {
            java.util.Vector<String> ids = new java.util.Vector<String>();
            ids.add(plan.idFor(channel.load.owner, EntityKind.COMPONENT, channel.load.resistorComponent));
            ids.add(plan.idFor(channel.load.owner, EntityKind.COMPONENT, channel.load.ledComponent));
            ids.add(plan.idFor(channel.driver.owner, EntityKind.COMPONENT, channel.driver.controlResistorComponent));
            ids.add(plan.idFor(channel.driver.owner, EntityKind.COMPONENT, channel.driver.pullDownComponent));
            ids.add(plan.idFor(channel.driver.owner, EntityKind.COMPONENT, channel.driver.transistorComponent));
            ids.add(plan.idFor(channel.control.owner, EntityKind.COMPONENT, channel.control.componentLocalId));
            layout.addRegion(new PcbLayoutRegion("channel-" + channel.index, "Channel " + channel.channel.getLabel(), ids));
        }
        java.util.Vector<String> support = new java.util.Vector<String>();
        support.add(plan.idFor(model.support.owner, EntityKind.COMPONENT, model.support.resistorComponent));
        support.add(plan.idFor(model.support.owner, EntityKind.COMPONENT, model.support.ledComponent));
        layout.addRegion(new PcbLayoutRegion("power-indicator", "Power indicator", support));
    }

    private static void addComponent(PcbBoardLayout layout, TroubleshootBoard board,
            ElectricalRealizationSpec spec, BoundedAssemblyPlan plan, String owner,
            String localComponentId, int x, int y) {
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, localComponentId);
        BoardComponent component = board.getComponent(componentId);
        if (component == null)
            throw new IllegalArgumentException("Controlled-indicator board is missing component " +
                componentId);
        PhysicalPackage packageDefinition = spec.getPackageMap().getPackages().get(componentId);
        if (packageDefinition == null || component.getPhysicalPackage() != packageDefinition)
            throw new IllegalStateException("Resolved package diverged for " + componentId);
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, x, y,
            packageDefinition.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static void addTraces(PcbBoardLayout layout, BoardModel model) {
        PcbPadPlacement powerPad = pad(layout, model.power.outputPad);
        for (ChannelNodes channel : model.channels) {
            PcbPadPlacement loadSupply = pad(layout, channel.load.supplyPad);
            trace(layout, model.supplyNet, model.power.outputPad, channel.load.supplyPad,
                powerPad.getX(), powerPad.getY(), SUPPLY_BRANCH_X, powerPad.getY(),
                SUPPLY_BRANCH_X, loadSupply.getY(), loadSupply.getX(), loadSupply.getY());
        }
        PcbPadPlacement supportSupply = pad(layout, model.support.supplyPad);
        trace(layout, model.supplyNet, model.power.outputPad, model.support.supplyPad,
            powerPad.getX(), powerPad.getY(), SUPPLY_BRANCH_X, powerPad.getY(),
            SUPPLY_BRANCH_X, supportSupply.getY(), supportSupply.getX(), supportSupply.getY());

        for (ChannelNodes channel : model.channels)
            addChannelTraces(layout, model, channel);
        addSupportTrace(layout, model);
        addReturnTraces(layout, model);
    }

    private static void addChannelTraces(PcbBoardLayout layout, BoardModel model,
            ChannelNodes channel) {
        int base = channelOriginY(channel);
        String driverControlNet = channel.driver.controlNet;
        String driverNodeNet = channel.driver.controlNodeNet;
        String switchedNet = channel.driver.switchedNet;
        String loadNodeNet = channel.load.nodeNet;

        PcbPadPlacement adapterControl = pad(layout, channel.control.outputPad);
        PcbPadPlacement driverControl = pad(layout, channel.driver.controlPad);
        trace(layout, driverControlNet, channel.control.outputPad,
            channel.driver.controlPad,
            adapterControl.getX(), adapterControl.getY(),
            1320, adapterControl.getY(), 1320, base - 20,
            580, base - 20, 580, driverControl.getY(),
            driverControl.getX(), driverControl.getY());

        PcbPadPlacement loadNodeResistor = pad(layout, channel.load.nodeResistorPad);
        PcbPadPlacement loadNodeLed = pad(layout, channel.load.nodeLedPad);
        int nodeLaneY = base + 330;
        trace(layout, loadNodeNet, channel.load.nodeResistorPad,
            channel.load.nodeLedPad,
            loadNodeResistor.getX(), loadNodeResistor.getY(),
            520, loadNodeResistor.getY(), 520, nodeLaneY,
            loadNodeLed.getX(), nodeLaneY, loadNodeLed.getX(),
            loadNodeLed.getY() + loadNodeLed.getEscapeDy() *
                loadNodeLed.getEscapeLength(),
            loadNodeLed.getX(), loadNodeLed.getY());

        PcbPadPlacement switchedLoad = pad(layout, channel.load.switchedPad);
        PcbPadPlacement switchedDriver = pad(layout, channel.driver.switchedPad);
        trace(layout, switchedNet, channel.load.switchedPad,
            channel.driver.switchedPad,
            switchedLoad.getX(), switchedLoad.getY(),
            switchedLoad.getX(), switchedLoad.getY() +
                switchedLoad.getEscapeDy() * switchedLoad.getEscapeLength(),
            860, base + 305, 860, base + 260, switchedDriver.getX(), base + 260,
            switchedDriver.getX(), switchedDriver.getY() +
                switchedDriver.getEscapeDy() * switchedDriver.getEscapeLength(),
            switchedDriver.getX(), switchedDriver.getY());

        PcbPadPlacement resistorNode = pad(layout, channel.driver.controlNodeResistorPad);
        PcbPadPlacement transistorControl = pad(layout, channel.driver.transistorControlPad);
        int transistorApproachX = transistorControl.getX() +
            transistorControl.getEscapeDx() * transistorControl.getEscapeLength();
        trace(layout, driverNodeNet, channel.driver.controlNodeResistorPad,
            channel.driver.transistorControlPad,
            resistorNode.getX(), resistorNode.getY(),
            840, resistorNode.getY(), 840, base + 230,
            transistorApproachX, base + 230,
            transistorApproachX, transistorControl.getY(), transistorControl.getX(),
            transistorControl.getY());

        PcbPadPlacement pullDownNode = pad(layout, channel.driver.pullDownControlPad);
        trace(layout, driverNodeNet, channel.driver.pullDownControlPad,
            channel.driver.controlNodeResistorPad,
            pullDownNode.getX(), pullDownNode.getY(),
            830, pullDownNode.getY(), 830, resistorNode.getY(),
            resistorNode.getX(), resistorNode.getY());
    }

    private static void addSupportTrace(PcbBoardLayout layout, BoardModel model) {
        PcbPadPlacement resistorNode = pad(layout, model.support.nodeResistorPad);
        PcbPadPlacement ledNode = pad(layout, model.support.nodeLedPad);
        trace(layout, model.support.nodeNet, model.support.nodeResistorPad,
            model.support.nodeLedPad,
            resistorNode.getX(), resistorNode.getY(),
            520, resistorNode.getY(), 520, 1010,
            ledNode.getX(), 1010, ledNode.getX(),
            ledNode.getY() + ledNode.getEscapeDy() * ledNode.getEscapeLength(),
            ledNode.getX(), ledNode.getY());
    }

    private static void addReturnTraces(PcbBoardLayout layout, BoardModel model) {
        String net = model.returnNet;
        PcbPadPlacement powerReturn = pad(layout, model.power.returnPad);

        for (ChannelNodes channel : model.channels) {
            PcbPadPlacement controlReturn = pad(layout, channel.control.returnPad);
            trace(layout, net, model.power.returnPad, channel.control.returnPad,
                powerReturn.getX(), powerReturn.getY(), RETURN_LEFT_X, powerReturn.getY(),
                RETURN_LEFT_X, RETURN_TRUNK_Y, RETURN_OUTER_X, RETURN_TRUNK_Y,
                RETURN_OUTER_X, controlReturn.getY(), controlReturn.getX(),
                controlReturn.getY());
        }

        for (ChannelNodes channel : model.channels) {
            int base = channelOriginY(channel);
            PcbPadPlacement pullDownReturn = pad(layout, channel.driver.pullDownReturnPad);
            PcbPadPlacement transistorReturn = pad(layout, channel.driver.transistorReturnPad);
            int branchEndY = transistorReturn.getY() +
                transistorReturn.getEscapeDy() * transistorReturn.getEscapeLength();
            // Ground the pulldown through its local transistor return, then
            // join the nearby command connector.  The common rail remains
            // explicit copper without a long route for every local pad.
            trace(layout, net, channel.driver.pullDownReturnPad,
                channel.driver.transistorReturnPad,
                pullDownReturn.getX(), pullDownReturn.getY(),
                1160, pullDownReturn.getY(), 1160, base + 240,
                transistorReturn.getX(), base + 240,
                transistorReturn.getX(), branchEndY, transistorReturn.getX(),
                transistorReturn.getY());
            PcbPadPlacement controlReturn = pad(layout, channel.control.returnPad);
            trace(layout, net, channel.driver.transistorReturnPad,
                channel.control.returnPad,
                transistorReturn.getX(), transistorReturn.getY(),
                transistorReturn.getX(), branchEndY, 1140, branchEndY,
                1140, base + 260, 1320, base + 260,
                1320, controlReturn.getY(), controlReturn.getX(), controlReturn.getY());
        }

        PcbPadPlacement supportReturn = pad(layout, model.support.returnPad);
        trace(layout, net, model.power.returnPad, model.support.returnPad,
            powerReturn.getX(), powerReturn.getY(), RETURN_LEFT_X, powerReturn.getY(),
            RETURN_LEFT_X, RETURN_TRUNK_Y, supportReturn.getX(), RETURN_TRUNK_Y,
            supportReturn.getX(), supportReturn.getY());
    }

    private static void addLabels(PcbBoardLayout layout, TroubleshootBoard board,
            BoardPhysicalSpecifications specifications, BoundedAssemblyPlan plan,
            BoardModel model) {
        label(layout, "board-title", "CONTROLLED INDICATOR",
            new Rectangle(650, 30, 160, 18), true, null);

        labelComponent(layout, board, plan, model.power.owner, model.power.componentLocalId,
            80, 55);
        label(layout, "supply", supplyLabel(specifications),
            new Rectangle(185, 88, 60, 16), false, model.power.outputPad);
        label(layout, "return", "GND", new Rectangle(185, 155, 36, 16),
            false, model.power.returnPad);

        for (ChannelNodes channel : model.channels) {
            int base = channelOriginY(channel);
            labelComponent(layout, board, plan, channel.control.owner,
                channel.control.componentLocalId, 1230, base + 60);
            labelComponent(layout, board, plan, channel.load.owner,
                channel.load.resistorComponent, 330, base + 185);
            labelComponent(layout, board, plan, channel.load.owner,
                channel.load.ledComponent, 580, base + 165);
            labelComponent(layout, board, plan, channel.driver.owner,
                channel.driver.controlResistorComponent, 680, base + 75);
            labelComponent(layout, board, plan, channel.driver.owner,
                channel.driver.pullDownComponent, 900, base);
            labelComponent(layout, board, plan, channel.driver.owner,
                channel.driver.transistorComponent, 1070, base + 80);
            label(layout, "channel:" + channel.channel.getKey(),
                "CHANNEL " + channel.channel.getLabel(),
                new Rectangle(330, base + 135, 100, 18), true, null);
        }

        labelComponent(layout, board, plan, model.support.owner,
            model.support.resistorComponent, 330, 865);
        labelComponent(layout, board, plan, model.support.owner,
            model.support.ledComponent, 580, 845);
    }

    private static String supplyLabel(BoardPhysicalSpecifications specifications) {
        PowerInputNameplate nameplate = specifications.getPowerInputNameplate("LOAD_VIN_INPUT");
        if (nameplate == null)
            throw new IllegalArgumentException("Controlled-indicator load supply nameplate is required");
        return nameplate.getDisplayLabel();
    }

    private static void labelComponent(PcbBoardLayout layout, TroubleshootBoard board,
            BoundedAssemblyPlan plan, String owner, String localComponentId, int x, int y) {
        String componentId = plan.idFor(owner, EntityKind.COMPONENT, localComponentId);
        BoardComponent component = board.getComponent(componentId);
        if (component == null)
            throw new IllegalArgumentException("Missing label component " + componentId);
        String text = component.getDisplayName();
        int width = Math.max(24, text.length() * 8 + 4);
        label(layout, "component:" + componentId, text,
            new Rectangle(x, y, width, 18), true, null);
    }

    private static void label(PcbBoardLayout layout, String id, String text,
            Rectangle bounds, boolean bold, String targetPad) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text, bounds, 12,
            bold, targetPad));
    }

    private static DeviceAdapterContract findAdapter(BoundedAssemblyPlan plan, String key) {
        for (DeviceAdapterContract adapter : plan.getDeviceAdapters())
            if (key.equals(adapter.getKey())) return adapter;
        throw new IllegalArgumentException("Missing controlled-indicator adapter " + key);
    }

    private static AdapterNodes adapter(BoundedAssemblyPlan plan,
            DeviceAdapterContract adapter) {
        if (adapter == null)
            throw new IllegalArgumentException("Missing device adapter");
        FunctionalBlockDescriptor descriptor = adapter.getDescriptor();
        String outputPadLocal = padForPort(descriptor, adapter.getOutputPortId());
        String returnPadLocal = padForNet(descriptor, "RETURN", null);
        return new AdapterNodes(adapter.getKey(), adapter.getComponentLocalId(),
            qualifiedPad(plan, adapter.getKey(), outputPadLocal),
            qualifiedPad(plan, adapter.getKey(), returnPadLocal));
    }

    private static DriverNodes driver(BoundedAssemblyPlan plan, String owner) {
        FunctionalBlockDescriptor descriptor = blockDescriptor(plan, owner);
        String controlPadLocal = padForPort(descriptor, "CONTROL");
        String switchedPadLocal = padForPort(descriptor, "SWITCHED_SINK");
        String controlResistor = componentForPad(descriptor, controlPadLocal);
        String transistor = componentForPad(descriptor, switchedPadLocal);
        String controlNodePadLocal = differentNetPadOnComponent(descriptor,
            controlResistor, controlPadLocal);
        String controlNode = netForPad(descriptor, controlNodePadLocal);
        String transistorControlPadLocal = padForComponentAndNet(descriptor,
            transistor, controlNode);
        String pullDownControlPadLocal = padForNetExcluding(descriptor, controlNode,
            controlResistor, transistor);
        String pullDown = componentForPad(descriptor, pullDownControlPadLocal);
        String transistorReturnPadLocal = remainingPadOnComponent(descriptor,
            transistor, switchedPadLocal, transistorControlPadLocal);
        String returnNet = netForPad(descriptor, transistorReturnPadLocal);
        String pullDownReturnPadLocal = padForComponentAndNet(descriptor, pullDown, returnNet);
        return new DriverNodes(owner,
            qualifiedPad(plan, owner, controlPadLocal),
            qualifiedPad(plan, owner, controlNodePadLocal),
            qualifiedPad(plan, owner, transistorControlPadLocal),
            qualifiedPad(plan, owner, switchedPadLocal),
            qualifiedPad(plan, owner, transistorReturnPadLocal),
            qualifiedPad(plan, owner, pullDownControlPadLocal),
            qualifiedPad(plan, owner, pullDownReturnPadLocal),
            controlResistor, pullDown, transistor,
            plan.netFor(owner, netForPad(descriptor, controlPadLocal)),
            plan.netFor(owner, controlNode),
            plan.netFor(owner, netForPad(descriptor, switchedPadLocal)));
    }

    private static LoadNodes load(BoundedAssemblyPlan plan, String owner) {
        FunctionalBlockDescriptor descriptor = blockDescriptor(plan, owner);
        String supplyPadLocal = padForNet(descriptor, "SUPPLY", null);
        String resistor = componentForPad(descriptor, supplyPadLocal);
        String nodeResistorPadLocal = differentNetPadOnComponent(descriptor,
            resistor, supplyPadLocal);
        String node = netForPad(descriptor, nodeResistorPadLocal);
        String nodeLedPadLocal = padForNetExcluding(descriptor, node, resistor);
        String led = componentForPad(descriptor, nodeLedPadLocal);
        String switchedPadLocal = padForNet(descriptor, "SWITCHED_LOAD", led);
        return new LoadNodes(owner,
            qualifiedPad(plan, owner, supplyPadLocal),
            qualifiedPad(plan, owner, nodeResistorPadLocal),
            qualifiedPad(plan, owner, nodeLedPadLocal),
            qualifiedPad(plan, owner, switchedPadLocal),
            resistor, led,
            plan.netFor(owner, node));
    }

    private static SupportNodes support(BoundedAssemblyPlan plan, String owner) {
        FunctionalBlockDescriptor descriptor = blockDescriptor(plan, owner);
        String supplyPadLocal = padForNet(descriptor, "SUPPLY", null);
        String resistor = componentForPad(descriptor, supplyPadLocal);
        String nodeResistorPadLocal = differentNetPadOnComponent(descriptor,
            resistor, supplyPadLocal);
        String node = netForPad(descriptor, nodeResistorPadLocal);
        String nodeLedPadLocal = padForNetExcluding(descriptor, node, resistor);
        String led = componentForPad(descriptor, nodeLedPadLocal);
        String returnPadLocal = padForNet(descriptor, "RETURN", led);
        return new SupportNodes(owner,
            qualifiedPad(plan, owner, supplyPadLocal),
            qualifiedPad(plan, owner, nodeResistorPadLocal),
            qualifiedPad(plan, owner, nodeLedPadLocal),
            qualifiedPad(plan, owner, returnPadLocal),
            resistor, led,
            plan.netFor(owner, node));
    }

    private static FunctionalBlockDescriptor blockDescriptor(BoundedAssemblyPlan plan,
            String owner) {
        ComposedBlockContribution contribution = plan.getBlocks().get(owner);
        if (contribution == null)
            throw new IllegalArgumentException("Missing controlled-indicator block " + owner);
        return contribution.getDescriptor();
    }

    private static String qualifiedPad(BoundedAssemblyPlan plan, String owner,
            String local) {
        return plan.idFor(owner, EntityKind.PAD, local);
    }

    private static String padForPort(FunctionalBlockDescriptor descriptor, String portId) {
        FunctionalBlockDescriptor.Port port = descriptor.getPorts().get(portId);
        if (port == null || port.getAttachment().getKind() != EntityKind.PAD)
            throw new IllegalArgumentException("Layout port is not attached to a pad: " +
                descriptor.getInstanceKey() + "/" + portId);
        return port.getAttachment().getId();
    }

    private static String componentForPad(FunctionalBlockDescriptor descriptor,
            String padLocal) {
        FunctionalBlockDescriptor.Pad pad = descriptor.getPads().get(padLocal);
        if (pad == null)
            throw new IllegalArgumentException("Layout pad is undeclared: " + padLocal);
        FunctionalBlockDescriptor.Endpoint endpoint = descriptor.getEndpoints().get(
            pad.getEndpointId());
        if (endpoint == null)
            throw new IllegalArgumentException("Layout pad endpoint is undeclared: " + padLocal);
        return endpoint.getComponentId();
    }

    private static String padForNet(FunctionalBlockDescriptor descriptor, String localNet,
            String component) {
        for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry :
                descriptor.getPads().entrySet()) {
            FunctionalBlockDescriptor.Pad pad = entry.getValue();
            if (!localNet.equals(pad.getNetId())) continue;
            if (component == null || component.equals(componentForPad(descriptor, entry.getKey())))
                return entry.getKey();
        }
        throw new IllegalArgumentException("Layout net has no matching pad: " +
            descriptor.getInstanceKey() + "/" + localNet);
    }

    private static String padForComponentAndNet(FunctionalBlockDescriptor descriptor,
            String component, String localNet) {
        return padForNet(descriptor, localNet, component);
    }

    private static String padForNetExcluding(FunctionalBlockDescriptor descriptor,
            String localNet, String excludedComponent) {
        return padForNetExcluding(descriptor, localNet, excludedComponent, null);
    }

    private static String padForNetExcluding(FunctionalBlockDescriptor descriptor,
            String localNet, String firstExcluded, String secondExcluded) {
        for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry :
                descriptor.getPads().entrySet()) {
            if (!localNet.equals(entry.getValue().getNetId())) continue;
            String component = componentForPad(descriptor, entry.getKey());
            if (component.equals(firstExcluded) || component.equals(secondExcluded)) continue;
            return entry.getKey();
        }
        throw new IllegalArgumentException("Layout net has no non-excluded pad: " +
            descriptor.getInstanceKey() + "/" + localNet);
    }

    private static String differentNetPadOnComponent(FunctionalBlockDescriptor descriptor,
            String component, String excludedPad) {
        String excludedNet = netForPad(descriptor, excludedPad);
        for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry :
                descriptor.getPads().entrySet()) {
            if (entry.getKey().equals(excludedPad) ||
                    !component.equals(componentForPad(descriptor, entry.getKey())) ||
                    excludedNet.equals(entry.getValue().getNetId())) continue;
            return entry.getKey();
        }
        throw new IllegalArgumentException("Layout component has no second net: " + component);
    }

    private static String remainingPadOnComponent(FunctionalBlockDescriptor descriptor,
            String component, String firstExcluded, String secondExcluded) {
        for (Map.Entry<String, FunctionalBlockDescriptor.Pad> entry :
                descriptor.getPads().entrySet()) {
            if (!component.equals(componentForPad(descriptor, entry.getKey())) ||
                    entry.getKey().equals(firstExcluded) || entry.getKey().equals(secondExcluded))
                continue;
            return entry.getKey();
        }
        throw new IllegalArgumentException("Layout transistor has no return pad: " + component);
    }

    private static String netForPad(FunctionalBlockDescriptor descriptor, String padLocal) {
        FunctionalBlockDescriptor.Pad pad = descriptor.getPads().get(padLocal);
        if (pad == null)
            throw new IllegalArgumentException("Layout pad has no net: " + padLocal);
        return pad.getNetId();
    }

    private static PcbPadPlacement pad(PcbBoardLayout layout, String padId) {
        PcbPadPlacement result = layout.getPad(padId);
        if (result == null)
            throw new IllegalStateException("Controlled-indicator layout is missing pad " + padId);
        return result;
    }

    private static void trace(PcbBoardLayout layout, String net, String start, String end,
            int... points) {
        if (points == null || points.length < 4 || (points.length & 1) != 0)
            throw new IllegalArgumentException("Invalid controlled-indicator trace points");
        int count = points.length / 2;
        int[] x = new int[count];
        int[] y = new int[count];
        for (int index = 0; index < count; index++) {
            x[index] = points[index * 2];
            y[index] = points[index * 2 + 1];
        }
        if (x[0] != pad(layout, start).getX() || y[0] != pad(layout, start).getY() ||
                x[count - 1] != pad(layout, end).getX() ||
                y[count - 1] != pad(layout, end).getY())
            throw new IllegalArgumentException("Trace does not start/end at declared pads: " +
                start + " / " + end);
        layout.addTrace(new PcbTraceGeometry(net, start, end, x, y));
    }

    private static final class BoardModel {
        final AdapterNodes power;
        final List<ChannelNodes> channels;
        final SupportNodes support;
        final String supplyNet;
        final String returnNet;

        BoardModel(AdapterNodes power, List<ChannelNodes> channels,
                SupportNodes support, String supplyNet, String returnNet) {
            this.power = power;
            this.channels = channels;
            this.support = support;
            this.supplyNet = supplyNet;
            this.returnNet = returnNet;
        }
    }

    private static final class ChannelNodes {
        final ControlledIndicatorChannel channel;
        final int index;
        final DriverNodes driver;
        final LoadNodes load;
        final AdapterNodes control;

        ChannelNodes(ControlledIndicatorChannel channel, int index,
                DriverNodes driver, LoadNodes load, AdapterNodes control) {
            this.channel = channel;
            this.index = index;
            this.driver = driver;
            this.load = load;
            this.control = control;
        }
    }

    private static class AdapterNodes {
        final String owner;
        final String componentLocalId;
        final String outputPad;
        final String returnPad;

        AdapterNodes(String owner, String componentLocalId, String outputPad,
                String returnPad) {
            this.owner = owner;
            this.componentLocalId = componentLocalId;
            this.outputPad = outputPad;
            this.returnPad = returnPad;
        }
    }

    private static final class DriverNodes {
        final String owner;
        final String controlPad;
        final String controlNodeResistorPad;
        final String transistorControlPad;
        final String switchedPad;
        final String transistorReturnPad;
        final String pullDownControlPad;
        final String pullDownReturnPad;
        final String controlResistorComponent;
        final String pullDownComponent;
        final String transistorComponent;
        final String controlNet;
        final String controlNodeNet;
        final String switchedNet;

        DriverNodes(String owner, String controlPad, String controlNodeResistorPad,
                String transistorControlPad, String switchedPad, String transistorReturnPad,
                String pullDownControlPad, String pullDownReturnPad,
                String controlResistorComponent, String pullDownComponent,
                String transistorComponent, String controlNet, String controlNodeNet,
                String switchedNet) {
            this.owner = owner;
            this.controlPad = controlPad;
            this.controlNodeResistorPad = controlNodeResistorPad;
            this.transistorControlPad = transistorControlPad;
            this.switchedPad = switchedPad;
            this.transistorReturnPad = transistorReturnPad;
            this.pullDownControlPad = pullDownControlPad;
            this.pullDownReturnPad = pullDownReturnPad;
            this.controlResistorComponent = controlResistorComponent;
            this.pullDownComponent = pullDownComponent;
            this.transistorComponent = transistorComponent;
            this.controlNet = controlNet;
            this.controlNodeNet = controlNodeNet;
            this.switchedNet = switchedNet;
        }
    }

    private static final class LoadNodes {
        final String owner;
        final String supplyPad;
        final String nodeResistorPad;
        final String nodeLedPad;
        final String switchedPad;
        final String resistorComponent;
        final String ledComponent;
        final String nodeNet;

        LoadNodes(String owner, String supplyPad, String nodeResistorPad,
                String nodeLedPad, String switchedPad, String resistorComponent,
                String ledComponent, String nodeNet) {
            this.owner = owner;
            this.supplyPad = supplyPad;
            this.nodeResistorPad = nodeResistorPad;
            this.nodeLedPad = nodeLedPad;
            this.switchedPad = switchedPad;
            this.resistorComponent = resistorComponent;
            this.ledComponent = ledComponent;
            this.nodeNet = nodeNet;
        }
    }

    private static final class SupportNodes {
        final String owner;
        final String supplyPad;
        final String nodeResistorPad;
        final String nodeLedPad;
        final String returnPad;
        final String resistorComponent;
        final String ledComponent;
        final String nodeNet;

        SupportNodes(String owner, String supplyPad, String nodeResistorPad,
                String nodeLedPad, String returnPad, String resistorComponent,
                String ledComponent, String nodeNet) {
            this.owner = owner;
            this.supplyPad = supplyPad;
            this.nodeResistorPad = nodeResistorPad;
            this.nodeLedPad = nodeLedPad;
            this.returnPad = returnPad;
            this.resistorComponent = resistorComponent;
            this.ledComponent = ledComponent;
            this.nodeNet = nodeNet;
        }
    }
}
