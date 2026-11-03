# Adapter design: typed LOAD12 graph target

Source-only design for the adapter-owned `Rb100PhysicalDiagnostics.java` change.
Baseline: `b033826e108773ef1c0fc723efd9efc120d539cd`, branch
`codex/q100-load12-graph-20261103`. The working tree already contains other
workers' edits; this note is the only file added by this adapter task. No Java
implementation or runtime gate is authorized before root freezes and ACKs the
shared WIRE/API.

## Adapter shape

Keep one shared `QualityScalarProbe` source driver and select one graph target
per operation with a closed `D_SENSE` / `LOAD12` value. Preserve the existing
no-argument `runQualityZeroViaGraphContracts()` entry as the D target, and
provide a target-taking entry for the root-owned LOAD12 native wrapper. Do not
run both graph cursors in one operation. The existing probe is at 602/640 outer
work units, leaving 38; each cursor call must still use a fresh charged
`valueUnit` (at most 32768 work), with all existing 90 s / 640 / 5 s / 120 s
limits intact.

The D target continues to begin at the retained D_SENSE branch's stage-1
Publisher boundary in `inspectActualTargetAfterSlice` and keeps the existing
same-face path, source, prefix-allocation, rejection, NULL/PROBE parity and
cleanup oracles. The common D scalar/path and prefix-allocation records remain
required for both target entries. Decouple D prefix allocation from D graph
materialization: `beginPrefixAllocation` belongs to the D branch observation;
it must not be skipped just because the selected graph target is LOAD12.

For LOAD12, start the graph immediately after `captureCompletedLoad12Pre()`
has retained the completed immutable PreResult, while its Publisher is still
held at stage 1 / `qualityIndex == reversed.size`, and before the next ordinary
Publisher step. The source is the exact retained Search/BranchSearch/Publisher,
the pre-publication layout and its 17-hole prefix, actual two-face routers,
actual branch goal union, and the path witness exposed by PreResult. Bind the
same run to the captured seed snapshot, pass 1, LOAD12 net position 3 / next
fetch 4, branch ordinal 73, and the approved root / placement / routing seed
strings. Do not use the later D_SENSE path or the post-publication 21-hole
layout to populate this graph.

The manual-unit priority should be: pending LOAD12 pre capture; selected graph
cursor; pending LOAD12 post capture; D prefix-allocation cursor; then the
ordinary scalar single-step group. This inserts LOAD12 graph work between the
completed pre capture and resumption of Publisher without changing P07 source
order. `advanceManualGroup` must verify the same route session, owner, Search,
BranchSearch, Publisher, branch ordinal, net/vector identity and values,
current-net position, face routers, goal identity and values, layout, template
hole count 0, prefix hole IDs/raw fingerprints, and seed snapshot before and
after each graph advance. Keep the existing target accounting, source-path,
same-owner rejection, NULL/PROBE chronology, route-adoption/layout flags,
terminal cleanup, and parity checks. No reflected writes or branch resumption
are permitted while the cursor is live.

At cursor completion, copy the immutable result and the exact target binding
needed by the closed event into a target-tagged receipt; verify repeated result
reads, source stability, and completion debit there. Then clear the graph
cursor, mutable Context/source binding, and any adapter-owned duplicate path or
goal arrays before Publisher resumes. Preserve `PreResult` and its existing
LOAD12 capture context only as required by the normal post-capture lifecycle.
The later post cursor must complete and release its own live capture sources;
the D_SENSE branch must still be observed and rejected/accounted on the same
Search before NULL/PROBE terminal cleanup. A completed graph receipt, rather
than a live cursor, should satisfy terminal and event gates.

## Helper API and proof boundaries to freeze in WIRE

