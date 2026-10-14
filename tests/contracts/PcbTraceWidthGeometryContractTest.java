package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/** Independent literal contract for routed copper widths and physical graph identity. */
public final class PcbTraceWidthGeometryContractTest {
    private static int assertions;

    public static void main(String[] args) {
        widthRulesAndImmutability();
        literalStrokesAndClearance();
        canonicalizationPreservesWidthBoundaries();
        graphWidthIdentityAndLegacyEncoding();
        profileAndTwoFaceCopper();
        componentEndpointEscapeRanges();
        System.out.println("PASS: PCB trace width geometry contracts assertions=" + assertions);
    }

    private static void widthRulesAndImmutability() {
        PcbTraceGeometry legacyGeometry = new PcbTraceGeometry("N", new int[] { 10, 30 },
            new int[] { 20, 20 });
        PcbTraceGeometry legacyPads = new PcbTraceGeometry("N", "A", "B",
            new int[] { 10, 30 }, new int[] { 20, 20 });
        PcbTraceGeometry legacyFull = new PcbTraceGeometry("source", "N", "A", "B",
            PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
            new int[] { 10, 30 }, new int[] { 20, 20 });
        check(legacyGeometry.getWidth() == 9 && legacyPads.getWidth() == 9 &&
            legacyFull.getWidth() == 9,
            "every legacy trace constructor retains implicit width9");

        int[] x = { 40, 80 };
        int[] y = { 60, 60 };
        PcbTraceGeometry wide = new PcbTraceGeometry("wide", "H", "H1", "H2",
            PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED, x, y, 13);
        x[0] = -100;
        y[0] = -100;
        check(wide.getWidth() == 13 && wide.getXPoints()[0] == 40 &&
            wide.getYPoints()[0] == 60,
            "width trace owns immutable copies of caller coordinates");
        PcbTraceGeometry changedPath = wide.withPath(new int[] { 45, 85 },
            new int[] { 70, 70 });
        check(changedPath.getWidth() == 13 && wide.getWidth() == 13 &&
            changedPath.getXPoints()[0] == 45 && wide.getXPoints()[0] == 40,
            "withPath preserves width without mutating the original trace");

        for (final int invalid : new int[] { Integer.MIN_VALUE, -1, 0, 8, 10, 62, 64,
                Integer.MAX_VALUE }) {
            reject("invalid width rule " + invalid, new Action() {
                public void run() { PcbTraceRules.requireWidth(invalid); }
            });
            reject("invalid trace width " + invalid, new Action() {
                public void run() {
                    new PcbTraceGeometry("bad", "N", null, null, PcbCopperLayer.TOP,
                        PcbCopperAccess.Exposure.EXPOSED, new int[] { 1, 20 },
                        new int[] { 1, 1 }, invalid);
                }
            });
        }
        for (int valid : new int[] { 9, 11, 13, 63 }) {
            PcbTraceRules.requireWidth(valid);
            check(new PcbTraceGeometry("ok", "N", null, null, PcbCopperLayer.TOP,
                PcbCopperAccess.Exposure.EXPOSED, new int[] { 1, 20 },
                new int[] { 1, 1 }, valid).getWidth() == valid,
                "valid odd width is retained: " + valid);
        }
    }

