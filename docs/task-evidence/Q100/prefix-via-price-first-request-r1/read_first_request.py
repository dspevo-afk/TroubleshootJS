"""Independent one-request reader for the frozen Phase2 adapter."""
from __future__ import annotations
import hashlib, importlib.util, json, math, os, re, stat, sys, tempfile
from pathlib import Path

sys.dont_write_bytecode = True
SCHEMA = 'tsj-via-price-first-request-native-v1'
HOST = 'tsj-via-price-first-request-host-v1'
HOST_RESULT = 'tsj-via-price-first-request-host-result-v1'
INPUTS = 'tsj-via-price-first-request-inputs-v1'
INDEX = 'tsj-via-price-first-request-index-v1'
WIRE = '0bd76a4fe4261a8abe5c92b2b0b5f50000077e9541e7ca7e84e9af3ed1661cfb'
BASE_MANIFEST = '8bc48095905b2e34b3315653c12e54262a8ef6aa4770f9db2c8a59239d4f40ab'
OLD_READER = 'a314cdc732069bca17f73a724cd21295af334d9baead82fcdb13bf0600c5554e'
OLD_SOURCE = BASE_MANIFEST
OLD_INDEX = '42476676f5e1395bd8bfcf6a4ecbe36cef791c80af8e9720a1a17f55ecdbac20'
OLD_OP1 = '448a10dd766cf4d3dc59c5097d20b9e654ba0d48940470cf39e37ba148fb250b'
PLAN_SHA = '571a288d8252ead69c5df528673d0c0cb8d2c27e2f653432aeb121969b86c531'
PROTOCOL_SHA = '429feb12df57037ecdb204b9f82e52a0e21e296faf8c0933486b995e1e7aa740'
WRAPPER_SHA = '3ad73a67e37d140d28fd394bd511406e1b336f7044c75f343d649386d100a6f8'
PYTHON_SHA = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec'
LIMITS = dict(compileMs=60000, nativeMs=480000, cleanupMs=120000, outerMs=1800000,
              wrapperNanos=90000000000, groupNanos=5000000000, groups=640,
              receiptBytes=8388608, lineBytes=65536)
HEX = re.compile(r'[0-9a-f]{64}\Z')
UUID = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\Z')

class Rejected(ValueError): pass
def need(ok, why):
    if not ok: raise Rejected(why)
def sha(raw): return hashlib.sha256(raw).hexdigest()
def keys(v, want, why): need(type(v) is dict and set(v) == set(want), why)
def pairs(rows):
    out = {}
    for k, v in rows:
        need(k not in out, 'DUPLICATE_JSON_KEY'); out[k] = v
    return out
def parse(raw):
    need(type(raw) is bytes and not raw.startswith(b'\xef\xbb\xbf'), 'JSON_BOM_OR_TYPE')
    def integer(s): need(s != '-0', 'JSON_NEGATIVE_ZERO'); return int(s)
    def floating(s):
        v = float(s); need(math.isfinite(v), 'JSON_NONFINITE'); return v
    def constant(_): raise Rejected('JSON_NONFINITE')
    try: v = json.loads(raw.decode('utf-8', 'strict'), object_pairs_hook=pairs,
                        parse_int=integer, parse_float=floating, parse_constant=constant)
    except (UnicodeError, json.JSONDecodeError, ValueError) as e:
        if isinstance(e, Rejected): raise
        raise Rejected('JSON_PARSE') from e
    def strings(x):
        if type(x) is str: need(not any(0xd800 <= ord(c) <= 0xdfff for c in x), 'JSON_SURROGATE')
        elif type(x) is dict:
            for k, y in x.items(): strings(k); strings(y)
        elif type(x) is list:
            for y in x: strings(y)
    strings(v); return v
def no_reparse(path):
    p = Path(os.path.abspath(path))
    for q in (p, *p.parents):
        s = q.lstat()
        need(not stat.S_ISLNK(s.st_mode) and not getattr(s, 'st_file_attributes', 0) & 0x400,
             'REPARSE_PATH')
    return p
