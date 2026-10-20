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
source has 30 expected contract assertions and emits the root-registered
prefix `PASS: PCB packing metrics contracts assertions=<N> fixtures=<N>`.
The LED fixture uses the declared physical package and literal local access
envelope `(-10,-32,108,167)` on a 300x300 outline; its expected metrics are
area 90000, copper 0, vias 0, and protected void 27000. The declared 80-unit
isolation-strip pair has literal protected-void metrics 22000 with the strip
and 60800 without it. Both are also compared against the unchanged independent
sampler. Additional actual trace, via, non-plated-hole, and label objects check
their metric and occupancy contributions against that oracle.

Validation was static source/API inspection only. The new Java contract,
native checks, and builds were NOT RUN; root owns registration and execution.
No runtime evidence or R1 golden changes were created.
