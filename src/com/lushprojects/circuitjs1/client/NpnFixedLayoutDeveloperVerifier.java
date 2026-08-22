package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Developer-only finite proof for the approved NPN fixed-layout realization. */
final class NpnFixedLayoutDeveloperVerifier {
    private static final String[] VARIANT_KEYS = {
        "SPAN_220", "SPAN_240", "SPAN_260"
    };
    private static final String[] TRACE_NETS = {
        "LOAD_SUPPLY", "CONTROL_INPUT", "LOAD_NODE", "COLLECTOR", "BASE", "BASE", "GND",
        "GND", "GND"
    };
    private static final String[] TRACE_STARTS = {
        "J1.1", "J2.1", "RLOAD.2", "LED1.K", "Q1.B", "Q1.B", "J1.2", "J1.2",
        "J1.2"
    };
    private static final String[] TRACE_ENDS = {
        "RLOAD.1", "RB.1", "LED1.A", "Q1.C", "RPD.1", "RB.2", "J2.2", "RPD.2",
        "Q1.E"
    };

    private NpnFixedLayoutDeveloperVerifier() { }

    static void verify() {
        GeneratedBoardInstance fixture = new NpnLowSideSwitchGenerator()
            .generateForFaultVerification(0, GeneratedFaultType.TRANSISTOR_CE_OPEN);
        TroubleshootBoard board = fixture.getBoard();
        BoardPhysicalSpecifications specifications = fixture.getPhysicalSpecifications();
        require(board != null && specifications != null,
            "NPN fixed-layout matrix fixture is missing board or specifications");
        board.validate();

        int caseCount = 0;
        for (String rloadKey : VARIANT_KEYS) {
            for (String rbKey : VARIANT_KEYS) {
                for (String rpdKey : VARIANT_KEYS) {
                    String normalizedReference = null;
                    for (int variationMode = 0; variationMode < 4; variationMode++) {
                        PcbBoardLayout first = NpnLowSideSwitchPcbLayoutFactory
                            .createForDeveloperVerification(board, specifications, variationMode,
                                rloadKey, rbKey, rpdKey);
                        first.validateGeometry(board);
                        verifyCanonicalVariants(first, rloadKey, rbKey, rpdKey);
                        verifyTraceWitness(first, board);

                        PcbBoardLayout second = NpnLowSideSwitchPcbLayoutFactory
                            .createForDeveloperVerification(board, specifications, variationMode,
                                rloadKey, rbKey, rpdKey);
                        second.validateGeometry(board);
                        require(first.geometryFingerprint().equals(second.geometryFingerprint()),
                            "NPN fixed-layout matrix is not deterministic: mode=" +
                                variationMode + " variants=" + rloadKey + "/" + rbKey + "/" +
                                rpdKey);

                        String normalized = normalizedGeometrySignature(first);
                        if (normalizedReference == null)
                            normalizedReference = normalized;
                        else
                            require(normalizedReference.equals(normalized),
                                "NPN origin class changed normalized geometry: variants=" +
                                    rloadKey + "/" + rbKey + "/" + rpdKey + " mode=" +
                                    variationMode);
                        caseCount++;
                    }
                }
            }
        }
        require(caseCount == 3 * 3 * 3 * 4,
            "NPN fixed-layout matrix did not execute exactly 108 cases: " + caseCount);
        String evidence = "PASS:NPN_FIXED_LAYOUT_MATRIX:cases=" + caseCount + "/108" +
            ";variantTuples=27;originClasses=4";
        CirSim.console(evidence);
        System.out.println(evidence);
    }

    private static void verifyCanonicalVariants(PcbBoardLayout layout, String rloadKey,
            String rbKey, String rpdKey) {
        verifyCanonicalVariant(layout, "RLOAD", rloadKey);
        verifyCanonicalVariant(layout, "RB", rbKey);
        verifyCanonicalVariant(layout, "RPD", rpdKey);
    }

    private static void verifyCanonicalVariant(PcbBoardLayout layout, String componentId,
            String expectedKey) {
        PcbComponentPlacement placement = layout.getComponent(componentId);
        require(placement != null &&
                placement.getPhysicalPackage() == PhysicalPackages.AXIAL_RESISTOR &&
                expectedKey.equals(placement.getGeometryVariantKey()),
            "NPN matrix selected the wrong resistor variant: " + componentId + " expected=" +
                expectedKey);
        PhysicalPackage.GeometryVariant variant = placement.getPhysicalPackage()
            .getGeometryVariant(expectedKey);
        require(variant != null && placement.getPhysicalGeometry() == variant.getGeometry() &&
                placement.getGeometryRealization().getPhysicalGeometry() == variant.getGeometry(),
            "NPN matrix did not use the canonical resistor geometry object: " + componentId);
    }

    private static void verifyTraceWitness(PcbBoardLayout layout, TroubleshootBoard board) {
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        require(traces.size() == TRACE_NETS.length,
            "NPN fixed layout does not contain exactly nine approved traces");
        for (int index = 0; index < TRACE_NETS.length; index++) {
            PcbTraceGeometry trace = traces.get(index);
            require(TRACE_NETS[index].equals(trace.getNetId()) &&
                    TRACE_STARTS[index].equals(trace.getStartPadId()) &&
                    TRACE_ENDS[index].equals(trace.getEndPadId()),
                "NPN fixed-layout trace endpoint order changed at index " + index);
        }

        String[][] membership = {
            { "LOAD_SUPPLY", "J1.1", "RLOAD.1" },
            { "CONTROL_INPUT", "J2.1", "RB.1" },
            { "LOAD_NODE", "RLOAD.2", "LED1.A" },
            { "COLLECTOR", "LED1.K", "Q1.C" },
            { "BASE", "Q1.B", "RB.2", "RPD.1" },
            { "GND", "J1.2", "J2.2", "RPD.2", "Q1.E" }
        };
        for (String[] netMembership : membership) {
            Vector<String> actual = board.getNet(netMembership[0]).getPadIds();
            require(actual.size() == netMembership.length - 1,
                "NPN logical pad membership count changed for " + netMembership[0]);
            for (int index = 1; index < netMembership.length; index++)
                require(actual.contains(netMembership[index]),
                    "NPN logical pad membership missing " + netMembership[index] + " on " +
                        netMembership[0]);
        }
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
            for (int terminal = 0; terminal < component.getPhysicalPackage().getTerminalCount();
                    terminal++) {
                appendPoint(result, component.getPadPoint(terminal), originX, originY);
                appendRectangle(result, component.getPadBounds(terminal), originX, originY);
                appendRectangle(result, component.getLeadBounds(terminal), originX, originY);
                appendRectangle(result, component.getLeadBounds(terminal, true), originX,
                    originY);
            }
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

    private static void appendPoint(StringBuilder result, Point point, int originX, int originY) {
        result.append("point=").append(point.x - originX).append(',').append(point.y - originY)
            .append(';');
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
