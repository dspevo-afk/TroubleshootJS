package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Built-in typed package definitions used by the current generated families. */
final class PhysicalPackages {
    static final PhysicalPackage AXIAL_RESISTOR = packageOf("AXIAL_RESISTOR",
        new String[] { "1", "2" }, false,
        PhysicalPackage.GeometryVariantOwner.AXIAL_RESISTOR_SPANS);
    static final PhysicalPackage AXIAL_DIODE = packageOf("AXIAL_DIODE",
        new String[] { "A", "K" }, false,
        PhysicalPackage.GeometryVariantOwner.AXIAL_DIODE_SPANS);
    static final PhysicalPackage THROUGH_HOLE_LED = packageOf("THROUGH_HOLE_LED",
        new String[] { "A", "K" }, false);
    static final PhysicalPackage TO92_NPN = packageOf("TO92_NPN",
        new String[] { "B", "C", "E" }, false);
    static final PhysicalPackage TO92_NMOS = packageOf("TO92_NMOS",
        new String[] { "G", "D", "S" }, false);
    static final PhysicalPackage RADIAL_ELECTROLYTIC_CAPACITOR =
        packageOf("RADIAL_ELECTROLYTIC_CAPACITOR", new String[] { "+", "-" }, false);
    static final PhysicalPackage RADIAL_CERAMIC_CAPACITOR =
        packageOf("RADIAL_CERAMIC_CAPACITOR", new String[] { "1", "2" }, false);
    static final PhysicalPackage THROUGH_HOLE_CONNECTOR_2 = packageOf("THROUGH_HOLE_CONNECTOR_2",
        new String[] { "1", "2" }, true);
    /** A two-pin output header is physically a connector but not the power-input anchor. */
    static final PhysicalPackage THROUGH_HOLE_OUTPUT_HEADER_2 =
        packageOf("THROUGH_HOLE_OUTPUT_HEADER_2", new String[] { "1", "2" }, false);
    static final PhysicalPackage MULTI_TERMINAL = developerCanaryPackageOf("MULTI_TERMINAL",
        new String[] { "1", "2", "3", "4", "5", "6" }, false);
    static final PhysicalPackage DEV_CANARY_3 = developerCanaryPackageOf("DEV_CANARY_3",
        new String[] { "1", "2", "3" }, new String[] { "1=2" }, false);
    static final PhysicalPackage DEV_CANARY_3_ORDERED = developerCanaryPackageOf(
        "DEV_CANARY_3_ORDERED", new String[] { "1", "2", "3" },
        new String[] { "1=2", "2=3" }, false);
    static final PhysicalPackage DEV_CANARY_4 = developerCanaryPackageOf("DEV_CANARY_4",
        new String[] { "1", "2", "3", "4" }, new String[] { "1=2" }, false);
    static final PhysicalPackage DEV_CANARY_5 = developerCanaryPackageOf("DEV_CANARY_5",
        new String[] { "1", "2", "3", "4", "5" }, false);
    static final PhysicalPackage DEV_CANARY_6 = developerCanaryPackageOf("DEV_CANARY_6",
        new String[] { "1", "2", "3", "4", "5", "6" }, false);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_3 = developerCanaryPackageOf(
        "DEV_CANARY_CONNECTOR_3", new String[] { "1", "2", "3" }, true);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_4 = developerCanaryPackageOf(
        "DEV_CANARY_CONNECTOR_4", new String[] { "1", "2", "3", "4" }, true);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_5 = developerCanaryPackageOf(
        "DEV_CANARY_CONNECTOR_5", new String[] { "1", "2", "3", "4", "5" }, true);
    static final PhysicalPackage DEV_CANARY_CONNECTOR_6 = developerCanaryPackageOf(
        "DEV_CANARY_CONNECTOR_6", new String[] { "1", "2", "3", "4", "5", "6" }, true);

    private PhysicalPackages() { }

    /** Compatibility mapping occurs at logical-board construction, not in PCB providers. */
    static PhysicalPackage forLegacyComponentType(String type) {
        if ("CONNECTOR".equals(type)) return THROUGH_HOLE_CONNECTOR_2;
        if ("RESISTOR".equals(type)) return AXIAL_RESISTOR;
        if ("DIODE".equals(type)) return AXIAL_DIODE;
        if ("LED".equals(type)) return THROUGH_HOLE_LED;
        if ("NPN_TRANSISTOR".equals(type)) return TO92_NPN;
        if ("NMOS_TRANSISTOR".equals(type)) return TO92_NMOS;
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
        return packageOf(id, terminalIds, new String[0], connector,
            PhysicalPackage.GeometryVariantOwner.NONE);
    }

