"""Add an accepted15-only wrapper and primitive observer collector to the COPY."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
COPY = Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
TESTS = COPY/'tests/contracts'
wrapper = TESTS/'PcbPostSelectionEdgeTrimContractTest.java'
original = wrapper.read_bytes().decode('utf-8')
newline = '\r\n' if '\r\n' in original else '\n'
needle = '    private PcbPostSelectionEdgeTrimContractTest() { }'
assert original.count(needle) == 1 and 'observeAccepted15' not in original
addition = '''

    /** Native diagnostic reuses every existing accepted15 physical witness. */
    static String observeAccepted15(long seed) {
        check(isFixedRoot(seed), "fixed accepted15 diagnostic root");
        Rb56Plan plan = Rb56Plan.resolve(seed, 15);
        TroubleshootBoard board = plan.board();
        PcbCanvasBoundaryContractTest.verifyBoardAgainstPlan(plan, board);
        return routeAndObserve(seed, 15, plan, board,
            PlayerSessionFingerprint.of(savedIntent(plan)),
            PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),
            PcbCanvasBoundaryContractTest.liveGraphSha256(board)).json;
    }
'''
wrapper.write_bytes(original.replace(needle, needle+addition.replace('\n', newline)).encode('utf-8'))
source = r'''package com.lushprojects.circuitjs1.client;

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
'''
target = TESTS/'PcbCompactorCutCauseContractTest.java'
assert not target.exists();target.write_bytes(source.encode('utf-8'))
roots=[-1017,-1004,-1022,0,17,42,1,7,5,14,2,3,4,6]
suites=[['PcbCompactorCutCauseContractTest','PCB compactor cut cause contracts ']]
for group in range(2,8):
    name='PcbCompactorCutCauseContractTestGroup'+str(group)
    marker='PCB compactor cut cause Group'+str(group)+' contracts '
    first,second=roots[(group-1)*2:group*2]
    java='package com.lushprojects.circuitjs1.client;\npublic final class '+name+' { public static void main(String[] args) { PcbCompactorCutCauseContractTest.runSeeds(new long[]{'+str(first)+'L,'+str(second)+'L}, "'+marker+'"); } }\n'
    target=TESTS/(name+'.java');assert not target.exists();target.write_bytes(java.encode('utf-8'))
    suites.append([name,marker])
unit=[['PcbCompactorCutObserverContractTest','PCB compactor cut observer contracts ']]
registry=COPY/'scripts/verify-current-contracts.ps1'
text=registry.read_bytes().decode('utf-8');nl='\r\n' if '\r\n' in text else '\n'
needle="        @{ Name = 'PcbCompactnessContractTest'; Marker = 'actual RB56 geometry report ' },"
assert text.count(needle)==1 and 'PcbCompactorCutObserverContractTest' not in text
entries=nl.join("        @{ Name = '"+name+"'; Marker = '"+marker+"' }," for name,marker in unit+suites)
registry.write_bytes(text.replace(needle,entries+nl+needle).encode('utf-8'))
protocol={'schema':1,'baseCommit':'8b88311959d3faa8df2fefa4e31b0dcd9594ff8f',
    'roots':[str(seed) for seed in roots], 'unitSuites':unit,'routesSuites':suites,
    'baselinePath':'docs/task-evidence/PCB/passive-local-pose-choice-r1/accepted15-baselines.json',
    'baselineSha256':'12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816',
    'unitPassDetail':'assertions=N fixtures=3 evaluator=compactRouted',
    'routePassDetail':'roots=2 rows=2 evaluator=compactRouted diagnosticOnly=true',
    'casePrefix':'PCB_COMPACTOR_CUT_CASE ','eventPrefix':'PCB_COMPACTOR_CUT_EVENT ',
    'outcomes':['NOCUT','ACCEPTED','PLACEMENT_REJECTED','QUALITY_REJECTED'],
    'placementReasons':['MISSING_COMPONENT','DUPLICATE_COMPONENT','ENVELOPE_ACCESS_OR_DOMAIN','STRANDED_ESCAPE','DOMAIN_CORRIDOR','ACCESS_GRID_BUDGET','DISCONNECTED_ESCAPE_CHANNEL'],
    'qualityReasons':['BENDS','DETOUR'],
    'scope':'Observational current15 only; all42 non-time fields must remain exact; no shrinkage claim'}
(HERE/'protocol.json').write_text(json.dumps(protocol,indent=2)+'\n')
print('PASS COPY wrapper/collector/seven route partitions/registry; protocol written; no runtime')
