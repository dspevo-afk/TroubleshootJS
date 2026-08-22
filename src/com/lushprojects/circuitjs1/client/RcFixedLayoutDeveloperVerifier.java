package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Developer-only finite proof for the approved RC fixed-layout realization. */
final class RcFixedLayoutDeveloperVerifier {
    private static final String[] VARIANT_KEYS = {
        "SPAN_220", "SPAN_240", "SPAN_260"
    };
    private static final String[] TRACE_NETS = {
        "VIN", "VIN", "RC_OUT", "RC_OUT", "RC_OUT", "GND", "GND", "GND", "GND"
    };
    private static final String[] TRACE_STARTS = {
        "J1.1", "J1.1", "R1.2", "R1.2", "R1.2", "J1.2", "J1.2", "J1.2", "J1.2"
    };
    private static final String[] TRACE_ENDS = {
        "R1.1", "C2.1", "C1.+", "J2.1", "R2.1", "C1.-", "J2.2", "C2.2", "R2.2"
    };

    private RcFixedLayoutDeveloperVerifier() { }

    static void verify() {
        GeneratedBoardInstance fixture = new RcDelayGenerator()
            .generateForFaultVerification(0, GeneratedFaultType.CAPACITOR_OPEN);
        TroubleshootBoard board = fixture.getBoard();
        require(board != null, "RC fixed-layout matrix fixture is missing board");
        board.validate();

        verifySeamNegativeCanaries(board);
        int caseCount = 0;
        for (String r1Key : VARIANT_KEYS) {
            for (String r2Key : VARIANT_KEYS) {
                String normalizedReference = null;
                for (int variationMode = 0; variationMode < 4; variationMode++) {
                    try {
                        PcbBoardLayout first = createCase(board, variationMode, r1Key, r2Key);
                        validateCase(first, board, variationMode, r1Key, r2Key,
                            "independent validateGeometry");
                        verifyCanonicalVariants(first, r1Key, r2Key);
                        verifyTraceWitness(first, board);
                        verifyRouteQuality(first, board);

                        PcbBoardLayout second = createCase(board, variationMode, r1Key, r2Key);
                        validateCase(second, board, variationMode, r1Key, r2Key,
                            "duplicate validateGeometry");
                        verifyCanonicalVariants(second, r1Key, r2Key);
                        require(first.geometryFingerprint().equals(second.geometryFingerprint()),
                            caseMessage(variationMode, r1Key, r2Key,
                                "deterministic duplicate full geometry fingerprint diverged"));
                        verifyCanonicalRealizationParity(first, second, variationMode, r1Key,
                            r2Key);

                        String normalized = normalizedGeometrySignature(first);
                        if (normalizedReference == null)
                            normalizedReference = normalized;
                        else
                            require(normalizedReference.equals(normalized),
                                caseMessage(variationMode, r1Key, r2Key,
                                    "origin class changed normalized full geometry"));
                        caseCount++;
                    } catch (RuntimeException failure) {
                        if (failure.getMessage() != null &&
                                failure.getMessage().indexOf("RC fixed-layout case r1=") == 0)
                            throw failure;
                        throw caseFailure(variationMode, r1Key, r2Key,
                            "matrix witness/quality", failure);
                    }
                }
            }
        }
        require(caseCount == 3 * 3 * 4,
            "RC fixed-layout matrix did not execute exactly 36 cases: " + caseCount);
        verifyProductionSeedParity(board);
        String evidence = "PASS:RC_FIXED_LAYOUT_MATRIX:cases=" + caseCount + "/36" +
            ";variantTuples=9;originClasses=4";
        CirSim.console(evidence);
        System.out.println(evidence);
    }

    private static PcbBoardLayout createCase(TroubleshootBoard board, int variationMode,
            String r1Key, String r2Key) {
        try {
            return RcDelayPcbLayoutFactory.createForDeveloperVerification(board, variationMode,
                r1Key, r2Key);
        } catch (RuntimeException failure) {
            throw caseFailure(variationMode, r1Key, r2Key, "factory/compaction", failure);
        }
    }

    private static void validateCase(PcbBoardLayout layout, TroubleshootBoard board,
            int variationMode, String r1Key, String r2Key, String phase) {
        try {
            layout.validateGeometry(board);
        } catch (RuntimeException failure) {
            throw caseFailure(variationMode, r1Key, r2Key, phase, failure);
        }
    }