def stable_read(path, maximum=8*1024*1024):
    p = no_reparse(path); before = p.stat()
    need(stat.S_ISREG(before.st_mode) and before.st_size <= maximum, 'FILE_KIND_OR_SIZE')
    def shared(s): return s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns, s.st_nlink
    with p.open('rb') as f:
        opened = os.fstat(f.fileno()); raw = f.read(maximum+1); closed = os.fstat(f.fileno())
    after = p.stat()
    need(shared(before) == shared(opened) == shared(closed) == shared(after)
         and before.st_ctime_ns == after.st_ctime_ns and opened.st_ctime_ns == closed.st_ctime_ns
         and len(raw) == after.st_size and len(raw) <= maximum, 'FILE_CHANGED_OR_SIZE')
    no_reparse(p); return raw
def integer(v, lo=0, hi=2**63-1, name='INTEGER'):
    need(type(v) is int and lo <= v <= hi, name); return v
def number(v, hi, name, lo=0):
    need(type(v) in (int, float) and math.isfinite(v) and lo <= v <= hi, name); return v
def same_path(a, b):
    return os.path.normcase(os.path.normpath(str(a))) == os.path.normcase(os.path.normpath(str(b)))
def below(path, parent):
    p=Path(os.path.abspath(path)); q=Path(os.path.abspath(parent))
    return same_path(p,q) or any(same_path(q,q0) for q0 in p.parents)
def child(root, name):
    p = Path(os.path.abspath(Path(root) / name)); r = Path(os.path.abspath(root))
    need(p != r and r in p.parents, 'RUN_PATH_ESCAPE'); return p
def object_file(root, name, maximum=1024*1024): return parse(stable_read(child(root, name), maximum))
def process(p, success):
    need(type(p) is dict and type(p.get('TerminationProven')) is bool
         and p['TerminationProven'] is True, 'PROCESS_TERMINATION')
    need(type(p.get('ExitCode')) is int and ((p['ExitCode'] == 0) if success else p['ExitCode'] != 0),
         'PROCESS_EXIT')
    need(type(p.get('Stdout')) is str and type(p.get('Stderr')) is str, 'PROCESS_STREAMS')
    integer(p.get('ProcessId'), 1, 2**32-1, 'PROCESS_ID')
    integer(p.get('ProcessStartTicks'), 1, 2**63-1, 'PROCESS_START_TICKS')
    need(type(p.get('Arguments')) is list and all(type(x) is str for x in p['Arguments'])
         and type(p.get('FilePath')) is str, 'PROCESS_INVOCATION')
    return p
def validate_entry(e):
    keys(e, ('operation','armId','receipt','elapsedToCloseNanos','maxFullGroupNanos','cleanup'), 'ENTRY_FIELDS')
    need(type(e['operation']) is int and e['operation'] == 1 and e['armId'] == 'OBSERVED_DEFAULT_NEAREST',
         'ENTRY_IDENTITY')
    keys(e['receipt'], ('path','bytes','sha256'), 'RECEIPT_REF_FIELDS')
    need(e['receipt']['path'] == 'op-1.jsonl' and type(e['receipt']['sha256']) is str
         and HEX.fullmatch(e['receipt']['sha256']) is not None, 'RECEIPT_REF')
    integer(e['receipt']['bytes'], 1, LIMITS['receiptBytes'], 'RECEIPT_BYTES')
    integer(e['elapsedToCloseNanos'], 1, LIMITS['wrapperNanos'], 'OPERATION_NANOS')
    integer(e['maxFullGroupNanos'], 1, LIMITS['groupNanos'], 'MAX_GROUP_NANOS')
    c=e['cleanup']; keys(c, ('status','elapsedNanos','ownersReleased','contextRestored'), 'CLEANUP_FIELDS')
    need(c['status'] == 'PASS' and type(c['ownersReleased']) is bool and c['ownersReleased'] is True
         and type(c['contextRestored']) is bool and c['contextRestored'] is True, 'CLEANUP_PROOF')
    integer(c['elapsedNanos'], 0, 120_000_000_000, 'CLEANUP_NANOS')
    return e
