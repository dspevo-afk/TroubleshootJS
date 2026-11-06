from pathlib import Path
import hashlib,importlib.util,json,os,re,stat,sys
BASE='f94eca24bac4b24cc264fe4ca5038c68cbbf54b8530bf529ed3ec66e48ecf019'
PARENT='831d69a41d9504bfb845caef6942e9ef58c9d6d2'
ROLES=('router','support','writer','adapter','blocker','queries','netRouterOverlay','stageInspector')
CONTROL=dict(planSha256='31547c7531d5c950d305f2010bf3cabfbe56fd709498953298d8d31e8bcab68f',protocolSha256='4b795d7784afa14eaf95000f48ae4144ee57621c14773026acb27b6f98f697ec',addendumSha256='3da49dcbc8dbb1950e5a875a5bf04c00b0829d60e61f2ef7e294a4fc4295abc2',zeroDeltaInputSha256='089167366a28d11f4342e07af94db109f3bb8655afd968ad5262c2c75b3a3b6f',fullCutInputSha256='1a1d7739d3a8ce4409896b56477c68f38b12b8d77f0d0264e16b0b3cf1bf70c5',p5InputPinsSha256='1e570d555c919755d82e5e0f0c728425c7aa313c3bd2c123ebfd874832a7b636',p5QualificationSha256='301fca39736eca2b21c40b8dc92ca01e0c58b45493b103d3a8d3bee53624922e')
def need(ok,why):
 if not ok:raise ValueError(why)
def sha(raw):return hashlib.sha256(raw).hexdigest()
def parse(raw):
 def pairs(rows):
  out={}
  for key,value in rows:need(key not in out,'DUP_JSON');out[key]=value
  return out
 return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda value:(_ for _ in ()).throw(ValueError('NONFINITE_JSON')))
def read(path,limit=104857600):
 path=Path(os.path.abspath(path))
 for item in (path,*path.parents):
  st=item.lstat();need(not stat.S_ISLNK(st.st_mode) and not getattr(st,'st_file_attributes',0)&0x400,'REPARSE')
 before=path.stat();need(stat.S_ISREG(before.st_mode) and before.st_size<=limit,'FILE_SIZE')
 def key(s):return s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns,s.st_nlink
 with path.open('rb') as stream:opened=os.fstat(stream.fileno());raw=stream.read(limit+1);closed=os.fstat(stream.fileno())
 after=path.stat();need(key(before)==key(opened)==key(closed)==key(after) and before.st_ctime_ns==after.st_ctime_ns and opened.st_ctime_ns==closed.st_ctime_ns and len(raw)==after.st_size<=limit,'CHANGED')
 return raw
