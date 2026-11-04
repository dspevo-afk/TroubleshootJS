# Actual LOAD12 cost240 at cap48: frozen initial scope

Status: accepted for the finite cost240 diagnostic in [README.md](README.md) and
[qualification.json](qualification.json). The frozen initial scope below remains
the experiment's acceptance contract; no default adoption or whole-layout
qualification follows. Start from the published cap17 diagnostic
`af37323ebe5fd8698aed9de2ef5d588887df9d8d` and its documentation receipt.
Source parent f0c2004fabbea74b9367a0bf4fe5332fb49cfa6c; branch codex/q100-load12-cost240-20261104. Preserve the existing worktrees, failures, raw source snapshots and data limits.

## Question and corrected objective

Does the real initial LOAD12/73 frontier find and normally publish a useful
alternative when only its detached VIA cost is240 and its allowance stays48?
Keep the original source at cost60/cap48 and resume it normally afterward.

The objective includes more than physical path length and VIA count. The current
Search charges GRID10 per planar step,2 per secondary-face step,35 per turn,
and a secondary-face seed charge2. Root checked every captured node-cost
recurrence against these source rules for the two accepted witnesses:

| Witness | Actual cost at60 | Cost at100 | Cost at240 |
| --- | --- | --- | --- |
| Original four-transition path | 2170+106secondary+70turn+240VIA=2586 | 2746 | 3306 |
| Detached zero-transition path | 2510+502secondary+175turn+2seed=3189 | 3189 | 3189 |

These two fixed witnesses cross at210.75;211 is the first integer reversal.
The earlier length-only85 comparison omitted real charges and is not a valid
treatment basis. Cost240 is a diagnostic hypothesis,117 above the zero-transition
witness when evaluating the original route. It does not predict which route
Search will find or whether it fits the existing bounds.

## Owned change and dependencies

Policy.FULLER_TWO_LAYER fixes cost60/cap48/transitions4/secondary2. Session has
an instance cap but no instance cost. Add an immutable effective VIA cost with
the normal default taken from the immutable policy. An explicit diagnostic copy
must configure only the copied Session/Search and pass that same effective cost
to both Search transitions and relaxed-distance-bound VIA edges. Keep seed
charges, turns, secondary surcharges and enum values unchanged. Do not mutate an
enum through reflection or install a seed/net-specific production rule.

Admit the override only at the captured before-first-work frontier: no prior
expansion, exactly the original queued seeds, no accumulated old-cost search
states or stale bound. Validate all parameters/state before live mutation.
Retain source cap/cost/frontier/geometry identity and ordinary resumption proof.
The initial seed g-costs and turn heuristic do not depend on VIA cost; preserve
their identities/order. Later search priority may change because costs change.
Use normal Search/Publisher; never inject the already known path or goal bits.

The explicit Search override must cost at least
`max(source effective VIA cost, TURN_COST)` and at most240. This keeps the
queued turn-aware heuristic admissible without rewriting the copied frontier.
For the normal FULLER source this admits60..240. In an open2x2 counterexample,
the diagonal goal has initial heuristic55, but a cost1 face reset permits a
23-cost path (10 planar +1 VIA +12 secondary planar). Admitting that cheaper
Search regime would invalidate the existing heuristic. A standalone relaxed
Dijkstra bound may still use nonnegative0/1 prices as a mathematical oracle;
those are not operational Search overrides in this scope.

Root owns the integrated scope, evidence, runtime and publication. Assign one
writer per production owner, native adapter, independent consumer and docs.
Source-only leaves preserve others' work and do not spawn, run, stage or publish.
An owner prerequisite and its actual bounded replay are necessary substeps of
this one scope; do not present entry scalars as an actual route result.

## Acceptance and gates

First prove the actual selected JDK8/PS5 path with focused positive and negative
canaries. Literal fixtures need independent weighted-cost expectations, default
parity, original/copied isolation, bound-cost propagation, invalid/advanced
frontier rejection and lifecycle cleanup. Reuse independent existing oracles.
Then run the exact source selector from the accepted cap17 packet, keeping both
source/clone allowance48 and changing only detached cost to240. The event and
closed reader must bind the treatment explicitly and recompute actual node-cost
recurrences, not trust reported totals. Any successful route also needs full
captured-mask/goal, physical publication and source preservation/resumption proof.
Keep terminal search, budget, quality, timeout, incomplete work and cleanup
failures distinct; a failed case is not a qualified route or a reason to raise
limits. Preserve failures and do not publish an unresolved blocker as success.

Production Java changes require actual final-source Player and Developer builds
through scripts/build.ps1 and applicable native, compiled lifecycle/privacy and
three-root normal regressions. Complete the affected contract gates and root
diff/staged checks. One expensive matrix at a time; record ownership and wait
for actual exit/termination before its successor. Default adoption is a separate
decision requiring the appropriate layout/model epoch and whole-route/finalizer/
clearance/connectivity/access/electrical/runtime proof.

All90s/640units/5s/120scleanup/32768quantum/request1M/branch100K/grid250K/order3
bounds remain. No production-default cost change is authorized by this plan.
The standing roadmap authorizes the private experiment and necessary fixes.
Only the three approved development roots may be used for normal regressions:
`-2230691771308388107`, `6702341860553104274`, `-8360266000248569150`;
full30 remains owner-excluded. HARD/PSYCHOTIC remain unavailable and unrun.
Protected HARD stays unread/unrun in this scope; historical unread status remains
UNCERTIFIED. Playable100 remains0 and beta remains held.