    private static PhysicalPackage developerCanaryPackageOf(String id, String[] terminalIds,
            boolean connector) {
        return developerCanaryPackageOf(id, terminalIds, new String[0], connector);
    }

    private static PhysicalPackage developerCanaryPackageOf(String id, String[] terminalIds,
            String[] internalConnections, boolean connector) {
        Vector<String> terminals = toVector(terminalIds);
        Vector<String> connections = toVector(internalConnections);
        return PhysicalPackage.developerPackageWithGenericGeometry(id, terminals, connections,
            connector);
    }

    private static PhysicalPackage packageOf(String id, String[] terminalIds, boolean connector,
            PhysicalPackage.GeometryVariantOwner geometryVariantOwner) {
        return packageOf(id, terminalIds, new String[0], connector, geometryVariantOwner);
    }

    private static PhysicalPackage packageOf(String id, String[] terminalIds,
            String[] internalConnections, boolean connector) {
        return packageOf(id, terminalIds, internalConnections, connector,
            PhysicalPackage.GeometryVariantOwner.NONE);
    }

    private static PhysicalPackage packageOf(String id, String[] terminalIds,
            String[] internalConnections, boolean connector,
            PhysicalPackage.GeometryVariantOwner geometryVariantOwner) {
        Vector<String> terminals = new Vector<String>();
        for (String terminalId : terminalIds) terminals.add(terminalId);
        Vector<String> connections = new Vector<String>();
        for (String connection : internalConnections) connections.add(connection);
        return new PhysicalPackage(id, terminals, connections, connector,
            geometryFor(id, terminals, connector), geometryVariantOwner);
    }

    private static PhysicalPackageGeometry geometryFor(String id, Vector<String> terminals,
            boolean connector) {
        if ("AXIAL_RESISTOR".equals(id))
            return axialResistor();
        if ("AXIAL_DIODE".equals(id))
            return axialDiode();
        if ("THROUGH_HOLE_LED".equals(id))
            return led();
        if ("TO92_NPN".equals(id) || "TO92_NMOS".equals(id))
            return to92(terminals);
        if ("RADIAL_ELECTROLYTIC_CAPACITOR".equals(id))
            return electrolytic();
        if ("RADIAL_CERAMIC_CAPACITOR".equals(id))
            return ceramic();
        if ("THROUGH_HOLE_CONNECTOR_2".equals(id))
            return connector(terminals);
        if ("THROUGH_HOLE_OUTPUT_HEADER_2".equals(id))
            return outputHeader();
        throw new IllegalArgumentException("No authoritative geometry declared for package: " + id);
    }

    private static Vector<String> toVector(String[] values) {
        Vector<String> result = new Vector<String>();
        for (String value : values) result.add(value);
        return result;
    }

    private static PhysicalPackageGeometry axialResistor() {
        return axialResistorVariant(220);
    }

