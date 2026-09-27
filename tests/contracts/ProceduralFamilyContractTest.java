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
                if (Rb30Plan.FAMILY_ID.equals(family)) runRootCohort(family, selectedCohort);
                else runCohort(family, selectedCohort);
                break;
            }
            for (int cohort=0;cohort<SEEDS.length;cohort++) {
                if (Rb30Plan.FAMILY_ID.equals(family)) runRootCohort(family, cohort);
                else runCohort(family, cohort);
            }
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
                owner.requireNormalPhysicalAdmission();
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
                if (Rb30Plan.FAMILY_ID.equals(family)) {
                    check(owner.getPhysicalAdmission() instanceof MediumBoardNormalAdmission &&
                        MediumBoardPhysicalPolicy.selected(owner.getBoard().getPlacementConstraints()),
                        "Normal Q30 cohort lacks its accepted medium physical admission");
                } else {
                    check(owner.getBoard().getPlacementConstraints().routingLayer==PcbCopperLayer.BOTTOM,
                        "Normal family used a fixed top-face layout path");
                }
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
    /**
     * Q30's population is sampled through the public New Board entropy-root
     * search. The older families above intentionally retain their direct exact
     * seed corpus and its original row/assertion contract.
     */
    private static void runRootCohort(String family,int cohort) {
        int acceptedRoots=0;
        HashSet<String> layouts=new HashSet<String>(),macros=new HashSet<String>();
        DifficultyProfile profile=PlayerFamilyCatalog.candidateProfile(family);
        for (long root:SEEDS[cohort]) {
            long began=System.currentTimeMillis();
            PlayerLaunchRequest launch=PlayerLaunchRequest.random(family,
                Long.toString(root),profile.name());
            GenerationRequest search=launch.generation();
            int candidateCount=search.candidateCount();
            check(launch.seed==root && launch.candidateSearch && search.isCandidateSearch() &&
                candidateCount==QuickPlayAdmission.MAX_CANDIDATES &&
                QuickPlayAdmission.MAX_CANDIDATES==4,
                "Q30 New Board root must retain its exact entropy and finite four-candidate search");
            boolean rootAccepted=false;
            for (int ordinal=0;ordinal<candidateCount;ordinal++) {
                GenerationRequest exact=search.candidate(ordinal);
                long candidateSeed=exact.getDescriptor().getRootSeed();
                check(candidateSeed==QuickPlayAdmission.candidateSeed(root,ordinal),
                    "Q30 candidate API changed canonical ordinal order");
                try {
                    GeneratedBoardInstance owner=construct(exact);
                    owner.requireNormalPhysicalAdmission();
                    owner.getPcbLayout().validateGeometry(owner.getBoard());
                    String fine=PhysicalBoardFingerprint.of(owner),macro=macro(owner);
                    String exactGeometry=owner.getPcbLayout().geometryFingerprint();
                    check(owner.getSeed()==candidateSeed && owner.getCircuitFamilyId().equals(family),
                        "Accepted Q30 candidate changed its exact seed or family identity");
                    check(owner.getPhysicalAdmission() instanceof MediumBoardNormalAdmission &&
                        MediumBoardPhysicalPolicy.selected(owner.getBoard().getPlacementConstraints()),
                        "Accepted Q30 root lacks its normal medium physical admission");
                    check(owner.getPcbLayout().getGenerationPlacementAttempts()>0,
                        "Accepted Q30 candidate recorded no actual generic placement work");
                    layouts.add(fine);macros.add(macro);acceptedRoots++;
                    rootAccepted=true;
                    System.out.println("PROCEDURAL_ROOT_ATTEMPT|"+cohort+"|"+family+"|"+root+
                        "|"+ordinal+"|"+candidateSeed+"|ACCEPT|STRUCTURAL_ACCEPTANCE");

                    PlayerLaunchRequest accepted=launch.accepted(candidateSeed);
                    String replayText=accepted.replay();
                    PlayerLaunchRequest replay=PlayerLaunchRequest.parse(replayText);
                    GenerationRequest exactReplay=replay.generation();
                    boolean replayPassed=!replay.candidateSearch && replay.seed==candidateSeed &&
                        replay.familyId.equals(family) && replay.profile==profile &&
                        exactReplay.candidateCount()==1 && !exactReplay.isCandidateSearch();
                    String replayDetail="";
                    if (replayPassed) {
                        try {
                            GeneratedBoardInstance replayOwner=construct(exactReplay);
                            replayOwner.requireNormalPhysicalAdmission();
                            replayOwner.getPcbLayout().validateGeometry(replayOwner.getBoard());
                            replayPassed=replayOwner.getSeed()==candidateSeed &&
                                replayOwner.getCircuitFamilyId().equals(family) &&
                                fine.equals(PhysicalBoardFingerprint.of(replayOwner)) &&
                                exactGeometry.equals(replayOwner.getPcbLayout().geometryFingerprint());
                            if (!replayPassed) replayDetail="identity-or-full-geometry-mismatch";
                        } catch (PcbRoutingRejectedException rejection) {
                            replayPassed=false;
                            replayDetail=logDetail(rejection.getMessage());
                        } catch (SupportedEnvelope.Rejected rejection) {
                            replayPassed=false;
                            replayDetail=logDetail(rejection.getMessage());
                        }
                    } else {
                        replayDetail="accepted-replay-was-not-one-exact-candidate";
                    }
                    check(replayPassed,
                        "Accepted Q30 replay must parse as one exact candidate with identical full geometry");
                    System.out.println("PROCEDURAL_ROOT_REPLAY|"+cohort+"|"+family+"|"+root+
                        "|"+candidateSeed+"|"+(replayPassed?"PASS":"FAIL")+
                        (replayDetail.length()==0?"":"|"+replayDetail));
                    Rectangle outline=owner.getPcbLayout().getBoardOutline();
                    System.out.println("PROCEDURAL_ROOT_ROW|"+cohort+"|"+family+"|"+root+
                        "|ACCEPT|"+candidateSeed+"|"+ordinal+"|"+outline.width+"|"+
                        outline.height+"|"+fine.hashCode()+"|"+macro.hashCode()+"|"+
                        (System.currentTimeMillis()-began));
                    break;
                } catch (PcbRoutingRejectedException rejection) {
                    System.out.println("PROCEDURAL_ROOT_ATTEMPT|"+cohort+"|"+family+"|"+root+
                        "|"+ordinal+"|"+candidateSeed+"|ROUTE_REJECT|"+
                        logDetail(rejection.getMessage()));
                } catch (SupportedEnvelope.Rejected rejection) {
                    System.out.println("PROCEDURAL_ROOT_ATTEMPT|"+cohort+"|"+family+"|"+root+
                        "|"+ordinal+"|"+candidateSeed+"|ENVELOPE_REJECT|"+
                        logDetail(rejection.getMessage()));
                }
            }
            if (!rootAccepted) {
                System.out.println("PROCEDURAL_ROOT_ROW|"+cohort+"|"+family+"|"+root+
                    "|REJECTED|none|-1|none|none|none|none|"+
                    (System.currentTimeMillis()-began));
            }
        }
        System.out.println("PROCEDURAL_ROOT_SUMMARY|"+family+"|"+cohort+"|"+
            acceptedRoots+"|"+layouts.size()+"|"+macros.size());
        check(acceptedRoots>=6,"Too many structurally rejected Q30 roots in cohort "+cohort);
        check(layouts.size()>=6,"Q30 full geometry diversity collapsed in cohort "+cohort);
        check(macros.size()>=4,"Q30 macro layouts collapsed in cohort "+cohort);
    }

    private static GeneratedBoardInstance construct(GenerationRequest request) {
        if (request.isCandidateSearch())
            throw new IllegalArgumentException("Construct one exact Q30 candidate at a time");
        GenerationRequest.Prepared prepared=request.resolve(new GenerationRequest.PlanCache());
        if (request.getExecutionPolicy()!=GenerationExecutionPolicy.NORMAL_MEDIUM)
            return prepared.construct().instance;
        GenerationRequest.ConstructionSession session=prepared.beginConstruction();
        int steps=0;
        while(!session.advance())
            if(++steps>GenerationCoordinator.MAX_JOB_STEPS)
                throw new AssertionError("Normal Q30 route exceeded the unchanged shared step bound");
        return session.result().instance;
    }

    private static String logDetail(String detail) {
        if (detail==null) return "";
        return detail.replace('|','/').replace('\r',' ').replace('\n',' ');
    }

    private static GeneratedBoardInstance construct(String family,long seed) {
        GenerationRequest request = new PlayerLaunchRequest(family,Long.toString(seed),
            PlayerFamilyCatalog.candidateProfile(family).name()).generation();
        GenerationRequest.Prepared prepared=request.resolve(new GenerationRequest.PlanCache());
        if (request.getExecutionPolicy()!=GenerationExecutionPolicy.NORMAL_MEDIUM)
            return prepared.construct().instance;
        GenerationRequest.ConstructionSession session=prepared.beginConstruction();
        int steps=0;
        while(!session.advance())
            if(++steps>GenerationCoordinator.MAX_JOB_STEPS)
                throw new AssertionError("Normal Q30 route exceeded the unchanged shared step bound");
        return session.result().instance;
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
