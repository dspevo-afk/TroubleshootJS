# QA route fixture worker handoff

Worker scope is implemented only in the accepted isolated source copy `<owned accepted-source copy>`, in the generator adapter and three contract files listed below. Main-source publication, registry/readers, freeze, and all runtime work remain with root.

## Design and scope

`SeededPcbLayoutGenerator.routeSuppliedMediumTwoLayerPlan` is package-private and accepts a supplied placement plan plus its stable placement-attempt identity. It requires `plan.candidate == placementAttempt`, creates an ordinary medium `Session` with exactly one `PlacementCandidate`, starts at existing P07 stage 2/attempt 1, and lets the existing session routing and finalization path run. It does not call the six-plan placement generator, rank candidates, or run P05. Reported candidate/evaluation counters describe this one supplied fixture plan; `rankedPlacementAttempts` is empty and the receipt labels the order as diagnostic, not historical.

The shared contract makes one real planner call per JVM for Rb56 root 42, interpretation version 15, density version 14, requested placement attempt 1 (internal algorithm 0). It pins the original-plan fingerprint and exact layout/routing seed streams, validates either supplied input plan before entering the route path, and keeps source-plan/live-graph invariance checks after routing. TRANSLATED changes only QA by (-300,-30), from (1690,1580) to (1390,1550); it checks all other 53 footprint fingerprints, QA pad/net/component identities and translated pad/probe geometry, and unchanged plan metadata. Original P07 mismatch/rejection is a failure; translated typed physical rejection may produce a completed diagnostic record marked `REJECTED`. Unexpected errors propagate.

The original success case pins the accepted final geometry SHA and exact parts, pads, area, width, height, protected-empty-rectangle, copper-length, vias, and route-quality values. The existing `PcbFunctionalGroupingContractTest.reportWhitespace` path and its seed-42/version-15 void ceiling (`<327600`) are retained unchanged; a successful route that violates that guard is a test failure. P07 caps are emitted from existing constants and asserted unchanged: orderings 3, total expansions 1,000,000, branch expansions 100,000, grid cells 250,000, work quantum 32,768. Route elapsed time is existing `statistics.routeMillis` and explicitly excludes finalization; the root runner owns whole-native-phase elapsed time including compile, both JVMs and terminal cleanup; per-JVM elapsed is not measured.

## Receipt schema

Each contract emits `PCB_QA_ROUTE_CASE { ... }` with schema `pcb-qa-route-comparison-r1` and these top-level fields:

- `schema`, `variant`, `physicalStatus` (`SUCCESS` or `REJECTED`)
- `source`: seed/version/density, accepted-case source SHA, source plan and live-graph SHA before/after and unchanged booleans, exact layout/routing stream seeds
- `placement`: requested attempt, internal algorithm, planner call count, original and supplied plan SHA before/after, identity/unchanged booleans, one supplied candidate/attempt, existing-origin sequence, diagnostic candidate-order meaning, QA-only flags, other-53 and QA binding booleans, unchanged metadata, full `qaBefore` and `qaAfter` component/package/pose/geometry and pad/net/terminal/bounds/escape bindings
- `route`: existing policy/identity, attempt and route request, selected route/placement attempt, P05/P07 counts, route outcomes, orderings/expansions and placement counts, empty ranked-attempt list, phase elapsed value/meaning, and existing route limits
- `geometry`: `null` for typed rejection, otherwise `parts`, `pads`, `area`, `width`, `height`, `largestProtectedEmptyRectangleArea`, `copperManhattanLength`, `vias`, `geometrySha256`, and `selectedQualityMilli`
- `rejectionReason`, `comparisonScope`

The `PASS: PCB QA route original/translated contracts: N assertions` terminal marker means the diagnostic contract completed; the JSON `physicalStatus` explicitly describes physical routing outcome.

## Static review and validation status

Static source review only. No compilation, Java contract execution, native command, browser, or process operation was run by this worker; both runtime cases are `NOT RUN` here and remain root-owned. The independent JSON reader and any source-copy freeze/publication are also root-owned. No generation limits, policy behavior, or expected outcomes were changed or exercised.

## File SHA-256

- `src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java`: `85B9F70EDE596D9BBDEEFD35DD72C48CE52D11B9CBE53433EECFFFF60A19664B`
- `tests/contracts/PcbQaRouteComparisonContractTest.java`: `F345B0B4D6DC513440D83C00C4CD38A4AE8A697DE499133415845FC46116BFB0`
- `tests/contracts/PcbQaRouteOriginalContractTest.java`: `8E2894AA5FA000C893ED1D182A63B65F56129B36EE878FC6E5048DEAD18F086B`
- `tests/contracts/PcbQaRouteTranslatedContractTest.java`: `067BEC1E394D951204785753DED3E968E5A3FB817B69E80CA9C9B7762474F8B4`
