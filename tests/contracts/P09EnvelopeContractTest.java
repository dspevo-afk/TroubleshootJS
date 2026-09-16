package com.lushprojects.circuitjs1.client;
public final class P09EnvelopeContractTest {
    public static void main(String[] args) {
        CirSim sim=new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        GeneratedBoardInstance owner=GenerationRequest.leaf("LED_INDICATOR",0,false)
            .resolve(new GenerationRequest.PlanCache()).construct().instance;
        int checks=P09EnvelopeChecks.verify(owner)+P09EnvelopeChecks.verifyColdAdmission(sim)+verifyEventSnapshot(sim);
        for(DifficultyProfile profile:DifficultyProfile.values()) {
            if(!profile.isAvailable()) {
                boolean rejected=false;
                try { new PlayerLaunchRequest("LED_INDICATOR","0",profile.name()); }
                catch(IllegalArgumentException expected) { rejected=true; }
                if(!rejected) throw new AssertionError("Unqualified difficulty accepted");
                checks++; continue;
            }
            GenerationRequest request=new PlayerLaunchRequest("LED_INDICATOR","0",profile.name()).generation();
            if(request.getSupportedEnvelope()!=SupportedEnvelope.current() || !request.canonical().contains("physicalEnvelope="+SupportedEnvelope.current().identity()))
                throw new AssertionError("Difficulty profile omits physical contract");
            checks++;
        }
        System.out.println("P09_POLICY "+SupportedEnvelope.current().canonical());
        System.out.println("PASS: P09 physical envelope contracts assertions="+checks);
    }
    private static int verifyEventSnapshot(CirSim sim) {
        sim.elmList=new java.util.Vector<CircuitElm>(); sim.t=0;
        final StringBuilder fired=new StringBuilder();
        SolverEventQueue queue=sim.solverExecutor.events(null);
        SolverEventQueue.Event first=queue.schedule(1,new SolverEventQueue.Action(){
            public void fire(double scheduled,double accepted){fired.append("A");}});
        queue.schedule(1,new SolverEventQueue.Action(){
            public void fire(double scheduled,double accepted){fired.append("B");}});
        SolverEventQueue.Event cancelled=queue.schedule(1,new SolverEventQueue.Action(){
            public void fire(double scheduled,double accepted){fired.append("X");}});
        cancelled.cancel();
        CircuitSolverExecutor.PrivateState saved=sim.solverExecutor.snapshotState();
        first.cancel(); queue.dispatchAccepted(2); sim.t=2; queue.cancel();
        sim.t=0; sim.solverExecutor.snapshotRestored(saved);
        SolverEventQueue restored=sim.solverExecutor.events(null);
        restored.dispatchAccepted(0);
        if(restored!=queue || restored.nextTime()!=1) throw new AssertionError("Snapshot lost event queue/handles");
        fired.setLength(0); restored.dispatchAccepted(1);
        if(!"AB".equals(fired.toString())) throw new AssertionError("Snapshot lost event ordering/cancellation: "+fired);
        boolean rejected=false;
        try { restored.dispatchAccepted(.5); } catch(IllegalStateException expected){rejected=true;}
        if(!rejected) throw new AssertionError("Ordinary backwards event dispatch was allowed");
        sim.t=0; sim.solverExecutor.snapshotRestored(saved);
        first.cancel(); fired.setLength(0); sim.solverExecutor.events(null).dispatchAccepted(1);
        if(!"B".equals(fired.toString())) throw new AssertionError("Restored event handle no longer cancels its event");
        sim.t=0; sim.solverExecutor.snapshotRestored(saved);
        fired.setLength(0); sim.solverExecutor.events(null).dispatchAccepted(1);
        if(!"AB".equals(fired.toString())) throw new AssertionError("Snapshot changed after earlier restoration");
        sim.t=0; sim.solverExecutor.snapshotRestored(saved); sim.solverExecutor.retire();
        return 5;
    }
}
