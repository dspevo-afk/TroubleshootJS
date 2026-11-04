# Next: actual A_DRIVE copied-prefix graph diagnostic

**Status:** NEXT UNSTARTED. Freeze the next candidate and validation scope before code changes. No production treatment, routing-policy change, budget increase, or oracle change is proposed here. Begin only after the current A_DRIVE guard milestone is validated, accepted, and published. Then continue under the standing roadmap authorization.

## Question and evidence basis

Use the exact copied prefix and actual A_DRIVE target from the accepted guard witness, with root seed `9007199254740993`, placement seed `-6587958367974952701`, routing seed `8445301674744508279`, request/index 0, placement attempt 4, pass 1. The copied route uses VIA cost 240 and cap 48 after the LOAD12 source branch at cost 60. The target is A_DRIVE at net-vector position 24 / next-fetch 25, branch ordinal 142, ordering 2. The source's earlier LOAD12 branch ordinal 73 is provenance only.

At the pre-guard checkpoint the measured path has 82 nodes, length 770, direct distance 190, 5 bends, and 4 transitions. The current quality rule rejects when direct is zero, length exceeds three times direct, or bends exceed 16. Here 770 exceeds 570; bends and transitions are within their recorded limits. The witness records no publication for the rejected A_DRIVE branch. The copied prefix at that checkpoint remains 37 holes and 177 traces across the guard call; the original source snapshot remains 17 holes and 88 traces.

The next bounded diagnostic question is whether the actual copied copper/obstacle graph, under the current routing owners' existing constraints, contains a path satisfying the existing guard and route constraints from the actual start to the full production-legal goal set. The observed QA.B endpoint is one measured goal, not an assumption that production has no other valid tree/barrel or terminal goals. The present witness does not answer that question. It does not show that a shorter path exists or that the board is impossible to route.

## Proposed evidence sequence

1. **Freeze the exact input and owner lineage.** Reuse the same seeds, selected placement, source/copy lineage, A_DRIVE target and pre-guard prefix. Capture the graph from the live copied owner through an existing owner/adapter. Bind it to actual net, start/root, the complete legal goal set, layout, source/copy snapshots and prefix digests. Preserve per-layer goal flags and each terminal, prior copper-tree or barrel identity that the production owner actually permits. Treat runtime owner/search/layout identity hashes as within-run lineage, not durable graph IDs; preserve the stable component, terminal, net, pad, copper and probe identities supplied by the authoritative graph.

2. **Resolve constraint semantics before any implementation.** Read the current routing owners/adapters that define obstacles, copper occupancy, clearances, legal layer transitions, endpoint access, via placement/cost, and same-net traversal. Record their exact current meanings. Do not infer electrical connectivity from rendered geometry, substitute a synthetic board, invent a capture API, or silently add constraints. Audit the existing graph-artifact caps first; the 4,096-node and 1 MiB bounds belong to the current path witness and must not be assumed to authorize a larger graph snapshot.

3. **Run a read-only, bounded feasibility diagnostic.** Use the actual frozen graph and unchanged production constraints to search or verify candidate paths. Any reported candidate must independently recompute its endpoint binding, length, bends, layer transitions, and guard predicates from the frozen graph. For the observed QA.B goal, direct distance is190 and the length ceiling is570. Other legal goals need their own Manhattan direct distance and unchanged3x ceiling; do not impose570 on the entire goal set. Bends remain at most16 and the existing transition cap is4. A fixed-QA.B-only result must be labeled as such and cannot exclude alternatives permitted by production. Preserve VIA cost240/cap48. Derive counted existing VIAs and remaining allowance from the actual owner; the reported37-hole count alone does not define VIA-budget semantics. Preserve current request, branch, grid, ordering, operation, unit, owner-step, capture and payload limits. Charge graph capture, search, recomputation, and serialization separately and within existing budgets. Do not change thresholds, costs, geometry, continuation semantics, scoring, or owner state.

4. **Report only what the bounded evidence establishes.**
   - A verified qualifying path is a feasibility witness for this exact copied prefix and constraint model; it is not permission to publish or adopt it.
   - If exploration completes over the captured modeled graph with no qualifying candidate, report only that no candidate was found in that bounded modeled domain. Do not call the physical board impossible or claim no valid design exists.
   - If a cap, unsupported rule, incomplete graph capture, stale owner, or time budget prevents a complete check, report INCOMPLETE/BLOCKED with the exact boundary. Do not raise a limit to force a result.

5. **Check non-mutation and cleanup.** Preserve the paused source and the copied prefix before/after the diagnostic. Confirm no rejected route geometry was published, no graph/lease/listener state leaked, and cleanup is separately accounted. Keep finalizer, clearance/connectivity, physical-access, electrical/solver, repair, ordinary comparison, route adoption, and player qualification as separate evidence scopes.

## Unchanged limits and dependencies

Keep the existing 90-second operation, 640 controller units, 5-second per unit, and 32,768 actual owner steps per unit; request 1,000,000, branch 100,000, grid 250,000, orderings 3, VIA 48, transitions 4, access 700,000, cleanup 120 seconds, and shutdown 15 seconds. Preserve the current 4,096-node path and 1 MiB payload bounds where their contract applies. No budget or oracle changes are authorized. If current graph capture cannot be represented under existing caps, stop and record that prerequisite for root rather than increasing a limit or fabricating an adapter.

The frozen PLAN/WIRE define the current witness contract; they do not supply the later A_DRIVE branch metrics in advance. Final-source native and reader evidence must be independently reviewed. Old focused 8/native 33/original 22, the six pre-existing diagnostic/consumer pairs, old Python 170, and normal-three remain separate gates; do not treat the current focused receipts as a complete matrix. Only the three approved normal MEDIUM roots and existing envelope apply; full30 is excluded. Protected HARD data remains unread/unrun in the new scope; historical unread remains UNCERTIFIED. HARD/PSYCHOTIC are unavailable/unrun, independent two-face access is unavailable, playable100 remains 0, and beta is held.


## Recorded predecessor boundary

The accepted predecessor must be identified by its publication receipt before
this scope starts. Its measured source manifest is6260778c6b16670038e22a8e8ae4ad329d2a2e6d2216ede96bda820edd6d6458,
layout26/dependency28; the guard witness source is00cb2c4e46418856c03f68b5e197a8cace4590f60abefc23d002bc706e153aaa.
The actual start isRPDA.1 at(3790,2560), observed terminalQA.B at(3830,2410),
grid478x309/origin(30,30)/pitch10. Path-row SHA is
a2d33696158882cf3d6bd4c949c021c4456a00b325b80fe3815b09df2db15c10;
the unchanged37-hole/177-trace preguard geometry SHA is
a1e6d1d802ed5806b80c918745953cdc25ec4f0891058ffec1e8ba833d11a257.
These identify the measured fixture and its observed path, not a complete graph
or an all-goal feasibility certificate. Within-run object identities must be
remeasured rather than copied from the predecessor's process.

Use the current passing guard mode and frozen independent consumer as parity
oracles. A new graph mode needs its own exact transport and source binding;
preserve old receipts and reject incomplete or unsupported graph capture. Do
not project a synthetic old receipt or adopt a different route in this scope.

Predecessor publication is now verified: implementation `9dc822d4c15df3ee21c0f06c12339fb145b735f9`,
normal push to `origin/codex/q100-physical-feasibility`, completion Gmail `1a1a7ed052322950`
sent once; see [publication.md](publication.md). This next scope remains unstarted
at that receipt and is eligible under the standing roadmap authorization.