    static PhysicalPackageGeometry axialResistorVariant(int span) {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("1", 30, 30, 50, 30, 75, 30, -1, 0, 50));
        terminals.add(terminal("2", span - 30, 30, span - 50, 30, span - 75, 30,
            1, 0, 50));
        int bodyInset = 70;
        return geometry(span, 70, terminals, new Rectangle(bodyInset, 18, span - 140, 34),
            new Rectangle(bodyInset, 18, span - 140, 34),
            new Rectangle(12, 5, span - 24, 60));
    }

    private static PhysicalPackageGeometry axialDiode() {
        return axialDiodeVariant(230);
    }

    static PhysicalPackageGeometry axialDiodeVariant(int span) {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("A", 30, 30, 50, 30, 72, 30, -1, 0, 50));
        terminals.add(terminal("K", span - 30, 30, span - 50, 30, span - 72, 30,
            1, 0, 50));
        int bodyInset = 72;
        return geometry(span, 70, terminals, new Rectangle(bodyInset, 19, span - 144, 32),
            new Rectangle(bodyInset, 19, span - 144, 32),
            new Rectangle(12, 5, span - 24, 60));
    }

    private static PhysicalPackageGeometry led() {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("A", 20, 70, 45, 42, 30, 51, 0, 1, 35));
        terminals.add(terminal("K", 60, 70, 35, 42, 50, 51, 0, 1, 35));
        return geometry(90, 100, terminals, new Rectangle(15, 11, 50, 50),
            new Rectangle(12, 8, 66, 58), new Rectangle(6, 4, 78, 92));
    }

    private static PhysicalPackageGeometry to92(Vector<String> terminalIds) {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal(terminalIds.get(0), 20, 90, 45, 62, 52, 60, -1, 0, 30));
        terminals.add(terminal(terminalIds.get(1), 60, 90, 35, 62, 72, 82, 0, 1, 32));
        terminals.add(terminal(terminalIds.get(2), 100, 90, 75, 62, 88, 82, 0, 1, 32));
        return geometry(130, 125, terminals, new Rectangle(52, 26, 56, 56),
            new Rectangle(28, 12, 84, 78), new Rectangle(5, 4, 120, 118));
    }

    private static PhysicalPackageGeometry electrolytic() {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("+", 30, 30, 42, 18, 42, 18, 0, -1, 38));
        terminals.add(terminal("-", 80, 30, 68, 18, 68, 18, 0, -1, 38));
        return geometry(120, 120, terminals, new Rectangle(24, 18, 62, 62),
            new Rectangle(15, 12, 90, 75), new Rectangle(5, 4, 110, 110));
    }

    private static PhysicalPackageGeometry ceramic() {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("1", 20, 30, 32, 20, 28, 28, 0, -1, 30));
        terminals.add(terminal("2", 60, 30, 48, 20, 52, 28, 0, -1, 30));
        return geometry(90, 90, terminals, new Rectangle(17, 16, 46, 45),
            new Rectangle(12, 16, 66, 45), new Rectangle(5, 5, 80, 80));
    }

    private static PhysicalPackageGeometry connector(Vector<String> terminalIds) {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        int pitch = terminalIds.size() == 2 ? 60 : 40;
        for (int index = 0; index < terminalIds.size(); index++)
            terminals.add(terminal(terminalIds.get(index), 90, 40 + index * pitch,
                70, 40 + index * pitch, 90, 40 + index * pitch, 1, 0, 30));
        return geometry(100, 130, terminals, new Rectangle(8, 8, 84, 114),
            new Rectangle(0, 0, 100, 130), new Rectangle(-6, -6, 112, 142));
    }

    private static PhysicalPackageGeometry outputHeader() {
        Vector<PhysicalPackageGeometry.Terminal> terminals = new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("1", 20, 30, 20, 15, 20, 30, 0, -1, 30));
        terminals.add(terminal("2", 70, 30, 70, 15, 70, 30, 0, -1, 30));
        return geometry(100, 70, terminals, new Rectangle(8, 8, 84, 54),
            new Rectangle(8, 8, 84, 54), new Rectangle(-6, 0, 112, 70));
    }

    private static PhysicalPackageGeometry geometry(int width, int height,
            Vector<PhysicalPackageGeometry.Terminal> terminals, Rectangle body,
            Rectangle keepOut, Rectangle courtyard) {
        Rectangle selection = null;
        for (PhysicalPackageGeometry.Terminal terminal : terminals) {
            selection = union(selection, terminal.getPadBounds());
            selection = union(selection, terminal.getProbeBounds());
            selection = union(selection, terminal.getLead().getBounds());
        }
        selection = union(selection, body);
        selection = new Rectangle(selection.x - 4, selection.y - 4, selection.width + 8,
            selection.height + 8);
        Rectangle dragSource = union(selection, keepOut);
        Rectangle drag = new Rectangle(dragSource.x - 6, dragSource.y - 6,
            dragSource.width + 12, dragSource.height + 12);
        return new PhysicalPackageGeometry(width, height, terminals, body, keepOut, courtyard,
            selection, drag);
    }

    private static PhysicalPackageGeometry.Terminal terminal(String id, int padX, int padY,
            int probeX, int probeY, int bodyX, int bodyY, int escapeDx, int escapeDy,
            int escapeLength) {
        Point pad = new Point(padX, padY);
        Point body = new Point(bodyX, bodyY);
        int left = Math.min(padX, bodyX) - 3;
        int top = Math.min(padY, bodyY) - 3;
        int width = Math.max(6, Math.abs(padX - bodyX) + 6);
        int height = Math.max(6, Math.abs(padY - bodyY) + 6);
        Point probe = new Point(probeX, probeY);
        Rectangle probeBounds = union(centered(probe, 46, 46), centered(pad, 26, 26));
        return new PhysicalPackageGeometry.Terminal(id, pad, centered(pad, 26, 26),
            probe, probeBounds,
            new PhysicalPackageGeometry.Lead(pad, body, new Rectangle(left, top, width, height)),
            escapeDx, escapeDy, escapeLength);
    }

    private static Rectangle centered(Point point, int width, int height) {
        return new Rectangle(point.x - width / 2, point.y - height / 2, width, height);
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        return first == null ? new Rectangle(second) : first.union(second);
    }

}
