# Preserved failures and component observations

## Baseline audit call 1 — FAIL, corrected invocation

`baseline-audit-r1`: the unchanged accepted input auditor returned exit1,
`P9_RESERVATION_INPUT_REJECT P9_INPUT_ROOT_BINDING`, parent elapsed476.5761ms.
The command supplied a forward-slash pins path. The existing contract requires
the exact canonical Windows spelling (`canonical(..., exact=True)`), so the
rejection preceded source qualification. No auditor rule or pin was changed.
The actual returned process receipt proves exit; the process root is absent.
Original request, streams and receipts are preserved in that call directory.

## Baseline audit call 2 — PASS, unchanged source closure only

`baseline-audit-r2` uses the exact canonical argument spelling from the accepted
earlier invocation. Actual exit0, elapsed18521.6074ms, stdout361B, stderr0,
termination proven and process root absent. Result:
`PASS_Q100_RESERVATION_INPUTS_ONLY`, existing3437/direct17/derived2/total3456,
production850/top27. Pins2d717040c9b78244cf591857af96cba51bb52366af6c270e70b741db0468bf08,
inventorybbf01a9e919d6be5cd8fe72387852fd126a3b1ca72ad7c9343c6a2b9f8b0833e.
This audits unchanged accepted inputs; it does not qualify the new native adapter,
a copper-free route, GWT/Player/electrical behavior, or any population.

New compile/native/reader gates are NOT RUN at this entry. Historical P9 and
earlier failures remain in their original evidence; none is reclassified here.

## Metadata-only reflection prerequisite — PASS / limited scope

Root-owned CopperFreeSeamProbe does not construct or route a board. It tests the
real JDK8 private constructor/field/method access required by the adapter.
The study adapter itself and authored search remain NOT RUN.

- probe-prepare-call-r1: exit0/9509.2354ms, stdout213B/stderr0; exact1085 accepted
  old sources plus the one probe. Input manifest478aa77aeae9ba847f085d7d60ec3decf292f704a3dea1429d99a600fe8c7952,
  compiler list9af54ec13d336edb1cee02714de9fb72a3547a9ed79df294ebd2223a0c58a9c8.
- probe-compile-call-r1: actual1086-source JDK8 compile exit0/8228.9272ms,
  stdout0/stderr204B: source7 bootstrap warning and ordinary unchecked notes.
- probe-native-call-r1: actual JVM exit0/654.8858ms, stdout86B/stderr0,
  PASS_METADATA_ONLY/checks34. No board or branch search executed.
- probe-negative-call-r1: EXPECTED_REJECTION, exit1/339.9706ms, stdout0/
  stderr180B, exact IllegalArgumentException for --bad. It is not a failed
  authored diagnostic or proof of the future adapter's argument handling.
- probe-finalize-call-r1: exit0/19355.8083ms, stdout305B/stderr0;4094 actual
  class files bound to returned compiler receipt and unchanged source/tool bytes.
  Class inventoryfd4220ababaa9b98e9159e70fa3a1b731d4b43ccd2275216f9076c6b2bb8d374,
  probe run manifest856cd7ed8a5b4bc5aab4f05d0a5fe7f4d983b1bd1c410af85a4252d19cad9b32.

Each call returned an actual process receipt, proven termination and absent
process root. The compiler finalizer additionally checks actual copied streams
and absent compiler logs/root. The selected existing process helper is unchanged.
Retained class files and task-owned artifacts are evidence, not running resources.
Wire R0f9415370... is retained; current R1 is34e207c7138af47db19932af9ff31496be2f64805c4c83d3c4daa69fafdb2dd0.

## Initial native-study snapshot compile — FAIL, preserved

Root took a stable read-only snapshot of the in-progress study into
source-revisions/native-initial-r1, SHA483944a9dd2d2edcc834b6a6e07431fd8e7bf7401d3b40c6f5ac179b0f7f407e.
This permits an early real compile while its sole writer continues its primary
source; it is not a final-source gate or an authored run.

