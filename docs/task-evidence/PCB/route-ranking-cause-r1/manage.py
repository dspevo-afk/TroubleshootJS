"""Prepare exact archived diagnostic and restore byte snapshots; no tuning."""
import hashlib, importlib.util, json, subprocess, sys, tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
OLD=HERE.parent/'soft-isolation-advice-r1'
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def save(name, data): (HERE/name).write_bytes((json.dumps(data,indent=2)+'\n').encode())
def originals(): return json.loads((OLD/'original-source.json').read_text(encoding='utf-8-sig'))['files']
def prepare():
    if (HERE/'local-state.json').exists(): raise ValueError('Existing preparation')
    rows=originals()
    for r in rows:
        if sha(ROOT/r['path'])!=r['sha256']: raise ValueError('Original mismatch '+r['path'])
    scratch=Path(tempfile.mkdtemp(prefix='tsj-route-cause-'))
    for i,r in enumerate(rows): (scratch/str(i)).write_bytes((ROOT/r['path']).read_bytes())
    # This local receipt is never published; contains the owned OS-temp path.
    save('local-state.json',{'scratch':str(scratch),'files':rows})
    subprocess.run([sys.executable,'-X','utf8','-B',str(OLD/'apply_trial.py')],check=True)
    expected=json.loads((OLD/'byte-restoration.json').read_text(encoding='utf-8-sig'))['files']
    for r in expected:
        if r['path'].startswith('src/') and sha(ROOT/r['path'])!=r['before']: raise ValueError('Trial mismatch')
    test='PcbRouteRankingCauseContractTest'
    (ROOT/'tests/contracts'/f'{test}.java').write_bytes((HERE/f'{test}.java').read_bytes())
    names=[test]
    for suffix,seeds in [('Second','14L,4L'),('Last','6L')]:
        name=test+suffix
        data=('package com.lushprojects.circuitjs1.client;\npublic final class '+name+' { public static void main(String[] args) { '+test+'.runSeeds(new long[]{'+seeds+'}, "PCB route ranking cause '+suffix+' "); } }\n').encode()
        (HERE/f'{name}.java').write_bytes(data)
        (ROOT/'tests/contracts'/f'{name}.java').write_bytes(data)
        names.append(name)
    p=ROOT/'scripts/verify-current-contracts.ps1'; raw=p.read_bytes(); eol=b'\r\n' if b'\r\n' in raw else b'\n'
    anchor=b"        @{ Name = 'PcbCanvasOwnerBoundsContractTest';"
    if raw.count(anchor)!=1: raise ValueError('Registry anchor')
    entries=eol.join(("        @{ Name = '"+n+"'; Marker = 'PCB route ranking cause "+(['','Second ','Last '][i])+"' },").encode() for i,n in enumerate(names))+eol
    p.write_bytes(raw.replace(anchor,entries+anchor))
    save('focused-plan.json',{'names':names})
    print('PREPARED exact archived trial and three diagnostic JVM partitions')
def restore():
    state=json.loads((HERE/'local-state.json').read_text()); scratch=Path(state['scratch']).resolve()
    if scratch.parent!=Path(tempfile.gettempdir()).resolve() or not scratch.name.startswith('tsj-route-cause-') or scratch.is_symlink(): raise ValueError('Scratch containment')
    for i,r in enumerate(state['files']):
        p=scratch/str(i)
        if sha(p)!=r['sha256']: raise ValueError('Snapshot mismatch')
        (ROOT/r['path']).write_bytes(p.read_bytes())
    for p in HERE.glob('PcbRouteRankingCauseContractTest*.java'):
        active=ROOT/'tests/contracts'/p.name
        if sha(active)!=sha(p): raise ValueError('Diagnostic active changed')
        active.unlink()
    for i,r in enumerate(state['files']):
        if sha(ROOT/r['path'])!=r['sha256']: raise ValueError('Restoration failed')
        (scratch/str(i)).unlink()
    scratch.rmdir(); (HERE/'local-state.json').unlink()
    save('byte-restoration.json',{'status':'PASS','ownerCount':len(state['files']),'activeDiagnosticsRetired':3,'ownedSnapshotRemoved':True})
    print('RESTORED original bytes and removed verified task-owned snapshots')
if __name__=='__main__': {'prepare':prepare,'restore':restore}[sys.argv[1]]()
