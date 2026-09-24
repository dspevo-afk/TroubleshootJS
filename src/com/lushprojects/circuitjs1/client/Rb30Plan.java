package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Immutable Q30 device intent. This declares only physical packages, named
 * nets, public regions and seeded structural choices; the generator owns live
 * CircuitJS elements, physical service and diagnostic proof.
 */
final class Rb30Plan {
    static final String FAMILY_ID = "RB30_CONTROL";

    final long seed, layoutSeed, routingSeed;
    final boolean driverABjt, driverBBjt, sharedHystereticReference;
    final String selectedFault;

    private static final String[] FAULTS = {
        "DREV_OPEN", "REN_OPEN", "SENSOR_A_OPEN", "DRIVE_A_OPEN",
        "RELAY_B_COIL_OPEN"
    };

    private Rb30Plan(long seed) {
        this.seed = seed;
        NamedRandomStreams streams = new NamedRandomStreams(
            NamedRandomStreams.DERIVATION_VERSION, seed, FAMILY_ID, 1);
        driverABjt = streams.openBlock("output-a",
            NamedRandomStreams.Concern.TOPOLOGY, 1, "driver").nextInt(2) == 0;
        driverBBjt = streams.openBlock("output-b",
            NamedRandomStreams.Concern.TOPOLOGY, 1, "driver").nextInt(2) == 0;
        sharedHystereticReference = streams.openDevice(
            NamedRandomStreams.Concern.TOPOLOGY, 1,
            "sensor-reference-arrangement").nextInt(2) == 0;
        selectedFault = FAULTS[streams.openDevice(
            NamedRandomStreams.Concern.FAULT, 1,
            "serviceable-region").nextInt(FAULTS.length)];
        layoutSeed = streams.deviceSeed(NamedRandomStreams.Concern.PLACEMENT,
            1, "board");
        routingSeed = streams.deviceSeed(NamedRandomStreams.Concern.ROUTING,
            1, "copper");
    }

    static Rb30Plan resolve(long seed) { return new Rb30Plan(seed); }

    RelayDriverProvider driverA() {
        return driverABjt ? new RelayDriverProvider.Bjt() :
            new RelayDriverProvider.Nmos();
    }

    RelayDriverProvider driverB() {
        return driverBBjt ? new RelayDriverProvider.Bjt() :
            new RelayDriverProvider.Nmos();
    }

    String topology() {
        return "RB30_" + (sharedHystereticReference ?
            "SHARED_HYSTERETIC" : "SEPARATE_DIRECT") + "_A_" +
            (driverABjt ? "BJT" : "NMOS") + "_B_" +
            (driverBBjt ? "BJT" : "NMOS");
    }

    String canonical() {
        return "rb30-plan@1;seed=" + Long.toString(seed) +
            ";topology=" + topology() + ";layout=" + Long.toString(layoutSeed) +
            ";routing=" + Long.toString(routingSeed) +
            ";physicalPolicy=" + MediumBoardPhysicalPolicy.identity() +
            ";fault=" + selectedFault +
            ";packages=33;main=12V;regulator=5V-E02;coil=5V-E03" +
            ";load=isolated-12V-180ohm-per-channel" +
            ";sensors=two-E04-decisions;loadReference=isolated";
    }

