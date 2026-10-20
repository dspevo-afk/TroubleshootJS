package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/**
 * Read-only RB30 geometry export for the fresh multi-rail gap diagnosis.
 *
 * <p>This is deliberately task-owned evidence source.  It resolves one
 * canonical signed-long seed, uses the current RB30 plan and medium policy,
 * validates the selected routed layout through the existing physical owners,
 * and emits one namespaced JSON row.  It does not construct solver state,
 * apply a fault, or run the RB56 occupancy helper.</p>
 */
public final class FreshRb30GapDiagnostic {
    private static int assertions;

    private FreshRb30GapDiagnostic() { }

    public static void main(String[] args) {
        long seed = parseSeed(args);
        export(seed);
        System.out.println("PASS: fresh RB30 gap diagnostic seed=" + Long.toString(seed) +
            " assertions=" + assertions);
    }

    private static long parseSeed(String[] args) {
        if (args == null || args.length != 2 || !"--seed".equals(args[0]))
            throw new IllegalArgumentException("Usage: --seed <canonical signed-long>");
        final long seed;
        try {
            seed = Long.parseLong(args[1]);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("RB30 diagnostic seed is not a signed long");
        }
        if (!Long.toString(seed).equals(args[1]))
            throw new IllegalArgumentException("RB30 diagnostic seed is not canonical");
        return seed;
    }

    private static void export(long seed) {
        Rb30Plan plan = Rb30Plan.resolve(seed);
        TroubleshootBoard board = plan.board();
        String logicalBefore = logicalBoardCanonical(board);

        MediumBoardPhysicalPolicy.Result route = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
        check(route != null && route.accepted() && route.getLayout() != null,
            "RB30 medium route is accepted for seed " + Long.toString(seed));
        if (route == null || !route.accepted() || route.getLayout() == null)
            throw new IllegalStateException("RB30 medium route did not produce a selected layout");

        PcbBoardLayout layout = route.getLayout();
        String geometryBefore = layout.geometryFingerprint();
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        Vector<PcbFootprint> footprints = orderedFootprints(board, layout);

        // Keep every check on the current selected layout and current policy.
        // These are evidence checks, not alternate routing or packing paths.
        layout.validateGeometry(board);
        layout.validateRoutingGeometry(board);
        layout.validateRouteQuality();
        PcbTraceClearance.Statistics clearance =
            PcbTraceClearance.validate(layout.getTraces());
        new PcbTwoLayerRules(board, layout).validate(layout);
        PcbTwoLayerRules.requireNormalMediumGeometry(board, layout,
            PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias);
        int accessReachableCells = PcbAccessPlanner.validate(layout.getBoardOutline(),
            footprints, constraints.getTraceWidthProfile().maximumWidth());

        boolean top = false;
        boolean bottom = false;
        int vias = 0;
        for (PcbTraceGeometry trace : layout.getTraces()) {
            top |= trace.getLayer() == PcbCopperLayer.TOP;
            bottom |= trace.getLayer() == PcbCopperLayer.BOTTOM;
        }
        for (PcbBoardHole hole : layout.getHoles())
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
        check(top && bottom, "selected route contains both copper faces");
        check(vias > 0, "selected P07 route contains a via");
        check(layout.matchesGenerationSeeds(plan.layoutSeed, plan.routingSeed),
            "selected layout retains exact RB30 layout/routing seeds");

        MediumBoardPhysicalPolicy.Statistics statistics = route.getStatistics();
        check(statistics != null && statistics.selectedPlacementAttempt >= 0,
            "medium receipt has a selected placement attempt");
        check(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                statistics.selectedRoutePolicy), "medium receipt selects P07");
        PcbPackingProbeReceipt packing = layout.getPackingProbeReceipt();
        if (packing != null)
            packing.requireGeometry(layout);

        String logicalAfter = logicalBoardCanonical(board);
        String geometryAfter = layout.geometryFingerprint();
        check(logicalBefore.equals(logicalAfter),
            "logical RB30 board identity is unchanged by export");
        check(geometryBefore.equals(geometryAfter),
            "selected layout geometry fingerprint is unchanged by export");