initial-prepare-call-r1 returned exit0/9433.3793ms, stdout213B/stderr0, exact1085
old sources plus the snapshot. initial-compile-call-r1 returned exit1/7403.1372ms,
stdout0/stderr566B. The sole javac error is snapshot line259 assigning
result.validators when Cf defines no such field (the intended field is
validatorRows). The normal source7 warning remains. The source owner was sent
the actual error; the archived failing snapshot and outputs stay unchanged.
No JVM study, canary, authored branch or independent reader was run from it.

failed-compile-finalize-negative-r1 is an EXPECTED_REJECTION of this genuine
failed compiler receipt: exit1/943.7564ms, stdout0/stderr728B, RuntimeError
COMPILE_EXIT. No run-manifest.json was created. This complements the actual
successful probe finalizer; it does not reinterpret the compiler failure.
All three calls returned actual receipts with proven termination and absent
process roots. The partial compiler output is retained as failed-build evidence.

## Corrected study component and real directory boundary — PASS / limited scope

Study D447ABE192F62BDBFE35722F8838E6764CA4237D5B3B0A2E7FAD00502DACA8E6,
wire65f36395fa663da4a94e7701b127598b40d2721cd725c1bbfcfabbeceed23449.
The field mismatch is corrected; the failed483944a9 snapshot remains unchanged.

- component-prepare-call-r1: exit0/9740.486ms, stdout213B/stderr0, exact1086
  sources (1085 original plus current study).
- component-compile-call-r1: exit0/7366.1985ms, stdout0/stderr204B ordinary warnings.
- component-finalize-call-r1: exit0/15764.07ms, stdout305B/stderr0. Actual4106
  classes, inventory6517195b8b3eda2196f4a92006dec8612a66a6b18e2a62079e5239d8687e82bf,
  run manifest7fdca3d97369200169c40d0f0372b32f73529691a97a2d3f3a33577102c90093.
- component-args-negative-r1: EXPECTED_REJECTION exit1/347.0858ms, stdout0/
  stderr255B, exact argument-vector rejection before generation.
- component-junction-negative-r1: EXPECTED_REJECTION exit1/366.9399ms,
  stdout0/stderr221B, exact real-directory rejection before generation. The actual
  Windows Directory/ReparsePoint/Junction points only to this task's own empty
  target; target stayed empty. Identity/containment were rechecked, the exact link
  was deleted nonrecursively, target remains present/empty and link is absent.
  Owner and cleanup JSON receipts remain private; no general cleanup occurred.

All five calls have actual returned child receipts, proven exits and absent
process roots. This proves compilation and input guards only: --canary was still
unimplemented and no authored board was generated by these component calls.
Subsequent timer-only source9eaa9161 changes the full export interval guard; that
new source has no renewed compile/runtime gate yet. No component PASS is a final
authored diagnostic PASS.

## Independent reader literal component — PASS / limited scope

Root reader-literal-call-r1: selected Python -I -B, exit0/384.6431ms, stdout46B,
stderr121B (ordinary unittest progress/summary), actual17/17 tests PASS. Reader
2bee8ded724e0cd967bc8f82a911231a4b7b59712217599fd3dd9ecea26de152;
tests50ee5c5d348a433b305d67ea238b9808e9c83c19efbc6f9d4a7ba411ba68fe2d.
Native manifest/process/authored-result validation remains NOT RUN. Leaf also
reported selected-Python AST and the same17 literal tests passing; the root's
returned call is the recorded process evidence. No population or DRC renewal.

## Source-closure reader revision — component PASS, superseded wire pin

Readerd66ee4b4/testsEF673b01 add the explicit fixture source and seven exact source
tail/count/order cases. Root reader-literal-call-r2: exit0/405.7107ms, stdout46B/
stderr128B ordinary unittest,24/24 PASS, proven exit/absent process root. Later
wire-pin-only reader4e0a863f uses wire64494d29; its fresh literal run is pending.

## Final candidate R1 — compile PASS, native canary FAIL

FROZEN_CANDIDATE_R1 and source-revisions/final-candidate-r1 preserve StudyD5732453,
fixturea0b646fb,wire64494d29,reader4e0a863f/testsEF673b01 verbatim. Original plan
b5a9d85e (4975B) is also archived there before the applicability clarification.

