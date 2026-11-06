import hashlib,json,os,stat,sys
from pathlib import Path
BASE='98c19ae62429a1b7806ebdc7cdb977ec817297b731a2a6f9e52897439db2c11a'
PARENT='83027040b3314b3131ae1d315db58f51a3c4231d'
CONTROL_PINS=dict(planSha256='6128ea850b934a36864c6be048419c520afb942c380911186ba0c2d26bdbf05c',protocolSha256='d03ab20bf2736f1c023498fcbdd13c6e76099080bf226f5da74def0a242d5e94',addendumSha256='0c8607e6b7e57c80224ba57a5d69a09bf461a538418caf290986f455e9205dda',cutInputSha256='1a1d7739d3a8ce4409896b56477c68f38b12b8d77f0d0264e16b0b3cf1bf70c5',p4QualificationSha256='0cc91c276bba8b6e1de867a732d9f34d3cea78dcadb28250ef99a3948687ce53')
DELTA={'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java'}
def need(ok,code):
 if not ok:raise ValueError(code)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def parse(raw):
 def pairs(rows):
  out={}
  for k,v in rows:need(k not in out,'DUP_JSON');out[k]=v
  return out
 return json.loads(raw,object_pairs_hook=pairs)
def read(path,limit=104857600):
 p=Path(os.path.abspath(path))
 for q in (p,*p.parents):
  st=q.lstat();need(not stat.S_ISLNK(st.st_mode) and not getattr(st,'st_file_attributes',0)&0x400,'REPARSE')
 before=p.stat();need(stat.S_ISREG(before.st_mode) and before.st_size<=limit,'FILE_SIZE')
 def key(s):return s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_nlink
 with p.open('rb') as stream:
  opened=os.fstat(stream.fileno());raw=stream.read(limit+1);closed=os.fstat(stream.fileno())
 after=p.stat()
 need(key(before)==key(opened)==key(closed)==key(after) and before.st_ctime_ns==after.st_ctime_ns and opened.st_ctime_ns==closed.st_ctime_ns and len(raw)==after.st_size<=limit,'CHANGED')
 return raw
