package com.lushprojects.circuitjs1.client;

/** Literal area/access/terminal correspondence checks, independent of rendered scale. */
public final class PcbCompactionContractTest {
    private static int assertions;
    private static void check(boolean condition, String reason) {
        assertions++; if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        int smaller = 0;
        for (String family : PlayerFamilyCatalog.families()) {
            GeneratedBoardInstance instance = new PlayerLaunchRequest(family, "0",
                PlayerFamilyCatalog.candidateProfile(family).name()).generation()
                .resolve(new GenerationRequest.PlanCache()).construct().instance;
            TroubleshootBoard board = instance.getBoard();
            PcbPlacementPlanner planner = new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry());
            PcbPlacementPlanner.Plan original = planner.plan(board, board.getPlacementConstraints(), 0, 0);
            String untouched = fingerprint(original);
            PcbPlacementPlanner.Plan compact = PcbPlacementCompactor.compact(board, board.getPlacementConstraints(), original);
            check(untouched.equals(fingerprint(original)), "compaction mutates no original candidate");
            check(fingerprint(compact).equals(fingerprint(PcbPlacementCompactor.compact(board,
                board.getPlacementConstraints(), original))), "repeat compaction is deterministic");
            PcbPlacementPlanner.validate(board, board.getPlacementConstraints(), compact.outline, compact.footprints);
            long before=(long)original.outline.width*original.outline.height;
            long after=(long)compact.outline.width*compact.outline.height;
            check(after <= before, "board never grows"); if(after < before) smaller++;
            verifyParts(original, compact);
            System.out.println("PCB_COMPACTION family="+family+" before="+before+" after="+after);
        }
        check(smaller > 0, "real playable placement has less empty area");
        System.out.println("PASS: PCB compaction contracts assertions="+assertions+" reducedFamilies="+smaller);
    }
    private static String fingerprint(PcbPlacementPlanner.Plan plan) {
        StringBuilder value=new StringBuilder(); value.append(plan.outline.width).append('x').append(plan.outline.height);
        for(PcbFootprint part:plan.footprints)value.append(part.geometryFingerprint());
        return value.toString();
    }
    private static void verifyParts(PcbPlacementPlanner.Plan original, PcbPlacementPlanner.Plan compact) {
        check(original.footprints.size()==compact.footprints.size(), "no part is discarded");
        for(int i=0;i<original.footprints.size();i++) {
            PcbFootprint a=original.footprints.get(i), b=compact.footprints.get(i);
            PcbComponentPlacement ap=a.getPlacement(),bp=b.getPlacement();
            int dx=bp.getX()-ap.getX(),dy=bp.getY()-ap.getY();
            check(ap.getComponentId().equals(bp.getComponentId()) &&
                ap.getPhysicalGeometry()==bp.getPhysicalGeometry(), "exact package geometry and identity retained");
            for(PcbPadPlacement pad:a.getPads()) {
                PcbPadPlacement moved=b.getPad(pad.getPadId());
                check(moved.getX()==pad.getX()+dx && moved.getY()==pad.getY()+dy,
                    "pad follows the same rigid translation as its part");
                check(moved.getProbeBounds().width==pad.getProbeBounds().width &&
                    moved.getProbeBounds().height==pad.getProbeBounds().height,
                    "real probe targets are not shrunk or inflated");
            }
        }
    }
}
