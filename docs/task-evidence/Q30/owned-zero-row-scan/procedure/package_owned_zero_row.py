#!/usr/bin/env python3
"""Fail-closed packager for the owned-zero-row intermediate trial; runs no gates."""
import argparse, gzip, hashlib, json, os, re, shutil, statistics, sys, uuid
from pathlib import Path

SCRATCH = Path(__file__).resolve().parent
LABELS = [
 ('owned-zero-row-01-control-7','control','7'), ('owned-zero-row-02-candidate-7','candidate','7'),
 ('owned-zero-row-03-candidate-10387','candidate','10387'), ('owned-zero-row-04-control-10387','control','10387'),
 ('owned-zero-row-05-control-10014','control','10014'), ('owned-zero-row-06-candidate-10014','candidate','10014'),
 ('owned-zero-row-07-candidate-7','candidate','7'), ('owned-zero-row-08-control-7','control','7')]
PAIRS = [('owned-zero-row-pair-1',0,1),('owned-zero-row-pair-2',2,3),('owned-zero-row-pair-3',4,5),('owned-zero-row-pair-4',6,7)]
SOURCE = {'driver':'scripts/verify-current-contracts.ps1',
          'test':'tests/contracts/Q30OwnedLuZeroRowContractTest.java',
          'cirsim':'src/com/lushprojects/circuitjs1/client/CirSim.java'}
RUN_FILES = ('identity.json','input-manifest-before.json','input-manifest-after.json',
 'owned-processes-before.json','owned-processes-before-close.json','progress.json','result.json',
 'run-start.json','runner-before.json','runner-after.json','spec.json')
PRIVATE = re.compile(rb'(?i)[a-z]:[/\\]+Users[/\\]+david(?:[/\\][^\\/\s"<>|?*:,;]+)*')

def die(s): raise RuntimeError(s)
def digest(b): return hashlib.sha256(b).hexdigest()
def jread(p):
 try: return json.loads(p.read_text(encoding='utf-8'))
 except Exception as e: die(f'bad JSON {p.name}: {e}')
def scrub(b, exact=False):
 if exact and PRIVATE.search(b): die('personal absolute path in byte-exact input')
 if not exact: b=PRIVATE.sub(b'[REDACTED_USER_PATH]',b)
 if PRIVATE.search(b): die('personal path remains')
 return b
def within(p, root):
 p=p.resolve(strict=True)
 try: p.relative_to(root.resolve(strict=True))
 except ValueError: die(f'path escaped expected root: {p}')
 return p
def inputmap(entries):
 m={x['path']:x for x in entries}
 if len(m)!=len(entries): die('duplicate source input path')
 return m

