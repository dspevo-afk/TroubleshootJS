#!/usr/bin/env python3
"""Build a compact, privacy-scrubbed archive from the frozen solve-profile receipts."""
import argparse, gzip, hashlib, json, re, statistics, sys
from pathlib import Path

HERE=Path(__file__).resolve().parent
REPO=HERE.parents[3]
PRIVATE=re.compile(rb'(?i)[a-z]:[/\\]+Users[/\\]+david(?:[/\\][^\\/\s"<>|?*:,;]+)*')
LABELS=[('solve-profile-01-off-10014','off'),('solve-profile-02-profile-10014','profile')]
CHANGED=['src/com/lushprojects/circuitjs1/client/CirSim.java',
 'src/com/lushprojects/circuitjs1/client/Q30CoordinatorQualificationVerifier.java',
 'scripts/verify-current-contracts.ps1',
 'tests/contracts/Q30LuRepetitionProfileContractTest.java',
 'tests/contracts/Q30LuSolveProfileContractTest.java']

def die(m): raise RuntimeError(m)
def sha(b): return hashlib.sha256(b).hexdigest()
def readj(p):
 try: return json.loads(p.read_text(encoding='utf-8'))
 except Exception as e: die(f'bad JSON {p.name}: {e}')
def under(p,root):
 q=p.resolve(strict=True)
 try:q.relative_to(root.resolve(strict=True))
 except ValueError:die(f'input escapes root: {p}')
 return q
def redact(b,exact=False):
 if exact and PRIVATE.search(b):die('personal absolute path in raw report/source overlay')
 b,n=PRIVATE.subn(b'[REDACTED_USER_PATH]',b)
 if PRIVATE.search(b):die('path scrub incomplete')
 return b,n

ap=argparse.ArgumentParser(description=__doc__); ap.add_argument('--scratch',required=True,type=Path); a=ap.parse_args()
S=a.scratch.resolve(strict=True)
if not (REPO/'.git').exists():die('unexpected repository location')
if any(p.name!='package_solve_profile.py' for p in HERE.iterdir()):die('output folder is not empty except for packager')
planp=S/'solve-profile-measurement-plan.json'; seqp=S/'solve-profile-sequence-result.json'
gatep=S/'solve-profile-r1-gates.json'; auditp=S/'solve-profile-02-profile-10014-vs-solve-profile-01-off-10014-solve-profile-validation-root-r1.json'
plan=readj(planp); seq=readj(seqp); gates=readj(gatep); validation=readj(auditp)
if plan.get('status')!='PREDECLARED' or str(plan.get('seed'))!='10014' or len(plan.get('sequence',[]))!=2:die('unexpected predeclared plan')
if sha(planp.read_bytes())!=seq.get('planSha256') or seq.get('notRun')!=[]:die('plan hash or sequence completion mismatch')
if [(r.get('label'),r.get('mode')) for r in plan['sequence']]!=LABELS:die('plan order mismatch')
rows=seq.get('rows',[])
if [(r.get('label'),r.get('mode')) for r in rows]!=LABELS or any(r.get('runnerOutcome')!='PASS' or r.get('exitCode')!=0 for r in rows):die('timing rows incomplete/failed')
if gates.get('status')!='FOCUSED_GATES_PASS_NOT_ACCEPTANCE' or len(gates.get('steps',[]))!=4 or any(x.get('exitCode')!=0 for x in gates['steps']):die('focused gates incomplete/failed')
if sha(gatep.read_bytes())!=seq.get('gatesSha256') or sha(planp.read_bytes())!=validation.get('planSha256'):die('receipt hash linkage mismatch')
if gates.get('sourceAuditSha256')!=seq.get('sourceAuditSha256') or gates.get('sourceAuditSha256')!=validation.get('reproductionDependencies',{}).get('sourceAuditSha256'):die('source audit is not bound by all receipts')
gate_files={'native-solve-profile-r1':'native-solve-profile-r1.json','solve-profile-r1-gwt':'solve-profile-r1-gwt.json',
 'solve-profile-r1-canaries-summary':'solve-profile-r1-canaries-summary.json','exact-a07-reader-and-three-corruptions':'solve-profile-r1-a07-reader.log'}
