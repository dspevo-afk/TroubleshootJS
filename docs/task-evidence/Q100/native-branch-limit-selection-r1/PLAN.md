# Branch-limit selected-node provenance prerequisite

Status: FROZEN_FOR_NATIVE_ONLY_COMPONENT_IMPLEMENTATION. Continue the standing
roadmap after accepted diagnostic66fd2d7c/publication receiptda46e92f. This scope
uses generic fixtures only. Additional authored original/control/Session=0.
The accepted D original and two arms, source/data/classes/failures stay immutable.

## Owner and output

Derive a native-only owner overlay from exact F PcbLayerRoutingPrototype.java
c382f3bc08f8ad65b3501dad57c927dd5c1f9f1c04914ac79edb772f4b4f4e3e.
Do not edit the production owner, D owner/Study, numerical solver or routing
policy. A checked generator produces only the derived owner and a source proof
in an owned empty OS-temp output. Public code is a generator/canary, not a new
Player feature. No GWT claim: production source remains unchanged. Native final
source compilation and actual selected host gates are required for the overlay.

## Exact opt-in contract

Add package-private nested interface BranchLimitSelectionDiagnostics extending
TerminalOnlyDiagnostics, with
onBranchLimitSelected(BranchSnapshot snapshot,BranchLimitSelection selection).
For this opt-in mode only, an actual BRANCH_SEARCH_LIMIT uses this one callback
instead of onTerminalBranch. Other terminal reasons keep onTerminalBranch.
All existing Diagnostics/TerminalOnlyDiagnostics/NULL dispatch remains unchanged.
BranchSnapshot and its constructor/fields remain unchanged.

BranchLimitSelection is an immutable ten-int scalar record:
branchOrdinal,cell,layer,direction,transitions,cost,storedHeuristic,serial,key,
storedPriority. The last field is exactly Java int cost+heuristic as used by
Node.compareTo, not a mathematical lower-bound certificate. Construct it only
for a nonnull selected Node at the branch-cap path and the new marker mode.
Do not expose/retain Node, previous, queue, index, Search, Session or layout.

The existing advanceOne order is authoritative: poll; reject stale best-identity;
observer check as before; total-cap check; local-cap check; optional stronger
bound refresh; expansion. Pass the selected current local into a private
emitTerminal overload only at local cap. The ordinary overload passes null.
This is the fresh queue head selected at the existing identity check, already
removed from the heap, not a node still pending there. A completed bound can
coexist with an older stored h because cap checking precedes refresh. Preserve
that h and comparator tuple f/h/key/serial exactly. No queue scan, repoll, path
copy, heuristic recomputation, state insertion or extra search advance.

Session owns callback dispatch and uses its EXISTING RuntimeException/Error
abort/latch/clear behavior; reuse the same try/catch owner, not a second abort
implementation. Snapshot and selection are one synchronous callback pair, bound
to existing pass/net/endpoints/grid/reason/counters/flags. branchOrdinal supplies
the additional local branch identity. Detached copies preserve the mode with a
static no-op new marker, never the caller. No selection field is retained by
the live owner. Caller remains passive and exclusively owns detached DTOs.

Existing owner-operation accounting remains one Search.advanceOne credit, not a
primitive instruction bound. The opt-in work adds one fixed-size scalar object
and dispatch inside that operation's measured time. No new D wrapper/Meter/wire
integration is accepted: any future consumer must explicitly charge its ten
scalar copies/validation/serialization exactly once in its existing shared
budget, and separately qualify that integration. No new authored run follows.

## Generic fixture and independent gates

Use actual P06FactoryLinkFixtures.Fixture and real Session/NodeQueue/NodeIndex.
Boundary frames may reflectively inject expansion counters and a small known
queue/index solely to reach the unchanged cap. Label these injected frames,
never real routed results or a naturally exhausted100000-step branch.

Required canary coverage: independent f/h/key/serial tie expectations; stale
queue record rejection before the selected identity-live node; exact copied ten
fields, same terminal context and raw post-pop queue/index membership; unchanged
expansion counters; total-cap precedence; empty-queue/no-path nonselection;
completed real relaxed bound with an older injected stored h and no refresh at
the cap; immutable primitive-only DTOs; RuntimeException and Error identity,
owner clearing/latching and fresh reuse; detached no-op mode/caller release;
actual default/old-terminal/new-marker small-route and blocked parity/source
placement preservation. Bound setup/driver loops and record real fixture counts.

Retain independent expectations. Do not invent a second queue/search solver or
claim route feasibility from a stored priority. Run the existing focused
terminal-only, branch-diagnostics and relaxed-distance native suites on the
derived source using an explicit javac list/classpath excluding the old owner.
The maintained runner has no overlay switch; do not silently run it unchanged.
Audit reused1092-source D dependency classes and tools unchanged, compile fresh
overlay/canary/test sources into an empty stage and measure actual class census.

Generator gates: final-source units plus actual selected Python positive and
wrong-parent/occupied/reparse controls, exclusive write/readback/closure. Reuse
the byte-pinned existing source-exporter's checked I/O helpers; document its
hash and API boundary. No duplicate general host framework or global settings.
One expensive matrix at a time. Actual processes use maintained RootCall with
canonical RequestPath and selected JDK8/Python-I-B; preserve failures/receipts.

## Limits and acceptance

All production/global/branch/copy/time budgets and deterministic routing remain
unchanged. Native compile/suite60s; authored90s/640groups/5s/32768units and wrapper
480s/reader180s/cleanup120s stay unchanged and are not renewed by this component.
Full30 EXCLUDED; HARD/PSYCHOTIC unread/unrun and unavailable; historical protected
unread UNCERTIFIED. Playable100=0/betaHELD. No new population/electrical/Player,
full1092-source-overlay, physical/access/runtime-functional or adoption proof.

Root reviews actual source/diff/gates. Publish only the validated bounded
prerequisite with sanitized proof, normal commit/push and one completion Gmail.
Next afterward: separately scoped consumer/witness integration with its own
budget and independent oracle; no cap treatment or authored retry inferred.
