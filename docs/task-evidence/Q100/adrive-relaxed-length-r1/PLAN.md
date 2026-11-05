# A_DRIVE bounded relaxed length analysis — frozen R2

Status: frozen for implementation after capture publication; no analysis result.
The accepted capture implementation is b9a2146abca9b4a00747d0736deb333cd92ee039,
receipt6f905668caf7c62c0cfb42818200fbb86649c99e, exact remote verified. Its Gmail
1a1a8e9a56ef51ba was sent ONCE; never repeat it. New source parent is receipt6f905668.

## Scope and immutable input

Add only analyze_load12_adrive_relaxed_bound.py and its independent tests under
tests/qualification/rb100-layout, plus scoped documentation/evidence. No Java,
production model, routing, epoch, population or visible Player change. The tool
reads the accepted capture and never regenerates/resumes routing or emits a path.
CircuitJS remains electrical truth; this is mathematical analysis of its existing
PCB routing owner's necessary masks, not an electrical replacement solver.

Use the exact R11 captured graph, validated afresh through the unchanged private
reader fa31046816c4c70b643357fcce34b5daf161fe71e784bc0e0ccddce40baa9892 and its
run_validation API. Bind and deep-compare the returned report to the retained
accepted reader report SHA82b5eab039994175816f65c21e914282793c56d3987a48b8722b7dc81b0570ba.
Native result SHAac118abf69d373860e9c58365c11cbbf0128e4871046fb92744871e1dd681647,
receipt8ed3156d48fde4aa9615e76abaf48b352e27bbb1302250312202d0f7c98f33ca and
stdout483f1e04acaaba8e0f255b667a4b66dc88758abe0dfd1591bc38cb7e8ef5d456 identify
the accepted native run. Validate raw artifacts/source, not the small root review.

Native1079 manifest f27c588a5690e666a934c53d120c52a6911b46f5ba0db72ce8585decbbdb428c,
228661raw bytes/87400672 summed bytes, source revision20/layout26/dependency28;
packed graph0c73ce21580a9c4e2508a251425907da01d11c200483409870181a5d8d30ef11,
738743canonical ASCII bytes, protocol p07-fixed-prefix-packed-graph-v1,
DIRECTED_TWO_FACE_HISTORY_RELAXED. Grid478x309/cells147702/origin30,30/pitch10.
Exact root seed9007199254740993, placement-6587958367974952701,
routing8445301674744508279, request/index0/attempt4/pass1; copied A_DRIVE branch142
at ordering2/net position24/next25. Source17holes/88traces unresumed; copied
37allholes(per-kindVIA37)/177traces unchanged. Retained source/artifacts remain
in their task-owned capture worktree/QW; do not copy raw captures into Git.

Actual start RPDA.1(3790,2560), cell121310. Owner policy initialFacesMask3 and
captured starts give states121310 and269012. Both are zero physical length,
zero new transitions/holes; secondary A* seed cost2 is not length or a VIA.
Complete actual goals114144 and261846 are QA.B's two faces at(3830,2410),
direct190/length limit570. Require the complete mask, not the observed endpoint.
These are exact fixture facts, not universal assumptions about other boards.

## CLI and closed input

CLI: --input-spec PATH --input-spec-sha256 SHA256; no budget, root, route or
seed override options. Closed ten-key input schema:

- schema=p07-load12-cost240-adrive-relaxed-bound-input-v1
- target=LOAD12
- purpose=FIXED_CAPTURE_ADRIVE_BOUNDED_RELAXATION
- captureInputSpecPath and captureInputSpecSha256
- captureReaderReportPath and captureReaderReportSha256
- captureReaderSha256
- graphSha256 and graphCanonicalBytes

SHA values are lowercase64hex; counts are exact integers, never booleans.
Spec limit16KiB; result limit1MiB. Keep existing250K-cell/8MiB graph,
16MiB-line/32MiB capture-file limits and all source/metadata collection caps.
Hash/reparse/alias/path rules and strict UTF-8/duplicate-key/nonfinite rejection
come through the maintained readers; apply the same rules to added spec/report
paths. No caller-supplied result path: emit one JSON result to stdout.

