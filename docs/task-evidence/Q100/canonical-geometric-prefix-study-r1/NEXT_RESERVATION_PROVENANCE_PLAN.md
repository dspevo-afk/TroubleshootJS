# Next reservation-provenance diagnostic

**State:** `NOT_STARTED`; conditional follow-up to the locally accepted P8
geometric-reason diagnosis. Execution follows P8 publication and a separate contract freeze; no capture
has started and no physical feasibility or routing adoption is implied.

## Question

Can an independent reconstruction from the actual disposable Router inputs
reproduce the `MIXED` sentinel for the original parent-11 horizontal cache slot?
The scope is only provenance of that cached rejection. It does not open the
edge, change any constraint, or test another route.

## Frozen case

Use the authored BASE / SEPARATE case only: root seed `9007199254740993`,
placement seed `-6587958367974952701`, routing seed `8445301674744508279`,
request 0 / attempt 4 / pass 2, `A_COIL_LOW`, `QA.C` to `DA.A`, receipt 307,
lineage 77, H48. Preserve prefix geometry SHA-256
`6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb`, graph
SHA-256 `ff6466d21600fc07de94a2cc35ae9a369558219207d0bb2734dbda27ab1e4f0a`,
all original 22 query identities/order, and selected ordinals
`0,1,2,3,4,5,6,7,8,9,11,15,17,20`.

The single target is selected parent ordinal 11: layer 1, bit 8,
`(382,238) -> (381,238)`, corresponding to horizontal reservation slot
`[381][238]`. The accepted P8 event says the slot is `MIXED`; that value is a
comparison target only, never an oracle input.

## Why a new native capture is needed

Existing P4 PAD rows contain identity, net, role and center position. Their
candidate header identifies `ROLE_WIDTHS`, which establishes that the
profiled-pad-touch policy is enabled in the pinned Router source. P5 geometry
rows contain traces and holes; the P6 parent-11 row contains the directed grid
edge and result. None contains the exact pad bounds, escape corridor inputs,
per-net trace widths, active trace width, clearance operands, or per-pad cache
contributors. Therefore these receipts cannot independently rebuild the
parent-11 cache value.

The Router derives pad bounds and widths, escape corridors and cache entries
inside its actual query owner. It expands each escape corridor by the
PcbTraceRules clearance margin, optionally reserves exact pad-touch steps, and
stores a mixed sentinel when different net reservations collide. Those actual
operands must be captured from the disposable owner; regenerating them from
the existing P4/P5 projections would guess missing geometry.

## Minimal passive observation

Before any producer work, freeze exact input/output schemas, source pins, host
process/stream contracts, work accounting, timeouts and cleanup proof. Attach a
passive tap only to the disposable query Router while its existing reservation
cache is prepared. Target the one horizontal slot `[381][238]`; do not call
`isLegalMove`, `canTraverse`, `touch`, or any predicate a second time, and do
not change cache writes, route result, query order, source state or effects.

Capture bounded raw operands for the pad census needed to test every possible
contributor to this slot: pad ID/net/role, center, mounting face, escape vector
and length, pad-bounds rectangle, and the pad-net width. Also bind the active
net/width, clearance rule inputs, grid origin and pitch, target edge geometry,
empty-face state and profiled-touch flag. Record only the final actual cache
sentinel (`null`, one net, or `MIXED`); do not export a Java-computed
contributor list as the expected answer.

An independent offline consumer must derive the escape rectangles, clearance
inflation, optional touch-step hits, and distinct contributing net set from
those raw operands using a separately implemented literal oracle. It then
applies the existing null/same-net/different-net aggregation rule and compares
its result with the captured final slot. Include independent small fixtures for
single net, repeated same net, two distinct nets, boundary contact, and touch
steps enabled/disabled. Keep contributor identities private to the diagnostic;
do not claim unique ownership.

## Gates and stop conditions

Freeze and pass selected-host positive/negative controls before reading any
accepted-case output. Require final-source JDK8 compile, fresh native capture,
independent strict-consumer result, NULL/OBSERVE parity, old CALLBACK_THROW
and REASON_THROW behavior, negative schema/source/transport controls, actual
process termination, source closure, unchanged P4/P5/P6/P8 evidence, and
before/after effects plus cleanup proofs.

Retain the existing limits without adjustment: 90 s work, 640 actual plus
wrapper groups, 5 s per complete group, 32768 units, 1048576-entry clones / 1000000 expansions,
4096-entry chunks, 48 holes/four flips, 64 KiB lines, 8 MiB operation output,
8 MiB host output and 40 MiB SUITE aggregate; compile 60 s, audit 60 s, native
480 s, reader 180 s, outer 1800 s, cleanup 120 s. Charge every new capture and
consumer operation under the existing accounting model.

Any operand mismatch, unreproducible sentinel, changed route/query result,
source/effect drift, missing process exit, timeout, or cleanup failure is a
failure. Stop after this one slot. No edge opening, constraint relaxation,
broader prefix, net removal, route-policy/epoch/budget change, unique-owner,
general feasibility, production adoption, GWT/Player renewal, or playable100
claim follows. Full30 remains owner-excluded; HARD/PSYCHOTIC remain unavailable/unrun.
Protected bodies are unread/unrun here; the historical unread boundary remains
UNCERTIFIED. Playable100 remains zero and beta held.
