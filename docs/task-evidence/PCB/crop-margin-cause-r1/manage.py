"""Register isolated developer exports; no production algorithm changes."""
import hashlib,json,sys,tempfile
from pathlib import Path
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[3]
REGISTRY=ROOT/'scripts/verify-current-contracts.ps1'
ORIGINAL='9b6ad0000a75c92b19ffaeeb30f6564ce7085202cc87bb151a17ff9592401ef3'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(name,obj):(HERE/name).write_bytes((json.dumps(obj,indent=2)+'\n').encode())
def prepare():
    if (HERE/'local-state.json').exists() or sha(REGISTRY)!=ORIGINAL:raise ValueError('Preparation boundary')
    scratch=Path(tempfile.mkdtemp(prefix='tsj-crop-margin-'))
    (scratch/'registry').write_bytes(REGISTRY.read_bytes())
    save('local-state.json',{'scratch':str(scratch)})
    base='PcbCropMarginCauseContractTest';names=[base]
    (ROOT/'tests/contracts'/f'{base}.java').write_bytes((HERE/f'{base}.java').read_bytes())
    for suffix,seeds in [('Second','14L,4L'),('Last','6L,42L')]:
        name=base+suffix;names.append(name)
        data=('package com.lushprojects.circuitjs1.client;\npublic final class '+name+' { public static void main(String[] args) { '+base+'.runSeeds(new long[]{'+seeds+'}, "PCB crop margin cause '+suffix+' "); } }\n').encode()
        (HERE/f'{name}.java').write_bytes(data);(ROOT/'tests/contracts'/f'{name}.java').write_bytes(data)
    raw=REGISTRY.read_bytes();eol=b'\r\n' if b'\r\n' in raw else b'\n';anchor=b"        @{ Name = 'PcbCanvasOwnerBoundsContractTest';"
    if raw.count(anchor)!=1:raise ValueError('Registry anchor')
    entries=eol.join(("        @{ Name = '"+n+"'; Marker = 'PCB crop margin cause "+(['','Second ','Last '][i])+"' },").encode() for i,n in enumerate(names))+eol
    REGISTRY.write_bytes(raw.replace(anchor,entries+anchor));save('focused-plan.json',{'names':names})
    print('PREPARED three developer diagnostics; production unchanged')
def restore():
    scratch=Path(json.loads((HERE/'local-state.json').read_text())['scratch']).resolve()
    if scratch.parent!=Path(tempfile.gettempdir()).resolve() or not scratch.name.startswith('tsj-crop-margin-') or scratch.is_symlink():raise ValueError('Snapshot containment')
    p=scratch/'registry'
    if p.is_symlink() or sha(p)!=ORIGINAL:raise ValueError('Registry snapshot')
    REGISTRY.write_bytes(p.read_bytes())
    for archived in HERE.glob('PcbCropMarginCauseContractTest*.java'):
        active=ROOT/'tests/contracts'/archived.name
        if sha(active)!=sha(archived):raise ValueError('Active exporter changed')
        active.unlink()
    p.unlink();scratch.rmdir();(HERE/'local-state.json').unlink()
    save('byte-restoration.json',{'status':'PASS','registrySha256':sha(REGISTRY),'activeDiagnosticsRetired':3,'ownedSnapshotRemoved':True})
    print('RESTORED original registry; verified active diagnostics and snapshot removed')
if __name__=='__main__':{'prepare':prepare,'restore':restore}[sys.argv[1]]()