def main():
 need(sys.dont_write_bytecode and len(sys.argv)==6,'USAGE_ROOT_BASE_BASESHA_PINS_PINSSHA')
 root,base_path,base_sha,pins_path,pins_sha=sys.argv[1:];root=Path(root);P=Path(__file__).resolve().parent
 need(base_sha==BASE,'BASE_PIN');base_raw=read(base_path,4194304);need(sha(base_raw)==BASE,'BASE_HASH');base=parse(base_raw)
 raw=read(pins_path,4194304);need(sha(raw)==pins_sha,'PINS_HASH');pins=parse(raw)
 need(pins['schema']=='tsj-canonical-prefix-stage-inputs-v1' and Path(pins['repositoryRoot'])==root and pins['parent']==PARENT,'INPUT_CONTEXT')
 need(base['schema']=='tsj-prefix-rejection-source-baseline-v1' and base['parent']==PARENT and Path(pins['historicalRoot'])==Path(base['repositoryRoot']),'BASE_CONTEXT')
 need(pins['baselineManifestSha256']==BASE and Path(pins['baselineManifestPath'])==Path(base_path),'BASE_PATH_CONTEXT')
 for name,digest in CONTROL.items():need(pins[name]==digest,'CONTROL_PIN_'+name)
 need(type(pins['changedRows']) is list and not pins['changedRows'],'ZERO_SOURCE_DELTA')
 need(len(pins['sourceRows'])==len(base['files'])==1084 and pins['sourceRows']==base['files'],'SOURCE_CENSUS')
 prod=0
 for row in pins['sourceRows']:
  need(type(row['bytes']) is int and 0<=row['bytes']<=104857600 and type(row['sha256']) is str and re.fullmatch('[0-9a-f]{64}',row['sha256']) is not None,'SOURCE_ROW_TYPES')
  need(row['path'] and not Path(row['path']).is_absolute() and '..' not in row['path'].split('/'),'SOURCE_PATH')
  for source in (root/row['path'],Path(pins['historicalRoot'])/row['path']):
   data=read(source);need(len(data)==row['bytes'] and sha(data)==row['sha256'],'SOURCE_HASH:'+row['path'])
  prod+=row['path'].startswith('src/')
 need(prod==850,'PRODUCTION_CENSUS')
 refs={}
 for row in pins['files']:
  need(type(row['bytes']) is int and 0<=row['bytes']<=8388608 and type(row['sha256']) is str and re.fullmatch('[0-9a-f]{64}',row['sha256']) is not None,'EXTRA_ROW_TYPES')
  path=Path(row['path']);key=os.path.normcase(str(path));need(path.is_absolute() and str(path)==os.path.abspath(path) and key not in refs,'PIN_PATH')
  data=read(path,8388608);need(len(data)==row['bytes'] and sha(data)==row['sha256'],'EXTRA_HASH:'+str(path));refs[key]=row
 def pinned(path,digest=None):
  key=os.path.normcase(os.path.abspath(path));need(key in refs,'UNBOUND_INPUT')
  if digest:need(refs[key]['sha256']==digest,'BOUND_HASH')
 for name in ('baselineManifestPath','preflight','planPath','protocolPath','addendumPath','zeroDeltaInputPath','fullCutInputPath','bridgeManifest','p5InputPinsPath','p5QualificationPath','p5ReaderPath','p5ControlReceipt'):pinned(pins[name])
 for stem in ('plan','protocol','addendum','zeroDeltaInput','fullCutInput','p5InputPins','p5Qualification'):pinned(pins[stem+'Path'],pins[stem+'Sha256'])
 need(Path(pins['preflight'])==Path(__file__).resolve(),'AUDITOR_IDENTITY');need(set(pins['modeFiles'])==set(ROLES),'MODE_KEYS')
 for path in pins['modeFiles'].values():pinned(path)
 p5root=Path(pins['p5Root']);p5=parse(read(pins['p5InputPinsPath'],4194304));q=parse(read(pins['p5QualificationPath'],4194304))
 need(Path(pins['p5InputPinsPath'])==p5root/'input-pins-r7.json' and Path(pins['p5QualificationPath'])==p5root/'qualification-r7.json' and q['status']=='PASS_FINITE_CUT_ATTRIBUTION_ONLY','P5_CONTEXT')
 need(Path(pins['p5ReaderPath'])==p5root/'read_blockers.py','P5_READER_PATH')
 copies={row['path']:row for row in q['publishedCopies']};pinned(pins['p5ReaderPath'],copies['read_blockers.py']['sha256'])
 need(Path(pins['p5CaptureRunRoot'])==p5root/'capture-r3' and Path(pins['p5ControlReceipt'])==p5root/'control-r3/data/op-1.jsonl','P5_RUN_PATHS')
 for row in p5['files']:pinned(row['path'],row['sha256'])
 for role in ('router','support','queries'):need(pins['modeFiles'][role]==p5['modeFiles'][role],'P5_MODE_'+role)
 need(pins['modeFiles']['blocker']==p5['modeFiles']['inspector'],'P5_BLOCKER_PATH')
 need(Path(pins['modeFiles']['writer'])==root/'tests/qualification/rb100-layout/java/ActualGeneratorBranchOrderStudy.java','WRITER_PATH')
 need(Path(pins['modeFiles']['adapter'])==P/'source/CanonicalPrefixStageStudy.java' and Path(pins['modeFiles']['stageInspector'])==P/'source/CanonicalPrefixStageInspector.java' and Path(pins['modeFiles']['netRouterOverlay'])==P/'overlays-r1/PcbNetRouter.java','P6_ROLE_PATHS')
 for name in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java'):pinned(Path(pins['bridgeRoot'])/('bridge-'+name))
 need(pins['bridgeRoot']==p5['bridgeRoot'] and pins['bridgeManifest']==p5['bridgeManifest'],'P5_BRIDGE_CONTEXT')
 builder_path=P/'build_overlay.py';pinned(builder_path);pinned(P/'overlays-r1/manifest-r1.json');pinned(P/'overlays-r1/PcbNetRouter.diff')
 spec=importlib.util.spec_from_file_location('p6_pinned_overlay_builder',builder_path);module=importlib.util.module_from_spec(spec);sys.modules[spec.name]=module;spec.loader.exec_module(module)
 source=read(root/'src/com/lushprojects/circuitjs1/client/PcbNetRouter.java',2097152)
 need(len(source)==129081 and sha(source)=='92347537ca59629f91d6a72596cd59d6520cff2deebb3681f22d6d9ec82c21ec','ROUTER_SOURCE_PIN')
 overlay=read(pins['modeFiles']['netRouterOverlay'],2097152)
 need(module.make_overlay(source)==overlay,'EXACT_OVERLAY_TRANSFORM')
 manifest=parse(read(P/'overlays-r1/manifest-r1.json',65536));diff=read(P/'overlays-r1/PcbNetRouter.diff',2097152)
 need(manifest['source']['bytes']==len(source) and manifest['source']['sha256']==sha(source) and manifest['overlay']['bytes']==len(overlay) and manifest['overlay']['sha256']==sha(overlay),'OVERLAY_MANIFEST_SOURCE')
 need(manifest['diff']['bytes']==len(diff) and manifest['diff']['sha256']==sha(diff) and manifest['builder']['sha256']==sha(read(builder_path,2097152)) and manifest['addendum']['sha256']==CONTROL['addendumSha256'],'OVERLAY_MANIFEST_BOUNDARY')
 need(manifest['nonWhitelistedBytesIdentical'] is True and manifest['routerBoundsAndEndpointLoopByteIdentical'] is True and manifest['observerFieldCopiedToFixedGraphCopies'] is False and manifest['predicateOrderAndSingleEvaluation']==['isLegalMove','canTraverse','canOccupy'],'OVERLAY_MANIFEST_PROPERTIES')
 need(read(pins_path,4194304)==raw and read(base_path,4194304)==base_raw,'CLOSE_CHANGED')
 print(json.dumps(dict(status='PASS_CANONICAL_PREFIX_STAGE_INPUT_CLOSURE_ONLY',sourceRows=1084,changedRows=0,extraFiles=len(refs),pinsSha256=pins_sha),sort_keys=True))
if __name__=='__main__':main()
