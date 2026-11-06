import hashlib,json,os,stat,sys,tempfile
from pathlib import Path
P=Path(__file__).resolve().parent
def sha(b):return hashlib.sha256(b).hexdigest()
def ref(p):
 p=Path(p).resolve(strict=True)
 for q in (p,*p.parents):
  s=q.lstat();assert not stat.S_ISLNK(s.st_mode) and not getattr(s,'st_file_attributes',0)&0x400
 b=p.read_bytes();return dict(path=str(p),bytes=len(b),sha256=sha(b))
def main():
 assert sys.dont_write_bytecode and len(sys.argv)==3,'Usage make_inputs.py V NEW_OUTPUT'
 V=Path(sys.argv[1]).resolve(strict=True);out=Path(sys.argv[2]);Q=P.parent
 baseline=Q/'outf-graph-r1/host-prep-r1/native-study-authored-v6-r2/source-manifest.json'
 bridges=Q/'outf-graph-r1/host-prep-r1/host-authored-v6-r2/driver'
 old=json.loads(baseline.read_bytes());W=Path(old['repositoryRoot']);assert W.is_absolute()
 sources=[];deltas=[]
 for row in old['files']:
  item=dict(row);new=ref(V/row['path']);item.update(bytes=new['bytes'],sha256=new['sha256']);sources.append(item)
  if (item['bytes'],item['sha256'])!=(row['bytes'],row['sha256']):deltas.append(dict(path=row['path'],before={k:row[k] for k in ('bytes','sha256')},after={k:item[k] for k in ('bytes','sha256')}))
 overlay=Q/'prefix-via-ramp-r1/overlays-r5/BASE'
 modes=dict(router=str((overlay/'PcbLayerRoutingPrototype.java').resolve()),support=str((overlay/'PrefixViaRampSupport.java').resolve()),writer=str((V/'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java').resolve()),adapter=str((P/'source/CanonicalPrefixCaptureStudy.java').resolve()))
 paths=[baseline,bridges/'scratch-bridge-inputs.json',P/'PLAN_R1.md',P/'PROTOCOL_R1.md',P/'ADDENDUM_R1.md',P/'audit_inputs.py',P/'make_inputs.py',P/'run-native.ps1',P/'read_capture.py',P/'test_read_capture.py',P/'test_capture_controls.py',Path(modes['router']),Path(modes['support']),Path(modes['adapter']),Path(modes['writer']),V/'tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py',Q/'prefix-via-first-request-r1/read_first_request.py',Q/'prefix-via-first-request-r1/base-authored-r1/data/op-1.jsonl']
 paths.extend(bridges/('bridge-'+n) for n in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java'))
 paths.extend(P/'oracles-r1'/n for n in ('read_fixed_prefix_graph.py','fixed_prefix_minimum_vias.py','read_fixed_prefix_capture.py'))
 refs=[ref(x) for x in paths];assert len({x['path'].casefold() for x in refs})==len(refs)
 data=dict(schema='tsj-canonical-prefix-inputs-v1',repositoryRoot=str(V),historicalRoot=str(W),parent='96611058bc41f208a0de677bf06c677c909471e3',baselineManifestPath=str(baseline.resolve()),baselineManifestSha256=sha(baseline.read_bytes()),sourceRows=sources,changedRows=deltas,modeFiles=modes,bridgeRoot=str(bridges.resolve()),bridgeManifest=str((bridges/'scratch-bridge-inputs.json').resolve()),preflight=str((P/'audit_inputs.py').resolve()),controlRunRoot=str(P/'control-r3'),parentControlReceipt=str((Q/'prefix-via-first-request-r1/base-authored-r1/data/op-1.jsonl').resolve()),files=refs)
 for name,key in (('PLAN_R1.md','plan'),('PROTOCOL_R1.md','protocol'),('ADDENDUM_R1.md','addendum')):data[key+'Path']=str(P/name);data[key+'Sha256']=sha((P/name).read_bytes())
 assert out.is_absolute() and out.parent.resolve().is_relative_to(Path(tempfile.gettempdir()).resolve()) and not out.exists()
 b=(json.dumps(data,indent=2)+'\n').encode()
 with out.open('xb') as f:f.write(b)
 print(json.dumps(dict(status='INPUTS_FROZEN_NOT_NATIVE_ACCEPTANCE',sourceRows=len(sources),changedRows=len(deltas),extraFiles=len(refs),sha256=sha(b))))
if __name__=='__main__':main()
