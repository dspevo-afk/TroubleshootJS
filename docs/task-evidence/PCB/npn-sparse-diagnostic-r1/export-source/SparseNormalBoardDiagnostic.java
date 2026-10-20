package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.Vector;

/** Read-only export of the accepted normal-player NPN sparse-board replay. */
public final class SparseNormalBoardDiagnostic {
    private static final String PREFIX = "PCB_SPARSE_NORMAL_DIAGNOSTIC ";
    private static final String NPN_FAMILY = QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH;
    private static int assertions;

    private SparseNormalBoardDiagnostic() { }

    public static void main(String[] args) {
        Request request = parseArguments(args);
        CirSim prior = CircuitElm.sim;
        CirSim sim = new CirSim();
        sim.elmList = new Vector<CircuitElm>();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        GeneratedBoardInstance owner = null;
        try {
            PlayerLaunchRequest launch = PlayerLaunchRequest.parse(request.replayId);
            check(!launch.candidateSearch && request.family.equals(launch.familyId) &&
                    request.profile.equals(launch.profile.name()) &&
                    request.seedText.equals(Long.toString(launch.seed)) &&
                    request.replayId.equals(launch.replay()),
                "the supplied canonical public replay resolves without candidate search");
            GenerationRequest generation = launch.generation();
            check(!generation.isCandidateSearch() &&
                    request.family.equals(generation.getFamilyId()) &&
                    generation.getDescriptor().getRootSeed() == launch.seed,
                "the normal generation request retains the exact replay family and signed seed");
            GenerationRequest.Prepared prepared = generation.resolve(
                new GenerationRequest.PlanCache());
            String executionPolicy = generation.getExecutionPolicy().identity();
            owner = prepared.construct().instance;
            check(owner != null && owner.getSeed() == launch.seed &&
                    request.family.equals(owner.getCircuitFamilyId()),
                "one exact normal construction retains the accepted public identity");
            export(request, launch, owner, executionPolicy);
        } finally {
            try {
                dispose(owner);
            } finally {
                CircuitElm.sim = prior;
            }
        }
        System.out.println("PASS: sparse normal board diagnostic family=" + request.family +
            " seed=" + request.seedText + " assertions=" + assertions);
    }

    private static Request parseArguments(String[] args) {
        if (args == null || args.length != 4 || !"--family".equals(args[0]) ||
                !"--seed".equals(args[2]))
            throw new IllegalArgumentException(
                "Usage: --family NPN_LOW_SIDE_SWITCH --seed 380469407799690561");
        String family = args[1];
        if (!NPN_FAMILY.equals(family))
            throw new IllegalArgumentException("Unsupported sparse diagnostic family");
        String profile = DifficultyProfile.EASY.name();

        String seedText = args[3];
        final long seed;
        try {
            seed = Long.parseLong(seedText);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Sparse diagnostic seed is not a signed long");
        }
        if (!Long.toString(seed).equals(seedText))
            throw new IllegalArgumentException("Sparse diagnostic seed is not canonical");
        if (seed != 380469407799690561L)
            throw new IllegalArgumentException("Seed is not the pinned accepted public replay for this family");
        String replayId = PlayerLaunchRequest.EPOCH + "/" + profile + "/" + family + "/" + seedText;
        return new Request(family, profile, seedText, seed, replayId);
    }

