# Current private LU preflight profile — independent review

## Frozen source identity

Candidate `current-preflight-profile-draft/candidate`:

- CirSim r2 SHA-256: `15a576e8a254def5e1a90dcd860992ec36cacb9d1c1dbfd61026e52988cf340d`
- Q30 coordinator SHA-256: `b378808e40c9173e337fea996c74375c361c251f1c9032d90504b777f0f147d8`
- Contract test SHA-256: `3bf162017b8967304e87a07928c3fdd5d7966dd3a7945e988560a9ed4b9d5e3c`
- Paired baseline CirSim SHA-256: `f836bfc58bdfb87d67f25184ebcd51e60d9eb820d6cc5acb966bcde98a64296b`
- Paired baseline coordinator SHA-256: `43f5aa1cfa85401aa048b2394261e266bab2697bef4f66fbf01cabb0fc86040c`

This review covers only the profiler delta. I did not edit source or run a build/test/profile.

## Source findings

I found no sampler-semantic blocker in the frozen r2 source.

- `runCircuitOwned` still calls `lu_factorNonlinearOwned` once per nonlinear factor attempt. Query-off and nonsampled attempts choose the four-argument wrapper, which delegates once to the five-argument private helper with a null profile; sampled start/compare attempts call the timed overload once. No factor or solve result is reused, and the sample buffer only copies/compares matrix values. Factorization arithmetic and the zero-row predicate/order are unchanged.
- Sampling is 1-based over factor calls inside active coordinator work windows: start at 1021, compare the immediately following factor at 1022, then start at 2042/compare at 2043. A comparison is accepted only for matching size, generation, revision, and analysis epoch. Work-scope close, owned analysis, owner-work invalidation, and profile end abort pending comparisons; end clears the buffer. Cold and warm coordinator reports each receive their own profile. The coordinator wraps each manual advance in `setWorkScope(true)`/`false` with the close in `finally`, and all finish, successor, pending-cleanup, and run-completion paths close the profile.
- Sampled row metrics correctly treat `+0.0` and `-0.0` as zero and a subnormal as nonzero. The diagonal-first formula accounts for the diagonal probe and skips it during the ascending remainder; exact zero rows cost `n` probes. The fixture's diagonal, off-diagonal, late-hit, signed-zero, subnormal, and all-zero rows produce the asserted totals (24 left-to-right reads, 20 diagonal-first reads, 4 nonzero diagonals, 2 zero rows across two traversals).
- Matrix inner loops have no per-cell clock reads or long-counter increments. Input traversals aggregate cell-read totals once per matrix, comparison totals once per pair, and row counters once per row. Hot factor-call profiling uses an `int` call counter/cadence check; allocations and clocks occur only on sampled calls while the opted-in profile window is active.
- The zero-row timer starts after workspace reset and closes in `finally` around only the zero-row scan, before LU arithmetic and before workspace clear. r2 increments `preflightTimingCompletions` at `finishPreflightTiming` entry, before its native early return, and the contract test now asserts begin+completion on sampled factors, the zero-row early return, and a malformed-row exception. The outer workspace `finally` remains intact.
- The native contract invokes both private overloads reflectively and verifies cadence, workspace cleanup, read metrics, signed-zero comparison, and that sampling itself does not factor/mutate. It does not exercise the full `runCircuitOwned` coordinator call path; that still needs the real compiled browser run.

## Measurement limits and required gates

The profile brackets are narrower than total instrumentation overhead. `probeOverheadMillis` covers capture/compare work only; it excludes the per-factor profile/null/window branch, integer counter/modulo, overload dispatch, and code-generation effects. `zeroRowPreflightMillis` excludes workspace reset/clear, sample copy/compare and row-metric traversal, and LU arithmetic. Although query-off performs no profile allocation or clock call by source inspection, it still goes through the added four-argument wrapper, five-argument helper, and `try/finally`; the query-off candidate is therefore not a zero-overhead uninstrumented control. Do not claim zero total instrumentation cost or use these bracket timings as absolute production cost. `performance.now()` resolution/coarsening is also unreported, so very small/zero samples should not be overinterpreted.

The emitted row fields are modeled short-circuit read counts, not measured timings for the alternative scans. Native contract coverage is source evidence, not GWT/browser evidence. Required next evidence remains the exact isolated GWT build and strict cold/warm reader with `tsjQ30LuRepetitionProfile=true`, checking cadence/window/reset/error counters and unchanged proof/cleanup receipts; a query-off run through the same harness; and a separate comparison against the uninstrumented integrated source if total instrumentation overhead is to be quantified. Root reports the focused native sampler contract passed in its isolated fixture; that run was not performed by this reviewer and does not replace the browser validation.