Hash-load the pinned current reader privately, call its API, and require the
exact accepted native command, source census, hashes, goal completeness, policy,
cleanup/status and source parity. After validation, reread the receipt boundedly
and require its raw SHA unchanged before decoding the already validated packed
graph. Recheck inputs/helper bytes after analysis and before successful output.
Do not project a new command into an old native wrapper or modify old oracles.

## Core contract and mathematics

Expose analyze_relaxed_graph(graph, budget=None) for synthetic contracts. Its
normalized read-only graph has width,height,pitch,starts,goalStates,steps,vias,
allHoleCount,maximumHoles,maximumTransitions. Steps has2*cells nibble masks,
vias has cells booleans, starts/goalStates are complete face-encoded state IDs.
All starts must share their one physical start cell. Core fixtures may vary
geometry/caps; CLI is restricted to the exact accepted fixture above.

Use0-1 BFS on(layer,cell,transitionsUsed), normalizing each planar pitch to1
and every same-cell static VIA flip to0; multiply final distances by pitch.
Every planar move must follow its source directed mask and stay in-grid; never
symmetrize or wrap rows. VIA requires the captured static mask and increments
transitions. Effective cap=min(maximumTransitions,maximumHoles-allHoleCount),
here min(4,48-37)=4. Invalid negative capacity is rejected, not clamped silently.
Do not charge A* costs10/12/35/240 or secondary-start2 as physical length.

For each complete goal, direct=Manhattan(start cell,goal cell)*pitch and its
limit=3*direct. Direct-zero is rejected by the unchanged predicate. Prune a
state only if, for EVERY nonzero-direct goal, best distance+Manhattan-to-goal
exceeds that specific goal's limit. The current two limits both equal570.
Do not apply570 to arbitrary unrelated goals in synthetic contracts.

This drops bends<=16 and spacing from earlier path VIAs, so it is a superset
of production-valid paths. The real spacing exclusion is strictly abs(dx)<20
AND abs(dy)<20; equality20 on either axis is allowed. Static prefix clearance,
directed steps, complete goals and necessary transition/all-hole caps remain.
No path-parent/witness reconstruction or selected endpoint is produced.

Drain the bounded relaxed queue completely before a conclusive result. For a
goal reached within its own limit, report minimumRelaxedLengthWithinLimit.
Otherwise report NO_PATH_WITHIN_LIMIT (or DIRECT_ZERO_REJECTED), with null
minimum. Pruning does NOT compute an exact global minimum above the limit or
establish unbounded unreachability. Never report either of those stronger claims.

Result classes:

- EXCLUDED_BY_BOUNDED_RELAXATION: every complete goal has no relaxed path within
  its own necessary length/cap limit or is direct-zero. Any production-valid
  path is in this superset, so none can meet those necessary guards in this
  exact fixed graph. This is not whole-board/design impossibility.
- NOT_EXCLUDED_BY_RELAXATION: at least one goal has a relaxed minimum within
  its limit. Omitted bends/history may still reject all actual paths; feasibility,
  selection and adoption are unproven.
- INCOMPLETE: validated analysis stopped on a deadline/work/space/cleanup limit
  or unprocessed queue. Exit2, never exclusion or success.

Malformed/unsupported/source/hash/path inputs produce a separate REJECTED
error record and exit2 before analysis. Valid conclusive classes exit0. Never
emit FEASIBLE, SELECTED, ROUTE_ADOPTED, BOARD_IMPOSSIBLE or PLAYABLE.

## Honest bounds and reporting

One shared monotonic90s operation,640groups,5s/group; actual P07 owner steps0.
Keep at most32768 counted analysis operations/group. Count queue pushes/pops
(including stale pops), finalized states, four planar direction checks plus
one VIA eligibility check per finalized state, and valid-edge relaxation
attempts separately; sum these tokens as analysisOperations, not P07 steps.
No empty extra group at an exact quantum boundary. Record accepted edges,
successful relaxations, discovered labels and peak queue separately.

