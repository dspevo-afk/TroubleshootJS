"""Prepare two bounded paired-route fixtures; observer owns only passive capture."""
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
path=COPY/'tests/contracts/PcbPassiveChoiceRouteContractTest.java'
assert not path.exists()
path.write_text('''package com.lushprojects.circuitjs1.client;
/** Actual old/trial generation with read-only candidate1 observation. */
public final class PcbPassiveChoiceRouteContractTest {
    public static void main(String[] args) { run(42L,"PCB passive choice route42 contracts "); }
    static void run(long seed,String marker) {
        if(seed!=42L && seed!=3L)throw new AssertionError("Fixed two diagnostic roots only");
        PcbPassiveChoiceObserver.Capture saved=observe(seed,15,null);
        PcbPassiveChoiceObserver.Capture trial=observe(seed,16,saved);
        System.out.println("PCB_PASSIVE_CHOICE_CAPTURE "+saved.json());
        System.out.println("PCB_PASSIVE_CHOICE_CAPTURE "+trial.json());
        System.out.println("PASS: "+marker+"roots=1 rows=2");
    }
    private static PcbPassiveChoiceObserver.Capture observe(long seed,int version,
            PcbPassiveChoiceObserver.Capture reference) {
        Rb56Plan plan=PcbPassivePoseTrialSourceContractTest.bound(seed,version);
        TroubleshootBoard board=plan.board();
        PcbPassiveChoiceObserver.Capture capture=PcbPassiveChoiceObserver.start(
            seed,plan.layoutSeed,version,reference);
        PcbPostSelectionEdgeTrimContractTest.PosePolicyObservation observed;
        try {
            observed=PcbPostSelectionEdgeTrimContractTest.routePosePolicy(seed,version,plan,board,
                PcbPostSelectionEdgeTrimContractTest.sourceIntentShaForPosePolicy(plan),
                PcbCanvasBoundaryContractTest.sourcePlanSha256(plan),
                PcbCanvasBoundaryContractTest.liveGraphSha256(board));
        } finally { PcbPassiveChoiceObserver.stop(capture); }
        String prefix="PCB_EDGE_TRIM_CASE ";
        if(observed==null || observed.layout==null || !observed.json.startsWith(prefix))
            throw new AssertionError("Missing actual physical route observation");
        if(!observed.admissionIdentity.equals(version==15 ? "RB56_BOARD_NORMAL@1" : "RB56_BOARD_NORMAL@2"))
            throw new AssertionError("Wrong actual issued admission identity");
        System.out.println("PCB_PASSIVE_CHOICE_ROUTE "+observed.json.substring(prefix.length()));
        return capture;
    }
}
''')
(COPY/'tests/contracts/PcbPassiveChoiceRouteContractTestGroup2.java').write_text('''package com.lushprojects.circuitjs1.client;
public final class PcbPassiveChoiceRouteContractTestGroup2 {
    public static void main(String[] args) { PcbPassiveChoiceRouteContractTest.run(3L,"PCB passive choice route3 contracts "); }
}
''')
unit=[['PcbPassiveChoiceObserverContractTest','PCB passive choice observer contracts '],
 ['PcbPassivePoseSourceContractTest','PCB passive pose source bindings '],
 ['PcbPassivePoseTrialSourceContractTest','PCB passive pose trial source contracts ']]
routes=[['PcbPassiveChoiceRouteContractTest','PCB passive choice route42 contracts '],
 ['PcbPassiveChoiceRouteContractTestGroup2','PCB passive choice route3 contracts ']]
(HERE/'unit-suites.json').write_text(json.dumps(unit,indent=2)+'\n')
(HERE/'route-suites.json').write_text(json.dumps(routes,indent=2)+'\n')
print('Prepared paired42/3 fullroute fixtures and unit3/route2 suite lists; no runtime')