    private static void literalStrokesAndClearance() {
        equal(new Rectangle(96, 196, 49, 9), PcbConductorBuilder.stroke(100, 200, 140, 200),
            "legacy horizontal width9 stroke stays byte-for-byte geometry");
        equal(new Rectangle(95, 195, 51, 11),
            PcbConductorBuilder.stroke(100, 200, 140, 200, 11),
            "literal horizontal width11 stroke");
        equal(new Rectangle(94, 194, 53, 13),
            PcbConductorBuilder.stroke(100, 200, 140, 200, 13),
            "literal horizontal width13 stroke");
        equal(new Rectangle(134, 194, 13, 63),
            PcbConductorBuilder.stroke(140, 250, 140, 200, 13),
            "literal reverse vertical width13 stroke");
        equal(new Rectangle(96, 96, 109, 9),
            PcbConductorBuilder.stroke(100, 100, 200, 100),
            "legacy horizontal stroke preserves old exact envelope");
        reject("diagonal width stroke", new Action() {
            public void run() { PcbConductorBuilder.stroke(0, 0, 1, 1, 13); }
        });

        check(PcbTraceRules.clearance(9, 9) == 15 &&
            PcbTraceRules.clearance(13, 9) == 17 &&
            PcbTraceRules.clearance(13, 13) == 19 &&
            PcbTraceRules.clearance(63, 13) == 44,
            "literal centerline gaps are ceil((w1+w2)/2)+6");

        clearanceRejects(13, 9, 16,
            "wide search envelope finds foreign-net pair one unit below17");
        clearanceAccepts(13, 9, 17, "13/9 exact17 boundary is legal");
        clearanceRejects(13, 13, 18, "13/13 one below19 is rejected");
        clearanceAccepts(13, 13, 19, "13/13 exact19 boundary is legal");
        clearanceRejects(9, 9, 14, "legacy9/9 one below15 remains rejected");
        clearanceAccepts(9, 9, 15, "legacy9/9 exact15 boundary remains legal");
        reject("invalid clearance width", new Action() {
            public void run() { PcbTraceRules.clearance(13, 10); }
        });

        PcbBoardLayout thinOccupied = new PcbBoardLayout(400, 300,
            new Rectangle(0, 0, 250, 250), new Rectangle(270, 0, 100, 200));
        thinOccupied.addTrace(line("thin", "T", 9, new int[] { 100, 200 },
            new int[] { 100, 100 }));
        PcbBoardLayout wideOccupied = new PcbBoardLayout(400, 300,
            new Rectangle(0, 0, 250, 250), new Rectangle(270, 0, 100, 200));
        wideOccupied.addTrace(line("wide", "T", 13, new int[] { 100, 200 },
            new int[] { 100, 100 }));
        equal(new Rectangle(96, 96, 109, 9), thinOccupied.getOccupiedContentBounds(),
            "layout occupied envelope retains exact legacy width9 stroke");
        equal(new Rectangle(94, 94, 113, 13), wideOccupied.getOccupiedContentBounds(),
            "layout occupied envelope includes full width13 stroke");
    }

    private static void clearanceRejects(int firstWidth, int secondWidth, int gap,
            String message) {
        final List<PcbTraceGeometry> traces = Arrays.asList(
            line("a", "A", firstWidth, new int[] { 0, 100 }, new int[] { 0, 0 }),
            line("b", "B", secondWidth, new int[] { 0, 100 }, new int[] { gap, gap }));
        reject(message, new Action() {
            public void run() { PcbTraceClearance.validate(traces); }
        });
    }

    private static void clearanceAccepts(int firstWidth, int secondWidth, int gap,
            String message) {
        PcbTraceClearance.validate(Arrays.asList(
            line("a", "A", firstWidth, new int[] { 0, 100 }, new int[] { 0, 0 }),
            line("b", "B", secondWidth, new int[] { 0, 100 }, new int[] { gap, gap })));
        check(true, message);
    }

    private static void canonicalizationPreservesWidthBoundaries() {
        PcbBoardLayout layout = new PcbBoardLayout(400, 300,
            new Rectangle(0, 0, 250, 250), new Rectangle(270, 0, 100, 200));
        layout.addTrace(line("wide", "N", 13, new int[] { 20, 60, 120 },
            new int[] { 80, 80, 80 }));
        layout.addTrace(line("thin", "N", 9, new int[] { 60, 120, 180 },
            new int[] { 80, 80, 80 }));
        PcbRouteCanonicalizer.canonicalize(layout);
        PcbTraceGeometry wide = source(layout, "wide");
        PcbTraceGeometry thin = source(layout, "thin");
        check(wide.getWidth() == 13 && thin.getWidth() == 9,
            "canonicalizer preserves per-source widths");
        check(containsPoint(wide, 60, 80) && containsPoint(wide, 120, 80) &&
            containsPoint(thin, 60, 80) && containsPoint(thin, 120, 80),
            "canonicalizer retains both ends of differing-width overlap boundaries");
    }

