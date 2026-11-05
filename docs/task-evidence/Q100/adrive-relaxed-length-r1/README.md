# A_DRIVE fixed-prefix relaxed length bound

Status: **LOCALLY ACCEPTED FINITE DIAGNOSTIC; PUBLICATION PENDING** (2026-11-04).
Source parent `6f905668caf7c62c0cfb42818200fbb86649c99e`, branch
`codex/q100-adrive-length-bound-20261104`. The frozen [R2 plan](PLAN.md) is
unchanged, SHA-256 `d7cb074e9703daa022f852e775417427bf20e561f2d236482169b61038f395c2`.

The exact captured A_DRIVE graph has **no relaxed path within570 length and
four new transitions to either complete goal**. Dropping bend and prior-via
spacing restrictions makes this a superset of production-valid paths. This
excludes this fixed graph under the existing necessary guards. It does not
establish an exact minimum above570, unbounded unreachability, whole-board
impossibility, a routing treatment, or playable100 qualification.

## Candidate, source and method

The retained [capture](../adrive-graph-r1/README.md) was published as
`b9a2146abca9b4a00747d0736deb333cd92ee039`; its one completion Gmail remains
`1a1a8e9a56ef51ba`. This analysis neither regenerates nor resumes it.
Root seed `9007199254740993`, placement `-6587958367974952701`, routing
`8445301674744508279`, request/index0, placement attempt4 and pass1 remain exact.
The cost240/cap48 copy reaches ordering2/A_DRIVE branch142/net position24/next25.
Source17holes/88traces stays unchanged and unresumed; the copied prefix stays
37holes/177traces. Its current-net goal provenance has2pads/0trace rows/37VIAs;
that per-net census is distinct from whole-prefix trace count.

The packed graph has478x309 cells, origin(30,30), pitch10, complete starts
121310/269012 and complete goals114144/261846. RPDA.1(3790,2560) to
QA.B(3830,2410) gives direct190 and the unchanged3x limit570 on both faces.
Graph SHA-256 is `0c73ce21580a9c4e2508a251425907da01d11c200483409870181a5d8d30ef11`,
738743 canonical bytes. Native1079 source manifest is
`f27c588a5690e666a934c53d120c52a6911b46f5ba0db72ce8585decbbdb428c`.

The new Python adapter hash-loads the unchanged strict reader from the original
capture checkout, validates all raw native/source bindings and deep-compares
its report with the accepted report. Bootstrap and selected-checkout helpers
are rechecked; no reader code, file identity or root check is overridden.
0-1 BFS uses directed planar edges at one pitch, VIA flips at zero length and
an explicit transitions-used dimension, cap=min(4,48-37)=4. Per-goal Manhattan
lower bounds prune only states unable to meet every nonzero goal's own3x bound.
The queue must drain. No parents, selected endpoint, route witness or geometry
are emitted. A private tiny-test observer exposes retained distances only to
the independent tests; normal API/CLI callers never request it.

## Final-source evidence

The controlled [qualification summary](qualification.json) and
[input audit](final-input-audit.json) contain compact metadata only. Raw captures,
host scripts, manifests and receipts remain in task-owned external scratch.
Let QN denote its `adrive-length-bound-r1` directory; all hosts below use the
pinned Windows PowerShell5.1 executable and Python3.13. Runtime executable hashes
are recorded in the input audit, PID/start/commands in external launch receipts.

