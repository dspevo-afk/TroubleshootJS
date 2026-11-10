"""Record accepted final gates; publication remains a separate receipt."""
from pathlib import Path
import argparse,hashlib,json,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',required=True,type=Path)
p.add_argument('--main-mirror',required=True,type=Path);a=p.parse_args();F=a.repo
E=F/'docs/task-evidence/Q100/current-cleanup-staging-r1'
def read(path):return Path(path).read_bytes()
def parse(path):return json.loads(read(path))
def sha(raw):return hashlib.sha256(raw).hexdigest()
def dump(path,value):
    raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
    with path.open('xb') as f:f.write(raw)
    return sha(raw)
public=parse(R/'FINAL_PUBLIC_GATE_R1.json')
assert public['status']=='PASS_CLEANUP_PUBLIC_BYTES_PURE_READERS_AND_PROCESS_AUDIT'
assert len(public['actualCallsReauthenticated'])==28 and public['pureTestCount']==16
for c in public['actualCallsReauthenticated']:assert c['terminationProven'] and c['ownedRootAndLogsAbsent']
for row in public['publicRuntimeSource']:assert sha(read(F/row['path']))==row['sha256']
gates={name:parse(R/(name+'_GATE_R1.json')) for name in ['HOST','COMPILE','PURE','SMOKE','SUITE','REGRESSION','CONTROLS']}
assert all(v['status'].startswith('PASS_') for v in gates.values())
assert gates['SUITE']['summary']['caseCount']==22
proof={'status':'LOCALLY_ACCEPTED_SYNTHETIC_PENDING_CLEANUP_AND_R7_HANDOFF',
 'baseCommit':'1b7f972d6d0158b20f32b1b7c56370479ae28be6',
 'sourceFreezeSha256':sha(read(R/'SOURCE_FREEZE_R1.json')),
 'helperSha256':sha(read(R/'CurrentAssemblyMetadata.java')),
 'canarySha256':sha(read(R/'CleanupStagingCanary.java')),
 'contractSha256':sha(read(R/'CONTRACT_R1.md')),'referenceSha256':sha(read(R/'REFERENCE_R1.json')),
 'gateReceipts':{name+'_GATE_R1.json':sha(read(R/(name+'_GATE_R1.json'))) for name in gates},
 'finalPublicGateSha256':sha(read(R/'FINAL_PUBLIC_GATE_R1.json')),
 'realReaderHostGateSha256':sha(read(R/'READER_HOST_GATE_R3.json')),
 'sourceReviewSha256':sha(read(R/'SOURCE_REVIEW_R3.json')),
 'nativeCases':24,'privatePureTests':16,'publicPureTests':16,
 'maintainedCases':81,'maintainedAssertions':12089,'reauthenticatedProcessCalls':28,
 'pendingConstructorDebit':[14,13,1],'cleanupInstallerDebit':[67,11,2],
 'syntheticR7Debit':[18576,132,19], 'syntheticR7Groups':{'wrapper':1,'prior':1,'totalBeforeExport':2},
 'independentReviews':[
   {'result':'PASS_SOURCE_ONLY','scope':'New helper and unchanged old regions; ownership995/tree17311 census','helperSha256':sha(read(R/'CurrentAssemblyMetadata.java'))},
   {'result':'PASS_SOURCE_ONLY','scope':'R5/R7 prerequisites and corrected24-case canary diff','canarySha256':sha(read(R/'CleanupStagingCanary.java'))},
   {'result':'PASS_SOURCE_ONLY','scope':'N2 file identity/ctime policy; N3 read-policy functions unchanged, new pin dispatch root-reviewed'}],
 'independentNativeRun':False,'isolatedProcessCleanupDurationMs':None,
 'limits':{'productionGwtPlayer':'NOT_APPLICABLE','electricalPopulation':'NOT_RENEWED','ci':'NOT_RUN',
 'normalFullDHostLifecycle':'CLOSED','genuineEnvelope':'UNSPENT_GATED','playable100':0,'beta':'HELD',
 'full30':'EXCLUDED','hardPsychotic':'UNAVAILABLE_UNRUN','protectedUnread':'UNCERTIFIED'},
 'publication':'SEPARATE_PUBLICATION_RECEIPT_REQUIRED'}