- final-prepare-call-r1: exit0/9606.0028ms, stdout213B/stderr0, exact1087 sources.
- final-compile-call-r1: exit0/7276.6494ms, stdout0/stderr204B ordinary warnings.
- final-finalize-call-r1: exit0/15368.0389ms, stdout305B/stderr0, source/compiler/
  class binding returned. Actual manifest/inventory remain private in j8-final-r1.
- native-canary-call-r1: FAIL exit1/406.7336ms, stdout0/stderr441B. Wrapper reported
  ROUTING_GEOMETRY_FAILED at runCounterfactual451/runCanary130; no marker, no suite
  PASS. Line130 is the BLOCKED fixture. The adapter had incorrectly invoked a
  full-board connectivity validator for an intentionally unrouted NO_PATH case.
  All child receipts returned with proven termination/absent process roots.

The first validator wrapper lost its original cause. Source99d3a610 now chains
that exact exception; no runtime gate was claimed for this intermediate edit.
The native owner must keep full error details while fixing checker applicability.

Root-owned initial-fixture probe (no routing): fixture-probe-compile-r1 actual
JDK8 one-source compile PASS737.664ms/stdout0/stderr92B bootstrap warning.
fixture-probe-native-r1 returned exit1/459.7813ms/stdout0/stderr549B: exact
`PCB net is electrically disconnected: A_COIL_LOW` from the unchanged all-net
gate at PcbConductorGraph278/layout738. This is a failed diagnostic probe, not a
retroactive PASS; it identifies the API scope. No authored branch was run.

PLAN_R1 now clarifies full routingGeometry/all-net applicability. Product gates
stay unchanged. OBS FOUND partial uses NOT_APPLICABLE for whole-board geometry,
retains mandatory native two-layer/quality/conductor and independent branch proof;
all non-FOUND route validators are NOT_RUN. Literal OPEN canary separately must
PASS unchanged full geometry after its only multi-pad net is published. Literal
source templates expect the exact all-net disconnection control and reject any
earlier physical failure. No exception is suppressed to claim a geometry PASS.

Fixture owner also found/fixed a separate E/W obstruction body/courtyard overlap:
newfixturead299a40 only reverses those package rotations, retaining all pad centers,
packages, roles and guards. Manual rectangle audit is source-only; fresh native
gate pending. Originala0b646 failure evidence remains byte-identical.

## Candidate R2 — compile PASS; preflight literal FAIL

StudyD5b19ad8/fixturead299a40/wirebae7f5e9 are archived in final-candidate-r2.
final-prepare-call-r2 exit0/9574.8735ms; final-compile-call-r2 exit0/7207.6034ms
with ordinary204B warnings; final-finalize-call-r2 exit0/15362.1781ms,4109 actual
classes, manifest5c445e75.../inventoryf20efcf8.... Native-canary-call-r2 FAIL:
exit1/442.9012ms, stdout0/stderr1050B. Expected preflight message omitted the
source's colon-space; actual unchanged checker produced the correct message.
Only the exact literal was corrected in Study/WIRE; no matcher was broadened.
No authored run from R2. All returned processes terminated/proven roots absent.

## Candidate R3 — native observations complete; acceptance still PENDING

Frozen Study33d5215a/fixturead299a40/wireE799FDEA/readerF0DBF1E6/tests4A36B649
are preserved verbatim in final-candidate-r3. PLAN clarification is included.
final-prepare-call-r3 exit0/9561.2385ms; actual1087-source JDK8 compile exit0/
7234.4516ms, stdout0/stderr204B warnings; finalizer exit0/15955.895ms,4109 classes.
Final run manifest5e60efb588abcc81840b22d0024262dab0c0a9cc2155472c4f480a61652f8943;
class inventory469161f8a9c8fea11f6cd6d422422a41f6f3beb0bdb3baee438e9c1a447eea41.

