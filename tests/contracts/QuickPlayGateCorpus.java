package com.lushprojects.circuitjs1.client;

/** Frozen population. JVM proves geometry only; real admission is a browser gate. */
public final class QuickPlayGateCorpus {
    public static void main(String[] args) { run(false); }
    static long seed(boolean holdout, int i) {
        long z = (holdout ? 2026091602L : 2026091601L) + 0x9e3779b97f4a7c15L * (i + 1);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
    static void run(boolean holdout) {
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        int count=holdout?48:24;
        for (int i=0;i<count;i++) {
            long seed=seed(holdout,i), began=System.currentTimeMillis();
            try {
                Rb15Plan plan=Rb15Plan.resolve(seed); TroubleshootBoard board=plan.board();
                PcbBoardLayout layout=new SeededPcbLayoutGenerator().generate(board,plan.layoutSeed,plan.routingSeed);
                SupportedEnvelope.current().requireBounds(board,layout); layout.validateGeometry(board);
                Rb15Plan replay=Rb15Plan.resolve(seed);
                if(!plan.canonical().equals(replay.canonical())) throw new AssertionError("Replay plan changed");
                PcbBoardLayout repeat=new SeededPcbLayoutGenerator().generate(replay.board(),replay.layoutSeed,replay.routingSeed);
                if(!layout.geometryFingerprint().equals(repeat.geometryFingerprint())) throw new AssertionError("Replay geometry changed");
                Rectangle b=layout.getBoardOutline();
                StringBuilder parts=new StringBuilder("{");
                for(PcbComponentPlacement p:layout.getComponents()) {
                    if(parts.length()>1) parts.append(',');
                    parts.append('"').append(p.getComponentId()).append("\":[").append(p.getX()-b.x).append(',').append(p.getY()-b.y).append(']');
                }
                parts.append('}');
                System.out.println("GATE_ROW {\"seed\":\""+seed+"\",\"cohort\":\""+(holdout?"holdout":"development")+
                    "\",\"outcome\":\"PASS\",\"width\":"+b.width+",\"height\":"+b.height+
                    ",\"design\":\""+plan.topology()+"\",\"parts\":"+parts+",\"millis\":"+(System.currentTimeMillis()-began)+"}");
            } catch (SupportedEnvelope.Rejected rejection) {
                rejected(seed,holdout,"ENVELOPE_"+rejection.reason,began);
            } catch (PcbRoutingRejectedException rejection) {
                rejected(seed,holdout,"ROUTING_"+rejection.getKind(),began);
            }
        }
        System.out.println("PASS: Quick Play gate corpus rows="+count+" holdout="+holdout);
    }
    private static void rejected(long seed,boolean holdout,String reason,long began) {
        System.out.println("GATE_ROW {\"seed\":\""+seed+"\",\"cohort\":\""+(holdout?"holdout":"development")+
            "\",\"outcome\":\""+reason+"\",\"millis\":"+(System.currentTimeMillis()-began)+"}");
    }
}
