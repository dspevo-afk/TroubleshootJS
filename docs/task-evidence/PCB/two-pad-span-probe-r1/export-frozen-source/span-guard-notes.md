# Two-pad span probe

This is a private, source-only experiment for selected copies of the current
placement planner and compactor. It has no production GO and does not change
the repository Java sources.

## Inputs and transform contract

The frozen scope is `two-pad-span-probe-r1`, based at
`113456fc2d781411f9a3e94bfca4b676fee883b6`. The current worktree source blobs
read by the adapter were:

- `PcbPlacementPlanner.java`: `e34312c88ab25143a0c34981c0a5f526a03ae254`
- `PcbPlacementCompactor.java`: `94870b8fa8893a65730cf04627497e08b56ab67c`

`span_guard_patch.py` is pure in-memory Python. `adapt(planner, compactor)`
returns changed source strings plus an exact replacement ledger. Every source
replacement must occur once, and each source keeps its own newline style. The
module has no file I/O, imports of project code, or import-time work.

The planner copy receives deterministic `TreeMap` bounds for every board net
with exactly two pads. Bounds use the actual placed pad centers and the
authoritative board/topology graph, with a long Manhattan span. A non-null cap
map must match the complete current two-pad-net inventory, contain non-negative
values, and contain no null, missing, extra, or incompatible entries. Missing
footprints or any board pad placement fail fast, including pads on multi-pad
nets. A null cap map preserves the original path.

The optional vacancy pass receives a four-argument overload. The existing
three-argument method delegates with null. The cap filter runs only after a
candidate vector has been built and before the existing area, wire, validation,
and acceptance logic. The existing topology graph is reused for each trial;
the cap check adds no evaluation, route, observer, or budget operation.

The compactor copy receives a four-argument overload whose three-argument
method delegates with null. Caps are forwarded only to the optional connected
vacancy pass. Strip behavior, paired compaction, ordering, evaluation limits,
and all existing validators remain outside the probe.

## Later fixture suggestions

The independent promotion fixture should use the selected-source-relative
two-pad endpoint pair that measures span `370` in the source placement and
`390` after the proposed translation. A cap of `370` should reject that trial;
a cap of `390` should accept it when all existing geometry and wire or area
oracles pass. The fixture should also include a multi-pad net and assert that it
is absent from the bounds and cap inventory, while still requiring every
multi-pad endpoint placement to be present and unchanged.

Negative cases should pass a map missing one exact two-pad net, containing an
extra net, containing a null or negative value, and containing a value for an
incompatible inventory; each must fail fast. The fixture should fingerprint the
board, footprints, cap map, and graph-relevant identities before and after the
probe. It should verify deterministic bounds and replay results, unchanged
evaluation counts for cap checks, and source-geometry fallback using the
trial's already-counted evaluations when a final cap check rejects a later
post-selection result.

## Validation status and limits

The module is checked with Python AST parsing and an in-memory transform and
reverse round trip only. Java compilation, native generation, routing, browser
checks, and the maintained build are intentionally NOT RUN in this leaf. The
root factory owns applying the transform to sealed private copies, source
compilation, reverse verification against the source hashes, and all native
acceptance gates.
