# Independent review: LU support census r2 wired draft

Review status: `SOURCE_ONLY_DRAFT_NOT_VALIDATED`. This review covers the
collector, native contract, and the concrete CirSim/verifier overlay patches.
It does not apply the patches, validate the baseline source hashes, run a
native contract, build GWT, or exercise a host. The collector currently hashes
to `67402923F4669FC92703B69BE04552490837A5CAEC3116724505D822EC6A04E3`.

The collector and patch identities are recorded consistently except for the
native contract: `source-manifest.json` records
`dc382ade14eaec12719b8f7a2c261c195447ecaca7cba5585c8d262b01769c6c`, while
the current `Q30LuSupportCensusContractTest.java` bytes hash to
`EEDA9EB4AEBB3D06C3C2DC2163BEAB436D10D69DDF4F1C13AD19BCE82E5076A3`. The
R2 identity is therefore not frozen until that manifest entry and any exact
diff identity are regenerated or the file is restored.

## R1 fixes confirmed by static inspection

The collector now marks failed or invalid replay data as
`initialOrderOnly=true`, rejects duplicate pre-factor capture, reconstructs
the eligible total from the 4096-call primitive cadence, and accepts only the
active `n`-by-`n` prefix of a larger original matrix allocation. Long report
fields are converted to `JSONString` values in the verifier serializer.

The selected CirSim path still calls the original LU once. It records a
diagnostic failure without replacing the physical result, finishes selected
factor accounting on an original `Error` or `RuntimeException`, and rethrows
that same object. The successful path replays the supplied zero-based `ipvt`
labels without a second factorization. These are static findings; the real
exception identity and real LU pivot trace remain untested.

## Freeze blockers

1. **The census can be frozen and cleared while the coordinator is still
   active.** In the wired overlay, `failBeforeOrDuringRun` calls
   `endSupportCensus()` before cancellation has been shown to quiesce the job.
   The existing scope-loss path can call `finishWithoutTouchingSuccessor` while
   the job remains running, and the patched `finish` calls `endSupportCensus`
   before its `isRunning()`/`isAdvancing()` check. `endSupportCensus` then clears
   the simulator pointer, so later factor calls are uninstrumented and the
   report can look complete despite a truncated denominator. Freeze only after
   the owned job is quiescent, or mark an active-at-freeze report explicitly
   incomplete and prevent it from being used as evidence. A clear failure also
   loses the retry handle because `supportCensus` is nulled before the identity
   clear; retain a clear-pending owner without refreezing the report.

2. **The published sample still discards the actual `ipvt` prefix.**
   `finishFactor` uses `ipvt` to derive counts and then stores only those
   counts. The verifier JSON consequently cannot independently recount the
   replay or distinguish a real selected trace from a producer bug. For each
   successful selected factor, retain a defensive copy of the first `n`
   entries in `Sample` and serialize that bounded prefix. A failed factor must
   remain `initialOrderOnly` and must not publish a partial prefix as a valid
   replay. The synthetic native fixtures cannot supply this production oracle.

3. **`stableOriginalComponents` does not cover all support drift.** It compares
   only the connected-component partitions. An added or removed edge inside an
   already connected component leaves that flag true, and `Opportunity.exact`
   does not inspect `supportAdded` or `supportRemoved`. For example, an
   original 2-by-2 support `[[1,1],[1,0]]` and current support with `(1,1)`
   added have the same partition but different support. Add a separate
   `supportStable` fact and require it for any exact evidence tied to the
   unchanged support, or document that `exact` is only a current label-domain
   count and gate all optimization decisions on the drift counters. Add this
   internal-edge drift fixture; the existing cross-component fixture does not
   cover it.

4. **`completeSuccessfulEvidence` is weaker than its name suggests.** Its
   freeze condition checks successful capture, lifecycle/diagnostic errors,
   drops, and factor counts, but does not require
   `zeroPivotObservationAvailable`, every sample's `opportunity.exact`, stable
   support, or no sentinel risk. The existing unknown-fallback and support-drift
   fixtures can therefore produce `valid=true` and
   `completeSuccessfulEvidence=true` while their sample opportunity is not
   exact. Either rename this to an execution-capture fact or require all
   exactness and observation predicates before exposing it as complete evidence.
   Root acceptance must not use it alone.

5. **The lower/upper metric meaning needs to be explicit.**
   `localLowerCandidates` counts `i > k` before applying the current pivot row
   swap; `localUpperCandidates` likewise uses the selected row label before
   that swap. This is a pre-swap candidate-domain count. `localPivotCandidates`
   includes the `i == k` domain and therefore overlaps the lower domain; the
   fields must not be added to estimate post-swap scaling work, nonzero counts,
   or total LU work. Add this definition to the README/JSON notes or use names
   that expose the pre-swap scope. The collector's count intent can remain
   unchanged if that is the declared metric.

## Additional limits

* The scan and component/opportunity arithmetic still uses plain increments
  internally, while only aggregate updates use the saturating helper. Current
  caps make the bounded values safe, but the `counterOverflow` field does not
  cover those expressions if caps or scope change. Keep the cap proof explicit
  or route every exported count through the saturating arithmetic.
* The native contract is a useful independent graph oracle, but its `ipvt`
  arrays are synthetic. It does not test the wired CirSim exception path,
  pointer identity clearing, report serialization types, clear retry, an
  active-at-freeze cancellation, or a real selected LU `ipvt` prefix. It also
  does not assert that unknown fallback, support drift, or sentinel risk make
  `completeSuccessfulEvidence` false.
* The current overlay has no zero-pivot branch observation hook, so exact
  replay must remain false until root adds and validates an allowed observation
  source. The post-factor `1e-18` sentinel is conservative risk evidence and
  must not be reported as an observed fallback.

No sourcebase file was edited and no execution, build, browser, host, timing,
stage, commit, or push was performed for this review.