        StringBuilder json = new StringBuilder(120000);
        json.append('{');
        field(json, "schema", "packing-metric-evidence@1");
        field(json, "seed", Long.toString(seed));
        field(json, "boardId", board.getId());
        field(json, "planCanonical", plan.canonical());
        json.append(",\"plan\":{");
        field(json, "family", Rb30Plan.FAMILY_ID);
        json.append(",\"version\":").append(Rb30Plan.PLAN_VERSION);
        field(json, "topology", plan.topology());
        field(json, "topologyAxis", plan.topologyAxis());
        field(json, "support", plan.supportIdentity());
        json.append(",\"channelCount\":").append(plan.channelCount);
        json.append(",\"layoutSeed\":").append(plan.layoutSeed);
        json.append(",\"routingSeed\":").append(plan.routingSeed);
        json.append(",\"packageCount\":").append(plan.physicalPackageCount());
        json.append('}');

        json.append(",\"geometryIdentity\":{");
        field(json, "geometryFingerprintBefore",
            PlayerSessionFingerprint.of(geometryBefore));
        field(json, "geometryFingerprintAfter",
            PlayerSessionFingerprint.of(geometryAfter));
        json.append(",\"geometryFingerprintEqual\":").append(
            Boolean.toString(geometryBefore.equals(geometryAfter)));
        field(json, "logicalBoardFingerprintBefore",
            PlayerSessionFingerprint.of(logicalBefore));
        field(json, "logicalBoardFingerprintAfter",
            PlayerSessionFingerprint.of(logicalAfter));
        json.append(",\"logicalBoardFingerprintEqual\":").append(
            Boolean.toString(logicalBefore.equals(logicalAfter)));
        json.append(",\"componentIds\":").append(stringArray(sorted(board.getComponentIds())));
        json.append(",\"padIds\":").append(stringArray(sorted(board.getPadIds())));
        json.append(",\"netIds\":").append(stringArray(sorted(board.getNetIds())));
        json.append('}');

        json.append(",\"route\":{");
        field(json, "outcome", statistics.outcome);
        field(json, "selectedRoutePolicy", statistics.selectedRoutePolicy);
        json.append(",\"selectedPlacementAttempt\":")
            .append(statistics.selectedPlacementAttempt);
        json.append(",\"placementCandidates\":").append(statistics.placementCandidates);
        json.append(",\"placementRejections\":").append(statistics.placementRejections);
        json.append(",\"placementEvaluations\":").append(statistics.placementEvaluations);
        json.append(",\"routeAttempts\":").append(statistics.routeAttempts);
        json.append(",\"routingOrders\":").append(statistics.routingOrders);
        json.append(",\"routingExpansions\":").append(statistics.routingExpansions);
        json.append(",\"selectedPlacementScore\":")
            .append(statistics.selectedPlacementScore);
        json.append(",\"selectedRouteQualityMilli\":")
            .append(statistics.selectedRouteQualityMilli);
        json.append(",\"rankedPlacementAttempts\":")
            .append(integerArray(statistics.rankedPlacementAttempts));
        json.append(",\"placementScores\":")
            .append(stringArray(statistics.placementScores));
        json.append(",\"routeOutcomes\":")
            .append(stringArray(statistics.routeOutcomes));
        // The native assessment records the complete Result canonical,
        // including the trailing failure receipt (null on this accepted row).
        field(json, "routeCanonical", route.toCanonical());
        nullableField(json, "failure", route.getFailure());
        json.append(",\"packingProbe\":");
        appendPackingProbe(json, packing);
        json.append('}');

        json.append(",\"layout\":{");
        json.append("\"algorithmVersion\":").append(layout.getLayoutAlgorithmVersion());
        json.append(",\"canvas\":[").append(layout.getWidth()).append(',')
            .append(layout.getHeight()).append(']');
        json.append(",\"outline\":");
        appendRect(json, layout.getBoardOutline());
        json.append(",\"partsTray\":");
        appendRect(json, layout.getPartsTray());
        json.append(",\"components\":");
        appendComponents(json, board, layout, constraints);
        json.append(",\"traces\":");
        appendTraces(json, board, layout, constraints);
        json.append(",\"vias\":");
        appendHoles(json, layout);
        json.append(",\"silk\":");
        appendSilk(json, layout);
        json.append('}');

        json.append(",\"physicalConstraints\":");
        appendConstraints(json, board, constraints);
        json.append(",\"domainUnions\":");
        appendDomainUnions(json, collectDomainUnions(board, layout));
        json.append(",\"nets\":");
        appendNets(json, board);
        json.append(",\"powerInputs\":");
        appendPowerInputs(json, board);