| Gate | Result and scope |
|---|---|
| `run-python-units-r2.ps1 -RunName length-python-r4` | PASS12methods, no skips,0.5105946s including child teardown.1,024 independent expanded-state Bellman-Ford comparisons check every retained distance, goal minima, verdict and counters. |
| `run-analysis-host-r3.ps1 -RunName actual-r4` | PASS, exit0, termination proven, empty stderr;7.4386683s including child teardown. Root review separately accepts bounded exclusion. |
| `wrong-sha-r3` | Expected exit2/REJECTED at `OUTER_INPUT_SPEC_SHA256_MISMATCH`; original artifacts unchanged. |
| `tampered-receipt-r2` | Expected exit2/REJECTED at unchanged reader's `NATIVE_RUN_RECEIPT_BINDING_INVALID`; one-byte task-owned copy, accepted receipt untouched. |
| `run-junction-control-r3.ps1` / `junction-negative-r2` | Real junction rejected at `OUTER_INPUT_SPEC_PATH_INVALID`. Exact link removed nonrecursively; regular target and original unchanged. Operation1.6632662s; cleanup0.01078s. |
| `review-actual-r2.py` | PASS strict result/hash/identity/complete-goal/counter/group/limit/input/negative/cleanup review. SHA-256 `dd7adf350cc38c5e455f6b26767922c0db3528c19980e8a06a51bf031d8e6322`. |
| Final consumed-input and variant/archive audits | PASS1079native/850production/223contractJava, old44Python,9SDK,24normal3 sources,28old hosts, selected executables,1202compiler inputs/357outputs per variant and archived Player357. |
| Independent source review | Limited PASS on final005b2c86/17b4c238 source; no execution or end-to-end claim by reviewer. Required retained-distance coverage gap fixed and freshly tested. |

Actual search discovers/finalizes6006 labels, peak queue359, pending0. It counts
73572 analysis operations in32768/32768/8036 groups. Directions24024, VIA checks
6006, valid-edge attempts25524 =24606 accepted+918 pruned. Pushes/pops6006;
successful relaxations6004 plus2seeds. Both goal results are NO_PATH_WITHIN_LIMIT,
with null minimum. These are observations, not blessed routing goldens.

One shared operation uses59/640groups,7.124s/90s, maximum4.689s/5s (the atomic
unchanged-reader validation). Report preparation1ms; owner P07 steps0. Opaque
reader/source-audit work is NOT_EXPORTED, not falsely counted as zero. Decode
and validation groups each cap32768 entries. Diagnostic1Mlabel/250Kqueue caps
are unchanged and are not process-RSS claims. The result is serialized once in
a charged group and streamed with a bounded scalar timing footer. Selected-host
cleanup split is NOT_EXPORTED; child termination is proved by the actual host.

Final46 Python source manifest SHA-256
`5958327a63cb4ead68712f7dd7cf02b855a8bd2ebc53d85e8e9b53540c463b10`,
8632raw bytes/1989614summed bytes. New analyzer SHA-256
`005b2c869188a0f2b2bd33df3fe248fe1a5a95710d2dc7cb96bfb9d41bb7b7e5`;
test SHA-256 `17b4c238ff5f8a55b8a847f864c7c52a60ae69efc739838049a6d856d0dbadd6`.
Actual report SHA-256 `ef76fa5716915d98871e14caf7c414f6432f90a61a797ea6cb500caef47e185e`,
15576bytes. Earlier failures are retained in [FAILURES.md](FAILURES.md).

## Reuse, limits and next scope

This Python-only diagnostic changes no production/native/build input, routing,
model epoch, budget, electrical oracle, Player UI or population. Byte audits
justify reusing the capture milestone's recorded JDK8/GWT Player/Developer,
native2/8/33(original22), seven readers,231Python methods(227PASS/4declared
privilege skips), privacy/E06State10/Current9 and exactly3approved normal MEDIUM
roots. These are unchanged-input reuse, not newly run qualification. Player and
Developer input digests remain distinct; active output is Developer, archived
Player output independently matches all357files. Fresh GWT/native/population
runs and visible Player input/screenshots are NOT APPLICABLE to this scope.

Playable100 remains0; beta held. Full30 is owner-excluded; HARD/PSYCHOTIC stay
unavailable/unrun. No protected HARD body was read/run here; historical unread
remains UNCERTIFIED. No new access, human repair, visual/FPS, all-seed, capacity
or physical qualification follows. Self-hosted Windows runner setup remains a
prerequisite; no remote CI-green claim. All previous failures/limits remain.

Next is a bounded source audit and frozen generic earlier-prefix/order treatment
or necessary diagnostic. Retrying this identical fixed graph cannot overcome
the proved necessary length bound. Any treatment still needs actual finalizer,
clearance/connectivity/access, functional and regression proofs before adoption.
