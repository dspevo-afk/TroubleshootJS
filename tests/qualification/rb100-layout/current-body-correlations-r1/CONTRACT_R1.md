# Current body correlations: finite Source contract

This prerequisite adds three read-only correlations over ordinary synthetic
metadata. It does not qualify a complete result, authenticate a factory or
receipt, prove body lifetime, or admit a genuine envelope or playable Q100.

The current export count, write and compare calls select `writeCorrelatedBody`.
Its unchanged header validation precedes these checks; unchanged canonical
domain traversal follows them. The lower-level `writeBody` remains the canonical
transport component, preserving its independent encoding/domain test oracles.
Every current exporter call migrates; it may not use the transport-only entry.

- `qualifiedPrior.inputContextSha256` must be a String equal to the root context
  identity, already validated as lowercase SHA-256 by the header. Failure:
  `BODY_PRIOR_CONTEXT`.
- `arms.null.armRole` must equal `NULL`, and `arms.observe.armRole` must equal
  `OBSERVE`. Failures: `BODY_NULL_ROLE`, `BODY_OBSERVE_ROLE`.
- `cleanup.cleanupNanos` and finalized `work.cleanupNanos` must each be Integer
  or Long, in 0..120000000000 inclusive, and numerically equal. Failure:
  `BODY_CLEANUP_NANOS`. This is the supplied work cleanup scalar, not a newly
  computed sum of arm cleanup times.
- A required nested map that is absent or not a Map fails
  `BODY_CORRELATION_MAP`. Root order/container/header failures keep their existing
  codes and precedence. Extra nested fields are outside this partial contract.

The existing stable, exclusively borrowed body lifetime is a precondition.
`beforeDataOperation` runs before each lookup and scalar/type check. This uses
the existing export block admission: it is not per-field native-unit pricing.
No bytes, containers, copies, groups or Meter operations are added by the checks.
At position zero block zero has already been admitted by header validation.
For valid bodies the existing fee/copy equations remain `5*blocks+3`,
`2*resultBytes+32`, and `2*blocks+1` before marker publication. A mismatch fails
in the initial count pass, before canonical bytes or a result stream are emitted.
This does not assert atomic path handling or real owner/host qualification.

Source assembly is a pure bytes-to-bytes function. It accepts only the pinned
current Study (236944 bytes, 90f4c227d125587a7d1c8d3b324fe7fd375fd89af7a8a34a83a65a3c0d310bad),
current constructor-equipped codec (20119 bytes,
04b016afa690d2154f0b970640bc30b7c4887b450622444f8f18329bc04c3672), and reviewed
fragment. It inserts before the unique codec constructor and changes only three
export call names. It owns no filesystem/CLI/process interface; its caller must
use existing admitted I/O. It rejects incompatible inputs before returning any
output. Historical builders, data classes and held-out inputs are not invoked.

Validation: final-source JDK8 target7 compile; independent literal synthetic
encoding oracle; relation/type/boundary/rejection cases; lookup admission and
zero-byte failure; unchanged real R5 count fees/copies/groups and old codec
domain/marker/wide/block-cap oracles. No actual/fake arm owners, graph, factory,
Session, positive original DTO, routing, exportV2 or genuine run is executed.
Budgets, exact seeds/epochs and independent oracles remain unchanged. Production
GWT/Player are not applicable; electrical/population qualification is not renewed.
Full30 is excluded, HARD/PSYCHOTIC unavailable/unrun, playable100=0 and beta HELD.
