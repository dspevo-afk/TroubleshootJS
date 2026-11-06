import hashlib,importlib.util,json,stat,sys
from pathlib import Path
def require(v,m):
    if not v:raise ValueError(m)
def sha(b):return hashlib.sha256(b).hexdigest()
def read(p,limit):
    p=Path(p)
    for q in (p,*p.parents):
        s=q.lstat();require(not stat.S_ISLNK(s.st_mode) and not getattr(s,'st_file_attributes',0)&0x400,'REPARSE:'+str(q))
    a=p.stat();require(a.st_size<=limit,'SIZE:'+str(p))
    b=p.read_bytes();z=p.stat();require((a.st_size,a.st_mtime_ns)==(z.st_size,z.st_mtime_ns),'CHANGED:'+str(p));return b
def main():
    require(sys.dont_write_bytecode,'BYTECODE_DISABLED_REQUIRED')
    root,base_path,base_sha,pins_path,pins_sha=sys.argv[1:]
    root=Path(root)
    require(base_sha=='8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab','BASE_PIN')
    base=read(base_path,4*1024*1024);require(sha(base)==base_sha,'BASE_HASH')
    raw=read(pins_path,1024*1024);require(sha(raw)==pins_sha,'INPUT_PINS_HASH')
    pins=json.loads(raw);require(pins['schema']=='tsj-via-price-first-request-inputs-v1','INPUT_SCHEMA')
    require(Path(pins['repositoryRoot'])==root,'ROOT')
    require(Path(pins['baseManifestPath'])==Path(base_path),'BASE_PATH')
    require(pins['baseManifestSha256']==base_sha,'PIN_BASE')
    require(pins['planSha256']=='571a288d8252ead69c5df528673d0c0cb8d2c27e2f653432aeb121969b86c531','PLAN_PIN')
    reader=root/'tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py'
    rb=read(reader,1024*1024);require(sha(rb)=='a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e','READER_HASH')
    spec=importlib.util.spec_from_file_location('frozen_source_audit_only',reader);r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
    m,w,refs=r.check_source_manifest(base,Path(base_path).parent)
    require(m['kind']=='AUTHORED' and Path(m['repositoryRoot'])==root,'BASE_CONTEXT')
    for ref,parent,maximum in refs:r.file_ref(ref,parent,maximum)
    require(len(m['files'])==1084 and sum(x['role']=='BASE_SOURCE' for x in m['files'])==1080,'BASE_CENSUS')
    require(pins['boundarySha256']=='429feb12df57037ecdb204b9f82e52a0e21e296faf8c0933486b995e1e7aa740','BOUNDARY_PIN')
    seen=set()
    for row in pins['files']:
        p=Path(row['path']);require(p.is_absolute() and str(p.resolve())==str(p),'PIN_CANONICAL_ABSOLUTE');require(str(p) not in seen,'DUP_PIN');seen.add(str(p))
        b=read(p,4*1024*1024);require(len(b)==row['bytes'] and sha(b)==row['sha256'],'EXTRA_HASH:'+str(p))
    require(set(pins['modeFiles'])=={'BASE','RAMP'},'MODE_KEYS')
    for mode,support_sha in (('BASE','e35c512dfe10234bdf099b7f74642113dff3c97cc8365d71b80a09729fb8e48a'),('RAMP','2f51a5dad184bf81ce3fbef9686c07dec8eb90d9270bb134a7f071c48c8cd43e')):
        require(sha(read(pins['modeFiles'][mode]['router'],1024*1024))=='70cacaacc97cb3f01a1ad6221753e4d80a05479ba6663cd5dc6474a00dc4fb21','QUALIFIED_ROUTER')
        require(sha(read(pins['modeFiles'][mode]['support'],65536))==support_sha,'QUALIFIED_SUPPORT')
        require(Path(pins['modeFiles'][mode]['writer'])==root/'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java','WRITER_IDENTITY')
        require(sha(read(pins['modeFiles'][mode]['writer'],1024*1024))=='6fc5c0b7a9a1826c79095f2b7bfb240e7b8af5bc0c392c67820bd0bffd5fb442','WRITER_HASH')
        require(Path(pins['modeFiles'][mode]['wrapper'])==Path(__file__).resolve().parent/'source/PrefixViaFirstRequestStudy.java','WRAPPER_IDENTITY')
        require(sha(read(pins['modeFiles'][mode]['wrapper'],1024*1024))=='3ad73a67e37d140d28fd394bd511406e1b336f7044c75f343d649386d100a6f8','WRAPPER_HASH')
    consumed=[pins[k] for k in ('baseManifestPath','preflight','planPath','boundaryPath','overlayManifestPath','bridgeManifest')]
    require(Path(pins['preflight'])==Path(__file__).resolve(),'PREFLIGHT_IDENTITY')
    for mode in ('BASE','RAMP'):
        require(set(pins['modeFiles'][mode])=={'router','support','writer','wrapper'},'MODE_SOURCE_KEYS')
        consumed.extend(pins['modeFiles'][mode].values())
    require(Path(pins['bridgeRoot']).is_absolute(),'BRIDGE_ROOT')
    consumed.extend(str(Path(pins['bridgeRoot'])/('bridge-'+n)) for n in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java'))
    for name in consumed:
        require(Path(name).is_absolute() and str(Path(name).resolve())==name and name in seen,'UNBOUND_CONSUMED_INPUT:'+name)
    for key,expected in (('planPath',pins['planSha256']),('boundaryPath',pins['boundarySha256']),('overlayManifestPath',pins['overlayManifestSha256']),('bridgeManifest','e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b')):
        require(sha(read(pins[key],4*1024*1024))==expected,'CONTROL_HASH:'+key)
    require(read(base_path,4*1024*1024)==base and read(pins_path,1024*1024)==raw and read(reader,1024*1024)==rb,'CLOSE_CHANGED')
    print(json.dumps(dict(status='PASS_PRIVATE_OVERLAY_INPUT_CLOSURE_ONLY',baseFiles=1084,extraFiles=len(seen),pinsSha256=pins_sha),sort_keys=True))
if __name__=='__main__':main()