    private static void export(Request request, PlayerLaunchRequest launch,
            GeneratedBoardInstance owner, String executionPolicy) {
        check(owner != null, "normal construction returned an owner");
        check(owner.getSeed() == launch.seed && request.family.equals(owner.getCircuitFamilyId()),
            "owner retains the exact accepted replay identity");
        TroubleshootBoard board = owner.getBoard();
        PcbBoardLayout layout = owner.getPcbLayout();
        check(board != null && layout != null, "normal construction returned board and selected layout");

        String graphBefore = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String geometryBefore = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        owner.requireNormalPhysicalAdmission();
        layout.validateGeometry(board);
        layout.validateRoutingGeometry(board);
        layout.validateRouteQuality();
        PcbTraceClearance.Statistics clearance = PcbTraceClearance.validate(layout.getTraces());
        String graphAfterAdmission = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String geometryAfterAdmission = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        check(graphBefore.equals(graphAfterAdmission),
            "normal physical admission and geometry validation preserve the live graph");
        check(geometryBefore.equals(geometryAfterAdmission),
            "normal physical admission and geometry validation preserve selected geometry");

        requireExactInventories(board, layout);
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        int accessReachableCells = PcbAccessPlanner.validate(layout.getBoardOutline(),
            orderedFootprints(board, layout), constraints.getTraceWidthProfile().maximumWidth());
        int whitespace = PcbFunctionalGroupingContractTest.reportWhitespace(
            board, layout, launch.seed, 1);
        String graphAfterSampler = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String geometryAfterSampler = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        check(graphAfterAdmission.equals(graphAfterSampler),
            "existing independent whitespace sampler preserves the live graph");
        check(geometryAfterAdmission.equals(geometryAfterSampler),
            "existing independent whitespace sampler preserves selected geometry");

        Rectangle outline = layout.getBoardOutline();
        long independentArea = (long) outline.width * outline.height;
        long independentCopper = copperManhattanLength(layout);
        int independentVias = viaCount(layout);
        PcbPackingProbeReceipt probe = layout.getPackingProbeReceipt();
        check(probe != null, "normal selected layout carries its packing probe receipt");
        probe.requireGeometry(layout);

        PcbPackingMetrics metrics = null;
        String metricsUnavailable = null;
        try {
            metrics = PcbPackingMetrics.measure(board, layout);
        } catch (PcbPackingMetrics.Unavailable unavailable) {
            metricsUnavailable = unavailable.getMessage();
        }
        if (metrics != null) {
            check(metrics.area == independentArea && metrics.copper == independentCopper &&
                    metrics.vias == independentVias && metrics.protectedVoid == whitespace,
                "production metrics agree with independent area, copper, via and whitespace evidence");
        }

        String graphAfterExport = PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        String geometryAfterExport = PlayerSessionFingerprint.of(layout.geometryFingerprint());
        check(graphBefore.equals(graphAfterExport),
            "all physical observations and metrics preserve the live graph");
        check(geometryBefore.equals(geometryAfterExport),
            "all physical observations and metrics preserve selected geometry");
        String admission = owner.getPhysicalAdmission() == null ?
            SupportedEnvelope.current().identity() : owner.getPhysicalAdmission().identity();

        StringBuilder json = new StringBuilder(100000);
        json.append('{');
        field(json, "schema", "sparse-normal-board@1");
        field(json, "family", request.family);
        field(json, "profile", request.profile);
        field(json, "publicSeed", request.seedText);
        field(json, "replayId", request.replayId);
        field(json, "executionPolicy", executionPolicy);
        field(json, "physicalAdmission", admission);
        json.append(",\"identity\":{");
        field(json, "selectedGeometrySha256Before", geometryBefore);
        field(json, "selectedGeometrySha256After", geometryAfterExport);
        json.append(",\"selectedGeometryUnchanged\":true");
        field(json, "liveGraphSha256Before", graphBefore);
        field(json, "liveGraphSha256After", graphAfterExport);
        json.append(",\"liveGraphUnchanged\":true");
        json.append(",\"componentIds\":").append(stringArray(sorted(board.getComponentIds())));
        json.append(",\"padIds\":").append(stringArray(sorted(board.getPadIds())));
        json.append(",\"netIds\":").append(stringArray(sorted(board.getNetIds())));
        json.append('}');

        json.append(",\"counts\":{");
        json.append("\"logicalParts\":").append(board.getComponentIds().size());
        json.append(",\"layoutParts\":").append(layout.getComponents().size());
        json.append(",\"logicalPads\":").append(board.getPadIds().size());
        json.append(",\"layoutPads\":").append(layout.getPads().size());
        json.append(",\"logicalNets\":").append(board.getNetIds().size());
        json.append(",\"traces\":").append(layout.getTraces().size());
        json.append(",\"holes\":").append(layout.getHoleCount());
        json.append('}');

        json.append(",\"physicalMetrics\":");
        appendMetrics(json, metrics, metricsUnavailable);
        json.append(",\"independentMetrics\":{");
        json.append("\"samplerVersion\":1,\"samplerGridStep\":10");
        json.append(",\"area\":").append(independentArea);
        json.append(",\"copper\":").append(independentCopper);
        json.append(",\"vias\":").append(independentVias);
        json.append(",\"protectedVoid\":").append(whitespace);
        json.append(",\"matchesProduction\":").append(metrics != null);
        if (metrics == null) nullableField(json, "comparisonUnavailableReason", metricsUnavailable);
        json.append('}');
        json.append(",\"packingProbe\":");
        appendProbe(json, probe);

        json.append(",\"layout\":{");
        json.append("\"algorithmVersion\":").append(layout.getLayoutAlgorithmVersion());
        json.append(",\"canvas\":[").append(layout.getWidth()).append(',')
            .append(layout.getHeight()).append(']');
        json.append(",\"outline\":"); appendRect(json, layout.getBoardOutline());
        json.append(",\"partsTray\":"); appendRect(json, layout.getPartsTray());
        json.append(",\"components\":");
        appendComponents(json, board, layout, constraints);
        json.append(",\"pads\":"); appendPads(json, board, layout);
        json.append(",\"nets\":"); appendNets(json, board);
        json.append(",\"traces\":"); appendTraces(json, board, layout, constraints);
        json.append(",\"holes\":"); appendHoles(json, layout);
        json.append(",\"silkscreen\":"); appendSilkscreen(json, layout);
        json.append(",\"constraints\":"); appendConstraints(json, constraints);
        json.append('}');

        json.append(",\"validation\":{");
        field(json, "normalAdmission", "PASS");
        field(json, "geometry", "PASS");
        field(json, "routingGeometry", "PASS");
        field(json, "routeQuality", "PASS");
        field(json, "traceClearance", "PASS");
        field(json, "access", "PASS");
        field(json, "independentMetricComparison", metrics == null ? "NOT_COMPARABLE" : "PASS");
        json.append(",\"accessReachableCells\":").append(accessReachableCells);
        json.append(",\"traceClearanceExactPairs\":").append(clearance.exactPairs);
        json.append(",\"traceClearancePotentialPairs\":").append(clearance.potentialPairs);
        json.append(",\"graphImmutability\":true");
        json.append(",\"selectedGeometryImmutability\":true");
        json.append('}');
        json.append('}');

        check(graphBefore.equals(PcbCanvasBoundaryContractTest.liveGraphSha256(board)) &&
                geometryBefore.equals(PlayerSessionFingerprint.of(layout.geometryFingerprint())),
            "final serialization preserves the accepted graph and selected geometry");
        System.out.println(PREFIX + json.toString());
    }

