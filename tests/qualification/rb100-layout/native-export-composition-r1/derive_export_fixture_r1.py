"""Root synthetic factory/census plus independent literal export calibration."""
from pathlib import Path
import ast, copy, hashlib, json, re, sys, types
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
assert not sys.argv[1:]
C=Path(__file__).resolve().parent
def read(name,sha):
    p=C/'source'/name
    assert p.is_file() and not p.is_symlink() and 0<p.stat().st_size<=4194304
    raw=p.read_bytes();assert hashlib.sha256(raw).hexdigest()==sha
    return raw
codec=read('context_codec.py','de21e233e27f93c8183101a89ed6fdb9a00812d69e1fe38cc47c3f3e573b3eb8')
test=read('test_context_codec.py','2a9479e97393273341eb44b71aa6870cb9c0661c87e8985e2bd4c83c193f7f36')
module=types.ModuleType('_admitted_pure_context_codec')
exec(compile(codec,'context_codec.py','exec'),module.__dict__)
tree=ast.parse(test)
nodes=[node for node in tree.body if isinstance(node,ast.FunctionDef) and node.name=='_synthetic_context']
assert len(nodes)==1
ns={'copy':copy,'_CODEC':module}
exec(compile(ast.Module(body=nodes,type_ignores=[]),'_synthetic_context_source_only','exec'),ns)
context=ns['_synthetic_context']()
raw=module.canonical_context(context)
sha=hashlib.sha256(raw).hexdigest()
assert sha=='ca2d5b88b686845889e97de4eea96c4d86f80c1c5ec720aa0d912ba5b675666f'
assert len(raw)==66485
factory=module.generate_input_java(context,sha)
assert hashlib.sha256(factory).hexdigest()=='c8a25f88ccf523e030e7e1c14073255b78b9358687ac65088339a237bea80089'
assert len(factory)==21404 and module.data_entries(context['nativeProjection'])==271
# Independent literal root and work inventory, not codec extraction or Java output.
keys=('elapsedCutoffNanos','cleanupNanos','groupsBeforeExport','wrapperGroupsBeforeExport','maxGroupNanosBeforeExport',
      'maximumUnitsPerGroupBeforeExport','unitsBeforeExport','setupUnits','setupGroups','setupCopiedEntries',
      'setupCopyChunks','originalCaptureOwnerUnits','originalCaptureWrapperUnits',
      'originalCaptureCallbackUnits','originalCaptureDiagnosticUnits',
      'originalCaptureGroups','originalCaptureCopiedEntries','originalCaptureCopyChunks',
      'nullOwnerUnits','nullWrapperUnits','nullObserverUnits','observeOwnerUnits',
      'observeWrapperUnits','observeObserverUnits','observerUnits','meterUnits',
      'metadataUnits','metadataGroups','metadataCopiedEntries','metadataCopyChunks',
      'copiedEntries','copyChunks','copyChunkLimit','copiedEntryLimit')
body={'schema':'tsj-cohort-terminal-result-v2',
      'mode':'OBSERVED_COHORT_TERMINAL_TELEMETRY','status':'COMPLETED_DIAGNOSTIC_ONLY',
      'runId':'00000000-0000-0000-0000-000000000001','sourceManifestSha256':'1'*64,
      'wireContractSha256':'1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed',
      'inputContextSha256':sha,'qualifiedPrior':{'schema':'tsj-cohort-terminal-qualified-prior-v2',
      'inputContextSha256':sha,**copy.deepcopy(context['nativeProjection']['prior'])},
      'originalCapture':{},'cohort':{},'arms':{},'work':dict.fromkeys(keys,0),'cleanup':{}}
census=dict(M=0,L=0,Em=0,El=0,S=0,N=0,B=0,keyCharacters=0,valueCharacters=0)
seen=set()
def count(x):
    k=type(x)
    if k in (dict,list):
        assert id(x) not in seen;seen.add(id(x))
    if k is dict:
        census['M']+=1;census['Em']+=len(x)
        for key,value in x.items():
            assert type(key) is str and key.isascii() and key
            census['keyCharacters']+=len(key);count(value)
    elif k is list:
        census['L']+=1;census['El']+=len(x)
        for value in x:count(value)
    elif k is str:
        assert x and x.isascii();census['S']+=1;census['valueCharacters']+=len(x)
    elif k is bool:census['B']+=1
    elif k is int:census['N']+=1
    else:raise AssertionError('UNEXPECTED_LITERAL')