def main():
 need(sys.dont_write_bytecode and len(sys.argv)==6,'USAGE_ROOT_BASE_BASESHA_PINS_PINSSHA')
 root,base_path,base_sha,pins_path,pins_sha=sys.argv[1:];root=Path(root)
 need(base_sha==BASE,'BASE_PIN');base_raw=read(base_path,4194304);need(sha(base_raw)==BASE,'BASE_HASH')
 raw=read(pins_path,4194304);need(sha(raw)==pins_sha,'PINS_HASH');pins=parse(raw);base=parse(base_raw)
 need(pins['schema']=='tsj-canonical-prefix-blocker-inputs-v1' and Path(pins['repositoryRoot'])==root and pins['parent']==PARENT,'INPUT_CONTEXT')
 need(base['schema']=='tsj-prefix-blocker-source-baseline-v1' and base['parent']==PARENT and Path(base['repositoryRoot'])==Path(pins['historicalRoot']),'BASE_CONTEXT')
 need(Path(pins['baselineManifestPath'])==Path(base_path) and pins['baselineManifestSha256']==BASE,'BASE_PATH_CONTEXT')
 for k,h in CONTROL_PINS.items():need(pins[k]==h,'CONTROL_PIN_'+k)
 expected={r['path']:r for r in base['files']};rows={r['path']:r for r in pins['sourceRows']}
 need(len(expected)==len(rows)==len(pins['sourceRows'])==1084 and set(expected)==set(rows),'SOURCE_CENSUS')
 deltas={r['path']:r for r in pins['changedRows']};need(len(pins['changedRows'])==1 and set(deltas)==DELTA,'DELTA_CENSUS')
 changed=set();prod=0
 for name,before in expected.items():
  current=rows[name];need(current['role']==before['role'],'ROLE_CHANGED')
  a=read(Path(pins['historicalRoot'])/name);need(len(a)==before['bytes'] and sha(a)==before['sha256'],'HISTORICAL_HASH:'+name)
  b=read(root/name);need(len(b)==current['bytes'] and sha(b)==current['sha256'],'CURRENT_HASH:'+name)
  prod+=name.startswith('src/')
  if (current['bytes'],current['sha256'])!=(before['bytes'],before['sha256']):
   changed.add(name);need(name in DELTA,'UNEXPECTED_SOURCE_DELTA')
   need(deltas[name]['before']==dict(bytes=before['bytes'],sha256=before['sha256']) and deltas[name]['after']==dict(bytes=current['bytes'],sha256=current['sha256']),'DELTA_REF')
 need(changed==DELTA and prod==850,'EXACT_SOURCE_BOUNDARY')
 p4_root=Path(pins['p4QualificationPath']).parent
 expected_transport=p4_root/'capture-r2/data/fixed-prefix.jsonl'
 refs={}
 for row in pins['files']:
  path=Path(row['path']);need(path.is_absolute() and str(path)==os.path.abspath(path),'ABSOLUTE_PIN')
  key=os.path.normcase(str(path));need(key not in refs,'DUP_PIN');refs[key]=row
  maximum=8388608 if path==expected_transport else 4194304
  b=read(path,maximum);need(len(b)==row['bytes'] and sha(b)==row['sha256'],'EXTRA_HASH:'+str(path))
 def pinned(path,digest=None):
  key=os.path.normcase(os.path.abspath(path));need(key in refs,'UNBOUND_INPUT')
  need(digest is None or refs[key]['sha256']==digest,'BOUND_HASH')
 for key in ('baselineManifestPath','preflight','planPath','protocolPath','addendumPath','cutInputPath','bridgeManifest','parentControlReceipt','p4QualificationPath','p4ReaderPath'):pinned(pins[key])
 need(Path(pins['preflight'])==Path(__file__).resolve(),'AUDITOR_IDENTITY')
 for prefix in ('plan','protocol','addendum','cutInput','p4Qualification'):pinned(pins[prefix+'Path'],pins[prefix+'Sha256'])
 modes=pins['modeFiles'];need(set(modes)=={'router','support','writer','adapter','inspector','queries'},'MODE_KEYS')
 for role,path in modes.items():pinned(path)
 need(Path(modes['writer'])==root/next(iter(DELTA)),'WRITER_PATH')
 need(Path(modes['adapter'])==Path(__file__).resolve().parent/'source/CanonicalPrefixCaptureStudy.java','ADAPTER_IDENTITY')
 need(Path(modes['inspector'])==Path(__file__).resolve().parent/'source/CanonicalPrefixBlockerInspector.java','INSPECTOR_IDENTITY')
 need(Path(modes['queries'])==Path(__file__).resolve().parent/'source/CanonicalPrefixCutQueries.java','QUERIES_IDENTITY')
 pinned(modes['adapter'],'b5370fa281b34f3e4c36a5b5ae652e75a8fcdac0ad33489ece4d4bc488cd6c4a')
 pinned(modes['queries'],'4effc3deb4808feb18b295f368a2f2c5df4c312c4da17e77ad74daf2bdf8b401')
 pinned(modes['inspector'],'ce71efd9cf16c00e9747f6759b64406b8f2b342ea2e93e5961c91afd9c12e297')
 pinned(modes['writer'],'264c89e58e0e29b3375520459d627d945ec28cd747065647181ce118d75dd9f6')
 pinned(modes['router'],'70cacaacc97cb3f01a1ad6221753e4d80a05479ba6663cd5dc6474a00dc4fb21')
 pinned(modes['support'],'e35c512dfe10234bdf099b7f74642113dff3c97cc8365d71b80a09729fb8e48a')
 pinned(pins['bridgeManifest'],'e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b')
 for name in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java'):pinned(Path(pins['bridgeRoot'])/('bridge-'+name))
 q=parse(read(pins['p4QualificationPath'],4194304));copies={row['path']:row for row in q['publishedCopies']}
 p4=Path(pins['p4QualificationPath']).parent
 need(Path(pins['p4ReaderPath'])==p4/'read_capture.py','P4_READER_IDENTITY')
 for name in ('read_capture.py','oracles-r1/read_fixed_prefix_graph.py','oracles-r1/fixed_prefix_minimum_vias.py','oracles-r1/read_fixed_prefix_capture.py'):pinned(p4/name,copies[name]['sha256'])
 control=[row for row in q['caseRuns'] if row['mode']=='CONTROL'];need(len(control)==1,'P4_CONTROL_CENSUS')
 pinned(pins['parentControlReceipt'],control[0]['operationReceipt']['sha256'])
 need(Path(pins['p4InputPinsPath'])==p4/'input-pins-r4.json' and pins['p4InputPinsSha256']=='ca06e1ab3c1a7bcb11dab90cba7b64dc9d95b031b09d76678f408d9fa0f0f7e0','P4_INPUT_IDENTITY')
 pinned(pins['p4InputPinsPath'],pins['p4InputPinsSha256'])
 need(Path(pins['p4CaptureRunRoot'])==p4/'capture-r2','P4_CAPTURE_ROOT')
 capture=[row for row in q['caseRuns'] if row['mode']=='CAPTURE'];need(len(capture)==1,'P4_CAPTURE_CENSUS')
 pinned(p4/'capture-r2/data/op-1.jsonl',capture[0]['operationReceipt']['sha256'])
 pinned(expected_transport,q['captureTransport']['sha256'])
 index=p4/'capture-r2/data/canonical-prefix-index.json';pinned(index)
 need(parse(read(index,65536))==capture[0]['index'],'P4_CAPTURE_INDEX')
 pinned(root/'tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py','a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e')
 need(read(pins_path,4194304)==raw and read(base_path,4194304)==base_raw,'CLOSE_CHANGED')
 print(json.dumps(dict(status='PASS_CANONICAL_PREFIX_BLOCKER_INPUT_CLOSURE_ONLY',sourceRows=1084,changedRows=1,extraFiles=len(refs),pinsSha256=pins_sha),sort_keys=True))
if __name__=='__main__':main()
