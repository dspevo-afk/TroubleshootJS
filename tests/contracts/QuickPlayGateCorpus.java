package com.lushprojects.circuitjs1.client;

import java.util.HashSet;

/** Frozen population. JVM proves geometry only; real admission is a browser gate. */
public final class QuickPlayGateCorpus {
    public static void main(String[] args) {
        if (args.length == 0) { run(false); return; }
        if (!"--fixture-census".equals(args[0]))
            throw new IllegalArgumentException("Usage: QuickPlayGateCorpus [--fixture-census <1-128 canonical signed-long seeds>]");
        runFixtureCensus(args);
    }
    static long seed(boolean holdout, int i) {
        long z = (holdout ? 2026091602L : 2026091601L) + 0x9e3779b97f4a7c15L * (i + 1);
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
    static void run(boolean holdout) {
        run(holdout,null);
    }
    private static void runFixtureCensus(String[] args) {
        int count=args.length-1;
        if(count<1 || count>128)
            throw new IllegalArgumentException("Fixture census requires 1-128 exact signed-long seeds");
        String[] fixtures=new String[count]; HashSet<Long> seen=new HashSet<Long>();
        for(int i=0;i<count;i++) {
            String text=args[i+1]; long value;
            try { value=Long.parseLong(text); }
            catch(NumberFormatException invalid) {
                throw new IllegalArgumentException("Fixture census seed is not a signed long",invalid);
            }
            if(!Long.toString(value).equals(text))
                throw new IllegalArgumentException("Fixture census seed is not canonical signed-long text");
            if(!seen.add(Long.valueOf(value)))
                throw new IllegalArgumentException("Fixture census seeds must be distinct");
            fixtures[i]=text;
        }
        run(false,fixtures);
    }
    private static void run(boolean holdout,String[] fixtureSeeds) {
        CirSim sim = new CirSim(); sim.gridSize=16; sim.gridMask=~15; sim.gridRound=7; CircuitElm.sim=sim;
        boolean fixture=fixtureSeeds!=null;
        int count=fixture?fixtureSeeds.length:(holdout?48:24);
        String cohort=fixture?"fixture":(holdout?"holdout":"development");
        for (int i=0;i<count;i++) {
            long seed=fixture?Long.parseLong(fixtureSeeds[i]):seed(holdout,i), began=System.currentTimeMillis();
            try {
                Rb15Plan plan=Rb15Plan.resolve(seed); TroubleshootBoard board=plan.board();
                SupportedEnvelope envelope=SupportedEnvelope.current();
                // Match normal admission: filter each candidate by the envelope before selecting geometry.
                PcbBoardLayout layout=new SeededPcbLayoutGenerator().generate(board,
                    plan.layoutSeed,plan.routingSeed,envelope);
                envelope.requireBounds(board,layout); layout.validateGeometry(board);
                Rb15Plan replay=Rb15Plan.resolve(seed);
                if(!plan.canonical().equals(replay.canonical())) throw new AssertionError("Replay plan changed");
                PcbBoardLayout repeat;
                try {
                    repeat=new SeededPcbLayoutGenerator().generate(replay.board(),
                        replay.layoutSeed,replay.routingSeed,envelope);
                } catch (SupportedEnvelope.Rejected replayRejection) {
                    throw new IllegalStateException("Exact geometry replay was rejected by the envelope",replayRejection);
                } catch (PcbRoutingRejectedException replayRejection) {
                    throw new IllegalStateException("Exact geometry replay was rejected by routing",replayRejection);
                }
                if(!layout.geometryFingerprint().equals(repeat.geometryFingerprint())) throw new AssertionError("Replay geometry changed");
                Rectangle b=layout.getBoardOutline();
                StringBuilder parts=new StringBuilder("{");
                for(PcbComponentPlacement p:layout.getComponents()) {
                    if(parts.length()>1) parts.append(',');
                    parts.append('"').append(p.getComponentId()).append("\":[").append(p.getX()-b.x).append(',').append(p.getY()-b.y).append(']');
                }
                parts.append('}');
                System.out.println("GATE_ROW {\"seed\":\""+seed+"\",\"cohort\":\""+cohort+"\""+
                    (fixture?",\"index\":"+i:"")+",\"outcome\":\"PASS\",\"width\":"+b.width+",\"height\":"+b.height+
                    ",\"design\":\""+plan.topology()+"\",\"parts\":"+parts+",\"millis\":"+(System.currentTimeMillis()-began)+"}");
            } catch (SupportedEnvelope.Rejected rejection) {
                rejected(seed,cohort,i,fixture,"ENVELOPE_"+rejection.reason,began);
            } catch (PcbRoutingRejectedException rejection) {
                rejected(seed,cohort,i,fixture,"ROUTING_"+rejection.getKind(),began);
            }
        }
        System.out.println("PASS: Quick Play gate corpus rows="+count+(fixture?" cohort=fixture":" holdout="+holdout));
    }
    private static void rejected(long seed,String cohort,int index,boolean fixture,String reason,long began) {
        System.out.println("GATE_ROW {\"seed\":\""+seed+"\",\"cohort\":\""+cohort+"\""+
            (fixture?",\"index\":"+index:"")+",\"outcome\":\""+reason+"\",\"millis\":"+
            (System.currentTimeMillis()-began)+"}");
    }
}
