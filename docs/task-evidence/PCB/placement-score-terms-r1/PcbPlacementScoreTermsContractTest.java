package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Read-only decomposition of one previously selected accepted-density placement attempt. */
public final class PcbPlacementScoreTermsContractTest {
    private static final long ROOT=42L;
    private static final long LAYOUT_SEED=5526715536693200536L;
    private static final int VERSION=15,DENSITY=14,ATTEMPT=1,TRACE_CAP=60000;
    private static final String SOURCE_PLAN_SHA=
        "4280267f0929521ab4be8664f74e841dfef6bef6c8b4a9552c18c621be685f59";
    private static final String SOURCE_GRAPH_SHA=
        "840dc1be8cd3ea75d6bfbeb62b9c9e2bf2fa6b718eea2aad557b899a9cacd96a";
    private static final String SELECTED_PLAN_SHA=
        "d88e50e98d6c268d3bb3486f6c7c4f1377a4399d9ecf84dc9cfbe26b3a94b73b";
    private static final String PRIOR_LOG_SHA=
        "ec3c40456877334d5bcdb4db850bcb456d617fe1445dc4037cf61468d7fe6cbe";
    private static final double CHOSEN_SCORE=1808.5128484848483;
    private static final double ALTERNATIVE_SCORE=5948.889212121212;
    private static int assertions;

    private PcbPlacementScoreTermsContractTest() { }

    public static void main(String[] args) {
        check(args.length==0,"one fixed informed root and selected attempt only");
        Rb56Plan sourcePlan=Rb56Plan.resolve(ROOT,VERSION);
        TroubleshootBoard board=sourcePlan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(sourcePlan,board);
        PcbPlacementConstraints constraints=board.getPlacementConstraints();
        check(sourcePlan.seed==ROOT && sourcePlan.placementInterpretationVersion==VERSION,
            "accepted15 root42 source identity");
        check(sourcePlan.layoutSeed==LAYOUT_SEED,"exact accepted layout seed");
        check(constraints.placementDensityVersion==DENSITY,"accepted15 maps to density14");
        String sourceBefore=PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String graphBefore=PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(SOURCE_PLAN_SHA.equals(sourceBefore),"accepted15 source plan fingerprint");
        check(SOURCE_GRAPH_SHA.equals(graphBefore),"accepted15 live graph fingerprint");

        Observation observation=new Observation(LAYOUT_SEED,ATTEMPT);
        PcbPlacementPlanner.ScoreDiagnosticObserver previous=
            PcbPlacementPlanner.installScoreDiagnosticObserver(observation);
        PcbPlacementPlanner.Plan returnedPlan;
        try {
            returnedPlan=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry())
                .plan(board,constraints,sourcePlan.layoutSeed,ATTEMPT);
        } finally {
            PcbPlacementPlanner.restoreScoreDiagnosticObserver(observation,previous);
        }

        String returnedPlanSha=PlayerSessionFingerprint.of(planFingerprint(returnedPlan));
        check(returnedPlan.candidate==ATTEMPT,"direct planner returned selected attempt1");
        check(SELECTED_PLAN_SHA.equals(returnedPlanSha),"returned plan binds prior selected plan");
        check(returnedPlan.evaluations>0 && returnedPlan.evaluations<=TRACE_CAP,
            "one planner attempt remains inside its 60000 evaluation bound");
        check(observation.rawPlanCalls==1 && SELECTED_PLAN_SHA.equals(observation.rawPlanSha),
            "pre-compaction raw plan binds the prior selected plan exactly once");
        check(observation.rawPlanSha.equals(returnedPlanSha),
            "raw, returned and previously selected plan fingerprints agree");
        check(observation.context!=null && observation.contextRecords==1,
            "one QA context captured from the ordinary placement call");
        check(observation.choices.size()==1 && observation.choices.get(0).found,
            "one QA chosen placement captured");
        check(observation.origins.size()==6800 && !observation.traceCapped,
            "all 6800 origins retained under the 60000 record cap");
        for(int index=0;index<observation.origins.size();index++)
            check(observation.origins.get(index).sequence==index,
                "global original origin sequence remains contiguous at " + index);
        check(observation.context.pinLocal && !observation.context.anchor &&
                observation.context.algorithmCandidate==0,
            "pinned QA advice predicates and algorithm candidate");
        check(observation.context.allowedRectangle!=null &&
                !observation.context.vacancyTargets.isEmpty() &&
                observation.context.vacancyPreference!=null && observation.context.vacancyPocket!=null,
            "existing vacancy context is captured");

