# A02 — predicate and geometry correctness seams

Status: IMPLEMENTED — ACCEPTED. All required gates and independent delta review passed.
Accepted A01/source baseline: `1eff2278ccd53181951c19d2965f0b32d9f0a126`.
Branch: `codex/task43p-final-recovery`; upstream: same branch on `origin`.
Entry worktree: clean, including untracked paths. A01 and N00 remain closed.

## Frozen acceptance scope

Only F3 admission/hypothesis consistency, F5 bend semantics, F6 connected
placement arithmetic, and their necessary replay boundaries are authorized.
A03, router redesign, copper canonicalization, new fault families, large-board
work, configuration changes, and historical acceptance campaigns are excluded.

| Finding | Production boundary / baseline reproduction | Corrected oracle and fixtures | Replay decision | Final verifier / environment |
| --- | --- | --- | --- | --- |
| F3 | GeneratedFaultCandidate, GeneratedFaultEngine, GeneratedDiagnosticSolvabilityAdmission/Contract and Task41 proof enumeration/regeneration; compatible unserviceable open mixed with valid candidates, same-owner hypotheses | Null/incompatible/missing or rejected serviceability excluded; admitted hypotheses counted, selected and proved by semantic key; two hypotheses/one owner; identical duplicates deterministic; conflicts and empty live pools fail; divergent proof rejected | Compare admitted keys and selected fault for retained Task48/49 fixtures before deciding whether selection changes | Repository-native Java contracts plus actual compiled diagnostic routes; JDK8, GWT2.7 and Browser |
| F5 | PcbBoardLayout.getTraceBendCount and route-quality consumers; unequal collinear subdivisions | Equal/unequal horizontal/vertical subdivisions, orthogonal turns, reverse traversal, U-turns, repeated points, coordinate boundaries; diagonals remain invalid | Preserve old score/winning layout/fingerprint where accepted descriptors reach the old metric; compare before/after explicitly | Production method regressions, layout validation, deterministic layout samples and compiled runtime |
| F6 | SeededPcbLayoutGenerator.placeTopologyComponent and footprint translation; raw weighted neighbor target | One/multiple weighted neighbors, no-neighbor center fallback, local pad offsets, positive/negative/boundary translations, safe out-of-range failure; production caller consumes helper | Preserve accepted geometry3 dispatch if corrected placement changes replay; new generation explicitly selects corrected contract | Production raw-target tests plus deterministic generated geometry and compiled normal challenge |

Closed final gates: focused A02 regression suite; affected candidate/fault/
diagnostic/PCB contracts; descriptor, block, assembly and challenge contracts
including Task48/49 compatibility; final integrated JDK8/GWT2.7 OBF production
build (all five permutations); targeted compiled admission, hypothesis identity,
legacy/corrected generation, geometry/probes, diagnosis, physical repair and
customer retest; developer-only privacy and owner restoration; exact task-owned
resource cleanup; one fresh independent Luna MAX review and any needed delta
review. Helpers, compiled developer checks and actual player interaction are
separate evidence categories. Required missing proof is BLOCKED, never PASS.

Baseline reproductions were retained before behavior changes; exact fixtures,
candidate fingerprints and gate receipts follow below and in adjacent artifacts.
Old A01 and Task48/49 evidence remains historical and unchanged.

## Preserved baseline and root causes

The native probes were compiled with JDK8 against the accepted baseline's real
client classes and GWT2.7 jars. The unrelated Task35 developer verifier has a
pre-existing generic-reference comparison that javac8 rejects; that one class
was replaced in the JVM harness with a throwing stub. A02 methods are unchanged
production bytecode. This JVM gate does not qualify GWT UI or solver execution.

| Finding | Fixture and actual production result | Independent expectation / demonstrated impact |
| --- | --- | --- |
| F3 | `GeneratedFaultEngine.select(long, Vector)` and `select(type, Vector)` on compatible `F3_BAD_OPEN` (no repair action) plus serviceable `F3_GOOD_OPEN`: admission count/owner count 1/1, both selectors throw for the bad record | Both selectors should exclude the unserviceable record and select the good one. Selection/admission mismatch is reproduced; no existing normal-player failure is claimed for this injected pool. Task41's actual private enumeration also returns hardcoded type routes on the detached path. |
| F5 | `PcbBoardLayout.getTraceBendCount`: `(0,0)->(10,0)->(30,0)` returns 1; vertical, reverse traversal and near-MAX straight cases also return 1 | The route never changes direction, so expected 0. Real quality scoring/validation consumes this metric. Generated winning-layout impact is measured separately. |
| F6 | Actual private `placeTopologyComponent`: LED fixture, J1 at (100,150), prototype R1 pad (30,30), J1 pad (190,190), link weight 1.35; zero jitter. Historical raw target (480,375), production search result (360,320) | Desired origin is (190,190)-(30,30)=(160,160). The historical center origin (320,215) is added again. The later offset search changes the selected placement; the returned placement is not treated as the raw-target oracle. |

