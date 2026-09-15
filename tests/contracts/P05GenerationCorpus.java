package com.lushprojects.circuitjs1.client;
public final class P05GenerationCorpus {
 public static void main(String[] args) throws Exception {
  long[] seeds={0,1,2,3,17,42,101,-1,9007199254740993L,Long.MIN_VALUE,Long.MAX_VALUE,-4518705223253195925L,-5365808313541656343L};
  for(long seed:seeds) {
   Rb15Plan plan=Rb15Plan.resolve(seed);TroubleshootBoard board=plan.board();long start=System.nanoTime();
   try {
    PcbBoardLayout layout=new SeededPcbLayoutGenerator().generate(board,plan.layoutSeed,plan.routingSeed);
    layout.validateGeometry(board);Rectangle b=layout.getBoardOutline();
    System.out.println("P05_GENERATION {\"seed\":\""+seed+"\",\"outcome\":\"SUCCESS\",\"placements\":"+layout.getGenerationPlacementAttempts()+
     ",\"routes\":"+layout.getGenerationRoutingAttempts()+",\"expansions\":"+layout.getGenerationRoutingExpansions()+
     ",\"area\":"+((long)b.width*b.height)+",\"score\":"+layout.getRouteQualityScore(board)+",\"elapsedMs\":"+(System.nanoTime()-start)/1000000.0+"}");
   } catch(PcbRoutingRejectedException exhausted) {
    System.out.println("P05_GENERATION {\"seed\":\""+seed+"\",\"outcome\":\""+exhausted.getKind()+"\",\"placements\":"+exhausted.getAttemptCount()+
     ",\"elapsedMs\":"+(System.nanoTime()-start)/1000000.0+"}");
   }
  }
  System.out.println("PASS: P05 generation comparison rows="+seeds.length);
 }
}