    /** One logical graph with exactly 33 real physical package identities. */
    TroubleshootBoard board() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID + "_BOARD");
        for (String id : new String[] {
                "RAW12", "FUSED12", "RAIL12", "EN5", "RAIL5", "LOAD12"
            }) net(board, id, BoardNet.RoutingRole.SUPPLY);
        for (String id : new String[] { "CTRL_RETURN", "LOAD_RETURN" })
            net(board, id, BoardNet.RoutingRole.RETURN);
        for (String id : new String[] { "A_RAW", "A_SENSE",
                "A_CMD", "A_DRIVE", "B_RAW", "B_SENSE",
                "B_CMD", "B_DRIVE" })
            net(board, id, BoardNet.RoutingRole.CONTROL);
        if (sharedHystereticReference)
            net(board, "REF_SHARED", BoardNet.RoutingRole.CONTROL);
        else {
            net(board, "A_REF", BoardNet.RoutingRole.CONTROL);
            net(board, "B_REF", BoardNet.RoutingRole.CONTROL);
        }
        for (String id : new String[] { "A_COIL_LOW", "B_COIL_LOW",
                "OUT_A", "OUT_B" })
            net(board, id, BoardNet.RoutingRole.HIGH_CURRENT);
        for (String id : new String[] { "NC_A", "NC_B", "LED_FEED" })
            net(board, id, BoardNet.RoutingRole.SIGNAL);

        // Four entry/protection packages. The upstream and downstream
        // capacitors occupy different electrical nets, not a duplicated load.
        part(board, "J1", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "RAW12", "CTRL_RETURN");
        part(board, "F1", "FUSE", PhysicalPackages.AXIAL_FUSE,
            "RAW12", "FUSED12");
        part(board, "DREV", "DIODE", PhysicalPackages.AXIAL_DIODE,
            "FUSED12", "RAIL12");
        part(board, "C12", "CAPACITOR",
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "FUSED12", "CTRL_RETURN");

        // Four regulation/filter packages. REN is a real, serviceable enable
        // path and its open hypothesis removes the rail causally.
        part(board, "U1", "REGULATOR", PhysicalPackages.TO220_REGULATOR_4,
            "RAIL12", "RAIL5", "CTRL_RETURN", "EN5");
        part(board, "CIN", "CAPACITOR",
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            "RAIL12", "CTRL_RETURN");
        part(board, "C5", "CAPACITOR",
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "RAIL5", "CTRL_RETURN");
        part(board, "REN", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            "RAIL12", "EN5");

        sensor(board, "A", sharedHystereticReference ? "REF_SHARED" : "A_REF");
        sensor(board, "B", sharedHystereticReference ? "REF_SHARED" : "B_REF");
        if (sharedHystereticReference) {
            part(board, "RREF_H", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "RAIL5", "REF_SHARED");
            part(board, "RREF_L", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "REF_SHARED", "CTRL_RETURN");
            part(board, "RFB_A", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "A_CMD", "A_SENSE");
            part(board, "RFB_B", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "B_CMD", "B_SENSE");
        } else {
            for (String channel : new String[] { "A", "B" }) {
                part(board, "RREF_H" + channel, "RESISTOR",
                    PhysicalPackages.AXIAL_RESISTOR,
                    "RAIL5", channel + "_REF");
                part(board, "RREF_L" + channel, "RESISTOR",
                    PhysicalPackages.AXIAL_RESISTOR,
                    channel + "_REF", "CTRL_RETURN");
            }
        }

        output(board, "A", driverA());
        output(board, "B", driverB());
        part(board, "RLED", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            "RAIL5", "LED_FEED");
        part(board, "LED1", "LED", PhysicalPackages.THROUGH_HOLE_LED,
            "LED_FEED", "CTRL_RETURN");

        // Sensor and load connectors remain physical, service-owned packages.
        // The two 180 ohm loads and their finite 12 V source are external
        // infrastructure. Both relay contacts share that loaded source.
        part(board, "JSA", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "A_RAW", "CTRL_RETURN");
        part(board, "JSB", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "B_RAW", "CTRL_RETURN");
        part(board, "JLOAD", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "LOAD12", "LOAD_RETURN");
        part(board, "JOA", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            "OUT_A", "LOAD_RETURN");
        part(board, "JOB", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            "OUT_B", "LOAD_RETURN");

        board.addPowerInput(new ExternalBoardPowerInput("MAIN12", "J1.1",
            "J1.2", "RAW12", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("SENSOR_A", "JSA.1",
            "JSA.2", "A_RAW", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("SENSOR_B", "JSB.1",
            "JSB.2", "B_RAW", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("LOAD12", "JLOAD.1",
            "JLOAD.2", "LOAD12", "LOAD_RETURN"));
        board.setPlacementConstraints(placement(board));
        board.validate();
        if (board.getComponentIds().size() != 33)
            throw new IllegalStateException("Q30 reference package accounting changed");
        return board;
    }

    private static void sensor(TroubleshootBoard board, String channel,
            String referenceNet) {
        part(board, "U2" + channel, "SENSOR_CONTROL",
            PhysicalPackages.E04_DECISION_CONTROL_5,
            channel + "_SENSE", referenceNet, "RAIL5",
            channel + "_CMD", "CTRL_RETURN");
        part(board, "RS" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_RAW", channel + "_SENSE");
    }

    private static void output(TroubleshootBoard board, String channel,
            RelayDriverProvider driver) {
        part(board, "RD" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_CMD", channel + "_DRIVE");
        part(board, "RPD" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_DRIVE", "CTRL_RETURN");
        part(board, "Q" + channel, driver.getId(), driver.getPackage(),
            channel + "_DRIVE", channel + "_COIL_LOW", "CTRL_RETURN");
        part(board, "D" + channel, "DIODE", PhysicalPackages.AXIAL_DIODE,
            channel + "_COIL_LOW", "RAIL5");
        part(board, "K" + channel, "RELAY", PhysicalPackages.RELAY_SPDT,
            "RAIL5", channel + "_COIL_LOW", "LOAD12",
            "NC_" + channel, "OUT_" + channel);
    }

    private static void net(TroubleshootBoard board, String id,
            BoardNet.RoutingRole role) {
        board.addNet(new BoardNet(id, role));
    }

    private static void part(TroubleshootBoard board, String id, String type,
            PhysicalPackage physicalPackage, String... nets) {
        Vector<String> terminals = physicalPackage.getTerminalIds();
        if (terminals.size() != nets.length)
            throw new IllegalArgumentException("Q30 terminal count: " + id);
        board.addComponent(new BoardComponent(id, type, physicalPackage));
        for (int index = 0; index < nets.length; index++)
            board.addPad(new BoardPad(id + "." + terminals.get(index), id,
                terminals.get(index), nets[index]));
    }

    private static PcbPlacementConstraints placement(TroubleshootBoard board) {
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        for (String id : board.getComponentIds()) {
            String region = id.equals("J1") || id.equals("F1") ||
                id.equals("DREV") || id.equals("C12") ? "entry" :
                id.equals("U1") || id.equals("CIN") || id.equals("C5") ||
                id.equals("REN") ? "regulation" :
                id.equals("JSA") || id.equals("U2A") || id.equals("RSA") ||
                id.equals("RREFA") || id.equals("RFB_A") ||
                id.equals("RREF_HA") || id.equals("RREF_LA") ? "sensor-a" :
                id.equals("JSB") || id.equals("U2B") || id.equals("RSB") ||
                id.equals("RREFB") || id.equals("RFB_B") ||
                id.equals("RREF_HB") || id.equals("RREF_LB") ? "sensor-b" :
                id.equals("RREF_H") || id.equals("RREF_L") ?
                "sensor-reference" :
                id.equals("RLED") || id.equals("LED1") ? "status" :
                id.endsWith("A") || id.equals("JOA") ? "output-a" :
                id.endsWith("B") || id.equals("JOB") ? "output-b" :
                "output-common";
            String label = region.equals("entry") ? "12 V entry" :
                region.equals("regulation") ? "5 V regulation" :
                region.equals("sensor-a") ? "Sensor A" :
                region.equals("sensor-b") ? "Sensor B" :
                region.equals("sensor-reference") ? "Sensor reference" :
                region.equals("status") ? "Status" :
                region.equals("output-a") ? "Output A" :
                region.equals("output-b") ? "Output B" :
                "Load supply";
            PcbPlacementConstraints.Anchor anchor =
                id.equals("J1") || id.equals("JSA") || id.equals("JSB") ?
                    PcbPlacementConstraints.Anchor.LEFT :
                id.equals("JLOAD") ? PcbPlacementConstraints.Anchor.RIGHT :
                    PcbPlacementConstraints.Anchor.NONE;
            parts.add(new PcbPlacementConstraints.Part(id, region, label,
                "low-voltage-board", anchor, 20));
        }
        return new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
            MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION);
    }
}
