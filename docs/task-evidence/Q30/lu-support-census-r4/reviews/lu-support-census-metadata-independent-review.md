# LU support census metadata validator independent review

Review scope: read-only review of
`lu-support-census-metadata-validator.py` against the finalized R2 producer
bundle in `scratch/lu-support-census-r2-wired-draft`. No producer, validator,
repository, build, host, browser, or native source was changed.

The reviewed validator SHA256 is
`e0dfc6e12d40ee0a19d78cb18f7999b7afdc1916d190f26f1252e96090e1c23d`.
The bound producer manifest is `b1b0eea88eeb71c12f1fbc757289cff722fb33689babe13a69f02062fe8efe17`;
the collector is `1c2aeaa150a766f4e6dcddacaa9f8865b58274f9e160294d90b8073646bfd5b5`.

## Checks performed

The pure validator selftest was run with bytecode generation disabled:

```text
python -B lu-support-census-metadata-validator.py --selftest
PASS: 2 positive phases, 14 rejected corruption mutations
```

The independent replay formulas agree with the finalized collector: full
pivot candidates are `n*(n+1)/2`, full lower and upper candidates are
`n*(n-1)/2`, lower candidates are counted before the current row swap, and
upper candidates use the selected row label and later columns. The validator
also independently checks pivot-domain bounds, replay totals, graph partition
labels, support conservation, cell-read totals, frozen/scope/capture flags,
and the zero-error counters.

The current private producer has no zero-pivot observation hook. The validator
correctly requires `zeroPivotObservationAvailable=false`, zero fallback counts,
and `exact=false`; a fabricated `exact=true` mutation is rejected. Sentinel
risk remains representable while exact evidence stays unavailable.

## Required root decision gate

The validator accepts support drift as structurally valid metadata and returns
`countsAreStructural=true`; it does not return a `supportDriftFree` result or
reject nonzero `supportAdded`/`supportRemoved`. A pure mutation that changed a
stable 3-by-3 fixture to `supportAdded=1`, `supportRemoved=1`,
`supportUnchanged=4`, and `valueDifferences=2` returned `status=PASS` with
`exactEvidence=false`.

That behavior is safe only when the caller treats the result as metadata
validation. The finalized README and producer contract correctly say that
stable component partitions do not imply stable support. The root binding must
therefore require both support-drift counters to be zero before using any
candidate-domain count or structural result for an optimization conclusion.
Using this validator's `PASS`, `countsAreStructural`, or producer `exact` field
alone would be a false-acceptance path. Current runtime `exact=false` prevents
an exact claim, but it does not prevent a caller from misusing the candidate
counts.

Requiring zero support drift can conservatively discard a structurally useful
candidate, which is an intentional false-negative tradeoff for this private
decision. Likewise, reports from a future producer that genuinely adds a
zero-pivot observation hook would be rejected by this source-bound validator;
that should trigger a new schema/source binding review rather than a relaxed
check.

The emitted graph contains component labels and aggregate edge counts, not the
cell coordinates or values. Consequently the validator can check bounds and
conservation but cannot independently reconstruct exact value differences,
cross-original-edge totals, or support-added/removed cell identities. A forged
counter set that satisfies those bounds can pass. Keep the source/runtime
binding, original strict application reader, and the support-drift gate in the
acceptance path; do not treat these metadata fields as an independent
optimization proof.

## Concrete fail-closed gaps

These are validator-side schema gaps, not producer behavior observed in the
finalized source:

1. `_validate_graph()` compares `components[*].rows` and
   `components[*].columns` to expected Python lists but does not type-check
   each member with `_int()`. JSON `true` compares equal to integer `1`, and
   `1.0` compares equal to integer `1`, so either can pass as a component
   member index. The producer emits integer arrays; a strict corruption reader
   should reject non-integer members before list comparison.
2. A component with no rows and no columns can be appended while changing
   `componentCount` and `stableOriginalComponents` to `false`; the validator
   then returns `PASS`. `Graph.from()` cannot emit an entirely empty component
   because every union-find component owns a vertex. This should be rejected by
   a strict shape check, or the root must reject any result with unstable
   partitions before consuming structural counts.

The pure mutation suite does not cover either case. It also does not include a
support-drift mutation, so the root run should retain explicit canaries for
nonzero drift, unsupported exact claims, and malformed component members.

## Provenance and scope limits

The full-report path checks `supportCensusRequested=true` and the exact
`tsjQ30SupportCensus=true` marker. The alternate extracted-object path
intentionally accepts only the nested census key set and bypasses that query
check. It is safe only after a caller has selected the nested object from the
untouched full report and independently validated the query. The off/control
arm and top-level report provenance remain responsibilities of the strict
application reader and pair binding.

No actual cold/warm application report was validated in this review. Root
reported corrected GWT and compiled-canary receipts; the real native LU,
selected `ipvt`, and failure-path canary remain pending. No measurement or
optimization claim follows from this validator review.
