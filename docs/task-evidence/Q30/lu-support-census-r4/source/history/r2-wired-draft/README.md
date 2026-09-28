# Q30 LU support census r2 wired draft

This is a source-only private probe draft. It measures structural facts at
the existing nonlinear LU boundary so the root task can decide whether an
unchanged-order block has enough support to justify later work. It does not
change LU arithmetic, pivot order, tie handling, finite checks, settling,
hypotheses, budgets, or solver callbacks.

The draft owns one nullable `Q30LuSupportCensus` per private cold or warm
verifier run. The verifier installs it only when all existing debug and Q30
scope checks pass and the client URL contains
`tsjQ30SupportCensus=true`. No normal-player query, field, budget, graph
retention, or static active flag is added. `CirSim` clears only the exact
owner pointer, so a successor graph or successor census cannot be cleared by
an older run.

`CirSim` calls `beginFactor` after `doStep` and immediately before the
existing `lu_factorNonlinearOwned` call. Every eligible nonlinear factor call
advances a primitive bounded `int` ordinal. The default private cadence is
4096 calls; the report reconstructs the exact eligible total from full
selection batches and the tail at freeze. Long aggregates change only at a
selected boundary or during freeze. Native contract fixtures use an explicit
cadence of 8 so they can exercise selected and unselected paths independently.

Each selected sample performs one bounded pre-factor scan of the current and
original matrices, records row/column bipartite components, support additions
and removals, cross-original edges, nonfinite entries, and a fixed operation
count. The scan reads only the active `n`-by-`n` prefix, so a full-size
`origMatrix` allocation tail from matrix simplification is ignored and a
short prefix is invalid. It also replays the actual successful LU `ipvt` array
on copied row labels after the original factor returns. The post-factor sentinel scan only
marks a conservative risk when a diagonal equals `1e-18`; it never claims that
the fallback branch was observed. No clock, per-pivot hook, second LU, or
algorithm feedback is present. A malformed diagnostic operation is reported
as invalid while the physical LU path continues; an original LU exception is
finished for accounting and rethrown with its original identity.

The value-difference counter uses GWT 2.7's canonical `doubleToLongBits`
semantics; distinct NaN payloads are intentionally not distinguished.

`stableOriginalComponents` requires the current and original row/column
partitions to be identical in both directions. Support drift therefore has a
separate explicit no-go fact even when a supplied `ipvt` replay has a complete
component-label trace. `exact` means only that the bounded component-label
candidate replay satisfied its declared structural conditions. It is not an
optimization proof and does not establish numeric pivot eligibility, fill,
writer coverage, or a safe block restriction. The private wiring does not
declare a zero-pivot fallback observation hook, so its reports deliberately
retain `exact=false` until root adds and validates an allowed observation
source. Failed factors retain their initial-order counts with
`initialOrderOnly=true`; those values are unknown after row swaps and are not
upper bounds. `completeCapture` is separate from `valid` and is
false for failed, dropped, malformed, incomplete, or diagnostic-error
samples and active/lost ownership. scopeComplete explicitly records owned-job
quiescence. These are execution-capture facts, not optimization evidence;
unknown fallback and support drift remain separate mandatory decision inputs.

The GWT verifier serializes frozen value state only. Long counters are JSON
strings to avoid JavaScript number rounding. The native contract never calls
the GWT JSON serializer. The independent native oracle covers an asymmetric
five-by-five graph, stable exact replay, support drift, failed and malformed
scopes, duplicate captures, sentinel risk, unknown fallback observation,
default 4096 cadence, and cold/warm state isolation. Its `ipvt` arrays are
synthetic graph fixtures; a real LU selected-failure/`ipvt` canary remains a
root registration and gate obligation because this draft does not run builds,
native tests, or browser checks.

## Files and application

`src/com/lushprojects/circuitjs1/client/Q30LuSupportCensus.java` and
`tests/contracts/Q30LuSupportCensusContractTest.java` are new source/test
files. Apply `overlays/CirSim.java.patch` and
`overlays/Q30CoordinatorQualificationVerifier.java.patch` to the unchanged
fusionR2 candidate. The two `*.expected.sha256` files are hashes of the full
resulting source files after applying those patches, not hashes of the patch
text; they preserve each candidate source file's existing line-ending style.
`exact-diff.patch.gz` is the non-circular gzip diff for these two
overlays plus the new collector, native contract, and this README; the source
manifest is excluded from that diff and therefore can safely record the other
artifact hashes without a circular digest.

No sourcebase file was edited and no build, test, browser, host, timing, stage,
commit, or push was run for this draft.

Root finalization preserves the worker handoff. Successful selected samples
retain a defensive ipvtPrefix and ipvtTraceComplete; failed factors expose an
empty incomplete trace. An independent reader can recount pivot domains.
Lower domains count i > k before the current row swap; upper domains use
the selected row label and later columns. Pivot and lower domains overlap.
These are label-domain counts, not scaling counts, nonzero work, or additive
LU timings. Stable component partitions do not imply stable support; both
support-added/removed counters remain mandatory optimization decision inputs.
Per-sample unsaturated counts are bounded by n <= 512: cell reads <= 5*n*n,
edges <= n*n, and candidate domains <= n*(n+1)/2. The identity-clear method
is a reference comparison and assignment with no throwing operation; no
hypothetical injected clear failure is certified.
