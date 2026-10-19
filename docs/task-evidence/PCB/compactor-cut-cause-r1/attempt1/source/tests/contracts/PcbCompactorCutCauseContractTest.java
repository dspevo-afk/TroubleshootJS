package com.lushprojects.circuitjs1.client;

/** Current15 observation only; no alternative cut or routing selection. */
public final class PcbCompactorCutCauseContractTest {
    private static final long[] ROOTS={-1017L,-1004L,-1022L,0L,17L,42L,1L,7L,5L,14L,2L,3L,4L,6L};
    public static void main(String[] args) {
        runSeeds(new long[]{-1017L,-1004L}, "PCB compactor cut cause contracts ");
    }
    static void runSeeds(long[] seeds,String marker) {
        need(seeds.length==2,"two fixed roots per bounded JVM");
        need(seeds[0]!=seeds[1],"distinct fixed roots");
        for(long seed:seeds) {
            boolean known=false; for(long root:ROOTS)if(seed==root)known=true;
            need(known,"fixed informed root");
            need(PcbPlacementCompactor.diagnosticObserver==null,"no prior observer");
            Collector observer=new Collector(seed);
            String row;
            try {
                PcbPlacementCompactor.diagnosticObserver=observer;
                row=PcbPostSelectionEdgeTrimContractTest.observeAccepted15(seed);
            } finally {
                PcbPlacementCompactor.diagnosticObserver=null;
            }
            need(!observer.active && observer.call>=1 && observer.call%2==1,"complete paired axis lifecycles");
            need(PcbPlacementCompactor.diagnosticObserver==null,"observer cleared");
            String prefix="PCB_EDGE_TRIM_CASE ";
            need(row.startsWith(prefix),"existing physical receipt prefix");
            System.out.println("PCB_COMPACTOR_CUT_CASE "+row.substring(prefix.length()));
        }
        System.out.println("PASS: "+marker+"roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true");
    }
    private static void need(boolean value,String reason) {
        if(!value)throw new AssertionError(reason);
    }
    private static final class Collector implements PcbPlacementCompactor.DiagnosticObserver {
        final long seed;
        int call=-1,candidate,axis,width,height,cuts,removed,priorCoordinate;
        boolean active;
        Collector(long seed){this.seed=seed;}
        private String base(String event) {
            return "PCB_COMPACTOR_CUT_EVENT {\"seed\":\""+seed+"\",\"call\":"+call+
                ",\"candidate\":"+candidate+",\"axis\":"+axis+",\"event\":\""+event+"\"";
        }
        public void begin(int candidate,int axis,int beforeWidth,int beforeHeight,int traces,int holes) {
            need(!active,"no nested axis observation");
            call++;need(axis==call%2 && candidate>=0 && candidate<6,"axis/candidate order");
            need(beforeWidth>0 && beforeHeight>0 && traces>=0 && holes>=0,"begin dimensions/counts");
            if(axis==1)need(candidate==this.candidate && beforeWidth==width && beforeHeight==height,"paired axis input follows retained result");
            this.candidate=candidate;this.axis=axis;this.width=beforeWidth;this.height=beforeHeight;
            cuts=0;removed=0;priorCoordinate=Integer.MIN_VALUE;active=true;
            System.out.println(base("BEGIN")+",\"beforeWidth\":"+width+",\"beforeHeight\":"+height+
                ",\"traces\":"+traces+",\"holes\":"+holes+"}");
        }
        public void cut(int candidate,int axis,int index,int coordinate,int amount) {
            need(active && this.candidate==candidate && this.axis==axis,"cut owns current axis");
            need(index==cuts && coordinate>priorCoordinate && amount>0 && amount%10==0,"canonical cut order/amount");
            cuts++;removed+=amount;priorCoordinate=coordinate;
            System.out.println(base("CUT")+",\"index\":"+index+",\"coordinate\":"+coordinate+",\"amount\":"+amount+"}");
        }
        public void end(int candidate,int axis,int cutCount,int removed,String outcome,String reason,int afterWidth,int afterHeight) {
            need(active && this.candidate==candidate && this.axis==axis,"end owns current axis");
            need(cutCount==cuts && removed==this.removed,"cut count and sum exact");
            boolean accepted="ACCEPTED".equals(outcome),noCut="NOCUT".equals(outcome);
            boolean rejected="PLACEMENT_REJECTED".equals(outcome)||"QUALITY_REJECTED".equals(outcome);
            need(accepted||noCut||rejected,"finite outcome");
            need(noCut ? cuts==0 : cuts>0,"outcome cut count");
            need((accepted||noCut) ? reason.length()==0 : reason.matches("[A-Z][A-Z0-9_]*"),"bounded reason token");
            need(afterWidth==width-(accepted && axis==0?removed:0) &&
                afterHeight==height-(accepted && axis==1?removed:0),"accepted transform or retained axis");
            System.out.println(base("END")+",\"cutCount\":"+cutCount+",\"removed\":"+removed+
                ",\"outcome\":\""+outcome+"\",\"reason\":\""+reason+"\",\"afterWidth\":"+afterWidth+
                ",\"afterHeight\":"+afterHeight+"}");
            need(afterWidth>0 && afterHeight>0,"positive retained dimensions");
            width=afterWidth;height=afterHeight;active=false;
        }
    }
}
