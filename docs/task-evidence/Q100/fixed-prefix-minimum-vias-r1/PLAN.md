# Minimum-via lower bound on accepted fixed-prefix graphs

Frozen2026-11-02 before implementation or new measurements. Source parent
0572b959b27a1950980276b316ef4f387ed9e359; prior witness implementation25170c65.
This Python-only diagnostic consumes only the exact two already accepted B6
captures. It makes no production routing, epoch, electrical or admission change.

The previous weighted shortest witnesses contain1/8 vias, but those counts are
only upper bounds on the fewest possible vias. Compute the exact minimum number
of VIA transitions in the immutable DIRECTED_TWO_FACE_HISTORY_RELAXED graph:
all allowed directed planar edges cost0, enabled face-toggle edges cost1, and
every supplied start has initial count0. Check the original exact goal states.
This is a separate objective, not an edit of the pinned weighted-cost oracle.

Use a deterministic layered0/1 search. Exhaust each level's directed planar
closure before promoting any pending via targets. A global visited bytearray
marks states assigned their best level; source order is sorted and directions
N,E,S,W. Check goals when popped. Report GOAL_POPPED and the exact minimum on
success; only complete frontier exhaustion reports UNREACHABLE. An exception,
deadline or cancellation is a failed/incomplete diagnostic, never unreachable.
Do not retain predecessors or publish a full path. At most500000 states and
2500000 outgoing edges are available. Each state expands at most once; one
bytearray and two bounded unsigned32 arrays keep incremental storage bounded.
Counters describe work through termination, not a full census after success.

The allowed production transition count is4. Prefix all-hole counts are48/39;
before making a hole-budget inference, recheck that production charges each
transition as a new hole. A minimum above a justified applicable bound excludes
paths meeting that bound only in this fixed relaxed graph. A minimum at/below it
does not establish history, via-spacing, scheduler or full layout feasibility.
No outcome proves the board impossible or diagnoses production exhaustion.
Always retain productionFeasibility UNKNOWN and all acceptance flags false.

Inputs remain root9007199254740993, placement-6587958367974952701,
routing8445301674744508279, SHARED_HYSTERETIC_LINEAR/SEPARATE_DIRECT_SWITCHING,
layout25/dependency27, bare private generator envelope=null. Summary SHA-256
9dab3b92652734ff33b1c0bc183b896e7aaa51e6d30735eba6a8844ea7f64aa8;
receipt7769545bytes SHA-256
05b24b31c62fbd1d0fb14902dbb610384a1b58900339466726c4939c6e2f6086;
graphs e1937e17dd01b00c49021ade1931e0a510694eb76626b1a0146ff38bc6d06248 and
5b1c80b2c97dbfd4cd65ccd45ff7dbc056e78fa53f23c794ee02cd2bba67e9a5.
Keep oraclec146cef4336d96eb6b6597569115d746d6a1fffe3b0c2ee2759d0e259d8b2a00
and the strict previously published witness reader34e9275d byte-identical.
Reuse its pinned input/provenance/path safeguards; pin new algorithm code before
execution. No new raw dataset exports, other captures or seeds are authorized.

Before real graph runs require literal0/1/multiple-via/unreachable/multiple-start/
multiple-goal cases and independent exhaustive simple-path enumeration on tiny
hand-specified directed graphs. Include a long zero-via alternative beating a
short via route and a deferred-promotion regression where a pending via must
not hide a current-level planar path. Test graph/type rejection and immutable
inputs. The selected real CLI must pass both exact cases and wrong binding,
corrupted receipt and modified helper rejection with actual exit/termination
evidence. Each child has60s under the maintained selected WinPS process helper;
record operation/cleanup separately if available and do not invent that split.

Audit all consumed production/native/build inputs unchanged before reusing
their prior evidence; fresh native/GWT/Player/Current/manual-input gates are
NOT APPLICABLE only if that audit passes. One actor at a time through cleanup.
Production90s/640/5s/500ms/P07branch100K/request1M/cells250000/vias48/browser15s/
cleanup120s remain unchanged. Raw data/process receipts stay task-owned OS-temp;
publish only bounded controlled results/source hashes and truthful limitations.
Three approved normal development roots remain the regression ceiling; owner
excluded full30 manifest test stays excluded/unrun. HARD/PSYCHOTIC unavailable/
unrun, prior unread certification UNCERTIFIED and no unmaterialized-data claim.
Accepted playable100 layouts0; public beta held. Next treatment depends on these
results; real production/finalizer/connectivity/access/runtime proof remains.

## Hole-charge source audit before measurements

PcbLayerRoutingPrototype.pathViaFits:1991 checks
layout.getHoleCount()+current.transitions+1 before viaFits runs. viaFits:1957
permits reuse of an existing same-net hole and publication:1930 can avoid a
duplicate physical hole; that does not bypass the transition budget charge.
Thus prefix48 allows0 transitions and prefix39 allows4 under the exact authored
caller's maximum48 (SeededPcbLayoutGenerator:588). Captured relaxedViaFits:1962
and its graph scan:1786 omit this budget and prior path history. The comparison
quantity is PREFIX_HOLES_PLUS_TRANSITIONS, not a measured physical hole count.
The prior witness report's historical field holeCountAfterAddition describes
that gate's charge; clarify its prose without modifying old source/results.

The implementation's visited byte also marks deferred discoveries with value2.
Such a target can be admitted by planar movement at the current level before
promotion, without suppressing the cheaper path. uniqueStatesAdmitted counts
first discoveries including these deferred targets; statePops counts actual
processed states through termination. maximumQueueStates counts live FIFO
entries, not backing-buffer capacity. Preserve literal promotion and two-VIA
fixtures plus exhaustive tiny-path comparisons before accepted-graph runs.
