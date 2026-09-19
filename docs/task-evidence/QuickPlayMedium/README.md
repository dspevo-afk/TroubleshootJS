# Procedural Medium Quick Play follow-up

Date: 2026-09-17
Branch: `codex/task43p-final-recovery`
Base inspected: `8a807c4c4eaac4979b6d05d6edd22b95710d9ce6`

## Defect

COMPOSED_CONTROLLED_INDICATOR/MEDIUM looked procedural in the UI but normal launches collapsed fresh entropy to seeds 0 or 3. Both frozen seeds selected the same channel-A RLOAD-open fault, and normal construction used ControlledIndicatorPcbLayoutFactory, whose role/channel coordinates and copper lanes were deliberately fixed. Repeated Medium sessions therefore produced effectively the same board.

## Current correction

- Medium normal launches retain full signed-64-bit entropy and use the bounded four-candidate admission policy.
- Composition identity is preserved when candidate ordinal seeds are derived.
- PhysicalConstructionMaterializer supplies semantic power-input, channel-A, channel-B and power-indicator placement regions plus edge-only connector demands.
- BoundedGeneratedBoardAssembler sends the controlled board through SeededPcbLayoutGenerator; the authored fixed full-board factory is no longer a live construction dependency.
- Generic connector demands may choose a deterministic left/right edge and along-edge position. RB15 keeps its functional input/output edge roles but also varies along-edge position, preserving its qualified single-layer routing envelope.
- Exact accepted seeds remain deterministic.

## Diversity evidence

Predeclared Medium regression seeds: 0, 1, 2, 3, 17, 42, 101, -1, Long.MIN_VALUE, Long.MAX_VALUE, -9007199254740993, 9007199254740993. The contract requires all twelve to produce distinct complete PCB geometry fingerprints, at least eight coarse 100-unit macro placement groups, at least four connector edge/position layouts, at least four distinct fault identities, and exact same-seed geometry/fault replay. All checks passed.

## Validation

- ControlledIndicatorAssemblyContractTest: PASS, 254 assertions.
- QuickPlayGateContractTest: PASS, 26,913 assertions.
- P03PlacementContractTest: PASS, 864,267 assertions.
- Q15ControlBoardContractTest: PASS, 6,863 assertions.
- U04SessionContractTest: PASS, 2,827 assertions.
- A10GenerationContractTest: PASS, 190 assertions.
- scripts/build.ps1 with Temurin JDK8, OBF style: PASS; five GWT permutations compiled and linked.

## Limits

This does not claim arbitrary circuits, every family, Q30/Q60/Q100, or per-seed success. The normal search remains bounded to four candidates and may reject unsupported/exhausted candidates. A later QuickPlayContractTest rerun was NOT RUN because the sole Lobu shell worker was repeatedly occupied by a separate concurrent Lobu-daemon setup session after the production build had already passed. Production UI entropy transport was not changed by this patch.
