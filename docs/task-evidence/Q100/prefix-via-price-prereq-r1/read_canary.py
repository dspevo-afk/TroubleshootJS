"""Strict acceptance for the private Phase1 canary host receipts."""
import hashlib,json,math,stat,sys
from pathlib import Path

SCHEMA='tsj-prefix-via-ramp-canary-v1'
GATES=('price','literalPath','realBranch','zeroCapGuard','saturatedCapGuard','copyGate','sourceUnchanged','captureOutput')
NATIVE_KEYS={'schema','mode','assertions',*GATES,'copyPrefixHoles','copyMaximumVias','baseViaCost','sourceBranchPrice','copiedBase60Price','copiedRaisedPrice','priceRows','authoredRun','phase2','scope','layoutQualification','canaryWorkElapsedNanos'}
class Rejected(ValueError):pass
def need(ok,why):
    if not ok:raise Rejected(why)
def sha(b):return hashlib.sha256(b).hexdigest()
def pairs(items):
    out={}
    for k,v in items:
        need(k not in out,'DUPLICATE_JSON_KEY');out[k]=v
    return out
def invalid_constant(v):raise Rejected('NONFINITE_JSON')
def parse(raw):
    return json.loads(raw,object_pairs_hook=pairs,parse_constant=invalid_constant)
def read(path,maximum=20*1024*1024):
    p=Path(path)
    for q in (p,*p.parents):
        s=q.lstat()
        need(not stat.S_ISLNK(s.st_mode) and not getattr(s,'st_file_attributes',0)&0x400,'REPARSE_PATH')
    a=p.stat();need(stat.S_ISREG(a.st_mode) and a.st_size<=maximum,'FILE_KIND_OR_SIZE')
    b=p.read_bytes();z=p.stat()
    need((a.st_size,a.st_mtime_ns)==(z.st_size,z.st_mtime_ns),'FILE_CHANGED')
    return b
def number(v,maximum,name):
    need(type(v) in (int,float) and math.isfinite(v) and 0<=v<=maximum,'NUMBER_'+name);return v
def integer(v,maximum,name,minimum=0):
    need(type(v) is int and minimum<=v<=maximum,'INTEGER_'+name);return v
def json_file(root,name):
    return parse(read(Path(root)/name))
def process(p,success):
    need(type(p) is dict and p.get('TerminationProven') is True,'PROCESS_TERMINATION')
    need(type(p.get('ExitCode')) is int and ((p['ExitCode']==0) if success else (p['ExitCode']!=0)),'PROCESS_EXIT')
    need(type(p.get('Stdout')) is str and type(p.get('Stderr')) is str,'PROCESS_STREAMS')
    integer(p.get('ProcessId'),2**32-1,'PROCESS_ID',1)
    ticks=p.get('ProcessStartTicks')
    need(type(ticks) is int and 0<ticks<=2**63-1,'PROCESS_START')
    return p
def validate_native(body,mode):
    need(type(body) is dict and set(body)==NATIVE_KEYS,'NATIVE_FIELDS')
    need(body['schema']==SCHEMA and body['mode']==mode and mode in ('BASE','RAMP'),'NATIVE_CONTEXT')
    for gate in GATES:need(body[gate]=='PASS','NATIVE_GATE_'+gate)
    integer(body['assertions'],10000000,'ASSERTIONS',1)
    need(body['copyPrefixHoles']==2 and type(body['copyPrefixHoles']) is int,'COPY_PREFIX')
    need(body['copyMaximumVias']==48 and type(body['copyMaximumVias']) is int,'COPY_CAP')
    expected=60 if mode=='BASE' else 62
    for k,v in (('baseViaCost',60),('sourceBranchPrice',expected),('copiedBase60Price',expected),('copiedRaisedPrice',120)):
        need(type(body[k]) is int and body[k]==v,'PRICE_'+k)
    integer(body['priceRows'],4096,'PRICE_ROWS',1)
    integer(body['canaryWorkElapsedNanos'],5000000000,'CANARY_WORK_NANOS')
    need(body['authoredRun']=='NOT_RUN' and body['phase2']=='NOT_RUN','NO_AUTHORED_CLAIM')
    need(body['scope']=='PREFIX_VIA_PRICE_PREREQUISITE_ONLY' and body['layoutQualification'] is False,'SCOPE')
    return body