        PcbPlacementPlanner.ScoreDiagnosticChoice choice=observation.choices.get(0);
        check("QA".equals(choice.componentId) && choice.x==1690 && choice.y==1580 &&
                "DEG_0".equals(choice.rotation) && "TOP".equals(choice.mountingSide) &&
                choice.effectiveScore==CHOSEN_SCORE,
            "QA chosen origin and prior effective score are exact");
        PcbPlacementPlanner.ScoreDiagnosticOrigin chosen=findChosen(observation,choice);
        PcbPlacementPlanner.ScoreDiagnosticOrigin alternative=findAlternative(observation);
        check(chosen!=null && chosen.terms!=null,"chosen QA origin has existing score terms");
        check(alternative!=null && alternative.terms!=null && alternative.sequence==6297,
            "recorded same-pose alternative exists at original sequence6297");
        check("QA".equals(alternative.componentId) && alternative.x==1390 && alternative.y==1550 &&
                "DEG_0".equals(alternative.rotation) &&
                "TOP".equals(alternative.mountingSide) && alternative.effectiveScore==ALTERNATIVE_SCORE,
            "QA alternative origin and prior effective score are exact");
        check(chosen.terms.effectiveScore==CHOSEN_SCORE &&
                alternative.terms.effectiveScore==ALTERNATIVE_SCORE,
            "captured effective terms bind both pinned totals");