    private static void graphWidthIdentityAndLegacyEncoding() {
        PcbConductorGraph legacy = legacyGraph();
        String expected = "P02-COPPER/1\n" +
            "J1:a1:NTOP:100,100\n" +
            "J1:b1:NTOP:200,100\n" +
            "E2:e11:a1:b1:NTRACE:1:r\n" +
            "S1:s1:a2:e1-1:TOP:EXPOSED:96,96,109,9\n" +
            "H0:";
        check(expected.equals(legacy.toCanonical()),
            "width9 graph retains the exact legacy canonical record format");
        check(legacy.getEdges().get("e1").getWidth() == 9,
            "legacy edge constructor defaults to width9");

        // A valid layout has one profile width per net, so the public builder
        // cannot receive mixed-width routes on the same net. This explicit
        // graph fixture checks that already-partitioned span widths and source
        // provenance survive graph storage without smearing neighboring spans.
        PcbConductorGraph mixed = prepartitionedMixedWidthGraph();
        Map<String, PcbConductorGraph.Edge> edges = mixed.getEdges();
        check(edges.get("edge/first").getWidth() == 13 &&
            edges.get("edge/overlap").getWidth() == 13 &&
            edges.get("edge/last").getWidth() == 9,
            "graph preserves the supplied width on each shared or independent span");
        check(edges.get("edge/overlap").getSources().contains("wide-route") &&
            edges.get("edge/overlap").getSources().contains("thin-route"),
            "shared span retains both independently supplied route sources");
        check(surface(mixed, "surface/first").getBounds().equals(
                PcbConductorBuilder.stroke(0, 0, 50, 0, 13)) &&
            surface(mixed, "surface/overlap").getBounds().equals(
                PcbConductorBuilder.stroke(50, 0, 100, 0, 13)) &&
            surface(mixed, "surface/last").getBounds().equals(
                PcbConductorBuilder.stroke(100, 0, 150, 0, 9)),
            "edge surfaces exactly match each local width group");
        check(mixed.toCanonical().contains("TRACEW13:") &&
            !mixed.toCanonical().contains("TRACEW9:"),
            "graph canonical identity encodes only nondefault trace widths");
        reject("non-trace edge cannot claim width13", new Action() {
            public void run() {
                new PcbConductorGraph.Edge("bad", "a", "b", "N",
                    PcbConductorGraph.Kind.CONTACT, Arrays.asList("contact"), 13);
            }
        });
    }

    private static PcbConductorGraph legacyGraph() {
        List<PcbConductorGraph.Junction> junctions = Arrays.asList(
            new PcbConductorGraph.Junction("a", "N", PcbCopperLayer.TOP, 100, 100),
            new PcbConductorGraph.Junction("b", "N", PcbCopperLayer.TOP, 200, 100));
        List<PcbConductorGraph.Edge> edges = Arrays.asList(
            new PcbConductorGraph.Edge("e1", "a", "b", "N",
                PcbConductorGraph.Kind.TRACE, Arrays.asList("r")));
        List<PcbConductorGraph.Surface> surfaces = Arrays.asList(
            new PcbConductorGraph.Surface("s", "a", "e1", null, PcbCopperLayer.TOP,
                PcbCopperAccess.Exposure.EXPOSED,
                PcbConductorBuilder.stroke(100, 100, 200, 100)));
        return new PcbConductorGraph(junctions, edges, surfaces,
            Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(), "");
    }