if {x['name'] for x in gates['steps']}!=set(gate_files):die('unexpected focused gate set')
for step in gates['steps']:
 if sha((S/gate_files[step['name']]).read_bytes())!=step.get('receiptSha256'):die(f"gate receipt hash mismatch: {step['name']}")
if validation.get('status')!='PASS' or validation.get('requestExact') is not True or validation.get('reportExactOutsideDeclaredTimingAndProfileFields') is not True:die('root pair validator did not pass exact parity')
if len(validation.get('metadataNegativeCanaries',[]))!=79 or any(x.get('status')!='REJECTED_AS_EXPECTED' for x in validation['metadataNegativeCanaries']):die('metadata negative count/status mismatch')
sourceptr=S/'solve-profile-source-path.txt'; fixture=under(Path(sourceptr.read_text().strip()),S)
sourceauditp=fixture/'source-audit.json'
if sha(sourceauditp.read_bytes())!=seq.get('sourceAuditSha256'):die('source audit hash mismatch')
audit=readj(sourceauditp)
if audit.get('status')!='SOURCE_ONLY_FIXTURE_PREPARED_NOT_VALIDATED' or audit.get('generatedWarCopied') is not False or audit.get('changedPaths')!=CHANGED:die('historical source audit scope/status mismatch')
inputs={x['path']:x for x in audit.get('inputs',[])}
if len(inputs)!=1341 or len(audit.get('rootInputs',[]))!=1339:die('full source input inventory count mismatch')
for rel in CHANGED:
 p=under(fixture/Path(rel),fixture); b=p.read_bytes(); e=inputs.get(rel,{})
 if sha(b)!=e.get('sha256') or len(b)!=e.get('bytes'):die(f'source overlay identity mismatch: {rel}')
base=REPO/'docs/task-evidence/Q30/owned-zero-row-scan'
baseinv=base/'inventory.json'; basesum=base/'inventory.sha256'; baseline=base/'source/lineage.json'
if any(not p.is_file() for p in (baseinv,basesum,baseline)) or basesum.read_text().split()[0]!=sha(baseinv.read_bytes()):die('accepted owned-zero-row source baseline missing/invalid')
if sha((S/'solve-profile-r1-gates.json').read_bytes())!=seq.get('gatesSha256'):die('focused-gate linkage mismatch')
if not validation.get('runs') or len(validation['runs'])!=2:die('pair validator must attest both raw reports')
report_for={}
for item in validation['runs']:
 rp=under(S/Path(item['reportPath']),S); b=rp.read_bytes()
 if sha(b)!=item.get('reportSha256'):die('report receipt hash mismatch')
 redact(b,True); report_for[item['label']]=(rp,b,item)
if set(report_for)!=set(x[0] for x in LABELS):die('pair validator report labels mismatch')
if len(validation.get('strictReaderSourceHashes',{}))!=2:die('strict reader source hashes missing')
# Frozen source audit, final focused gate receipt, host pair validation and input custody.
if gates.get('sourceInputCount')!=1341 or seq.get('sourceAuditSha256')!=sha(sourceauditp.read_bytes()):die('gate/source audit binding mismatch')
for row in rows:
 if row.get('cleanup',{}).get('status')!='PASS' or row.get('cleanup',{}).get('ownedSurvivors') or row.get('cleanup',{}).get('errors'):die('host cleanup failure')
 ia=row.get('inputAudit',{})
 if ia.get('status')!='PASS' or ia.get('beforeSha256')!=ia.get('afterSha256') or ia.get('sourceSha256Before')!=ia.get('sourceSha256After') or ia.get('webSha256Before')!=ia.get('webSha256After') or ia.get('runnerBeforeSha256')!=ia.get('runnerAfterSha256'):die('input stability failure')
 if ia.get('fileCountBefore')!=1525 or ia.get('fileCountAfter')!=1525:die('input manifest count mismatch')
 d=S/row['label']
 for suffix,key in (('before','beforeSha256'),('after','afterSha256')):
  mf=d/f'input-manifest-{suffix}.json'
  if not mf.is_file():die(f"input manifest missing: {row['label']} {suffix}")
  manifest=readj(mf)
  if manifest.get('sha256')!=ia.get(key) or manifest.get('fileCount')!=ia.get('fileCountBefore'):
   die(f"input manifest content identity mismatch: {row['label']} {suffix}")
