# R12 Phase 2: normal MEDIUM qualification

Status: finite R12 qualification locally accepted; technical, Player, original22 and 30-root gates PASS; publication follows this acceptance checkpoint. Base `bd846017be5c9f46b1fce3fc9fc90438318cac28`, branch `codex/r12-medium-phase2`. Q100 remains unstarted.

The [v2 plan](artifact-plan-v2.json), SHA `4816a8b6c3df06109eb54f5ede5366b8febdde00ce94094baa46b5403709e9ac`, and source binding `8c6591a9cbbde7a7c90fdc349795e0e8ef9e3408e17296a2a066b5ff5122d333` remain unchanged. It pins 1,255 consumed inputs, 30 identity rows and both complete 357-file inventories. Schema5, predeclared roots and 90s/640-work/5s are unchanged.

The controlled [qualification report](qualification-v2.json) is `PASS_FINITE_R12_QUALIFICATION_V2`, SHA `60b2e8316d60df73bc4158d7e5635988274cc8398a747da6368fd18db5f0bd41`. It covers 30 unique roots in order across profile/development/holdout/replacement-blind/replacement-blind-2 counts 3/3/8/8/8. All 30 raw case reports pass once. The eight first-use replacement-blind-2 roots pass with no retry or substitution. All retain cold proof, five hypotheses and proof rows, five calibration rows with complete observations and five temporal waits. Maximum generation is 68,483ms, 502 work and 1,842ms maximum unit; all five partition host cleanups pass, with the last at 0.9535681s and zero owned survivors/errors.

| Gate | Result |
|---|---|
| [Native49 boundaries](native-boundaries-v2.json) | PASS /576.192s including cleanup; all 48 complete ordered non-wall geometry rows and all 30 manifest identities exact. Selected native union only; full native matrix and independent seed/value/role oracles NOT RUN. |
| Fresh reader contracts | Nine PASS /4.379s phase. |
| Actual JDK8/GWT artifacts | Player11 /Developer21 PASS /80.431s and /112.206s; Player12 /Developer22 PASS /88.837s and /121.758s. Final Player/Developer audits pass; full Developer22 output reproduces its pinned 357-file artifact. |
| [Compiled14 strict aggregate](compiled-boundaries-v2-r2.json) | PASS 14/14; capture/audit phase 1260.520s, cleanup PASS /1.071s. Boundary coverage only, not the full maintained compiled matrix. |
| [Actual Player gate](player-gate-v2.json) | Root-accepted PASS, SHA `e8b259502187c7c317b40237a59f372da879f2b79fdf1bec09c8715a0b23b6fe`. Three unseeded RB56 tickets, zero retries, distinct identities, exact third replay. Four public replay and five session-file rejection cases each preserve the third board on all five compared fields. |
| Original save / source-only retest | Original H19/S4 restored with current-build rebinding; Sensor A source-only connection adds four source actions to H23, with no physical actions, and the visible customer retest passes. |
| Physical repair/retest | Pristine public replay retest fails before mutation. With board power off and supplies disconnected, RENAC is replaced using the recorded RDA catalog acquisition; the final H11 export passes customer retest. This is a finite visible repair path, not a human repair qualification. |
| Player privacy and cleanup | Post-UI audit PASS; five Player caches have no developer markers. Four current screenshots reviewed. Owned preview/tab cleanup PASS /2.5148391s. |
| Formal original22 | All four partitions plus reader/audit/cleanup PASS; [comparison](regression-equivalence-v2.json) `PASS_EXACT_ORIGINAL_22`, SHA `a6d2af22ac4f41bc59ff622a7b4664622893069c6637c0e31dc3372a9a9e4f64`. All 22 full contexts, observations and work match baseline; 1095.551424s sequence elapsed includes maintained cleanup and excludes only new blind8; operation/cleanup remain separate in the qualification report. |
| Finite population | [Qualification](qualification-v2.json) PASS for 30/30 unique roots; no case retries/substitution. All five partition cleanups PASS. |
| Broader release | Locally accepted finite MEDIUM scope; the completion handoff records the subsequent commit/push/email outcome. Public beta HELD; CI NOT RUN; self-hosted Windows runner registration remains a CI prerequisite. Q100 unstarted. |

