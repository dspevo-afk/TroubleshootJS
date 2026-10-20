# Routed passive feasibility census worker notes

Scope is limited to the new private `PcbRoutedPassiveFeasibilityCensusContractTest.java`
copy and this note. The source is based on the reviewed routed passive move proof
copy at source SHA `1914be70f35d7c65decd3a357d7dee9baaafd0b5f91c4ee3cae08a34c415c3ba`.
The final source SHA is
`ae0251cc855a423c8c633ad7d9116f9f4608b8fc2300a829f971f8080f2cc622`
(`106426` bytes).
The reviewed feasibility protocol is SHA
`fe580517a9c104ef9b22b3961826e4d6ca49c82b509a88a7bf8484baa07239e1`.

The implementation installs the existing census feasibility observer, snapshots
the original source once, reconstructs each canonical move independently, and
records the ordered physical census schema. Candidate construction preserves
component, pad, label, region, hole, trace metadata, route statistics, recovery
statistics, and the fixed board outline. It rewrites only explicit incident trace
endpoints, validates known nets for every plated source and candidate hole, runs
the actual placement, geometry, and explicit two-layer validators, and performs
the independent 9045-pair reachability and trace provenance checks for admitted
rows. Unknown errors, malformed input, proof mismatch, budget exhaustion, and
cancellation propagate. The unchanged drill/land clearance invariant is not
classified as a move rejection; nested two-layer and quality failures from the
outer geometry call remain `GEOMETRY` stage failures.

The six unit fixture groups are:

1. `endpointStartEndMetadataAndDelta`
2. `rejectInvalidIncidenceOrEndpoint`
3. `footprintPadsUnrelatedGeometryAndSourceImmutability`
4. `observerOwnershipNullAndFailureCleanup`
5. `courtyardRegression`
6. `stageClassificationAndFailurePropagation`

Static checks completed: source SHA and byte count captured; one LF at EOF and no
trailing whitespace; balanced Java delimiters; no stale move-proof bridge symbols;
no Java 8-only lambda, stream, `var`, try-with-resources, or switch-arrow syntax;
six fixture calls and six fixture markers; protocol SHA confirmed. No native unit,
route wrapper, schema reader, GWT/player build, full matrix, or unseen/feasible
move gate was run in this worker. Runtime timing and census outcome claims remain
NOT RUN pending root-owned integration and native execution.
