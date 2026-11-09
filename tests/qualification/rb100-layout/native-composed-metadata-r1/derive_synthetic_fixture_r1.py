"""Root Source-admitted synthetic fixture and independent pre-code census."""
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