The historical formal FAIL for root `7817806517725629686` remains retained. The exact original22 comparison uses the completed CPU diagnostic as the non-wall baseline; the fresh current formal unprofiled PASS /64.871s browser operation belongs to the accepted22. The earlier focused unprofiled diagnostic PASS /88.681s is excluded from formal qualification and was not that comparison baseline. The initial compiled14 aggregate remains FAIL for five metadata adapter issues; the corrected aggregate passes on the same raw captures. Pre-observation receipt/argument problems are preserved as external helper defects, not application results. Earlier runtime failures keep their prior classifications.

The accepted Player UI evidence does not claim a direct provider stock count. It records acquisition and physical history; native/compiled stock proof is separate. Public save/restore acceptance covers the tested history/source/repair envelope, not all possible history, damage or catalog states. No claim of all-seed support, human repair, modest-host operation, advanced maximum capacity/endurance, expanded FPS/visual coverage or beta readiness follows.

## Current and historical screenshot files

The four current r3 images have `.jpg` filenames, match the hashes in [player-gate-v2.json](player-gate-v2.json), and were reviewed by the root: [third ticket](screenshots-v2/01-third-ticket.jpg), [restored mixed sources](screenshots-v2/02-restored-mixed-sources.jpg), [unrepaired isolated RENAC](screenshots-v2/03-unrepaired-isolated-renac.jpg), and [repaired customer retest PASS](screenshots-v2/04-repaired-customer-retest-pass.jpg).

Nonblocking archival note: eight earlier r1/r2 screenshot files retain their original `.png` filenames while their file signatures are JPEG. They remain unchanged: [r1 ticket](../rb56-medium-normal-r1/screenshots/01-ticket.png), [r1 unrepaired](../rb56-medium-normal-r1/screenshots/02-unrepaired.png), [r1 repaired](../rb56-medium-normal-r1/screenshots/03-repaired.png), [r2 failed third attempt](../rb56-medium-normal-r2/screenshots/01-current-third-failure.png), [r2 ticket](../rb56-medium-normal-r2/screenshots-v2/01-third-ticket.png), [r2 restored sources](../rb56-medium-normal-r2/screenshots-v2/02-restored-mixed-sources.png), [r2 isolated RENAC](../rb56-medium-normal-r2/screenshots-v2/04-unrepaired-isolated-renac.png), and [r2 repaired retest](../rb56-medium-normal-r2/screenshots-v2/05-customer-retest-passed.png). The current four `.jpg` files have the correct extension and exact report-pinned bytes.

The corrected compiled report SHA is `9256ce99b8902b90003e664ff0715e680a62c51064185808e43a5dba50e25e27`. Its fourteen case oracles passed in the same raw captures used by the retained [initial aggregate FAIL](compiled-boundaries-v2.json). That aggregate's five issues concerned metadata adapters; no application case validator, expected constant or physics oracle changed.

## Historical candidates and retained failures

The [initial compiled14 aggregate](compiled-boundaries-v2.json) remains FAIL for
five metadata adapter issues, with all 14 case oracles PASS. Its raw captures
were retained unchanged for the corrected aggregate above.

Earlier status: IN PROGRESS / publication held. Base
`bd846017be5c9f46b1fce3fc9fc90438318cac28`, branch
`codex/r12-medium-phase2`. The [plan](PLAN.md), [campaign](campaign-plan.json)
and [runtime scope](RUNTIME_SCOPE.md) retain all prior roots and failures.