proof_sha=dump(E/'PROOF_R1.json',proof)
(E/'FINAL_PUBLIC_GATE_R1.json').write_bytes(read(R/'FINAL_PUBLIC_GATE_R1.json'))
(E/'HOST_PREFREEZE_GATE_R1.json').write_bytes(read(R/'HOST_PREFREEZE_GATE_R1.json'))
(E/'write_final_docs_r3.py').write_bytes(read(Path(__file__)))
def times(call):return f"{call['processElapsedMs']:.4f} / {call['wrapperTotalElapsedMs']:.4f}ms"
table=[('Final-source JDK8 compile,2 Sources/11 major51 classes/59455B',gates['COMPILE']['call']),
       ('Private16 pure tests',gates['PURE']['call']),('Native SMOKE2',gates['SMOKE']['call']),
       ('Independent SMOKE reader',gates['SMOKE']['reader']),('Native SUITE22',gates['SUITE']['call']),
       ('Independent SUITE reader',gates['SUITE']['reader']),('Maintained81 cases/12089 assertions',gates['REGRESSION']['call']),
       ('Public16 pure tests',public['pure'])]
rows='\n'.join('| '+name+' | PASS | '+times(call)+' |' for name,call in table)
readme=f'''# Pending cleanup and detached R7 handoff

The metadata owner can now construct a body with explicit pending prior, work and
cleanup slots, then install cleanup from that body's own stable DTO views and the
existing R7-owned scalar before R7 closes the meter. The installer checks the
inherited0..120-second scalar bound before body observation, uses the existing
cleanup projection, and prepays its sole replacement. It does not reread or resum
durations or finish the meter. Previous metadata methods remain byte-identical.

This corrected candidate is locally accepted for its synthetic component scope;
publication is recorded separately. [PROOF_R1.json](PROOF_R1.json) SHA
`{proof_sha}` binds Source freeze `{proof['sourceFreezeSha256']}`.
The initial contract/reference preceded helper implementation; the R3 correction
and final Source pins preceded corrected native execution. No golden was derived
from native output. The generator reproduces the revised reference exactly.

Pending construction costs14units/13copies/1chunk. Installation costs67/11/2,
including six charged body-derived map views, the existing55/10/1 projector, and
the prepaid sole put. Installer rejection leaves the body and children unchanged;
there is no caller finish after it. Generic ownership allows null; the actual
codec rejects pending cleanup before byte0. This is explicit staging, not a
relaxation of the direct constructor's prevalidated-child contract.

The24 native cases include real scalar/copy/group boundaries, pending export,
false observations and below-sum scalar consumption, plus same-meter detached R7
handoffs. Valid R7 fixtures use the real native begin/admit/adopt budget APIs for
one synthetic owner credit, with zero genuine owner objects or work. Their exact
debit is18576/132/19, one wrapper group plus one prior credit, work groups2 and
originalCaptureGroups1. Independent ownership995/tree17311 plus prefix83/fixed187
reconcile the unit total. A separate zero-credit case must reject after the actual
R7 close with R5_METADATA_SNAPSHOT, changed body and installed cleanup, then
discard without export or another finish. This downstream rejection is distinct
from installer atomicity. False flags and scalar5 below rawsum71 are transported
unchanged; they receive no semantic acceptance. Formatting uses an unmetered
bounded testbench sink after close and does not prove production export accounting.

| Final gate | Result | Operation / wrapper elapsed |
| --- | --- | --- |
{rows}

The accepted dependency closure was reauthenticated, with the superseded metadata
class excluded. There are2507 unchanged dependency classes/12034690B and11 fresh
classes, all major51, with no duplicate/shadow paths. The accepted863-Source/
2486-class compile ancestry remains pinned; no legacy DATA or data class was read.
Private and public actual-output readers pass. Two final real JDK8 host controls,
real stable-file and during-read append controls, one selected reader positive,
nine expected-exit2 reader negatives, and two expected-exit1 JVM protocol controls
pass. Reparse handling uses the actual existing junction and creates no link.
All28 final-root process receipts/inputs are reauthenticated, including two earlier
host preflights; termination and owned-root/log absence are proved. Isolated
process-cleanup duration is unmeasured. The helper owns no OS resource. After
public copy, runtime Source bytes are exact and public pure/readers run freshly;
native tests are not repeated for unchanged bytes.

The [failure history](SOURCE_HISTORY_R1.json) preserves two pure preflight failures,
the [N1 reader failure](failed-reader-r1/FAILURE_R1.json), and the
[N2 native failure](failed-native-r2/FAILURE_R1.json). Windows path and handle ctime
had different meanings on rewritten files; the correction retains ctime stability
within each API and compares stable cross-API identity fields. Real append is
rejected FILE_CHANGED. N2 then failed a639-group setup assertion and three
zero-credit R5 prerequisites. Correcting the fixtures retained every original
case and added the explicit zero-credit rejection; no validator or budget was
weakened. Draft Source, full raw failures/classes and private process receipts
remain retained. Public pure-error text is sanitized and carries raw-byte hashes.
Root also corrected a contract-pin transcription before final freeze; static pin
checks now bind all source copies to actual contract/reference/wire bytes.

Root reviewed the integrated Source/diff. Independent Source reviews cover the
helper/census, R5/R7 prerequisites and corrected canary, and file identity policy;
no independent native execution is claimed. The public native harness still
requires pinned private current dependency Sources/classes and selected-host
closure; this is not a standalone replay or full-D result/host acceptance.

Production/GWT/Player are unchanged: those builds and visible checks are NOT
APPLICABLE. Electrical/population qualification is NOT RENEWED; CI NOT RUN.
Normal full-D/actual-host/lifecycle remain closed, genuine original then exact
NULL/OBSERVE UNSPENT_GATED, playable100=0/betaHELD, full30EXCLUDED,
HARD/PSYCHOTIC unavailable/unrun, protectedUnreadUNCERTIFIED. Oldnormal20 remains
historicalREAD/no reread; protected/held-out and old actual DATA/classes stay denied.
All existing90s/640groups/5s/32768units/copy1MiB/chunk4096, Source/report/class,
host and120s cleanup caps, exact seeds/epochs and CircuitJS truth remain.

Next is a narrow current whole-result/producer compatibility Source audit before
any full-D/actual-host/lifecycle qualification or genuine-envelope spend.
'''
(E/'README.md').write_text(readme,encoding='ascii',newline='\n')
road=F/'docs/ROADMAP.md';text=road.read_text(encoding='utf-8')
anchor='`1a1c28920295eadb` acknowledged once; the documentation receipt sends no second email.\n'
assert text.count(anchor)==1
paragraph='''
The [pending cleanup and detached R7 prerequisite](task-evidence/Q100/current-cleanup-staging-r1/README.md)
is locally accepted for its corrected synthetic candidate. Explicit pending-body
construction and same-body cleanup installation preserve the existing scalar,
projection, metadata methods, R5/R7 and budget owners. Final-source JDK8 compile2/
11major51 classes, native SMOKE2/SUITE22 with independent private/public readers,
private/public16 pure tests, maintained81/12089, real file/host controls, nine
reader negatives and two JVM protocol rejections pass;28 process receipts reaudited.
Failures remain preserved: Windows cross-API ctime interpretation and invalid
639-group/zero-owner-credit fixture preconditions. Existing validators were not
weakened; the suite retains zero-credit post-close rejection. This closes only
synthetic pending-body/scalar/R7 sequencing; normal whole-result/full-D/actual-host
and lifecycle remain closed. Next is narrow current producer compatibility Source
audit. Genuine original,NULL,OBSERVE stays unspent/gated; playable100=0/betaHELD,
full30EXCLUDED, protected data exclusions, exact seeds/epochs and all caps unchanged.
Publication is pending normal commit/push and one completion Gmail.
'''
assert 'The [pending cleanup and detached R7 prerequisite]' not in text
road.write_text(text.replace(anchor,anchor+paragraph,1),encoding='utf-8',newline='\n')
arch=F/'docs/ARCHITECTURE.md';text=arch.read_text(encoding='utf-8');anchor='\nThe [pure prior normalizer]'
assert text.count(anchor)==1
paragraph='''
The [pending cleanup component](task-evidence/Q100/current-cleanup-staging-r1/README.md)
adds explicit `CurrentAssemblyMetadata.pendingCleanupBody` (14/13/1) and
`installCleanup` (67/11/2). The latter preflights the inherited scalar bound,
derives six stable views from the same body, calls the unchanged cleanup projector
and prepays one replacement. It never finishes the meter or rereads durations.
Prior/work/cleanup are explicitly pending; actual codec root checks reject an
unfilled cleanup before byte0. Generic ownership itself permits null. Detached
R7 owns close/snapshot/finalization unchanged, including its admitted-owner-credit
prerequisite and zero-credit post-close rejection. A real native budget credit
in a synthetic fixture is not a genuine owner instance or capture. Testbench
formatting after close gives no production export-accounting proof. Normal
whole-result/full-D/actual-host/lifecycle remain separate and closed.
'''
arch.write_text(text.replace(anchor,'\n'+paragraph+anchor,1),encoding='utf-8',newline='\n')
checkpoint=f'''# Current checkpoint: pending cleanup and detached R7 locally accepted

UTC2026-11-10. F codex/q100-terminal-only-20261107 baseHEAD1b7f972d6d0158b20f32b1b7c56370479ae28be6,
no upstream; implementation commit/push/Gmail pending. Previous679f3621/1b7f972d
normally pushed and exact remote verified; Gmail1a1c28920295eadb SENT_ONCE, never repeat.

Candidate OS-temp leaf {R.name}. Sourcefreeze{proof['sourceFreezeSha256']}; proof{proof_sha}.
Helpera98ea929 adds pendingCleanupBody14/13/1, installCleanup67/11/2. Same-body
views, inherited scalar/projector, prepaid sole put, no finish or duration resum;
old metadata methods byte-identical. Current codec/R5/R7/ownership unchanged.
Final JDK8 compile2Sources/11major51classes59455B,2507deps12034690B/no shadows;
SMOKE2/SUITE22 actual readers, private/public16pures, maintained81/12089 PASS.
Host/file controls and9expectedexit2 reader/2expectedexit1 protocol negatives PASS.
All28 actual process receipts/inputs reauthenticated; termination/root+logabsence
proved, isolated cleanupduration unmeasured. Independent Source reviews PASS in
their helper/census/R5/R7/canary/file-policy scopes; no independent runtime claimed.

Valid R7 synthetic fixture real native begin/admit0/adopt1 APIs: one prior credit,
one wrapper group, worktotal2/captureGroups1, zero genuineowner/arm/graph/work.
Exact18576/132/19 via ownership995/tree17311. Explicit zero-credit postclose
R5_METADATA_SNAPSHOT rejection retained. Actual codec pending-header0byte reject.
Testbench formatter does not qualify production export/transport. Fullresult/
fullD/actualhost/lifecycle remain closed; next narrow producer compatibility
Source audit, before genuine-envelope spend. Protected/held-out DATA/old actual
classes/builders/consumers denied. Genuine original then exact NULL/OBSERVE UNSPENT_GATED.

N1 pure preflights FAIL(args then namespace13errors); N1 reader FAIL_FILE_CHANGED
from cross-API Windows ctime. Corrected within-API stability/cross-API identity
passes real stable-rewrite/append controls. N2 SUITE FAIL17pass/4fail:639group
precondition and zero-credit snapshot. R3 fixes fixtures, adds24th negative;
no budget/validator weakened. Pin transcription caught before finalfreeze.
All failures/Source/classes retained; no failure reclassified PASS.

F task paths only changed; Main5f05b005 9trackedM/6untracked, E d9747229 reportM,
Desktop e0c36885 7trackedM/89untracked preserved. Main/E reportmirroronly. No new
persistent process/link; scratch Source/classes/failure fixtures retained. Old
uncertain browser14116/start639294472690745890/58320, preview34180/start639294472514546595/
58264+14children and oldDprocess/unlinkUNCERTIFIED untouched. All90s/640/5s/32768/
copy1MiB/chunk4096 and Source/report/class/host/cleanup caps unchanged. GWT/Player
N/A,electrical/population NOTRENEWED,CI NOTRUN,full30EXCLUDED,HARD/PSYCHOTIC
unavailable/unrun,oldnormal20 historicalREAD/no reread,protectedUnreadUNCERTIFIED,
playable100=0/betaHELD. Next root stage/diff/privacy/byte checks, normal commit/push,
verify exact remote and one new Gmail, then publication receipt and next audit.

'''
for root in [F,a.main_mirror,R.parent/'tsj-q100-geometric-reasons-20261106-r1']:
    file=root/'docs/CODEX_TASK_REPORT.md'
    with file.open('rb') as f:head=f.read(16384);tail=f.read()
    i=head.index(b'# Historical checkpoint:');file.write_bytes(checkpoint.encode('ascii')+head[i:]+tail)
print(json.dumps({'proofSha256':proof_sha,'status':proof['status']}))
