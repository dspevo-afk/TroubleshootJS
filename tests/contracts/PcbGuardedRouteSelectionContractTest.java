package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Adversarial preliminary ranks; actual geometry/admission lives in the six-root suite. */
public final class PcbGuardedRouteSelectionContractTest {
    private static int checks;
    public static void main(String[] args) {
        permute(new int[]{0,1,2,3,4,5}, 0);
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> lowExperimental = candidates(new int[]{3,2,0,1,4,5});
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> saved6 =
            SeededPcbLayoutGenerator.prioritizeGroupedPlacementRouteCandidates(lowExperimental, 6, true);
        check(saved6.get(1).attempt == 3, "saved6 reproduces preliminary compact displacement");
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> current =
            SeededPcbLayoutGenerator.prioritizeGroupedPlacementRouteCandidates(lowExperimental, 7, true);
        check(current.get(1).attempt == 2, "lower-scored experimental3 cannot evict retained2");
        check(attempt(current, 2, 0) == 5, "zero P07 successes retains roomy even after P05 succeeds");
        check(attempt(current, 2, 1) == 3, "validated policy route permits best remaining experiment");
        for (int missing : new int[]{2,4,5}) {
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> subset = candidates(new int[]{3,2,0,1,4,5});
            for (int i=subset.size()-1;i>=0;i--) if(subset.get(i).attempt==missing) subset.remove(i);
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> chosen =
                SeededPcbLayoutGenerator.prioritizeGroupedPlacementRouteCandidates(subset,7,true);
            check(attempt(chosen,2,1)==chosen.get(2).attempt, "missing retained placement does not spend protected slot");
            check(chosen.size()==subset.size(), "missing placement creates no invented candidate");
        }
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> single =
            SeededPcbLayoutGenerator.prioritizeGroupedPlacementRouteCandidates(lowExperimental,7,false);
        check(single.get(0).attempt==0 && single.get(1).attempt==1 && single.get(2).attempt==5,
            "single-group policy preserves both tight crops and roomy fallback");
        check(MediumBoardPhysicalPolicy.ROUTING_CANDIDATES==3 &&
            MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES==6, "unchanged independent finite caps");
        System.out.println("PASS: guarded PCB route selection contracts assertions="+checks);
    }
    private static int attempt(Vector<MediumBoardPhysicalPolicy.PlacementCandidate> rows,int index,int successes) {
        return rows.get(SeededPcbLayoutGenerator.guardedRouteCandidateIndex(rows,index,successes)).attempt;
    }
    private static void permute(int[] order,int position) {
        if(position==order.length) {
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> original=candidates(order);
            Vector<MediumBoardPhysicalPolicy.PlacementCandidate> chosen=
                SeededPcbLayoutGenerator.prioritizeGroupedPlacementRouteCandidates(original,7,true);
            check(chosen.size()==6 && original.get(0).attempt==order[0], "retains full census without mutating input");
            check(chosen.get(0).attempt==4 && chosen.get(1).attempt==2 && chosen.get(2).attempt==5,
                "every adversarial rank preserves 4/2/5 fallback chain");
            int best=-1;
            for(int id:order) if(id!=4 && id!=2 && id!=5) {best=id;break;}
            for(int successes:new int[]{0,1,2}) {
                check(attempt(chosen,0,successes)==4 && attempt(chosen,1,successes)==2,
                    "successful predecessor never displaces retained second route");
                int third=attempt(chosen,2,successes);
                check(third==(successes==0?5:best), "policy-local success gates third-slot exploration");
                check(third!=4 && third!=2, "three unique actual route requests");
            }
            return;
        }
        for(int i=position;i<order.length;i++) {
            int swap=order[position];order[position]=order[i];order[i]=swap;
            permute(order,position+1);
            swap=order[position];order[position]=order[i];order[i]=swap;
        }
    }
    private static Vector<MediumBoardPhysicalPolicy.PlacementCandidate> candidates(int[] order) {
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> rows=new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        for(int id:order) rows.add(new MediumBoardPhysicalPolicy.PlacementCandidate(id,
            new PcbPlacementPlanner.Plan(new Rectangle(0,0,100,100),new Vector<PcbFootprint>(),
                new Vector<PcbLayoutRegion>(),0,0,id,0)));
        return rows;
    }
    private static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
}
