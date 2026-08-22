package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Developer-only finite proof for the approved NMOS fixed-layout realization. */
final class NmosFixedLayoutDeveloperVerifier {
    private static final String[] VARIANT_KEYS = {
        "SPAN_220", "SPAN_240", "SPAN_260"
    };
    private static final String[] TRACE_NETS = {
        "LOAD_SUPPLY", "LOAD_NODE", "DRAIN", "CONTROL_INPUT", "CONTROL_INPUT", "GND",
        "GND", "GND"
    };
    private static final String[] TRACE_STARTS = {
        "J1.1", "RLOAD.2", "LED1.K", "J2.1", "J2.1", "J1.2", "J1.2", "J1.2"
    };
    private static final String[] TRACE_ENDS = {
        "RLOAD.1", "LED1.A", "Q1.D", "RPD.1", "Q1.G", "J2.2", "RPD.2", "Q1.S"
    };

    private NmosFixedLayoutDeveloperVerifier() { }

    static void verify() {
        GeneratedBoardInstance fixture = new NmosLowSideSwitchGenerator()
            .generateForFaultVerification(0, GeneratedFaultType.NMOS_DS_OPEN);
        TroubleshootBoard board = fixture.getBoard();
        BoardPhysicalSpecifications specifications = fixture.getPhysicalSpecifications();
        require(board != null && specifications != null,
            "NMOS fixed-layout matrix fixture is missing board or specifications");
        board.validate();

        int caseCount = 0;
        for (String rloadKey : VARIANT_KEYS) {
            int rloadSpan = span(rloadKey);
            for (String rpdKey : VARIANT_KEYS) {
                int rpdSpan = span(rpdKey);
                String normalizedReference = null;
                for (int variationMode = 0; variationMode < 4; variationMode++) {
                    PcbBoardLayout first = NmosLowSideSwitchPcbLayoutFactory
                        .createForDeveloperVerification(board, specifications, variationMode,
                            rloadKey, rpdKey);
                    first.validateGeometry(board);
                    verifyCanonicalVariants(first, rloadKey, rpdKey);
                    verifyTraceWitness(first, board, variationMode, rloadSpan, rpdSpan);
                    verifyQ1Provider(first, board, variationMode);
                    verifyRouteQuality(first, board);

                    PcbBoardLayout second = NmosLowSideSwitchPcbLayoutFactory
                        .createForDeveloperVerification(board, specifications, variationMode,
                            rloadKey, rpdKey);
                    second.validateGeometry(board);
                    require(first.geometryFingerprint().equals(second.geometryFingerprint()),
                        "NMOS fixed-layout matrix is not deterministic: mode=" +
                            variationMode + " variants=" + rloadKey + "/" + rpdKey);

                    String normalized = normalizedGeometrySignature(first);
                    if (normalizedReference == null)
                        normalizedReference = normalized;
                    else
                        require(normalizedReference.equals(normalized),
                            "NMOS origin class changed normalized geometry: variants=" +
                                rloadKey + "/" + rpdKey + " mode=" + variationMode);
                    caseCount++;
                }
            }
        }
        require(caseCount == 3 * 3 * 4,
            "NMOS fixed-layout matrix did not execute exactly 36 cases: " + caseCount);
        String evidence = "PASS:NMOS_FIXED_LAYOUT_MATRIX:cases=" + caseCount + "/36" +
            ";variantTuples=9;originClasses=4";
        CirSim.console(evidence);
        System.out.println(evidence);
    }