for run in validation['runs']:
 if run.get('reportExactOutsideDeclaredTimingAndProfileFields') is False:die('raw report parity did not pass')
 host=run.get('hostAudit',{}); errors=host.get('errorArrays',{})
 if host.get('runnerExitCode')!=0 or host.get('hostOutcome')!='PASS' or host.get('caseOutcome')!='PASS' or host.get('timedOut') is not False:
  die(f"host canary did not pass: {run.get('label')}")
 if any(errors.get(k) for k in ('pageErrors','httpErrors','consoleErrors','attributeReadErrors','listenerCleanupErrors')):
  die(f"host error arrays are nonempty: {run.get('label')}")
 if host.get('cleanup',{}).get('status')!='PASS' or host.get('cleanup',{}).get('ownedSurvivors') or host.get('cleanup',{}).get('errors'):
  die(f"host cleanup did not pass: {run.get('label')}")
if len(validation['strictReaderSourceHashes'])!=2:die('expected both maintained strict readers')

for run in validation['runs']:
 sr=run.get('strictReader',{})
 if sr.get('readerExitCode')!=0 or sr.get('currentPlan4Pass') is not True or sr.get('selfNegativeCanaries')!=43 or sr.get('selfTestCanaryCounts',{}).get('corruptionCanaries')!=43:
  die(f"strict reader result mismatch: {run.get('label')}")

files={}; transforms=[]
def add(name,data,source,sanitize=True,compress=False,exact=False,jsoncheck=False):
 original=data; oh=sha(original); n=0
 if exact:
  if PRIVATE.search(data):die(f'personal path in exact payload: {source}')
 else:data,n=redact(data)
 if jsoncheck:
  try:json.loads(data.decode('utf-8'))
  except Exception as e:die(f'sanitized JSON invalid ({source}): {e}')
 payload=gzip.compress(data,mtime=0) if compress else data
 if name in files and files[name]!=payload:die(f'duplicate output path {name}')
 files[name]=payload
 transforms.append({'source':source,'stored':name,'originalBytes':len(original),'originalSha256':oh,
  'redactions':n,'storedPayloadBytes':len(data),'storedPayloadSha256':sha(data),
  'storedBytes':len(payload),'storedSha256':sha(payload),'gzip':compress})

def copy_file(src,name,compress=False,exact=False,jsoncheck=False):
 if not src.is_file():die(f'missing evidence file {src.name}')
 add(name,src.read_bytes(),src.name,compress=compress,exact=exact,jsoncheck=jsoncheck)

def addj(name,value,source):
 add(name,json.dumps(value,indent=2,ensure_ascii=False).encode()+b'\n',source,jsoncheck=True)
# Preserve sanitized run provenance and byte-exact application reports.
run_metrics=[]
for label,mode in LABELS:
 row=next(x for x in rows if x['label']==label); d=S/label
 for name in ('identity.json','progress.json','result.json','run-start.json','runner-before.json','runner-after.json','owned-processes-before.json','owned-processes-before-close.json','spec.json'):
  copy_file(d/name,f'runs/{label}/{name}.gz',compress=True,jsoncheck=name.endswith('.json'))
 for name in ('input-manifest-before.json','input-manifest-after.json'):
  copy_file(d/name,f'runs/{label}/{name}.json.gz',compress=True,jsoncheck=True)
 for name in (f'{label}-summary.json',f'{label}.log'):
  copy_file(S/name,f'runs/{name}.gz',compress=True,jsoncheck=name.endswith('.json'))
 req=next(x for x in validation['runs'] if x['label']==label)
 rp,b,receipt=report_for[label]
 if len(b)!=req.get('rawReportByteLength') or sha(b)!=req.get('reportSha256'):die('raw report hash/length changed')
 # Raw app JSON is stored unmodified; personal paths in it are a hard failure.
 redact(b,True); raw_name=f'runs/{label}/application-report.json.gz'
 payload=gzip.compress(b,mtime=0); files[raw_name]=payload
 transforms.append({'source':str(Path(req['reportPath']).as_posix()),'stored':raw_name,'originalBytes':len(b),
  'originalSha256':sha(b),'redactions':0,'storedPayloadBytes':len(b),'storedPayloadSha256':sha(b),
  'storedBytes':len(payload),'storedSha256':sha(payload),'gzip':True,'byteExactPayload':True})
 case=rp.parent
 for suffix in ('.json','.state.txt'):
  f=case/(rp.name.replace('.report.json',suffix))
  copy_file(f,f'runs/{label}/{f.name}.gz',compress=True,jsoncheck=suffix=='.json')
 run_metrics.append({'label':label,'mode':mode,'seed':'10014','coldMs':row['cold'].get('elapsedMs'),
  'proofMs':row['cold'].get('proofElapsedMs'),'routingMs':row['cold'].get('routingElapsedMs'),
  'hostSeconds':row.get('hostMonotonicSeconds'),'cleanupSeconds':row.get('cleanup',{}).get('seconds'),
  'reportSha256':req['reportSha256'],'reportBytes':len(b),'sourceSha256':req.get('sourceSha256'),
  'webSha256':req.get('webSha256')})