Theoretical state bound=2*cells*(effectiveCap+1), actual1477020. Independent
diagnostic safety caps: at most1000000 discovered labels and250000 queued
entries. A cap hit is INCOMPLETE; never raise caps to obtain an answer. These
container caps are not a process-RSS claim. All traversal is bounded and readonly.

Include spec/read/import/accepted-reader validation, decoding, analysis, input
recheck and report preparation in the same90s clock and640groups. Existing
reader validation is an atomic5s-checked phase: its internal work count is
NOT_EXPORTED, not fabricated as zero analysis work. Record its actual elapsed.
Decode at most32768 mask entries per named group. Search groups use the stated
token quantum. Each phase/group deadline is checked; slow atomic validation
fails rather than being exempted or given a larger unit limit.

Serialize/encode the bounded result once inside a charged preparation group;
stream only immutable prepared bytes afterward. A small bounded scalar footer
may record final group/operation/report timings after closing the group, as in
the accepted capture contract. No second full serialization, clock reset or
uncharged traversal. Record operation versus selected-host/cleanup timing
separately; unexported splits remain NOT_EXPORTED. Nonzero exit, timeout,
missing termination or failed cleanup is never PASS.

Closed result schema p07-load12-cost240-adrive-relaxed-length-result-v1 binds
source/spec/reader/receipt/graph hashes, dimensions/seeds/complete goals/caps,
per-goal bounded outcomes, counters/group ledger and final timing footer.
Qualification flags say feasibilityProven=false, routeAccepted=false,
routeAdoption=false, geometryPublished=false, layoutQualification=false,
playable100=false. This is no Player answer-key or new physical qualification.

## Required validation and publication

New tests must use hand-calculated literals plus a separate tiny Bellman-Ford
expanded-state oracle, with at least1024 deterministic directed two-face tiny
comparisons. Compare kept finalized distances/within-goal minima and classification;
do not demand traversal of deliberately pruned states. Include directed/no-reverse
edges, zero-cost flips, transition0/4, all-hole boundaries, partial seed faces,
distinct goal limits, direct-zero, exact boundary/smallest reachable over-limit, and a relaxed
path violating omitted bend/spacing rules that stays merely NOT_EXCLUDED.
Assert planar-length parity independently: each four-neighbor move flips cell
parity and a zero-length layer flip does not. Hence length/pitch and direct/pitch
have equal parity; 3*direct has that parity too. One pitch over the threshold is
unreachable under this contract; the smallest reachable over-limit case is two
pitches over. Do not invent a limit override to manufacture the impossible case.
Test source/tamper/malformed errors, deadline/quantum/space failures and no extra
empty fence group. Keep graph inputs unchanged. No blindly blessed golden.

Root owns all execution. Early real selected PS5/Python3.13 positive and negative
canaries must prove the actual adapter, then final new units and actual capture
analysis. Negative controls use task-owned spec/receipt copies and preserve
accepted raw data. A real junction negative is required for any added path-loader
boundary. Record executable hashes, PID/start, exact commands, raw receipts,
exit/termination and cleanup; no mock or injected controller certifies the host.

Audit all consumed inputs before/after. Old44 Python, production/native/SDK/build
and immutable capture inputs may reuse accepted evidence only after unchanged-
input audit; this Python-only milestone requires no fresh JDK/GWT or population
run if that boundary remains unchanged. Any source change invalidates only the
consumed proof it affects. One expensive matrix per worktree at a time.

Root reviews integrated diff/evidence and updates architecture/roadmap/report,
then normally commits/pushes, verifies exact remote and sends ONE new milestone
Gmail; never repeat the accepted capture mail. Visible Player input is N/A.
Full30 is owner-excluded, HARD/PSYCHOTIC unavailable/unrun, protected content
unread/unrun here; historical unread remains UNCERTIFIED. Only3 approved normal
MEDIUM roots remain in regression scope; this analysis introduces no population
root. No independent two-face access proof, playable100=0/beta held.