    private static void verifyCanonicalVariants(PcbBoardLayout layout, String rloadKey,
            String rpdKey) {
        verifyCanonicalVariant(layout, "RLOAD", PhysicalPackages.AXIAL_RESISTOR, rloadKey);
        verifyCanonicalVariant(layout, "RPD", PhysicalPackages.AXIAL_RESISTOR, rpdKey);
        verifyCanonicalVariant(layout, "J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "DEFAULT");
        verifyCanonicalVariant(layout, "J2", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "DEFAULT");
        verifyCanonicalVariant(layout, "LED1", PhysicalPackages.THROUGH_HOLE_LED, "DEFAULT");
        verifyCanonicalVariant(layout, "Q1", PhysicalPackages.TO92_NMOS, "DEFAULT");
        require(!PhysicalPackages.TO92_NMOS.isInternallyConnected("G", "D") &&
            !PhysicalPackages.TO92_NMOS.isInternallyConnected("G", "S") &&
            !PhysicalPackages.TO92_NMOS.isInternallyConnected("D", "S"),
            "TO92_NMOS unexpectedly provides internal terminal connectivity");
    }

    private static void verifyCanonicalVariant(PcbBoardLayout layout, String componentId,
            PhysicalPackage expectedPackage, String expectedKey) {
        PcbComponentPlacement placement = layout.getComponent(componentId);
        require(placement != null && placement.getPhysicalPackage() == expectedPackage &&
            expectedKey.equals(placement.getGeometryVariantKey()) &&
            "IDENTITY".equals(placement.getGeometryTransformKey()),
            "NMOS matrix selected the wrong canonical realization: " + componentId);
        PhysicalPackage.GeometryVariant variant = expectedPackage.getGeometryVariant(expectedKey);
        require(variant != null && placement.getPhysicalGeometry() == variant.getGeometry() &&
            placement.getGeometryRealization().getPhysicalGeometry() == variant.getGeometry() &&
            expectedKey.equals(placement.getGeometryRealization().getGeometryVariantKey()),
            "NMOS matrix did not preserve the canonical geometry object: " + componentId);
    }

    private static void verifyTraceWitness(PcbBoardLayout layout, TroubleshootBoard board,
            int variationMode, int rloadSpan, int rpdSpan) {
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        require(traces.size() == TRACE_NETS.length,
            "NMOS fixed layout does not contain exactly eight approved traces");
        int s = variationMode * 10;
        int translationX = layout.getPad("J1.1").getX() - (170 + s);
        int translationY = layout.getPad("J1.1").getY() - 120;
        verifyPadCenters(layout, s, rloadSpan, rpdSpan, translationX, translationY);

        for (int index = 0; index < TRACE_NETS.length; index++) {
            PcbTraceGeometry trace = traces.get(index);
            require(TRACE_NETS[index].equals(trace.getNetId()) &&
                    TRACE_STARTS[index].equals(trace.getStartPadId()) &&
                    TRACE_ENDS[index].equals(trace.getEndPadId()),
                "NMOS fixed-layout trace endpoint order changed at index " + index);
            int[][] expected = expectedPoints(index, s, rloadSpan, rpdSpan);
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            require(x.length == expected.length,
                "NMOS fixed-layout route point count changed at trace " + index);
            for (int point = 0; point < expected.length; point++)
                require(x[point] == expected[point][0] + translationX &&
                        y[point] == expected[point][1] + translationY,
                    "NMOS fixed-layout coordinate witness changed at trace " + index +
                        " point " + point);
            verifyEndpointEscape(layout, trace, true);
            verifyEndpointEscape(layout, trace, false);
        }

        String[][] membership = {
            { "LOAD_SUPPLY", "J1.1", "RLOAD.1" },
            { "LOAD_NODE", "RLOAD.2", "LED1.A" },
            { "DRAIN", "LED1.K", "Q1.D" },
            { "CONTROL_INPUT", "J2.1", "RPD.1", "Q1.G" },
            { "GND", "J1.2", "J2.2", "RPD.2", "Q1.S" }
        };
        for (String[] netMembership : membership) {
            Vector<String> actual = board.getNet(netMembership[0]).getPadIds();
            require(actual.size() == netMembership.length - 1,
                "NMOS logical pad membership count changed for " + netMembership[0]);
            for (int index = 1; index < netMembership.length; index++)
                require(actual.contains(netMembership[index]),
                    "NMOS logical pad membership missing " + netMembership[index] + " on " +
                        netMembership[0]);
        }

        verifyAllPadsRepresented(layout, board);
        verifyControlPhysicalUnion(layout, board);
        verifyEscapes(layout);
    }

    private static void verifyPadCenters(PcbBoardLayout layout, int s, int a, int b,
            int translationX, int translationY) {
        int[][] centers = {
            { 170 + s, 120 }, { 170 + s, 180 }, { 170 + s, 440 }, { 170 + s, 500 },
            { 380 + s, 230 }, { 320 + a + s, 230 }, { 330 + s, 350 },
            { 270 + b + s, 350 }, { 520 + s, 140 }, { 560 + s, 140 },
            { 920 + s, 190 }, { 960 + s, 190 }, { 1000 + s, 190 }
        };
        String[] padIds = {
            "J1.1", "J1.2", "J2.1", "J2.2", "RLOAD.1", "RLOAD.2", "RPD.1", "RPD.2",
            "LED1.A", "LED1.K", "Q1.G", "Q1.D", "Q1.S"
        };
        for (int index = 0; index < padIds.length; index++) {
            PcbPadPlacement pad = layout.getPad(padIds[index]);
            require(pad != null && pad.getX() == centers[index][0] + translationX &&
                    pad.getY() == centers[index][1] + translationY,
                "NMOS named pad center changed: " + padIds[index]);
        }
    }

    private static int[][] expectedPoints(int traceIndex, int s, int a, int b) {
        if (traceIndex == 0)
            return points(170 + s, 120, 200 + s, 120, 330 + s, 120, 330 + s, 230,
                380 + s, 230);
        if (traceIndex == 1)
            return points(320 + a + s, 230, 370 + a + s, 230, 370 + a + s, 195,
                520 + s, 195, 520 + s, 140);
        if (traceIndex == 2)
            return points(560 + s, 140, 560 + s, 175, 390 + a + s, 175,
                390 + a + s, 270, 960 + s, 270, 960 + s, 226, 960 + s, 190);
        if (traceIndex == 3)
            return points(170 + s, 440, 200 + s, 440, 280 + s, 440, 280 + s, 350,
                330 + s, 350);
        if (traceIndex == 4)
            return points(170 + s, 440, 200 + s, 440, 1032 + s, 440, 1032 + s, 98,
                890 + s, 98, 890 + s, 190, 920 + s, 190);
        if (traceIndex == 5)
            return points(170 + s, 180, 200 + s, 180, 200 + s, 250, 60 + s, 250,
                60 + s, 550, 200 + s, 550, 200 + s, 500, 170 + s, 500);
        if (traceIndex == 6)
            return points(170 + s, 180, 200 + s, 180, 200 + s, 250, 60 + s, 250,
                60 + s, 300, 320 + b + s, 300, 320 + b + s, 350,
                270 + b + s, 350);
        if (traceIndex == 7)
            return points(170 + s, 180, 200 + s, 180, 200 + s, 250, 60 + s, 250,
                60 + s, 300, 1000 + s, 300, 1000 + s, 226, 1000 + s, 190);
        throw new IllegalArgumentException("Unknown NMOS trace index: " + traceIndex);
    }

    private static int[][] points(int... values) {
        require(values.length % 2 == 0, "NMOS coordinate witness is not a point list");
        int[][] result = new int[values.length / 2][2];
        for (int index = 0; index < result.length; index++) {
            result[index][0] = values[index * 2];
            result[index][1] = values[index * 2 + 1];
        }
        return result;
    }

    private static void verifyAllPadsRepresented(PcbBoardLayout layout, TroubleshootBoard board) {
        for (String padId : board.getPadIds()) {
            require(layout.getPad(padId) != null,
                "NMOS logical pad is absent from the physical layout: " + padId);
            boolean endpoint = false;
            for (PcbTraceGeometry trace : layout.getTraces())
                endpoint |= padId.equals(trace.getStartPadId()) ||
                    padId.equals(trace.getEndPadId());
            require(endpoint, "NMOS logical pad is not represented by a trace endpoint: " + padId);
        }
    }

    private static void verifyControlPhysicalUnion(PcbBoardLayout layout,
            TroubleshootBoard board) {
        // validateGeometry above is the real physical-union oracle. These rooted
        // branches prove that the three-pad CONTROL_INPUT union is copper, not a
        // BoardNet declaration or package-internal shortcut.
        layout.validateGeometry(board);
        PcbTraceGeometry rpdBranch = layout.getTraces().get(3);
        PcbTraceGeometry gateBranch = layout.getTraces().get(4);
        require("CONTROL_INPUT".equals(rpdBranch.getNetId()) &&
                "CONTROL_INPUT".equals(gateBranch.getNetId()) &&
                "J2.1".equals(rpdBranch.getStartPadId()) &&
                "J2.1".equals(gateBranch.getStartPadId()) &&
                "RPD.1".equals(rpdBranch.getEndPadId()) &&
                "Q1.G".equals(gateBranch.getEndPadId()),
            "NMOS CONTROL_INPUT does not have the two required rooted branches");
        require(rpdBranch.getXPoints()[1] == gateBranch.getXPoints()[1] &&
                rpdBranch.getYPoints()[1] == gateBranch.getYPoints()[1],
            "NMOS CONTROL_INPUT branches do not share the J2.1 copper root");
    }

    private static void verifyEscapes(PcbBoardLayout layout) {
        requireEscape(layout.getPad("J1.1"), 1, 0, 30);
        requireEscape(layout.getPad("J1.2"), 1, 0, 30);
        requireEscape(layout.getPad("J2.1"), 1, 0, 30);
        requireEscape(layout.getPad("J2.2"), 1, 0, 30);
        requireEscape(layout.getPad("RLOAD.1"), -1, 0, 50);
        requireEscape(layout.getPad("RLOAD.2"), 1, 0, 50);
        requireEscape(layout.getPad("RPD.1"), -1, 0, 50);
        requireEscape(layout.getPad("RPD.2"), 1, 0, 50);
        requireEscape(layout.getPad("LED1.A"), 0, 1, 35);
        requireEscape(layout.getPad("LED1.K"), 0, 1, 35);
        requireEscape(layout.getPad("Q1.G"), -1, 0, 30);
        requireEscape(layout.getPad("Q1.D"), 0, 1, 36);
        requireEscape(layout.getPad("Q1.S"), 0, 1, 36);
    }

    private static void requireEscape(PcbPadPlacement pad, int dx, int dy, int length) {
        require(pad != null && pad.getEscapeDx() == dx && pad.getEscapeDy() == dy &&
                pad.getEscapeLength() == length,
            "NMOS package escape changed for " + (pad == null ? "null" : pad.getPadId()));
    }

    private static void verifyQ1Provider(PcbBoardLayout layout, TroubleshootBoard board,
            int variationMode) {
        PcbComponentPlacement actualPlacement = layout.getComponent("Q1");
        require(actualPlacement != null && actualPlacement.getPhysicalPackage() ==
                PhysicalPackages.TO92_NMOS,
            "NMOS Q1 placement lost the canonical TO92_NMOS package");
        PcbFootprint expected = StandardPcbFootprintProviders.createRegistry().create(
            board.getComponent("Q1"), actualPlacement.getX(), actualPlacement.getY(),
            new java.util.Random(variationMode + 5), layout.getBoardOutline());
        require(samePlacement(actualPlacement, expected.getPlacement()),
            "NMOS Q1 placement/body/courtyard diverges from registered provider");
        String[] terminalIds = { "Q1.G", "Q1.D", "Q1.S" };
        Vector<PcbPadPlacement> expectedPads = expected.getPads();
        require(expectedPads.size() == terminalIds.length,
            "NMOS provider did not expose three stable terminals");
        for (int index = 0; index < terminalIds.length; index++)
            require(expectedPads.get(index).getPadId().equals(terminalIds[index]) &&
                    samePad(layout.getPad(terminalIds[index]), expectedPads.get(index)),
                "NMOS Q1 provider pad diverged: " + terminalIds[index]);
    }

    private static void verifyRouteQuality(PcbBoardLayout layout, TroubleshootBoard board) {
        layout.validateRouteQuality();
        layout.validateTraceClearance();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            require(layout.getTraceBendCount(trace) <= 16 &&
                    layout.getTraceDetourRatio(trace) <= 3.0,
                "NMOS route-quality limit changed: " + trace.getNetId());
        }
        require(layout.getRouteQualityScore(board) >= 0 && layout.getCompactnessMetric() >= .40 &&
                layout.getLargestEdgeMargin() <= 34,
            "NMOS fixed route quality envelope changed");
    }

