package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/** Fixed actual RB56 route measurements; reports geometry without a density pass limit. */
public final class PcbCompactnessContractTest {
    private static int assertions;

    private static final RootCase[] ROOTS = new RootCase[] {
        new RootCase(-1017L, 40, 96),
        new RootCase(-1004L, 44, 104),
        new RootCase(-1022L, 60, 143)
    };

    private static final class RootCase {
        final long seed;
        final int packages, pads;
        RootCase(long seed, int packages, int pads) {
            this.seed = seed; this.packages = packages; this.pads = pads;
        }
    }

    /** Hash a saved layout-16 geometry body after the global layout epoch became 17. */
    static String legacyGeometrySha256(PcbBoardLayout layout) {
        if (layout == null) throw new IllegalArgumentException("Missing PCB layout");
        if (SeededPcbLayoutGenerator.CURRENT_VERSION != 17)
            throw new AssertionError("Legacy geometry oracle requires layout version 17");
        String fingerprint = layout.geometryFingerprint();
        String currentPrefix = "layout=17|";
        if (!fingerprint.startsWith(currentPrefix))
            throw new AssertionError("Expected actual layout=17 geometry fingerprint");
        emitReferenceOracle(layout, fingerprint);
        return PlayerSessionFingerprint.of(
            "layout=16|" + fingerprint.substring(currentPrefix.length()));
    }