native-canary-call-r3 returned exit0/511.6102ms, stdout1714B/stderr0: literal
positive FOUND18nodes/1trace/0holes and fullGeometryControl PASS; blocked NO_PATH
without publication; injected callback observed and listed references released.
Original context is explicitly NOT_APPLICABLE for these structural controls.
Later source review found the injection fires during Session construction before
Search exists. Therefore this PASS is not active Search/Branch cleanup proof and
does not clear the final acceptance gate; the meaningful-injection fix is pending.
The actual early-setup result is retained, not relabeled as later-stage coverage.

Final-source arguments negative exit1/363.6606ms and actual Windows junction
negative exit1/431.9751ms reject before generation. Target stayed empty; exact link
removed nonrecursively after identity/containment checks, target retained empty.
reader-literal-call-r3 exit0/480.1658ms, actual32/32 tests (stdout46B/stderr136B
ordinary unittest). This consumes F0DB reader; later plan-pin revision needs fresh
focused checks and cannot inherit this complete-file PASS.

native-observe-call-r3 returned exit0/12993.6419ms/stdout181B/stderr0. Actual
single-branch result: FOUND63nodes/1trace/0holes,114 expansions, selected endpoints
connected, native partial two-layer/quality/conductor checks PASS; full-board
geometry NOT_APPLICABLE/all-net continuity NOT_RUN. Raw operands remain private.
This observation is not independent acceptance, whole-route feasibility or adoption.

reader-observe-call-r3 FAIL exit2/438.1363ms BF_PLAN_HASH: stale expected original
plan pin. Reader71ABBF2A updates only the intended current e394fd97... plan hash,
which matches the existing F3 final manifest and actual plan. R3b then FAIL
exit2/398.7422ms BF_SOURCE_PATH: its three-root allowlist omitted genuine parent
P4/P5/P6/P8/BASE_OVERLAY/BRIDGE sources and JDK donor. Fix must use authenticated
old auditor/exact expected1085 source paths, not permit arbitrary temp paths.
Consumer-only fixes are pending; no child failure/exit2 is PASS. All these calls
returned genuine receipts with proven exit and absent process roots.

Independent static review of the exact33d5215a core found only the active-branch
injection coverage blocker: first observer check occurs in Session constructor.
Identity/root-only capture, fresh Search/faces, first-Publisher stop, no original
Search/board mutation path and validator scope were otherwise source-scoped CLEAR.
No runtime qualification follows from that review. Root holds Native/WIRE while
the consumer integration is debugged, then the injection will be armed only on
an actual installed Search and BranchSearch and all affected final gates rerun.

## Consumer integration failures after R3 — preserved, not waived

ReaderE73F5C2C authenticates the genuine parent auditor and exact1085-source list.
reader-observe-call-r3c FAIL exit2/8525.2377ms BF_DOCUMENT_FRAMING: compact LF-only
native parsing was incorrectly applied to actual PowerShell CRLF/pretty host JSON.
Reader843D3E5D separates bounded host JSON from strict native/document framing.
R3d FAIL exit2/8569.6125ms BF_PROCESS_STDERR: generic empty-stderr rule rejected
the actual known204B successful JDK8 warning. Compiler-only exact warning handling
was added; native stderr still must be empty. No actual evidence was rewritten.

Root semantic-component-call-r4 FAIL exit2/409.1369ms
BF_OP1_UNEXPECTED_RECORD: the new custom v6 parser omitted eight genuine
DIAGNOSTIC_CLONE rows and also conflated JSONL line index, global BRANCH ordinal
and pass-local lineage. Its duplicate protocol implementation must be retired in
favor of the unchanged genuine inner API. The raw receipt has308 branches,
101 groups,246 pads,8 diagnostic clones and one NET_ORDER/RESULT/HEADER/FOOTER.

Root p07-receipt-component-r4 PASS exit0/497.8038ms/stdout268B/stderr0: unchanged
sourceA314CDC7.validate_operation(raw, ACTUALnewOperation1, sourceSHA, wireSHA,
arm1, actualUUID, AUTHORED) accepts the actual F4 receipt. Actual global BRANCH
ordinal307 has derived pass2-local lineage77 and exact expected prefix6b49cfb2.
No old host/index/entry was fabricated. Component only, not whole-host proof.