The current graph helper (`P07ZeroViaGraphDiagnostics.java`) accepts physical
`[x,y,layer]` rows but caps them at 66 and rejects any face change. Freeze a
read-only witness accessor over the actual immutable path nodes in
`P07AcceptedBranchCaptureDiagnostics.PreResult`; do not reflect/copy the
mutable search queue to reconstruct another path. The D target may continue to
adapt its existing immutable `QualityPathSnapshot`. The shared helper must
accept at most 4096 witness nodes and at most four coincident-cell face flips.
Check each planar step against its own source face's ordinary mask. A flip must
be at the same physical cell, switch layers, and match the authoritative
captured path; it is not an ordinary edge and does not add VIA edges or copper.

Derive `initialLayerMask` from the retained seed-node identities/layers, not
from the FULLER policy constant. Preserve both face seeds for this case, while
keeping the API general enough to reject an inconsistent seed/path binding.
Keep the actual `BranchSearch.goal` as authority and compare it to the
incrementally copied goal snapshot. Bind the pre-layout and hole inventory by
identity plus hole IDs and raw fingerprints; indices are provenance only.

The current helper's `Context`/`Cursor` setup performs potentially broad
fingerprinting, face-effect snapshots, and path copying synchronously, and
`Result.bodyJson()` / result construction materializes full-grid strings after
the cursor. The frozen helper contract must bring those operations under
bounded, resumable charged work too: constructor work is constant-size setup;
cursor stages debit every copied, scanned, hashed, compared, and serialized
entry in chunks no larger than 32768. Final event assembly may append bounded
header text to the already-materialized immutable body, but must not rescan or
rehash the grid outside the charge. Keep the caps at 250000 cells, 48 vias,
4 transitions, 1M routing requests, 100K branches, 250K grid work, and 3 source
orders; do not infer actual masks or outputs from baseline hashes.

The existing graph event and summary are hardcoded to D_SENSE fields. Change
them only after WIRE ACK so each is explicitly target-bound and consumes the
immutable receipt. D prefix-allocation checks remain independently tied to
their D source. The current equality between D graph prefix geometry SHA and D
allocation prefix SHA is valid only for a D-target operation; for LOAD12 compare
graph geometry to the LOAD12 pre-prefix snapshot and do not compare it to later
D allocation geometry. Keep the common record count/order and exact closed
keys in the acknowledged WIRE. Both events continue to say feasibility is
`UNKNOWN`, with legal-route proof, route adoption and layout qualification
false.

## Risks / acceptance boundary

- LOAD12's real path has 222 nodes and four face flips. Any path truncation,
  same-face assumption, flip turned into a VIA, or mask lookup on the wrong face
  invalidates the graph.
- The LOAD12 graph must use the pre-Publisher 17-hole/88-trace state and finish
  before normal publication. A stale cursor must never cross into the post
  capture or successor Search.
- Only the fixed-prefix ordinary-edge lower bound is measured. It does not
  establish BranchSearch acceptance, turn/bend limits, clearance, access,
  finalizer behavior, electrical behavior, route adoption, avoidability, or
  layout qualification. Do not upgrade `UNKNOWN` or emit those claims.
- The current source has one D-specific `ZeroViaGraphBinding`; target selection,
  event construction, prefix-SHA checks, manual scheduling and terminal gates
  all need to agree on the same target. A partially generalized binding could
  silently make a LOAD receipt describe D state.

Source-only review found the main adapter seams at
`QualityScalarProbe.nextManualUnitName` / `advanceManualGroup` (3138/3164),
`captureCompletedLoad12Pre` (3683), `beginZeroViaGraph` (3875),
`advanceZeroViaGraph` / completion verification (4240-4290),
`inspectActualTargetAfterSlice` (4312), terminal/path gates (4400-4455),
graph event and summary (4528-4581), D allocation record (4596), and
`ZeroViaGraphBinding` (4803). The helper seams are its path cap/validation
(10, 27-100), cursor setup/work (111-297), planar witness validation (385),
result construction/body serialization (411-502), plus PreResult/seed accessors
in `P07AcceptedBranchCaptureDiagnostics.java` (32-100, 345-395).

No tests, builds, actors, gates, or runtime captures were run for this design.
Next action: root/helper owners freeze the exact API, event, and summary in
WIRE; adapter Java edits begin only after root ACKs that version.