        json.append(",\"packingMetrics\":");
        appendPackingMetrics(json, board, layout);
        json.append(",\"validation\":{");
        field(json, "geometry", "PASS");
        field(json, "routingGeometry", "PASS");
        field(json, "routeQuality", "PASS");
        field(json, "traceClearance", "PASS");
        field(json, "p07Rules", "PASS");
        field(json, "normalMediumAdmission", "PASS");
        field(json, "access", "PASS");
        json.append(",\"accessReachableCells\":").append(accessReachableCells);
        json.append(",\"traceClearanceExactPairs\":").append(clearance.exactPairs);
        json.append(",\"traceClearancePotentialPairs\":").append(clearance.potentialPairs);
        json.append(",\"bothCopperFaces\":true");
        json.append(",\"viaCount\":").append(vias);
        json.append('}');
        json.append('}');
        check(logicalBefore.equals(logicalBoardCanonical(board)),
            "all serialization and metrics preserve logical RB30 identity");
        check(geometryBefore.equals(layout.geometryFingerprint()),
            "all serialization and metrics preserve selected layout geometry");
        System.out.println("PCB_PACKING_METRIC_EVIDENCE " + json.toString());
    }

    private static Vector<PcbFootprint> orderedFootprints(TroubleshootBoard board,
            PcbBoardLayout layout) {
        Vector<PcbFootprint> result = new Vector<PcbFootprint>();
        Vector<String> ids = sorted(board.getComponentIds());
        for (String id : ids)
            result.add(footprintFor(board, layout, id));
        return result;
    }

    private static PcbFootprint footprintFor(TroubleshootBoard board,
            PcbBoardLayout layout, String componentId) {
        BoardComponent component = board.getComponent(componentId);
        PcbComponentPlacement placement = layout.getComponent(componentId);
        if (component == null || placement == null)
            throw new IllegalArgumentException("Missing selected RB30 component: " + componentId);
        Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
        for (String padId : component.getPadIds()) {
            PcbPadPlacement pad = layout.getPad(padId);
            if (pad == null)
                throw new IllegalArgumentException("Missing selected RB30 pad: " + padId);
            pads.add(pad);
        }
        return new PcbFootprint(placement, pads);
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
            PcbFootprint footprint = footprintFor(board, layout, id);
            PhysicalPackage physical = placement.getPhysicalPackage();
            PhysicalPackageGeometry geometry = placement.getPhysicalGeometry();
            PhysicalPackageGeometry.Placement placed = geometry.placedAt(placement.getPose());
            check(component != null && part != null && physical != null && geometry != null,
                "complete selected component identity " + id);
            out.append('{');
            field(out, "componentId", id);
            field(out, "type", component.getType());
            field(out, "publicReference", component.getDisplayName());
            out.append(",\"referenceLabelCharacters\":")
                .append(component.getReferenceLabelCharacters());
            field(out, "packageId", physical.getId());
            out.append(",\"packageGeometryVersion\":")
                .append(physical.getGeometryContractVersionValue());
            field(out, "geometryVariant", placement.getGeometryVariantKey());
            field(out, "geometryTransform", placement.getGeometryTransformKey());
            out.append(",\"developerGeneric\":")
                .append(Boolean.toString(physical.isDeveloperGeneric()));
            out.append(",\"pose\":{");
            out.append("\"x\":").append(placement.getX());
            out.append(",\"y\":").append(placement.getY());
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
            out.append(",\"access\":");
            appendRect(out, PcbPlacementPlanner.envelope(footprint, part.accessMargin));
            out.append(",\"dragBounds\":"); appendRect(out, placement.getDragEnvelope());
            out.append(",\"selectionBounds\":");
            appendRect(out, placement.getSelectionEnvelope());
            out.append(",\"routingCourtyard\":");
            appendRect(out, placement.getRoutingCourtyard());
            out.append(",\"domainCourtyards\":");
            appendPlacementDomains(out, constraints, placement);
            out.append(",\"isolationBody\":");
            appendIsolationBody(out, geometry, placed);
            out.append(",\"internalConnections\":");
            appendInternalConnections(out, physical);
            out.append(",\"terminals\":[");
            Vector<String> padIds = component.getPadIds();
            Vector<String> terminalIds = physical.getTerminalIds();
            check(padIds.size() == terminalIds.size() &&
                    terminalIds.size() == geometry.getTerminals().size(),
                "terminal declaration count " + id);
            for (int terminalIndex = 0; terminalIndex < padIds.size(); terminalIndex++) {
                if (terminalIndex > 0) out.append(',');
                String padId = padIds.get(terminalIndex);
                BoardPad logical = board.getPad(padId);
                PcbPadPlacement pad = layout.getPad(padId);
                PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(terminalIndex);
                check(logical != null && pad != null && terminal != null &&
                        terminal.getTerminalId().equals(logical.getTerminalId()) &&
                        terminal.getTerminalId().equals(terminalIds.get(terminalIndex)),
                    "terminal declaration order " + id + "." + terminalIndex);
                out.append('{');
                out.append("\"index\":").append(terminalIndex);
                field(out, "terminalId", logical.getTerminalId());
                field(out, "padId", padId);
                field(out, "net", logical.getNetId());
                field(out, "domain", part.terminalDomain(logical.getTerminalId()));
                field(out, "attachment", pad.getAttachment().name());
                field(out, "mountingSide", pad.getMountingSide().name());
                field(out, "exposure", pad.getExposure().name());
                out.append(",\"padCenter\":");
                appendPoint(out, new Point(pad.getX(), pad.getY()));
                out.append(",\"padBounds\":"); appendRect(out, pad.getPadBounds());
                out.append(",\"probeCenter\":");
                appendPoint(out, placed.getBoardPadProbeCenter(terminalIndex));
                out.append(",\"probeBounds\":"); appendRect(out, pad.getProbeBounds());
                out.append(",\"escape\":{");
                out.append("\"dx\":").append(pad.getEscapeDx());
                out.append(",\"dy\":").append(pad.getEscapeDy());
                out.append(",\"length\":").append(pad.getEscapeLength());
                out.append('}');
                out.append(",\"connectedLead\":");
                appendLead(out, placement, terminalIndex, false);
                out.append(",\"liftedLead\":");
                appendLead(out, placement, terminalIndex, true);
                out.append('}');
            }
            out.append("]}");
        }
        out.append(']');
    }

    private static void appendLead(StringBuilder out, PcbComponentPlacement placement,
            int index, boolean lifted) {
        out.append('{');
        out.append("\"endPoint\":");
        appendPoint(out, placement.getLeadEndPoint(index, lifted));
        out.append(",\"bodyPoint\":");
        appendPoint(out, placement.getLeadBodyPoint(index, lifted));
        out.append(",\"bounds\":");
        appendRect(out, placement.getLeadBounds(index, lifted));
        out.append(",\"probeCenter\":");
        appendPoint(out, placement.getComponentLeadProbeCenter(index, lifted));
        out.append(",\"probeBounds\":");
        appendRect(out, placement.getComponentLeadProbeBounds(index, lifted));
        out.append('}');
    }

    private static void appendInternalConnections(StringBuilder out, PhysicalPackage physical) {
        out.append('[');
        Vector<String> terminals = physical.getTerminalIds();
        boolean first = true;
        for (int i = 0; i < terminals.size(); i++) {
            for (int j = i + 1; j < terminals.size(); j++) {
                if (!first) out.append(',');
                first = false;
                out.append('{');
                field(out, "first", terminals.get(i));
                field(out, "second", terminals.get(j));
                out.append(",\"connected\":")
                    .append(Boolean.toString(physical.isInternallyConnected(
                        terminals.get(i), terminals.get(j))));
                out.append('}');
            }
        }
        out.append(']');
    }

    private static void appendPlacementDomains(StringBuilder out,
            PcbPlacementConstraints constraints, PcbComponentPlacement placement) {
        TreeMap<String,Rectangle> domains = constraints.domainCourtyards(placement);
        out.append('[');
        boolean first = true;
        for (Map.Entry<String,Rectangle> entry : domains.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('{');
            field(out, "domain", entry.getKey());
            out.append(",\"rect\":"); appendRect(out, entry.getValue());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendIsolationBody(StringBuilder out,
            PhysicalPackageGeometry geometry, PhysicalPackageGeometry.Placement placed) {
        PhysicalPackageGeometry.IsolationBody body = geometry.getIsolationBody();
        if (body == null) {
            out.append("null");
            return;
        }
        out.append('{');
        field(out, "canonical", body.canonical());
        out.append(",\"firstCourtyard\":");
        appendRect(out, placed.getIsolationFirstCourtyard());
        out.append(",\"secondCourtyard\":");
        appendRect(out, placed.getIsolationSecondCourtyard());
        out.append(",\"bodySpan\":");
        appendRect(out, placed.getIsolationBodySpan());
        out.append('}');
    }

    private static void appendTraces(StringBuilder out, TroubleshootBoard board,
            PcbBoardLayout layout, PcbPlacementConstraints constraints) {
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        Collections.sort(traces, new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry first, PcbTraceGeometry second) {
                int result = String.valueOf(first.getSourceId()).compareTo(
                    String.valueOf(second.getSourceId()));
                if (result != 0) return result;
                result = String.valueOf(first.getNetId()).compareTo(
                    String.valueOf(second.getNetId()));
                if (result != 0) return result;
                result = first.getLayer().compareTo(second.getLayer());
                if (result != 0) return result;
                return String.valueOf(first.getStartPadId()).compareTo(
                    String.valueOf(second.getStartPadId()));
            }
        });
        int maximumWidth = constraints.getTraceWidthProfile().maximumWidth();
        out.append('[');
        for (int index = 0; index < traces.size(); index++) {
            if (index > 0) out.append(',');
            PcbTraceGeometry trace = traces.get(index);
            check(board.getNet(trace.getNetId()) != null,
                "routed trace net exists " + trace.getNetId());
            out.append('{');
            nullableField(out, "sourceId", trace.getSourceId());
            field(out, "net", trace.getNetId());
            nullableField(out, "startPad", trace.getStartPadId());
            nullableField(out, "endPad", trace.getEndPadId());
            field(out, "layer", trace.getLayer().name());
            field(out, "exposure", trace.getExposure().name());
            out.append(",\"width\":").append(trace.getWidth());
            out.append(",\"whitespaceGuard\":").append(
                PcbTraceRules.clearance(trace.getWidth(), maximumWidth));
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            check(x.length == y.length && x.length >= 2,
                "routed trace points are paired");
            out.append(",\"points\":[");
            for (int point = 0; point < x.length; point++) {
                if (point > 0) out.append(',');
                PcbCoordinateSystem.requireBoardCoordinate(x[point]);
                PcbCoordinateSystem.requireBoardCoordinate(y[point]);
                out.append('[').append(x[point]).append(',').append(y[point]).append(']');
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
            nullableField(out, "net", hole.netId);
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

    private static void appendSilk(StringBuilder out, PcbBoardLayout layout) {
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
            out.append(",\"bold\":").append(Boolean.toString(label.isBold()));
            nullableField(out, "targetPad", label.getTargetPadId());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendConstraints(StringBuilder out, TroubleshootBoard board,
            PcbPlacementConstraints constraints) {
        out.append('{');
        field(out, "physicalPolicy", constraints.getPhysicalPolicyIdentity());
        out.append(",\"physicalPolicyVersion\":")
            .append(constraints.getPhysicalPolicyVersion());
        out.append(",\"placementDensityVersion\":")
            .append(constraints.placementDensityVersion);
        out.append(",\"placementPackingVersion\":")
            .append(constraints.getPlacementPackingVersion());
        field(out, "routingLayer", constraints.routingLayer.name());
        out.append(",\"requiresTwoLayerRouting\":")
            .append(Boolean.toString(constraints.requiresTwoLayerRouting));
        field(out, "traceWidthProfile", constraints.getTraceWidthProfile().canonical());
        out.append(",\"parts\":[");
        Vector<PcbPlacementConstraints.Part> parts = constraints.getParts();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) out.append(',');
            PcbPlacementConstraints.Part part = parts.get(index);
            out.append('{');
            field(out, "componentId", part.componentId);
            field(out, "regionId", part.regionId);
            field(out, "regionLabel", part.regionLabel);
            field(out, "domainId", part.domainId);
            field(out, "anchor", part.anchor.name());
            out.append(",\"accessMargin\":").append(part.accessMargin);
            out.append(",\"terminalDomains\":[");
            Vector<PcbPlacementConstraints.TerminalDomain> declarations =
                part.getTerminalDomains();
            for (int declaration = 0; declaration < declarations.size(); declaration++) {
                if (declaration > 0) out.append(',');
                PcbPlacementConstraints.TerminalDomain value = declarations.get(declaration);
                out.append('{');
                field(out, "terminalId", value.terminalId);
                field(out, "domain", value.domainId);
                out.append('}');
            }
            out.append("]}");
        }
        out.append(']');
        out.append(",\"barriers\":[");
        Vector<PcbPlacementConstraints.Barrier> barriers = constraints.getBarriers();
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

    private static TreeMap<String,Rectangle> collectDomainUnions(
            TroubleshootBoard board, PcbBoardLayout layout) {
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        TreeMap<String,Rectangle> unions = new TreeMap<String,Rectangle>();
        for (String componentId : sorted(board.getComponentIds())) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            if (placement == null)
                throw new IllegalArgumentException("Missing domain package: " + componentId);
            TreeMap<String,Rectangle> domains = constraints.domainCourtyards(placement);
            for (Map.Entry<String,Rectangle> entry : domains.entrySet()) {
                Rectangle prior = unions.get(entry.getKey());
                unions.put(entry.getKey(), prior == null ? new Rectangle(entry.getValue()) :
                    union(prior, entry.getValue()));
            }
        }
        if (unions.isEmpty()) throw new IllegalArgumentException("No domain courtyards");
        return unions;
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        long left = Math.min((long) first.x, second.x);
        long top = Math.min((long) first.y, second.y);
        long right = Math.max((long) first.x + first.width,
            (long) second.x + second.width);
        long bottom = Math.max((long) first.y + first.height,
            (long) second.y + second.height);
        return new Rectangle(PcbCoordinateSystem.requireBoardCoordinate(left),
            PcbCoordinateSystem.requireBoardCoordinate(top),
            PcbCoordinateSystem.checkedInt(right - left),
            PcbCoordinateSystem.checkedInt(bottom - top));
    }

    private static void appendDomainUnions(StringBuilder out,
            TreeMap<String,Rectangle> unions) {
        out.append('[');
        boolean first = true;
        for (Map.Entry<String,Rectangle> entry : unions.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append('{');
            field(out, "domain", entry.getKey());
            out.append(",\"rect\":"); appendRect(out, entry.getValue());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendNets(StringBuilder out, TroubleshootBoard board) {
        out.append('[');
        Vector<String> netIds = sorted(board.getNetIds());
        for (int index = 0; index < netIds.size(); index++) {
            if (index > 0) out.append(',');
            String netId = netIds.get(index);
            BoardNet net = board.getNet(netId);
            Vector<String> pads = sorted(net.getPadIds());
            out.append('{');
            field(out, "netId", netId);
            field(out, "routingRole", net.getRoutingRole().name());
            out.append(",\"padIds\":").append(stringArray(pads));
            out.append('}');
        }
        out.append(']');
    }

    private static void appendPowerInputs(StringBuilder out, TroubleshootBoard board) {
        out.append('[');
        Vector<String> ids = sorted(board.getPowerInputIds());
        for (int index = 0; index < ids.size(); index++) {
            if (index > 0) out.append(',');
            ExternalBoardPowerInput input = board.getPowerInput(ids.get(index));
            out.append('{');
            field(out, "id", input.getId());
            field(out, "positivePadId", input.getPositivePadId());
            field(out, "returnPadId", input.getReturnPadId());
            field(out, "positiveNetId", input.getPositiveNetId());
            field(out, "returnNetId", input.getReturnNetId());
            out.append('}');
        }
        out.append(']');
    }

    private static void appendPackingProbe(StringBuilder out,
            PcbPackingProbeReceipt packing) {
        if (packing == null) {
            out.append("null");
            return;
        }
        out.append('{');
        out.append("\"attempts\":").append(packing.attempts);
        out.append(",\"orderings\":").append(packing.orderings);
        out.append(",\"expansions\":").append(packing.expansions);
        out.append(",\"millis\":").append(packing.millis);
        field(out, "routePolicy", packing.routePolicy);
        field(out, "disposition", packing.disposition);
        field(out, "baselineGeometry", packing.baselineGeometry);
        field(out, "selectedGeometry", packing.selectedGeometry);
        field(out, "canonical", packing.toCanonical());
        out.append(",\"metricEvidence\":{");
        PcbPackingProbeReceipt.MetricEvidence evidence = packing.metricEvidence;
        field(out, "state", evidence.state.name());
        nullableField(out, "unavailableReason", evidence.unavailableReason);
        out.append(",\"baseline\":"); appendObservedMetrics(out, evidence.baseline);
        out.append(",\"candidate\":"); appendObservedMetrics(out, evidence.candidate);
        out.append("}}");
    }

    private static void appendObservedMetrics(StringBuilder out, PcbPackingMetrics metrics) {
        if (metrics == null) { out.append("null"); return; }
        out.append('{');
        out.append("\"area\":").append(metrics.area);
        out.append(",\"copper\":").append(metrics.copper);
        out.append(",\"vias\":").append(metrics.vias);
        out.append(",\"protectedVoid\":").append(metrics.protectedVoid);
        out.append('}');
    }

    private static void appendPackingMetrics(StringBuilder out, TroubleshootBoard board,
            PcbBoardLayout layout) {
        try {
            PcbPackingMetrics metrics = PcbPackingMetrics.measure(board, layout);
            out.append('{');
            out.append("\"available\":true");
            out.append(",\"area\":").append(metrics.area);
            out.append(",\"copper\":").append(metrics.copper);
            out.append(",\"vias\":").append(metrics.vias);
            out.append(",\"protectedVoid\":").append(metrics.protectedVoid);
            out.append('}');
        } catch (PcbPackingMetrics.Unavailable unavailable) {
            out.append('{');
            out.append("\"available\":false");
            field(out, "reason", unavailable.getMessage());
            out.append('}');
        }
    }

    private static String logicalBoardCanonical(TroubleshootBoard board) {
        StringBuilder out = new StringBuilder();
        token(out, board.getId());
        for (String componentId : sorted(board.getComponentIds())) {
            BoardComponent component = board.getComponent(componentId);
            token(out, "component");
            token(out, component.getId());
            token(out, component.getType());
            token(out, component.getDisplayName());
            PhysicalPackage physical = component.getPhysicalPackage();
            token(out, physical.getId());
            for (String terminal : physical.getTerminalIds()) token(out, terminal);
            for (String padId : component.getPadIds()) {
                BoardPad pad = board.getPad(padId);
                token(out, "pad");
                token(out, pad.getId());
                token(out, pad.getComponentId());
                token(out, pad.getTerminalId());
                token(out, pad.getNetId());
            }
        }
        for (String netId : sorted(board.getNetIds())) {
            BoardNet net = board.getNet(netId);
            token(out, "net");
            token(out, netId);
            token(out, net.getRoutingRole().name());
            for (String padId : sorted(net.getPadIds())) token(out, padId);
        }
        for (String powerId : sorted(board.getPowerInputIds())) {
            ExternalBoardPowerInput input = board.getPowerInput(powerId);
            token(out, "power");
            token(out, input.getId());
            token(out, input.getPositivePadId());
            token(out, input.getReturnPadId());
            token(out, input.getPositiveNetId());
            token(out, input.getReturnNetId());
        }
        return out.toString();
    }

    private static void token(StringBuilder out, String value) {
        if (value == null) throw new IllegalArgumentException("Null logical board identity token");
        out.append(value.length()).append(':').append(value).append('|');
    }

    private static Vector<String> sorted(Vector<String> values) {
        Vector<String> result = new Vector<String>(values);
        Collections.sort(result);
        return result;
    }

    private static String stringArray(Vector<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            appendString(out, values.get(index));
        }
        return out.append(']').toString();
    }

    private static String integerArray(Vector<Integer> values) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            out.append(values.get(index).intValue());
        }
        return out.append(']').toString();
    }

    private static void appendRect(StringBuilder out, Rectangle rect) {
        if (rect == null) throw new IllegalArgumentException("Missing geometry rectangle");
        PcbCoordinateSystem.requireBoardRectangle(rect);
        out.append('[').append(rect.x).append(',').append(rect.y).append(',')
            .append(rect.width).append(',').append(rect.height).append(']');
    }

    private static void appendPoint(StringBuilder out, Point point) {
        PcbCoordinateSystem.requireBoardPoint(point);
        out.append('[').append(point.x).append(',').append(point.y).append(']');
    }

    private static void field(StringBuilder out, String key, String value) {
        comma(out);
        appendString(out, key);
        out.append(':');
        appendString(out, value);
    }

    private static void nullableField(StringBuilder out, String key, String value) {
        comma(out);
        appendString(out, key);
        out.append(':');
        if (value == null) out.append("null");
        else appendString(out, value);
    }

    private static void comma(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) != '{' &&
                out.charAt(out.length() - 1) != '[')
            out.append(',');
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

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
