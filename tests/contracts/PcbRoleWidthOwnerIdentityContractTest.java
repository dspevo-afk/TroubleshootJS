package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Real-owner regression for private RB56 placement identity across density-compatible versions. */
public final class PcbRoleWidthOwnerIdentityContractTest {
    private static int assertions;

    public static void main(String[] args) {
        CirSim sim=new CirSim();
        sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7;
        CircuitElm.sim=sim;
        Vector<GeneratedBoardInstance> owners=new Vector<GeneratedBoardInstance>();
        try {
            GenerationRequest request11=privateRequest(11,0L);
            GenerationRequest roleWidthsRequest12=privateRequest(12,0L);
            GenerationRequest request13=privateRequest(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_NEAREST_VACANCY,0L);
            GenerationRequest request14=privateRequest(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CANVAS,0L);
            GenerationRequest request15=privateRequest(
                GenerationRequest.RB56_PLACEMENT_INTERPRETATION_CURRENT,0L);
            PlayerSessionReplay replay11=PlayerSessionReplay.forQualification(request11);
            PlayerSessionReplay replay12=PlayerSessionReplay.forQualification(roleWidthsRequest12);
            PlayerSessionReplay replay13=PlayerSessionReplay.forQualification(request13);
            PlayerSessionReplay replay14=PlayerSessionReplay.forQualification(request14);
            PlayerSessionReplay replay15=PlayerSessionReplay.forQualification(request15);
            GeneratedBoardInstance saved11=constructAdmitted(request11);
            owners.add(saved11);
            GeneratedBoardInstance saved12=constructAdmitted(roleWidthsRequest12);
            owners.add(saved12);
            GeneratedBoardInstance saved13=constructAdmitted(request13);
            owners.add(saved13);
            GeneratedBoardInstance saved14=constructAdmitted(request14);
            owners.add(saved14);
            GeneratedBoardInstance current15=constructAdmitted(request15);
            owners.add(current15);

            PcbPlacementConstraints constraints11=saved11.getBoard().getPlacementConstraints();
            PcbPlacementConstraints constraints12=saved12.getBoard().getPlacementConstraints();
            PcbPlacementConstraints constraints13=saved13.getBoard().getPlacementConstraints();
            PcbPlacementConstraints constraints14=saved14.getBoard().getPlacementConstraints();
            PcbPlacementConstraints constraints15=current15.getBoard().getPlacementConstraints();
            check(saved11.getCircuitFamilyId().equals(Rb56Plan.FAMILY_ID) && saved11.getSeed()==0L &&
                    saved12.getCircuitFamilyId().equals(Rb56Plan.FAMILY_ID) && saved12.getSeed()==0L &&
                    saved13.getCircuitFamilyId().equals(Rb56Plan.FAMILY_ID) && saved13.getSeed()==0L &&
                    saved14.getCircuitFamilyId().equals(Rb56Plan.FAMILY_ID) && saved14.getSeed()==0L &&
                    current15.getCircuitFamilyId().equals(Rb56Plan.FAMILY_ID) && current15.getSeed()==0L,
                "all actual admitted owners are RB56 seed0");
            check(constraints11.placementDensityVersion==11 &&
                    constraints11.getTraceWidthProfile()==PcbTraceWidthProfile.UNIFORM,
                "saved11 owner independently retains density11 and uniform trace width");
            check(constraints12.placementDensityVersion==11 &&
                    constraints12.getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS,
                "saved12 owner independently retains density11 and role trace widths");
            check(constraints13.placementDensityVersion==12 &&
                    constraints13.getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS,
                "saved13 owner independently retains density12 and role trace widths");
            check(constraints14.placementDensityVersion==13 &&
                    constraints14.getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS &&
                    constraints14.requiresTwoLayerRouting,
                "saved14 owner independently retains density13, ROLE widths, and two-layer routing");
            check(constraints15.placementDensityVersion==14 &&
                    constraints15.getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS &&
                    constraints15.requiresTwoLayerRouting,
                "current15 owner independently selects density14, ROLE widths, and two-layer routing");
            check(request13.getRb56PlacementInterpretationVersion()==13 &&
                    request14.getRb56PlacementInterpretationVersion()==14 &&
                    request15.getRb56PlacementInterpretationVersion()==15,
                "saved13, saved14, and current15 requests retain their exact placement identities");
            check(replay11.rb56PlacementInterpretationVersion()==11 && replay11.matches(saved11) &&
                    replay12.rb56PlacementInterpretationVersion()==12 && replay12.matches(saved12) &&
                    replay13.rb56PlacementInterpretationVersion()==13 && replay13.matches(saved13) &&
                    replay14.rb56PlacementInterpretationVersion()==14 && replay14.matches(saved14) &&
                    replay15.rb56PlacementInterpretationVersion()==15 && replay15.matches(current15),
                "each private replay identity matches its own normally admitted owner");
            String replay13Text="tsj-private-session/13/"+GenerationDependencyContext.INTERPRETATION_EPOCH+
                "/rb56-placement-13/RB56_CONTROL/0";
            String replay14Text="tsj-private-session/14/"+GenerationDependencyContext.INTERPRETATION_EPOCH+
                "/rb56-placement-14/RB56_CONTROL/0";
            String replay15Text="tsj-private-session/15/"+GenerationDependencyContext.INTERPRETATION_EPOCH+
                "/rb56-placement-15/RB56_CONTROL/0";
            check(replay13Text.equals(replay13.canonical()) && replay14Text.equals(replay14.canonical()) &&
                    replay15Text.equals(replay15.canonical()) &&
                    !replay13.canonical().equals(replay14.canonical()) &&
                    !replay14.canonical().equals(replay15.canonical()),
                "saved13, saved14, and current15 use distinct versioned replay namespaces");
            check(replay13.matches(request13) && replay14.matches(request14) && replay15.matches(request15) &&
                    !replay13.matches(request14) && !replay14.matches(request13) &&
                    !replay14.matches(request15) && !replay15.matches(request14),
                "saved13/saved14/current15 replay identities reject cross-version requests");
            check(!replay11.matches(saved12),"saved11 replay rejects the saved12 owner");
            check(!replay11.matches(saved13),"saved11 replay rejects the saved13 owner");
            check(!replay11.matches(saved14),"saved11 replay rejects the saved14 owner");
            check(!replay11.matches(current15),"saved11 replay rejects the current15 owner");
            check(!replay12.matches(saved11),"saved12 replay rejects the saved11 owner");
            check(!replay12.matches(saved13),"saved12 replay rejects the saved13 owner");
            check(!replay12.matches(saved14),"saved12 replay rejects the saved14 owner");
            check(!replay12.matches(current15),"saved12 replay rejects the current15 owner");
            check(!replay13.matches(saved11),"saved13 replay rejects the saved11 owner");
            check(!replay13.matches(saved12),"saved13 replay rejects the saved12 owner");
            check(!replay13.matches(saved14) && !replay14.matches(saved13),
                "ROLE-width replay identities reject the density12/density13 cross-match");
            check(!replay13.matches(current15) && !replay14.matches(current15) &&
                    !replay15.matches(saved13) && !replay15.matches(saved14),
                "saved13/saved14/current15 identities reject density12/13/14 cross-matches");
            check(!replay14.matches(saved11) && !replay14.matches(saved12) &&
                    !replay15.matches(saved11) && !replay15.matches(saved12),
                "saved14 and current15 replays reject older density/profile owners");

            PlayerSessionReplay wrongSeed=PlayerSessionReplay.forQualification(privateRequest(13,1L));
            check(!wrongSeed.matches(saved13),"saved13 owner identity rejects a different exact seed");
            PlayerSessionReplay wrongSaved14Seed=PlayerSessionReplay.forQualification(privateRequest(14,1L));
            check(!wrongSaved14Seed.matches(saved14),"saved14 owner identity rejects a different exact seed");
            PlayerSessionReplay wrongCurrentSeed=PlayerSessionReplay.forQualification(privateRequest(15,1L));
            check(!wrongCurrentSeed.matches(current15),"current15 owner identity rejects a different exact seed");
            GeneratedBoardInstance otherFamily=GenerationRequest.leaf("LED_INDICATOR",0L,false)
                .resolve(new GenerationRequest.PlanCache()).construct().instance;
            owners.add(otherFamily);
            otherFamily.requireNormalPhysicalAdmission();
            check(!replay14.matches(otherFamily) && !replay15.matches(otherFamily),
                "saved14 and current15 RB56 identities reject another family owner");
            check(!replay14.matches((GeneratedBoardInstance)null) &&
                    !replay15.matches((GeneratedBoardInstance)null),
                "saved14 and current15 owner identities reject a missing candidate");
            System.out.println("PASS: PCB role width owner identity contracts assertions="+assertions);
        } finally {
            for(GeneratedBoardInstance owner:owners)
                for(CircuitElm element:owner.getSimulationElements()) element.delete();
            sim.solverExecutor.retire();
        }
    }

    private static GenerationRequest privateRequest(int placementVersion,long seed) {
        return GenerationRequest.forFamilyQualification(Rb56Plan.FAMILY_ID,seed)
            .withRb56PlacementInterpretationVersion(placementVersion);
    }

    private static GeneratedBoardInstance constructAdmitted(GenerationRequest request) {
        GenerationRequest.Prepared prepared=request.resolve(new GenerationRequest.PlanCache());
        GenerationRequest.ConstructionSession session=prepared.beginConstruction();
        int advances=0;
        while(true) {
            if(advances>=640)
                throw new AssertionError("RB56 placement "+request.getRb56PlacementInterpretationVersion()+
                    " exceeded the unchanged 640-advance construction bound");
            if(session.advance()) break;
            advances++;
        }
        GeneratedBoardInstance owner=session.result().instance;
        owner.requireNormalPhysicalAdmission();
        return owner;
    }

    private static void check(boolean condition,String message) {
        assertions++;
        if(!condition) throw new AssertionError(message);
    }
}
