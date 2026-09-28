from pathlib import Path
import gzip,hashlib,json,re,subprocess,sys
s=Path(__file__).resolve().parent;r=Path(sys.argv[1]);p=r/'docs/task-evidence/Q30/live-lu-support-r1'
assert not p.exists();p.mkdir(parents=True)
sha=lambda b:hashlib.sha256(b).hexdigest()
entries=[]
def sanitize(raw):
    t=raw.decode('utf-8')
    for root,token in [(s,'<TASK_SCRATCH>'),(r,'<TASK_REPO>')]:
        for v in sorted({str(root),str(root).replace('\\','\\\\'),root.as_posix()},key=len,reverse=True):t=t.replace(v,token)
    t=re.sub(r'''(?i)[A-Z]:(?:\\+|/)+Users(?:\\+|/)+[^\\/\s"'<>]+''','<USER_HOME>',t)
    assert not re.search(r'''(?im)(?:authorization|proxy-authorization)\s*[:=]\s*(?:bearer\s+)?[A-Za-z0-9._~+/=-]{12,}''',t)
    return t.encode('utf-8')
def put(dest,raw,ref,exact=False,compress=False,generated=False):
    payload=raw if exact else sanitize(raw)
    if exact:assert sanitize(raw)==raw,dest
    transform='generated' if generated else ('exact' if payload==raw else 'sanitize-task-and-user-roots')
    if compress or len(payload)>262144 or dest.endswith('.patch'):
        dest+='.gz';stored=gzip.compress(payload,mtime=0);transform='gzip-mtime-0;'+transform
    else:stored=payload
    out=p/dest;assert not out.exists();out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(stored)
    entries.append({'path':dest,'sourceRef':ref,'sourceBytes':len(raw),'sourceSha256':sha(raw),
      'storedBytes':len(stored),'storedSha256':sha(stored),'payloadBytes':len(payload),'payloadSha256':sha(payload),'transform':transform})
def copy(rel,dest=None,exact=False,compress=False):put(dest or rel,(s/rel).read_bytes(),'scratch/'+rel,exact,compress)
def generated(dest,d):put(dest,(json.dumps(d,indent=2)+'\n').encode(),'generated:package_live_lu_support_trial_root.py',generated=True)
for name in ['live-lu-support-r1-measurement-plan.json','live-lu-support-r1-execution-binding.json','live-lu-support-r1-sequence-result.json','live-lu-support-r1-root-interpretation.json','live-lu-support-r1-source-review.json','live-lu-support-r2-oracle-correction.json']:
    copy(name,'experiment/'+name)
for label in [x['label'] for x in json.loads((s/'live-lu-support-r1-sequence-result.json').read_bytes())['rows']]+['live-lu-support-r1-canaries']:
    for f in sorted((s/label).iterdir()):
        if f.is_file():copy(f.relative_to(s).as_posix(),'hosts/'+label+'/'+f.name)
    for f in sorted((s/label/'cases').iterdir()):
        if f.is_file():copy(f.relative_to(s).as_posix(),'hosts/'+label+'/cases/'+f.name,exact=f.name.endswith('.report.json'),compress=f.name.endswith('.report.json'))
    for suffix in ['-summary.json','-spec.json','.log']:
        f=s/(label+suffix)
        if f.exists():copy(f.relative_to(s).as_posix(),'hosts/'+label+'/'+f.name)
for f in sorted(s.iterdir()):
    if f.is_file() and (re.fullmatch(r'live-lu-support-r1-pair-[12]\.log',f.name) or (f.name.startswith('live-lu-support-r1-0') and any(t in f.name for t in ['-strict-wrapper-','-strict-reader-','-timing-parity-']))):
        copy(f.name,'comparisons/'+f.name)
for stem in ['live-lu-support-r1-native-tracker-preflight','live-lu-support-r1-native9','live-lu-support-r2-native9','live-lu-support-r1-gwt5','live-lu-zero-sign-witness-r1-native']:
    for suffix in ['.json','.log','-receipt.txt']:
        if (s/(stem+suffix)).exists():copy(stem+suffix,'gates/'+stem+suffix)
copy('live-lu-support-r1-a07-reader.log','gates/live-lu-support-r1-a07-reader.log')
audit=json.loads((s/'live-lu-support-r1-preparation/source-audit.json').read_bytes())
copy('live-lu-support-r1-preparation/source-audit.json','source/source-audit.json',compress=True)
before={e['path']:e for e in audit['armInputs']['control']};after={e['path']:e for e in audit['armInputs']['candidate']}
changed=sorted(k for k in set(before)|set(after) if before.get(k)!=after.get(k))
assert len(changed)==6
overlays=[]
for rel in changed:
    dest='source/final-overlays/'+rel.replace('/','__')
    copy('live-lu-support-r1-preparation/candidate/'+rel,dest,exact=True,compress=True)
    overlays.append({'path':rel,'storedAt':dest+'.gz','sha256':after[rel]['sha256'],'bytes':after[rel]['bytes']})