Sources/results: [F3 probe](A02F3Baseline.java), [F3 output](baseline-f3.txt),
[geometry probe](A02BaselineProbe.java), [geometry output](baseline-geometry.txt).
One unused absolute-path constant was removed from the retained F3 source;
executable statements and captured results are unchanged. The original geometry
probe's auxiliary `corrected` oracle mishandles its repeated-point case 5:
it reports 1; the required zero-length policy gives 0. This failed auxiliary
oracle is retained explicitly, not used to define corrected acceptance.

Fresh accepted-build Task48/49 Browser results are summarized in
[baseline-replay.json](baseline-replay.json): both PASS, eight cases each,
755/1053 assertions, original owner restored, candidate cleanup PASS. Three
representative per-route receipts are retained in that compact summary. A first
attempt to start the Task49 verifier on its controlled ordinary initial owner
produced no terminal receipt; the established LED seed3 verifier entry produced
the qualified results. No missing receipt is labeled PASS.

## A02 coordinate and replay contracts

Prototype footprints are created at origin (0,0); their pads are local offsets.
A placed footprint's component origin and pad coordinates are in board/world
coordinates. The connected target solves for the candidate component origin:
`round(sum(weight * (neighborWorldPad - candidateLocalPad)) / sum(weight))`.
The center origin is used only with no usable placed neighbor. Coordinate
differences are widened before subtraction, weighted sums must remain finite,
and a rounded origin outside the integer coordinate range is rejected.

The production caller then consumes its existing two jitter draws, searches its
existing offsets and grid alignment, and applies its existing fit/quality checks.
Footprint translation applies the chosen origin once. After routing and labels,
`compactToContent` translates the whole accepted board to its final margin and
location. Raw target translation is tested before those independent operations;
final compaction intentionally normalizes board location.

Package geometry remains `PcbGeometryContractVersion.CURRENT=3`.
`legacy-leaf@1` dispatches seeded layout algorithm3, preserving old placement,
raw-bend route scores and quality admission. `legacy-leaf@2` dispatches algorithm4
for LED, diode and parallel seeded families. Their ordinary generation defaults
to algorithm4. Fixed RC/NPN/NMOS families retain algorithm3. The new descriptor
keeps `geometry=3` because that field denotes the package schema; no replay
manifest/schema framework is added. The exact geometry fingerprint format is
unchanged; algorithm identity is recorded separately. Corrected Task46 diagnostic
snapshots use version2 and include hypothesis keys; legacy snapshots retain
version1 and their existing rows.

Task48 `bounded-assembler@2`, Task49 `bounded-assembler@3`, their providers/value
policy and the fixed controlled geometry factory remain unchanged. The Task47
seeded canary is explicitly dispatched through algorithm3. Generator2 retains
its fixed 330-ohm/0.25-W meaning.

## Integrated native evidence

[Focused receipts](focused-contracts.txt) are from the final integrated native
suite, JDK `1.8.0_502`, source/target 7, exit 0 and owned-scratch cleanup PASS.
Candidate tests call the real predicate, key, selectors, contract, Task41
non-null-owner enumeration and private route generation. They cover two R1
hypotheses/one owner, mixed unserviceable open exclusion, an empty admitted
owner failing instead of receiving a detached fallback, duplicate-key rejection,
missing/substituted/duplicate proof receipts, and exact current/legacy route
regeneration. Numeric key fields encode `Double.doubleToLongBits` as decimal
longs, preventing JDK/GWT decimal-formatting differences. These native tests
qualify generation and proof plumbing, not installed solver or player actions.

The geometry test passes 66 assertions. The synthetic unequal straight
subdivision changes route score from `3399.2749999999996` to
`3364.2749999999996` (one false bend removed, delta -35). Rescoring the exact
first five viable legacy candidates for LED seeds0/2, diode seed0 and parallel
seed3 changes none of their scores, winners or geometry fingerprints. This is
a bounded 20-candidate result, not proof over all seeds or previously rejected
attempts. F6's actual private placement fixture changes from `(360,320)` to
`(240,160)` after the same zero-jitter/search pipeline; raw target is `(160,160)`.
Positive, negative and boundary translations move that raw target exactly once.
Diagonal and degenerate traces reject at their specific geometry checks.

