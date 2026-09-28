# Independent review: LU support census r1

Review status: `SOURCE_ONLY_DRAFT_NOT_VALIDATED`. I reviewed the draft
collector and native contract at the pinned identities below. The current
fusionR2 baseline has no profiled-factor seam, so this review does not validate
or imply any wiring, lifecycle integration, timing result, query behavior, or
production effect.

| artifact | SHA-256 |
| --- | --- |
| `Q30LuSupportCensus.java` | `6FCD61B3BECCA3D11C9E09FBF8B5268F04BCA35AB0B8D90F7D6914C6C4E41B62` |
| `Q30LuSupportCensusContractTest.java` | `38191D0B9FB557FB2096330E10DCB8E014C4C09B6FE95FD1E8411C90FA8E14A0` |

## What the draft does well

The scan uses separate bipartite union-find graphs for current and original
support, includes isolated row and column vertices, emits ascending member
arrays, and records current edges that cross original components. It treats
nonfinite supported entries as invalid evidence without invoking a solver
finite guard. `Graph`, `Sample`, and `Report` copies are defensive, and the
256-sample/512-dimension limits bound retained arrays.

The replay algorithm is structurally consistent with the observed zero-based
LU convention: it reads `ipvt[k]` before the corresponding label swap, counts
the current suffix, then swaps copied row labels for the next pivot. Invalid
indices, failed factors, missing zero-pivot observation, fallback counts, and
nonfinite support prevent `Opportunity.exact`. This is a static review only;
the draft has not received a real LU `ipvt` or a real factor exception.

## Blockers before using the output as evidence

1. **Failed-factor values are not upper bounds.** `Opportunity.asIncomplete`
   retains the initial-order local counts, while the design and contract call
   them "initial-order upper bounds." Row swaps can increase a suffix count.
   For example, with row labels `[A,B,A]`, column labels `[B,A,A]`, and a
   valid first pivot selecting row 1, the swap produces `[B,A,A]`; at the next
   `A` column the initial suffix has one `A` row but the post-swap suffix has
   two. The same issue applies to lower and upper counts. Report these as
   `initialOrderOnly` observations with unknown post-swap opportunity, or
   remove the upper-bound wording and add a counterexample to the contract.

2. **`exact` needs a narrower meaning and a separate safety gate.** The flag
   proves only that component labels replayed under one supplied `ipvt` trace
   met the collector's conditions. It does not prove exact LU arithmetic work,
   numeric pivot eligibility, fill behavior, writer coverage, or a safe block
   restriction. In addition, support additions/removals and
   `currentCrossOriginalEdges` do not currently clear `exact`; the contract
   deliberately adds a cross-original edge and still expects `exact=true`.
   That is defensible only if the claim is explicitly "exact for the observed
   current-component label count." If the consumer uses original components for
   a structural optimization, any support drift must force a separate
   `stableOriginalComponents=false`/NO-GO result. Root must gate on that result
   and never treat `exact` alone as an optimization proof.

3. **Report validity does not mean complete successful evidence.** A failed
   selected factor increments `failedFactorCalls` but leaves the report
   `valid` when the bounded diagnostic lifecycle is otherwise clean. Likewise,
   an unavailable zero-pivot hook can leave the report valid while every sample
   remains non-exact. Add an explicit report-level completeness/unknown flag,
   or make the root acceptance rule require zero failed selected factors and a
   known fallback hook in addition to per-sample `exact`; do not use `valid` as
   that rule.

4. **Duplicate pre-factor capture is silently accepted.**
   `recordPreFactor` overwrites `token.snapshot` when called twice and adds the
   second scan's cell reads, without a lifecycle error. A caller mistake can
   therefore replace the matrix evidence while the report stays valid. Reject
   a second capture for the same token and add a native negative fixture.

## Secondary limits to resolve or document

* The design says all counters are saturating, but scan, component edge, and
  opportunity counters use plain `++`/`+=` rather than `add`. The configured
  512-dimension and 256-sample caps make their current maxima far below `long`
  overflow, but `counterOverflow` would not protect these expressions if caps
  or scopes change. Use the saturating helper or document and contract-test the
  bounded arithmetic proof.
* "Bitwise value differences" uses `Double.doubleToLongBits`, which canonicalizes
  NaN payloads. Nonfinite samples are already invalid for exact use, but the
  reported bit-difference metric is still inaccurate for distinct NaN payloads;
  use `doubleToRawLongBits` or rename the metric to canonical value bits.
* The defensive-copy and cap tests are useful, but the native oracle hard-codes
  an `ipvt` path rather than obtaining one from the real LU. It does not cover
  duplicate capture, cap drops, counter saturation, exception/finally paths,
  NaN payload differences, post-capture matrix mutation, or the support-drift
  safety rule. Those are required canaries after a real seam exists.

The draft remains suitable as an unregistered design/collector review once
the above semantics are fixed or explicitly narrowed. No test, build, browser
run, new profile, integration, timing measurement, or heavy-copy execution was
performed here; P07 timing remains outside this review.
