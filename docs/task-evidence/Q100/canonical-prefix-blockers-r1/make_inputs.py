from pathlib import Path
import hashlib,json,os,stat,sys,tempfile
P=Path(__file__).resolve().parent
def sha(raw):return hashlib.sha256(raw).hexdigest()
def ref(path):
 path=Path(path).resolve(strict=True)
 for parent in (path,*path.parents):
  st=parent.lstat();assert not stat.S_ISLNK(st.st_mode) and not getattr(st,'st_file_attributes',0)&0x400
 raw=path.read_bytes();return dict(path=str(path),bytes=len(raw),sha256=sha(raw))
def main():
 assert sys.dont_write_bytecode and len(sys.argv) in (3,4),'Usage make_inputs.py REPOSITORY NEW_ABSOLUTE_OUTPUT [--producer-canary-only]'
 preliminary=len(sys.argv)==4
 assert not preliminary or sys.argv[3]=='--producer-canary-only'
 repository=Path(sys.argv[1]).resolve(strict=True);out=Path(sys.argv[2]);Q=P.parent
 base_path=P/'source-baseline-r1.json';raw=base_path.read_bytes()
 assert sha(raw)=='98c19ae62429a1b7806ebdc7cdb977ec817297b731a2a6f9e52897439db2c11a'
 base=json.loads(raw);historical=Path(base['repositoryRoot']);sources=[];changes=[]
 for before in base['files']:
  row=dict(before);current=ref(repository/row['path']);row.update(bytes=current['bytes'],sha256=current['sha256']);sources.append(row)
  if (row['bytes'],row['sha256'])!=(before['bytes'],before['sha256']):
   changes.append(dict(path=row['path'],before={k:before[k] for k in ('bytes','sha256')},after={k:row[k] for k in ('bytes','sha256')}))
 assert len(changes)==1 and changes[0]['path']=='tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java'
 overlay=Q/'prefix-via-ramp-r1/overlays-r5/BASE'
 bridge=Q/'outf-graph-r1/host-prep-r1/host-authored-v6-r2/driver'
 P4=Q/'canonical-prefix-capture-r1'
 modes=dict(router=str((overlay/'PcbLayerRoutingPrototype.java').resolve()),support=str((overlay/'PrefixViaRampSupport.java').resolve()),
  writer=str((repository/'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java').resolve()),
  adapter=str((P/'source/CanonicalPrefixCaptureStudy.java').resolve()),
  inspector=str((P/'source/CanonicalPrefixBlockerInspector.java').resolve()),
  queries=str((P/'source/CanonicalPrefixCutQueries.java').resolve()))
 paths=[base_path,P/'PLAN_R1.md',P/'PROTOCOL_R1.md',P/'ADDENDUM_R2.md',P/'cut-input-r1.json',
  P/'audit_inputs.py',P/'make_inputs.py',P/'run-native.ps1',P/'read_blockers.py',P/'test_read_blockers.py',
  bridge/'scratch-bridge-inputs.json',P4/'qualification-r4.json',P4/'read_capture.py',P4/'control-r3/data/op-1.jsonl',
  repository/'tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py',
  P4/'input-pins-r4.json',P4/'capture-r2/data/canonical-prefix-index.json',
  P4/'capture-r2/data/op-1.jsonl',P4/'capture-r2/data/fixed-prefix.jsonl']
 paths.extend(Path(value) for value in modes.values())
 paths.extend(bridge/('bridge-'+name) for name in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java'))
 paths.extend(P4/'oracles-r1'/name for name in ('read_fixed_prefix_graph.py','fixed_prefix_minimum_vias.py','read_fixed_prefix_capture.py'))
 if preliminary:paths=[path for path in paths if path not in (P/'read_blockers.py',P/'test_read_blockers.py')]
 files=[ref(path) for path in paths];assert len({os.path.normcase(row['path']) for row in files})==len(files)
 data=dict(schema='tsj-canonical-prefix-blocker-inputs-v1',repositoryRoot=str(repository),historicalRoot=str(historical),parent=base['parent'],
  baselineManifestPath=str(base_path.resolve()),baselineManifestSha256=sha(raw),sourceRows=sources,changedRows=changes,
  modeFiles=modes,bridgeRoot=str(bridge.resolve()),bridgeManifest=str((bridge/'scratch-bridge-inputs.json').resolve()),
  preflight=str((P/'audit_inputs.py').resolve()),controlRunRoot=str((P/'control-r3').resolve()),
  parentControlReceipt=str((P4/'control-r3/data/op-1.jsonl').resolve()),
  p4ReaderPath=str((P4/'read_capture.py').resolve()),
  p4InputPinsPath=str((P4/'input-pins-r4.json').resolve()),
  p4InputPinsSha256='ca06e1ab3c1a7bcb11dab90cba7b64dc9d95b031b09d76678f408d9fa0f0f7e0',
  p4CaptureRunRoot=str((P4/'capture-r2').resolve()),files=files)
 for name,key in [('PLAN_R1.md','plan'),('PROTOCOL_R1.md','protocol'),('ADDENDUM_R2.md','addendum'),('cut-input-r1.json','cutInput')]:
  data[key+'Path']=str((P/name).resolve());data[key+'Sha256']=sha((P/name).read_bytes())
 data['p4QualificationPath']=str((P4/'qualification-r4.json').resolve());data['p4QualificationSha256']=sha((P4/'qualification-r4.json').read_bytes())
 if preliminary:data['prerequisiteOnly']=True
 assert out.is_absolute() and out.parent.resolve().is_relative_to(Path(tempfile.gettempdir()).resolve()) and not out.exists()
 encoded=(json.dumps(data,indent=2)+'\n').encode()
 with out.open('xb') as stream:stream.write(encoded)
 print(json.dumps(dict(status='INPUTS_FROZEN_NOT_NATIVE_ACCEPTANCE',sourceRows=len(sources),changedRows=len(changes),extraFiles=len(files),sha256=sha(encoded))))
if __name__=='__main__':main()
