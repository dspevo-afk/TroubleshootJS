from pathlib import Path
import hashlib,json,os,stat,sys,tempfile
P=Path(__file__).resolve().parent
P5=P.parent/'canonical-prefix-blockers-r1'
BASE='f94eca24bac4b24cc264fe4ca5038c68cbbf54b8530bf529ed3ec66e48ecf019'
P5_INPUT='1e570d555c919755d82e5e0f0c728425c7aa313c3bd2c123ebfd874832a7b636'
P5_QUAL='301fca39736eca2b21c40b8dc92ca01e0c58b45493b103d3a8d3bee53624922e'
def sha(raw):return hashlib.sha256(raw).hexdigest()
def ref(path):
 path=Path(path).resolve(strict=True)
 for item in (path,*path.parents):
  st=item.lstat();assert not stat.S_ISLNK(st.st_mode) and not getattr(st,'st_file_attributes',0)&0x400
 raw=path.read_bytes();return dict(path=str(path),bytes=len(raw),sha256=sha(raw))
def main():
 assert sys.dont_write_bytecode and len(sys.argv)==4,'Usage make_inputs.py REPOSITORY NEW_OUTPUT_JSON NULL_RUN_ROOT'
 repository=Path(sys.argv[1]).resolve(strict=True);out=Path(sys.argv[2]);null_root=Path(sys.argv[3])
 assert out.is_absolute() and null_root.is_absolute() and not out.exists()
 temp=Path(tempfile.gettempdir()).resolve();assert out.parent.resolve().is_relative_to(temp) and null_root.parent.resolve().is_relative_to(temp)
 base_path=P/'source-baseline-r1.json';raw=base_path.read_bytes();assert sha(raw)==BASE;base=json.loads(raw)
 p5pins_path=P5/'input-pins-r7.json';raw=p5pins_path.read_bytes();assert sha(raw)==P5_INPUT;p5=json.loads(raw)
 qpath=P5/'qualification-r7.json';raw=qpath.read_bytes();assert sha(raw)==P5_QUAL;q=json.loads(raw)
 assert q['status']=='PASS_FINITE_CUT_ATTRIBUTION_ONLY'
 sources=[]
 for row in base['files']:
  current=ref(repository/row['path']);assert (current['bytes'],current['sha256'])==(row['bytes'],row['sha256'])
  sources.append(dict(row))
 assert len(sources)==1084 and sum(row['path'].startswith('src/') for row in sources)==850
 modes=dict(router=p5['modeFiles']['router'],support=p5['modeFiles']['support'],writer=str((repository/'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java').resolve()),adapter=str((P/'source/CanonicalPrefixStageStudy.java').resolve()),blocker=p5['modeFiles']['inspector'],queries=p5['modeFiles']['queries'],netRouterOverlay=str((P/'overlays-r1/PcbNetRouter.java').resolve()),stageInspector=str((P/'source/CanonicalPrefixStageInspector.java').resolve()))
 refs={}
 def add(path,expected=None):
  row=ref(path);key=os.path.normcase(row['path'])
  if expected:assert row['bytes']==expected['bytes'] and row['sha256']==expected['sha256']
  if key in refs:assert refs[key]==row
  else:refs[key]=row
 for row in p5['files']:add(row['path'],row)
 for path in (base_path,p5pins_path,qpath,P/'PLAN_R1.md',P/'PROTOCOL_R1.md',P/'ADDENDUM_R1.md',P/'ADDENDUM_R2.md',P/'SOURCE_AUDIT_R1.md',P/'zero-delta-input-r1.json',P/'audit_inputs.py',P/'make_inputs.py',P/'run-native.ps1',P/'read_stages.py',P/'test_read_stages.py',P/'build_overlay.py',P/'overlays-r1/manifest-r1.json',P/'overlays-r1/PcbNetRouter.diff'):
  add(path)
 for path in modes.values():add(path)
 root_names=('host-launch.json','host-result.json','preflight-process.json','pre-native-process.json','post-source-process.json','compiler-launch-intent.json','compiler-process.json','compiler-sources.txt','consumed-bridge-copies.json','native-launch-intent.json','native-process.json','native-capture-negative-intent.json','native-capture-limit-negative.json')
 for name,data_names in [('control-r3',('op-1.jsonl','canonical-blocker-index.json')),('capture-r3',('op-1.jsonl','canonical-blocker-index.json','fixed-prefix.jsonl','prefix-blockers.json'))]:
  for filename in root_names:add(P5/name/filename)
  for filename in data_names:add(P5/name/'data'/filename)
 data=dict(schema='tsj-canonical-prefix-stage-inputs-v1',repositoryRoot=str(repository),historicalRoot=base['repositoryRoot'],parent=base['parent'],baselineManifestPath=str(base_path.resolve()),baselineManifestSha256=BASE,sourceRows=sources,changedRows=[],modeFiles=modes,bridgeRoot=p5['bridgeRoot'],bridgeManifest=p5['bridgeManifest'],preflight=str((P/'audit_inputs.py').resolve()),nullRunRoot=str(null_root.absolute()),p5Root=str(P5.resolve()),p5InputPinsPath=str(p5pins_path.resolve()),p5InputPinsSha256=P5_INPUT,p5QualificationPath=str(qpath.resolve()),p5QualificationSha256=P5_QUAL,p5ReaderPath=str((P5/'read_blockers.py').resolve()),p5CaptureRunRoot=str((P5/'capture-r3').resolve()),p5ControlReceipt=str((P5/'control-r3/data/op-1.jsonl').resolve()),files=list(refs.values()))
 for stem,path in [('plan',P/'PLAN_R1.md'),('protocol',P/'PROTOCOL_R1.md'),('addendum',P/'ADDENDUM_R2.md'),('zeroDeltaInput',P/'zero-delta-input-r1.json'),('fullCutInput',P5/'cut-input-r1.json')]:
  data[stem+'Path']=str(path.resolve());data[stem+'Sha256']=sha(path.read_bytes())
 raw=(json.dumps(data,indent=2)+'\n').encode()
 with out.open('xb') as stream:stream.write(raw)
 print(json.dumps(dict(status='INPUTS_FROZEN_NOT_NATIVE_ACCEPTANCE',sourceRows=1084,changedRows=0,extraFiles=len(refs),sha256=sha(raw))))
if __name__=='__main__':main()
