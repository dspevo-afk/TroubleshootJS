"""Reconstruct all original owners after erasing only the diagnostic adapters."""
import ast
import difflib
import hashlib
import json
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
COPY=Path(json.loads((HERE/'local-state.json').read_text())['scratch'])
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
paths=['src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java',
    'tests/contracts/PcbPostSelectionEdgeTrimContractTest.java','scripts/verify-current-contracts.ps1']
original=(ROOT/paths[0]).read_bytes().decode('utf-8')
current=(COPY/paths[0]).read_bytes().decode('utf-8')
nl='\r\n' if '\r\n' in original else '\n'
def replace_once(text,before,after=''):
    before=before.replace('\n',nl);after=after.replace('\n',nl)
    assert text.count(before)==1, before[:100]
    return text.replace(before,after)
stripped=replace_once(current,'''
    interface DiagnosticObserver {
        void begin(int candidate,int axis,int beforeWidth,int beforeHeight,int traces,int holes);
        void cut(int candidate,int axis,int index,int coordinate,int amount);
        void end(int candidate,int axis,int cutCount,int removed,String outcome,String reason,
                int afterWidth,int afterHeight);
    }
    static DiagnosticObserver diagnosticObserver;
''')
stripped=replace_once(stripped,'            DiagnosticObserver observer=diagnosticObserver;\n')
stripped=replace_once(stripped,'''            if(observer!=null) observer.begin(plan.candidate,axis,plan.outline.width,
                plan.outline.height,result.layout.getTraces().size(),result.layout.getHoles().size());
''')
stripped=replace_once(stripped,'if(observer!=null)observer.cut(plan.candidate,axis,cuts.size()-1,next[0],remove);')
stripped=replace_once(stripped,'''            if(removed==0) {
                if(observer!=null) observer.end(plan.candidate,axis,cuts.size(),removed,"NOCUT","",
                    result.plan.outline.width,result.plan.outline.height);
                continue;
            }
''','            if(removed==0)continue;\n')
stripped=replace_once(stripped,'            String outcome="ACCEPTED",reason="";\n')
stripped=replace_once(stripped,' outcome="PLACEMENT_REJECTED";reason=rejected.reason;')
stripped=replace_once(stripped,' outcome="QUALITY_REJECTED";reason=rejected.getKind().name();')
stripped=replace_once(stripped,'''            if(observer!=null) observer.end(plan.candidate,axis,cuts.size(),removed,outcome,reason,
                result.plan.outline.width,result.plan.outline.height);
''')
assert stripped==original,'Compactor changes extend beyond the reviewed primitive observer'
# Reconstruct the existing complete physical witness helper byte for byte.
module=ast.parse((HERE/'prepare_cases.py').read_text())
addition=next(ast.literal_eval(node.value) for node in module.body if isinstance(node,ast.Assign) and
    any(isinstance(target,ast.Name) and target.id=='addition' for target in node.targets))
prior=(ROOT/paths[1]).read_bytes().decode('utf-8');new=(COPY/paths[1]).read_bytes().decode('utf-8')
ending='\r\n' if '\r\n' in prior else '\n'
addition=addition.replace('\n',ending)
assert new.count(addition)==1 and new.replace(addition,'')==prior
# Registry retains every existing entry, ordering and verifier command.
prior=(ROOT/paths[2]).read_bytes().decode('utf-8');new=(COPY/paths[2]).read_bytes().decode('utf-8')
ending='\r\n' if '\r\n' in prior else '\n';protocol=json.loads((HERE/'protocol.json').read_text())
for name,marker in protocol['unitSuites']+protocol['routesSuites']:
    line="        @{ Name = '"+name+"'; Marker = '"+marker+"' },"+ending
    assert new.count(line)==1;new=new.replace(line,'')
assert new==prior
additions=['tests/contracts/PcbCompactorCutObserverContractTest.java','tests/contracts/PcbCompactorCutCauseContractTest.java']
additions+=['tests/contracts/PcbCompactorCutCauseContractTestGroup'+str(i)+'.java' for i in range(2,8)]
diff=[]
for name in paths+additions:
    old=(ROOT/name).read_text().splitlines(True) if name in paths else []
    new=(COPY/name).read_text().splitlines(True)
    diff.extend(difflib.unified_diff(old,new,fromfile=name,tofile='COPY/'+name,n=0))
(HERE/'source-review.diff').write_text(''.join(diff),encoding='utf-8')
record={'status':'PASS','compactorAfterObserverErasure':'BYTE_EXACT',
    'physicalWitnessAfterWrapperErasure':'BYTE_EXACT','verifierAfterRegistrationErasure':'BYTE_EXACT',
    'diffSha256':sha(HERE/'source-review.diff'),
    'sourceDeltas':{name:sha(COPY/name) for name in paths+additions},
    'runtimeEvidence':'NOT RUN by source audit'}
(HERE/'source-review.json').write_text(json.dumps(record,indent=2)+'\n')
print('PASS three owners reconstruct byte-exact; eleven diagnostic deltas bound to current diff')
