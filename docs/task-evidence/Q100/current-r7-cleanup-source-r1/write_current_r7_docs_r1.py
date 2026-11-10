"""Record final current Source-emission proof; publication is separate."""
from pathlib import Path
import argparse,hashlib,json,sys
assert sys.flags.isolated and sys.dont_write_bytecode and not sys.flags.optimize
R=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--repo',required=True,type=Path);p.add_argument('--main-mirror',required=True,type=Path);a=p.parse_args();F=a.repo
T=F/'tests/qualification/rb100-layout/current-r7-cleanup-source-r1'
E=F/'docs/task-evidence/Q100/current-r7-cleanup-source-r1'
def read(path):return Path(path).read_bytes()
def parse(path):return json.loads(read(path))
def sha(raw):return hashlib.sha256(raw).hexdigest()
def dump(path,value):
    raw=(json.dumps(value,indent=2)+'\n').encode('ascii')
    with path.open('xb') as f:f.write(raw)
    return sha(raw)
public=parse(R/'FINAL_PUBLIC_GATE_R1.json')
assert public['status']=='PASS_CURRENT_R7_PUBLIC_BYTES_SOURCE_TESTS_EMIT_READERS_PROCESS_AUDIT'
assert len(public['actualCallsReauthenticated'])==24 and public['pureCounts']==[14,12]
for row in public['publicSource']:assert sha(read(F/row['path']))==row['sha256']
gates={name:parse(R/(name+'_GATE_R1.json')) for name in ['HOST','GENERATION','PURE','COMPILE','SMOKE','SUITE','REGRESSION']}
assert all(v['status'].startswith('PASS_') for v in gates.values())
proof={'status':'LOCALLY_ACCEPTED_MAINTAINED_CURRENT_R7_CLEANUP_SOURCE_PREREQUISITE',
 'baseCommit':'8e8ef678bae6b747def9956446eb9cedfb0bde94',
 'sourceFreezeSha256':sha(read(R/'SOURCE_FREEZE_R1.json')),
 'contractSha256':sha(read(R/'CONTRACT_R1.md')),'referenceSha256':sha(read(R/'REFERENCE_R1.json')),
 'gateReceipts':{name+'_GATE_R1.json':sha(read(R/(name+'_GATE_R1.json'))) for name in gates},
 'finalPublicGateSha256':sha(read(R/'FINAL_PUBLIC_GATE_R1.json')),
 'actualGenerationManifestSha256':public['actualManifestSha256'],
 'publicSource':public['publicSource'],'privatePureCounts':[14,12],'publicPureCounts':[14,12],
 'nativeDetachedCases':24,'maintainedCases':81,'maintainedAssertions':12089,
 'selectedEmissionExpectedExit2':7,'reauthenticatedProcessCalls':24,
 'sourceDelta':{'installerLineBytes':71,'ownershipLineBytes':47,'finalStudyBytes':235025,
 'finalStudySha256':'48f67e37f5603fdf9eade38d41cd86c95fb7171b16edc7a6f6cd8b13637e0b00',
 'onlyCaptureMethodChanged':True,'detachedApiAndCoreUnchanged':True,'mainAssembleHoldsUnchanged':True},
 'reviews':{'implementation':'PASS_SOURCE_ONLY','canonicalCollisionFixtureCorrection':'PASS_SOURCE_ONLY',
 'rootRoutingCorrection':'ROOT_REVIEWED_EXACT_AST_ROUTING','independentNativeRun':False},
 'isolatedProcessCleanupDurationMs':None,
 'limits':{'actualAggregationHook':'NOT_RUN_UNSPENT_GATED','wholeResultFullDHostLifecycle':'CLOSED',
 'genuineEnvelope':'UNSPENT_GATED','productionGwtPlayer':'NOT_APPLICABLE','electricalPopulation':'NOT_RENEWED',
 'ci':'NOT_RUN','playable100':0,'beta':'HELD','full30':'EXCLUDED','hardPsychotic':'UNAVAILABLE_UNRUN',
 'protectedUnread':'UNCERTIFIED'},'publication':'SEPARATE_RECEIPT_REQUIRED'}