# Source audit is historical and copied read-only; five changed files are the complete overlay.
for rel in CHANGED:
 src=under(fixture/Path(rel),fixture)
 outname='source/overlays/'+rel+'.gz'
 add(outname,src.read_bytes(),f'fixture/{rel}',exact=True,compress=True)
add('source/input-hash-inventory.json.gz',json.dumps({'schema':1,'head':audit.get('head'),
 'rootInputCount':len(audit['rootInputs']),'inputCount':len(inputs),'inputs':audit['inputs']},
 indent=2).encode(), 'source-audit.inputs',compress=True,jsoncheck=True)
copy_file(sourceauditp,'source/source-audit.json.gz',compress=True,jsoncheck=True)

base_lineage=readj(baseline)
addj('source/base-reference.json',{'bundle':'../owned-zero-row-scan',
 'inventorySha256':basesum.read_text().split()[0],'lineageSha256':sha(baseline.read_bytes()),
 'reconstruction':'Start with the owned-zero-row-scan source baseline; apply the five exact overlays listed in source-audit.json. The complete 1,341-row input hash inventory is included here.',
 'baselineLineage':base_lineage},'existing owned-zero-row-scan packet')
# The source pointer is replaced by a relative reference; its raw path is never copied.
addj('source/fixture-reference.json',{'fixtureLeaf':fixture.name,'pointerOriginalSha256':sha(sourceptr.read_bytes()),
 'sourceAuditSha256':sha(sourceauditp.read_bytes()),'status':audit['status']},'solve-profile-source-path.txt')

# Plan, host sequence, focused gates and root's exact parity validator.
for src,dst in [(planp,'plan/measurement-plan.json'),(seqp,'plan/host-sequence-result.json'),
 (gatep,'gates/focused-gates.json'),(S/'solve-profile-r1-gwt.json','gates/gwt.json'),
 (S/'solve-profile-r1-canaries-spec.json','gates/canaries-spec.json'),
 (S/'solve-profile-r1-canaries-summary.json','gates/canaries-summary.json'),
 (S/'solve-profile-r1-gates.json','gates/focused-gates-copy.json'),
 (auditp,'validation/root-pair-validation.json'),(S/'solve-profile-new-metadata-only.json','validation/metadata-only-receipt.json')]:
 copy_file(src,dst+'.gz',compress=True,jsoncheck=src.suffix=='.json')
for src,dst in [(S/'solve-profile-r1-gwt.log','gates/gwt.log.gz'),(S/'solve-profile-r1-canaries.log','gates/canaries.log.gz'),
 (S/'solve-profile-r1-a07-reader.log','gates/a07-reader.log.gz'),(S/'solve-profile-validation-root-r1.log','validation/root-pair-validation.log.gz'),
 (S/'solve-profile-sequence.log','plan/sequence.log.gz'),(S/'native-solve-profile-r1.json','gates/native.json.gz'),
 (S/'native-solve-profile-r1.log','gates/native.log.gz'),(S/'native-solve-profile-r1-receipt.txt','gates/native-receipt.txt.gz')]:
 copy_file(src,dst,compress=dst.endswith('.gz'),jsoncheck=src.suffix=='.json')

