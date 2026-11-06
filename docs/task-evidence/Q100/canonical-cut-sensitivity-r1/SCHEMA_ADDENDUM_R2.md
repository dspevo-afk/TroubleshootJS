# Effective P7 work/source clarification R2

Frozen before implementation or execution. PROTOCOL_R1 and SCHEMA_R1 remain
preserved. This addendum resolves accounting details without increasing any cap.

WorkBudget.call(phase,action,entries=0) declares an upper entry bound for that
actual unit, not a fabricated completed-entry count. Add method
record_entries(kind,count), kind exactly copy or bfs, called inside the active
unit after the work. It records actual entries/pops, rejects bool/negative values,
duplicate accounting or a count above the unit's declared bound4096. Copy and BFS
unit counts increase only for real corresponding calls; opaque calls use entries0.
The BFS's last short batch reports its actual pops, not4096. No nested timed units.

Derive the complete outgoing cut from CONTROL's already-computed full closure:
disabled in-bounds planar edges from a reached state to an unreached state only.
Disabled internal edges and out-of-bounds steps are excluded. Sort by numeric
state then N/E/S/W; require an exact match to all opening descriptors, including
unselected rows. CONTROL uses the validated base graph directly, not a pointless
full clone. Its BFS is not repeated solely to derive the cut.

The source host performs the transitive P6 source/parent proof before the analysis
worker and the full post-source verification afterwards. These are separately
measured prerequisite/cleanup gates, not fictitious P7 math groups. The worker
stable-reads/hashes its own directly consumed code/spec/data/oracles inside timed
analysis units; it does not duplicate the whole1084-row prerequisite under one
opaque5s unit. All inherited inputs must still pass the real host pre/post checks.
Conservative cleanup charges full prerequisite+worker+post-source wall<=120s.

Result work.analysis is a snapshot through result preparation. Its
serializationNanos measures bounded preview/preparation serialization. The final
result encoding/hash/CREATE_NEW write is one additional real timed transport unit.
The worker index gains exact key work, a final WorkBudget.finish snapshot after
that unit; index elapsedNanos matches that final snapshot. Its source/process
envelope serialization/last emission is conservatively bounded by the host:
residualUpperNanos=max(0,workerWallNanos-index.elapsedNanos). This upper includes
startup/final-emission/process-close overhead; require<=5s and reserve one real
host-measured transport/closure group. Final groups=index.work.groups+1<=640,
and maximum=max(index.work.maximumGroupNanos,residualUpperNanos)<=5s. Full worker
wall<=90s conservatively covers every worker phase and final I/O. No tail work is
declared free or included in a self-referential result timestamp.

The result snapshot and final index work must agree on all previously completed
copy/BFS/oracle counters; the final group delta is exactly1 transport group.
Host records the residual upper and the stricter full-worker wall separately.
Any failed residual/group/wall/source/cleanup check rejects the whole run.

CANARY work uses the same final index transport accounting and declares real
casesRun0. Its pure literal checks do not read the actual capture. Final input
spec additionally pins this effective addendum path/hash; include original schema
and protocol in its exact file list. Implementation may now use these combined
frozen contracts; source byte hashes are bound before any canary/case execution.
