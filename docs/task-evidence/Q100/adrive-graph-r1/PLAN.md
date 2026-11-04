# Actual A_DRIVE pre-guard graph capture

## Current acceptance — 2026-11-04

R11 actual capture and all final gates are locally accepted for the exact finite
scope in [README.md](README.md) and qualification.json. Published implementation
is `b9a2146abca9b4a00747d0736deb333cd92ee039`; see publication.md for the verified
remote and one completion Gmail.
The R10 rejection and provisional estimates below remain historical design
evidence. No feasibility search or production routing treatment has started.

Status: R10 capture design INCOMPLETE / NOT ADOPTED after the actual work-limit
failure. R11 is the replacement private observer contract, frozen in WIRE_SPEC.md
before its implementation. This remains the next bounded substep of the accepted
guard's NEXT_GRAPH_PLAN.md. Feasibility search is separate and unstarted. No
production routing treatment is included.

## Candidate and question

Parent96589b8dfaf17cdd7bc74deec4518ab36891c66a contains published implementation
9dc822d4c15df3ee21c0f06c12339fb145b735f9; completion Gmail1a1a7ed052322950 was
sent once. The new branch is codex/q100-adrive-graph-20261104. Parent native
manifest6260778c6b16670038e22a8e8ae4ad329d2a2e6d2216ede96bda820edd6d6458
closes1079 inputs/87245070 bytes; Python42 and SDK9 are byte-exact at bootstrap.

Capture the graph that the actual retained copied A_DRIVE BranchSearch sees
before its Publisher's stage1 quality rejection, including the complete legal
goal mask and its source identities. The observed path's final QA.B terminal is
not an assertion that no other goal is legal. Capture does not prove route
feasibility, board impossibility, a selected alternate route, or route adoption.

Use root9007199254740993, placement-6587958367974952701,
routing8445301674744508279, request/index0/attempt4/pass1, sourceLOAD12branch73,
source cost60/copy240/cap48/order2, actual A_DRIVEbranch142/netpos24next25.
The measured startRPDA.1(3790,2560), QA.B(3830,2410), grid478x309/origin30,30/
pitch10,82-row pathSHAa2d33696158882cf3d6bd4c949c021c4456a00b325b80fe3815b09df2db15c10
and37-hole/177-trace copied prefix are parity expectations, not invented input
data. Object identities must be measured in the new process. Keep the17/88
paused source unchanged and unresumed.

## Source contract and required representation

The source map in OWNER_CONTRACT.md distinguishes static capture from actual
path constraints. Reuse the real owner/router copy and comparison machinery.
The private FixedPrefixCaptureTask constructor and finish method are candidates
for a test-only adapter at the already measured live checkpoint; validate their
actual signatures and ownership before use. Do not fabricate an API, select a
different branch, use the failed-branch callback on FOUND, or mutate the owner to
make the existing callback fire. No production behavior change is proposed.

Capture both face step masks, both face goal bits, static VIA-site eligibility,
start face mask, dimensions/origin/pitch, actual root/start/reached PTH pads,
actual all-hole list/count and VIA-kind/per-kind counts, policy/secondary/turn/
VIA costs, maximum holes and transitions. Bind these to the actual copied
owner/Search/BranchSearch/Publisher/layout and source/context lineage. Compare
every captured goal bit against the retained actual BranchSearch goal array.

For each goal face/cell, retain all applicable actual root/reached-pad and
same-net tree/trace/barrel source identities. Use existing stable pad IDs,
PcbTraceGeometry source IDs and PcbBoardHole IDs; retain their source rows and
coordinates/layers. Derived grid-state keys are graph coordinates, not invented
physical conductor identities. Require complete coverage and reject a missing,
foreign, duplicate, unsupported or unattributed source rather than guessing.
Any attribution relation must follow the real Router occupancy/grid mapping;
geometric proximity alone cannot create a goal or electrical connection.

The static VIA mask is HISTORY_RELAXED: it omits remaining-hole capacity and
earlier path-VIA spacing. Preserve that label. Record exact additional path
rules: transitions at most4, all-hole-count plus path transitions at most48, and
the actual prior-VIA spacing predicate (abs(dx)<20 AND abs(dy)<20, strictly). Initial opposite-face seed cost2 is
not a drilled VIA. Capture costs240/10/12/35; do not reuse the generic cost60
oracle as a copied-owner selection or feasibility proof. No search runs here.

## Bounds, charging and cleanup

Keep one90s operation,640 enclosing controller groups,5s per group and32768
actual P07 owner steps per group. Request1M/branch100K/grid250K/order3/holes48/
transitions4/access700K/cleanup120s/shutdown15s stay unchanged. Existing fixed-
prefix graph artifact caps are250K cells/8MiB canonical payload,390 holes and
246 pads; the current guard's4096-node/1MiB payload limit still applies only to
its separate path witness. Keep16MiB event-line and32MiB capture-file limits.
No increased limit is authorized. Reject oversized metadata/source collections
under the existing applicable collection caps; do not truncate silently.

