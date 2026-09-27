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
        for (String family : PlayerFamilyCatalog.registeredFamilies()) {
            // Q30 seed 0 is a retained route rejection; seed 13 is a qualified accepted route.
            GeneratedBoardInstance instance = construct(family,
                Rb30Plan.FAMILY_ID.equals(family) ? 13L : 0L);
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
    private static GeneratedBoardInstance construct(String family, long seed) {
        GenerationRequest request = new PlayerLaunchRequest(family, Long.toString(seed),
            PlayerFamilyCatalog.candidateProfile(family).name()).generation();
        GenerationRequest.Prepared prepared = request.resolve(new GenerationRequest.PlanCache());
        if (request.getExecutionPolicy() != GenerationExecutionPolicy.NORMAL_MEDIUM)
            return prepared.construct().instance;
        GenerationRequest.ConstructionSession session = prepared.beginConstruction();
        int steps = 0;
        while (!session.advance()) {
            if (++steps > GenerationCoordinator.MAX_JOB_STEPS)
                throw new AssertionError("Normal Q30 route exceeded the unchanged shared step bound");
        }
        return session.result().instance;
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
