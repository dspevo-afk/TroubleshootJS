# A_DRIVE source contract prerequisite

Source-only inspection at parent96589b8dfaf17cdd7bc74deec4518ab36891c66a.
Production owner PcbLayerRoutingPrototype.java SHA
1acc6acf9a172952fabdd316ca732d4e86905f449b65dc51e7a17bf7c8e5848e;
native diagnostic SHA00cb2c4e46418856c03f68b5e197a8cace4590f60abefc23d002bc706e153aaa.
Two bounded leaf source explorers and root inspected these boundaries; they ran
no runtime capture, parser, tests, build or feasibility search. This is no graph
or physical qualification.

## Goal and move authority

PcbLayerRoutingPrototype.Search.goals (lines2019-2034) includes the primary and
transition-enabled root face, every joinable same-net Router-owned cell on each
enabled face, and reached PTH terminal cells with copper on that face.
reachedPthTerminals (1999-2017) excludes root, checks membership and sorts pad IDs.
terminalAt (2145-2153) returns a root/reached terminal only for the matching cell
and face; a valid tree-cell goal can have no terminal. Both faces seed the start
under FULLER (2110-2113), with secondary-face start cost2 and no drilled VIA.

Router.permitsLayerStep calls its existing legal-move, traverse and occupancy
rules (PcbNetRouter.java1070-1109). These include pad escapes/reservations,
foreign occupied/clearance cells, role-width stroke, component courtyards,
permitted underpasses and barriers (546-570,790-907); ownsLayerGridCell includes
the same-net claim and canJoinTree courtyard rule (749-758). Trace clearance is
(w1+w2+1)/2+6; occupancy reserves a one-cell radius. Geometry alone is not this
predicate. Router.gridPoints (976-996) defines authoritative trace occupancy
grid mapping, and requireExactOccupancy (1000 onward) detects divergence from
current physical traces. Trace getSourceId and hole/pad IDs are existing stable
source identities, while the goal mask itself retains no per-cell source IDs.

## VIA and quality authority

BranchSearch freezes actual layout holes at construction (2094-2101). viaFits
uses holes.length and physical site constraints; relaxedViaFits (2695-2728)
omits the remaining-hole cap. pathViaFits (2730-2738) additionally requires
layout.getHoleCount()+current.transitions+1 <= maximumVias and rejects a site
whose x and y are both within20 of an earlier path VIA. All PcbBoardHole entries
count for that cap; lineage separately counts Kind.VIA. Never infer VIA-kind
count or remaining allowance from the observed37 holes alone.

The ordinary cost is10, plus2 on the secondary face and35 on a turn; a layer
flip costs the detached owner's240 (ordinary policy cost60). A* prioritizes
g+h, h, key and serial and stops at the first popped goal (1445-1459,2358-2370).
Publisher counts planar length, resets turn direction across a layer flip and
rejects direct==0, length>3*direct or bends>16 before publication (2605-2617).
Thus a different feasible higher-cost path would not prove current selection.

## Existing capture boundary and artifact scope

emitTerminal deliberately skips FOUND before fixed-prefix observation
(2133-2143). The actual A_DRIVE path was found, then rejected by Publisher, so
the existing failed-branch callback cannot supply its graph. The live zero-via
test adapter supports only D_SENSE and LOAD12 and has no VIA-site mask.

The private BranchSearch.FixedPrefixCaptureTask (2373 onward) clones both face
routers twice, checks pristine/source/query state and effects, scans root/tree/
reached goals, ordinary directed edges and relaxed VIA sites, then checks live
state and prefix geometry before returning an immutable snapshot. It uses4096
probe scan chunks and32768 copy/comparison chunks. Its constructor requires real
lineage, actual context and live faces. Its finish() returns a completed snapshot
without emitting a callback. Detached Session preserves fixedPrefixContext
(1013). A new test-only adapter must validate this actual construction path at
the paused branch without mutating callback state or production behavior.

Generic fixed-prefix graph format has250K-cell/8MiB canonical limits and explicit
DIRECTED_TWO_FACE_HISTORY_RELAXED semantics. Its cost60 oracle does not apply
the copied cost240, four-transition, hole-budget, path-spacing, bend or length
rules. The capture reader additionally bounds390 holes/246 pads,640 enclosing
groups and2560 internal capture units; event-line/capture-file limits16/32MiB.
The zero-via adapter's1M UTF-16 body and4096 path-node bounds belong to that
different artifact. The guard witness's1MiB/4096-node bounds remain separate.
These limits do not automatically qualify any new graph representation.

Implementation must preserve complete goal provenance and explicitly record
which path-dependent constraints the static masks omit. Missing provenance or
an unsupported capture state is an incomplete diagnostic, not a routing fact.
