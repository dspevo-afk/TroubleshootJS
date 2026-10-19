"""Register/archive diagnostic bytes; restore only verified task-owned originals."""
import hashlib,json,subprocess,sys,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
NAME='PcbPocketDecisionDiagnosticContractTest'
OWNERS=['src/com/lushprojects/circuitjs1/client/PcbPlacementPlanner.java','scripts/verify-current-contracts.ps1']
ACTIVE=ROOT/'tests/contracts'/str(NAME+'.java')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def read(n):return json.loads((HERE/n).read_text(encoding='utf-8-sig'))
def save(n,obj):(HERE/n).write_text(json.dumps(obj,indent=2)+'\n')
def scratch():
    state=HERE/'local-state.json'
    if state.is_symlink() or state.is_junction():raise ValueError('Recovery state reparse')
    p=Path(read('local-state.json')['scratch'])
    if not p.is_absolute() or p.parent.resolve()!=Path(tempfile.gettempdir()).resolve() or not p.name.startswith('tsj-pocket-decision-') or p.is_symlink() or p.is_junction() or p.resolve()!=p.absolute():raise ValueError('Snapshot containment/reparse')
    if {x.name for x in p.iterdir()}!={'0','1'}:raise ValueError('Snapshot inventory')
    for index,row in enumerate(read('original-source.json')['files']):
        child=p/str(index)
        if child.is_symlink() or child.is_junction() or sha(child)!=row['sha256']:raise ValueError('Original snapshot changed')
    return p
def record():
    if not ACTIVE.is_file():raise ValueError('Diagnostic source missing')
    archived=HERE/(NAME+'.java');archived.write_bytes(ACTIVE.read_bytes())
    (HERE/'planner-instrumentation.java').write_bytes((ROOT/OWNERS[0]).read_bytes())
    patch=subprocess.check_output(['git','diff','--',OWNERS[0]],cwd=ROOT)
    (HERE/'planner-instrumentation.patch').write_bytes(patch)
    save('diagnostic-source.json',{'files':[{'path':p,'sha256':sha(ROOT/p)} for p in OWNERS]+[{'path':ACTIVE.relative_to(ROOT).as_posix(),'sha256':sha(ACTIVE)}]})
def register():
    p=scratch();registry=ROOT/OWNERS[1]
    original=read('original-source.json')['files'][1]
    if sha(registry)!=original['sha256']:raise ValueError('Registry changed outside initial registration')
    data=(p/'1').read_bytes();eol=b'\r\n' if b'\r\n' in data else b'\n'
    anchor=b"        @{ Name = 'PcbCanvasOwnerBoundsContractTest';"
    if data.count(anchor)!=1 or NAME.encode() in data:raise ValueError('Registry anchor/uniqueness')
    row=("        @{ Name = '"+NAME+"'; Marker = 'PCB pocket decision diagnostic contracts ' },").encode()+eol
    registry.write_bytes(data.replace(anchor,row+anchor))
    save('focused-plan.json',{'names':[NAME],'count':1,'bounds':'Maintained60s JVM; native-only informed2-root measurement; no production behavior acceptance'})
    record();print('REGISTERED one diagnostic suite; archived current source/patch')
def restore():
    p=scratch();owned=read('diagnostic-source.json')['files']
    for row in owned:
        actual=ROOT/row['path']
        if not actual.is_file() or sha(actual)!=row['sha256']:raise ValueError('Task owner changed since final diagnostic recording: '+row['path'])
    if sha(ACTIVE)!=sha(HERE/(NAME+'.java')):raise ValueError('Active/archive mismatch')
    originals=read('original-source.json')['files']
    for i,row in enumerate(originals):(ROOT/row['path']).write_bytes((p/str(i)).read_bytes())
    ACTIVE.unlink()
    for i,row in enumerate(originals):
        if sha(ROOT/row['path'])!=row['sha256']:raise ValueError('Restore failed')
        (p/str(i)).unlink()
    p.rmdir();(HERE/'local-state.json').unlink()
    save('byte-restoration.json',{'status':'PASS','verifiedOriginalOwnersRestored':2,'verifiedActiveDiagnosticRetired':True,'ownedSnapshotAndPrivateStateRemoved':True})
    print('RESTORED exact accepted15 planner/registry; retired archived active test and owned originals')
if __name__=='__main__':{'register':register,'record':record,'restore':restore}[sys.argv[1]]()
