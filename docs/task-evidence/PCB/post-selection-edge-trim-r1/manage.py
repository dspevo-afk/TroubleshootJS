"""Snapshot only intended original owners, reconstruct trial, restore if rejected."""
import hashlib,json,subprocess,sys,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
OWNER_NAMES=['Rb56Placement.java','PcbPlacementConstraints.java','SeededPcbLayoutGenerator.java','PcbBoardLayout.java']
OWNERS=['src/com/lushprojects/circuitjs1/client/'+n for n in OWNER_NAMES]+['scripts/verify-current-contracts.ps1']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(name,obj):(HERE/name).write_bytes((json.dumps(obj,indent=2)+'\n').encode())
def prepare_source():
    if (HERE/'local-state.json').exists():raise ValueError('Existing trial state')
    known={r['path']:r['sha256'] for r in json.loads((HERE.parent/'viewport-scroll-r1/final-inputs.json').read_text(encoding='utf-8-sig'))['files']}
    rows=[{'path':p,'sha256':sha(ROOT/p)} for p in OWNERS]
    if any(r['sha256']!=known[r['path']] for r in rows):raise ValueError('Accepted source boundary changed')
    scratch=Path(tempfile.mkdtemp(prefix='tsj-edge-trim-'))
    for i,r in enumerate(rows):(scratch/str(i)).write_bytes((ROOT/r['path']).read_bytes())
    save('original-source.json',{'files':rows});save('local-state.json',{'scratch':str(scratch)})
    subprocess.run([sys.executable,'-X','utf8','-B',str(HERE/'apply_trial.py')],check=True)
    save('trial-source.json',{'files':[{'path':p,'sha256':sha(ROOT/p)} for p in OWNERS]})
    print('PREPARED explicit native15 source only; original registry unchanged')
def register():
    if not (HERE/'local-state.json').exists():raise ValueError('No source snapshots')
    registry=ROOT/'scripts/verify-current-contracts.ps1'
    previous={r['path']:r['sha256'] for r in json.loads((HERE/'trial-source.json').read_text())['files']}
    if sha(registry)!=previous['scripts/verify-current-contracts.ps1']:raise ValueError('Registry changed outside this task')
    scratch=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
    snapshot=scratch/str(OWNERS.index('scripts/verify-current-contracts.ps1'))
    original={r['path']:r['sha256'] for r in json.loads((HERE/'original-source.json').read_text())['files']}
    if snapshot.is_symlink() or sha(snapshot)!=original['scripts/verify-current-contracts.ps1']:raise ValueError('Registry snapshot changed')
    base='PcbPostSelectionEdgeTrimContractTest';target='PcbPostSelectionEdgeTrimTargetContractTest';copy='PcbPostSelectionEdgeTrimCopyContractTest';graph='PcbPostSelectionEdgeTrimGraphContractTest'
    for name in (base,target,copy,graph):(ROOT/'tests/contracts'/f'{name}.java').write_bytes((HERE/f'{name}.java').read_bytes())
    names=[target,copy,graph,base]
    partitions=json.loads((HERE/'proposal.json').read_text())['partitions']
    for index,seeds in enumerate(partitions[1:],2):
        name=base+'Group'+str(index);names.append(name)
        data=('package com.lushprojects.circuitjs1.client;\npublic final class '+name+' { public static void main(String[] args) { '+base+'.runSeeds(new long[]{'+','.join(s+'L' for s in seeds)+'}, "PCB post-selection edge trim Group'+str(index)+' "); } }\n').encode()
        (HERE/f'{name}.java').write_bytes(data);(ROOT/'tests/contracts'/f'{name}.java').write_bytes(data)
    p=registry;raw=snapshot.read_bytes();eol=b'\r\n' if b'\r\n' in raw else b'\n';anchor=b"        @{ Name = 'PcbCanvasOwnerBoundsContractTest';"
    if raw.count(anchor)!=1:raise ValueError('Registry anchor')
    entries=eol.join(("        @{ Name = '"+n+"'; Marker = 'PCB post-selection edge trim "+('target ' if n==target else 'copy ' if n==copy else 'graph ' if n==graph else '')+"' },").encode() for n in names)+eol
    p.write_bytes(raw.replace(anchor,entries+anchor));save('focused-plan.json',{'names':names})
    save('trial-source.json',{'files':[{'path':p,'sha256':sha(ROOT/p)} for p in OWNERS]})
    print('PREPARED explicit native15; default/replay14 retained; ten unique serial suites')
def prepare():
    prepare_source();register()
def restore():
    rows=json.loads((HERE/'original-source.json').read_text())['files']
    scratch=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
    if scratch.parent!=Path(tempfile.gettempdir()).resolve() or not scratch.name.startswith('tsj-edge-trim-') or scratch.is_symlink():raise ValueError('Snapshot containment')
    for i,r in enumerate(rows):
        p=scratch/str(i)
        if p.is_symlink() or sha(p)!=r['sha256']:raise ValueError('Snapshot changed')
        (ROOT/r['path']).write_bytes(p.read_bytes())
    retired=0
    for archived in HERE.glob('PcbPostSelectionEdgeTrim*.java'):
        active=ROOT/'tests/contracts'/archived.name
        if not active.exists():continue
        preserved=[archived]+[p/archived.name for p in HERE.glob('attempt*') if (p/archived.name).is_file()]
        if sha(active) not in {sha(p) for p in preserved}:raise ValueError('Active diagnostic changed outside archived task versions')
        active.unlink();retired+=1
    for i,r in enumerate(rows):
        if sha(ROOT/r['path'])!=r['sha256']:raise ValueError('Restore failed')
        (scratch/str(i)).unlink()
    scratch.rmdir();(HERE/'local-state.json').unlink()
    save('byte-restoration.json',{'status':'PASS','originalOwnerCount':len(rows),'activeDiagnosticsRetired':retired,'ownedSnapshotRemoved':True})
    print('RESTORED exact originals; retired '+str(retired)+' verified active contracts; removed task snapshots')
if __name__=='__main__':{'source':prepare_source,'register':register,'prepare':prepare,'restore':restore}[sys.argv[1]]()
