# Normal packing R2 metric worker notes

Base `2a73b949a02de718bd968c2920ba6045d7406c5a`, branch
`codex/q60-faulted-profile-counts`. This worker owns only
`src/com/lushprojects/circuitjs1/client/PcbPackingMetrics.java`,
`tests/contracts/PcbPackingMetricsContractTest.java`, and this note. R1's
metric failures and frozen evidence are unchanged.

Production exposes immutable `area`, `copper`, `vias`, and `protectedVoid`
metrics and rejects nonpositive area or negative metric values. Area is final
outline width times height; copper is the long sum of
Manhattan centerline segment lengths over every trace; vias count only VIA
holes; protected void is the largest sampled empty rectangle's area. The
comparator admits a candidate only when its area is strictly smaller and its
copper, via count, and protected void are each no greater than the incumbent.

The production GRID10 sampler preserves the independent
`PcbFunctionalGroupingContractTest.reportWhitespace` mark rounding and
occupancy sources: package access envelopes, trace strokes guarded by each
trace width and the full profile maximum on both faces, every hole with the
full-profile hole guard, silkscreen bounds, and strips between declared domain
courtyards. The histogram helper returns cell count and accepts only a
dimension-matched row-major array. Sampling is limited to 250000 cells;
unsupported cell counts throw `PcbPackingMetrics.Unavailable`, while invalid
inputs and geometry failures propagate. Checkpoints cover entry, rows,
256-cell mark chunks, trace ordering, and copper accumulation.

The seven named groups are `histogramLiteralCases`,
`literalRealLedGeometry`, `actualCopperViaAndSilkscreen`,
`declaredIsolationSampling`, `strictMetricComparator`,
`unsupportedGridAndInvalidGeometry`, and `cancellationAndScopeCleanup`. The
source now has 32 expected contract assertions after root review and emits the root-registered
prefix `PASS: PCB packing metrics contracts assertions=<N> fixtures=<N>`.
The LED fixture uses the declared physical package and literal local access
envelope `(-19,-36,123,171)` on a 300x300 outline; its expected metrics are
area 90000, copper 0, vias 0, and protected void 24000. The declared 80-unit
isolation-strip pair has literal protected-void metrics 20000 with the strip
and 60000 without it. Both are also compared against the unchanged independent
sampler. Additional actual trace, via, non-plated-hole, and label objects check
their metric and occupancy contributions against that oracle.

Validation was static source/API inspection only. The new Java contract,
native checks, and builds were NOT RUN; root owns registration and execution.
No runtime evidence or R1 golden changes were created by the leaf.

Root integration: native attempt2 failed the original LED literal. Its complete
source/gates/native output and the predecessor note remain under native-attempt-2.
The root derived the full selection/courtyard/escape envelope from PhysicalPackages
before repairing the expectations: selection(1,7,78,82), courtyard(6,4,78,92),
escape extent through y115, then access20/header20. The occupied single-LED grid
is columns8..21/rows6..24; its largest free strip is8x30cells. The two-domain
fixture's middle strip is30x20cells; the barrier blocks columns27..36 and leaves
10x20cells on each side. Neither dual-fixture result had been observed at this
repair. Two literal envelope/domain assertions now bind that derivation.
Fresh native validation is pending; no failing result is called PASS.

Root final native integration: the unchanged independent sampler caught and
qualified the literal-envelope correction. Attempt5 then exposed NMOS pad sorting;
production now preserves the declared G,D,S order. The added TO92 GDS/BCE geometry
renaming fixture leaves graph and layout unchanged. Current metric suite PASS36
assertions/eight groups, and production-versus-independent sampling agrees on all44
actual normal boards. The four fixed sampler reports remain unchanged in shape.
The earlier leaf NOT RUN and predecessor32-assertion note describe their original
handoff; current root phase receipts and candidate-inputs.json bind final bytes.