for label,_ in LABELS:
 for suffix in ('strict-wrapper-root-r1.json','strict-reader-root-r1.txt'):
  src=S/f'{label}-{suffix}'; copy_file(src,f'validation/{src.name}.gz',compress=True,jsoncheck=src.suffix=='.json')
# Capture strict reader sources by their validated hashes and exact executed local helpers.
readerdir=REPO/'docs/task-evidence/Q30/scale-plan-4'
for name,h in validation['strictReaderSourceHashes'].items():
 src=readerdir/name
 if not src.is_file() or sha(src.read_bytes())!=h:die(f'strict reader source hash mismatch: {name}')
 add(f'procedure/readers/{name}.gz',src.read_bytes(),f'repo/{src.relative_to(REPO).as_posix()}',compress=True,exact=True)
helper_names=['run_timing.py','run_solve_profile_pair.py','validate_solve_profile_pair_root.py',
 'prepare_solve_profile.py','solve-profile-metadata-validator.py','native-solve-profile.ps1',
 'build-run.ps1','run_lu_profile_canaries.py','run_current_solver_canaries.py','read_a07.ps1']
for name in helper_names:
 src=S/name
 if src.is_file():
  raw=src.read_bytes()
  if name=='run_solve_profile_pair.py' and (sha(raw)!=seq.get('hostDriverSha256') or sha(raw)!=validation.get('reproductionDependencies',{}).get('currentPreflightRunnerSha256')):die('executed host-driver hash mismatch')
  if name=='solve-profile-metadata-validator.py' and sha(raw)!=validation.get('reproductionDependencies',{}).get('solveMetadataModuleSha256'):die('metadata validator hash mismatch')
  copy_file(src,f'procedure/{name}.gz',compress=True)
for name in ('solve-profile-independent-review.md','solve-profile-validator-independent-review.md','sticky-finite-solve-independent-review.md'):
 src=S/'scratch'/name if name!='solve-profile-validator-independent-review.md' else S/name
 if src.is_file():copy_file(src,f'reviews/{name}.gz',compress=True)
# Main reviewer note is also available directly under scratch/scratch.
for srcname in ('solve-profile-independent-review.md','sticky-finite-solve-independent-review.md'):
 src=S/'scratch'/srcname
 if src.is_file() and f'reviews/{srcname}.gz' not in files:copy_file(src,f'reviews/{srcname}.gz',compress=True)

# Derive census and extrapolation from the actual profiled report, and label the latter clearly.
def visit(x):
 if isinstance(x,dict):
  yield x
  for v in x.values():yield from visit(v)
 elif isinstance(x,list):
  for v in x:yield from visit(v)