count(body)
assert [census[k] for k in ('M','L','Em','El','S','N','B')]==[9,2,72,62,85,37,2]
characters=census['keyCharacters']+census['valueCharacters']
minimum=1+census['Em']+census['El']+2*census['Em']+census['S']
traversal=1+5*census['M']+6*census['L']+7*census['Em']+2*census['El']+7*census['S']+7*census['N']+8*census['B']+6*characters
assert minimum==364 and traversal==1556+6*characters
ownership=5+6*census['M']+5*census['L']+10*census['Em']+5*census['El']
assert ownership==1099
result={'status':'SYNTHETIC_ONLY_NOT_FULL_D','contextSha256':sha,'contextBytes':len(raw),
        'factorySha256':hashlib.sha256(factory).hexdigest(),'factoryBytes':len(factory),
        'census':census,'characters':characters,'accountMinimumUnits':minimum,
        'accountTraversalUnits':traversal,'accountTotalUnits':minimum+traversal,
        'ownershipUnits':ownership,'R7FixedUnits':184,
        'R7TotalUnits':184+ownership+minimum+traversal,
        'wholeUnitsExcludingDynamic10P6B':13496+6*characters,
        'wholeCopiesExcluding2P':618,'wholeChunks':23,
        'wholeWrapperGroups':2,'syntheticPriorGroups':1,
        'workKeys':keys,'genuineEnvelope':'UNSPENT_GATED'}
out=C/'emitted';out.mkdir()
for name,data in [('CohortTerminalTelemetryInputV2.java',factory),
                  ('synthetic-context.json',raw),
                  ('INDEPENDENT_CENSUS_R1.json',(json.dumps(result,indent=2)+'\n').encode('ascii'))]:
    with (out/name).open('xb') as stream:stream.write(data)
print(json.dumps(result,sort_keys=True))

# PRE-CODE literal calibration. These clock numbers are Python test data only.
# No Java/native codec, candidate oracle, finalized Java body or marker is read.
p=11;u0=28828;c0=640;cutoff=80000;setup_ns=1000;metadata_ns=2000;export_ns=3000
vector=[80000,0,3,2,2000,18821,28828,10007,1,478,3,0,0,0,0,1,0,0,
        0,0,0,0,0,0,0,28828,18821,1,162,20,640,23,4096,1048576]
assert len(vector)==34 and len(keys)==34
body['work']=dict(zip(keys,vector))
def canonical_literal(value):
    encoded=(json.dumps(value,separators=(',',':'),ensure_ascii=True)+'\n').encode('ascii')
    assert len(encoded)<=32768
    return encoded
expected_result=canonical_literal(body);r=len(expected_result);blocks=(r+4095)//4096
digest=hashlib.sha256(expected_result).hexdigest();fee=5*blocks+3
marker={'schema':'tsj-cohort-terminal-marker-v2','status':'COMPLETED_DIAGNOSTIC',
        'runId':'00000000-0000-0000-0000-000000000001','sourceManifestSha256':'1'*64,
        'wireContractSha256':'1abecdf5c1c4260b8461c5454822f7fa0b07fd050e574d59b1f9244ab6cc93ed',
        'inputContextSha256':sha,'resultSha256':digest,'resultBytes':r,
        'elapsedNanos':90000,'finalExportNanos':3000,'totalGroups':4,
        'totalUnits':u0+fee,'maxGroupNanos':3000,'maximumUnitsPerGroup':18821}