def validate_index(index, raw, profile, kind, run_id, input_sha):
    keys(index, ('schema','profile','kind','runId','sourceManifestSha256','wireSha256',
                 'scope','layoutQualification','operations'), 'INDEX_FIELDS')
    need(index['schema'] == INDEX and index['profile'] == profile and index['kind'] == kind
         and index['runId'] == run_id and index['sourceManifestSha256'] == input_sha
         and index['wireSha256'] == WIRE and index['scope'] == 'ONE_CANONICAL_REQUEST_ONLY'
         and type(index['layoutQualification']) is bool and index['layoutQualification'] is False
         and type(index['operations']) is list and len(index['operations']) == 1, 'INDEX_IDENTITY')
    e=validate_entry(index['operations'][0])
    need(len(raw) <= 65536, 'INDEX_SIZE'); return e
def validate_wrapper_cleanup(wrapper, entry):
    need(wrapper['wrapper']['elapsedNanos']-wrapper['wrapper']['workNanos']
         ==entry['cleanup']['elapsedNanos'], 'WRAPPER_CLEANUP_ACCOUNTING')
def combined_group_count(operation_groups):
    n=integer(operation_groups,0,LIMITS['groups']-2,'OPERATION_GROUPS')
    need(n+2<=LIMITS['groups'],'COMBINED_GROUP_CAP'); return n+2
def validate_trailer(stdout, stderr, output_bytes, profile, kind, run_id, input_sha):
    need(type(stdout) is str and stderr == '' and type(stderr) is str, 'NATIVE_STREAMS')
    try: total=len(stdout.encode('utf-8'))+len(stderr.encode('utf-8'))
    except UnicodeError as e: raise Rejected('NATIVE_UTF8') from e
    need(total <= LIMITS['receiptBytes'] and type(output_bytes) is int and output_bytes == total,
         'NATIVE_CAPTURE_BYTES')
    need(stdout.endswith('\n') and stdout.count('\n') == 1 and '\r' not in stdout and '\x00' not in stdout,
         'TRAILER_LINE_CLOSURE')
    line=stdout[:-1]; need(len(line.encode('utf-8')) <= LIMITS['lineBytes'], 'TRAILER_LINE_SIZE')
    prefix='PREFIX_VIA_FIRST_REQUEST_COMPLETE '
    need(line.startswith(prefix), 'TRAILER_MARKER')
    body=parse(line[len(prefix):].encode('utf-8'))
    keys(body, ('schema','profile','kind','runId','sourceManifestSha256','wireSha256','descriptor',
                'wrapper','scope','layoutQualification'), 'TRAILER_FIELDS')
    need(body['schema'] == SCHEMA and body['profile'] == profile and body['kind'] == kind
         and body['runId'] == run_id and body['sourceManifestSha256'] == input_sha
         and body['wireSha256'] == WIRE and body['scope'] == 'ONE_CANONICAL_REQUEST_ONLY'
         and type(body['layoutQualification']) is bool and body['layoutQualification'] is False,
         'TRAILER_IDENTITY')
    d=body['descriptor']; keys(d, ('path','bytes','sha256'), 'DESCRIPTOR_FIELDS')
    need(d['path'] == 'one-arm-index.json' and type(d['sha256']) is str
         and HEX.fullmatch(d['sha256']) is not None, 'DESCRIPTOR_REF')
    integer(d['bytes'], 1, 65536, 'DESCRIPTOR_BYTES')
    w=body['wrapper']; keys(w, ('preparationNanos','postNanos','elapsedNanos','workNanos','groups'),
                             'WRAPPER_FIELDS')
    prep=integer(w['preparationNanos'],0,5_000_000_000,'PREPARATION_NANOS')
    post=integer(w['postNanos'],0,5_000_000_000,'POST_NANOS')
    elapsed=integer(w['elapsedNanos'],1,210_000_000_000,'WRAPPER_ELAPSED')
    work=integer(w['workNanos'],0,LIMITS['wrapperNanos'],'WRAPPER_WORK')
    need(type(w['groups']) is int and w['groups'] == 2 and prep+post <= work <= elapsed,
         'WRAPPER_ACCOUNTING')
    return body, total