[Baseline production geometry](baseline-production-geometry.txt) retains full
geometry/component/trace/silkscreen SHA256 digests from the pre-change classes.
The focused suite verifies exact legacy geometry digests and scores against four
representative records. New seeded geometry is required to differ where the
corrected placement path is reached; compiled old/new outputs are a separate gate.

[Existing affected contracts](existing-contracts.txt) passed: Task44 247,
Task45 202, Task46 410 (36 negatives), Task47 269, Task48 224 plus 12 switched
compatibility assertions, and Task49 137; independent named-stream/assembly/
value oracles also passed. These pure gates do not certify live Task48/49.

Retained development failures: the first all-client JVM compile exposed the
pre-existing unrelated Task35 generic comparison (handled by the documented
throwing stub); initial geometry anonymous captures needed Java7 `final`
locals; a transient root verifier call referenced a private settlement method;
the initial native route fixture lacked the CircuitElm grid context. A later
test-only regeneration adapter incorrectly attempted installed runtime
capability validation before installation; it was removed and the native test
now invokes the actual private Task41 route directly. Required live capability
validation remains in the production installation path and browser gate.
None of these failed runs is labeled PASS.

The default shell launcher began returning `0xC0000142` before executing a
command, including a read-only `tasklist` attempt. The existing runtime's hidden
pipe-based process launcher successfully runs the identical PowerShell scripts,
JDK and repository cleanup helpers. One build launch using file-backed standard
handles failed with the same signature; the pipe-based launch reaches GWT.
No global configuration, privilege or verifier ownership rules were changed.

## Compiled and visible production gates

The final JDK8/GWT2.7 OBF build passed all five permutations, with 53.082 seconds
for compilation and 1.292 seconds for linking. [Build receipt](production-build.txt)
records the source/execution digests. The native suite and all compiled checks
use the integrated A02 source, not the accepted A01 build.

[Compiled hypothesis/geometry receipts](runtime-hypotheses-geometry.json) pass
for LED seed3 (3 hypotheses/2 owners), diode seed0 (1/1), and parallel seed3
(2/1). Each uses actual normal generation at algorithm4, proves the exact key
set with real probes, solver observations, physical repair and customer retest,
checks compatible/unserviceable exclusion, regenerates both descriptor versions,
and explicitly proves legacy algorithm3 admission. Original owners restore and
detached candidate cleanup passes. The fixed numeric hypothesis fixture also
passes under GWT. Both same-owner parallel hypotheses survive the compiled proof.

The [Task41 corpus](task41-corpus.json) passes 14 routes and 128 solver samples.
An initial `tsjDebug=true` Task41 entry failed because that route did not create
a rendered workbench; the established ordinary-workbench entry passed. One
receipt read timed out before CDP dispatch while the corpus ran; the completed
DOM receipt was then read successfully. Those setup/transport failures are
separate from the completed product gate.

[Task48/49 compatibility](final-replay.json) passes 755/1053 assertions, eight
cases per task, exact original-owner restoration and candidate cleanup. A
recursive comparison of **every field in both full baseline/final reports**
finds zero differences, preserving Generator2 fixed330/.25 and the accepted
Task49 recipe/geometry/version meaning. Normalized full-report digests are
retained. [Task46 replay](task46-replay.json) passes corrected `legacy-leaf@2`,
snapshot2, direct/replay equality and 18 unsupported-input rejections.

[Visible player and privacy receipts](player-and-canaries.json) are separate
from developer proof: the ordinary LED seed3 workbench reports the unresolved
complaint, reads supply12V and resistor100kΩ through real left/red and
right/black clicks, exits meter mode on reselection, removes R1 physically,
installs a new1000Ω part and passes customer retest with the LED lit. Explicit
A02 failure reports FAIL with no passing receipt. Without `tsjDebug`, both
verifier/failure flags are ignored and no hypothesis/developer receipt appears.

Curated real screenshots, each inspected nonblank:

- [Faulty retest and supply](player-01-faulty-retest-supply.png): ordinary
  complaint remains unresolved while the measured supply is12V.
- [Resistance diagnosis](player-02-resistance-diagnosis.png): power isolated,
  physical R1 probes and100kΩ result.
- [Physical removal](player-03-physical-removal.png): empty R1 slot, removed
  resistor in the tray and replacement controls.
- [Successful repair](player-04-repaired-customer-pass.png): replacement fitted,
  indicator lit and explicit customer retest success.

