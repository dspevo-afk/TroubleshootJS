# Private routed passive move proof worker notes

Scope is limited to the new private COPY source
`tests/contracts/PcbRoutedPassiveMoveProofContractTest.java`. No production PCB
source, census bridge, wrapper, reader, gate, or published selected layout was
changed by this worker. The source COPY is based on
`a816fdf186e2c89316bdd57baf60393299795a02` and has SHA-256
`35ce872ff743c0787b04efc41b5c6610d96a3242867e4ca6358d8a37fbb7c186`.

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

Validation status: **NOT RUN**. Per the bounded leaf assignment, no Java/GWT
build, native process, runtime contract, route trial, or test JVM was started.

Remaining validation is root-owned: run the four unit fixture groups, the
existing census thresholds, then the fixed seed-5/seed-2 native wrapper and
independent JSON reader. Those gates must establish the expected dynamic
selection and private candidate values; this note makes no physical feasibility,
shrinkage, acceptance, policy, or production-behavior claim.