expected_marker=canonical_literal(marker);m=len(expected_marker)
assert m<=656
ints=('operationNanos cleanupNanos pathEntries receiptBytes receiptHashBlocks '
      'units copiedEntries copyChunks wrapperGroups priorGroups groupUnits maxGroupUnits '
      'maxGroupNanos closedGroupMeasures workFinalizedSlots exportCutoffNanos '
      'beforeExportElapsedNanos afterExportElapsedNanos setupMeasureUnits setupMeasureNanos '
      'metadataMeasureUnits metadataMeasureNanos exportMeasureUnits exportMeasureNanos '
      'resultBytesObserved metricsResultBytes metricsMarkerBytes metricsExportUnits '
      'metricsFinalExportNanos metricsElapsedNanos metricsTotalUnits metricsTotalGroups '
      'metricsMaxGroupNanos metricsMaximumUnitsPerGroup metricsCopiedEntriesDelta '
      'metricsCopyChunksDelta').split()
positive=dict.fromkeys(ints,0)
positive.update(name='current_export_composition',status='PASS',phase='complete',reason='OK',
    cleanupDisposition='ALREADY_CLOSED_AND_REMOVED',error='',cleanupError='',expectedFailure='',
    resultHex=expected_result.hex(),markerHex=expected_marker.hex(),meterOpen=False,
    stdoutRestored=True,metricsReturned=True,meterDiscarded=False,receiptMutationApplied=False,
    operationNanos=1000000,cleanupNanos=100,pathEntries=11,receiptBytes=4096,receiptHashBlocks=1,
    units=u0+fee,copiedEntries=c0+2*r+32+m,copyChunks=23+2*blocks+2,wrapperGroups=3,
    priorGroups=1,groupUnits=fee,maxGroupUnits=18821,maxGroupNanos=3000,closedGroupMeasures=3,
    workFinalizedSlots=34,exportCutoffNanos=80000,beforeExportElapsedNanos=70000,
    afterExportElapsedNanos=100000,setupMeasureUnits=10007,setupMeasureNanos=1000,
    metadataMeasureUnits=18821,metadataMeasureNanos=2000,exportMeasureUnits=fee,
    exportMeasureNanos=3000,resultBytesObserved=r,metricsResultBytes=r,metricsMarkerBytes=m,
    metricsExportUnits=fee,metricsFinalExportNanos=3000,metricsElapsedNanos=90000,
    metricsTotalUnits=u0+fee,metricsTotalGroups=4,metricsMaxGroupNanos=3000,
    metricsMaximumUnitsPerGroup=18821,metricsCopiedEntriesDelta=2*r+32+m,
    metricsCopyChunksDelta=2*blocks+2)
negative=dict(positive)
negative.update(name='receipt_changed_before_export_close',
    cleanupDisposition='EXPECTED_PARTIAL_EXPORT_METER_DISCARDED_AND_REMOVED',
    expectedFailure='FINAL_SHA_MISMATCH',markerHex='',meterOpen=True,metricsReturned=False,
    meterDiscarded=True,receiptMutationApplied=True,units=u0+5*blocks+1,
    copiedEntries=c0+2*r,copyChunks=23+2*blocks,wrapperGroups=2,groupUnits=5*blocks+1,
    maxGroupNanos=2000,closedGroupMeasures=2,exportMeasureUnits=0,exportMeasureNanos=0)
for key in ints:
    if key.startswith('metrics'):negative[key]=0
reference={'status':'PRE_CODE_SYNTHETIC_CLOCK_REFERENCE_NOT_NATIVE_MEASUREMENT',
    'resultBytes':r,'resultSha256':digest,'markerBytes':m,
    'markerSha256':hashlib.sha256(expected_marker).hexdigest(),'blocks':blocks,'exportFee':fee,
    'positive':positive,'negative':negative,'integerFields':ints,'genuineEnvelope':'UNSPENT_GATED'}
for name,data in [('REFERENCE_RESULT_R1.bin',expected_result),('REFERENCE_MARKER_R1.bin',expected_marker),
                  ('REFERENCE_VECTOR_R1.json',(json.dumps(reference,indent=2)+'\n').encode('ascii'))]:
    with (out/name).open('xb') as stream:stream.write(data)
print(json.dumps({key:reference[key] for key in ('status','resultBytes','resultSha256',
    'markerBytes','markerSha256','blocks','exportFee')},sort_keys=True))