    private static PcbConductorGraph prepartitionedMixedWidthGraph() {
        List<PcbConductorGraph.Junction> junctions = Arrays.asList(
            new PcbConductorGraph.Junction("j0", "N", PcbCopperLayer.TOP, 0, 0),
            new PcbConductorGraph.Junction("j1", "N", PcbCopperLayer.TOP, 50, 0),
            new PcbConductorGraph.Junction("j2", "N", PcbCopperLayer.TOP, 100, 0),
            new PcbConductorGraph.Junction("j3", "N", PcbCopperLayer.TOP, 150, 0));
        List<PcbConductorGraph.Edge> edges = Arrays.asList(
            new PcbConductorGraph.Edge("edge/first", "j0", "j1", "N",
                PcbConductorGraph.Kind.TRACE, Arrays.asList("wide-route"), 13),
            new PcbConductorGraph.Edge("edge/overlap", "j1", "j2", "N",
                PcbConductorGraph.Kind.TRACE, Arrays.asList("wide-route", "thin-route"), 13),
            new PcbConductorGraph.Edge("edge/last", "j2", "j3", "N",
                PcbConductorGraph.Kind.TRACE, Arrays.asList("thin-route"), 9));
        List<PcbConductorGraph.Surface> surfaces = Arrays.asList(
            surface("surface/first", "j0", "edge/first", 0, 50, 13),
            surface("surface/overlap", "j1", "edge/overlap", 50, 100, 13),
            surface("surface/last", "j2", "edge/last", 100, 150, 9));
        return new PcbConductorGraph(junctions, edges, surfaces,
            Collections.<String, String>emptyMap(), Collections.<String, String>emptyMap(), "");
    }

    private static PcbConductorGraph.Surface surface(String id, String junction, String edge,
            int firstX, int secondX, int width) {
        return new PcbConductorGraph.Surface(id, junction, edge, null, PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED,
            PcbConductorBuilder.stroke(firstX, 0, secondX, 0, width));
    }

    private static PcbConductorGraph.Surface surface(PcbConductorGraph graph, String id) {
        for (PcbConductorGraph.Surface surface : graph.getSurfaces())
            if (surface.id.equals(id)) return surface;
        throw new AssertionError("Missing graph surface " + id);
    }

