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
            GenerationRequest request12=privateRequest(12,0L);
            PlayerSessionReplay replay11=PlayerSessionReplay.forQualification(request11);
            PlayerSessionReplay replay12=PlayerSessionReplay.forQualification(request12);
            GeneratedBoardInstance saved11=constructAdmitted(request11);
            owners.add(saved11);
            GeneratedBoardInstance current12=constructAdmitted(request12);
            owners.add(current12);

            PcbPlacementConstraints constraints11=saved11.getBoard().getPlacementConstraints();
            PcbPlacementConstraints constraints12=current12.getBoard().getPlacementConstraints();
            check(saved11.getCircuitFamilyId().equals(Rb56Plan.FAMILY_ID) && saved11.getSeed()==0L &&
                    current12.getCircuitFamilyId().equals(Rb56Plan.FAMILY_ID) && current12.getSeed()==0L,
                "both actual admitted owners are RB56 seed0");
            check(constraints11.placementDensityVersion==11 &&
                    constraints11.getTraceWidthProfile()==PcbTraceWidthProfile.UNIFORM,
                "saved11 owner independently retains density11 and uniform trace width");
            check(constraints12.placementDensityVersion==11 &&
                    constraints12.getTraceWidthProfile()==PcbTraceWidthProfile.ROLE_WIDTHS,
                "current12 owner independently retains density11 and role trace widths");
            check(replay11.rb56PlacementInterpretationVersion()==11 &&
                    replay12.rb56PlacementInterpretationVersion()==12 &&
                    replay11.matches(saved11) && replay12.matches(current12),
                "each private replay identity matches its own normally admitted owner");
            check(!replay11.matches(current12),
                "saved11 replay rejects the current12 owner despite shared density11");
            check(!replay12.matches(saved11),
                "current12 replay rejects the saved11 owner despite shared density11");

            PlayerSessionReplay wrongSeed=PlayerSessionReplay.forQualification(privateRequest(12,1L));
            check(!wrongSeed.matches(current12),"private owner identity rejects a different exact seed");
            GeneratedBoardInstance otherFamily=GenerationRequest.leaf("LED_INDICATOR",0L,false)
                .resolve(new GenerationRequest.PlanCache()).construct().instance;
            owners.add(otherFamily);
            otherFamily.requireNormalPhysicalAdmission();
            check(!replay12.matches(otherFamily),"RB56 private owner identity rejects another family owner");
            check(!replay12.matches((GeneratedBoardInstance)null),
                "private owner identity rejects a missing candidate");
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