def main():
 ap=argparse.ArgumentParser(description=__doc__)
 ap.add_argument('--repo',required=True,type=Path)
 ap.add_argument('--destination',type=Path)
 a=ap.parse_args(); repo=a.repo.resolve(strict=True)
 if not (repo/'.git').exists(): die('--repo is not a Git worktree')
 out=(a.destination or repo/'docs/task-evidence/Q30/owned-zero-row-scan').absolute()
 if out.exists() or out.is_symlink(): die('destination exists; refusing overwrite')
 try: out.resolve(strict=False).relative_to((repo/'docs/task-evidence/Q30').resolve(strict=True))
 except ValueError: die('destination must stay below docs/task-evidence/Q30')

 # No output is created until timing rows, pair receipts, exact source lineage and final gates pass.
 planp=SCRATCH/'owned-zero-row-measurement-plan.json'; seqp=SCRATCH/'owned-zero-row-sequence-result.json'
 plan=jread(planp); seq=jread(seqp)
 expected=[(x,y,z) for x,y,z in LABELS]
 if seq.get('status')!='FOCUSED_EXPERIMENT_PASS_NOT_ACCEPTANCE' or seq.get('notRun')!=[]: die('sequence is incomplete or not an intermediate PASS')
 if digest(planp.read_bytes())!=seq.get('planSha256') or plan.get('status')!='PREDECLARED': die('plan hash/status mismatch')
 planrows=[(x.get('label'),x.get('arm'),str(x.get('seed'))) for x in plan.get('sequence',[])]
 rows=seq.get('rows',[]); actual=[(x.get('label'),x.get('plannedArm'),str(x.get('seed'))) for x in rows]
 if planrows!=expected or actual!=expected or len(seq.get('pairs',[]))!=4: die('timing order/count differs from exact eight-row plan')
 trial=within(SCRATCH/seq['sourcePairDirectory'],SCRATCH)
 auditp=trial/'source-audit.json'
 if digest(auditp.read_bytes())!=seq.get('sourcePairAuditSha256'): die('source-pair audit hash mismatch')
 audit=jread(auditp)
 if audit.get('status')!='SOURCE_ONLY_PAIR_PREPARED_NOT_VALIDATED' or audit.get('head')!=seq.get('rootHead') or audit.get('generatedWarCopied') is not False: die('source-only pair identity/status mismatch')
 if audit.get('differences')!=[SOURCE['cirsim']] or sorted(audit.get('sharedTestChanges',[]))!=sorted([SOURCE['driver'],SOURCE['test']]): die('source diff is outside reviewed three-file scope')
 arms=audit.get('armInputs',{}); control=inputmap(arms.get('control',[])); candidate=inputmap(arms.get('candidate',[]))
 prior=repo/'docs/task-evidence/Q30/validated-factor-input'
 prior_inv=prior/'inventory.json'; prior_sum=prior/'inventory.sha256'; prior_manifestp=prior/'source/trial-candidate-input-manifest.json'
 prior_overlayp=prior/'source/control-overlay-manifest.json'; prior_cirsimp=prior/'source/candidate-overlays/src/com/lushprojects/circuitjs1/client/CirSim.java.gz'
 if any(not p.is_file() for p in (prior_inv,prior_sum,prior_manifestp,prior_overlayp,prior_cirsimp)): die('committed validated-factor-input baseline is missing')
 if prior_sum.read_text().split()[0]!=digest(prior_inv.read_bytes()): die('prior inventory checksum mismatch')
 old=inputmap(jread(prior_manifestp).get('inputs',[])); oldc=old.get(SOURCE['cirsim'])
 inherited=gzip.decompress(prior_cirsimp.read_bytes())
 if not oldc or digest(inherited)!=oldc['sha256'] or len(inherited)!=oldc['bytes']: die('inherited candidate CirSim does not match old manifest')
 if len(control)!=1339 or len(candidate)!=1339: die('R2 arm input count mismatch')
 if set(control)-set(old)!={SOURCE['test']} or set(old)-set(control): die('R2 control path set is not old candidate plus one test')
 changed={p for p in old if old[p]['sha256']!=control[p]['sha256']}
 if changed!={SOURCE['driver']} or control[SOURCE['cirsim']]['sha256']!=oldc['sha256']: die('R2 control inheritance differs beyond driver')
 if {p for p in control if control[p]['sha256']!=candidate[p]['sha256']}!={SOURCE['cirsim']}: die('R2 candidate differs beyond CirSim.java')
 focused=jread(SCRATCH/'owned-zero-row-gates-r2.json')
 if focused.get('status')!='FOCUSED_GATES_PASS_NOT_ACCEPTANCE' or len(focused.get('steps',[]))!=7 or any(x.get('exitCode')!=0 for x in focused['steps']): die('R2 focused gates incomplete/failed')
 focusedp=SCRATCH/'owned-zero-row-gates-r2.json'
 if digest(focusedp.read_bytes())!=seq.get('focusedGateReceiptSha256'): die('focused gate receipt hash mismatch')
 final=jread(SCRATCH/'owned-zero-row-final-gates.json')
 if final.get('status')!='FINAL_SOURCE_GATES_PASS_PENDING_ROOT_INPUT_EQUALITY' or any(x.get('exitCode')!=0 for x in final.get('steps',[])): die('final index gates incomplete/failed')
 equal=jread(SCRATCH/'owned-zero-row-root-runtime-equality.json')
 if equal.get('status')!='PASS' or equal.get('inputCount')!=1525 or equal.get('completeWarFiles')!=392: die('root runtime equality receipt incomplete')
 if any(equal.get(k) for k in ('consumedInputMismatches','extraWarFiles','missingWarFiles','warByteDifferences')): die('root runtime equality reports differences')

 bylabel={}
 for row,(label,arm,seed) in zip(rows,LABELS):
  if row.get('runnerOutcome')!='PASS' or row.get('exitCode')!=0: die(f'row failed: {label}')
  c=row.get('cleanup',{}); h=row.get('hostAudit',{})
  if c.get('status')!='PASS' or not c.get('serverStopped') or c.get('ownedSurvivors') or c.get('errors'): die(f'cleanup failed: {label}')
  errors=h.get('errorArrays',{})
  if h.get('status')!='PASS' or h.get('outcome')!='PASS' or h.get('timedOut') or h.get('navigationError') or h.get('inputAuditStatus')!='PASS' or not errors or any(errors.values()): die(f'host/input errors: {label}')
  run=within(SCRATCH/label,SCRATCH)
  if any(not (run/n).is_file() for n in RUN_FILES): die(f'missing run provenance: {label}')
  report=within(run/h['reportFile'],run)
  b=report.read_bytes()
  if len(b)!=h['reportLength'] or digest(b)!=h['reportSha256']: die(f'report hash mismatch: {label}')
  bylabel[label]=row

 # Validate the four completed parity receipts against the raw app timing fields.
 pairdata=[]
 for tag,ia,ib in PAIRS:
  pair=seq['pairs'][len(pairdata)]; first,second=LABELS[ia][0],LABELS[ib][0]
  if pair.get('tag')!=tag or pair.get('status')!='PASS' or pair.get('validatorExitCode')!=0 or pair.get('labelsInPlanOrder')!=[first,second]: die(f'pair failed/order changed: {tag}')
  rp=SCRATCH/pair['receiptFile']; receipt=jread(rp)
  if receipt.get('status')!='PASS' or len(receipt.get('runs',[]))!=2: die(f'pair receipt invalid: {tag}')
  arow,brow=bylabel[first],bylabel[second]; tc=pair.get('timingComparison',{})
  diffs=(arow['cold']['elapsedMs']-brow['cold']['elapsedMs'], arow['cold']['proofElapsedMs']-brow['cold']['proofElapsedMs'], arow['cold']['routingElapsedMs']-brow['cold']['routingElapsedMs'])
  if diffs!=(tc.get('appColdElapsedMsAminusB'),tc.get('appColdProofElapsedMsAminusB'),tc.get('appColdRoutingElapsedMsAminusB')): die(f'pair deltas differ from raw rows: {tag}')
  sign=1 if arow['plannedArm']=='control' else -1
  pairdata.append({'tag':tag,'seed':pair['seed'],'controlMinusCandidateColdMs':sign*diffs[0],
   'controlMinusCandidateProofMs':sign*diffs[1],'controlMinusCandidateRoutingMs':sign*diffs[2]})

 files={}
 transforms=[]
 def put(name,data,exact=False,json_file=False):
  if Path(name).is_absolute() or '..' in Path(name).parts: die(f'unsafe output path: {name}')
  original=data
  data=scrub(data,exact)
  if not exact and not name.endswith('.gz'):
   data=b'\n'.join(line.rstrip() for line in data.replace(b'\r\n',b'\n').split(b'\n')).rstrip(b'\n')+b'\n'
  if original!=data:
   transforms.append({'path':name,'originalSha256':digest(original),'storedSha256':digest(data),'transform':'personal path redaction and archival text whitespace normalization only'})
  if json_file:
   try: json.loads(data.decode('utf-8'))
   except Exception as e: die(f'scrubbed JSON invalid {name}: {e}')
  if name in files and files[name]!=data: die(f'conflicting output path: {name}')
  files[name]=data
 def copied(outname,src,exact=False,json_file=False): put(outname,src.read_bytes(),exact,json_file)
 def obj(outname,value): put(outname,json.dumps(value,indent=2,ensure_ascii=False).encode()+b'\n',False,True)
 def report(outname,src,row):
  data=src.read_bytes()
  if digest(data)!=row['hostAudit']['reportSha256'] or len(data)!=row['hostAudit']['reportLength']: die(f'raw report identity changed: {row["label"]}')
  scrub(data,True); put(outname,gzip.compress(data,mtime=0),True)

 # Exact, bounded source overlays; inherited baseline stays in its already committed packet.
 srcfiles=[]
 for label,key in (("r2-candidate",'cirsim'),("r2-common-driver",'driver'),("r2-common-test",'test')):
  armroot=trial/('candidate' if key=='cirsim' else 'control')
  path=within(armroot/SOURCE[key],armroot); data=path.read_bytes()
  arm='candidate' if key=='cirsim' else 'control'; entry=inputmap(arms[arm])[SOURCE[key]]
  if digest(data)!=entry['sha256'] or len(data)!=entry['bytes']: die(f'source payload mismatch: {label}')
  scrub(data,True); name=f'source/{label}.bin.gz'; put(name,gzip.compress(data,mtime=0),True)
  srcfiles.append({'name':label,'path':SOURCE[key],'sha256':digest(data),'bytes':len(data),'stored':name})
 put('source/r2-source-audit.json',auditp.read_bytes(),False,True)
 put('source/prior-candidate-input-manifest.json',prior_manifestp.read_bytes(),False,True)
 put('source/prior-control-overlay-manifest.json',prior_overlayp.read_bytes(),False,True)
 put('source/prior-candidate-CirSim.java.gz',prior_cirsimp.read_bytes(),True)

 # Preserve the exact staged-index source snapshot separately from the timed trial source.
 integrationp=SCRATCH/'owned-zero-row-integration.json'; integration=jread(integrationp)
 indexroot=within(Path((SCRATCH/'owned-zero-row-index-path.txt').read_text().strip()),SCRATCH)
 indexauditp=indexroot/'index-source-audit.json'; indexaudit=jread(indexauditp); ix=inputmap(indexaudit.get('inputs',[]))
 staged=[]
 for item in integration.get('files',[]):
  path=item.get('path')
  if path not in SOURCE.values() or path not in ix: die('integration path outside reviewed three files')
  data=within(indexroot/Path(path),indexroot).read_bytes()
  if digest(data)!=item.get('stagedSha256') or digest(data)!=ix[path].get('sha256'): die(f'index source hash mismatch: {path}')
  scrub(data,True); name='source/final-index/'+Path(path).name+'.gz'; put(name,gzip.compress(data,mtime=0),True)
  staged.append({'path':path,'sha256':digest(data),'bytes':len(data),'stored':name})
 if {x['path'] for x in staged}!=set(SOURCE.values()): die('integration receipt does not list exactly three files')
 for n,p in [('integration-receipt.json',integrationp),('index-source-audit.json',indexauditp),
              ('final-gates.json',SCRATCH/'owned-zero-row-final-gates.json'),('root-runtime-equality.json',SCRATCH/'owned-zero-row-root-runtime-equality.json')]:
  put('source/'+n,p.read_bytes(),False,True)
 obj('source/lineage.json',{'scope':'intermediate only; not Q30 acceptance',
  'priorBundle':'../validated-factor-input','priorInventorySha256':digest(prior_inv.read_bytes()),
  'priorCandidateManifestSha256':digest(prior_manifestp.read_bytes()),'inheritedCandidateCirSimSha256':digest(inherited),
  'r2SourceAuditSha256':digest(auditp.read_bytes()),'r2Payloads':srcfiles,
  'finalIndexTree':indexaudit.get('indexTree'),'finalIndexInputCount':len(ix),'stagedPayloads':staged,
  'rootRuntimeEquality':{'status':equal['status'],'inputs':equal['inputCount'],'warFiles':equal['completeWarFiles']},
  'note':'Timed R2 files and staged-index files are separately identified; no equality is inferred from names alone.'})
 # Queue exact reports plus only useful run metadata; browser profiles/caches are excluded.
 runmeta=[]
 for label,arm,seed in LABELS:
  row=next(x for x in rows if x['label']==label); rr=SCRATCH/label
  for name in RUN_FILES: put(f'runs/{label}/{name}',(rr/name).read_bytes(),False,name.endswith('.json'))
  rp=within(rr/row['hostAudit']['reportFile'],rr)
  report(f'runs/{label}/application-report.json.gz',rp,row)
  req=rp.with_name(rp.name.replace('.report.json','.json')); state=rp.with_name(rp.name.replace('.report.json','.state.txt'))
  put(f'runs/{label}/{req.name}',req.read_bytes(),False,True); put(f'runs/{label}/{state.name}',state.read_bytes())
  for support in (SCRATCH/f'{label}-summary.json',SCRATCH/f'{label}.log'):
   put(f'runs/{label}/{support.name}',support.read_bytes(),False,support.suffix=='.json')
  runmeta.append({'label':label,'arm':arm,'seed':seed,'coldMs':row['cold']['elapsedMs'],
   'proofMs':row['cold']['proofElapsedMs'],'routingMs':row['cold']['routingElapsedMs'],
   'workUnits':row['cold']['totalWork'],'hypothesisWorkUnits':row['cold']['hypothesisWork'],
   'reportSha256':row['hostAudit']['reportSha256'],'reportBytes':row['hostAudit']['reportLength']})

 # Pair receipts, strict wrappers/readers and their maintained source are cross-checked.
 for tag,ia,ib in PAIRS:
  pair=seq['pairs'][PAIRS.index((tag,ia,ib))]; receiptp=SCRATCH/pair['receiptFile']
  receipt=jread(receiptp); put(f'pairs/{tag}-receipt.json',receiptp.read_bytes(),False,True)
  log=SCRATCH/f'{tag}-validation.log'; put(f'pairs/{tag}-validation.log',log.read_bytes())
  for ev in receipt.get('runs',[]):
   sr=ev.get('strictReader',{})
   for key,hashkey in (('wrapperFile','wrapperSha256'),('readerOutputFile',None)):
    name=sr.get(key); p=SCRATCH/name
    if not p.is_file(): die(f'missing strict-reader output: {name}')
    if hashkey and digest(p.read_bytes())!=sr.get(hashkey): die(f'strict-wrapper hash mismatch: {name}')
    put(f'pairs/{name}',p.read_bytes(),False,p.suffix=='.json')
   for pathkey,hashkey in (('readerPath','readerSha256'),('parserPath','parserSha256')):
    rel=sr.get(pathkey); p=repo/Path(rel)
    if not p.is_file() or digest(p.read_bytes())!=sr.get(hashkey): die(f'maintained strict-reader source mismatch: {rel}')
    put('procedure/readers/'+Path(rel).name,p.read_bytes(),True)

 # Keep the failed first revision, the seven focused R2 gates, and final staged-source gates.
 gatefiles=[
  ('native-owned-zero-row-candidate.json','gates/r1/native-failure.json'),('native-owned-zero-row-candidate.log','gates/r1/native-failure.log'),
  ('native-owned-zero-row-candidate-r2.json','gates/r2/native.json'),('native-owned-zero-row-candidate-r2.log','gates/r2/native.log'),
  ('native-owned-zero-row-candidate-r2-receipt.txt','gates/r2/native-receipt.txt'),
  ('owned-zero-row-gates-r2.json','gates/r2/focused.json'),('owned-zero-row-gates-r2.log','gates/r2/focused.log'),
  ('owned-zero-row-r2-candidate-gwt.json','gates/r2/candidate-gwt.json'),('owned-zero-row-r2-candidate-gwt.log','gates/r2/candidate-gwt.log'),
  ('owned-zero-row-r2-control-gwt.json','gates/r2/control-gwt.json'),('owned-zero-row-r2-control-gwt.log','gates/r2/control-gwt.log'),
  ('owned-zero-row-r2-candidate-canaries-spec.json','gates/r2/candidate-canaries-spec.json'),
  ('owned-zero-row-r2-candidate-canaries-summary.json','gates/r2/candidate-canaries-summary.json'),
  ('owned-zero-row-r2-candidate-canaries.log','gates/r2/candidate-canaries.log'),
  ('owned-zero-row-r2-control-canaries-spec.json','gates/r2/control-canaries-spec.json'),
  ('owned-zero-row-r2-control-canaries-summary.json','gates/r2/control-canaries-summary.json'),
  ('owned-zero-row-r2-control-canaries.log','gates/r2/control-canaries.log'),
  ('owned-zero-row-final-gates.json','gates/final/index-gates.json'),('owned-zero-row-final-gates.log','gates/final/index-gates.log'),
  ('native-owned-zero-row-index.json','gates/final/index-native.json'),('native-owned-zero-row-index.log','gates/final/index-native.log'),
  ('native-owned-zero-row-index-receipt.txt','gates/final/index-native-receipt.txt'),
  ('owned-zero-row-index-gwt.json','gates/final/index-gwt.json'),('owned-zero-row-index-gwt.log','gates/final/index-gwt.log'),
  ('owned-zero-row-index-canary-spec.json','gates/final/index-canary-spec.json'),
  ('owned-zero-row-index-canary-summary.json','gates/final/index-canary-summary.json'),
  ('owned-zero-row-index-canary.log','gates/final/index-canary.log'),
  ('owned-zero-row-root-gwt.json','gates/final/root-gwt.json'),('owned-zero-row-root-gwt.log','gates/final/root-gwt.log'),
  ('owned-zero-row-root-runtime-equality.json','gates/final/root-runtime-equality.json')]
 for srcname,dst in gatefiles:
  p=SCRATCH/srcname
  if not p.is_file(): die(f'missing required gate artifact {srcname}')
  put(dst,p.read_bytes(),False,p.suffix=='.json')
 for label,_,_ in LABELS: put(f'timing/{label}.log',(SCRATCH/f'{label}.log').read_bytes())
 for srcname,dst in [('owned-zero-row-measurement-plan.json','timing/plan.json'),
                      ('owned-zero-row-sequence-result.json','timing/sequence-result.json'),
                      ('owned-zero-row-sequence.log','timing/sequence.log')]:
  p=SCRATCH/srcname; put(dst,p.read_bytes(),False,p.suffix=='.json')
 for name in ('native-owned-zero-row.ps1','prepare_owned_zero_row_pair.py','prepare_owned_zero_row_pair_r2.py',
  'prepare_owned_zero_row_index.py','run_owned_zero_row_gates.py','run_owned_zero_row_gates_r2.py',
  'run_owned_zero_row_final_gates.py','run_owned_zero_row_sequence.py','run_timing.py',
  'validate_current_q30_timing_pair.py','run_current_solver_canaries.py','run_staged_solver_canary.py',
  'read_a07.ps1','build-run.ps1','integrate_owned_zero_row.py','audit_owned_zero_row_root_runtime.py'):
  p=SCRATCH/name
  if p.is_file(): put('procedure/'+name,p.read_bytes())
 for name in ('owned-zero-row-r2-source-pair-path.txt','owned-zero-row-source-pair-path.txt','owned-zero-row-index-path.txt'):
  p=SCRATCH/name
  if p.is_file(): put('provenance/'+name+'.scrubbed',p.read_bytes())
 review=SCRATCH/'scratch/scratch/owned-zero-row-independent-review.md'
 if review.is_file(): put('provenance/independent-review.md',review.read_bytes())

 # Preserve compiled gate raw reports and their actual runner/input/cleanup receipts.
 for runlabel in ('owned-zero-row-r2-candidate-canaries','owned-zero-row-r2-control-canaries','owned-zero-row-index-canary'):
  rr=SCRATCH/runlabel
  for name in RUN_FILES:
   put(f'gates/browser/{runlabel}/{name}',(rr/name).read_bytes(),False,True)
  for rp in sorted((rr/'cases').glob('*')):
   if not rp.is_file(): continue
   if rp.name.endswith('.report.json'):
    raw=rp.read_bytes();scrub(raw,True)
    put(f'gates/browser/{runlabel}/{rp.name}.gz',gzip.compress(raw,mtime=0),True)
   elif rp.suffix in ('.json','.txt'):
    put(f'gates/browser/{runlabel}/{rp.name}',rp.read_bytes(),False,rp.suffix=='.json')
 r1test=SCRATCH/'scratch/owned-zero-row-draft/r1-native-test.java'
 put('gates/r1/native-test.java.gz',gzip.compress(scrub(r1test.read_bytes(),True),mtime=0),True)
 put('procedure/repair_owned_zero_row_r2.py',(SCRATCH/'repair_owned_zero_row_r2.py').read_bytes())

 # Derive paired savings from the raw row order, never from assumed expected values.
 metric={'rows':runmeta,'pairs':pairdata}
 for key in ('controlMinusCandidateColdMs','controlMinusCandidateProofMs','controlMinusCandidateRoutingMs'):
  metric['median'+key[0].upper()+key[1:]]=statistics.median(x[key] for x in pairdata)
 c7=[x for x in runmeta if x['arm']=='control' and x['seed']=='7']; f7=[x for x in runmeta if x['arm']=='candidate' and x['seed']=='7']
 metric['seed7WithinArmRangesMs']={'controlCold':max(x['coldMs'] for x in c7)-min(x['coldMs'] for x in c7),
  'candidateCold':max(x['coldMs'] for x in f7)-min(x['coldMs'] for x in f7),
  'controlProof':max(x['proofMs'] for x in c7)-min(x['proofMs'] for x in c7),
  'candidateProof':max(x['proofMs'] for x in f7)-min(x['proofMs'] for x in f7)}
 obj('timing/derived-metrics.json',metric)
 # README is data-derived; this experiment is never labeled Q30 acceptance.
 table=['| Run | Arm | Seed | Cold ms | Proof ms | Route ms | Work units | Hypothesis work units |','|---|---|---:|---:|---:|---:|---:|---:|']
 for x in runmeta: table.append(f"| {x['label']} | {x['arm']} | {x['seed']} | {x['coldMs']} | {x['proofMs']} | {x['routingMs']} | {x['workUnits']} | {x['hypothesisWorkUnits']} |")
 pairs=['| Pair | Seed | Control−candidate cold ms | Proof ms | Route ms |','|---|---:|---:|---:|---:|']
 for x in pairdata: pairs.append(f"| {x['tag']} | {x['seed']} | {x['controlMinusCandidateColdMs']} | {x['controlMinusCandidateProofMs']} | {x['controlMinusCandidateRoutingMs']} |")
 readme=("# Owned zero-row scan — intermediate evidence\n\n"
  "**FOCUSED_EXPERIMENT_PASS_NOT_ACCEPTANCE.** Q30 remains unqualified. The fixed 90,000 ms, 640 work-unit, and 5,000 ms per-operation contracts were not changed.\n\n"
  "The candidate short-circuits each private owned nonlinear LU zero-row scan at its first nonzero while still checking every row. General finite validation, factor/solve arithmetic and ordering, proof work, and work accounting remain. The timed R2 source pair and final staged-index source are separately identified in `source/lineage.json`; inherited source reconstruction uses the adjacent committed [validated-factor-input baseline](../validated-factor-input/README.md).\n\n"
  "## Predeclared timing rows\n\n"+'\n'.join(table)+'\n\n'+'\n'.join(pairs)+'\n\n'
  f"Median paired savings: cold **{metric['medianControlMinusCandidateColdMs']} ms**, proof **{metric['medianControlMinusCandidateProofMs']} ms**, routing **{metric['medianControlMinusCandidateRoutingMs']} ms**. Seed-7 cold ranges: control {metric['seed7WithinArmRangesMs']['controlCold']} ms, candidate {metric['seed7WithinArmRangesMs']['candidateCold']} ms; proof ranges: control {metric['seed7WithinArmRangesMs']['controlProof']} ms, candidate {metric['seed7WithinArmRangesMs']['candidateProof']} ms. These four pairs are a focused trial, not a population claim.\n\n"
  "Warm phases are separate private proof-cache hits (one hypothesis work unit); they are not numerical factor reuse evidence. No signed-zero bit-identity claim is made.\n\n"
  "R1's failed native result is preserved. R2 focused gates and final exact-index native/GWT/A07 gates passed; the root build/runtime equality receipt reports 1,525 consumed inputs and 392 WAR files equal to the measured candidate. This evidence does not cover the full normal acceptance or scale corpus. Raw application reports are preserved byte-for-byte in gzip; metadata paths are scrubbed.\n\n"
  "`inventory.json` lists every stored file except itself and `inventory.sha256`; gzip entries include decompressed byte counts and hashes. The detached inventory checksum checks the inventory bytes; it is not keyed authentication.\n")
 put('README.md',readme.encode('utf-8'))
 put('procedure/package_owned_zero_row.py',Path(__file__).read_bytes())
 note=SCRATCH/'NOTES.md'
 if note.is_file(): put('procedure/NOTES.md',note.read_bytes())

 obj('provenance/archive-transforms.json',transforms)

 # Inventory covers stored bytes and decompressed gzip payloads; scan both for private paths.
 entries=[]
 for name,data in sorted(files.items()):
  scrub(data,True)
  ent={'path':name,'bytes':len(data),'sha256':digest(data)}
  if name.endswith('.gz'):
   payload=gzip.decompress(data); scrub(payload,True)
   ent.update({'uncompressedBytes':len(payload),'uncompressedSha256':digest(payload)})
  entries.append(ent)
 inv=json.dumps({'schema':1,'files':entries},indent=2).encode()+b'\n'; invsha=digest(inv)
 stage=out.parent/f'.{out.name}.building-{uuid.uuid4().hex}'
 stage.parent.mkdir(parents=True,exist_ok=True); stage.mkdir()
 try:
  for name,data in files.items():
   dest=stage/Path(name); dest.parent.mkdir(parents=True,exist_ok=True); dest.write_bytes(data)
  (stage/'inventory.json').write_bytes(inv)
  (stage/'inventory.sha256').write_text(f'{invsha}  inventory.json\n',encoding='ascii')
  for p in stage.rglob('*'):
   if p.is_file():
    raw=p.read_bytes(); scrub(raw,True)
    if p.suffix=='.gz': scrub(gzip.decompress(raw),True)
  if out.exists() or out.is_symlink(): die('destination appeared during generation')
  os.replace(stage,out)
 except Exception:
  # Leave the unique partial directory for explicit review; never recursively delete it here.
  raise
 print(json.dumps({'status':'PACKAGED_INTERMEDIATE_NOT_ACCEPTANCE','artifactCount':len(entries),
  'inventorySha256':invsha,'destination':str(out)},indent=2))
 return 0

if __name__=='__main__':
 try: raise SystemExit(main())
 except Exception as exc:
  print(f'package refused: {exc}',file=sys.stderr); raise SystemExit(2)
