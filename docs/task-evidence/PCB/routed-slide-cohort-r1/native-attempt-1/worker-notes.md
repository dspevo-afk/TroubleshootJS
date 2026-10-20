# Routed slide cohort worker notes

Status: the leaf-owned COPY sources and the cohort unit are ready for root
review. Native execution, build, transport, reader, freeze and publication
gates are **NOT RUN** by this worker. Root owns integration and publication.

The implementation is aligned to protocol SHA
f65e822905cb8b525305b4025d404d8dababde09c41ef992c2778462a85b686d. It keeps
version 15, density 14, the selected-after-edge-trim phase, the seven fixed
two-root partitions, the four-step and 128-evaluation limits, and the
published summary field order. The original two-root `observe` path retains
its literal seed frame checks and `PCB_ROUTED_SLIDE_DESCENT_CASE` prefix.

The descent source has one reversible adapter change: the original
`observe(seed,label)` delegates to a private `(seed,label,informedCohort)`
overload, and `observeInformedCohort` selects that overload. The Recorder holds
the boolean per instance. A false value calls the published literal frame
verifier and retains the old summary prefix; a true value calls the new generic
frame adapter and selects `PCB_ROUTED_SLIDE_COHORT_CASE`. The shared descent
driver, RuntimeBackend, metrics, search order, proof, comparator, caps and
termination code were left unchanged.

The compaction source adds one erasable adapter block. It checks generic
accepted-frame invariants: density 14, required P07 two-layer routing, current
layout algorithm, the descriptor's signed placement/routing seeds, six
assigned placement attempts, bounded positive routing-order work, assigned
routing statistics, selected attempt/candidate correspondence, nonnegative
plan metadata, source-declared region membership, and complete board/layout
component and pad identity. The route receipt uses the existing
`SupportedEnvelope.MAX_ROUTE_ATTEMPTS` bound because the layout getter stores
aggregate routing-order work (the sealed baselines record routing-order values
above the six route-candidate count). It reuses the existing reframe and region owners;
it does not introduce a seed-specific frame or a second physical validator.
The same block exposes the existing literal LED package constructor to the
cohort unit, so the unit exercises the adapter against package-backed geometry.

The three named unit fixture groups are:

* `genericFrameAndMetadata`
* `genericFrameRejectionAndImmutability`
* `cohortObserverCleanup`

The first uses an independent two-package P07/density14 literal board and
checks outline, component/pad/region inventory, plan metadata, assigned
generation receipt and source immutability. The second rejects independent
outline, coordinate, selected-candidate, region-label and generation-owner
mutations while checking the original layout and plan remain unchanged. The
third installs a sentinel observer, invokes the actual informed entrypoint
with an invalid fixed-root value, asserts the typed pre-generation rejection,
and verifies the sentinel is restored without callback inspection.

The inverse edit recipe is: restore the descent source from the published
descent source, remove `COHORT_SUMMARY_PREFIX`, remove
`observeInformedCohort` and the private boolean overload, restore the original
`Recorder(seed,label)` constructor and callback branch, and restore the direct
`SUMMARY_PREFIX` append. Restore the compaction source from the published
compaction source and remove the added generic-frame adapter and
`literalLedFootprintForCohort` wrapper. Delete this note and the new cohort
unit. No production source, registry, wrapper or prior packet file was edited.

Static checks completed:

* The three owned Java files have one LF EOF, no trailing whitespace, and
  balanced parentheses; no Java-8-only lambda or method-reference syntax was
  added.
* A no-index diff against the published descent source shows only the cohort
  prefix, entrypoint/Recorder branch and generic-adapter call. A no-index diff
  against the published compaction source shows only the generic adapter and
  literal-package exposure.
* The new unit has exactly three fixture names and the protocol evaluator
  marker `selectedRoutedSlideCohort`.
* No javac, native driver, route wrapper, PowerShell transport, independent
  reader, GWT, production build, browser/player check or full qualification
  matrix was run.

Current leaf-owned source fingerprints:

* `tests/contracts/PcbRoutedSlideCompactionContractTest.java` — 77,403 bytes —
  `ef0aba431ae8a07e4f4fb255a065fe24c6aad8ef560d6da3a48c594afafac4d9`
* `tests/contracts/PcbRoutedSlideDescentContractTest.java` — 52,623 bytes —
  `4b8dfedd2b6a536a67f5fb818b305471d0146ab16d86c3dc16369e6244b02c2a`
* `tests/contracts/PcbRoutedSlideCohortContractTest.java` — 12,457 bytes —
  `d0bc3fb8dadeb8d4df472f88f97355d947f783cdaba44b3f62301d96744e09ae`

These are source-only pre-freeze values. Root integration or protocol/reader
changes require fresh fingerprints and gate records.