    /** Draft-only export of saved layout-17 reference-label positions. */
    private static void emitReferenceOracle(PcbBoardLayout layout, String fingerprint) {
        String currentPrefix = "layout=17|";
        if (layout == null || fingerprint == null || !fingerprint.startsWith(currentPrefix))
            throw new AssertionError("Reference oracle requires an actual layout=17 fingerprint");
        if (!fingerprint.equals(layout.geometryFingerprint()))
            throw new AssertionError("Layout changed before reference-oracle observation");

        String normalized = "layout=<LAYOUT_EPOCH>|" +
            fingerprint.substring(currentPrefix.length());
        String legacyLayout16Sha256 = PlayerSessionFingerprint.of(
            "layout=16|" + fingerprint.substring(currentPrefix.length()));
        Vector<PcbSilkscreenLabel> labels = layout.getSilkscreenLabels();
        Collections.sort(labels, new java.util.Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel first, PcbSilkscreenLabel second) {
                return first.getId().compareTo(second.getId());
            }
        });
        TreeSet<String> labelIds = new TreeSet<String>();
        TreeSet<String> componentLabelIds = new TreeSet<String>();
        TreeSet<String> expectedComponentLabelIds = new TreeSet<String>();
        for (PcbComponentPlacement component : layout.getComponents())
            expectedComponentLabelIds.add("component:" + component.getComponentId());

        StringBuilder labelsJson = new StringBuilder("[");
        StringBuilder referenceRecordsJson = new StringBuilder("[");
        int referenceCount = 0;
        for (int index = 0; index < labels.size(); index++) {
            PcbSilkscreenLabel label = labels.get(index);
            String id = label.getId();
            String text = label.getText();
            if (!labelIds.add(id))
                throw new AssertionError("Duplicate reference-oracle label ID: " + id);
            Rectangle bounds = label.getBounds();
            String record = "L:" + id + "@" + text + ":" + bounds.x + "," + bounds.y + "," +
                bounds.width + "," + bounds.height + ";";
            if (occurrences(fingerprint, record) != 1)
                throw new AssertionError("Missing or duplicate uniquely framed label record: " + id);

            String normalizedRecord = record;
            if (id.startsWith("component:")) {
                if (!componentLabelIds.add(id))
                    throw new AssertionError("Duplicate component reference label: " + id);
                int offset = normalized.indexOf(record);
                if (offset < 0 || occurrences(normalized, record) != 1)
                    throw new AssertionError("Reference label record changed before normalization: " + id);
                normalizedRecord = "L:" + id + "@" + text + ":<REF_X>,<REF_Y>," +
                    bounds.width + "," + bounds.height + ";";
                normalized = normalized.substring(0, offset) + normalizedRecord +
                    normalized.substring(offset + record.length());
                if (referenceCount++ > 0) referenceRecordsJson.append(',');
                referenceRecordsJson.append("{\"id\":").append(quote(id))
                    .append(",\"record\":").append(quote(record))
                    .append(",\"normalizedRecord\":").append(quote(normalizedRecord)).append('}');
            }

            if (index > 0) labelsJson.append(',');
            labelsJson.append("{\"id\":").append(quote(id))
                .append(",\"text\":").append(quote(text))
                .append(",\"bounds\":[").append(bounds.x).append(',').append(bounds.y)
                .append(',').append(bounds.width).append(',').append(bounds.height).append(']')
                .append(",\"fontSize\":").append(label.getFontSize())
                .append(",\"bold\":").append(label.isBold())
                .append(",\"targetPadId\":").append(quoteOrNull(label.getTargetPadId()))
                .append('}');
        }
        labelsJson.append(']');
        referenceRecordsJson.append(']');
        if (!componentLabelIds.containsAll(expectedComponentLabelIds))
            throw new AssertionError("Reference-oracle inventory is missing a placed component label");

        String after = layout.geometryFingerprint();
        if (!fingerprint.equals(after))
            throw new AssertionError("Reference-oracle observation changed layout geometry");

        System.out.println("PCB_REFERENCE_ORACLE {\"schema\":\"REFERENCE_LEAD_CLEARANCE_R1_DRAFT\","
            + "\"layoutVersion\":17,\"layoutUnchanged\":true,"
            + "\"graphSnapshot\":\"UNAVAILABLE_LAYOUT_ONLY_HELPER\","
            + "\"layoutFingerprint\":" + quote(fingerprint)
            + ",\"layoutSha256\":" + quote(PlayerSessionFingerprint.of(fingerprint))
            + ",\"legacyLayout16Sha256\":" + quote(legacyLayout16Sha256)
            + ",\"normalizedLayoutFingerprint\":" + quote(normalized)
            + ",\"normalizedLayoutSha256\":" + quote(PlayerSessionFingerprint.of(normalized))
            + ",\"normalization\":" + quote("only leading layout=17| becomes layout=<LAYOUT_EPOCH>|; only x/y in exact L:component:*@text:x,y,width,height; records become <REF_X>,<REF_Y>; all other bytes retained")
            + ",\"referenceLabelCount\":" + referenceCount
            + ",\"referenceLabelRecords\":" + referenceRecordsJson
            + ",\"labelCount\":" + labels.size()
            + ",\"labels\":" + labelsJson + "}");
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static String quoteOrNull(String value) {
        return value == null ? "null" : quote(value);
    }

    public static void main(String[] args) {
        String mode = parseMode(args);
        System.out.println("PCB_COMPACTNESS_CRITERIA {\"mode\":" + quote(mode) +
            ",\"rootSeeds\":[\"-1017\",\"-1004\",\"-1022\"]," +
            "\"packages\":[40,44,60],\"pads\":[96,104,143]," +
            "\"routingPolicy\":\"MEDIUM_BOARD@1\",\"admission\":\"RB56_BOARD_NORMAL@1\"," +
            "\"densityThreshold\":null,\"scope\":\"FIXED_ACTUAL_RB56_GEOMETRY_REPORT_ONLY\"}");
        if ("report".equals(mode)) verifyLegacyGeometry();
        for (RootCase root : ROOTS) runCase(root, mode);
        System.out.println("PASS: actual RB56 geometry report " + assertions +
            " assertions cases=" + ROOTS.length + " mode=" + mode +
            " scope=NO_DENSITY_ACCEPTANCE_THRESHOLD");
    }

    /** Frozen before-source geometry: legacy saved interpretation must remain exact. */
    private static void verifyLegacyGeometry() {
        long[] seeds = new long[] {-1017L, -1004L, -1022L};
        String[] expected = new String[] {"72ad2dc88c440746a5166e6dc488a6c31395e8c4bbbae94adb8c881a7bf442d0","075f0e7c369ef7753815c3d6f97cceaf8f7fe0feb6371afcfaaf15d6ffa22ee6","84724d29497f62e72e67648ed461263f6c9f8c1d875f0cabefd335458b9877de"};
        for (int i=0; i<seeds.length; i++) {
            Rb56Plan plan=Rb56Plan.resolve(seeds[i],Rb56Placement.LEGACY_VERSION);
            MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
                .generateWithPolicyResult(plan.board(),plan.layoutSeed,plan.routingSeed);
            require(result.accepted(),"legacy fixed saved-layout route remains accepted");
            require(expected[i].equals(legacyGeometrySha256(result.getLayout())),
                "legacy geometry including exact pads/copper/labels remains byte-exact for "+seeds[i]);
            if (seeds[i] == -1022L) verifyMatchedViaCompaction(plan.board(), result.getLayout());
            boolean refused=false;
            try { Rb56Placement.create(plan,Rb56Placement.CURRENT_VERSION); }
            catch (IllegalArgumentException expectedMismatch) { refused=true; }
            require(refused,"plan and constraints cannot carry different placement interpretations");
        }
        System.out.println("PASS: exact legacy RB56 geometry retained for three fixed saved-board interpretations");
    }

    /** One already-routed golden board; no new routing search or synthetic copper. */
    private static void verifyMatchedViaCompaction(TroubleshootBoard board, PcbBoardLayout original) {
        Vector<PcbFootprint> footprints=new Vector<PcbFootprint>();
        for(PcbComponentPlacement component:original.getComponents()) {
            Vector<PcbPadPlacement> pads=new Vector<PcbPadPlacement>();
            for(String terminal:component.getPhysicalGeometry().getTerminalIds())
                pads.add(original.getPad(component.getComponentId()+"."+terminal));
            footprints.add(new PcbFootprint(component,pads));
        }
        Rectangle outline=original.getBoardOutline();
        PcbPlacementPlanner.Plan input=new PcbPlacementPlanner.Plan(outline,footprints,
            original.getRegions(),0,0,0,0);
        PcbBoardLayout routed=input.materialize();
        for(PcbTraceGeometry trace:original.getTraces())routed.addTrace(trace);
        for(PcbBoardHole hole:original.getHoles())routed.addHole(hole);
        require(!routed.getHoles().isEmpty(),"golden 60-part route has real plated layer connections");
        String before=routed.geometryFingerprint();
        PcbPlacementCompactor.Routed compact=PcbPlacementCompactor.compactRouted(
            board,board.getPlacementConstraints(),input,routed);
        require((long)compact.plan.outline.width*compact.plan.outline.height <
            (long)outline.width*outline.height,"matched real via route exercises a changed compaction axis");
        require(before.equals(routed.geometryFingerprint()),"matched routed input remains untouched");
        require(compact.layout.getHoles().size()==original.getHoles().size(),
            "every golden layer connection survives compaction");
        for(PcbBoardHole prior:original.getHoles()) {
            PcbBoardHole found=null;
            for(PcbBoardHole hole:compact.layout.getHoles())if(prior.id.equals(hole.id))found=hole;
            require(found!=null && prior.kind==found.kind && prior.netId.equals(found.netId) &&
                prior.drillRadius==found.drillRadius && prior.landRadius==found.landRadius &&
                prior.exposure==found.exposure,"golden via identity, net, plating and size remain exact");
        }
        compact.layout.validateRoutingGeometry(board);
        new PcbTwoLayerRules(board,compact.layout).validate(compact.layout);
    }

    private static String parseMode(String[] args) {
        if (args == null || args.length == 0) return "report";
        if (args.length != 1 || !("baseline".equals(args[0]) || "report".equals(args[0])))
            throw new IllegalArgumentException("Expected no argument or one mode: baseline|report");
        return args[0];
    }

    private static void runCase(RootCase root, String mode) {
        Rb56Plan plan = Rb56Plan.resolve(root.seed, "baseline".equals(mode) ?
            Rb56Placement.LEGACY_VERSION : Rb56Placement.COMPACT_VERSION);
        require(plan.physicalPackageCount() == root.packages,
            "resolved package count is the independent fixed expectation for seed " + root.seed);
        TroubleshootBoard board = plan.board();
        int declaredPads = 0;
        TreeMap<String, Integer> plannedPackages = new TreeMap<String, Integer>();
        TreeMap<String, Integer> plannedPadsByPackage = new TreeMap<String, Integer>();
        TreeSet<String> declaredPadIds = new TreeSet<String>();
        for (Rb56Plan.Part part : plan.parts()) {
            String packageId = part.physicalPackage.getId();
            int terminals = part.terminalIds().length;
            declaredPads += terminals;
            increment(plannedPackages, packageId, 1);
            increment(plannedPadsByPackage, packageId, terminals);
            for (String terminal : part.terminalIds())
                require(declaredPadIds.add(part.id + "." + terminal),
                    "resolved plan has unique terminal identity " + part.id + "." + terminal);
        }
        require(declaredPads == root.pads,
            "resolved plan terminal declarations match the independent fixed pad census for seed " + root.seed);
        require(board.getComponentIds().size() == root.packages,
            "board component declarations retain the root package count");
        require(board.getPadIds().size() == declaredPads &&
                new TreeSet<String>(board.getPadIds()).equals(declaredPadIds),
            "board pad census matches the independent resolved-plan terminal census");

        MediumBoardPhysicalPolicy.Result result = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
        require(result != null && result.accepted() && result.getFailure() == null,
            "fixed seed has an actual accepted routed candidate: " +
                (result == null ? "null" : result.toCanonical()));
        MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
        require(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                statistics.selectedRoutePolicy),
            "the reported route is the actual selected P07 two-layer candidate");

        PcbBoardLayout layout = result.getLayout();
        if ("report".equals(mode)) {
            if(root.seed==-1017L) require("84eff3d9cb6e087578873a79fc9e8db72271b6fa653c309a79cfcedbee9d9a25".equals(legacyGeometrySha256(layout)),"saved compact2 geometry remains exact for -1017");
            if(root.seed==-1004L) require("a05b554248d83dd2b8c1dc9e8fb8e7e05a70081323e6e0383a6e673f5276de61".equals(legacyGeometrySha256(layout)),"saved compact2 geometry remains exact for -1004");
            if(root.seed==-1022L) require("eeadd722d90c3f407dd5889caaff11d7fbe0e02d81f3dccf131322bcd6ee4f16".equals(legacyGeometrySha256(layout)),"saved compact2 geometry remains exact for -1022");
        }
        layout.validateGeometry(board);
        new PcbTwoLayerRules(board, layout).validate(layout);
        PcbTraceClearance.Statistics clearance = PcbTraceClearance.validate(layout.getTraces());
        GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRb56Route(
            board, result, plan.layoutSeed, plan.routingSeed);
        require("RB56_BOARD_NORMAL@1".equals(admission.identity()),
            "actual routed result acquires only the bounded RB56 geometry admission");
        admission.requireConstruction(board, layout);

        require(layout.getComponents().size() == root.packages &&
                layout.getPads().size() == declaredPads,
            "routed geometry retains the resolved plan package and terminal totals");
        TreeSet<String> placedComponentIds = new TreeSet<String>();
        TreeMap<String, Integer> placedPackages = new TreeMap<String, Integer>();
        for (PcbComponentPlacement component : layout.getComponents()) {
            require(placedComponentIds.add(component.getComponentId()),
                "routed geometry has unique component identity " + component.getComponentId());
            increment(placedPackages, component.getPhysicalPackage().getId(), 1);
        }
        require(placedComponentIds.equals(new TreeSet<String>(board.getComponentIds())),
            "routed geometry preserves every declared component identity");
        require(placedPackages.equals(plannedPackages),
            "routed geometry preserves the resolved physical-package census");
        TreeSet<String> placedPadIds = new TreeSet<String>();
        for (PcbPadPlacement pad : layout.getPads()) placedPadIds.add(pad.getPadId());
        require(placedPadIds.equals(declaredPadIds),
            "routed geometry preserves every declared terminal/pad identity");

        PcbPlacementPlanner.Plan rawPlannerPlan = new PcbPlacementPlanner(
            StandardPcbFootprintProviders.createRegistry()).plan(board,
                board.getPlacementConstraints(), plan.layoutSeed,
                statistics.selectedPlacementAttempt);
        require(rawPlannerPlan.candidate == statistics.selectedPlacementAttempt &&
                rawPlannerPlan.footprints.size() == root.packages,
            "raw planner demand is reconstructed from the accepted placement attempt");

        ArrayList<Integer> nearestGaps = nearestAccessEnvelopeGaps(board, layout);
        TraceMetrics traces = traceMetrics(layout.getTraces());
        Rectangle outline = layout.getBoardOutline();
        long outlineArea = (long) outline.width * outline.height;
        long finalCourtyardArea = layout.getRoutingCourtyardArea();
        require(finalCourtyardArea == rawPlannerPlan.courtyardArea,
            "translation/cropping preserves the physical-package courtyard total");
        Rectangle rawPlannerOutline = rawPlannerPlan.outline;
        StringBuilder out = new StringBuilder();
        out.append("PCB_COMPACTNESS_CASE {");
        field(out, "mode", quote(mode));
        field(out, "placementInterpretationVersion", plan.placementInterpretationVersion);
        field(out, "rootSeed", quote(Long.toString(plan.seed)));
        field(out, "placementSeed", quote(Long.toString(plan.layoutSeed)));
        field(out, "routingSeed", quote(Long.toString(plan.routingSeed)));
        field(out, "topology", quote(plan.topology()));
        field(out, "packagesExpected", Integer.toString(root.packages));
        field(out, "packagesDeclared", Integer.toString(plan.physicalPackageCount()));
        field(out, "packagesPlaced", Integer.toString(layout.getComponents().size()));
        field(out, "padsExpected", Integer.toString(root.pads));
        field(out, "padsDeclaredFromPlan", Integer.toString(declaredPads));
        field(out, "padsPlaced", Integer.toString(layout.getPads().size()));
        field(out, "packageCensus", integerMap(plannedPackages));
        field(out, "padsByPackageCensus", integerMap(plannedPadsByPackage));
        field(out, "geometrySha256", quote(PlayerSessionFingerprint.of(layout.geometryFingerprint())));
        field(out, "boardWidth", Integer.toString(outline.width));
        field(out, "boardHeight", Integer.toString(outline.height));
        field(out, "boardArea", Long.toString(outlineArea));
        field(out, "occupiedContentUtilization", number(layout.getBoardUtilization()));
        field(out, "rawPlannerCandidateOutlineWidth", Integer.toString(rawPlannerOutline.width));
        field(out, "rawPlannerCandidateOutlineHeight", Integer.toString(rawPlannerOutline.height));
        field(out, "selectedPlacementAttempt", Integer.toString(statistics.selectedPlacementAttempt));
        field(out, "resolvedPlannerDemandArea", Long.toString(rawPlannerPlan.demandArea));
        field(out, "resolvedDemandUtilizationOnFinalBoard", number(rawPlannerPlan.demandArea / (double) outlineArea));
        field(out, "resolvedPlannerCourtyardArea", Long.toString(rawPlannerPlan.courtyardArea));
        field(out, "resolvedPlannerCourtyardUtilizationOnFinalBoard", number(rawPlannerPlan.courtyardArea / (double) outlineArea));
        field(out, "finalCourtyardArea", Long.toString(finalCourtyardArea));
        field(out, "finalCourtyardUtilization", number(finalCourtyardArea / (double) outlineArea));
        field(out, "nearestEnvelopeGapMetric", quote("per-part nearest peer access-envelope Chebyshev gap; drawing units; sorted"));
        field(out, "nearestEnvelopeGapUnits", integerArray(nearestGaps));
        field(out, "nearestEnvelopeGapMin", integerAt(nearestGaps, 0));
        field(out, "nearestEnvelopeGapP50", integerAt(nearestGaps, percentile(nearestGaps.size(), 0.50)));
        field(out, "nearestEnvelopeGapP90", integerAt(nearestGaps, percentile(nearestGaps.size(), 0.90)));
        field(out, "nearestEnvelopeGapMax", integerAt(nearestGaps, nearestGaps.size() - 1));
        field(out, "nearestEnvelopeGapMean", number(mean(nearestGaps)));
        field(out, "routePolicy", quote(statistics.selectedRoutePolicy));
        field(out, "routeAttempts", Integer.toString(statistics.routeAttempts));
        field(out, "P05AttemptsAndSuccesses", quote(statistics.oneFaceAttempts + "/" + statistics.oneFaceSuccesses));
        field(out, "P07AttemptsAndSuccesses", quote(statistics.twoLayerAttempts + "/" + statistics.twoLayerSuccesses));
        field(out, "routingExpansions", Integer.toString(statistics.routingExpansions));
        field(out, "tracePolylineCount", Integer.toString(traces.count));
        field(out, "routedNetCount", Integer.toString(traces.nets));
        field(out, "traceSegmentCount", Integer.toString(traces.segments));
        field(out, "tracesByLayer", integerMap(traces.byLayer));
        field(out, "copperClearanceRuleMinimumCenterline", Integer.toString(PcbTraceRules.MIN_CENTERLINE_CLEARANCE));
        field(out, "copperClearanceValidated", "true");
        field(out, "copperClearanceMeasurementScope", quote("all different-net same-layer trace-segment pairs"));
        field(out, "copperClearanceMinimumMeasuredCenterline", number(traces.minimumCenterline));
        field(out, "copperClearanceMinimumSquared", Long.toString(traces.minimumSquared));
        field(out, "copperClearanceCloseSegmentPairsChecked", Long.toString(clearance.exactPairs));
        field(out, "copperClearancePotentialSegmentPairs", Long.toString(clearance.potentialPairs));
        field(out, "admission", quote(admission.identity()));
        out.append("\"scopeNote\":\"Actual fixed-seed placement/routing geometry only; no density threshold, manufacturing rating, electrical qualification, or player claim.\"}");
        System.out.println(out.toString());
    }

    private static ArrayList<Integer> nearestAccessEnvelopeGaps(TroubleshootBoard board,
            PcbBoardLayout layout) {
        Vector<PcbComponentPlacement> components = layout.getComponents();
        ArrayList<Rectangle> envelopes = new ArrayList<Rectangle>();
        for (PcbComponentPlacement component : components) {
            BoardComponent declaration = board.getComponent(component.getComponentId());
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            for (String padId : declaration.getPadIds()) {
                PcbPadPlacement pad = layout.getPad(padId);
                if (pad == null) throw new AssertionError("Missing routed access-envelope pad " + padId);
                pads.add(pad);
            }
            PcbFootprint footprint = new PcbFootprint(component, pads);
            PcbPlacementConstraints.Part demand = board.getPlacementConstraints().get(
                component.getComponentId());
            envelopes.add(PcbPlacementPlanner.envelope(footprint, demand.accessMargin));
        }
        ArrayList<Integer> gaps = new ArrayList<Integer>();
        for (int first = 0; first < envelopes.size(); first++) {
            int nearest = Integer.MAX_VALUE;
            for (int second = 0; second < envelopes.size(); second++) {
                if (first == second) continue;
                nearest = Math.min(nearest, envelopeGap(envelopes.get(first), envelopes.get(second)));
            }
            if (nearest == Integer.MAX_VALUE) throw new AssertionError("Need at least two routed packages");
            gaps.add(Integer.valueOf(nearest));
        }
        Collections.sort(gaps);
        return gaps;
    }

    private static int envelopeGap(Rectangle first, Rectangle second) {
        long dx = Math.max(0L, Math.max((long) first.x - ((long) second.x + second.width),
            (long) second.x - ((long) first.x + first.width)));
        long dy = Math.max(0L, Math.max((long) first.y - ((long) second.y + second.height),
            (long) second.y - ((long) first.y + first.height)));
        return (int) Math.max(dx, dy);
    }

    private static final class TraceMetrics {
        int count, nets, segments;
        long minimumSquared = Long.MAX_VALUE;
        double minimumCenterline;
        final TreeMap<String, Integer> byLayer = new TreeMap<String, Integer>();
    }

    private static TraceMetrics traceMetrics(Vector<PcbTraceGeometry> traces) {
        TraceMetrics result = new TraceMetrics();
        TreeSet<String> nets = new TreeSet<String>();
        for (PcbTraceGeometry trace : traces) {
            result.count++;
            nets.add(trace.getNetId());
            increment(result.byLayer, trace.getLayer().name(), 1);
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            result.segments += x.length - 1;
        }
        result.nets = nets.size();
        for (int first = 0; first < traces.size(); first++) {
            PcbTraceGeometry a = traces.get(first);
            int[] ax = a.getXPoints(), ay = a.getYPoints();
            for (int second = first + 1; second < traces.size(); second++) {
                PcbTraceGeometry b = traces.get(second);
                if (a.getLayer() != b.getLayer() || a.getNetId().equals(b.getNetId())) continue;
                int[] bx = b.getXPoints(), by = b.getYPoints();
                for (int i = 1; i < ax.length; i++) for (int j = 1; j < bx.length; j++)
                    result.minimumSquared = Math.min(result.minimumSquared,
                        PcbBoardLayout.segmentDistanceSquared(ax[i - 1], ay[i - 1], ax[i], ay[i],
                            bx[j - 1], by[j - 1], bx[j], by[j]));
            }
        }
        if (result.minimumSquared == Long.MAX_VALUE)
            throw new AssertionError("Routed traces have no different-net same-layer segment pair to measure");
        if (result.minimumSquared < (long) PcbTraceRules.MIN_CENTERLINE_CLEARANCE *
                PcbTraceRules.MIN_CENTERLINE_CLEARANCE)
            throw new AssertionError("Measured routed copper clearance is below the established rule");
        result.minimumCenterline = Math.sqrt((double) result.minimumSquared);
        return result;
    }

    private static int percentile(int size, double fraction) {
        return Math.max(0, (int) Math.ceil(size * fraction) - 1);
    }

    private static int integerAt(ArrayList<Integer> values, int index) {
        return values.get(index).intValue();
    }

    private static double mean(ArrayList<Integer> values) {
        long total = 0;
        for (Integer value : values) total += value.intValue();
        return total / (double) values.size();
    }

    private static void increment(TreeMap<String, Integer> values, String key, int amount) {
        Integer previous = values.get(key);
        values.put(key, Integer.valueOf((previous == null ? 0 : previous.intValue()) + amount));
    }

    private static String integerMap(TreeMap<String, Integer> values) {
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!first) result.append(',');
            first = false;
            result.append(quote(entry.getKey())).append(':').append(entry.getValue().intValue());
        }
        return result.append('}').toString();
    }

    private static String integerArray(ArrayList<Integer> values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) result.append(',');
            result.append(values.get(index).intValue());
        }
        return result.append(']').toString();
    }

    private static void field(StringBuilder out, String name, int value) {
        field(out, name, Integer.toString(value));
    }

    private static void field(StringBuilder out, String name, String jsonValue) {
        out.append(quote(name)).append(':').append(jsonValue).append(',');
    }

    private static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException("Metrics must be finite JSON numbers");
        return Double.toString(value);
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '"') result.append('\\').append(character);
            else if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.append('"').toString();
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private PcbCompactnessContractTest() { }
}