    private static void profileAndTwoFaceCopper() {
        final Fixture profiled = new Fixture(PcbTraceWidthProfile.ROLE_WIDTHS, false);
        profiled.layout.validateAgainst(profiled.board);
        PcbConductorGraph graph = profiled.layout.captureConductorGraph(profiled.board);
        graph.pristine().requirePristineNetConnectivity(profiled.board);
        int topWide = 0, bottomWide = 0, signalLegacy = 0, viaLegacy = 0;
        for (PcbConductorGraph.Edge edge : graph.getEdges().values()) {
            if (edge.kind == PcbConductorGraph.Kind.TRACE && edge.netId.equals("HIGH")) {
                check(edge.getWidth() == 13, "HIGH_CURRENT trace edge has profile width13");
            }
            if (edge.kind == PcbConductorGraph.Kind.TRACE && edge.netId.equals("SIGNAL")) {
                check(edge.getWidth() == 9, "SIGNAL trace edge retains legacy width9");
                signalLegacy++;
            }
            if (edge.kind == PcbConductorGraph.Kind.VIA)
                check(edge.getWidth() == 9, "via remains legacy non-trace width9");
            if (edge.kind == PcbConductorGraph.Kind.VIA) viaLegacy++;
        }
        for (PcbConductorGraph.Surface surface : graph.getSurfaces()) {
            if (surface.edgeId == null || !surface.id.startsWith("copper/")) continue;
            PcbConductorGraph.Edge edge = graph.getEdges().get(surface.edgeId);
            PcbConductorGraph.Junction a = graph.getJunctions().get(edge.first);
            PcbConductorGraph.Junction b = graph.getJunctions().get(edge.second);
            check(surface.getBounds().equals(PcbConductorBuilder.stroke(a.x, a.y, b.x, b.y,
                edge.getWidth())), "real edge surface exactly matches width-aware stroke");
            check(PcbCopperProbeAccess.available(graph.pristine(), surface, surface.layer.getFace()) &&
                !PcbCopperProbeAccess.available(graph.pristine(), surface,
                    surface.layer == PcbCopperLayer.TOP ? PcbBoardSide.BOTTOM : PcbBoardSide.TOP),
                "actual width-bearing copper is probeable only on its exposed face");
            String probePad = PcbCopperProbeAccess.connectedPad(graph.pristine(), surface.id);
            check(probePad != null && profiled.board.getPad(probePad) != null &&
                edge.netId.equals(profiled.board.getPad(probePad).getNetId()),
                "both-face width-bearing probe surfaces resolve to their authoritative net pads");
            if (edge.netId.equals("HIGH") && a.layer == PcbCopperLayer.TOP) topWide++;
            if (edge.netId.equals("HIGH") && a.layer == PcbCopperLayer.BOTTOM) bottomWide++;
        }
        check(topWide > 0 && bottomWide > 0 && signalLegacy > 0 && viaLegacy == 1,
            "two-face width13 copper connects through a legacy-width via to the 26-unit PTH pad");
        check(profiled.layout.traceGeometryFingerprint().contains("W13:") &&
            !profiled.layout.traceGeometryFingerprint().contains("W9:"),
            "layout fingerprint records only nondefault trace widths");

        Fixture legacy = new Fixture(PcbTraceWidthProfile.UNIFORM, false);
        legacy.layout.validateAgainst(legacy.board);
        check(!legacy.layout.traceGeometryFingerprint().contains("W9:") &&
            !legacy.layout.traceGeometryFingerprint().contains("W13:"),
            "all-nine width profile retains exact implicit fingerprint fields");
        check(!profiled.layout.traceGeometryFingerprint().equals(
            legacy.layout.traceGeometryFingerprint()),
            "physical trace fingerprint changes when actual widths change");

        Vector<PcbTraceGeometry> original = profiled.layout.getTraces();
        Vector<PcbTraceGeometry> malformed = new Vector<PcbTraceGeometry>(original);
        PcbTraceGeometry high = null;
        for (PcbTraceGeometry trace : malformed)
            if (trace.getNetId().equals("HIGH")) { high = trace; break; }
        check(high != null, "fixture has actual high-current route");
        int[] xs = high.getXPoints(), ys = high.getYPoints();
        malformed.add(new PcbTraceGeometry("wrong-profile", "HIGH", null, null,
            high.getLayer(), high.getExposure(), xs, ys, 9));
        profiled.layout.replaceTraces(malformed);
        reject("layout rejects width that disagrees with net/profile", new Action() {
            public void run() { profiled.layout.validateAgainst(profiled.board); }
        });
        reject("builder rejects same-net mixed widths before graph normalization", new Action() {
            public void run() { profiled.layout.captureConductorGraph(profiled.board); }
        });
        profiled.layout.replaceTraces(original);

        final Fixture thickHole = new Fixture(PcbTraceWidthProfile.ROLE_WIDTHS, true);
        reject("actual width13 stroke reaches nearby non-plated hole", new Action() {
            public void run() { thickHole.layout.captureConductorGraph(thickHole.board); }
        });
        Fixture thinHole = new Fixture(PcbTraceWidthProfile.UNIFORM, true);
        thinHole.layout.captureConductorGraph(thinHole.board);
        check(true, "width9 control clears the same non-plated hole");
    }

    private static void componentEndpointEscapeRanges() {
        // For the forward courtyard [110,240), the integer centerlines whose
        // odd-width strokes touch it end at 244 for width11 and 245 for width13.
        // The mirrored courtyard [380,510) begins at 375 and 374 respectively.
        // These literal reaches put each real package escape exactly on both
        // physical collision boundaries, so the old symmetric envelope rejected
        // the valid routes one unit early at each component.
        componentEndpointEscapeWidth(11, 24, 25);
        componentEndpointEscapeWidth(13, 25, 26);

        // Width9 intentionally keeps the historical inclusive expansion: its
        // forward envelope reaches x=244, so a corridor ending at x=243 remains
        // rejected even though newer widths use the exact discrete stroke edge.
        final EndpointEscapeFixture legacyExact = new EndpointEscapeFixture(9, 24, 25);
        legacyExact.layout.validateRoutingGeometry(legacyExact.board);
        check(true, "legacy width9 component escapes retain their historical envelope");
        final EndpointEscapeFixture legacyOneShort = new EndpointEscapeFixture(9, 23, 25);
        rejectCourtyard("legacy width9 retains its historical one-unit escape boundary",
            "FORWARD", new Action() {
                public void run() {
                    legacyOneShort.layout.validateRoutingGeometry(legacyOneShort.board);
                }
            });
    }