The table below describes the historical frozen v1 candidate. Its compiled gate
failed, and it is not accepted. The current source adds branch-local primitive
routing-state keys and observation endpoint position hints checked against the
live graph on every use. That candidate's native6 PASS /78.281s, cleanup PASS; actual
Developer18 JDK8/GWT PASS /163.307s. The [compiled pilot](overhead-compiled-pilot-v1.json)
records DC controlled membership9 PASS and both normal-generation cases TIMEOUT;
source/web audits and host cleanup PASS. At that pilot checkpoint, final Player,
compiled, population and publication gates had not passed. The exact
v1 inputs and [runtime scope](RUNTIME_SCOPE-v1.md) remain archived.

The subsequent source also reuses session-owned turn-bound scratch and filters
pad coordinates before endpoint IDs. Native7 PASS /61.468s and actual Developer19
JDK8/GWT PASS /159.049s; its [compiled pilot](overhead-compiled-pilot-v2.json)
still records DC9 PASS and two normal-generation TIMEOUTs. All input audits and
host cleanup pass. This candidate is not accepted.

The [four-run diagnostic](overhead-abba-v1.json) against retained Developer18
records unprofiled FAIL, profiled PASS, profiled PASS, unprofiled FAIL. The two
complete reports match exactly outside measured wall time. A further
[pre-execution stop arm](overhead-prestop-v1.json) also passes after profiling
stops and detaches before the held application script runs. These observations
do not identify a cause, qualify the population or replace either failure.
No engine flags, machine settings, solver equations or required proof changed.
A standalone numeric-kernel experiment is outside the production source.

The [v2 campaign](../rb56-medium-normal-r2/replacement-blind-failure-v2.json)
recorded seven passes and one timeout. Root `7817806517725629686` reached
HYPOTHESES356 at 467 total work / 90.011 s, maximum unit1.782 s. No partial
proof published; rollback and app/host cleanup passed. A CPU-sampled repeat
completed all five proofs /65 samples in 85.744 s with 488 work,375 proof and
unchanged maximum520. This diagnostic repeat does not replace the failed outcome
or qualify that cohort. Both source/web audits match their frozen v2 artifact.

The candidate retains the original routing predicates and search. A small
immutable per-column list narrows the component courtyards considered at point
and adjacent-step queries, in original order. Exact collision, escape and
underpass checks still decide legality; broader traversals retain the full scan.
Equivalent domain/component stroke rectangles share one read-only value.
The solver and its numerical operation sequence remain unchanged.

All twenty-two observed roots are regression after this change. Exactly eight
new roots were fixed using OS randomness before behavior queries; no substitution,
failed-case retry, warm rescue, limit increase or oracle change is permitted.
HARD/PSYCHOTIC remain unavailable, protected HARD unread/unrun and Q100 unstarted.
No all-seed, modest-host, advanced capacity/endurance, human repair or beta claim.

