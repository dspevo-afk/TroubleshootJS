package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Built-in typed package definitions used by the current generated families. */
final class PhysicalPackages {
    static final PhysicalPackage AXIAL_RESISTOR = packageWithCatalog("AXIAL_RESISTOR",
        new String[] { "1", "2" }, false, axialResistorVariants(), "SPAN_220",
        PhysicalPackage.GeometryVariantSelection.SEEDED_CATALOG);
    static final PhysicalPackage AXIAL_DIODE = packageWithCatalog("AXIAL_DIODE",
        new String[] { "A", "K" }, false, axialDiodeVariants(), "SPAN_230",
        PhysicalPackage.GeometryVariantSelection.SEEDED_CATALOG);
    static final PhysicalPackage THROUGH_HOLE_LED = fixedPackage("THROUGH_HOLE_LED",
        new String[] { "A", "K" }, false, led());
    static final PhysicalPackage TO92_NPN = fixedPackage("TO92_NPN",
        new String[] { "B", "C", "E" }, false, to92(new String[] { "B", "C", "E" }));
    static final PhysicalPackage TO92_NMOS = fixedPackage("TO92_NMOS",
        new String[] { "G", "D", "S" }, false, to92(new String[] { "G", "D", "S" }));
    static final PhysicalPackage RADIAL_ELECTROLYTIC_CAPACITOR = fixedPackage(
        "RADIAL_ELECTROLYTIC_CAPACITOR", new String[] { "+", "-" }, false, electrolytic());
    static final PhysicalPackage RADIAL_CERAMIC_CAPACITOR = fixedPackage(
        "RADIAL_CERAMIC_CAPACITOR", new String[] { "1", "2" }, false, ceramic());
    static final PhysicalPackage THROUGH_HOLE_CONNECTOR_2 = connectorPackage(
        "THROUGH_HOLE_CONNECTOR_2", new String[] { "1", "2" });
    /** A two-pin output header is physically a connector but not the power-input anchor. */
    static final PhysicalPackage THROUGH_HOLE_OUTPUT_HEADER_2 = fixedPackage(
        "THROUGH_HOLE_OUTPUT_HEADER_2", new String[] { "1", "2" }, false, outputHeader());

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

    private static PhysicalPackage fixedPackage(String id, String[] terminalIds,
            boolean connector, PhysicalPackageGeometry geometry) {
        Vector<PhysicalPackage.GeometryVariant> variants = new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        return packageWithCatalog(id, terminalIds, connector, variants, "DEFAULT",
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT);
    }