        String sourceAfter=PcbCanvasBoundaryContractTest.sourcePlanSha256(sourcePlan);
        String graphAfter=PcbCanvasBoundaryContractTest.liveGraphSha256(board);
        check(sourceBefore.equals(sourceAfter),"diagnostic preserves source plan");
        check(graphBefore.equals(graphAfter),"diagnostic preserves live graph");
        check(returnedPlanSha.equals(PlayerSessionFingerprint.of(planFingerprint(returnedPlan))),
            "fingerprint inspection preserves returned plan");
        System.out.println(jsonReceipt(sourceBefore,sourceAfter,graphBefore,graphAfter,
            observation.rawPlanSha,returnedPlanSha,returnedPlan.evaluations,
            observation,chosen,alternative));
        System.out.println("PASS: PCB placement score terms contracts: " + assertions + " assertions");
    }

    private static PcbPlacementPlanner.ScoreDiagnosticOrigin findChosen(Observation observation,
            PcbPlacementPlanner.ScoreDiagnosticChoice choice) {
        for(PcbPlacementPlanner.ScoreDiagnosticOrigin origin:observation.origins)
            if(origin.fits && "QA".equals(origin.componentId) && origin.x==choice.x &&
                    origin.y==choice.y && origin.effectiveScore==choice.effectiveScore)return origin;
        return null;
    }

    private static PcbPlacementPlanner.ScoreDiagnosticOrigin findAlternative(Observation observation) {
        for(PcbPlacementPlanner.ScoreDiagnosticOrigin origin:observation.origins)
            if(origin.sequence==6297)return origin;
        return null;
    }

    private static final class Observation implements PcbPlacementPlanner.ScoreDiagnosticObserver {
        final long seed;
        final int attempt;
        final Vector<PcbPlacementPlanner.ScoreDiagnosticOrigin> origins=
            new Vector<PcbPlacementPlanner.ScoreDiagnosticOrigin>();
        final Vector<PcbPlacementPlanner.ScoreDiagnosticChoice> choices=
            new Vector<PcbPlacementPlanner.ScoreDiagnosticChoice>();
        PcbPlacementPlanner.ScoreDiagnosticContext context;
        int contextRecords;
        boolean traceCapped;
        int rawPlanCalls;
        String rawPlanSha;
        Observation(long seed,int attempt) { this.seed=seed;this.attempt=attempt; }
        public boolean matches(long observedSeed,int observedAttempt,int densityVersion) {
            return seed==observedSeed && attempt==observedAttempt && densityVersion==DENSITY;
        }
        public void context(PcbPlacementPlanner.ScoreDiagnosticContext value) {
            if("QA".equals(value.componentId)) {context=value;contextRecords++;}
        }
        public void origin(PcbPlacementPlanner.ScoreDiagnosticOrigin origin) { origins.add(origin); }
        public void chosen(PcbPlacementPlanner.ScoreDiagnosticChoice choice) {
            if("QA".equals(choice.componentId))choices.add(choice);
        }
        public void rawPlan(PcbPlacementPlanner.Plan plan) {
            rawPlanCalls++;
            rawPlanSha=PlayerSessionFingerprint.of(planFingerprint(plan));
        }
        public void traceCapped(long observedSeed,int observedAttempt,int cap) {
            traceCapped=true;
            check(observedSeed==seed && observedAttempt==attempt && cap==TRACE_CAP,
                "trace cap callback is bound to this replay");
        }
    }

    private static String planFingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value=new StringBuilder();
        append(value,plan.outline.x+","+plan.outline.y+","+plan.outline.width+","+plan.outline.height);
        append(value,Long.toString(plan.courtyardArea));append(value,Long.toString(plan.demandArea));
        append(value,Integer.toString(plan.candidate));append(value,Integer.toString(plan.evaluations));
        value.append(plan.footprints.size()).append('[');
        for(PcbFootprint footprint:plan.footprints)append(value,footprint.geometryFingerprint());
        value.append(plan.regions.size()).append('[');
        for(PcbLayoutRegion region:plan.regions) {
            append(value,region.id);append(value,region.label);
            Vector<String> ids=region.getComponentIds();value.append(ids.size()).append('[');
            for(String id:ids)append(value,id);value.append(']');
        }
        value.append(']');return value.toString();
    }

    private static void append(StringBuilder value,String text) {
        value.append(text.length()).append(':').append(text).append(';');
    }

    private static String jsonReceipt(String sourceBefore,String sourceAfter,String graphBefore,
            String graphAfter,String rawPlanSha,String planSha,int placementEvaluations,
            Observation observation,
            PcbPlacementPlanner.ScoreDiagnosticOrigin chosen,
            PcbPlacementPlanner.ScoreDiagnosticOrigin alternative) {
        StringBuilder out=new StringBuilder(900000);
        out.append("PCB_PLACEMENT_SCORE_TERMS_CASE {");
        field(out,"seed","42");field(out,"layoutSeed","5526715536693200536");
        out.append(",\"attempt\":1,\"densityVersion\":14");
        field(out,"sourcePlanSha256",sourceBefore);field(out,"sourceGraphSha256",graphBefore);
        out.append(",\"sourcePlanUnchanged\":").append(sourceBefore.equals(sourceAfter));
        out.append(",\"graphUnchanged\":").append(graphBefore.equals(graphAfter));
        field(out,"rawPlanSha256",rawPlanSha);field(out,"returnedPlanSha256",planSha);
        field(out,"pinnedSelectedPlanSha256",SELECTED_PLAN_SHA);
        field(out,"priorSelectionLogSha256",PRIOR_LOG_SHA);
        field(out,"selectionBindingSource","pocket-decision-cause-r1/focused-native.txt");
        field(out,"supportEndpointSpanMeaning","already weighted endpointSpan*3.0 cost");
        out.append(",\"placementEvaluations\":").append(placementEvaluations)
            .append(",\"noRoutingExecuted\":true,\"componentId\":\"QA\"");
        out.append(",\"trace\":{\"records\":").append(observation.origins.size())
            .append(",\"cap\":").append(TRACE_CAP).append(",\"capped\":")
            .append(observation.traceCapped).append('}');
        out.append(",\"context\":");appendContext(out,observation.context);
        out.append(",\"chosen\":");appendScore(out,chosen);
        out.append(",\"alternative\":");appendScore(out,alternative);
        out.append(",\"origins\":[");
        for(int index=0;index<observation.origins.size();index++) {
            if(index>0)out.append(',');appendOrigin(out,observation.origins.get(index));
        }
        out.append("]}");return out.toString();
    }

    private static void appendContext(StringBuilder out,PcbPlacementPlanner.ScoreDiagnosticContext c) {
        out.append('{');field(out,"componentId",c.componentId);
        out.append(",\"algorithmCandidate\":").append(c.algorithmCandidate)
            .append(",\"pinLocal\":").append(c.pinLocal).append(",\"anchor\":").append(c.anchor)
            .append(",\"narrow\":").append(c.narrow).append(",\"tight\":").append(c.tight)
            .append(",\"useCompact\":").append(c.useCompact).append(",\"useGrouped\":").append(c.useGrouped)
            .append(",\"scoreGrouped\":").append(c.scoreGrouped)
            .append(",\"scoreWholeContent\":").append(c.scoreWholeContent)
            .append(",\"direction\":").append(c.direction).append(",\"boundaryX\":").append(c.boundaryX);
        out.append(",\"preferred\":");appendPoint(out,c.preferredX,c.preferredY);
        out.append(",\"base\":");appendPoint(out,c.baseX,c.baseY);
        out.append(",\"connectedTarget\":");appendPoint(out,c.connectedTarget);
        out.append(",\"support\":");
        if(c.supportOwnerId==null)out.append("null");
        else {
            out.append('{');field(out,"ownerId",c.supportOwnerId);field(out,"firstPadId",c.supportFirstPadId);
            field(out,"secondPadId",c.supportSecondPadId);out.append(",\"target\":");appendPoint(out,c.supportTarget);
            out.append(",\"firstAnchor\":");appendPoint(out,c.supportFirstAnchor);
            out.append(",\"secondAnchor\":");appendPoint(out,c.supportSecondAnchor);out.append('}');
        }
        out.append(",\"allowedRectangle\":");appendRectangle(out,c.allowedRectangle);
        out.append(",\"vacancyTargets\":[");
        for(int index=0;index<c.vacancyTargets.size();index++) {
            if(index>0)out.append(',');appendPoint(out,c.vacancyTargets.get(index));
        }
        out.append("],\"vacancyPreference\":");appendPoint(out,c.vacancyPreference);
        out.append(",\"vacancyPocket\":");appendRectangle(out,c.vacancyPocket);out.append('}');
    }

    private static void appendScore(StringBuilder out,PcbPlacementPlanner.ScoreDiagnosticOrigin origin) {
        PcbPlacementPlanner.ScoreDiagnosticTerms t=origin.terms;
        out.append('{').append("\"sequence\":").append(origin.sequence)
            .append(",\"x\":").append(origin.x).append(",\"y\":").append(origin.y);
        field(out,"rotation",origin.rotation);field(out,"mountingSide",origin.mountingSide);
        out.append(",\"loopKind\":");quote(out,origin.loopKind);
        appendTerms(out,t);out.append('}');
    }

    private static void appendTerms(StringBuilder out,PcbPlacementPlanner.ScoreDiagnosticTerms t) {
        out.append(",\"baseScore\":").append(t.baseScore)
            .append(",\"wireDistance\":").append(t.wireDistance)
            .append(",\"corridorPenalty\":").append(t.corridorPenalty)
            .append(",\"wholeContentGrowth\":").append(t.wholeContentGrowth)
            .append(",\"groupGrowth\":").append(t.groupGrowth)
            .append(",\"groupCenter\":").append(t.groupCenter)
            .append(",\"hint\":").append(t.hint)
            .append(",\"supportEndpointSpan\":").append(t.supportEndpointSpan)
            .append(",\"vacancyDistanceCost\":").append(t.vacancyDistanceCost)
            .append(",\"vacancyOverlapReward\":").append(t.vacancyOverlapReward)
            .append(",\"effectiveScore\":").append(t.effectiveScore)
            .append(",\"accessEnvelope\":");appendRectangle(out,t.accessEnvelope);
    }

    private static void appendOrigin(StringBuilder out,PcbPlacementPlanner.ScoreDiagnosticOrigin origin) {
        out.append('{').append("\"sequence\":").append(origin.sequence);
        field(out,"componentId",origin.componentId);
        out.append(",\"x\":").append(origin.x).append(",\"y\":").append(origin.y);
        field(out,"rotation",origin.rotation);field(out,"mountingSide",origin.mountingSide);
        out.append(",\"fits\":").append(origin.fits).append(",\"loopKind\":");quote(out,origin.loopKind);
        if(origin.terms!=null)appendTerms(out,origin.terms);
        else {out.append(",\"effectiveScore\":");number(out,origin.effectiveScore);}
        out.append('}');
    }

    private static void appendPoint(StringBuilder out,Point point) {
        if(point==null)out.append("null");else appendPoint(out,point.x,point.y);
    }
    private static void appendPoint(StringBuilder out,int x,int y) {
        out.append("{\"x\":").append(x).append(",\"y\":").append(y).append('}');
    }
    private static void appendRectangle(StringBuilder out,Rectangle rectangle) {
        if(rectangle==null)out.append("null");
        else out.append("{\"x\":").append(rectangle.x).append(",\"y\":").append(rectangle.y)
            .append(",\"width\":").append(rectangle.width).append(",\"height\":")
            .append(rectangle.height).append('}');
    }
    private static void field(StringBuilder out,String name,String value) {
        if(out.charAt(out.length()-1)!='{')out.append(',');
        quote(out,name);out.append(':');quote(out,value);
    }
    private static void number(StringBuilder out,double value) {
        if(Double.isNaN(value)||Double.isInfinite(value))out.append("null");else out.append(value);
    }
    private static void quote(StringBuilder out,String value) {
        out.append('"');
        if(value!=null)for(int index=0;index<value.length();index++) {
            char ch=value.charAt(index);
            if(ch=='"'||ch=='\\')out.append('\\').append(ch);
            else if(ch=='\n')out.append("\\n");else if(ch=='\r')out.append("\\r");
            else if(ch=='\t')out.append("\\t");else out.append(ch);
        }
        out.append('"');
    }
    private static void check(boolean condition,String message) {
        assertions++;if(!condition)throw new AssertionError(message);
    }
}
