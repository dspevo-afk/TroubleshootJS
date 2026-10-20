# Normal packing phase runner notes

Status: **IMPLEMENTED, STATIC/TRANSPORT REVIEW ONLY**.  This leaf owns the
normal-packing phase runner, process-birth cleanup check, known-prefix log
sanitizer, suite manifest transport, 12-case transport canary, and these notes.
Native execution, Java/build execution, source sealing, assessment, reader,
freeze, and publication remain **NOT RUN** and are root-owned.

The runner reads the exact protocol scope
`Actual active11 normal random generation; fixed44 baseline/current packing comparison`
and accepts `baseline`, `unit`, or `routes` as its phase.  `baseline` resolves
the packet's `baseline-local-state.json` scratch owner and validates the
`tsj-normal-packing-baseline-` prefix.  `unit` and `routes` resolve the main
worktree.  Baseline and routes consume the eight-row `routes-suites.json`
manifest; unit consumes the two-row `unit-suites.json` manifest.  The runner
keeps the protocol's 60000 millisecond JVM and per-case limits.

Before driver capture and again after the native invocation, the runner calls
the root-owned `inputs.py` with `audit-baseline` for baseline or
`audit-candidate` for unit/routes.  The pre-audit blocks execution on failure.
The post-audit runs in cleanup and cannot replace an earlier native failure.
The runner refuses existing phase logs, receipts, process records, timing,
cleanup, or sanitization artifacts before starting.  Timing retains the
published fields and records the exact protocol scope and phase.

The process record captures both the launching PowerShell parent and the
current driver PID, UTC start ticks, and executable name before the phase
starts.  `check_process_cleanup.ps1` revalidates each PID birth and executable,
reports `ABSENT`, `DIFFERENT_PROCESS`, `STILL_RUNNING`, or `UNKNOWN`, and never
terminates a process.  Unknown or still-running births fail cleanup; absence
and a different birth are retained as explicit evidence.

`Get-NormalPackingSuiteNames` preserves the existing strict nested two-value
manifest contract, canonical class-name validation, nonempty single-line
markers, and unique class/marker prefixes.  The companion checker exercises
the real two-row unit and eight-row route manifests plus ten malformed or
duplicate negative manifests, for 12 transport cases total.  The canary does
not run Java suites.

`sanitize.py` accepts the same three phases and requires a passing timing and
birth-cleanup record.  It replaces only the baseline COPY, worktree, and
optional JDK prefixes in phase text and receipts.  It is idempotent and leaves
logs byte-for-byte unchanged when no known prefix is present; private absolute
paths are not intended to remain in published phase logs.

Static checks completed for this leaf:

* phase runner source contains all three phases, exact protocol scope, baseline
  scratch selection, main-worktree selection, prerequisite gates, pre/post
  audit calls, no-overwrite checks, and dual driver birth capture;
* cleanup source revalidates PID/start ticks/executable, reports absence, and
  contains no termination operation;
* suite transport validates the two/eight manifests and 12 transport cases;
* sanitizer source is phase-aware, uses only the three declared prefix classes,
  and preserves unchanged output;
* all owned text files have one LF EOF and no trailing whitespace.

The real PowerShell transport canary ran successfully with 12 cases: the
two-row unit manifest, the eight-row route manifest, and ten malformed or
duplicate negative manifests.  Current owned fingerprints are:

* `run_native.ps1` — 5630 bytes — `57a4dd7df0b3b3c3490310adb6f978616e64027052265d9761b00e589e57c6b2`
* `check_process_cleanup.ps1` — 2159 bytes — `0414ab2fdde713c008064ffcd3e7ecce6573c92f44ca0e0390a0b8a68183190c`
* `sanitize.py` — 1985 bytes — `dbcc2b98dd1db4a31ae00b433d938a1b9084b8b255fc96ac915ca38542d949db`
* `suite_transport.ps1` — 1118 bytes — `ffe119fadc13a9f6a6263dfb8b202ee475a020c3bdbcec31a8c20e6ef1c53371`
* `check_suite_transport.ps1` — 3104 bytes — `e464ca10c148e51b5de1fc0622d55d67ac9c1afe29c43c1516a9745f9cf7c633`
* `suite-transport-canaries.json` — 1527 bytes — `ef87d41e6f7841467ba0ddc826c3add6947f62f1409536ffb10b6393e9e63ac0`

No native driver, Java/JDK build, contract suite, route phase, baseline/current
assessment, browser/player flow, screenshot, or publication command was run by
this leaf.  Root must provide `inputs.py`, frozen input seals, prerequisites,
and final phase evidence before using the runner.