    private static PhysicalPackage connectorPackage(String id, String[] terminalIds) {
        PhysicalPackageGeometry geometry = connector(toVector(terminalIds));
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT_MIRRORED_X", "MIRROR_X",
            geometry.mirroredHorizontally()));
        return packageWithCatalog(id, terminalIds, true, variants, "DEFAULT",
            PhysicalPackage.GeometryVariantSelection.EDGE_ORIENTED);
    }

    private static PhysicalPackage packageWithCatalog(String id, String[] terminalIds,
            boolean connector, Vector<PhysicalPackage.GeometryVariant> variants,
            String defaultLooseVariantKey,
            PhysicalPackage.GeometryVariantSelection selection) {
        Vector<String> terminals = toVector(terminalIds);
        Vector<String> connections = new Vector<String>();
        return new PhysicalPackage(id, terminals, connections, connector,
            variants.get(0).getGeometry(), variants, defaultLooseVariantKey, selection);
    }

    private static PhysicalPackage developerCanaryPackageOf(String id, String[] terminalIds,
            boolean connector) {
        return developerCanaryPackageOf(id, terminalIds, new String[0], connector);
    }

    private static PhysicalPackage developerCanaryPackageOf(String id, String[] terminalIds,
            String[] internalConnections, boolean connector) {
        return PhysicalPackage.developerPackageWithGenericGeometry(id, toVector(terminalIds),
            toVector(internalConnections), connector);
    }

    private static Vector<PhysicalPackage.GeometryVariant> axialResistorVariants() {
        Vector<PhysicalPackage.GeometryVariant> result =
            new Vector<PhysicalPackage.GeometryVariant>();
        result.add(new PhysicalPackage.GeometryVariant("SPAN_220", "IDENTITY",
            axialResistorVariant(220)));
        result.add(new PhysicalPackage.GeometryVariant("SPAN_240", "IDENTITY",
            axialResistorVariant(240)));
        result.add(new PhysicalPackage.GeometryVariant("SPAN_260", "IDENTITY",
            axialResistorVariant(260)));
        return result;
    }

    private static Vector<PhysicalPackage.GeometryVariant> axialDiodeVariants() {
        Vector<PhysicalPackage.GeometryVariant> result =
            new Vector<PhysicalPackage.GeometryVariant>();
        result.add(new PhysicalPackage.GeometryVariant("SPAN_230", "IDENTITY",
            axialDiodeVariant(230)));
        result.add(new PhysicalPackage.GeometryVariant("SPAN_250", "IDENTITY",
            axialDiodeVariant(250)));
        return result;
    }

    static PhysicalPackageGeometry axialResistorVariant(int span) {
        if (span != 220 && span != 240 && span != 260)
            throw new IllegalArgumentException("Undeclared axial resistor span: " + span);
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("1", 30, 30, 75, 30, 82, 30, -1, 0, 50));
        terminals.add(terminal("2", span - 30, 30, span - 75, 30, span - 82, 30,
            1, 0, 50));
        int bodyInset = 70;
        return geometry(span, 70, terminals, new Rectangle(bodyInset, 18, span - 140, 34),
            new Rectangle(bodyInset, 18, span - 140, 34),
            new Rectangle(12, 5, span - 24, 60));
    }

    static PhysicalPackageGeometry axialDiodeVariant(int span) {
        if (span != 230 && span != 250)
            throw new IllegalArgumentException("Undeclared axial diode span: " + span);
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("A", 30, 30, 72, 30, 78, 30, -1, 0, 50));
        terminals.add(terminal("K", span - 30, 30, span - 72, 30, span - 78, 30,
            1, 0, 50));
        int bodyInset = 72;
        return geometry(span, 70, terminals, new Rectangle(bodyInset, 19, span - 144, 32),
            new Rectangle(bodyInset, 19, span - 144, 32),
            new Rectangle(12, 5, span - 24, 60));
    }

    private static PhysicalPackageGeometry led() {
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("A", 20, 70, 30, 51, 30, 45, 0, 1, 35));
        terminals.add(terminal("K", 60, 70, 50, 51, 50, 45, 0, 1, 35));
        return geometry(90, 100, terminals, new Rectangle(15, 11, 50, 50),
            new Rectangle(12, 8, 66, 58), new Rectangle(6, 4, 78, 92));
    }

    private static PhysicalPackageGeometry to92(String[] terminalIds) {
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal(terminalIds[0], 20, 90, 48, 65, 48, 59, -1, 0, 30));
        terminals.add(terminal(terminalIds[1], 60, 90, 80, 70, 80, 64, 0, 1, 36));
        terminals.add(terminal(terminalIds[2], 100, 90, 92, 70, 92, 64, 0, 1, 36));
        return geometry(130, 125, terminals, new Rectangle(44, 26, 64, 56),
            new Rectangle(28, 12, 84, 78), new Rectangle(5, 4, 120, 118));
    }

    private static PhysicalPackageGeometry electrolytic() {
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("+", 30, 30, 48, 8, 48, 4, 0, -1, 38));
        terminals.add(terminal("-", 80, 30, 62, 8, 62, 4, 0, -1, 38));
        return geometry(120, 120, terminals, new Rectangle(20, 8, 70, 64),
            new Rectangle(12, 4, 96, 84), new Rectangle(5, 4, 110, 110));
    }

    private static PhysicalPackageGeometry ceramic() {
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("1", 20, 30, 28, 10, 28, 6, 0, -1, 35));
        terminals.add(terminal("2", 60, 30, 52, 10, 52, 6, 0, -1, 35));
        return geometry(90, 90, terminals, new Rectangle(15, 8, 50, 53),
            new Rectangle(10, 4, 70, 65), new Rectangle(5, 0, 80, 90));
    }

    private static PhysicalPackageGeometry connector(Vector<String> terminalIds) {
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        int pitch = terminalIds.size() == 2 ? 60 : 40;
        for (int index = 0; index < terminalIds.size(); index++) {
            int y = checkedInt(40L + (long) index * pitch);
            terminals.add(terminal(terminalIds.get(index), 90, y, 60, y, 54, y,
                1, 0, 30));
        }
        return geometry(100, 130, terminals, new Rectangle(8, 8, 84, 114),
            new Rectangle(0, 0, 100, 130), new Rectangle(-6, -6, 112, 142));
    }

    private static PhysicalPackageGeometry outputHeader() {
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal("1", 20, 30, 20, 10, 20, 6, 0, -1, 35));
        terminals.add(terminal("2", 70, 30, 70, 10, 70, 6, 0, -1, 35));
        return geometry(100, 70, terminals, new Rectangle(8, 8, 84, 54),
            new Rectangle(8, 8, 84, 54), new Rectangle(-6, 0, 112, 70));
    }

    private static PhysicalPackageGeometry geometry(int width, int height,
            Vector<PhysicalPackageGeometry.Terminal> terminals, Rectangle body,
            Rectangle keepOut, Rectangle courtyard) {
        Rectangle selection = null;
        for (PhysicalPackageGeometry.Terminal terminal : terminals) {
            selection = union(selection, terminal.getPadBounds());
            selection = union(selection, terminal.getBoardPadProbeBounds());
            selection = union(selection, terminal.getConnectedLead().getBounds());
            selection = union(selection, terminal.getLiftedLead().getBounds());
            selection = union(selection, terminal.getComponentLeadProbeBounds());
            selection = union(selection, terminal.getComponentLeadProbeBounds(true));
        }
        selection = union(selection, body);
        selection = expand(selection, 4);
        Rectangle dragSource = union(selection, keepOut);
        Rectangle drag = expand(dragSource, 6);
        return new PhysicalPackageGeometry(width, height, terminals, body, keepOut, courtyard,
            selection, drag);
    }

    private static PhysicalPackageGeometry.Terminal terminal(String id, int padX, int padY,
            int connectedBodyX, int connectedBodyY, int liftedEndX, int liftedEndY,
            int escapeDx, int escapeDy, int escapeLength) {
        Point pad = new Point(padX, padY);
        Rectangle padBounds = centered(pad, 26, 26);
        Rectangle boardProbe = centered(pad, 30, 30);
        Point connectedBody = new Point(connectedBodyX, connectedBodyY);
        Point liftedEnd = new Point(liftedEndX, liftedEndY);
        PhysicalPackageGeometry.Lead connected = lead(pad, connectedBody, connectedBody);
        PhysicalPackageGeometry.Lead lifted = lead(liftedEnd, connectedBody, liftedEnd);
        return new PhysicalPackageGeometry.Terminal(id, pad, padBounds, pad, boardProbe,
            connected, lifted, escapeDx, escapeDy, escapeLength);
    }

    private static PhysicalPackageGeometry.Lead lead(Point endPoint, Point bodyPoint,
            Point componentProbeCenter) {
        int left = checkedInt(Math.min((long) endPoint.x, bodyPoint.x) - 3L);
        int top = checkedInt(Math.min((long) endPoint.y, bodyPoint.y) - 3L);
        int right = checkedInt(Math.max((long) endPoint.x, bodyPoint.x) + 3L);
        int bottom = checkedInt(Math.max((long) endPoint.y, bodyPoint.y) + 3L);
        return new PhysicalPackageGeometry.Lead(endPoint, bodyPoint,
            rectangleFromEdges(left, top, right, bottom), componentProbeCenter,
            centered(componentProbeCenter, 8, 8));
    }

    private static Rectangle centered(Point point, int width, int height) {
        return new Rectangle(checkedInt((long) point.x - width / 2L),
            checkedInt((long) point.y - height / 2L), width, height);
    }

    private static Rectangle expand(Rectangle value, int margin) {
        return new Rectangle(checkedInt((long) value.x - margin),
            checkedInt((long) value.y - margin), checkedInt((long) value.width + margin * 2L),
            checkedInt((long) value.height + margin * 2L));
    }

    private static Rectangle rectangleFromEdges(int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top)
            throw new IllegalArgumentException("Invalid package lead bounds");
        return new Rectangle(left, top, checkedInt((long) right - left),
            checkedInt((long) bottom - top));
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        if (second == null)
            throw new IllegalArgumentException("Missing package geometry bounds");
        if (first == null)
            return new Rectangle(second);
        long left = Math.min((long) first.x, second.x);
        long top = Math.min((long) first.y, second.y);
        long right = Math.max((long) first.x + first.width,
            (long) second.x + second.width);
        long bottom = Math.max((long) first.y + first.height,
            (long) second.y + second.height);
        return new Rectangle(checkedInt(left), checkedInt(top), checkedInt(right - left),
            checkedInt(bottom - top));
    }

    private static Vector<String> toVector(String[] values) {
        Vector<String> result = new Vector<String>();
        if (values != null)
            for (String value : values)
                result.add(value);
        return result;
    }

    private static int checkedInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Package geometry integer overflow: " + value);
        return (int) value;
    }
}
