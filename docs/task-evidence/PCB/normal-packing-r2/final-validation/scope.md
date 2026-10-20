# Normal-packing r2 final validation

This packet is the final serial validation wrapper for the already assessed
normal-packing r2 candidate. It has three bounded phases: `contracts` runs the
fifteen selected current-source Java contract suites, `matrix` runs the
maintained `QuickPlayPhysicalMatrixContractTest` partition, and `build` runs
the JDK8/GWT5 OBF compile. The runner reads the exact suite and budget values
from `suites.json`; it does not select seeds, replace suites, or change a
budget.

Every phase requires the parent r2 `routes-assessment.json` to report
`assessmentStatus`, `integrityStatus`, `nativeStatus`, and `fitnessStatus` as
`PASS`. The candidate input audit runs immediately before and after the phase,
binding the frozen 1481-file source inventory, its 38 declared deltas, and
fourteen r2 gate owners. A phase refuses to overwrite any prior artifact.
The next phase requires the prior phase timing and birth-checked cleanup
record to pass. Cleanup is a separate `-VerifyCleanup` invocation; it only
revalidates the recorded parent/current process births and never terminates an
uncertain process.

Each phase records its launch births, full operation log, timing, post-audit,
and known-prefix sanitization result. Contracts and matrix also retain the
driver receipt. Timing reports include terminal cleanup in the operation
boundary and use `NOT_SEPARATELY_MEASURED` for cleanup duration. The only
sanitized private prefixes are the selected main worktree and selected JDK;
published output does not claim that a phase passed until its exit, post-audit,
receipt, and cleanup evidence are independently present.

This leaf owns the wrapper and offline checker only. Native contract execution,
the physical matrix, the production compile, route assessment, screenshots,
and publication remain root-owned and are **NOT RUN** here.
