package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Built-in typed package definitions used by the current generated families. */
final class PhysicalPackages {
    static final PhysicalPackage AXIAL_RESISTOR = packageOf("AXIAL_RESISTOR",
        new String[] { "1", "2" }, false);
    static final PhysicalPackage AXIAL_DIODE = packageOf("AXIAL_DIODE",
        new String[] { "A", "K" }, false);
    static final PhysicalPackage THROUGH_HOLE_LED = packageOf("THROUGH_HOLE_LED",
        new String[] { "A", "K" }, false);
    static final PhysicalPackage RADIAL_ELECTROLYTIC_CAPACITOR =
        packageOf("RADIAL_ELECTROLYTIC_CAPACITOR", new String[] { "+", "-" }, false);
    static final PhysicalPackage RADIAL_CERAMIC_CAPACITOR =
        packageOf("RADIAL_CERAMIC_CAPACITOR", new String[] { "1", "2" }, false);
    static final PhysicalPackage THROUGH_HOLE_CONNECTOR_2 = packageOf("THROUGH_HOLE_CONNECTOR_2",
        new String[] { "1", "2" }, true);
    /** A two-pin output header is physically a connector but not the power-input anchor. */
    static final PhysicalPackage THROUGH_HOLE_OUTPUT_HEADER_2 =
        packageOf("THROUGH_HOLE_OUTPUT_HEADER_2", new String[] { "1", "2" }, false);
    static final PhysicalPackage MULTI_TERMINAL = packageOf("MULTI_TERMINAL",
        new String[] { "1", "2", "3", "4", "5", "6" }, false);
    static final PhysicalPackage DEV_CANARY_3 = packageOf("DEV_CANARY_3",
        new String[] { "1", "2", "3" }, new String[] { "1=2" }, false);
    static final PhysicalPackage DEV_CANARY_3_ORDERED = packageOf("DEV_CANARY_3_ORDERED",
        new String[] { "1", "2", "3" }, new String[] { "1=2", "2=3" }, false);
    static final PhysicalPackage DEV_CANARY_4 = packageOf("DEV_CANARY_4",
        new String[] { "1", "2", "3", "4" }, new String[] { "1=2" }, false);
    static final PhysicalPackage DEV_CANARY_5 = packageOf("DEV_CANARY_5",
        new String[] { "1", "2", "3", "4", "5" }, false);
    static final PhysicalPackage DEV_CANARY_6 = packageOf("DEV_CANARY_6",
        new String[] { "1", "2", "3", "4", "5", "6" }, false);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_3 = packageOf("DEV_CANARY_CONNECTOR_3",
        new String[] { "1", "2", "3" }, true);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_4 = packageOf("DEV_CANARY_CONNECTOR_4",
        new String[] { "1", "2", "3", "4" }, true);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_5 = packageOf("DEV_CANARY_CONNECTOR_5",
        new String[] { "1", "2", "3", "4", "5" }, true);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_6 = packageOf("DEV_CANARY_CONNECTOR_6",
        new String[] { "1", "2", "3", "4", "5", "6" }, true);

    private PhysicalPackages() { }

    /** Compatibility mapping occurs at logical-board construction, not in PCB providers. */
    static PhysicalPackage forLegacyComponentType(String type) {
        if ("CONNECTOR".equals(type)) return THROUGH_HOLE_CONNECTOR_2;
        if ("RESISTOR".equals(type)) return AXIAL_RESISTOR;
        if ("DIODE".equals(type)) return AXIAL_DIODE;
        if ("LED".equals(type)) return THROUGH_HOLE_LED;
        if ("CAPACITOR_ELECTROLYTIC".equals(type)) return RADIAL_ELECTROLYTIC_CAPACITOR;
        if ("CAPACITOR_CERAMIC".equals(type)) return RADIAL_CERAMIC_CAPACITOR;
        if ("MULTI_TERMINAL".equals(type)) return MULTI_TERMINAL;
        if ("DEV_CANARY_3".equals(type)) return DEV_CANARY_3;
        if ("DEV_CANARY_4".equals(type)) return DEV_CANARY_4;
        if ("DEV_CANARY_5".equals(type)) return DEV_CANARY_5;
        if ("DEV_CANARY_6".equals(type)) return DEV_CANARY_6;
        return null;
    }

    static PhysicalPackage developerConnectorForCount(int count) {
        if (count == 3) return DEV_CANARY_CONNECTOR_3;
        if (count == 4) return DEV_CANARY_CONNECTOR_4;
        if (count == 5) return DEV_CANARY_CONNECTOR_5;
        if (count == 6) return DEV_CANARY_CONNECTOR_6;
        throw new IllegalArgumentException("Unsupported developer connector count");
    }

    private static PhysicalPackage packageOf(String id, String[] terminalIds, boolean connector) {
        return packageOf(id, terminalIds, new String[0], connector);
    }

    private static PhysicalPackage packageOf(String id, String[] terminalIds,
            String[] internalConnections, boolean connector) {
        Vector<String> terminals = new Vector<String>();
        for (String terminalId : terminalIds) terminals.add(terminalId);
        Vector<String> connections = new Vector<String>();
        for (String connection : internalConnections) connections.add(connection);
        return new PhysicalPackage(id, terminals, connections, connector);
    }
}