    private static void componentEndpointEscapeWidth(final int width,
            final int forwardReach, final int reverseReach) {
        final EndpointEscapeFixture exact = new EndpointEscapeFixture(width, forwardReach,
            reverseReach);
        exact.layout.validateRoutingGeometry(exact.board);
        PcbTraceGeometry trace = exact.layout.getTraces().firstElement();
        check(trace.getWidth() == width && trace.getStartPadId().equals("FORWARD.1") &&
            trace.getEndPadId().equals("REVERSE.1") &&
            Arrays.equals(trace.getXPoints(), new int[] { 220, 400 }) &&
            Arrays.equals(trace.getYPoints(), new int[] { 130, 130 }),
            "literal width" + width + " trace clears both declared component escapes");

        final EndpointEscapeFixture forwardOneShort = new EndpointEscapeFixture(width,
            forwardReach - 1, reverseReach);
        rejectCourtyard("width" + width + " forward escape one unit short remains illegal",
            "FORWARD", new Action() {
                public void run() {
                    forwardOneShort.layout.validateRoutingGeometry(forwardOneShort.board);
                }
            });
        final EndpointEscapeFixture reverseOneShort = new EndpointEscapeFixture(width,
            forwardReach, reverseReach - 1);
        rejectCourtyard("width" + width + " reverse escape one unit short remains illegal",
            "REVERSE", new Action() {
                public void run() {
                    reverseOneShort.layout.validateRoutingGeometry(reverseOneShort.board);
                }
            });
    }