def same_json(a,b):
    if type(a) is not type(b): return False
    if type(a) is dict: return set(a)==set(b) and all(same_json(a[k],b[k]) for k in a)
    if type(a) is list: return len(a)==len(b) and all(same_json(x,y) for x,y in zip(a,b))
    return a == b
def normalized_base(raw, reader):
    need(raw.endswith(b'\n') and b'\r' not in raw, 'BASE_RECEIPT_ENCODING')
    rows=[reader.strict_json(line[:-1]) for line in raw.splitlines(keepends=True)]
    need(rows and rows[0].get('record') == 'HEADER', 'BASE_HEADER')
    for i,row in enumerate(rows):
        if i == 0:
            row['runId']='<NORMALIZED_RUN_ID>'; row['sourceManifestSha256']='<NORMALIZED_INPUT_SHA>'
        elif row.get('record') == 'GROUP': row['elapsedBeforeOwnLineNanos']=0
    return rows
def compare_base(candidate, baseline, reader):
    need(same_json(normalized_base(candidate,reader), normalized_base(baseline,reader)),
         'BASE_AUTHORED_OP1_PARITY')

def validate_run(run_root, pins_path, input_sha, profile, kind):
    need(type(input_sha) is str and HEX.fullmatch(input_sha) is not None, 'INPUT_SHA')
    need(profile in ('BASE','RAMP') and kind in ('TINY_NATIVE','AUTHORED'), 'CLI_CONTEXT')
    root=Path(os.path.abspath(run_root)); no_reparse(root)
    temp_root=Path(tempfile.gettempdir())
    need(below(root,temp_root), 'RUN_ROOT_CONFINEMENT')
    pins_raw=stable_read(pins_path,1024*1024); need(sha(pins_raw)==input_sha,'PINS_HASH')
    pins=parse(pins_raw); need(type(pins) is dict and pins.get('schema')==INPUTS,'PINS_SCHEMA')
    need(pins.get('baseManifestSha256')==BASE_MANIFEST and pins.get('planSha256')==PLAN_SHA
         and pins.get('boundarySha256')==PROTOCOL_SHA, 'FROZEN_INPUT_PINS')
    need(pins.get('wireSha256',WIRE)==WIRE, 'WIRE_PIN')
    need(type(pins.get('files')) is list, 'PINS_FILES')
    pinned={}
    for row in pins['files']:
        keys(row,('path','bytes','sha256'),'PIN_ROW_FIELDS')
        p=Path(row['path']); need(p.is_absolute() and str(p)==os.path.abspath(p), 'PIN_PATH_ABSOLUTE')
        need(type(row['sha256']) is str and HEX.fullmatch(row['sha256']) is not None,'PIN_SHA')
        n=integer(row['bytes'],0,4*1024*1024,'PIN_BYTES'); key=os.path.normcase(str(p))
        need(key not in pinned,'DUPLICATE_PIN'); pinned[key]=row
    need(pinned, 'EMPTY_PINS')
    for row in pinned.values():
        b=stable_read(row['path'],4*1024*1024); need(len(b)==row['bytes'] and sha(b)==row['sha256'],'INPUT_CLOSE_HASH')
    def pin(path, expected=None):
        key=os.path.normcase(os.path.abspath(path)); need(key in pinned,'UNPINNED_INPUT')
        row=pinned[key]; need(expected is None or row['sha256']==expected,'INPUT_PIN_VALUE'); return row
    repo=Path(pins['repositoryRoot']); base_path=Path(pins['baseManifestPath'])
    need(repo.is_absolute() and base_path.is_absolute() and not below(root,repo),'REPO_PATHS')
    pin(pins['planPath'],PLAN_SHA); pin(pins['boundaryPath'],PROTOCOL_SHA)
    pin(pins['preflight']); pin(pins['overlayManifestPath'],pins['overlayManifestSha256'])
    pin(pins['bridgeManifest'],'e31649f0ea2a5740c998c4859bdc595b2ca75651e689ff56b1d146e721b3cd8b')
    baseline_dir=Path(pins['bridgeRoot'])/'data'
    pin(baseline_dir/'study-index.json',OLD_INDEX); pin(baseline_dir/'op-1.jsonl',OLD_OP1)
    base_raw=stable_read(base_path,4*1024*1024); need(sha(base_raw)==BASE_MANIFEST,'BASE_MANIFEST_HASH')
    base=parse(base_raw); need(type(base.get('files')) is list and len(base['files'])==1084
         and sum(r.get('role')=='BASE_SOURCE' for r in base['files'])==1080,'BASE_MANIFEST_CENSUS')
    reader_path=repo/'tests/qualification/rb100-layout/branch-tree-study/read_branch_order_study.py'
    pin(reader_path,OLD_READER); reader_raw=stable_read(reader_path,1024*1024)
    need(sha(reader_raw)==OLD_READER,'OLD_READER_HASH')
    spec=importlib.util.spec_from_file_location('p2_pinned_branch_reader',reader_path)
    reader=importlib.util.module_from_spec(spec); spec.loader.exec_module(reader)
    launch=object_file(root,'host-launch.json'); host=object_file(root,'host-result.json')
    need(launch.get('schema')==HOST and host.get('schema')==HOST_RESULT
         and launch.get('status')=='LAUNCHED_NOT_ACCEPTED'
         and host.get('status')=='HOST_COMPLETE_READER_PENDING','HOST_SCHEMA_STATUS')
    script_pin=pin(launch.get('script',''))
    need(launch.get('scriptSha256')==script_pin['sha256'],'HOST_SCRIPT_PIN')
    run_id=launch.get('runId')
    need(type(run_id) is str and UUID.fullmatch(run_id) and host.get('runId')==run_id
         and launch.get('profile')==profile and host.get('profile')==profile
         and launch.get('kind')==kind and host.get('kind')==kind
         and launch.get('inputPinsSha256')==input_sha and host.get('inputPinsSha256')==input_sha,
         'HOST_CONTEXT')
    for key,value in (('compileLimitMs',60000),('nativeLimitMs',480000),
                      ('cleanupLimitMs',120000),('outerLimitMs',1800000)):
        need(type(launch.get(key)) is int and launch[key]==value,'HOST_LIMIT_'+key)
    need(host.get('sourceClosure')=='PASS' and host.get('scratchRemoved') is True
         and host.get('nativeCaptureLimitNegative')=='PASS' and host.get('compilerSources')==1077,
         'HOST_FINAL_PROOF')
    need(host.get('cleanupBoundMs')==120000,'HOST_CLEANUP_BOUND')
    native_wall=number(host.get('nativeWallMs'),480000,'NATIVE_WALL')
    neg_wall=number(host.get('captureNegativeWallMs'),135000,'CAPTURE_NEGATIVE_WALL')
    post=number(host.get('postSourceAndScratchCleanupMs'),120000,'POST_CLEANUP')
    outer=number(host.get('outerElapsedMs'),1800000,'OUTER_ELAPSED')
    work=integer(host.get('wrapperWorkNanos'),0,LIMITS['wrapperNanos'],'HOST_WRAPPER_WORK')
    upper=number(host.get('cleanupConservativeUpperMs'),120000,'CLEANUP_UPPER')
    need(abs(upper-(native_wall-work/1_000_000+post))<=0.001,'CLEANUP_ACCOUNTING')
    need(host.get('compilerTerminationProven') is True and host.get('nativeTerminationProven') is True
         and type(host.get('compilerExitCode')) is int and host['compilerExitCode']==0
         and type(host.get('nativeExitCode')) is int and host['nativeExitCode']==0,'HOST_PROCESS_PROOF')
    scratch=Path(launch.get('scratch','')); need(scratch.is_absolute() and below(scratch,temp_root),'SCRATCH_PATH')
    need(not scratch.exists(),'SCRATCH_STILL_PRESENT')
    ci=object_file(root,'compiler-launch-intent.json'); compiler=process(object_file(root,'compiler-process.json'),True)
    need(ci.get('status')=='INTENT_NOT_START_PROOF' and ci.get('sha256')=='61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64'
         and ci.get('limitMs')==60000,'COMPILER_INTENT')
    need(compiler['Arguments']==ci.get('arguments') and same_path(compiler['FilePath'],ci.get('executable'))
         and compiler['ProcessId']==host.get('compilerProcessId')
         and str(compiler['ProcessStartTicks'])==host.get('compilerProcessStartTicks'),'COMPILER_BINDING')
    classes=scratch/'classes'; empty=scratch/'empty-sourcepath'; source_list=scratch/'sources.txt'
    cp=';'.join((str(classes),str(repo/'.tools/gwt-2.7.0/gwt-user-2.7.0.jar'),
                 str(repo/'.tools/gwt-2.7.0/gwt-dev-2.7.0.jar')))
    expected_ci=['-source','7','-target','7','-encoding','UTF-8','-classpath',cp,'-sourcepath',str(empty),
                 '-d',str(classes),'@'+str(source_list)]
    need(ci.get('arguments')==expected_ci and same_path(ci.get('sourceListPath',''),source_list),
         'COMPILER_EXACT_INVOCATION')
    source_bytes=stable_read(child(root,'compiler-sources.txt'),1024*1024)
    need(sha(source_bytes)==ci.get('sourceListSha256'),'SOURCE_LIST_HASH')
    excluded={'src/com/lushprojects/circuitjs1/client/'+x for x in
              ('CirSim.java','ChipElm.java','PhysicalSpecificationDeveloperVerifier.java','PcbLayerRoutingPrototype.java')}
    expected=[str(repo/r['path']).replace('\\','/') for r in base['files']
              if r.get('role')=='BASE_SOURCE' and r['path'].endswith('.java') and r['path'] not in excluded]
    mf=pins.get('modeFiles'); need(type(mf) is dict and set(mf)=={'BASE','RAMP'},'MODE_FILES')
    for prof in ('BASE','RAMP'):
        need(type(mf[prof]) is dict and set(mf[prof])=={'router','support','writer','wrapper'},'MODE_FILE_KEYS')
        for role,path in mf[prof].items(): pin(path)
        pin(mf[prof]['wrapper'], WRAPPER_SHA)
    need(same_path(mf['BASE']['writer'],mf['RAMP']['writer'])
         and same_path(mf['BASE']['wrapper'],mf['RAMP']['wrapper']), 'SHARED_WRITER_WRAPPER')
    for role in ('router','support','writer','wrapper'): expected.append(str(Path(mf[profile][role])).replace('\\','/'))
    bridge_doc=parse(stable_read(pins['bridgeManifest'],65536)); need(type(bridge_doc) is list and len(bridge_doc)==3,'BRIDGE_MANIFEST')
    for row in bridge_doc: pin(Path(pins['bridgeRoot'])/('bridge-'+row['path']),row['sha256'])
    copies=object_file(root,'consumed-bridge-copies.json'); need(type(copies) is list and len(copies)==3,'BRIDGE_COPY_CENSUS')
    for actual,want in zip(copies,bridge_doc):
        keys(actual,('path','bytes','sha256'),'BRIDGE_COPY_FIELDS')
        need(same_path(actual['path'],scratch/want['path']) and actual['bytes']==want['bytes']
             and actual['sha256']==want['sha256'],'BRIDGE_COPY_BINDING')
        expected.append(str(Path(actual['path'])).replace('\\','/'))
    listed=source_bytes.decode('utf-8','strict').splitlines()
    need(source_bytes.endswith(b'\n') and b'\x00' not in source_bytes
         and all(x.startswith('"') and x.endswith('"') and '"' not in x[1:-1] for x in listed)
         and [x[1:-1] for x in listed]==expected and len(listed)==1077
         and len(set(expected))==1077,'COMPILER_SOURCE_CLOSURE')
    audit_args=['-B',str(Path(pins['preflight'])),str(repo),str(base_path),BASE_MANIFEST,
                str(Path(os.path.abspath(pins_path))),input_sha]
    for name in ('preflight-process.json','pre-native-process.json','post-source-process.json'):
        p=process(object_file(root,name),True); need(p['Stderr']=='','SOURCE_AUDIT_STDERR')
        a=p['Arguments']
        need(len(a)==7 and a[0]=='-B' and a[4]==BASE_MANIFEST and a[6]==input_sha
             and same_path(a[1],audit_args[1]) and same_path(a[2],audit_args[2])
             and same_path(a[3],audit_args[3]) and same_path(a[5],audit_args[5]),
             'SOURCE_AUDIT_ARGUMENTS')
        need(sha(stable_read(p['FilePath'],32*1024*1024))==PYTHON_SHA,'SOURCE_AUDIT_PYTHON')
        audited=parse(p['Stdout'].encode('utf-8'))
        need(audited.get('status')=='PASS_PRIVATE_OVERLAY_INPUT_CLOSURE_ONLY'
             and audited.get('baseFiles')==1084 and audited.get('extraFiles')==len(pinned)
             and audited.get('pinsSha256')==input_sha,'SOURCE_AUDIT_RESULT')
    native_intent=object_file(root,'native-launch-intent.json')
    neg_intent=object_file(root,'native-capture-negative-intent.json')
    native=process(object_file(root,'native-process.json'),True)
    negative=process(object_file(root,'native-capture-limit-negative.json'),False)
    java_kind='TINY_CANARY' if kind=='TINY_NATIVE' else 'AUTHORED'
    java='com.lushprojects.circuitjs1.client.PrefixViaFirstRequestStudy'
    output_root=Path(launch.get('outputRoot',''))
    need(output_root.is_absolute() and str(output_root)==os.path.abspath(output_root)
         and below(output_root,temp_root) and not below(output_root,repo),'OUTPUT_ROOT_PATH')
    data_arg=str(output_root/'data'); args=['-ea','-cp',cp,java,java_kind,run_id,input_sha,WIRE,data_arg]
    neg_args=['-ea','-cp',cp,java,'--capture-overflow']
    need(native_intent.get('status')=='INTENT_NOT_START_PROOF' and native_intent.get('sha256')=='e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
         and native_intent.get('arguments')==args and neg_intent.get('status')=='INTENT_NOT_START_PROOF'
         and neg_intent.get('sha256')==native_intent.get('sha256') and neg_intent.get('arguments')==neg_args,
         'NATIVE_INTENT')
    need(native['Arguments']==args and negative['Arguments']==neg_args
         and same_path(native['FilePath'],native_intent.get('executable'))
         and same_path(negative['FilePath'],native_intent.get('executable'))
         and native['ProcessId']==host.get('nativeProcessId')
         and str(native['ProcessStartTicks'])==host.get('nativeProcessStartTicks'),'NATIVE_BINDING')
    need(negative['Stdout']=='' and 'PREFIX_VIA_CAPTURE_LIMIT' in negative['Stderr']
         and len(negative['Stderr'].encode('utf-8'))<=LIMITS['receiptBytes'],'CAPTURE_LIMIT_NEGATIVE')
    try: need(sha(stable_read(native_intent['executable'],32*1024*1024))==native_intent['sha256'],'JAVA_BINARY_HASH')
    except OSError as e: raise Rejected('JAVA_BINARY_READ') from e
    try: need(sha(stable_read(ci['executable'],32*1024*1024))==ci['sha256'],'JAVAC_BINARY_HASH')
    except OSError as e: raise Rejected('JAVAC_BINARY_READ') from e
    wrapper,total=validate_trailer(native['Stdout'],native['Stderr'],host.get('nativeOutputBytes'),profile,kind,run_id,input_sha)
    need(work==wrapper['wrapper']['workNanos'] and wrapper['wrapper']['elapsedNanos']<=native_wall*1_000_000,
         'HOST_WRAPPER_BINDING')
    need(host['nativeOutputBytes']<=LIMITS['receiptBytes'] and neg_wall<=135000,'HOST_CAPTURE_BOUNDS')
    desc=wrapper['descriptor']; data_dir=child(root,'data')
    index_path=child(data_dir,desc['path']); index_raw=stable_read(index_path,65536)
    need(len(index_raw)==desc['bytes'] and sha(index_raw)==desc['sha256'],'INDEX_DESCRIPTOR_HASH')
    index=parse(index_raw); entry=validate_index(index,index_raw,profile,kind,run_id,input_sha)
    validate_wrapper_cleanup(wrapper,entry)
    op_path=child(data_dir,entry['receipt']['path']); receipt=stable_read(op_path,LIMITS['receiptBytes'])
    need(len(receipt)==entry['receipt']['bytes'] and sha(receipt)==entry['receipt']['sha256'],'ENTRY_RECEIPT_HASH')
    operation=reader.validate_operation(receipt,entry,input_sha,WIRE,1,run_id,kind)
    combined=combined_group_count(len(operation['groups']))
    if type(pins.get('wrapperGroupCount',2)) is int: need(pins.get('wrapperGroupCount',2)==2,'WRAPPER_GROUP_PIN')
    baseline='NOT_APPLICABLE'
    if profile=='BASE' and kind=='AUTHORED':
        olddir=Path(pins['bridgeRoot'])/'data'; oldidx=stable_read(olddir/'study-index.json',65536)
        oldraw=stable_read(olddir/'op-1.jsonl',LIMITS['receiptBytes'])
        need(sha(oldidx)==OLD_INDEX and sha(oldraw)==OLD_OP1,'BASELINE_HASH')
        old= parse(oldidx); need(old.get('schema')=='tsj-branch-order-index-v1', 'BASELINE_INDEX_SCHEMA')
        need(old.get('runId')=='c66f6960-d5be-4e0b-aaf1-30adc6f60bfa' and old.get('kind')=='AUTHORED'
             and old.get('sourceManifestSha256')==OLD_SOURCE and old.get('wireSha256')==WIRE
             and type(old.get('operations')) is list and len(old['operations'])==5,'BASELINE_INDEX_CONTEXT')
        oldentry=old['operations'][1]; validate_entry(oldentry)
        need(oldentry['receipt']['sha256']==OLD_OP1,'BASELINE_ENTRY_HASH')
        reader.validate_operation(oldraw,oldentry,OLD_SOURCE,WIRE,1,old['runId'],'AUTHORED')
        compare_base(receipt,oldraw,reader); baseline='PASS_EXACT_BASE_AUTHORED_OP1'
    for row in pinned.values():
        b=stable_read(row['path'],4*1024*1024); need(len(b)==row['bytes'] and sha(b)==row['sha256'],'INPUT_CHANGED_DURING_READ')
    need(stable_read(pins_path,1024*1024)==pins_raw and stable_read(base_path,4*1024*1024)==base_raw
         and stable_read(index_path,65536)==index_raw and stable_read(op_path,LIMITS['receiptBytes'])==receipt
         and stable_read(reader_path,1024*1024)==reader_raw,'CLOSE_CHANGED')
    summary=dict(route=operation['result']['route'],outcome=operation['result']['outcome'],
                 receipt=operation['result']['receipt'],groupCount=len(operation['groups']),
                 branchPrefixViaCounts=[dict(ordinal=b['ordinal'],netId=b['snapshot']['netId'],
                     prefixViaCount=b['snapshot']['prefixViaCount']) for b in operation['branches']])
    return dict(status='PASS_ONE_REQUEST_DIAGNOSTIC_ONLY',profile=profile,kind=kind,
                inputPinsSha256=input_sha,runId=run_id,baselineComparison=baseline,
                wrapperWorkNanos=work,combinedGroups=combined,
                nativeWallMs=native_wall,cleanupConservativeUpperMs=upper,operation=summary,
                layoutQualification=False)
def main():
    need(len(sys.argv)==6, 'USAGE: read_first_request.py RUN_ROOT INPUT_PINS INPUT_SHA PROFILE KIND')
    result=validate_run(*sys.argv[1:]); print(json.dumps(result,sort_keys=True,separators=(',',':')))
if __name__=='__main__': main()
