package com.lushprojects.circuitjs1.client;

/**
 * Device-owned construction for the bounded canaries.  It owns only external
 * supplies, isolation/connectors, command control, cross-block wires and
 * joins; local device recipes remain in the standard providers.
 */
final class BoundedElectricalDeviceConstructionAdapter {
    private static final double SUPPLY_VOLTAGE = 5.0;

    private BoundedElectricalDeviceConstructionAdapter() { }

    static DeviceJoinReceipt constructResistive(BoundedAssemblyPlan plan,
            ElectricalConstructionContext context,
            ContributionConstructionReceipt source,
            ContributionConstructionReceipt load) {
        return beginResistive(context, source).finish(load);
    }

    static ResistiveStage beginResistive(ElectricalConstructionContext context,
            ContributionConstructionReceipt source) {
        return new ResistiveStage(context, source);
    }

    /** Keeps the established source-half MERGE boundary without a second owner. */
    static final class ResistiveStage {
        private final ElectricalConstructionContext.DeviceScope device;
        private final ElectricalConstructionContext.ElementHandle supply;
        private final ElectricalConstructionContext.ElementHandle isolation;
        private final ElectricalConstructionContext.ElementHandle connector;
        private final ElectricalConstructionContext.ElementHandle supplyTrace;
        private final ElectricalConstructionContext.ElementHandle outputTrace;
        private final ElectricalConstructionContext.ElementHandle sourceFirst;
        private final ElectricalConstructionContext.ElementHandle sourceSecond;
        private final ElectricalConstructionContext.TerminalHandle sourceR1;
        private final ElectricalConstructionContext.TerminalHandle sourcePublic;

        private ResistiveStage(ElectricalConstructionContext context,
                ContributionConstructionReceipt source) {
            device = context.deviceScope("device");
            supply = device.voltageSource("SUPPLY", 100, 320, 100, 160, SUPPLY_VOLTAGE);
            isolation = device.switchElement("ISOLATION", 100, 160, 180, 160);
            connector = device.switchElement("CONNECTOR", 180, 160, 220, 160);
            supplyTrace = device.wire("SUPPLY_TRACE", 220, 160, 230, 160);
            outputTrace = device.wire("OUTPUT_TRACE", 420, 160, 500, 160);
            sourceR1 = device.terminal(source.getElement("R1"), "1");
            sourcePublic = device.terminal(source.getElement("R1_SECONDARY"), "2");
            sourceFirst = device.join("SOURCE_FIRST_ATTACHMENT",
                device.terminal(supplyTrace, "2"), sourceR1);
            sourceSecond = device.join("SOURCE_SECOND_ATTACHMENT", sourcePublic,
                device.terminal(outputTrace, "1"));
        }

        DeviceJoinReceipt finish(ContributionConstructionReceipt load) {
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
    }

