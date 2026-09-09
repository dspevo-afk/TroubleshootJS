# A03 evidence packet

Status: COMPLETE — QUALIFIED; publication pending. Base: `363244c0483ec665feff3cb8f40fab09851c7b98`.
Branch: `codex/task43p-final-recovery`. Entry worktree was clean.
A02 is accepted; A04 and later milestones remain unstarted.

## Closed acceptance set

| Gate | Production path / verifier | Oracle and fixtures | Environment | Status |
| --- | --- | --- | --- | --- |
| Identity and manifest canaries | New A03 native contracts over real namespace, bus resolver and replay adapter | Earlier alias, repeated type, insertion/reorder, collisions, missing/unknown versions, exact long boundaries, unlike topology, stale action, poisoned incidental identity | JDK8 | PASS: 109 shared identity vectors and 29 bounded replay/saved-action assertions |
| JVM/GWT parity | Same production A03 vector corpus on JVM and compiled debug entry | Byte-exact complete vector and three bounded-manifest comparisons | JDK8 / GWT2.7 / owned browser | PASS: v6 compiled report exactly matches the 17,505-byte JVM vector receipt and all three JVM manifests |
| Existing native contracts | verify-block-contracts, verify-challenge-contracts, verify-assembly-contracts, verify-a02-contracts | Existing native assertions and independent reference oracles; no golden changes | JDK8 / Python3.13.14 | PASS block/challenge/assembly and all three A02 suites |
| Production build | scripts/build.ps1 -Target Compile -Style OBF | All five existing permutations from final source candidate | JDK8 / GWT2.7 | PASS: JDK 8u502/GWT 2.7 all five production permutations; source inventory remains exact |
| Electrical and replay regressions | Existing Task41/46/47/48/49 and A02 debug entries on production preview | Existing solver/admission/replay/repair/cleanup assertions; representative leaf/composed cases | Owned preview/browser | PASS: `runtime-all-final-recovery-v6.json`, Gate All, nine routes, cleanup true |
| Task48/49 compatibility | Full final reports compared with accepted A02 reports | Every report field unchanged, including recipes, signatures and geometry versions | Fresh accepted-source export plus final production reports | PASS: exact Task48 (755 assertions) and Task49 (1,053 assertions) report equality |
| Developer boundary | A03 forced failure and debug-disabled routes | Failure has no PASS receipt; debug-off has no A03 receipt | Production preview/browser | PASS: expected forced-failure marker and blank debug-off A03 report |
| Scale sanity | Small resistive and controlled plans, fixed seed corpus | Timed plan/manifest encode/decode/resolve; no runtime graph retention or repeated local whole-board encoding | JVM and compiled runtime | PASS: JVM timings recorded; compiled A03 replay/poison corpus passed |
| Independent review | Fresh Luna MAX read-only integrated review | Original A03 contract, actual diff, relevant call paths and focused independent checks | Final source candidate | PASS: final implementation and v6 provenance review; one explicit future-interpretation follow-up |
| Final reconciliation | Diff/source integrity, complete Gate B and normal publication sequence | Only intended changes; exact accepted SHA at authorized origin branch | Git | PASS: final source/provenance audit and complete Gate B; commit/push pending |

Long seeds include 0, 1, -1, both signed-long extrema and positive/negative
values beyond JavaScript's exact integer range. Numerical recipe choices use
exact binary64 bits where needed; floating electrical margins are recomputed,
not used as identity. A03 does not add a player flow, so visible-interaction
screenshots are not an acceptance substitute or a required new artifact.

## Read-only architecture gate
Luna MAX investigators `identity_investigation` and `replay_investigation`
independently mapped namespace, graph/physical ownership and replay versions.
Independent Luna MAX `identity_model_challenge` passed the proposed model before
implementation, including the following scope and consumption conditions.

| Identity | Owner | Durable use |
| --- | --- | --- |
| DSU/root representative | Temporary assembly join mechanics | Never a new durable key |
| Local declaration | FunctionalBlockDescriptor within BlockNamespace | Stable semantic local names |
| Role and repeated instance | Explicit role version and caller-supplied instance key | No allocation/index suffixes |
| Variant internals | Provider and descriptor topology/schema realization | Components, terminals, pads and internal nets distinguish unlike variants |
| Device bus | Explicit device-owned declaration | Propagated through aliases; independent of lexical/root selection |
| Board/rendered net | Existing plan alias projection and board/layout owners | Retained for accepted report compatibility; not a new saved-action key |
| Solver endpoint/node | Live binding and CircuitJS owners | Runtime-only |
| Physical inventory part | PhysicalBoardRuntime acquisition identity | Not promoted into a durable pristine realization key |

The concrete F9 path is `BoundedAssemblyPlan.resolveNets()` ->
`Union.join()` (lexical root) -> `netAliases/portNets` ->
`BoundedGeneratedBoardAssembler` BoardNet IDs and legacy signatures.
The new durable partition must agree electrically with that projection without
serializing its representative. Explicit external singleton buses are declared;
genuinely unjoined internal nets remain variant-local identities.

The reviewed manifest scope is complete capture/resolution for the existing
bounded generator versions 1/2/3. Leaf replay retains ChallengeDescriptor with
frozen layout 3/4 dispatch; no general durable leaf-bus or leaf-manifest adapter
is claimed. Model/package/routing choices use their actual versioned generator
or layout owner; unknown or changed pins/choices must reject before mutable
assembly. No legacy plan/recipe semantic signature enters durable identity.

## Current evidence and closure record

- [Native/build summary](native-build-summary.txt) and [exact 899-file source inventory](candidate-source-files.json), rechecked against this checkout.
- [Final candidate fingerprint](candidate-final-files.json): a self-excluding
  SHA256 inventory of every intended A03 working-tree path at publication
  freeze.
- [Runtime v6 receipt](runtime-all-final-recovery-v6.json): compiled Gate All PASS, nine routes, durable cleanup true; its source, script, web, execution, preview and verifier digests match the current candidate.
- [Closure reconciliation](closure-reconciliation.md): exact compiled/JVM parity, Task48/49 baseline equality, final Gate B and resource audit.
- [Final independent review](independent-final-review.md): fresh Luna MAX read-only PASS on the implementation and current runtime provenance.
- [Browser/cleanup blocker](browser-blocker.txt): historical pre-recovery failure evidence only. Its old legacy profile/claim/run records are preserved, not relabeled as cleaned.

`candidate-files.json` is a pre-v6 historical snapshot. The self-excluding
final candidate fingerprint, source inventory and current execution provenance
above identify the publication candidate. A03 is qualified for normal
publication. A04 remains unstarted.
