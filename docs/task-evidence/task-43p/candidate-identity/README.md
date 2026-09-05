# Task 43P committed-candidate verification handoff

This correction follows published commit
`164704ccbbcbea14532b21a1d8c8bc6b4f380252` on
`codex/task43p-final-recovery`. It changes verifier candidate admission and its
regressions. C1/C2, Java, electrical behavior, geometry and the Task 43P acceptance
oracles are unchanged. Owner approval has not occurred; Task 44 is unstarted.

The [precommit validation](precommit-validation.json) and
[dependency audit](dependency-audit.json) contain sanitized projections and
hashes of the retained raw artifacts. Independent Luna review passed without
blockers. The reviewed source/verifier digest is
`1a9c9109e38aadfa7593684eeb36d26b94f148a1926afd594487b57ffa6c6753`.

The real `-MutationPreflight` entry point rejected that clean published HEAD
with exit `2` before testing any anchors, because actual HEAD was compared with
the historical `8bf442416a2fa2c0c9d654d1efa14e754c2b7ee7` baseline. Browser capture
and forced-proof consumers repeated the same mistake.

`Task43PCandidateIdentity.ps1` now freezes actual HEAD. All three entry points
validate optional `-ExpectedCandidateSha` instead of replacing it; Gate B and
the source-experiment parent propagate their candidate to child checks. The
historical `baselineSha` remains metadata and `candidateSha` records the tested
commit. The legacy `diagnosticBaselineHead` field carries that candidate in the
forced-proof protocol. Source, script and compiled-web fingerprints, repository
before/after checks, exact cleanup, the 500 ms proof limit, deadlines and exits
retain their existing meaning. Browser state capture also guards the full
invocation and final cleanup boundary.

Standalone mutation preflight needs current source and Git identity, not historic
HEAD equality or full history. Detached shallow CI checkouts use their actual
checked-out commit. Ancestry is a separate audited statement: the local
`git merge-base --is-ancestor <historical-baseline> HEAD` returned `0`; no ancestry
claim is inferred for a shallow checkout.

| Gate | Path, oracle and inputs | Result / evidence boundary |
| --- | --- | --- |
| Published-HEAD reproduction | Actual source-experiment CLI on clean `164704c` | Exit `2`, obsolete-HEAD rejection, repository unchanged. |
| Candidate regressions | Actual committed fixture preflights, a subsequent commit, actual depth-one detached Git clone, explicit identity/provenance negatives; production state readers with real source/status/HEAD mutations | PASS; all eleven named anchors and four restoration/rejection canaries retained. Fixtures are disposable Git repositories outside the working checkout. |
| Focused proof checks | Source `-ContractProbe`; browser `-GateBForcedNegativeProofProbe`; original complete proof and exact negative identities | PASS; stale/missing/foreign candidate, digest, diagnostic and cleanup claims reject. These are contract tests, not live browser proof. |
| Complete Gate B | Default `verify-gate-b.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07`; actual JDK8, deterministic process/listener and source contracts, including mutation preflight | Precommit exit `0`. Final clean committed-HEAD result belongs to the separate postcommit receipt. |
| Affected browser binding | Real `-Task43PForcedNegative` first, then `-Task43P -Task43PRuntime`; startup settle 30000 ms, existing timeout 90 s, exact cleanup | Required on the clean committed candidate; recorded in the postcommit receipt. Exit `2` is never a pass. |
| Reused build, physical and compiled proofs | Unchanged Java/web, build/preview/isolation, detectors, mutation definitions, source/compiled fingerprints and toolchain | Explicit dependency audit; original results and identities preserved, not recertified as fresh candidate-binding runs. |
| Publication / CI | Normal push on the existing recovery branch, exact remote SHA match, existing GitHub workflow attempt | Exact SHA, clean-HEAD results, CI outcome or limitation and notification result belong to the postcommit receipt. |

The regression harness's first attempt used in-memory extraction of a state
reader and lost its script root. That invocation failed. File-backed extraction
preserves the actual reader's path semantics, and the subsequent complete
regression passed. No failed invocation is counted as qualification.

The retained [C1/C2 acceptance packet](../c1c2/README.md) is historical evidence.
Its production build passed all five permutations; eighteen physical triads
qualified while the six legacy aggregate exits remained `2`; eleven compiled
falsifiers qualified with their individual child exits `1`. The dependency audit
preserves those exact limits, original HEAD/diff identities and hashes. It does
not replace the fresh forced-negative and dedicated A–I paths above.

The clean-HEAD check runs after committing the correction. Its receipt is saved
outside the checkout and linked in the final handoff, together with the exact
published SHA and CI outcome. Recording that receipt does not modify the commit
it qualifies. This tracked packet identifies source by its enclosing commit and
records precommit evidence separately; it does not claim that a dirty-parent
pass proves a later committed checkout.