    private static void verifyEndpointEscape(PcbBoardLayout layout, PcbTraceGeometry trace,
            boolean start) {
        String padId = start ? trace.getStartPadId() : trace.getEndPadId();
        PcbPadPlacement pad = layout.getPad(padId);
        if (pad.getEscapeLength() == 0)
            return;
        int[] x = trace.getXPoints();
        int[] y = trace.getYPoints();
        int index = start ? 1 : x.length - 2;
        int dx = start ? x[index] - x[0] : x[x.length - 1] - x[index];
        int dy = start ? y[index] - y[0] : y[y.length - 1] - y[index];
        int signX = dx == 0 ? 0 : dx < 0 ? -1 : 1;
        int signY = dy == 0 ? 0 : dy < 0 ? -1 : 1;
        int expectedX = start ? pad.getEscapeDx() : -pad.getEscapeDx();
        int expectedY = start ? pad.getEscapeDy() : -pad.getEscapeDy();
        require(signX == expectedX && signY == expectedY,
            "NMOS trace does not use pad escape direction: " + trace.getNetId() + " / " +
                padId);
    }

    private static String normalizedGeometrySignature(PcbBoardLayout layout) {
        Rectangle outline = layout.getBoardOutline();
        int originX = outline.x;
        int originY = outline.y;
        StringBuilder result = new StringBuilder();
        result.append("canvas=").append(layout.getWidth()).append('x').append(layout.getHeight())
            .append("|board=0,0,").append(outline.width).append(',').append(outline.height)
            .append('|');
        Rectangle tray = layout.getPartsTray();
        result.append("tray=");
        appendRectangle(result, tray, originX, originY);

        Vector<PcbComponentPlacement> components = layout.getComponents();
        Collections.sort(components, new Comparator<PcbComponentPlacement>() {
            public int compare(PcbComponentPlacement first, PcbComponentPlacement second) {
                return first.getComponentId().compareTo(second.getComponentId());
            }
        });
        for (PcbComponentPlacement component : components) {
            result.append("|C:").append(component.getComponentId()).append('@')
                .append(component.getX() - originX).append(',')
                .append(component.getY() - originY).append(',').append(component.getWidth())
                .append(',').append(component.getHeight()).append(':')
                .append(component.getPhysicalPackage().getId()).append(':')
                .append(component.getGeometryVariantKey()).append(':')
                .append(component.getGeometryTransformKey());
            appendRectangle(result, component.getBodyBounds(), originX, originY);
            appendRectangle(result, component.getKeepOut(), originX, originY);
            appendRectangle(result, component.getRoutingCourtyard(), originX, originY);
            appendRectangle(result, component.getSelectionEnvelope(), originX, originY);
            appendRectangle(result, component.getDragEnvelope(), originX, originY);
        }

        Vector<PcbPadPlacement> pads = layout.getPads();
        Collections.sort(pads, new Comparator<PcbPadPlacement>() {
            public int compare(PcbPadPlacement first, PcbPadPlacement second) {
                return first.getPadId().compareTo(second.getPadId());
            }
        });
        for (PcbPadPlacement pad : pads) {
            result.append("|P:").append(pad.getPadId()).append('@')
                .append(pad.getX() - originX).append(',').append(pad.getY() - originY)
                .append(':').append(pad.getEscapeDx()).append(',').append(pad.getEscapeDy())
                .append(',').append(pad.getEscapeLength());
            appendRectangle(result, pad.getPadBounds(), originX, originY);
            appendRectangle(result, pad.getProbeBounds(), originX, originY);
        }

        Vector<PcbTraceGeometry> traces = layout.getTraces();
        Collections.sort(traces, new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry first, PcbTraceGeometry second) {
                int result = first.getNetId().compareTo(second.getNetId());
                if (result != 0)
                    return result;
                result = first.getStartPadId().compareTo(second.getStartPadId());
                if (result != 0)
                    return result;
                return first.getEndPadId().compareTo(second.getEndPadId());
            }
        });
        for (PcbTraceGeometry trace : traces) {
            result.append("|T:").append(trace.getNetId()).append(':')
                .append(trace.getStartPadId()).append('-').append(trace.getEndPadId());
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            for (int index = 0; index < x.length; index++)
                result.append('@').append(x[index] - originX).append(',')
                    .append(y[index] - originY);
        }

        Vector<PcbSilkscreenLabel> labels = layout.getSilkscreenLabels();
        Collections.sort(labels, new Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel first, PcbSilkscreenLabel second) {
                return first.getId().compareTo(second.getId());
            }
        });
        for (PcbSilkscreenLabel label : labels) {
            result.append("|L:").append(label.getId()).append(':').append(label.getText())
                .append(':');
            appendRectangle(result, label.getBounds(), originX, originY);
            result.append(':').append(label.getFontSize()).append(':').append(label.isBold())
                .append(':').append(label.getTargetPadId());
        }
        return result.toString();
    }

    private static int span(String variantKey) {
        return Integer.parseInt(variantKey.substring("SPAN_".length()));
    }

    private static boolean samePlacement(PcbComponentPlacement first,
            PcbComponentPlacement second) {
        return first.getComponentId().equals(second.getComponentId()) &&
            first.getX() == second.getX() && first.getY() == second.getY() &&
            first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight() &&
            first.getPhysicalPackage() == second.getPhysicalPackage() &&
            first.getPhysicalGeometry() == second.getPhysicalGeometry() &&
            first.getKeepOut().equals(second.getKeepOut()) &&
            first.getRoutingCourtyard().equals(second.getRoutingCourtyard());
    }

    private static boolean samePad(PcbPadPlacement first, PcbPadPlacement second) {
        return first != null && second != null && first.getPadId().equals(second.getPadId()) &&
            first.getX() == second.getX() && first.getY() == second.getY() &&
            first.getEscapeDx() == second.getEscapeDx() &&
            first.getEscapeDy() == second.getEscapeDy() &&
            first.getEscapeLength() == second.getEscapeLength() &&
            first.getPadBounds().equals(second.getPadBounds()) &&
            first.getProbeBounds().equals(second.getProbeBounds());
    }

    private static void appendRectangle(StringBuilder result, Rectangle rectangle, int originX,
            int originY) {
        result.append("rect=").append(rectangle.x - originX).append(',')
            .append(rectangle.y - originY).append(',').append(rectangle.width).append(',')
            .append(rectangle.height).append(';');
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