## Candidate R4 — meaningful native cleanup proof and route observed

Reviewed active-FI draft6b7c7f6f/WIREe8841103 promoted byte-identically; fixture
ad299a40 unchanged. Actual state predicate waits for installed Search+Branch,
checks identity and records publisher state before the injected exception.
Independent source followup CLEAR_STATIC_ONLY; runtime gate follows below.

final-prepare-call-r4 exit0/9454.2011ms, actual1087-source compile exit0/
7669.9794ms, ordinary204B warnings. Finalizer exit0/15869.5947ms binds4109 classes.
Manifestd70952ca8e13a3a1395c81cc561da5c2db3877f42c681540e6922c71c97fa816;
class inventoryc70e4c73d57405b1726104ac4811ddab698db7fb8887b302ed26bd2dd793465a.

native-canary-call-r4 exit0/419.2117ms, stdout1890B/stderr0: positive and blocked
controls pass; injected phase BRANCH_SEARCH has actual Search=true/Branch=true/
Publisher=false, ordinal1 and exact fixture identity. Actual listed references
are released after the throw. This resolves the prior static coverage blocker.
Source-context proof remains NOT_APPLICABLE for literals, not assigned true.

Final-source args negative exit1/435.4369ms, actual Windows junction negative
exit1/419.2037ms reject before generation. Actual link safely removed after
checks, target remains empty. reader-literal-call-r4 consumes29E464C3/tests5B7D39A7,
actual40/40 PASS411.6756ms/stdout46B/stderr144B ordinary unittest.

native-observe-call-r4 exit0/12891.392ms/stdout181B/stderr0 returns genuine data
under native-data-r4. Reader-observe-call-r4 then FAIL exit2/8639.1808ms
BF_PROCESS_REQUEST_BINDING: Windows slash spelling of the same pinned Java image
was compared textually. Source93415DA1 canonicalizes executable identity without
changing raw receipt equality/tool/args. It also accepts exactly one LF or CRLF
console marker: actual Java println stdout ends CRLF; strict native result/JSONL
LF framing remains. New reader integration is pending; this exit2 is not PASS.

Root geometry-component-call-r4 PASS exit0/433.7142ms/stdout98B/stderr0: authentic
genuine-v6 pads/snapshot plus actual new data pass independent pad-land, node-chain
and published-geometry kernels (63 nodes/62 planar steps). This proves only those
semantic components, not full artifact/process acceptance or production adoption.
All listed calls have returned receipts/proven exits/absent process roots. Native
F4 source/inputs/data are HOLD; consumer-only fixes require their own fresh gates.

## Final integrated consumer — PASS for the finite diagnostic

ReaderDBE7EE44/tests338B0005 retire the custom v6 parser and call the authenticated
unchanged owner with the actual new operation entry. Global receipt ordinal307
and pass-local lineage77 are independently bound. Host path identity, console
framing and exact compiler-warning fixes preserve raw evidence and strict native
formats; no process/log/source artifact was rewritten to make a check pass.

reader-observe-call-r4b: exit0/10899.4116ms, stdout1825B/stderr0,
PASS_ISOLATED_BRANCH_DIAGNOSTIC_ONLY. Literal47/47 PASS428.5846ms. Authenticated
actual-run semantic controls21/21 expected rejections PASS13116.7414ms;
actual-data geometry controls13/13 PASS446.4725ms. Wrong manifest digest rejects
BF_MANIFEST_HASH (exit2/376.6319ms); wrong genuine operation rejects
BF_NATIVE_ARGUMENTS (exit2/8574.095ms). Positive baselines before/after mutations
pass; mutations are in memory and no historical host evidence is manufactured.
All listed processes have returned proven exits and absent log/process roots.

This accepts only the exact copper-free directed branch under the unchanged
constraints. Original failure evidence, early-injection coverage limits, old
constructor/consumer errors and unrelated resource uncertainty remain unchanged.
Whole-board geometry/all-net continuity, playable100 and production adoption are
not granted. Publication still requires packaging, final input audit and root
integrated diff review; no completion email has yet been sent for this milestone.