profile_report=json.loads(report_for[LABELS[1][0]][1].decode('utf-8'))
coldprof=profile_report.get('cold',{}).get('luRepetitionProfile',{})
samples=coldprof.get('luSolveTimingSamples')
if samples!=1453 or coldprof.get('factorCalls')!=742391:die('solve sample/factor census differs from reviewed receipt')
if coldprof.get('luSolveTimingErrors')!=0 or coldprof.get('luSolveNativeClockUnavailable')!=0 or coldprof.get('probeClockErrors')!=0:die('solve sampling errors/unavailable clock')
lo=coldprof.get('strictLowerEntries'); up=coldprof.get('strictUpperEntries')
zeros=coldprof.get('strictLowerExactZeros',0)+coldprof.get('strictUpperExactZeros',0)
entries=(lo or 0)+(up or 0)
solve_ms=coldprof.get('luSolveMillis')
if entries<=0 or not solve_ms:die('triangular census metrics missing')
extrap=solve_ms/samples*coldprof['factorCalls']/1000
analysis={'coldSolveSamples':samples,'sampledSolveMillis':solve_ms,'factorCalls':coldprof['factorCalls'],
 'strictLower':{'entries':lo,'exactZeros':coldprof.get('strictLowerExactZeros'),'nonzeros':coldprof.get('strictLowerNonzeros')},
 'strictUpper':{'entries':up,'exactZeros':coldprof.get('strictUpperExactZeros'),'nonzeros':coldprof.get('strictUpperNonzeros')},
 'combinedStrictTriangleExactZeroPercent':100*zeros/entries,
 'probeOverheadMillis':coldprof.get('probeOverheadMillis'),
 'probeClock':coldprof.get('probeClock'),'probeClockErrors':coldprof.get('probeClockErrors'),
 'sampledStarts':coldprof.get('sampledStarts'),'completedPairs':coldprof.get('completedPairs'),
 'matchedPairs':coldprof.get('matchedPairs'),'differentPairs':coldprof.get('differentPairs'),
 'sampledMatrixInputs':coldprof.get('sampledMatrixInputs'),'sampledSizeGroups':coldprof.get('sampledSizeGroups'),
 'zeroRowPreflightMillis':coldprof.get('zeroRowPreflightMillis'),
 'extrapolatedSolveSecondsIfEveryFactorCallCostTheSampleMean':extrap,
 'extrapolationStatus':'ROUGH EXTRAPOLATION ONLY; not measured time or an optimization claim',
 'limits':['performance.now granularity and sparse deterministic selection','probeOverheadMillis is sample capture/compare inside brackets; excludes per-factor window/counter/modulo and compilation effects',
  'reported probe overhead also excludes initial RHS and strict-triangle census/traversal',
  'one seed and one off/profile pair','near-identical total/proof/routing timing does not prove zero overhead']}
add('analysis/solve-profile-values.json',json.dumps(analysis,indent=2).encode()+b'\n','profiled raw report',jsoncheck=True)

rows_text='| Run | Mode | Cold ms | Proof ms | Routing ms | Host seconds | Cleanup seconds |\n|---|---|---:|---:|---:|---:|---:|\n'
for x in run_metrics:rows_text+=f"| {x['label']} | {x['mode']} | {x['coldMs']} | {x['proofMs']} | {x['routingMs']} | {x['hostSeconds']:.3f} | {x['cleanupSeconds']:.3f} |\n"
strict='; '.join(f"{r['strictReader']['selfNegativeCanaries']} negative cases for {r['label']}" for r in validation['runs'])
readme=("# Solve-profile measurement — intermediate evidence\n\n"
 "**No optimization or acceptance result. Q30 remains BLOCKED.** This is one private, seed-10014 off/profile diagnostic pair; the sampler was not integrated into production. It does not establish a speedup, zero overhead, or normal acceptance.\n\n"
 "## Timings\n\n"+rows_text+"\n"
 "The off/profile cold totals were 99,730/99,729 ms; proof was 71,015/70,837 ms and routing 22,466/22,604 ms. This near-identical pair is not proof of zero instrumentation overhead.\n\n"
 f"The cold profile sampled {analysis['sampledStarts']} starts, completed {analysis['completedPairs']} adjacent-pair comparisons ({analysis['matchedPairs']} equal and {analysis['differentPairs']} different), and read {analysis['sampledMatrixInputs']} input matrices across sizes {min(analysis['sampledSizeGroups'])}–{max(analysis['sampledSizeGroups'])}. It measured {samples} selected `lu_solve` calls totaling {solve_ms:.3f} ms among {coldprof['factorCalls']:,} factor calls. A rough per-sample-mean projection is {extrap:.3f} s across all factor calls; this is extrapolation only, not measured elapsed time or expected savings. The combined strict lower/upper triangles were {analysis['combinedStrictTriangleExactZeroPercent']:.2f}% exact zeros by structural census, not observed skipped arithmetic. `performance.now` granularity and sparse selection limit interpretation. The {analysis['probeOverheadMillis']:.1f} ms overhead measures sample capture/compare inside brackets; it excludes per-factor window/counter/modulo, compilation, initial RHS, and strict-triangle census/traversal.\n\n"
 "Each cold report recorded 390 hypothesis work units across five hypotheses. The later warm report is a separate proof-cache hit with one hypothesis work unit; this does not imply numerical factor reuse. The pair preserved request and report fields outside declared timing/profile paths. Both strict plan-4 readers passed 43 negative checks each; the root validator passed all 79 metadata-negative cases. Root focused native/GWT/compiled-canary gates also passed. The source audit remains historically `SOURCE_ONLY_FIXTURE_PREPARED_NOT_VALIDATED`; the gate receipt binds its exact hash.\n\n"
 "The frozen measurement plan did not contain an explicit package-count assertion; the executed seed-10014 reports identify 40 packages and passed the strict readers. Treat that as fixture evidence, not broader cohort coverage.\n\n"
 "Raw application reports are gzip-compressed byte-for-byte. Metadata/log path redactions, when present, are enumerated with original/stored hashes in `provenance/archive-transforms.json`. The five exact source overlays and complete 1,341-input hash inventory reconstruct on top of [owned-zero-row-scan](../owned-zero-row-scan/README.md). No profile cache, compiled WAR, or browser profile is included.\n\n"
 "`inventory.json` covers every stored file except itself and `inventory.sha256`; gzip entries include decompressed hashes. The detached SHA-256 checks the inventory bytes; it is not keyed authentication.\n")
