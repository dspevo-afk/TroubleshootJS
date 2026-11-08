# General observation V2 validation and consumer adapter prerequisite

Operative bounded scope, frozen by root before implementation. Parent receipt
8dd8175ce09617d5bd3dda774311c09a52e47dad; accepted R implementation
dfccca05d604935c29cadf1e96552027bd3e55e8. Standing roadmap continues. This is a
necessary non-authored substep toward full result/marker/R7/host migration,
not full-D qualification or permission to execute another authored experiment.

## Cause and owners

R's independent reader has a strict six-case fixture envelope. Its private
snapshot helper fixes one net, two pads and one callback context. D's current
observation consumer supports multiple nets, variable ordinals, preceding FOUND
callbacks, richer relaxed states and scoped branch-quality outcomes, but still
requires V1/17keys and terminal-only33D/D/69D+V accounting. Applying R's fixture
profile to D would reject valid general observations. Adding another head term
to D's work summation would double-count already-inclusive observation totals.

Create one independent profile-neutral observation kernel, generated into both
consumers from one source template. Keep source generation/current-only pins and
checked I/O with the existing owners. Do not add a runtime arbitrary-code loader,
sys.path change, dual-version resolver, producer semantic import or mutable
counter. The generated files are standalone Python source, with one kernel
definition embedded verbatim after their standard-library imports. No kernel
future-import or __main__ execution. The source proof binds the exact kernel
and both parents. Fixed R fixture restrictions remain in _context/_case.

## Inputs and API

Read source/protocols only. No captured D body, descriptor or compiled-input data.
Parents: R/read_selected_observation_v2.py
685e18d44f322a61d36f1aafa430cb562a5a7f536e2aa9c2640be929881d646b;
D/read_cohort_terminal_r2.py
2d53b33fb77e85c6c36f46b013612883866cffea2996596751ffcb2d30e2bc0a.
R WIRE_V2.md bebf6775a66a8702862d421fd9fd180364b8c0fcb393b8e9f34b1369e052dc80
still governs the unchanged nested18-key observation and generic packet.

Kernel observation_v2_kernel.py defines ObservationV2Error(code) and
ObservationV2Kernel static methods:

- validate_snapshot(s, requested, traversed, pads_by_id, pads_by_net, grid):
  preserve the existing general D33-field domain and source-bound net/pad/grid
  checks; return selected net ordinal. Preserve exact ordered keys and type
  rejection, Java integer limits, per-net reached-pad bound, terminal and relaxed
  predicates. Do not apply the one-net/two-pad fixture restriction here.
- validate_head(h, s): unchanged ten-int key/priority/cap/identity scalar domain.
- accounting(o): general V2 equations and bounds, returning entries/chunks/units.
- validate_observation(o, role, opportunities, requested, traversed, pads_by_id,
  pads_by_net, grid, outcome, terminal): strict18-key V2, role/mode/state,
  callback opportunity bound, general ordinal/finality/terminal/context binding,
  snapshot and head presence/domain, and accounting. Return an immutable mapping
  with observerUnits,copiedEntries,copyChunks,state, preserving the current D
  adapter result interface. No retained input aliases or state mutation.

Snapshot/head/accounting labels may be mapped by thin consumer wrappers into
their existing ReaderError owners. Prefix every kernel private name to avoid
collisions. Use standard-library code only. All public integer/type/key/domain
checks remain independent of Java and reject booleans as integers.

D callbacks <= min(247, opportunities). H=1 iff selectedHead is present; H<=D,
H<=1. Copies=33D+10H, chunks=D+H, units=69D+V+21H;
D<=V<=94D+12H; units<=163D+33H. Head iff selected first-nonfound terminal reason
BRANCH_SEARCH_LIMIT, matching the final observed callback/snapshot. No head in
NULL, success, nonbranch quality, other failure, or missing snapshot. Preserve
Java signed32 wrap for priority. A selected/popped head is not a path witness.

## Consumer generation and admission