    private static RuntimeException caseFailure(int variationMode, String r1Key, String r2Key,
            String phase, RuntimeException failure) {
        return new IllegalStateException(caseMessage(variationMode, r1Key, r2Key,
            "phase=" + phase + ";net/trace/pad/message=" + failure.getMessage()), failure);
    }

    private static String caseMessage(int variationMode, String r1Key, String r2Key,
            String message) {
        return "RC fixed-layout case r1=" + r1Key + " r2=" + r2Key +
            " mode=" + variationMode + ": " + message;
    }

    private static void verifyCanonicalVariants(PcbBoardLayout layout, String r1Key,
            String r2Key) {
        verifyCanonicalVariant(layout, "J1", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "DEFAULT", "IDENTITY");
        verifyCanonicalVariant(layout, "J2", PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            "DEFAULT", "IDENTITY");
        verifyCanonicalVariant(layout, "R1", PhysicalPackages.AXIAL_RESISTOR,
            r1Key, "IDENTITY");
        verifyCanonicalVariant(layout, "R2", PhysicalPackages.AXIAL_RESISTOR,
            r2Key, "IDENTITY");
        verifyCanonicalVariant(layout, "C1", PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            "DEFAULT", "IDENTITY");
        verifyCanonicalVariant(layout, "C2", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "DEFAULT", "IDENTITY");
        verifyNoInternalComponentConnections(layout);
        verifyPlacedPackageSurfaces(layout);
    }

    private static void verifyCanonicalVariant(PcbBoardLayout layout, String componentId,
            PhysicalPackage expectedPackage, String expectedKey, String expectedTransform) {
        PcbComponentPlacement placement = layout.getComponent(componentId);
        require(placement != null && placement.getPhysicalPackage() == expectedPackage &&
                placement.getGeometryVariantKey().equals(expectedKey) &&
                placement.getGeometryTransformKey().equals(expectedTransform),
            "RC matrix selected the wrong canonical realization: " + componentId);
        PhysicalPackage.GeometryVariant variant = expectedPackage.getGeometryVariant(expectedKey);
        require(variant != null && variant.getTransformKey().equals(expectedTransform) &&
                placement.getPhysicalGeometry() == variant.getGeometry() &&
                placement.getGeometryRealization() != null &&
                placement.getGeometryRealization().getPhysicalPackage() == expectedPackage &&
                placement.getGeometryRealization().getPhysicalGeometry() == variant.getGeometry() &&
                placement.getGeometryRealization().getGeometryVariantKey().equals(expectedKey) &&
                placement.getGeometryRealization().getGeometryTransformKey().equals(
                    expectedTransform),
            "RC matrix did not preserve the canonical geometry object: " + componentId);
    }

    private static void verifyNoInternalComponentConnections(PcbBoardLayout layout) {
        for (PcbComponentPlacement placement : layout.getComponents()) {
            PhysicalPackage physicalPackage = placement.getPhysicalPackage();
            Vector<String> terminalIds = physicalPackage.getTerminalIds();
            for (int first = 0; first < terminalIds.size(); first++) {
                for (int second = first + 1; second < terminalIds.size(); second++)
                    require(!physicalPackage.isInternallyConnected(terminalIds.get(first),
                        terminalIds.get(second)),
                        "RC matrix unexpectedly relies on internal package connectivity: " +
                            placement.getComponentId());
            }
        }
    }

    private static void verifyPlacedPackageSurfaces(PcbBoardLayout layout) {
        for (PcbComponentPlacement placement : layout.getComponents()) {
            PhysicalPackageGeometry geometry = placement.getPhysicalGeometry();
            PhysicalPackageGeometry.Placement placed = geometry.placedAt(placement.getX(),
                placement.getY());
            Vector<String> terminalIds = placement.getPhysicalPackage().getTerminalIds();
            for (int index = 0; index < terminalIds.size(); index++) {
                PcbPadPlacement pad = layout.getPad(placement.getComponentId() + "." +
                    terminalIds.get(index));
                require(pad != null && pad.getX() == placed.getPadPoint(index).x &&
                        pad.getY() == placed.getPadPoint(index).y &&
                        pad.getEscapeDx() == geometry.getTerminal(index).getEscapeDx() &&
                        pad.getEscapeDy() == geometry.getTerminal(index).getEscapeDy() &&
                        pad.getEscapeLength() == geometry.getTerminal(index).getEscapeLength() &&
                        pad.getPadBounds().equals(placed.getPadBounds(index)) &&
                        pad.getProbeBounds().equals(placed.getBoardPadProbeBounds(index)),
                    "RC placed package surface diverged: " + placement.getComponentId() +
                        "." + terminalIds.get(index));
            }
        }
    }