    private static void requireExactInventories(TroubleshootBoard board,
            PcbBoardLayout layout) {
        TreeSet<String> logicalComponents = new TreeSet<String>(board.getComponentIds());
        TreeSet<String> layoutComponents = new TreeSet<String>();
        for (PcbComponentPlacement placement : layout.getComponents())
            check(layoutComponents.add(placement.getComponentId()),
                "selected component identity is unique " + placement.getComponentId());
        check(logicalComponents.equals(layoutComponents),
            "selected geometry retains the exact logical component inventory");

        TreeSet<String> logicalPads = new TreeSet<String>(board.getPadIds());
        TreeSet<String> layoutPads = new TreeSet<String>();
        for (PcbPadPlacement pad : layout.getPads())
            check(layoutPads.add(pad.getPadId()), "selected pad identity is unique " + pad.getPadId());
        check(logicalPads.equals(layoutPads),
            "selected geometry retains the exact logical pad inventory");
        check(layout.getComponents().size() == board.getComponentIds().size() &&
                layout.getPads().size() == board.getPadIds().size(),
            "selected component and pad counts match the authoritative graph");
    }

    private static Vector<String> sorted(Vector<String> source) {
        Vector<String> result = new Vector<String>(source);
        Collections.sort(result);
        return result;
    }

    private static Vector<PcbFootprint> orderedFootprints(TroubleshootBoard board,
            PcbBoardLayout layout) {
        Vector<PcbFootprint> result = new Vector<PcbFootprint>();
        for (String id : sorted(board.getComponentIds())) {
            BoardComponent component = board.getComponent(id);
            PcbComponentPlacement placement = layout.getComponent(id);
            check(component != null && placement != null,
                "component and placement exist for access validation " + id);
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            for (String padId : component.getPadIds()) {
                PcbPadPlacement pad = layout.getPad(padId);
                check(pad != null, "selected placement exists for pad " + padId);
                pads.add(pad);
            }
            result.add(new PcbFootprint(placement, pads));
        }
        return result;
    }

