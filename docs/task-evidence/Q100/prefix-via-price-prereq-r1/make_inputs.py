import hashlib,json,stat,sys
from pathlib import Path
Q=Path(__file__).resolve().parent
def sha(b):return hashlib.sha256(b).hexdigest()
def ref(path):
    p=Path(path).resolve(strict=True)
    for n in (p,*p.parents):
        s=n.lstat()
        assert not stat.S_ISLNK(s.st_mode) and not getattr(s,'st_file_attributes',0)&0x400
    b=p.read_bytes();return dict(path=str(p),bytes=len(b),sha256=sha(b))
def main():
    assert sys.dont_write_bytecode and len(sys.argv)==6, 'Expected REPOSITORY BASE_MANIFEST BRIDGE_ROOT OVERLAY_ROOT NEW_OUTPUT'
    W=Path(sys.argv[1]).resolve(strict=True)
    BASE=Path(sys.argv[2]).resolve(strict=True)
    OLD=Path(sys.argv[3]).resolve(strict=True)
    overlay=Path(sys.argv[4]).resolve(strict=True);out=Path(sys.argv[5])
    import tempfile
    temp=Path(tempfile.gettempdir()).resolve(strict=True)
    assert out.is_absolute() and not out.exists() and out.parent.is_dir()
    assert out.parent.resolve().is_relative_to(temp) and not out.parent.resolve().is_relative_to(W), 'Output must be external OS temp'
    modes={m:{role:str((overlay/m/name).resolve(strict=True)) for role,name in (
        ('router','PcbLayerRoutingPrototype.java'),('support','PrefixViaRampSupport.java'),('canary','PrefixViaRampCanary.java'))} for m in ('BASE','RAMP')}
    paths=[BASE,Q/'run-canary.ps1',Q/'audit_inputs.py',Q/'PLAN_R1.md',Q/'PHASE1_BOUNDARY_R1.md',
           overlay/'overlay-manifest.json',OLD/'scratch-bridge-inputs.json']
    paths.extend(Path(v) for m in modes.values() for v in m.values())
    paths.extend(OLD/('bridge-'+n) for n in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java'))
    paths.extend(Q/'source'/n for n in ('prepare_overlay.py','PrefixViaRampSupport.java','PrefixViaRampCanary.java'))
    rows=[ref(p) for p in paths]
    assert len({p['path'].casefold() for p in rows})==len(rows)
    data=dict(schema='tsj-prefix-via-ramp-inputs-v1',repositoryRoot=str(W),baseManifestPath=str(BASE),
      baseManifestSha256=sha(BASE.read_bytes()),planPath=str(Q/'PLAN_R1.md'),
      planSha256=sha((Q/'PLAN_R1.md').read_bytes()),boundaryPath=str(Q/'PHASE1_BOUNDARY_R1.md'),
      boundarySha256=sha((Q/'PHASE1_BOUNDARY_R1.md').read_bytes()),preflight=str(Q/'audit_inputs.py'),
      overlayManifestPath=str(overlay/'overlay-manifest.json'),overlayManifestSha256=sha((overlay/'overlay-manifest.json').read_bytes()),
      bridgeRoot=str(OLD),bridgeManifest=str(OLD/'scratch-bridge-inputs.json'),modeFiles=modes,files=rows)
    assert data['baseManifestSha256']=='8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab'
    assert data['planSha256']=='2d640b3d36cca2ae2613718e356e84ed603abf3ccf813f4a1372f35b28e72bce'
    assert data['boundarySha256']=='9fae9c7ab6e2404c9aa157122f4c0e331830766de336fc3ff6dfd0b36b6ca68f'
    b=(json.dumps(data,indent=2)+'\n').encode('utf-8')
    with out.open('xb') as f:f.write(b)
    print(json.dumps(dict(status='FROZEN_INPUT_PINS_ONLY',files=len(rows),sha256=sha(b),path=str(out))))
if __name__=='__main__':main()