proof_sha=dump(E/'PROOF_R1.json',proof)
(E/'FINAL_PUBLIC_GATE_R1.json').write_bytes(read(R/'FINAL_PUBLIC_GATE_R1.json'))
(E/'write_current_r7_docs_r1.py').write_bytes(read(Path(__file__)))
def times(call):return f"{call['processElapsedMs']:.4f} / {call['wrapperTotalElapsedMs']:.4f}ms"
table=[('Actual current Source emission',gates['GENERATION']['positive']),
 ('Private14 authored tests',gates['PURE']['calls'][0]),('Private12 ownership tests',gates['PURE']['calls'][1]),
 ('Final-source JDK8 compile4/139major51 classes',gates['COMPILE']['call']),
 ('Detached native SMOKE2',gates['SMOKE']['call']),('Independent SMOKE reader',gates['SMOKE']['reader']),
 ('Detached native SUITE22',gates['SUITE']['call']),('Independent SUITE reader',gates['SUITE']['reader']),
 ('Maintained RUN81/12089',gates['REGRESSION']['call']),
 ('Public14 authored tests',public['pureCalls'][0]),('Public12 ownership tests',public['pureCalls'][1]),
 ('Actual public current Source emission',public['actualPublicEmission'])]
rows='\n'.join('| '+name+' | PASS | '+times(call)+' |' for name,call in table)
text=f'''# Maintained current R7 cleanup Source handoff

The authored R7 owner now calls the existing cleanup installer once, after its
owned duration and other scalar aggregations and before delegating to the detached
plan. The current emitter reproduces that change from the exact admitted current
Study, preserving the independent core and keeping body ownership insertion a
separate step. It does not replay legacy W/R/Q source builders or data.

This candidate is locally accepted for Source generation and the recorded
component regressions. [PROOF_R1.json](PROOF_R1.json) SHA `{proof_sha}` binds Source
freeze `{proof['sourceFreezeSha256']}`. Publication is recorded separately.

The frozen reference precedes implementation. Adding the exact71-byte call to
the22521-byte authored template yields22592B/8c2c6428..., and removing it restores
old3a303450... exactly. Current Study9ac45b... has one47-byte ownership insertion;
the pure transform strips it, verifies the old capture method against the authored
core, replaces only that method, and returns234978B/c8b23f39... pre-ownership Source.
The existing ownership step restores its unchanged call. Final235025B/SHA
48f67e37f5603fdf9eade38d41cd86c95fb7171b16edc7a6f6cd8b13637e0b00 equals the
published current Study plus exactly the installer call. All other bytes, detached
API/plan, main/assemble holds, reads/additions, counters and budget owners remain.
This Source-only placement does not execute the actual aggregation hook.

The obsolete W/R/Q/shared pure input API is retired for current use. One pinned
current bytes-only API returns pre-ownership Source and canonical provenance.
Useful parser/declaration/core oracles remain and synthetic collision checks call
them directly. The body emitter is the sole filesystem/CLI owner, with four
required roots; old three-root arguments reject before input reads. It reads
fixed files through the existing stable/reparse/capped reader, authenticates all
four input pins, and executes the authenticated module bytes without reopening
or importing a mutable path. After exclusive Java Source write it returns and
emits the full canonical1MiB-bounded manifest, including the exact pure provenance
bytes/hash/object and input/pre/final identities. No provenance is discarded.
The current selected gate authenticates and retains actual stdout and output.

| Final gate | Result | Operation / wrapper elapsed |
| --- | --- | --- |
{rows}

The accepted2518-class closure was reauthenticated. Every replaced class was
excluded:139 fresh major51 classes/382443B plus2379 dependencies/11711904B, with
no duplicate/shadow paths. Final-source JDK8 compiles the generated Study and
current metadata, ownership and unchanged R3 canary Sources. All24 detached
regressions and81/12089 RUN regression pass on that rebuilt Study. R3 fixtures/
reader/oracle inputs are byte-authenticated unchanged and reused only for their
detached API/budget contracts; the historical R3 whole-Source candidate is not
asserted unchanged. Actual-output readers run freshly, privately and publicly.

The actual CLI positive and seven precise expected-exit2 controls pass: changed
Study/helper/module/template, existing output, obsolete CLI and existing junction.
Real stable rewritten Source is accepted and actual append during read rejected;
the Path.open hook is restored and handles closed. No junction is created or
target file read. Two real JDK8 host controls pass. All24 candidate process
receipts/inputs are reauthenticated with termination and owned-root/log absence;
isolated process-cleanup duration is unmeasured. No persistent helper resource is
owned. Scratch Source/classes/control artifacts remain intentionally retained.
Exact public Source bytes match private tested bytes; public26 source tests,
actual emission and actual-output readers run freshly. Native tests are not
repeated for unchanged copied Source.

The [two Source-test failures](SOURCE_HISTORY_R1.json) remain FAIL. Three collision
assertions used same-line retained declarations outside the preserved canonical
line parser format; only those fixtures became multiline, retaining assertions.
The next ownership test required a nonexistent uppercase variable token. It now
checks the exact AST routing of all four fixed files to their contracted roots.
All26 methods and rejection oracles remain; implementation bytes never changed
for either correction. Old frozen Sources/raw failures/receipts are retained;
public stderr is sanitized and records raw-byte hashes.

Root reviewed the integrated Source/diff. Independent Source review covers the
three implementation files, scalar placement, conservation, authenticated module
execution, manifest and unchanged I/O controls; a separate Source review covers
the canonical fixture correction. No independent runtime run is claimed. The
[preceding Source audit](source-audit-r1/FINDINGS_R1.md) confirms current rawprior
and outputqualifiedPrior are different correct roles; no bridge/key fix was
needed. Retained old readInput is unused; normal producer main/assemble remain
held. Historical root-frozen builder/run snapshots are not replayed as current
commands. The committed current Study fixture is pinned code, not prior/result or
hidden-case data; native replay still requires the admitted private class/tool
closure and is not promised standalone.

Actual aggregation with genuine OriginalArm/ArmResult remains NOT RUN/
UNSPENT_GATED. No fake arm/capture object, graph, Session or routing run is made.
Raw cleanup flags/scalars are not semantic admission; whole-result/full-D reader,
actual host authority and lifecycle remain closed. Next is a narrowly admitted
current producer composition contract using actual outputs and one body, before
any genuine-envelope spend. This does not qualify playable100 or renew electrical/
population evidence. Production/GWT/Player builds and visible checks are NOT
APPLICABLE, CI NOT RUN. All90s/640/5s/32768/copy1MiB/chunk4096 and Source/report/
class/host/cleanup caps, exact seeds/epochs and CircuitJS truth remain. Full30
EXCLUDED,HARD/PSYCHOTIC unavailable/unrun,protectedUnreadUNCERTIFIED,oldnormal20
historicalREAD/no reread,playable100=0/betaHELD,genuine original,NULL,OBSERVE unspent.
'''
(E/'README.md').write_text(text,encoding='ascii',newline='\n')
(T/'README.md').write_text('''# Current R7 cleanup Source fixture

CURRENT_STUDY.java is the exact pinned, component-held baseline code used by the
current Source-only emitter. It contains no admitted DATA or genuine result.
The authored pure API and four-root body CLI derive the maintained cleanup hook
and separate ownership insertion. The frozen reference and evidence record exact
input/output hashes, checks and limits. This grants no producer or playable board.

Run Python with -I -B. The authored tests require --study-source pointing here;
ownership tests also require --authored-root pointing to authored-flat-work-plan-r1.
The body CLI requires --source-root (here), --component-root (native-body-ownership-r1),
--authored-root (authored-flat-work-plan-r1) and an existing task-owned --output-root.
Its stdout is a canonical Source-only provenance manifest after exclusive write.
See the linked evidence packet in docs/task-evidence/Q100/current-r7-cleanup-source-r1.
''',encoding='ascii',newline='\n')
road=F/'docs/ROADMAP.md';text=road.read_text(encoding='utf-8')
anchor='Gmail `1a1c2dfc524598d6` acknowledged once; this receipt sends no second email.\n'
assert text.count(anchor)==1
para='''
The [maintained current R7 Source prerequisite](task-evidence/Q100/current-r7-cleanup-source-r1/README.md)
is locally accepted for its corrected Source-only candidate. The authored owner
installs cleanup after its existing scalar aggregation, before detached R7. One
current Source path replaces obsolete W/R/Q inputs; the four-root CLI authenticates
module bytes and retains canonical provenance after exclusive write. Exact71-byte
delta/core conservation,26private/public source tests, real emission/negative/file
controls, final-source JDK8 compile4/139classes, detachedSMOKE2/SUITE22/readers and
RUN81/12089 pass;24 process receipts reaudited. Two test-fixture/assertion failures
remain preserved, implementation unchanged. Actual aggregation/genuine envelope
remain unrun/gated, producer entrypoints held, full-D/host/lifecycle closed. Next
current producer composition contract; no playable100 or electrical renewal.
All budgets, protected data exclusions, full30EXCLUDED and betaHELD remain.
Publication pending normal commit/push and one new completion Gmail.
'''
assert 'The [maintained current R7 Source prerequisite]' not in text
road.write_text(text.replace(anchor,anchor+para,1),encoding='utf-8',newline='\n')
arch=F/'docs/ARCHITECTURE.md';text=arch.read_text(encoding='utf-8');anchor='\nThe [pure prior normalizer]'
assert text.count(anchor)==1
para='''
The [current R7 Source emitter](task-evidence/Q100/current-r7-cleanup-source-r1/README.md)
keeps scalar-to-cleanup installation in AuthoredFlatWorkMembers, immediately before
detached-plan delegation. Its current pure derive API authenticates published
baseline code and the authored fragment, strips the one ownership line, replaces
only the capture method and proves byte conservation. The body-ownership CLI
reads four fixed Source inputs through three input roots and uses a fourth
output root. It authenticates all pins
before compiling module bytes, restores the separate ownership insertion and
exclusively writes final Source. Canonical stdout provenance retains pure bytes/
hash/object and all input/output identities. Old W/R/Q current APIs are retired;
normal producer holds and detached/core behavior remain. Source emission and
detached regressions do not prove genuine aggregate execution or full-result
semantics/host/lifecycle; those boundaries remain closed.
'''
arch.write_text(text.replace(anchor,'\n'+para+anchor,1),encoding='utf-8',newline='\n')
checkpoint=f'''# Current checkpoint: maintained R7 Source prerequisite locally accepted

UTC2026-11-10. F codex/q100-terminal-only-20261107 baseHEAD8e8ef678bae6b747def9956446eb9cedfb0bde94,
no upstream; new implementation commit/push/Gmail pending. Previous1f45c12e/8e8ef678
published and exact remote verified; Gmail1a1c2dfc524598d6 SENT_ONCE, never repeat.
Candidate OS-temp leaf {R.name}; Sourcefreeze{proof['sourceFreezeSha256']},
proof{proof_sha}. SourceDelta71B in authored capture before detached delegation;
pre234978/c8b23f39...,final235025/48f67e37..., othercode/core/holds unchanged.
Current pure derivation/four-root CLI replaceoldW/R/Q, authenticate allinputs and
modulebytes, preserve exclusivewrite and canonical stdoutprovenance. Source-only
actualaggregate NOTRUN/UNSPENT_GATED; no real/fake arm/capture/graph/Session/routing.

Private/public26pures, realCLIpositive+7expectedexit2 controls, actualstable/append
filepolicy, hostcontrols, finalJDK8compile4/139major51classes382443B,2379deps11711904B/
noshadows, detachedSMOKE2/SUITE22/readers and RUN81/12089 PASS. All24actualprocess
receipts/inputs reauthenticated, termination/root+logabsence proved, isolatedcleanup
durationunmeasured. RootintegratedSource review and independent3fileSource review
PASS; fixturecorrectionSource reviewed. No independentnative claimed. N5collision
fixture3assertionsFAIL andN6staleidentifier1FAIL preserved; onlytests corrected,
allcases/oracles retained, noimplementation change. ActualfullD/genuine unspent.

SourceauditN4found no activeproducer; main/assembleheld. Currentrawprior input vs
resultqualifiedPrior correct, no bridge/keyfix. Semanticconsumer/constants/work/
context remain unqualified. Next narrow currentproducercomposition contract,
beforewhole-result/fullD/actualhost/lifecycle or genuineenvelope spend.
F intendedcode/evidence/docs only changed. Main5f05b005 9trackedM/6untracked,
E d9747229 reportM,Desktop e0c36885 7trackedM/89untracked preserved; reportmirroronly.
No newpersistentprocess/link, scratchSource/classes/failures retained. Old uncertain
browser14116/start639294472690745890/58320 andpreview34180/start639294472514546595/
58264+14children, oldDprocess/unlinkUNCERTIFIED untouched. All90s/640/5s/32768/
copy1MiB/chunk4096/Source4MiB/opJSON8MiB/result1MiB/depth64/key1m/class10k256MiB/
compile-suite60s/reader180s/wrapper480s/cleanup120s caps unchanged. GWT/PlayerN/A,
electrical/populationNOTRENEWED,CINOTRUN,full30EXCLUDED,HARD/PSYCHOTIC unavailable/
unrun,protectedUnreadUNCERTIFIED,oldnormal20 historicalREAD/no reread,playable100=0/
betaHELD,genuine original then exact NULL/OBSERVE UNSPENT_GATED. Next rootstaged/
privacy/byte/diff checks, normalpush/exactverify andone newGmail, thenreceipt.

'''
for root in [F,a.main_mirror,R.parent/'tsj-q100-geometric-reasons-20261106-r1']:
    file=root/'docs/CODEX_TASK_REPORT.md'
    with file.open('rb') as f:head=f.read(16384);tail=f.read()
    i=head.index(b'# Historical checkpoint:');file.write_bytes(checkpoint.encode('ascii')+head[i:]+tail)
print(json.dumps({'status':proof['status'],'proofSha256':proof_sha}))