    private static void appendComponents(StringBuilder out, TroubleshootBoard board,
            PcbBoardLayout layout, PcbPlacementConstraints constraints) {
        out.append('[');
        Vector<String> ids = sorted(board.getComponentIds());
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) out.append(',');
            String id = ids.get(index);
            BoardComponent component = board.getComponent(id);
            PcbComponentPlacement placement = layout.getComponent(id);
            PcbPlacementConstraints.Part part = constraints.get(id);
            check(component != null && placement != null && part != null,
                "selected physical component and constraints exist " + id);
            PhysicalPackage physical = placement.getPhysicalPackage();
            PhysicalPackageGeometry geometry = placement.getPhysicalGeometry();
            PhysicalPackageGeometry.Placement placed = geometry.placedAt(placement.getPose());
            PcbFootprint footprint = footprintFor(board, layout, id);

            out.append('{');
            field(out, "componentId", id);
            field(out, "type", component.getType());
            field(out, "publicReference", component.getDisplayName());
            field(out, "packageId", physical.getId());
            out.append(",\"packageConnector\":").append(physical.isConnector());
            out.append(",\"packageDeveloperGeneric\":").append(physical.isDeveloperGeneric());
            out.append(",\"packageGeometryVersion\":")
                .append(physical.getGeometryContractVersionValue());
            field(out, "geometryVariant", placement.getGeometryVariantKey());
            field(out, "geometryTransform", placement.getGeometryTransformKey());
            out.append(",\"packageGeometrySize\":[").append(geometry.getWidth()).append(',')
                .append(geometry.getHeight()).append(']');
            out.append(",\"pose\":{");
            out.append("\"x\":").append(placement.getX());
            out.append(",\"y\":").append(placement.getY());
            out.append(",\"width\":").append(placement.getWidth());
            out.append(",\"height\":").append(placement.getHeight());
            field(out, "rotation", placement.getRotation().name());
            field(out, "mountingSide", placement.getMountingSide().name());
            out.append('}');
            field(out, "regionId", part.regionId);
            field(out, "regionLabel", part.regionLabel);
            field(out, "domainId", part.domainId);
            field(out, "anchor", part.anchor.name());
            out.append(",\"accessMargin\":").append(part.accessMargin);
            out.append(",\"bounds\":"); appendRect(out, placement.getBounds());
            out.append(",\"bodyBounds\":"); appendRect(out, placement.getBodyBounds());
            out.append(",\"keepOut\":"); appendRect(out, placement.getKeepOut());
            out.append(",\"routingCourtyard\":");
            appendRect(out, placement.getRoutingCourtyard());
            out.append(",\"accessEnvelope\":");
            appendRect(out, PcbPlacementPlanner.envelope(footprint, part.accessMargin));
            out.append(",\"selectionBounds\":");
            appendRect(out, placement.getSelectionEnvelope());
            out.append(",\"dragBounds\":"); appendRect(out, placement.getDragEnvelope());
            out.append(",\"domainCourtyards\":");
            appendDomainCourtyards(out, constraints, placement);
            out.append(",\"terminalDomains\":"); appendTerminalDomains(out, part);
            out.append(",\"terminals\":");
            appendTerminals(out, board, layout, component, placement, physical, geometry,
                placed, part);
            out.append('}');
        }
        out.append(']');
    }

    private static PcbFootprint footprintFor(TroubleshootBoard board,
            PcbBoardLayout layout, String componentId) {
        BoardComponent component = board.getComponent(componentId);
        PcbComponentPlacement placement = layout.getComponent(componentId);
        check(component != null && placement != null,
            "selected footprint has a component and placement " + componentId);
        Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
        for (String padId : component.getPadIds()) {
            PcbPadPlacement pad = layout.getPad(padId);
            check(pad != null, "selected footprint retains pad " + padId);
            pads.add(pad);
        }
        return new PcbFootprint(placement, pads);
    }

    private static void appendTerminals(StringBuilder out, TroubleshootBoard board,
            PcbBoardLayout layout, BoardComponent component, PcbComponentPlacement placement,
            PhysicalPackage physical, PhysicalPackageGeometry geometry,
            PhysicalPackageGeometry.Placement placed, PcbPlacementConstraints.Part part) {
        Vector<String> padIds = component.getPadIds();
        Vector<String> terminalIds = physical.getTerminalIds();
        check(padIds.size() == terminalIds.size() &&
                terminalIds.size() == geometry.getTerminals().size(),
            "package, logical pads and physical terminals have matching declarations " + component.getId());
        out.append('[');
        for (int index = 0; index < padIds.size(); index++) {
            if (index > 0) out.append(',');
            String padId = padIds.get(index);
            BoardPad logical = board.getPad(padId);
            PcbPadPlacement pad = layout.getPad(padId);
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            check(logical != null && pad != null && terminal != null &&
                    terminal.getTerminalId().equals(logical.getTerminalId()) &&
                    terminal.getTerminalId().equals(terminalIds.get(index)),
                "logical endpoint matches selected package terminal " + padId);
            out.append('{');
            out.append("\"index\":").append(index);
            field(out, "terminalId", logical.getTerminalId());
            field(out, "padId", logical.getId());
            field(out, "netId", logical.getNetId());
            nullableField(out, "domainId", part.terminalDomain(logical.getTerminalId()));
            field(out, "attachment", pad.getAttachment().name());
            field(out, "mountingSide", pad.getMountingSide().name());
            field(out, "exposure", pad.getExposure().name());
            out.append(",\"padCenter\":"); appendPoint(out, new Point(pad.getX(), pad.getY()));
            out.append(",\"padBounds\":"); appendRect(out, pad.getPadBounds());
            out.append(",\"boardProbeCenter\":");
            appendPoint(out, placed.getBoardPadProbeCenter(index));
            out.append(",\"boardProbeBounds\":"); appendRect(out, pad.getProbeBounds());
            out.append(",\"escape\":{");
            out.append("\"dx\":").append(pad.getEscapeDx());
            out.append(",\"dy\":").append(pad.getEscapeDy());
            out.append(",\"length\":").append(pad.getEscapeLength());
            out.append('}');
            out.append(",\"connectedLead\":"); appendLead(out, placement, index, false);
            out.append(",\"liftedLead\":"); appendLead(out, placement, index, true);
            out.append('}');
        }
        out.append(']');
    }

    private static void appendLead(StringBuilder out, PcbComponentPlacement placement,
            int index, boolean lifted) {
        out.append('{');
        out.append("\"endPoint\":"); appendPoint(out, placement.getLeadEndPoint(index, lifted));
        out.append(",\"bodyPoint\":"); appendPoint(out, placement.getLeadBodyPoint(index, lifted));
        out.append(",\"bounds\":"); appendRect(out, placement.getLeadBounds(index, lifted));
        out.append(",\"probeCenter\":");
        appendPoint(out, placement.getComponentLeadProbeCenter(index, lifted));
        out.append(",\"probeBounds\":");
        appendRect(out, placement.getComponentLeadProbeBounds(index, lifted));
        out.append('}');
    }

    private static void appendDomainCourtyards(StringBuilder out,
            PcbPlacementConstraints constraints, PcbComponentPlacement placement) {
        java.util.TreeMap<String,Rectangle> domains = constraints.domainCourtyards(placement);
        out.append('[');
        boolean first = true;
        for (java.util.Map.Entry<String,Rectangle> entry : domains.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('{');
            field(out, "domainId", entry.getKey());
            out.append(",\"bounds\":"); appendRect(out, entry.getValue());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendTerminalDomains(StringBuilder out,
            PcbPlacementConstraints.Part part) {
        Vector<PcbPlacementConstraints.TerminalDomain> domains = part.getTerminalDomains();
        out.append('[');
        for (int index = 0; index < domains.size(); index++) {
            if (index > 0) out.append(',');
            PcbPlacementConstraints.TerminalDomain domain = domains.get(index);
            out.append('{');
            field(out, "terminalId", domain.terminalId);
            field(out, "domainId", domain.domainId);
            out.append('}');
        }
        out.append(']');
    }

    private static void appendPads(StringBuilder out, TroubleshootBoard board,
            PcbBoardLayout layout) {
        out.append('[');
        Vector<String> ids = sorted(board.getPadIds());
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) out.append(',');
            String id = ids.get(index);
            BoardPad logical = board.getPad(id);
            PcbPadPlacement pad = layout.getPad(id);
            check(logical != null && pad != null, "logical pad has a selected physical endpoint " + id);
            out.append('{');
            field(out, "padId", logical.getId());
            field(out, "componentId", logical.getComponentId());
            field(out, "terminalId", logical.getTerminalId());
            field(out, "netId", logical.getNetId());
            out.append(",\"center\":"); appendPoint(out, new Point(pad.getX(), pad.getY()));
            out.append(",\"bounds\":"); appendRect(out, pad.getPadBounds());
            out.append(",\"probeBounds\":"); appendRect(out, pad.getProbeBounds());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendNets(StringBuilder out, TroubleshootBoard board) {
        out.append('[');
        Vector<String> ids = sorted(board.getNetIds());
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) out.append(',');
            String id = ids.get(index);
            BoardNet net = board.getNet(id);
            check(net != null, "authoritative net exists " + id);
            out.append('{');
            field(out, "netId", id);
            field(out, "routingRole", net.getRoutingRole().name());
            jsonField(out, "padIds", stringArray(sorted(net.getPadIds())));
            out.append('}');
        }
        out.append(']');
    }

    private static void appendTraces(StringBuilder out, TroubleshootBoard board,
            PcbBoardLayout layout, PcbPlacementConstraints constraints) {
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        Collections.sort(traces, new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry first, PcbTraceGeometry second) {
                int result = nullable(first.getSourceId(), second.getSourceId());
                if (result != 0) return result;
                result = nullable(first.getNetId(), second.getNetId());
                if (result != 0) return result;
                result = first.getLayer().compareTo(second.getLayer());
                if (result != 0) return result;
                result = first.getExposure().compareTo(second.getExposure());
                if (result != 0) return result;
                result = nullable(first.getStartPadId(), second.getStartPadId());
                if (result != 0) return result;
                result = nullable(first.getEndPadId(), second.getEndPadId());
                if (result != 0) return result;
                if (first.getWidth() != second.getWidth())
                    return first.getWidth() < second.getWidth() ? -1 : 1;
                result = coordinates(first.getXPoints(), second.getXPoints());
                return result != 0 ? result : coordinates(first.getYPoints(), second.getYPoints());
            }
        });
        int maxWidth = constraints.getTraceWidthProfile().maximumWidth();
        out.append('[');
        for (int index = 0; index < traces.size(); index++) {
            if (index > 0) out.append(',');
            PcbTraceGeometry trace = traces.get(index);
            check(board.getNet(trace.getNetId()) != null,
                "routed trace retains an authoritative net " + trace.getNetId());
            int[] xs = trace.getXPoints();
            int[] ys = trace.getYPoints();
            check(xs.length == ys.length && xs.length >= 2,
                "routed trace has paired coordinate arrays");
            out.append('{');
            nullableField(out, "sourceId", trace.getSourceId());
            field(out, "netId", trace.getNetId());
            nullableField(out, "startPadId", trace.getStartPadId());
            nullableField(out, "endPadId", trace.getEndPadId());
            field(out, "layer", trace.getLayer().name());
            field(out, "exposure", trace.getExposure().name());
            out.append(",\"width\":").append(trace.getWidth());
            out.append(",\"clearanceGuard\":")
                .append(PcbTraceRules.clearance(trace.getWidth(), maxWidth));
            out.append(",\"points\":[");
            for (int point = 0; point < xs.length; point++) {
                if (point > 0) out.append(',');
                out.append('[').append(xs[point]).append(',').append(ys[point]).append(']');
            }
            out.append("]}");
        }
        out.append(']');
    }

    private static void appendHoles(StringBuilder out, PcbBoardLayout layout) {
        Vector<PcbBoardHole> holes = layout.getHoles();
        Collections.sort(holes, new Comparator<PcbBoardHole>() {
            public int compare(PcbBoardHole first, PcbBoardHole second) {
                return first.id.compareTo(second.id);
            }
        });
        out.append('[');
        for (int index = 0; index < holes.size(); index++) {
            if (index > 0) out.append(',');
            PcbBoardHole hole = holes.get(index);
            out.append('{');
            field(out, "id", hole.id);
            field(out, "kind", hole.kind.name());
            nullableField(out, "netId", hole.netId);
            field(out, "exposure", hole.exposure.name());
            out.append(",\"x\":").append(hole.x);
            out.append(",\"y\":").append(hole.y);
            out.append(",\"drillRadius\":").append(hole.drillRadius);
            out.append(",\"landRadius\":").append(hole.landRadius);
            out.append(",\"bounds\":"); appendRect(out, hole.getBounds());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendSilkscreen(StringBuilder out, PcbBoardLayout layout) {
        Vector<PcbSilkscreenLabel> labels = layout.getSilkscreenLabels();
        Collections.sort(labels, new Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel first, PcbSilkscreenLabel second) {
                return first.getId().compareTo(second.getId());
            }
        });
        out.append('[');
        for (int index = 0; index < labels.size(); index++) {
            if (index > 0) out.append(',');
            PcbSilkscreenLabel label = labels.get(index);
            out.append('{');
            field(out, "id", label.getId());
            field(out, "text", label.getText());
            out.append(",\"bounds\":"); appendRect(out, label.getBounds());
            out.append(",\"baselineX\":").append(label.getBaselineX());
            out.append(",\"baselineY\":").append(label.getBaselineY());
            out.append(",\"fontSize\":").append(label.getFontSize());
            out.append(",\"bold\":").append(label.isBold());
            nullableField(out, "targetPadId", label.getTargetPadId());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendConstraints(StringBuilder out,
            PcbPlacementConstraints constraints) {
        out.append('{');
        field(out, "physicalPolicy", constraints.getPhysicalPolicyIdentity());
        out.append(",\"physicalPolicyVersion\":").append(constraints.getPhysicalPolicyVersion());
        out.append(",\"placementDensityVersion\":").append(constraints.placementDensityVersion);
        out.append(",\"placementPackingVersion\":").append(constraints.getPlacementPackingVersion());
        field(out, "routingLayer", constraints.routingLayer.name());
        out.append(",\"requiresTwoLayerRouting\":")
            .append(constraints.requiresTwoLayerRouting);
        field(out, "traceWidthProfile", constraints.getTraceWidthProfile().canonical());
        Vector<PcbPlacementConstraints.Barrier> barriers = constraints.getBarriers();
        Collections.sort(barriers, new Comparator<PcbPlacementConstraints.Barrier>() {
            public int compare(PcbPlacementConstraints.Barrier first,
                    PcbPlacementConstraints.Barrier second) {
                int result = first.firstDomain.compareTo(second.firstDomain);
                if (result != 0) return result;
                result = first.secondDomain.compareTo(second.secondDomain);
                if (result != 0) return result;
                if (first.clearance == second.clearance) return 0;
                return first.clearance < second.clearance ? -1 : 1;
            }
        });
        out.append(",\"barriers\":[");
        for (int index = 0; index < barriers.size(); index++) {
            if (index > 0) out.append(',');
            PcbPlacementConstraints.Barrier barrier = barriers.get(index);
            out.append('{');
            field(out, "firstDomain", barrier.firstDomain);
            field(out, "secondDomain", barrier.secondDomain);
            out.append(",\"clearance\":").append(barrier.clearance);
            out.append('}');
        }
        out.append("]}");
    }

    private static void appendMetrics(StringBuilder out, PcbPackingMetrics metrics,
            String unavailable) {
        out.append('{');
        out.append("\"available\":").append(metrics != null);
        out.append(",\"unavailable\":").append(metrics == null);
        out.append(",\"gridStep\":").append(PcbPackingMetrics.GRID_STEP);
        if (metrics == null) {
            nullableField(out, "reason", unavailable);
            out.append(",\"area\":null,\"copper\":null,\"vias\":null,\"protectedVoid\":null");
        } else {
            out.append(",\"area\":").append(metrics.area);
            out.append(",\"copper\":").append(metrics.copper);
            out.append(",\"vias\":").append(metrics.vias);
            out.append(",\"protectedVoid\":").append(metrics.protectedVoid);
            field(out, "protectedVoidMeaning", "largest protected empty drawing-space rectangle; not packing proof");
        }
        out.append('}');
    }

    private static void appendProbe(StringBuilder out, PcbPackingProbeReceipt probe) {
        out.append('{');
        field(out, "canonical", probe.toCanonical());
        out.append(",\"attempts\":").append(probe.attempts);
        out.append(",\"orderings\":").append(probe.orderings);
        out.append(",\"expansions\":").append(probe.expansions);
        out.append(",\"millis\":").append(probe.millis);
        field(out, "routePolicy", probe.routePolicy);
        field(out, "disposition", probe.disposition);
        field(out, "baselineGeometrySha256", probe.baselineGeometry);
        field(out, "selectedGeometrySha256", probe.selectedGeometry);
        out.append(",\"metricEvidence\":");
        PcbPackingProbeReceipt.MetricEvidence evidence = probe.metricEvidence;
        out.append('{');
        field(out, "state", evidence.state.name());
        out.append(",\"baseline\":"); appendMetricValue(out, evidence.baseline);
        out.append(",\"candidate\":"); appendMetricValue(out, evidence.candidate);
        nullableField(out, "unavailableReason", evidence.unavailableReason);
        out.append("}}");
    }

    private static void appendMetricValue(StringBuilder out, PcbPackingMetrics metrics) {
        if (metrics == null) {
            out.append("null");
            return;
        }
        out.append('{');
        out.append("\"area\":").append(metrics.area);
        out.append(",\"copper\":").append(metrics.copper);
        out.append(",\"vias\":").append(metrics.vias);
        out.append(",\"protectedVoid\":").append(metrics.protectedVoid);
        out.append('}');
    }

    private static long copperManhattanLength(PcbBoardLayout layout) {
        long total = 0L;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] xs = trace.getXPoints();
            int[] ys = trace.getYPoints();
            check(xs.length == ys.length, "copper metric receives paired route coordinates");
            for (int index = 1; index < xs.length; index++)
                total += Math.abs((long) xs[index] - xs[index - 1]) +
                    Math.abs((long) ys[index] - ys[index - 1]);
        }
        return total;
    }

    private static int viaCount(PcbBoardLayout layout) {
        int total = 0;
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) total++;
        return total;
    }

    private static int nullable(String first, String second) {
        if (first == null) return second == null ? 0 : -1;
        if (second == null) return 1;
        return first.compareTo(second);
    }

    private static int coordinates(int[] first, int[] second) {
        int count = Math.min(first.length, second.length);
        for (int index = 0; index < count; index++)
            if (first[index] != second[index]) return first[index] < second[index] ? -1 : 1;
        if (first.length == second.length) return 0;
        return first.length < second.length ? -1 : 1;
    }

    private static void appendRect(StringBuilder out, Rectangle rect) {
        if (rect == null) throw new IllegalArgumentException("Missing physical rectangle");
        PcbCoordinateSystem.requireBoardRectangle(rect);
        out.append('[').append(rect.x).append(',').append(rect.y).append(',')
            .append(rect.width).append(',').append(rect.height).append(']');
    }

    private static void appendPoint(StringBuilder out, Point point) {
        PcbCoordinateSystem.requireBoardPoint(point);
        out.append('[').append(point.x).append(',').append(point.y).append(']');
    }

    private static String stringArray(Vector<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            appendString(out, values.get(index));
        }
        return out.append(']').toString();
    }

    private static void field(StringBuilder out, String key, String value) {
        comma(out); appendString(out, key); out.append(':'); appendString(out, value);
    }

    private static void nullableField(StringBuilder out, String key, String value) {
        comma(out); appendString(out, key); out.append(':');
        if (value == null) out.append("null"); else appendString(out, value);
    }

    private static void jsonField(StringBuilder out, String key, String value) {
        comma(out); appendString(out, key); out.append(':').append(value);
    }

    private static void comma(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '{' &&
                out.charAt(out.length() - 1) != '[') out.append(',');
    }

    private static void appendString(StringBuilder out, String value) {
        if (value == null) throw new IllegalArgumentException("Missing JSON string");
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c == '\b') out.append("\\b");
            else if (c == '\f') out.append("\\f");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else if (c < 0x20) {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                for (int pad = hex.length(); pad < 4; pad++) out.append('0');
                out.append(hex);
            } else out.append(c);
        }
        out.append('"');
    }

    private static void dispose(GeneratedBoardInstance owner) {
        if (owner == null) return;
        owner.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : owner.getSimulationElements()) element.delete();
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Request {
        final String family;
        final String profile;
        final String seedText;
        final long seed;
        final String replayId;

        Request(String family, String profile, String seedText, long seed, String replayId) {
            this.family = family;
            this.profile = profile;
            this.seedText = seedText;
            this.seed = seed;
            this.replayId = replayId;
        }
    }
}