Capture, goal provenance, recomputation, canonical serialization, UTF-8 and
hashing execute in named charged phases. Preserve actual P07 continuation
owner-step counters separately from graph observer copy/probe/comparison work.
Each internal copy/comparison call retains its existing32768-entry quantum;
R11 groups at most four such calls, with explicit observer bound32768*calls
(at most131072), ending before a task-category change. This replaces the
unaccepted R10 one-copy-call grouping; it does not change actual P07 owner-step
limits. Scan calls remain4096 probes each, at most four/group; finalizer one.
Record observable work and conservative envelope without relabeling observer
entries as continuation steps. Internal capture units remain at most four times
task groups and within2560 overall. All actual unit/operation deadlines remain.
Use the new versioned, lossless packed mask strings and fuse bounded ASCII
serialization/encoding/hashing of the same32KiB chunk. Provenance walks at most
32768 states/group with all source checks preserved. Stream only prevalidated
immutable bytes after charged serialization/hashing.

Actual R10 query-graph-r4 hit640 units with345193 edge probes,147702 VIA probes
and the finalizer still pending. Those alone require at least33 further groups
under R10, before final comparisons or metadata. No unchanged rerun or serializer
optimization alone can close this gap. The source-only review found the R11
observer redesign compatible with original routing predicates and operation
caps, while explicitly changing provisional observer grouping/representation.
The roughly616-unit estimate is not evidence of fit. Require actual final-source
capture, full independent decoding/validation, ledger reconciliation, timing,
source parity and cleanup. Earlier failed designs and source captures stay
preserved; no old PASS is reused for changed code. See FAILURES.md.

Before/after capture, compare real router state/effects and source/copy prefix
fingerprints. Afterward let the unchanged next guard call reproduce the same
A_DRIVE:BRANCH_QUALITY_LIMIT without rejected-branch publication. Release all
retained capture/search/router references on success and every failure path.
Record operation and cleanup separately where exported; unknown splits remain
NOT_EXPORTED. An incomplete/budget/stale-owner result is FAIL/INCOMPLETE, not a
successful capture or a routing conclusion.

## Transport and independent acceptance

Freeze the exact wire schema before native/reader implementation. The proposed
private mode is -Rb100Load12Cost240GuardGraphReplay / --cost240-guard-graph in
Rb100Load12DetachedBaselineContractTest, with its own exact command validator.
Keep native v5 original keys; never project a graph command into an old wrapper.
Bind raw base and raw guard payload bytes by SHA-256. Reuse the frozen guard
reader's pure witness validator directly through a private hash-pinned module;
its old three-record transport remains unchanged. The new transport needs its
own complete bounded graph record, complete closed source census and final
summary. Do not promote synthetic fixtures into actual graph evidence.

The independent reader must check command/source/receipt bindings, closed
metadata and row shapes, every goal/source relation and path edge against the
captured masks, exact policy limits/counters and before/after lineage. It must
reject tampered masks, missing goals, false goal attribution, unsupported VIA
semantics, wrong graph/guard/base digests, wrong commands, stale inventory,
budget/cleanup failures and incomplete capture. No history-relaxed candidate
is accepted as a production path. Preserve useful old independent oracles.

## Validation and finish

Root first reviews the final wire and the actual selected-host transport/early
negative canaries. Run focused new reader contracts and actual graph capture/
independent validation, including exact boundary and non-mutation checks. Only
after those pass, run final-source prerequisite2/focused8/native33(original22),
all seven predecessor diagnostic/reader pairs including the guard, unchanged
Python196 methods plus new tests, actual JDK8/GWT Player/Developer builds,
Player privacy/E06State10/Current9, and exactly the three approved MEDIUM roots
under the four-candidate envelope with exact owned-profile cleanup. One
expensive matrix at a time; audit every consumed input and preserve failures.
Any reuse needs the complete unchanged-input boundary and task-required fresh
gates take precedence. Real visible Player input is N/A absent a Player-flow
change; compiled controller checks do not supply human-input/repair evidence.

Root reviews integrated source and evidence, updates roadmap/architecture/report,
then normally commits/pushes and verifies the accepted SHA before one completion
Gmail. Do not publish an unresolved capture blocker as success. Next unstarted
scope after a complete accepted capture is bounded constraint-aware feasibility
or lower-bound analysis under its own frozen contract. Protected HARD remains
unread/unrun in this scope, historical unread UNCERTIFIED; HARD/PSYCHOTIC remain
unavailable/unrun. Full30 is owner-excluded. Independent two-face access remains
unavailable, playable100=0 and beta is held. Preserve other worktrees, raw-only
statuses, prior failure evidence and uncertain8928 resources.