    private static void rejectCourtyard(String message, String componentId, Action action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            String detail = expected.getMessage();
            if (detail != null && detail.indexOf(
                    "PCB trace passes through component routing courtyard: ") == 0 &&
                    detail.indexOf(" / " + componentId + " segment ") >= 0) {
                check(true, message);
                return;
            }
            throw new AssertionError("Expected courtyard rejection for " + componentId +
                ", got: " + detail);
        }
        throw new AssertionError("Expected courtyard rejection for " + componentId + ": " +
            message);
    }

    private static final class EndpointEscapeFixture {
        final TroubleshootBoard board = new TroubleshootBoard("ENDPOINT_ESCAPE_FIXTURE");
        final PcbBoardLayout layout = new PcbBoardLayout(700, 320,
            new Rectangle(20, 20, 600, 230), new Rectangle(20, 270, 200, 30));

        EndpointEscapeFixture(int width, int forwardReach, int reverseReach) {
            BoardNet.RoutingRole role = width == 9 ? BoardNet.RoutingRole.SIGNAL :
                width == 11 ? BoardNet.RoutingRole.SUPPLY :
                width == 13 ? BoardNet.RoutingRole.HIGH_CURRENT : null;
            if (role == null)
                throw new IllegalArgumentException("Unsupported endpoint escape fixture width");
            PcbTraceWidthProfile profile = width == 9 ? PcbTraceWidthProfile.UNIFORM :
                PcbTraceWidthProfile.ROLE_WIDTHS;
            board.addNet(new BoardNet("ESCAPE", role));

            PhysicalPackage forwardPackage = pointPackage("ESCAPE_FORWARD", false,
                forwardReach);
            PhysicalPackage reversePackage = pointPackage("ESCAPE_REVERSE", true,
                reverseReach);
            BoardComponent forward = new BoardComponent("FORWARD", "point", forwardPackage);
            BoardComponent reverse = new BoardComponent("REVERSE", "point", reversePackage);
            board.addComponent(forward);
            board.addComponent(reverse);
            board.addPad(new BoardPad("FORWARD.1", "FORWARD", "1", "ESCAPE"));
            board.addPad(new BoardPad("REVERSE.1", "REVERSE", "1", "ESCAPE"));

            Vector<PcbPlacementConstraints.Part> parts =
                new Vector<PcbPlacementConstraints.Part>();
            parts.add(new PcbPlacementConstraints.Part("FORWARD", "route", "Route",
                "board", PcbPlacementConstraints.Anchor.NONE, 20));
            parts.add(new PcbPlacementConstraints.Part("REVERSE", "route", "Route",
                "board", PcbPlacementConstraints.Anchor.NONE, 20));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false, profile));

            addFootprint(PcbFootprint.fromPhysicalPackage(forward, 100, 100,
                forwardPackage.getGeometry()));
            addFootprint(PcbFootprint.fromPhysicalPackage(reverse, 370, 100,
                reversePackage.getGeometry()));
            layout.addTrace(new PcbTraceGeometry("component-escape", "ESCAPE", "FORWARD.1",
                "REVERSE.1", PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
                new int[] { 220, 400 }, new int[] { 130, 130 }, width));
        }

        private void addFootprint(PcbFootprint footprint) {
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads())
                layout.addPad(pad);
        }
    }

    private static PhysicalPackage pointPackage(String id, boolean mirrored,
            int escapeLength) {
        PhysicalPackageGeometry source = PhysicalPackageGeometry.generic(singleton("1"), false);
        if (mirrored)
            source = source.mirroredHorizontally();
        PhysicalPackageGeometry.Terminal sourceTerminal = source.getTerminal(0);
        PhysicalPackageGeometry.Terminal terminal = new PhysicalPackageGeometry.Terminal(
            sourceTerminal.getTerminalId(), sourceTerminal.getPadCenter(),
            sourceTerminal.getPadBounds(), sourceTerminal.getBoardPadProbeCenter(),
            sourceTerminal.getBoardPadProbeBounds(), sourceTerminal.getConnectedLead(),
            sourceTerminal.getLiftedLead(), sourceTerminal.getEscapeDx(),
            sourceTerminal.getEscapeDy(), escapeLength, sourceTerminal.getAttachment());
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(terminal);
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(source.getWidth(),
            source.getHeight(), terminals, source.getBodyBounds(), source.getBodyKeepOut(),
            source.getRoutingCourtyard(), source.getSelectionEnvelope(),
            source.getDragEnvelope(), source.getGeometryContractVersion());
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY", geometry));
        return new PhysicalPackage(id, singleton("1"), new Vector<String>(), false, geometry,
            variants, "DEFAULT", PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT);
    }

    private static final class Fixture {
        final TroubleshootBoard board = new TroubleshootBoard("TRACE_WIDTH_FIXTURE");
        final PcbBoardLayout layout = new PcbBoardLayout(600, 500,
            new Rectangle(20, 20, 500, 430), new Rectangle(530, 30, 60, 400));
        private final PhysicalPackage point = PhysicalPackage.developerPackageWithGenericGeometry(
            "TRACE_WIDTH_POINT", singleton("1"), new Vector<String>(), false);
        private final int highWidth;

        Fixture(PcbTraceWidthProfile profile, boolean nonPlatedHole) {
            highWidth = profile.widthFor(BoardNet.RoutingRole.HIGH_CURRENT);
            board.addNet(new BoardNet("HIGH", BoardNet.RoutingRole.HIGH_CURRENT));
            board.addNet(new BoardNet("SIGNAL", BoardNet.RoutingRole.SIGNAL));
            addPad("H1", "HIGH", 100, 100, PcbTerminalAttachment.PLATED_THROUGH_HOLE);
            addPad("H2", "HIGH", 250, 100, PcbTerminalAttachment.PLATED_THROUGH_HOLE);
            addPad("S1", "SIGNAL", 100, 300, PcbTerminalAttachment.SURFACE_PAD);
            addPad("S2", "SIGNAL", 250, 300, PcbTerminalAttachment.SURFACE_PAD);
            Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
            for (String component : board.getComponentIds())
                parts.add(new PcbPlacementConstraints.Part(component, "region", "Circuit",
                    "board", PcbPlacementConstraints.Anchor.NONE, 20));
            board.setPlacementConstraints(new PcbPlacementConstraints(parts,
                new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_ID,
                PcbPlacementConstraints.DEFAULT_PHYSICAL_POLICY_VERSION, 1, false, profile));

            layout.addHole(new PcbBoardHole("route-via", PcbBoardHole.Kind.VIA, "HIGH",
                175, 100, 4, 8, PcbCopperLayer.TOP, PcbCopperLayer.BOTTOM,
                PcbCopperAccess.Exposure.EXPOSED));
            layout.addTrace(new PcbTraceGeometry("high-top", "HIGH", "H1.1", null,
                PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
                new int[] { 100, 175 }, new int[] { 100, 100 }, highWidth));
            layout.addTrace(new PcbTraceGeometry("high-bottom", "HIGH", null, "H2.1",
                PcbCopperLayer.BOTTOM, PcbCopperAccess.Exposure.EXPOSED,
                new int[] { 175, 250 }, new int[] { 100, 100 }, highWidth));
            layout.addTrace(new PcbTraceGeometry("signal-top", "SIGNAL", "S1.1", "S2.1",
                PcbCopperLayer.TOP, PcbCopperAccess.Exposure.EXPOSED,
                new int[] { 100, 250 }, new int[] { 300, 300 },
                profile.widthFor(BoardNet.RoutingRole.SIGNAL)));
            if (nonPlatedHole)
                layout.addHole(new PcbBoardHole("near-trace", PcbBoardHole.Kind.NON_PLATED,
                    null, 130, 109, 3, 3, null, null, PcbCopperAccess.Exposure.COVERED));
        }

        private void addPad(String componentId, String net, int x, int y,
                PcbTerminalAttachment attachment) {
            board.addComponent(new BoardComponent(componentId, "point", point));
            String padId = componentId + ".1";
            board.addPad(new BoardPad(padId, componentId, "1", net));
            int width = 26;
            layout.addPad(new PcbPadPlacement(padId, x, y, 0, 0, 0,
                new Rectangle(x - width / 2, y - width / 2, width, width),
                new Rectangle(x - 17, y - 17, 34, 34), attachment, PcbBoardSide.TOP));
        }
    }

    private static Vector<String> singleton(String value) {
        Vector<String> values = new Vector<String>();
        values.add(value);
        return values;
    }

    private static PcbTraceGeometry line(String source, String net, int width,
            int[] x, int[] y) {
        return new PcbTraceGeometry(source, net, null, null, PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED, x, y, width);
    }

    private static PcbTraceGeometry source(PcbBoardLayout layout, String id) {
        for (PcbTraceGeometry trace : layout.getTraces())
            if (id.equals(trace.getSourceId())) return trace;
        throw new AssertionError("Missing route source " + id);
    }

    private static boolean containsPoint(PcbTraceGeometry trace, int x, int y) {
        int[] xs = trace.getXPoints(), ys = trace.getYPoints();
        for (int index = 0; index < xs.length; index++)
            if (xs[index] == x && ys[index] == y) return true;
        return false;
    }

    private static void equal(Rectangle expected, Rectangle actual, String message) {
        check(expected.x == actual.x && expected.y == actual.y &&
            expected.width == actual.width && expected.height == actual.height,
            message + " expected=" + expected + " actual=" + actual);
    }

    private static void reject(String message, Action action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(true, message);
            return;
        } catch (IllegalStateException expected) {
            check(true, message);
            return;
        }
        throw new AssertionError("Expected rejection: " + message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }
}
