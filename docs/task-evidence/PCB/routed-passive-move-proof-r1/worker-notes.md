# Private routed passive move proof worker notes

Scope is limited to the new private COPY source
`tests/contracts/PcbRoutedPassiveMoveProofContractTest.java`. No production PCB
source, census bridge, wrapper, reader, gate, or published selected layout was
changed by this worker. The source COPY is based on
`a816fdf186e2c89316bdd57baf60393299795a02` and has SHA-256
`1914be70f35d7c65decd3a357d7dee9baaafd0b5f91c4ee3cae08a34c415c3ba`.

The contract contains exactly these four named fixture groups:

- `endpointStartEndMetadataAndDelta`
- `rejectInvalidIncidenceOrEndpoint`
- `footprintPadsUnrelatedGeometryAndSourceImmutability`
- `observerOwnershipNullAndFailureCleanup`

Static review confirms the private path reconstructs finalized footprints in
board pad order, translates only the selected component to a new absolute
origin, rewrites only explicit incident first/last trace vertices, preserves
route and recovery statistics, marks generation metadata
`NOT_ASSIGNED_PRIVATE_CANDIDATE`, runs the three full validators once, and
checks independent source/candidate conductor reachability and trace
provenance. It captures each conductor graph and pristine snapshot once,
checks all sorted pad pairs, and calls `GenerationWorkScope.check()` through
the construction, footprint, trace, validator, and pair loops. The file is LF
terminated and has no trailing whitespace.

Validation status for this corrected source: **NOT RUN**. The root-owned first
native unit attempt on the preceding source is preserved as **FAIL** at Java 7
compilation: 12 captured local-capture errors at the three negative fixture
callbacks (lines 923, 933, and 943 in that attempt). The failure and 29-gate
source evidence are preserved in the native attempt record; no route receipt
was produced, and the unit-driver cleanup was PASS. The source-7 corrected
native unit then passed in 42.3943654 seconds with 40 old and 24 new
assertions across 6 and 4 fixture groups. The subsequent actual route attempt
is preserved as **FAIL** after 55.5587094 seconds: `TreeSet.contains(null)`
raised a `NullPointerException` in the prior `copyLabel` at line 550 before
the full validators were reached. All 49 frozen/source/phase files and the
failure evidence are preserved in the second native attempt; both drivers
reported cleanup PASS. This null-safety edit has no fresh runtime or native
result.

Remaining validation is root-owned: run the four unit fixture groups, the
existing census thresholds, then the fixed seed-5/seed-2 native wrapper and
independent JSON reader after resealing. Those gates must establish the
expected dynamic selection and private candidate values; this note makes no
physical feasibility, shrinkage, acceptance, policy, or production-behavior
claim.