generate_general_observation_readers.py accepts canonical absolute pairs
--generic-reader,--full-reader,--kernel,--base-generator,--io-helper,--output-dir.
Pin both parent hashes, the final reviewed kernel hash, the existing P loader
d11df617dc255257e3ac42cba45a9a8d78482f4fec9ea52f846a6f48f97ce1f0 and exporter
df9c297f4eb491117bade351ceedf26ccef5059e47ad9a7f36006000f1a10923.
Before execution root fills/fixes the final kernel pin and freezes all source
hashes; no placeholder executes. Reuse pinned regular-read/exclusive-write/
readback/closure with an empty owned output. Reject raw lexical aliases before
Path normalization, wrong pins, occupied output and reparse/canonical inputs.
Generate exactly read_selected_observation_v2.py, read_full_observation_component_v2.py,
and SOURCE_PROOF.json. Proof contains no private paths. Python always-I-B.

Generic derived reader retires duplicated snapshot/head/accounting domain bodies
in favor of thin kernel adapters while preserving all fixed packet/profile,
canonical parser, checked-I/O, readonly CLI and exact summary contracts. Its
existing15 tests must run against the derived source; add coverage where needed.
No production/native Java or producer wire changes. Exact accepted R generic
packet9579B/897db05949756ba10e376494de15bd869fd838355aa67ce2ebc6d38a0e3d801f
may be read only as generic fixture evidence, after consumed-input revalidation.

Full component derived reader replaces the single _validate_observation owner
and duplicated _validate_snapshot domain with kernel adapters. Remove/retire the
obsolete V1 observation constants and old33D-only arithmetic from that consumed
path. Preserve _validate_work summation algebra unchanged: observation totals
already contain head copies/units. Do not restore assembler/marker/export or
claim whole-format admission. main MUST deny with GENERAL_OBSERVATION_COMPONENT_ONLY
before argument handling, host module/descriptor/body reads, or artifact mutation.
Its other historical full functions are unavailable to CLI until separately
coordinated full protocol migration; no cross-version fallback is added.

## Tests, evidence and limits

Independent kernel fixtures must exercise multi-net/ordinals/preceding FOUND,
all observation states, branch/other failure, scoped/unscoped quality, relaxed
variants, per-net pad counts, wrong version/order/keys/types/bool/limits,
head presence/key/priority wrap, callback opportunities/finality and both copy
and unit equations. No fabricated original or fake whole-R7 result.

Project the six actual accepted R generic case DTOs through the generic and D
adapter entry and compare independently expected immutable accounting facts.
These are adapter context projections, NOT fresh-run/original captures. Never
call validate_result/_validate_work with a fake original to claim integration.
Fresh actual derived generic CLI must validate the unchanged accepted R packet
through the selected pinned Python/checked-I/O path, with source/process closure.
Fresh actual full component CLI must reject before data access. Test generator
positive and bad-pin/occupied/raw-alias controls early, and actual junction
boundaries with identity-owned literal nonrecursive cleanup if OS symlink tests
skip. Preserve all failures; nonzero rejection is not ordinary PASS.

Root owns final source checks, publication, one completion Gmail after exact
normal push, and checkpoint/roadmap/architecture. GWT/native builds and visible
Player gates are NOT APPLICABLE to Python/source-only consumer changes, not
renewed. Existing producer source/classes/tools and packet reuse needs explicit
consumed-input hash audit. No additional authored invocation in S.

This closes general observation semantics/adapters only. Full result/marker/R7
aggregate/source-bound host acceptance, any authored experiment and physical/
access/runtime evidence remain separately unstarted/unqualified. No implication
for electrical behavior, generation epoch, Player admission or playable100.

All90s/640groups/5s/32768units/copy1048576/chunk4096, compile/suite60s,
wrapper480s/reader180s/cleanup120s/local100000/global1m unchanged. Full30 EXCLUDED;
HARD/PSYCHOTIC unavailable/unrun; protected bodies unread here, historical unread
UNCERTIFIED. Playable100zero/betaHELD. One expensive matrix per worktree.
Workers are leaves, one writer per assigned file, preserve others' edits, no
execution/staging/commit/push/notification. Root integrates and owns acceptance.