    private static void verifyTraceWitness(PcbBoardLayout layout, TroubleshootBoard board) {
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        require(traces.size() == TRACE_NETS.length,
            "RC fixed layout does not contain exactly nine approved traces");
        for (int index = 0; index < TRACE_NETS.length; index++) {
            PcbTraceGeometry trace = traces.get(index);
            require(TRACE_NETS[index].equals(trace.getNetId()) &&
                    TRACE_STARTS[index].equals(trace.getStartPadId()) &&
                    TRACE_ENDS[index].equals(trace.getEndPadId()),
                "RC fixed-layout trace endpoint/net witness changed at index " + index);
            require(trace.getXPoints().length >= 2 &&
                    trace.getYPoints().length == trace.getXPoints().length,
                "RC fixed-layout trace has an invalid point witness at index " + index);
            verifyEndpointEscape(layout, trace, true);
            verifyEndpointEscape(layout, trace, false);
        }
        verifyRootedBranches(traces, "VIN", "J1.1", new String[] { "R1.1", "C2.1" });
        verifyRootedBranches(traces, "RC_OUT", "R1.2",
            new String[] { "C1.+", "J2.1", "R2.1" });
        verifyRootedBranches(traces, "GND", "J1.2",
            new String[] { "C1.-", "J2.2", "C2.2", "R2.2" });
        verifyLogicalNetMembership(layout, board);
        verifyAllPadsRepresented(layout, board);
        verifyPhysicalUnion(layout, board);
        verifyEscapeMetadata(layout, board);
    }

    private static void verifyRootedBranches(Vector<PcbTraceGeometry> traces, String netId,
            String rootPadId, String[] endPadIds) {
        int count = 0;
        Vector<String> actualEnds = new Vector<String>();
        for (PcbTraceGeometry trace : traces) {
            if (!netId.equals(trace.getNetId()))
                continue;
            count++;
            require(rootPadId.equals(trace.getStartPadId()) &&
                    !actualEnds.contains(trace.getEndPadId()),
                "RC rooted branch witness is not unique at " + netId);
            actualEnds.add(trace.getEndPadId());
        }
        require(count == endPadIds.length && actualEnds.size() == endPadIds.length,
            "RC rooted branch count changed for " + netId);
        for (String endPadId : endPadIds)
            require(actualEnds.contains(endPadId),
                "RC rooted branch is missing endpoint " + endPadId + " on " + netId);
    }

    private static void verifyLogicalNetMembership(PcbBoardLayout layout,
            TroubleshootBoard board) {
        String[][] expected = {
            { "VIN", "J1.1", "R1.1", "C2.1" },
            { "RC_OUT", "R1.2", "C1.+", "J2.1", "R2.1" },
            { "GND", "J1.2", "C1.-", "J2.2", "C2.2", "R2.2" }
        };
        require(board.getNetIds().size() == expected.length,
            "RC logical net count changed");
        for (String[] net : expected) {
            BoardNet boardNet = board.getNet(net[0]);
            require(boardNet != null && boardNet.getPadIds().size() == net.length - 1,
                "RC logical net membership count changed for " + net[0]);
            for (int index = 1; index < net.length; index++)
                require(boardNet.getPadIds().contains(net[index]),
                    "RC logical net membership missing " + net[index] + " on " + net[0]);
            for (String actualPadId : boardNet.getPadIds())
                require(contains(net, actualPadId),
                    "RC logical net membership has unexpected pad " + actualPadId +
                        " on " + net[0]);
        }
        require(layout.getPads().size() == board.getPadIds().size(),
            "RC physical pad count diverged from live logical board");
    }

    private static boolean contains(String[] values, String candidate) {
        for (String value : values)
            if (value.equals(candidate))
                return true;
        return false;
    }