for directory,dest in [('live-lu-support-r1-before-zero-sign-correction','source/before-zero-sign-correction'),('scratch/live-lu-support-r1-tracker-draft','source/tracker-draft'),('scratch/live-lu-support-r1-numeric-test','source/numeric-draft')]:
    for f in sorted((s/directory).rglob('*')):
        if f.is_file() and f.suffix in {'.java','.md','.json'}:
            copy(f.relative_to(s).as_posix(),dest+'/'+f.relative_to(s/directory).as_posix(),compress=f.suffix=='.java')
copy('live-lu-zero-sign-witness-r1/source-audit.json','source/unchanged-baseline-witness/source-audit.json',compress=True)
copy('live-lu-zero-sign-witness-r1/reference/tests/contracts/LuAcceptedZeroSignWitnessContractTest.java','source/unchanged-baseline-witness/LuAcceptedZeroSignWitnessContractTest.java',exact=True,compress=True)
for name in ['live-lu-support-owner-contract.md','live-lu-support-r1-root-wiring-review.md','root-live-lu-kernel-obligations.md','root-live-lu-prototype-notes.md','live-lu-support-r1-independent-pre-timing-audit.md','generic-wire-observation-contract.md','lu-solve-support-feasibility.md']:
    copy('scratch/'+name,'reviews/'+name)
helpers=['prepare_live_lu_support_r1_root.py','wire_live_lu_support_r1_root.py','register_live_lu_support_tests_root.py','repair_live_lu_support_owned_entry_root.py','repair_live_lu_support_stamp_order_root.py','integrate_live_lu_support_tracker_root.py','integrate_live_lu_numeric_test_root.py','prepare_lu_zero_sign_witness_root.py','correct_live_lu_oracle_zero_sign_root.py','audit_live_lu_support_source_root.py','prepare_live_lu_support_measurement_root.py','run_live_lu_support_r1_sequence_root.py','interpret_live_lu_support_trial_root.py','run_timing.py','validate_current_q30_timing_pair.py','run_current_solver_canaries.py','read_a07.ps1','native-live-lu-support.ps1','native-lu-zero-sign-witness.ps1','build-run.ps1','package_live_lu_support_trial_root.py']
for name in helpers:copy(name,'procedures/'+name)
for rel in ['tests/browser/compiled_attribute_acceptance.py','docs/task-evidence/Q30/scale-plan-4/check_coordinator.py','docs/task-evidence/Q30/scale-plan-4/check_d01.py']:
    put('procedures/consumed/'+rel.replace('/','__'),(r/rel).read_bytes(),'scratch/../INVALID')
    # These uncommitted consumed readers are named as a workspace snapshot,
    # never falsely attributed to a Git commit.
    entries[-1]['sourceRef']='workspace-snapshot:'+rel
parent=[]
commit='f94d523232677760c2af24cde886f4a92a09833d'
for rel in ['docs/task-evidence/Q30/lu-support-census-r4/source/overlay-map.json','docs/task-evidence/Q30/lu-support-census-r4/source/parent/pivot-lower-fusion-r2/stored-parent-source-audit.json.gz','docs/task-evidence/Q30/pivot-lower-fusion-r2/source/provenance.json']:
    raw=subprocess.check_output(['git','-c','core.longpaths=true','show',commit+':'+rel],cwd=r)
    parent.append({'commit':commit,'path':rel,'bytes':len(raw),'sha256':sha(raw)})