The task-owned Browser tab was closed. Preview PID79836 was revalidated by
exact command, executable, parent and creation ticks639244333159301524, then
stopped through the existing exact-process helper. Process absence and
authoritative empty port8899 listener inspection both passed. No unrelated
process was stopped. Final scratch reconciliation and independent review are
recorded in the closure checkpoint.

## Independent review repairs and final evidence boundary

The fresh independent review found two F3 blockers after the initial gates:
Task41 compared its unsorted proof keys against the sorted contract (observable
for NPN), and the dynamic corpus omitted the historical RC seed2 and NPN/NMOS
seed1/2 cases. Task41 now sorts before comparing, restores the established
seed/type fixtures through exact canonical hypothesis selection, and scopes
corpus duplicate checks by family/seed. The native regression invokes the real
private corpus and regenerates all 14 expected seed/type/hypothesis routes.

The explicit A02 compiled verifier now supports fixed-family admission as well
as seeded geometry. Its one-shot entry uses the existing bounded settlement
helper so NPN's pending analysis completes before proof. This change applies
only when the debug-gated A02 flag is requested. Two initial NPN entry attempts
produced no terminal receipt; they are NOT RUN, not passing proof.

[Review delta contracts](review-delta-contracts.txt) pass the final integrated
Candidate, Geometry66 and Replay112 suite, with exit0 and owned scratch cleanup.
[Final build](review-delta-build.txt) passes JDK8/GWT2.7 all five permutations
(47.112 seconds compile, 1.069 seconds link). The final source digest is
`c54876b0559bac5d06713e7ce7424acfb3d8feec428ec716321086563daedda7`;
execution digest is
`9592cd56c77db015231cc58cdbbf0f65c52f034c3a80216523a73cdfd4673029`.

[Final compiled proofs](review-delta-runtime.json) pass NPN seed2 (3 hypotheses,
2 owners), LED seed3 (3/2), diode seed0 (1/1), and parallel seed3 (2/1). NPN
proves the formerly order-sensitive complete key set, exact selected identity,
repair/retest, both retained fixed geometry dispatches and owner restoration.
The seeded routes repeat both algorithm3 and algorithm4 proof on this final build.
Earlier source/build receipts above remain pre-review evidence; they are not
relabeled as final.

[Restored corpus](review-delta-task41.json) passes all 14 historical seed/type
cases and 128 samples. [Final Task48/49](review-delta-replay.json) pass 755/1053
assertions and preserve every field in the full accepted-baseline reports.
[Final canaries](review-delta-canaries.json) retain explicit failure with no
passing report, and no developer/hypothesis receipt with debug disabled.
[Delta cleanup](review-delta-cleanup.txt) proves no Browser tab, owned native/build
process or preview listener remains. The first cleanup identity check rejected
a rounded 64-bit start-tick literal; original raw digits and fresh string-valued
inspection matched before exact termination. No identity rule was relaxed.

Evidence reuse is limited to unchanged inputs. The pure challenge/block/assembly
scripts and all their production dependencies are unchanged by the review delta
(Task41, the debug-only A02 verifier/entry and its native regression). The focused
A02 suite, final production build, all four A02 runtime routes, restored Task41
corpus, Task48/49 and debug canaries were rerun. Earlier Task46 standalone replay
and four visible-player screenshots are retained across unchanged descriptor/
generator/geometry, probe, instrument, mutation, parts, power and customer-retest
implementations. Final compiled routes repeat exact replay, physical repair,
retest and restoration, and the final debug-off check confirms the normal route
ignores the amended verifier. This is an explicit dependency audit, not a claim
that the earlier screenshots or standalone Task46 receipt were recollected.

## Review and acceptance closure

Fresh independent Luna MAX reviewer /root/a02_independent_review (Banach)
passed the targeted integrated repair review and independently reran the final
Candidate/Geometry66/Replay112 suite with cleanup PASS. The original two F3
blockers are resolved. One nonblocking observation remains: the sorted corpus
key vector is used only for duplicate checking; removing the unnecessary sort
is optional cleanup outside this correctness pass. No required proof is missing.

The [candidate manifest](candidate-manifest.json) binds the accepted A01 base,
final source/execution identities and every changed production/script/test file.
[Review record](independent-review.json) retains initial findings and final
disposition. A03 is the next eligible boundary and remains UNSTARTED.

Evidence packaging normalizes only trailing whitespace and the extra terminal
blank line in the retained baseline probe/cleanup text. Executable statements,
results and failure signatures are unchanged.