| Gate | Historical frozen v1 result |
|---|---|
| New finite campaign | Frozen; eight new roots NOT RUN. SHA `473ea73c8937f55c7a321ee71aff9cbc1f8c778c49668c8a33391fe42ba38c9d`. |
| Current reader/freezer | Schema5, thirty ordered identity rows, all old roots preserved. Nine Python tests PASS /4.080 s; synthetic reader fixtures are not solver evidence. |
| Indexed geometry coverage and full-scan parity | PASS: 1,008 assertions over both faces, uniform/role widths, actual stroke/point coverage, fixed query outcomes, escapes, underpasses, width changes, nonadjacent traversal and conservative overflow fallback. The existing `canJoinTree` full scan is unchanged. |
| Focused native routing/width contracts | PASS: five suites, exit0 /57.796 s, cleanup PASS. Existing trace geometry78, trace profile89, role routing1,003 and turn-bound7,692 assertions remain. The full native matrix is NOT RUN. |
| Actual JDK8/GWT Developer pilot | PASS: actual five permutations, exit0 /153.653 s, input `7e4c3364b05d87c5b713ac7c0051e2309cd98589872254dc9f684beee039afb6`, complete357-file output `302cf7afe7c36a3d1da25713c93b201d05db1d51ccc58c3cd47f8edf7410838c`. |
| Failed-root current-source canary | [PASS as a focused pilot](routing-index-pilot-v1.json): 89.568 s job /488 work,375 proof/520maximum, five complete proofs/65 samples, maximum unit1.740 s. Full context, program, manifests, proof rows, all observations, waits and stage work match the prior completed diagnostic exactly. Host cleanup PASS /0.990 s; source/web audits match. Thin timing margin; no general speed or qualification claim. |
| Final native census / exact non-wall geometry comparison | PASS: native42 exit0 /727.151 s, cleanup PASS. [All48 complete ordered non-wall cases remain exact](geometry-equivalence-v1.json); all30 identity-only rows validate, with prior22 byte-identical. [Native reuse boundary](native-reuse-boundary-v1.json) after the build-host-only fix preserves Java, native tests/verifier and jars. Full native matrix NOT RUN. |
| Compiled restore/yield/rollback boundaries | [Aggregate FAIL](compiled-boundaries-v1.json):9 PASS/4 FAIL. All6 guards, ordinaryRC and small private/publicRB56 restore cases pass. Both original-save-root cases and both third-Player-root cases time out while proving pristine generation, before history replay. [First failure details](compiled-failure-v1.json): HYPOTHESES347,432 work /90.016 s,max1.783 s. Host cleanup PASS /1.191 s, no owned survivors/errors, input audits exact. Failed reports do not prove app rollback. New blind8 untouched; no Player or population acceptance. |
| Final Player/Developer artifact freeze | [Schema5 plan frozen](artifact-plan-v1.json), SHA `50461b60160aba80805405d1cd988dbe47327ebe82864d1fdf493eaa4c7ff4bc`, source binding `57b8d9013bde15b4cb2e2f3cb33c5022f51338df6364fd8f6235ccd058ab5dd1`. All1,255 qualifier inputs,30 identity rows and both complete357-file inventories are pinned. Actual Player10 PASS /114.380 s; Developer17 PASS /156.651 s, output byte-exact to Developer16 despite the declared build-script input change. |
| Real Player three-attempt / original-save / repair / privacy gate | NOT RUN. |
| Twenty-two regression / exact historical contexts | NOT RUN on r3. |
| Eight first-use blind cases | NOT RUN. |
| Integrated review / publication / Gmail | HELD. |

Raw logs, CPU profiles, actual saves, rejected prototypes and frozen source
snapshots remain outside Git. The earlier [r1 packet](../rb56-medium-normal-r1/README.md)
and [r2 packet](../rb56-medium-normal-r2/README.md) retain their own source-bound
results, limits and failures. A passing historical gate is not a current-source
PASS without an audited unchanged-input boundary.

Two native fixture failures remain retained: exit2 /55.991 s incorrectly expected
all columns of a partly safe overflow fixture to use fallback; exit2 /54.549 s
placed a purportedly clear SUPPLY stroke inside the raised-link courtyard. Both
cleanups pass. Independent integer-bound and declared courtyard calculations
corrected the fixtures; production predicates were unchanged. Remaining fixed
expectations were audited against the declared geometry before the passing run.
Root and independent static review found no implementation blocker within these
two files. A reviewer mentioned a `canJoinTree` index coverage gap; root verified
that this method retains its original full scan, so that gap does not apply.

The first serial build attempt stopped before compilation: Player9 exit2 /1.446 s
because nested Windows PowerShell could not resolve `Get-FileHash` under inherited
module discovery. Both pinned artifact checks now use the existing exported
SHA256 owner, with the same comparisons and compiler arguments. Actual Windows
PowerShell promotion/hash/handle canaries PASS:18 assertions /4.403 s, three
ownership cases and foreign-volume rejection; actual bounded process exit/timeout
canary PASS /1.788 s. No global runtime setting changed. The failed attempt stays
retained; the historical Player10/Developer17 builds and artifact freeze passed,
then compiled13 failed as recorded above.