    static DeviceJoinReceipt constructControlled(BoundedAssemblyPlan plan,
            ElectricalConstructionContext context,
            ContributionConstructionReceipt driver,
            ContributionConstructionReceipt load) {
        ElectricalConstructionContext.DeviceScope device = context.deviceScope("device");
        ElectricalConstructionContext.ElementHandle loadSupply = device.voltageSource(
                "LOAD_SUPPLY", 112, 416, 112, 176, SUPPLY_VOLTAGE);
        ElectricalConstructionContext.ElementHandle loadIsolation = device.switchElement(
                "LOAD_ISOLATION", 112, 176, 192, 176);
        ElectricalConstructionContext.ElementHandle loadConnector = device.switchElement(
                "LOAD_CONNECTOR", 192, 176, 224, 176);
        ElectricalConstructionContext.ElementHandle controlSupply = device.voltageSource(
                "CONTROL_SUPPLY", 112, 496, 112, 96, SUPPLY_VOLTAGE);
        ElectricalConstructionContext.ElementHandle controlIsolation = device.switchElement(
                "CONTROL_ISOLATION", 112, 96, 192, 96);
        ElectricalConstructionContext.ElementHandle controlInputTrace = device.wire(
                "CONTROL_INPUT_TRACE", 192, 96, 240, 96);
        ElectricalConstructionContext.ElementHandle controlCommand = device.switchElement(
                "CONTROL_COMMAND", 240, 96, 272, 96);
        ElectricalConstructionContext.ElementHandle ground = device.ground(
                "GROUND", 900, 416, 900, 448);
        device.wire("LOAD_RETURN", 112, 416, 900, 416);
        device.wire("CONTROL_RETURN", 112, 496, 112, 416);

        ElectricalConstructionContext.TerminalHandle rg1 = device.terminal(
                driver.getElement("RG"), "1");
        ElectricalConstructionContext.TerminalHandle rg2 = device.terminal(
                driver.getElement("RG_SECONDARY"), "2");
        ElectricalConstructionContext.TerminalHandle rpd1 = device.terminal(
                driver.getElement("RPD"), "1");
        ElectricalConstructionContext.TerminalHandle rpd2 = device.terminal(
                driver.getElement("RPD"), "2");
        ElectricalConstructionContext.TerminalHandle q1g = device.terminal(
                driver.getElement("Q1"), "G");
        ElectricalConstructionContext.TerminalHandle q1d = device.terminal(
                driver.getElement("Q1"), "D");
        ElectricalConstructionContext.TerminalHandle q1s = device.terminal(
                driver.getElement("Q1"), "S");
        ElectricalConstructionContext.TerminalHandle rload1 = device.terminal(
                load.getElement("RLOAD"), "1");
        ElectricalConstructionContext.TerminalHandle rloadPublic = device.terminal(
                load.getElement("RLOAD_SECONDARY"), "2");
        ElectricalConstructionContext.TerminalHandle ledA = device.terminal(
                load.getElement("LED1"), "A");
        ElectricalConstructionContext.TerminalHandle ledK = device.terminal(
                load.getElement("LED1"), "K");

        // Pads stay on the persistent device copper when an attachment is removed.
        ElectricalConstructionContext.ElementHandle loadInputTrace = device.join(
                "LOAD_INPUT_TRACE", device.terminal(loadConnector, "2"),
                device.terminal(load.getElement("RLOAD_FIRST_ATTACHMENT"), "1"));
        ElectricalConstructionContext.ElementHandle controlBoardTrace = device.join(
                "CONTROL_BOARD_TRACE", device.terminal(controlCommand, "2"),
                device.terminal(driver.getElement("RG_FIRST_ATTACHMENT"), "1"));
        device.bindComponent("power-adapter", "J1", loadConnector, null);
        device.bindComponent("control-adapter", "J2", controlCommand, null);
        device.bindPad("power-adapter", "J1.1", device.terminal(loadConnector, "2"));
        device.bindPad("power-adapter", "J1.2", device.terminal(ground, "1"));
        device.bindPad("control-adapter", "J2.1", device.terminal(controlCommand, "2"));
        device.bindPad("control-adapter", "J2.2", device.terminal(ground, "1"));
        device.bindPad("driver", "RG.1", device.terminal(controlBoardTrace, "2"));
        device.bindPad("driver", "RG.2", device.terminal(driver.getElement("GATE_NODE_TRACE"), "1"));
        device.bindPad("driver", "RPD.1", rpd1);
        device.bindPad("driver", "RPD.2", rpd2);
        device.bindPad("driver", "Q1.G", q1g);
        device.bindPad("driver", "Q1.D", q1d);
        device.bindPad("driver", "Q1.S", q1s);
        device.bindPad("load", "RLOAD.1", device.terminal(loadInputTrace, "2"));
        device.bindPad("load", "RLOAD.2", device.terminal(load.getElement("LOAD_NODE_TRACE"), "1"));
        device.bindPad("load", "LED1.A", ledA);
        device.bindPad("load", "LED1.K", ledK);

        device.bindPower(ControlledIndicatorDeviceBehavior.LOAD_POWER_INPUT_ID,
                loadSupply, loadIsolation);
        device.bindPower(ControlledIndicatorDeviceBehavior.CONTROL_POWER_INPUT_ID,
                controlSupply, controlIsolation);
        device.bindComponentConnection("driver", "RG", "RG.1",
                device.terminal(controlBoardTrace, "2"),
                rg1,
                driver.getElement("RG_FIRST_ATTACHMENT"));
        device.bindComponentConnection("driver", "RG", "RG.2",
                device.terminal(driver.getElement("GATE_NODE_TRACE"), "1"), rg2,
                driver.getElement("RG_SECOND_ATTACHMENT"));
        device.bindComponentConnection("load", "RLOAD", "RLOAD.1",
                device.terminal(loadInputTrace, "2"),
                rload1,
                load.getElement("RLOAD_FIRST_ATTACHMENT"));
        device.bindComponentConnection("load", "RLOAD", "RLOAD.2",
                device.terminal(load.getElement("LOAD_NODE_TRACE"), "1"),
                rloadPublic, load.getElement("RLOAD_SECOND_ATTACHMENT"));


        device.join("DRAIN_TRACE", ledK, q1d);
        device.join("PULLDOWN_RETURN", rpd2, device.terminal(ground, "1"));
        device.join("SOURCE_RETURN", q1s, device.terminal(ground, "1"));
        device.command(ControlledIndicatorBlockContributions.CONTROL_CONNECTION_ID,
                controlCommand);
        return device.finish();
    }

}
