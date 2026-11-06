import hashlib,json,os,stat,sys
from pathlib import Path
BASE='8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab'
CONTROL_PINS=dict(planSha256='cdfae32ed6e569b76fb529f247a3cede8384ca6ea4eb748b924fa6a4c4db1962',protocolSha256='8b7a58e17c53460bae642bb7a564f39d8383e87a2c2e3dc7960b1ae44aaeaeec',addendumSha256='ac159a550890c7a785106d01e983b863d6aba07ca2b2414b8a679503d2d7f28f')
DELTA={'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java','tests/contracts/Rb100PhysicalDiagnostics.java'}
def need(v,m):
 if not v:raise ValueError(m)
def sha(b):return hashlib.sha256(b).hexdigest()
def parse(b):
 def pairs(xs):
  d={}
  for k,v in xs:need(k not in d,'DUP_JSON');d[k]=v
  return d
 return json.loads(b,object_pairs_hook=pairs)
def read(path,limit=104857600):
 p=Path(os.path.abspath(path))
 for q in (p,*p.parents):
  s=q.lstat();need(not stat.S_ISLNK(s.st_mode) and not getattr(s,'st_file_attributes',0)&0x400,'REPARSE')
 a=p.stat();need(stat.S_ISREG(a.st_mode) and a.st_size<=limit,'FILE_SIZE')
 def key(s):return s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_nlink
 with p.open('rb') as f:
  x=os.fstat(f.fileno());b=f.read(limit+1);y=os.fstat(f.fileno())
 z=p.stat();need(key(a)==key(x)==key(y)==key(z) and a.st_ctime_ns==z.st_ctime_ns and x.st_ctime_ns==y.st_ctime_ns and len(b)==z.st_size<=limit,'CHANGED')
 return b
def main():
 need(sys.dont_write_bytecode and len(sys.argv)==6,'USAGE_ROOT_BASE_BASESHA_PINS_PINSSHA')
 root,base_path,base_sha,pins_path,pins_sha=sys.argv[1:];root=Path(root)
 need(base_sha==BASE,'BASE_PIN');rawbase=read(base_path,4194304);need(sha(rawbase)==BASE,'BASE_HASH')
 raw=read(pins_path,4194304);need(sha(raw)==pins_sha,'PINS_HASH');p=parse(raw);old=parse(rawbase)
 need(p['schema']=='tsj-canonical-prefix-inputs-v1' and Path(p['repositoryRoot'])==root,'INPUT_CONTEXT')
 need(Path(p['baselineManifestPath'])==Path(base_path) and p['baselineManifestSha256']==BASE and Path(old['repositoryRoot'])==Path(p['historicalRoot']),'BASE_CONTEXT')
 for k,h in CONTROL_PINS.items():need(p[k]==h,'CONTROL_PIN_'+k)
 expected={r['path']:r for r in old['files']};rows={r['path']:r for r in p['sourceRows']}
 need(len(expected)==len(rows)==1084 and len(p['sourceRows'])==1084 and set(expected)==set(rows),'SOURCE_CENSUS')
 changed=set();changedfacts={r['path']:r for r in p['changedRows']};need(len(p['changedRows'])==2 and set(changedfacts)==DELTA,'DELTA_CENSUS')
 for name,before in expected.items():
  current=rows[name];need(current['role']==before['role'],'ROLE_CHANGED')
  a=read(Path(p['historicalRoot'])/name);need(len(a)==before['bytes'] and sha(a)==before['sha256'],'HISTORICAL_HASH:'+name)
  b=read(root/name);need(len(b)==current['bytes'] and sha(b)==current['sha256'],'CURRENT_HASH:'+name)
  if current['bytes']!=before['bytes'] or current['sha256']!=before['sha256']:
   changed.add(name);need(name in DELTA and not name.startswith('src/'),'UNEXPECTED_SOURCE_DELTA')
   need(changedfacts[name]['before']==dict(bytes=before['bytes'],sha256=before['sha256']) and changedfacts[name]['after']==dict(bytes=current['bytes'],sha256=current['sha256']),'DELTA_REF')
 need(changed==DELTA,'EXACT_DELTA')
 refs={}
 for row in p['files']:
  path=Path(row['path']);need(path.is_absolute() and str(path)==os.path.abspath(path),'ABSOLUTE_PIN')
  k=os.path.normcase(str(path));need(k not in refs,'DUP_PIN');refs[k]=row
  b=read(path,4194304);need(len(b)==row['bytes'] and sha(b)==row['sha256'],'EXTRA_HASH:'+str(path))
 def pinned(path,h=None):
  k=os.path.normcase(os.path.abspath(path));need(k in refs,'UNBOUND_INPUT');need(h is None or refs[k]['sha256']==h,'BOUND_HASH')
 for key in ('baselineManifestPath','preflight','planPath','protocolPath','addendumPath','bridgeManifest','parentControlReceipt'):pinned(p[key])
 need(Path(p['preflight'])==Path(__file__).resolve(),'AUDITOR_IDENTITY')
 for key,sha_key in (('planPath','planSha256'),('protocolPath','protocolSha256'),('addendumPath','addendumSha256')):pinned(p[key],p[sha_key])
 need(set(p['modeFiles'])=={'router','support','writer','adapter'},'MODE_KEYS')
 for path in p['modeFiles'].values():
  if Path(path)==root/'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java':continue
  pinned(path)
 need(Path(p['modeFiles']['writer'])==root/'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java','WRITER_PATH')
 pinned(p['modeFiles']['router'],'70cacaacc97cb3f01a1ad6221753e4d80a05479ba6663cd5dc6474a00dc4fb21')
 pinned(p['modeFiles']['support'],'e35c512dfe10234bdf099b7f74642113dff3c97cc8365d71b80a09729fb8e48a')
 need(Path(p['modeFiles']['adapter'])==Path(__file__).resolve().parent/'source/CanonicalPrefixCaptureStudy.java','ADAPTER_IDENTITY')
 pinned(p['modeFiles']['adapter'],'228960930c2578435a9686cb9347d0694ef955c4bae15c60646d20aa4d9650c4')
 pinned(p['bridgeManifest'],'e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b')
 for n in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java'):pinned(Path(p['bridgeRoot'])/('bridge-'+n))
 pinned(p['parentControlReceipt'],'108b66d6ff45111e6dee2be0e912af4089cce0a6463a2e5c6358d42096c2bf4f')
 need(read(pins_path,4194304)==raw and read(base_path,4194304)==rawbase,'CLOSE_CHANGED')
 print(json.dumps(dict(status='PASS_CANONICAL_PREFIX_INPUT_CLOSURE_ONLY',sourceRows=1084,changedRows=2,extraFiles=len(refs),pinsSha256=pins_sha),sort_keys=True))
if __name__=='__main__':main()