def validate_run(run_root,pins_path,expected_pins_sha,mode):
    root=Path(run_root)
    rawpins=read(pins_path,1024*1024);need(sha(rawpins)==expected_pins_sha,'PINS_HASH')
    pins=parse(rawpins);need(pins['schema']=='tsj-prefix-via-ramp-inputs-v1','PINS_SCHEMA')
    files={r['path']:r for r in pins['files']};need(len(files)==len(pins['files']),'DUP_INPUT_REF')
    for path,row in files.items():
        b=read(path,4*1024*1024)
        need(len(b)==row['bytes'] and sha(b)==row['sha256'],'INPUT_CLOSE_HASH')
    launch=json_file(root,'host-launch.json');host=json_file(root,'host-result.json')
    need(launch['schema']=='tsj-prefix-via-ramp-host-v1' and host['schema']=='tsj-prefix-via-ramp-host-result-v1','HOST_SCHEMA')
    need(host['status']=='HOST_COMPLETE_READER_PENDING' and launch['status']=='LAUNCHED_NOT_ACCEPTED','HOST_STATUS')
    for obj in (launch,host):
        need(obj['mode']==mode and obj['stage']=='CANARY' and obj['inputPinsSha256']==expected_pins_sha,'HOST_CONTEXT')
    need(host['runId']==launch['runId'],'RUN_BINDING')
    need(launch['script'] in files and launch['scriptSha256']==files[launch['script']]['sha256'],'HOST_SOURCE_PIN')
    need(host['sourceClosure']=='PASS' and host['scratchRemoved'] is True and host['nativeCaptureLimitNegative']=='PASS','HOST_FINAL_PROOF')
    need(host['compilerSources']==1076 and type(host['compilerSources']) is int,'COMPILER_CENSUS')
    need(host['compilerExitCode']==0 and host['compilerTerminationProven'] is True and host['nativeExitCode']==0 and host['nativeTerminationProven'] is True,'HOST_PROCESS_PROOFS')
    nativewall=number(host['nativeWallMs'],90000,'NATIVE_WALL')
    negwall=number(host['captureNegativeWallMs'],15000+120000,'NEGATIVE_WALL')
    post=number(host['postSourceAndScratchCleanupMs'],120000,'POST_AND_SCRATCH')
    upper=number(host['cleanupConservativeUpperMs'],120000,'CLEANUP_UPPER')
    need(abs(upper-(nativewall+negwall+post))<=0.001,'CLEANUP_UPPER_ACCOUNTING')
    number(host['outerElapsedMs'],1800000,'OUTER')
    need(host['cleanupBoundMs']==120000,'CLEANUP_BOUND')
    need(not Path(launch['scratch']).exists(),'SCRATCH_STILL_PRESENT')
    compiler=process(json_file(root,'compiler-process.json'),True)
    ci=json_file(root,'compiler-launch-intent.json')
    need(ci['status']=='INTENT_NOT_START_PROOF' and ci['sha256']=='61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64' and ci['limitMs']==60000,'COMPILER_INTENT')
    need(compiler['Arguments']==ci['arguments'] and Path(compiler['FilePath'])==Path(ci['executable']),'COMPILER_ACTUAL_ARGUMENTS')
    need(compiler['ProcessId']==host['compilerProcessId'] and str(compiler['ProcessStartTicks'])==host['compilerProcessStartTicks'],'COMPILER_PROCESS_BINDING')
    need(Path(ci['sourceListPath'])==Path(launch['scratch'])/'sources.txt' and ci['sourceListSha256']==sha(read(root/'compiler-sources.txt',1024*1024)),'COMPILER_SOURCE_LIST_BINDING')
    for name in ('preflight-process.json','pre-native-process.json','post-source-process.json'):
        p=process(json_file(root,name),True);need(p['Stderr']=='','SOURCE_AUDIT_STDERR')
        result=parse(p['Stdout'])
        need(result['status']=='PASS_PRIVATE_OVERLAY_INPUT_CLOSURE_ONLY' and result['baseFiles']==1084 and result['extraFiles']==len(files) and result['pinsSha256']==expected_pins_sha,'SOURCE_AUDIT_RESULT')
    native=process(json_file(root,'native-process.json'),True)
    need(native['ProcessId']==host['nativeProcessId'] and str(native['ProcessStartTicks'])==host['nativeProcessStartTicks'],'NATIVE_PROCESS_BINDING')
    need(native['Stderr']=='','NATIVE_STDERR')
    stream=native['Stdout']
    total=len(stream.encode('utf-8'))+len(native['Stderr'].encode('utf-8'))
    need(total<=8388608 and host['nativeOutputBytes']==total,'NATIVE_CAPTURE_BYTES')
    need(stream.endswith('\n') and '\x00' not in stream,'NATIVE_EOF')
    lines=stream.splitlines()
    need(len(lines)==1 and lines[0].startswith('PASS_PREFIX_VIA_RAMP_CANARY '),'NATIVE_LINE_CLOSURE')
    need(len(lines[0].encode('utf-8'))<=65536,'NATIVE_LINE_BYTES')
    body=validate_native(parse(lines[0].split(' ',1)[1]),mode)
    neg=process(json_file(root,'native-capture-limit-negative.json'),False)
    need(neg['Stdout']=='' and 'PREFIX_VIA_RAMP_CAPTURE_LIMIT' in neg['Stderr'] and len(neg['Stderr'].encode('utf-8'))<=8388608,'ACTUAL_CAPTURE_NEGATIVE')
    intent=json_file(root,'native-launch-intent.json');negative_intent=json_file(root,'native-capture-negative-intent.json')
    for obj in (intent,negative_intent):
        need(obj['status']=='INTENT_NOT_START_PROOF' and obj['sha256']=='e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce','JAVA_INTENT')
    a=intent['arguments'];n=negative_intent['arguments']
    need(len(a)==4 and a[:2]==['-ea','-cp'] and a[-1]=='com.lushprojects.circuitjs1.client.PrefixViaRampCanary','NATIVE_ARGUMENTS')
    need(n==a+['--capture-overflow'],'CAPTURE_NEGATIVE_ARGUMENTS')
    need(native['Arguments']==a and neg['Arguments']==n and Path(native['FilePath'])==Path(intent['executable']) and Path(neg['FilePath'])==Path(negative_intent['executable']),'ACTUAL_PROCESS_ARGUMENT_BINDING')
    need(ci['arguments']==['-source','7','-target','7','-encoding','UTF-8','-classpath',a[2],'-sourcepath',str(Path(launch['scratch'])/'empty-sourcepath'),'-d',str(Path(launch['scratch'])/'classes'),'@'+str(Path(launch['scratch'])/'sources.txt')],'COMPILER_EXACT_INVOCATION')
    base=parse(read(pins['baseManifestPath'],4*1024*1024))
    need(sha(read(pins['baseManifestPath'],4*1024*1024))==pins['baseManifestSha256'],'BASE_MANIFEST_CLOSE')
    repo=Path(pins['repositoryRoot'])
    excluded={'src/com/lushprojects/circuitjs1/client/'+s for s in ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java','PcbLayerRoutingPrototype.java')}
    expected=[repo/r['path'] for r in base['files'] if r['role']=='BASE_SOURCE' and r['path'].endswith('.java') and r['path'] not in excluded]
    expected.extend(Path(pins['modeFiles'][mode][r]) for r in ('router','support','canary'))
    bridgecopies=json_file(root,'consumed-bridge-copies.json');need(len(bridgecopies)==3,'BRIDGE_COPY_CENSUS')
    bridges=parse(read(pins['bridgeManifest'],65536))
    need(sha(read(pins['bridgeManifest'],65536))=='e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b','BRIDGE_MANIFEST')
    for actual,want in zip(bridgecopies,bridges):
        need(Path(actual['path'])==Path(launch['scratch'])/want['path'] and actual['bytes']==want['bytes'] and actual['sha256']==want['sha256'],'BRIDGE_COPY_PIN')
        expected.append(Path(actual['path']))
    paths=read(root/'compiler-sources.txt',1024*1024).decode('utf-8').splitlines()
    need(all(x.startswith('"') and x.endswith('"') for x in paths),'COMPILER_PATH_SYNTAX')
    actual=[Path(x[1:-1]) for x in paths]
    need(len(expected)==1076 and actual==expected and len(set(actual))==1076,'COMPILER_EXACT_INPUTS')
    need(read(pins_path,1024*1024)==rawpins,'PINS_CHANGED')
    return dict(status='PASS_PHASE1_CANARY_RECEIPTS_ONLY',mode=mode,inputPinsSha256=expected_pins_sha,
      native=body,nativeWallMs=nativewall,postSourceAndScratchCleanupMs=post,cleanupConservativeUpperMs=upper,
      authoredRun='NOT_RUN',phase2='NOT_RUN',layoutQualification=False)
def main():
    need(len(sys.argv)==5,'USAGE: read_canary.py RUN_ROOT INPUT_PINS SHA256 BASE_OR_RAMP')
    result=validate_run(*sys.argv[1:]);print(json.dumps(result,sort_keys=True))
if __name__=='__main__':main()
