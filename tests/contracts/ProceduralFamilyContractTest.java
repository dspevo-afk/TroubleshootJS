package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Vector;

/** Structural population oracle; actual solver admission is a separate browser gate. */
public final class ProceduralFamilyContractTest {
    private static int assertions;
    private static final Vector<String> failures = new Vector<String>();
    private static final long[][] SEEDS = {
        {0,1,4,17,42,-1,Long.MIN_VALUE,Long.MAX_VALUE},
        {936927718510540323L,-6751984890832468710L,4374486180868546127L,
         -1470617604193128648L,9007199254740993L,-9007199254740993L,
         281474976710656L,-4194978729361594021L}
    };
    public static void main(String[] args) {
        if (args.length == 1 && "--list-families".equals(args[0])) {
            System.out.println("PROCEDURAL_FAMILIES|" + familyList());
            return;
        }
        String selectedFamily = null;
        int selectedCohort = -1;
        if (args.length != 0) {
            if (args.length != 4 || !"--family".equals(args[0]) ||
                    !"--cohort".equals(args[2])) {
                throw new IllegalArgumentException("Expected --family <id> --cohort <0|1> or --list-families");
            }
            selectedFamily = args[1];
            if (!PlayerFamilyCatalog.contains(selectedFamily))
                throw new IllegalArgumentException("Unknown procedural family: " + selectedFamily);
            if (!"0".equals(args[3]) && !"1".equals(args[3]))
                throw new IllegalArgumentException("Cohort must be 0 or 1");
            selectedCohort = Integer.parseInt(args[3]);
        }
        CirSim sim=new CirSim(); sim.gridSize=16;sim.gridMask=~15;sim.gridRound=7;CircuitElm.sim=sim;
        for (String family:PlayerFamilyCatalog.families()) {
            if (selectedFamily != null && !selectedFamily.equals(family)) continue;
            for (DifficultyProfile profile:DifficultyProfile.values()) {
                boolean selectable = profile == PlayerFamilyCatalog.candidateProfile(family);
                check(QuickPlayAdmission.supports(family,profile)==selectable,
                    "Procedural admission must match actual player content");
                if (!selectable) continue;
                for (long seed:SEEDS[0]) {
                    PlayerLaunchRequest r=PlayerLaunchRequest.random(family,Long.toString(seed),profile.name());
                    check(r.seed==seed && r.candidateSearch && r.generation().candidateCount()==4,
                        "Random launch snapped entropy or omitted bounded search");
                    PlayerLaunchRequest exact=r.accepted(QuickPlayAdmission.candidateSeed(seed,0));
                    check(PlayerLaunchRequest.parse(exact.replay()).seed==seed &&
                        exact.generation().candidateCount()==1, "Replay must be exact, not another search");
                }
            }
            if (selectedFamily != null) {
                runCohort(family, selectedCohort);
                break;
            }
            for (int cohort=0;cohort<SEEDS.length;cohort++) runCohort(family, cohort);
        }
        if (!failures.isEmpty()) throw new AssertionError(failures.toString());
        System.out.println("PASS: procedural family contracts assertions="+assertions);
    }
    private static String familyList() {
        Vector<String> families=PlayerFamilyCatalog.families();
        StringBuilder result=new StringBuilder();
        for (int i=0;i<families.size();i++) {
            if (i>0) result.append(',');
            result.append(families.get(i));
        }
        return result.toString();
    }
    private static void runCohort(String family,int cohort) {
        int accepted=0; HashSet<String> layouts=new HashSet<String>(), macros=new HashSet<String>();
        for (long seed:SEEDS[cohort]) {
            long began=System.currentTimeMillis();
            try {
                GeneratedBoardInstance owner=construct(family,seed);
                SupportedEnvelope.current().requireNormal(owner);
                owner.getPcbLayout().validateGeometry(owner.getBoard());
                String fine=fingerprint(owner),macro=macro(owner);
                if (cohort==0 && seed==17 && family.equals("COMPOSED_CONTROLLED_INDICATOR")) {
                    GenerationRequest.Prepared prepared=new PlayerLaunchRequest(family,"17","MEDIUM")
                        .generation().resolve(new GenerationRequest.PlanCache());
                    GenerationRequest.ConstructionSession session=prepared.beginConstruction();
                    int turns=0;
                    while(!session.advance())check(++turns<=80,"Unbounded staged composition routing");
                    check(fine.equals(fingerprint(session.result().instance)),
                        "Staged composition changed exact layout relative to cold construction");
                }
                if (cohort==0 && family.equals("LED_INDICATOR")) System.out.println("LAYOUT_DETAIL|"+family+"|"+seed+"|attempts="+owner.getPcbLayout().getGenerationPlacementAttempts()+"|"+fine);
                layouts.add(fine);macros.add(macro);accepted++;
                check(owner.getSeed()==seed && owner.getCircuitFamilyId().equals(family),"Exact requested identity");
                check(owner.getBoard().getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM,
                    "Normal family used a fixed top-face layout path");
                check(owner.getPcbLayout().getGenerationPlacementAttempts()>0,
                    "No actual generic placement work was recorded");
                if (cohort==0 && seed==0) {
                    check(fine.equals(fingerprint(construct(family,seed))),"Same seed changed geometry");
                    for (GeneratedFaultCandidate hypothesis : owner.getFaultCandidates()) {
                        if (!hypothesis.isAdmitted()) continue;
                        GeneratedBoardInstance alternate = owner.getDiagnosticProvider().generateHypothesis(hypothesis);
                        check(alternate.getSeed()==seed && alternate.getCircuitFamilyId().equals(family),
                            "Hypothesis changed the physical board identity");
                        check(fine.equals(fingerprint(alternate)),
                            "Selected fault changed component placement or routed copper in " + family);
                    }
                }
                System.out.println("PROCEDURAL_ROW|"+cohort+"|"+family+"|"+seed+
                    "|ACCEPT|"+owner.getPcbLayout().getBoardOutline().width+
                    "|"+owner.getPcbLayout().getBoardOutline().height+
                    "|"+fine.hashCode()+"|"+macro.hashCode()+"|"+(System.currentTimeMillis()-began));
            } catch (PcbRoutingRejectedException rejection) {
                System.out.println("PROCEDURAL_ROW|"+cohort+"|"+family+"|"+seed+
                    "|ROUTE_REJECT|"+rejection.getMessage());
            } catch (SupportedEnvelope.Rejected rejection) {
                System.out.println("PROCEDURAL_ROW|"+cohort+"|"+family+"|"+seed+
                    "|ENVELOPE_REJECT|"+rejection.getMessage());
            }
        }
        System.out.println("PROCEDURAL_SUMMARY|"+family+"|"+cohort+"|"+accepted+"|"+layouts.size()+"|"+macros.size());
        check(accepted>=6,"Too many physical rejections in "+family+" cohort "+cohort);
        check(layouts.size()>=6,"Full geometry diversity collapsed in "+family);
        check(macros.size()>=4,"Macro layouts collapsed in "+family+" cohort "+cohort);
    }
    private static GeneratedBoardInstance construct(String family,long seed) {
        return new PlayerLaunchRequest(family,Long.toString(seed),PlayerFamilyCatalog.candidateProfile(family).name())
            .generation().resolve(new GenerationRequest.PlanCache()).construct().instance;
    }
    private static String fingerprint(GeneratedBoardInstance b) {
        PcbBoardLayout l=b.getPcbLayout();Rectangle o=l.getBoardOutline();
        StringBuilder s=new StringBuilder().append(o.width).append('/').append(o.height);
        Vector<String> ids=b.getBoard().getComponentIds();Collections.sort(ids);
        for(String id:ids) {
            PcbComponentPlacement p=l.getComponent(id);
            s.append(';').append(id).append(':').append(p.getX()-o.x).append(',').append(p.getY()-o.y)
                .append(':').append(p.getGeometryRealization().getVariantKey());
        }
        for(PcbTraceGeometry t:l.getTraces()) {
            s.append(';').append(t.getNetId()).append(':').append(t.getLayer());
            int[] x=t.getXPoints(),y=t.getYPoints();
            for(int i=0;i<x.length;i++)s.append('/').append(x[i]-o.x).append(',').append(y[i]-o.y);
        }
        return s.toString();
    }
    private static String macro(GeneratedBoardInstance b) {
        PcbBoardLayout l=b.getPcbLayout();Vector<String> ids=b.getBoard().getComponentIds();
        Collections.sort(ids);StringBuilder s=new StringBuilder();
        // Pairwise left/right/above/below relations ignore translation, edge padding,
        // component values, selected faults and simple uniform scaling.
        for(int i=0;i<ids.size();i++)for(int j=i+1;j<ids.size();j++) {
            PcbComponentPlacement a=l.getComponent(ids.get(i)),c=l.getComponent(ids.get(j));
            s.append(Integer.signum(a.getX()-c.getX())).append(',').append(Integer.signum(a.getY()-c.getY())).append(';');
        }
        return s.toString();
    }
    private static void check(boolean ok,String message) { assertions++;if(!ok)failures.add(message); }
}