add('README.md',readme.encode(), 'derived from frozen receipts')

# Preserve the two compiled-browser cases and run custody, but never the browser profile/cache.
canary=S/'solve-profile-r1-canaries'
for name in ('identity.json','progress.json','result.json','run-start.json','runner-before.json','runner-after.json',
             'owned-processes-before.json','owned-processes-before-close.json','input-manifest-before.json',
             'input-manifest-after.json','spec.json'):
 copy_file(canary/name,f'canaries/{name}.gz',compress=True,jsoncheck=name.endswith('.json'))
for case in sorted((canary/'cases').glob('*')):
 if case.is_file() and case.suffix in ('.json','.txt'):
  copy_file(case,f'canaries/cases/{case.name}.gz',compress=True,jsoncheck=case.suffix=='.json')
copy_file(S/'solve-profile-r1-canaries.log','canaries/run.log.gz',compress=True)
copy_file(S/'solve-profile-r1-a07-reader.log','canaries/a07-reader.log.gz',compress=True)

# Record the exact derivation transformations separately from the inventory.
files['provenance/archive-transforms.json']=json.dumps(transforms,indent=2,ensure_ascii=False).encode()+b'\n'

# Verify privacy over every stored payload and decompressed gzip before writing anything.
script_payload=(HERE/'package_solve_profile.py').read_bytes()
if PRIVATE.search(script_payload):die('personal path in packager source')
for name,payload in files.items():
 if PRIVATE.search(payload):die(f'private path in stored payload: {name}')
 if name.endswith('.gz'):
  try:expanded=gzip.decompress(payload)
  except Exception as e:die(f'bad gzip payload {name}: {e}')
  if PRIVATE.search(expanded):die(f'private path in decompressed payload: {name}')

if any(p.name!='package_solve_profile.py' for p in HERE.iterdir()):die('refusing to overlay existing packet files')
for name,payload in sorted(files.items()):
 dest=HERE/Path(name); dest.parent.mkdir(parents=True,exist_ok=True); dest.write_bytes(payload)

inventory=[]
for p in sorted(x for x in HERE.rglob('*') if x.is_file() and x.name not in ('inventory.json','inventory.sha256')):
 rel=p.relative_to(HERE).as_posix(); data=p.read_bytes()
 item={'path':rel,'bytes':len(data),'sha256':sha(data)}
 if rel.endswith('.gz'):
  expanded=gzip.decompress(data); item.update({'gzip':True,'uncompressedBytes':len(expanded),'uncompressedSha256':sha(expanded)})
 inventory.append(item)
for name in files:
 if not any(x['path']==name for x in inventory):die(f'inventory omission: {name}')
inv=json.dumps({'schema':1,'files':inventory},indent=2,ensure_ascii=False).encode()+b'\n'
(HERE/'inventory.json').write_bytes(inv)
(HERE/'inventory.sha256').write_text(f'{sha(inv)}  inventory.json\n',encoding='ascii')
print(json.dumps({'status':'PACKAGED_INTERMEDIATE_EVIDENCE','files':len(inventory),'inventorySha256':sha(inv),
 'rawReportsByteExact':True,'privacyScan':'PASS stored+decompressed payloads','output':str(HERE)},indent=2))