generated('source/overlay-map.json',{'schema':1,'baselineOriginalAuditSha256':'0d27e176dc46afb4db91316570f4674e52b51ce2b462c64f6b6a37b98cbec197','sourceInputsBefore':len(before),'sourceInputsAfter':len(after),'sourceAuditSha256':sha((s/'live-lu-support-r1-preparation/source-audit.json').read_bytes()),'exactFinalOverlays':overlays,'pinnedParentReconstructionReferences':parent,'instructions':'Reconstruct the measured fusion baseline using pinned parent source lineage, verify all 1340 control input entries in source-audit, then apply these six complete overlays and verify all 1343 candidate entries. No generated WAR or full checkout is included.'})
readme='''# Q30 live-support LU trial — INCONCLUSIVE / NOT INTEGRATED

Q30 remains BLOCKED / NOT ACCEPTED and disabled for normal players. No prototype source was integrated. Frozen limits remain 90,000 ms cumulative, 640 shared work units and 5,000 ms per active operation; these private measurements retain their separate ceiling. No full final matrix or later milestone ran, and nothing was pushed.

The generic prototype tracks a monotone bipartite superset of original matrix support plus every mapped runtime stamp, including zero/cancelling stamps. Matching owner/shape and balanced connected components permit shorter LU scan domains. Global pivot order, last equal-magnitude tie, arithmetic/update order and finite guards remain. A local zero maximum resumes full LU for the rest of that factor. Analyze/stop/snapshot restoration invalidate the owner; factor scratch is cleared. No solver steps, hypotheses, physical admission, generation/replay/order, service/repair/retest or cache requirements change.

| Fresh private seed 10014, 40 packages | Cold ms | Proof ms | Routing ms |
| --- | ---: | ---: | ---: |
| Control 1 | 122294 | 88331 | 26715 |
| Candidate 1 | 116584 | 82467 | 26892 |
| Candidate 2 | 114072 | 81064 | 25860 |
| Control 2 | 111612 | 78867 | 25772 |

The first pair saves 5,710 ms cold / 5,864 ms proof; the reverse pair regresses 2,460 / 2,197 ms. Controls span 10,682 ms, candidates 2,512 ms. The frozen repeat rule fails, so the remaining four 37/20-package rows are NOT RUN. There is no attributable speed improvement. Tracking overhead versus saved scans remains unresolved; no OS, scheduling or thermal cause is established.

Both full non-timing proof/replay comparisons and 43 strict corruptions per arm PASS. Every cold row retains 490 work and 390 hypothesis units; warm private-cache rows retain 101 work and one hypothesis unit. All four owned host cleanups PASS. Application timing fields use their existing wall clock; whole-host operation is monotonic, cleanup separate. Fresh browsers exclude warm-proof reuse, not all host-state variability. The earlier committed stage profile provides the monotonic stage/purpose/retry breakdown.

Focused native9 PASS (56.037 s): tracker 1,661 and numeric 2,148 assertions, plus unchanged execution/temporal/budget/policy/generation contracts. Actual JDK8/GWT5 PASS (100.522 s). Compiled A07/disabled-normal PASS (7.584 s; cleanup 1.350 s), strict A07 plus three corruption negatives PASS. The source audit binds 1,343 inputs and measured runtime 1,526; sources/builds stayed unchanged through the sequence.

Failures are retained: preflight exit 2 before compilation for pending test registration; first native9 exit 2 at the new historical dense-oracle signed-zero comparison. A separate unchanged accepted-source witness passed 23 assertions and proves the inherited +0/-0 difference. Only that new historical zero-sign comparison was corrected; exact current full-LU factor/pivot/solve bits, independent nonzero factor bits and existing oracles remain. Prefix test/source audit and correction receipt are retained. Early source review also corrected class binding, row-restore identity and supported-path eligibility assertions.

Independent source-only review found no mathematical/lifecycle blocker for private timing. Direct executable owner-invalidation/snapshot tests and exact n=512 eligibility coverage remain gaps before production adoption. The outer sequence lacks a separate subprocess timeout beyond its browser-case 600-second timeout. The reviewer did not run checks; runtime results are root receipts. This evidence grants no broad numerical or performance approval.

Six full raw qualification reports and six complete final source overlays are exact deterministic gzip payloads. Large JSON/log receipts are also gzip; decompress and remove the final suffix. Inventory records original/stored/payload hashes, sizes, transforms and provenance. Personal task/user roots are sanitized in procedures/host receipts only; exact report/source payloads are unchanged. Substitute your own paths for placeholders when reproducing. The source overlay map pins the already committed baseline reconstruction; no full tree, WAR, JAR, cache or browser profile is included.

Normal cold corpus with margin, full required scale coverage and focused gates must pass before the expensive final matrix. Current scale-plan-4 and disabled-normal hook remain unaccepted workspace changes. Next: isolate incremental support tracking and scan cost before another implementation. Packet integrity review is recorded in the authoritative task report.
'''
put('README.md',readme.encode(),'generated:package_live_lu_support_trial_root.py',generated=True)
entries.sort(key=lambda e:e['path'])
inv={'schema':'tsj-q30-live-lu-support-trial/1','status':'INCONCLUSIVE_NOT_INTEGRATED_PENDING_PACKET_AUDIT','sourceCommitReference':commit,'artifactCountExcludingInventory':len(entries),'artifacts':entries}
raw=(json.dumps(inv,indent=2)+'\n').encode();(p/'inventory.json').write_bytes(raw);(p/'inventory.sha256').write_text(sha(raw)+'  inventory.json\n',encoding='utf-8')
receipt={'status':'PACKAGED_PENDING_ROOT_AUDIT','artifacts':len(entries),'inventorySha256':sha(raw),'exactReportCount':6,'exactFinalSourceOverlays':6,'sourceInputsBefore':len(before),'sourceInputsAfter':len(after)}
(s/'live-lu-support-r1-packaging.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf-8');print(json.dumps(receipt))
