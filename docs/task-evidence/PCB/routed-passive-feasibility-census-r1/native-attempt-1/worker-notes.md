# Routed passive feasibility census worker notes

Scope is limited to the new private `PcbRoutedPassiveFeasibilityCensusContractTest.java`
copy and this note. The source is based on the reviewed routed passive move proof
copy at source SHA `1914be70f35d7c65decd3a357d7dee9baaafd0b5f91c4ee3cae08a34c415c3ba`.
The final source SHA is
`9f8a1a27bd0fcc2b50be79c944d07999431d36825ed9d1b861ef88e9493f92a7`
(`102183` bytes).
The reviewed feasibility protocol is SHA
`6982e4e88969f599c3c859ed8bb9fc2e67946f24c9f5b0453c7e705231102d30`.

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
outer geometry call remain `GEOMETRY` stage failures. Callback output uses the
closed protocol enums `CENSUS_COMPLETE`, `PHYSICAL_VALIDATED`,
`PHYSICAL_REJECTED`, and `COMPLETE` with null completion reason/message. The
callback reports monotonic `nanoTime` elapsed milliseconds and zero when the
target callback is absent; the finite 31-move vector is checked before source
construction. Obsolete duplicate strict-construction evidence types and
appenders were removed. Geometry classification requires a plain
`IllegalStateException` with a nonempty known prefix; typed checkpoint failures
with a matching message propagate unchanged.

The courtyard fixture retains the independent generic escape regression and
adds the actual ceramic C5 package at pads `(3186,1134)` and `(3226,1134)` with
source courtyard `Rect(3171,1104,80,90)`. Its physically consistent U1-to-C5
route validates before a rigid ten-unit upward C5 move and then rejects the
unchanged horizontal segment with the frozen exact validator message. The
cancellation fixture enters a real `GenerationJob` work scope, reaches the
census trace-provenance helper, cancels the job, observes the typed checkpoint
failure, and verifies owner cleanup in `finally`.

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

Root integration review then replaced the post-cancel direct checkpoint call
with the same census traceProvenanceHash helper used before cancellation. This
forces the actual helper to honor the cancelled job; the typed failure identity,
abort count and finally scope cleanup expectations stay unchanged. Final root
integrated source SHA bce8fea648d97d2814dc480f049190228ef318624ee5e7d5329a263552096b4e (102182 bytes). Native still NOT RUN at this
pre-freeze checkpoint. The worker source above remains its exact handoff hash.
