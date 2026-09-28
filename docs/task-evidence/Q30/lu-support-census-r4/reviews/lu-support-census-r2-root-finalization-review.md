# LU support census R2 root-finalization review

Review scope: read-only inspection of the root-finalized source-only bundle at
`scratch/lu-support-census-r2-wired-draft`. No repository files were edited and
no build, native test, browser run, host run, or actual-LU canary was run here.

## Binding and result

The current bundle is the post-GWT-clone-repair bundle, not the superseded
`cf08ea...`/`eb82b1...` handoff. The current source-manifest SHA256 is
`b1b0eea88eeb71c12f1fbc757289cff722fb33689babe13a69f02062fe8efe17`.
The manifest's byte and SHA256 entries all match the files inspected. Relevant
current identities are:

| artifact | SHA256 |
|---|---|
| `src/.../Q30LuSupportCensus.java` | `1c2aeaa150a766f4e6dcddacaa9f8865b58274f9e160294d90b8073646bfd5b5` |
| `src/.../Q30CoordinatorQualificationVerifier.java` | `e610de476df73c4f303e28182bffe8e4bae2893e62db69e10341be7df9c777e1` |
| `src/.../CirSim.java` | `1c89d9a1cd1d9c89fcdb4ff97672b27a41b314b2cc0edf5bd78852c970f72061` |
| `tests/contracts/Q30LuSupportCensusContractTest.java` | `d314d2c6d25179b60264763278072fa8de15415b1f953140f0cff469ca1f6350` |
| `README.md` | `a6ada1e61c4d9f15efc32f0e854243c1ff56221790a37559eb62d25ace38542f` |

Static outcome: the R2 fixes requested in the handoff are present. I found no
new producer-side blocker. One concrete root acceptance gate remains mandatory:
support drift must be rejected from any optimization conclusion independently
of the collector's `exact` flag.

## Fixes confirmed

- `endSupportCensus()` passes `scopeComplete` only when the captured job is the
  current job and the coordinator is neither running nor advancing. Active or
  lost ownership therefore freezes a report with `scopeComplete=false`.
- `completeCapture` additionally requires complete successful traces and replay,
  valid state, zero lifecycle/overflow/diagnostic/scan errors, no failed or
  dropped records, and selected/completed/sample count equality. It is clearly
  separate from `valid` and is described as capture evidence rather than a
  solver or optimization proof.
- Successful selected samples retain a bounded defensive `ipvtPrefix`; failed
  samples receive an empty prefix and `ipvtTraceComplete=false`. The collector
  and returned report use explicit `System.arraycopy`, so the GWT 2.7
  `int[].clone()` incompatibility is removed without changing the schema.
- Matrix inspection is limited to the active `n`-by-`n` prefix while allowing a
  larger outer allocation, and a short prefix remains invalid.
- `CirSim.clearQ30LuSupportCensus` performs exact owner comparison followed by
  assignment. The source does not claim an injected throwing clear operation.
- The serializer keeps long counters as JSON strings, preserves the new scope,
  capture, trace, and support-drift fields, and publishes the private query
  only for the opt-in census path.
- The README now states the pre-swap lower and selected-row upper domains and
  their overlap, making clear that they are label-domain counts rather than
  additive LU work. It also separates stable component partitions from support
  stability and identifies both drift counters as decision inputs.

## Required root gate: support drift is not part of `exact`

`Opportunity.replay()` computes `exact` from replay completion, pivot-domain
conditions, nonfinite state, stable component partitions, sentinel state, and
zero-pivot observation. It does not test
`snapshot.supportAdded == 0 && snapshot.supportRemoved == 0`.
`stableOriginalComponents` only proves that the row/column partitions agree;
an edge can be added or removed inside an unchanged component.

The native internal-drift fixture demonstrates the concrete case: a 2-by-2
current matrix adds one internal support edge while the component partition
stays stable. The fixture currently leaves zero-pivot observation unavailable,
so its `exact=false` assertion also exercises the unknown-fallback condition;
it does not prove that `exact` stays false when observation is declared
available. Therefore the root reader/binding must require both support drift
counters to be zero before treating any `exact` or candidate-domain result as
eligible evidence. If a consumer uses `exact` alone, this is a blocker. If the
root gate enforces the documented drift rule, no collector change is required.

## Accepted limits and pending evidence

- `valid=true` can coexist with `scopeComplete=false`; this is intentional for
  an active/lost owner. The accepted evidence path must require both
  `scopeComplete` and `completeCapture`.
- Runtime still has no zero-pivot observation hook, so runtime reports should
  retain `zeroPivotObservationAvailable=false` and `exact=false`. The census
  cannot establish an optimization or fallback proof by itself.
- Failed samples retain initial-order counts only and expose incomplete pivot
  traces. Those counts must not be interpreted as post-swap upper bounds.
- The root-reported pure collector assertion pass was not independently run in
  this review. The real selected successful/failed LU and `ipvt` integration
  canary remains pending, as does the corrected GWT build receipt.

## Root pre-execution checks

1. Bind the post-repair manifest and the four source identities above; do not
   reuse the superseded collector hash.
2. Require the exact private query, frozen/valid flags, zero error counters,
   `scopeComplete=true`, `completeCapture=true`, complete successful traces,
   and no failed/dropped samples for accepted evidence.
3. Gate supportAdded and supportRemoved independently at every accepted sample
   before using `exact` or comparing label-domain opportunity counts.
4. Run the actual LU canary and the original strict reader on untouched reports;
   preserve the source-only and unknown-fallback limits in the final record.