    private static void verifyAllPadsRepresented(PcbBoardLayout layout,
            TroubleshootBoard board) {
        for (String padId : board.getPadIds()) {
            require(layout.getPad(padId) != null,
                "RC logical pad is absent from the physical layout: " + padId);
            boolean endpoint = false;
            for (PcbTraceGeometry trace : layout.getTraces())
                endpoint |= padId.equals(trace.getStartPadId()) ||
                    padId.equals(trace.getEndPadId());
            require(endpoint, "RC logical pad is not represented by a trace endpoint: " + padId);
        }
    }

    private static void verifyPhysicalUnion(PcbBoardLayout layout, TroubleshootBoard board) {
        // The generic validator is the authoritative physical DSU/package union oracle.
        layout.validateGeometry(board);
        verifyRootedBranches(layout.getTraces(), "VIN", "J1.1",
            new String[] { "R1.1", "C2.1" });
        verifyRootedBranches(layout.getTraces(), "RC_OUT", "R1.2",
            new String[] { "C1.+", "J2.1", "R2.1" });
        verifyRootedBranches(layout.getTraces(), "GND", "J1.2",
            new String[] { "C1.-", "J2.2", "C2.2", "R2.2" });
    }

    private static void verifyEscapeMetadata(PcbBoardLayout layout, TroubleshootBoard board) {
        for (String componentId : board.getComponentIds()) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            Vector<String> padIds = board.getComponent(componentId).getPadIds();
            for (int index = 0; index < padIds.size(); index++) {
                PcbPadPlacement pad = layout.getPad(padIds.get(index));
                PhysicalPackageGeometry.Terminal terminal = placement.getPhysicalGeometry()
                    .getTerminal(index);
                require(pad.getEscapeDx() == terminal.getEscapeDx() &&
                        pad.getEscapeDy() == terminal.getEscapeDy() &&
                        pad.getEscapeLength() == terminal.getEscapeLength(),
                    "RC declared escape metadata changed: " + padIds.get(index));
                require(pad.getEscapeLength() == 0 ||
                        Math.abs(pad.getEscapeDx()) + Math.abs(pad.getEscapeDy()) == 1,
                    "RC declared escape direction is invalid: " + padIds.get(index));
            }
        }
    }

    private static void verifyEndpointEscape(PcbBoardLayout layout, PcbTraceGeometry trace,
            boolean start) {
        String padId = start ? trace.getStartPadId() : trace.getEndPadId();
        PcbPadPlacement pad = layout.getPad(padId);
        require(pad != null, "RC trace endpoint has no physical pad: " + padId);
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
            "RC trace does not use pad escape direction: " + trace.getNetId() + " / " +
                padId);
        require(Math.abs(dx) + Math.abs(dy) >= pad.getEscapeLength(),
            "RC trace endpoint does not span its declared escape length: " + padId);
    }

    private static void verifyRouteQuality(PcbBoardLayout layout, TroubleshootBoard board) {
        layout.validateRouteQuality();
        layout.validateTraceClearance();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            require(layout.getTraceBendCount(trace) <= 16 &&
                    layout.getTraceDetourRatio(trace) <= 3.0,
                "RC route-quality limit changed: " + trace.getNetId());
        }
        require(layout.getRouteQualityScore(board) >= 0 &&
                layout.getCompactnessMetric() >= .40 &&
                layout.getLargestEdgeMargin() <= 34,
            "RC fixed route quality envelope changed");
        require(PcbTraceRules.MIN_CENTERLINE_CLEARANCE ==
                PcbTraceRules.TRACE_WIDTH + PcbTraceRules.MIN_VISIBLE_CLEARANCE,
            "RC trace clearance rule is inconsistent with rendered width");
    }

    private static void verifyCanonicalRealizationParity(PcbBoardLayout first,
            PcbBoardLayout second, int variationMode, String r1Key, String r2Key) {
        for (PcbComponentPlacement firstPlacement : first.getComponents()) {
            String componentId = firstPlacement.getComponentId();
            PcbComponentPlacement secondPlacement = second.getComponent(componentId);
            require(firstPlacement.getPhysicalPackage() == secondPlacement.getPhysicalPackage() &&
                    firstPlacement.getPhysicalGeometry() == secondPlacement.getPhysicalGeometry() &&
                    firstPlacement.getGeometryRealization().getPhysicalPackage() ==
                        secondPlacement.getGeometryRealization().getPhysicalPackage() &&
                    firstPlacement.getGeometryRealization().getPhysicalGeometry() ==
                        secondPlacement.getGeometryRealization().getPhysicalGeometry() &&
                    firstPlacement.getGeometryVariantKey().equals(
                        secondPlacement.getGeometryVariantKey()) &&
                    firstPlacement.getGeometryTransformKey().equals(
                        secondPlacement.getGeometryTransformKey()),
                caseMessage(variationMode, r1Key, r2Key,
                    "duplicate case changed canonical realization identity for " + componentId));
        }
    }

    private static void verifyProductionSeedParity(TroubleshootBoard board) {
        for (long seed = 0; seed < 4; seed++) {
            int variationMode = (int) (seed % 4);
            PcbBoardLayout production;
            try {
                production = RcDelayPcbLayoutFactory.create(board, seed);
                production.validateGeometry(board);
            } catch (RuntimeException failure) {
                throw new IllegalStateException("RC production parity failed seed=" + seed +
                    " phase=factory/compaction message=" + failure.getMessage(), failure);
            }
            String r1Key = production.getComponent("R1").getGeometryVariantKey();
            String r2Key = production.getComponent("R2").getGeometryVariantKey();
            require(contains(VARIANT_KEYS, r1Key) && contains(VARIANT_KEYS, r2Key),
                "RC production selected a noncanonical resistor key at seed " + seed);
            PcbBoardLayout developer = createCase(board, variationMode, r1Key, r2Key);
            validateCase(developer, board, variationMode, r1Key, r2Key,
                "production parity developer validateGeometry");
            require(production.geometryFingerprint().equals(developer.geometryFingerprint()),
                "RC production/developer full geometry parity diverged at seed " + seed);
            verifyCanonicalRealizationParity(production, developer, variationMode, r1Key, r2Key);
        }
    }

    private static void verifySeamNegativeCanaries(TroubleshootBoard board) {
        expectRejected(board, 0, null, "SPAN_220", "null R1 key");
        expectRejected(board, 0, "SPAN_220", null, "null R2 key");
        expectRejected(board, 0, "SPAN_230", "SPAN_220", "SPAN_230 R1 key");
        expectRejected(board, 0, "SPAN_220", "SPAN_230", "SPAN_230 R2 key");
        expectRejected(board, 0, "UNKNOWN_RC_VARIANT", "SPAN_220", "unknown R1 key");
        expectRejected(board, 0, "SPAN_220", "UNKNOWN_RC_VARIANT", "unknown R2 key");
        expectRejected(board, -1, "SPAN_220", "SPAN_220", "negative variation mode");
        expectRejected(board, 4, "SPAN_220", "SPAN_220", "variation mode above range");
    }

    private static void expectRejected(TroubleshootBoard board, int variationMode,
            String r1Key, String r2Key, String name) {
        try {
            RcDelayPcbLayoutFactory.createForDeveloperVerification(board, variationMode, r1Key,
                r2Key);
        } catch (IllegalArgumentException expected) {
            return;
        } catch (RuntimeException unexpected) {
            throw new IllegalStateException("RC seam canary " + name +
                " failed with the wrong exception: " + unexpected.getMessage(), unexpected);
        }
        throw new IllegalStateException("RC seam canary was accepted: " + name);
    }

    private static String normalizedGeometrySignature(PcbBoardLayout layout) {
        Rectangle outline = layout.getBoardOutline();
        int originX = outline.x;
        int originY = outline.y;
        StringBuilder result = new StringBuilder();
        result.append("canvas=").append(layout.getWidth()).append('x').append(layout.getHeight())
            .append("|board=0,0,").append(outline.width).append(',').append(outline.height)
            .append('|');
        result.append("tray=");
        appendRectangle(result, layout.getPartsTray(), originX, originY);

        Vector<PcbComponentPlacement> components = layout.getComponents();
        Collections.sort(components, new Comparator<PcbComponentPlacement>() {
            public int compare(PcbComponentPlacement first, PcbComponentPlacement second) {
                return first.getComponentId().compareTo(second.getComponentId());
            }
        });
        for (PcbComponentPlacement component : components) {
            PhysicalPackage physicalPackage = component.getPhysicalPackage();
            PhysicalPackageGeometry geometry = component.getPhysicalGeometry();
            result.append("|C:").append(component.getComponentId()).append('@')
                .append(component.getX() - originX).append(',')
                .append(component.getY() - originY).append(',').append(component.getWidth())
                .append(',').append(component.getHeight()).append(':')
                .append(physicalPackage.getId()).append(':')
                .append(component.getGeometryVariantKey()).append(':')
                .append(component.getGeometryTransformKey()).append(':')
                .append(component.getGeometryContractVersionValue()).append(':')
                .append(component.getGeometryRealization().fingerprint());
            appendRectangle(result, component.getBodyBounds(), originX, originY);
            appendRectangle(result, component.getKeepOut(), originX, originY);
            appendRectangle(result, component.getRoutingCourtyard(), originX, originY);
            appendRectangle(result, component.getSelectionEnvelope(), originX, originY);
            appendRectangle(result, component.getDragEnvelope(), originX, originY);
            appendLocalGeometry(result, geometry);
            Vector<String> terminalIds = physicalPackage.getTerminalIds();
            for (int index = 0; index < terminalIds.size(); index++) {
                result.append("|CT:").append(terminalIds.get(index));
                appendPoint(result, component.getPadPoint(index), originX, originY);
                appendRectangle(result, component.getPadBounds(index), originX, originY);
                appendPoint(result, component.getBoardPadProbeCenter(index), originX, originY);
                appendRectangle(result, component.getBoardPadProbeBounds(index), originX, originY);
                appendPoint(result, component.getComponentLeadProbeCenter(index), originX,
                    originY);
                appendRectangle(result, component.getComponentLeadProbeBounds(index), originX,
                    originY);
                appendPoint(result, component.getComponentLeadProbeCenter(index, true), originX,
                    originY);
                appendRectangle(result, component.getComponentLeadProbeBounds(index, true),
                    originX, originY);
                appendPoint(result, component.getLeadEndPoint(index), originX, originY);
                appendPoint(result, component.getLeadBodyPoint(index), originX, originY);
                appendRectangle(result, component.getLeadBounds(index), originX, originY);
                appendPoint(result, component.getLeadEndPoint(index, true), originX, originY);
                appendPoint(result, component.getLeadBodyPoint(index, true), originX, originY);
                appendRectangle(result, component.getLeadBounds(index, true), originX, originY);
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

    private static void appendLocalGeometry(StringBuilder result,
            PhysicalPackageGeometry geometry) {
        result.append("|LOCAL:").append(geometry.getWidth()).append('x')
            .append(geometry.getHeight()).append(':').append(geometry.getGeometryContractVersionValue())
            .append(':').append(geometry.isDeveloperGeneric());
        appendRectangle(result, geometry.getBodyBounds(), 0, 0);
        appendRectangle(result, geometry.getBodyKeepOut(), 0, 0);
        appendRectangle(result, geometry.getRoutingCourtyard(), 0, 0);
        appendRectangle(result, geometry.getSelectionEnvelope(), 0, 0);
        appendRectangle(result, geometry.getDragEnvelope(), 0, 0);
        for (PhysicalPackageGeometry.Terminal terminal : geometry.getTerminals()) {
            result.append("|LT:").append(terminal.getTerminalId());
            appendPoint(result, terminal.getPadCenter(), 0, 0);
            appendRectangle(result, terminal.getPadBounds(), 0, 0);
            appendPoint(result, terminal.getBoardPadProbeCenter(), 0, 0);
            appendRectangle(result, terminal.getBoardPadProbeBounds(), 0, 0);
            appendLead(result, terminal.getConnectedLead(), 0, 0);
            appendLead(result, terminal.getLiftedLead(), 0, 0);
            result.append("escape=").append(terminal.getEscapeDx()).append(',')
                .append(terminal.getEscapeDy()).append(',').append(terminal.getEscapeLength())
                .append(';');
        }
    }

    private static void appendLead(StringBuilder result, PhysicalPackageGeometry.Lead lead,
            int originX, int originY) {
        appendPoint(result, lead.getEndPoint(), originX, originY);
        appendPoint(result, lead.getBodyPoint(), originX, originY);
        appendRectangle(result, lead.getBounds(), originX, originY);
        appendPoint(result, lead.getComponentProbeCenter(), originX, originY);
        appendRectangle(result, lead.getComponentProbeBounds(), originX, originY);
    }

    private static void appendPoint(StringBuilder result, Point point, int originX, int originY) {
        result.append("point=").append(point == null ? "null" :
            (point.x - originX) + "," + (point.y - originY)).append(';');
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
