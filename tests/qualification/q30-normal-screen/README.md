# Isolated Q30 normal-admission screen

This package applies only to an owned OS-temp export of application commit
`41f02f1b9f6c32cb8cf4ffbf9e8bd2bb3ea2476b`. It does not change shipping source,
the player catalog, or the unrelated scale implementation in this worktree.
The completed three-job evidence is in
[normal-admission-screen-r1](../../../docs/task-evidence/Q30/normal-admission-screen-r1/README.md).

## Ownership and execution

`baseline.patch` adds a generic, one-request eligibility authority at the normal
coordinator's start, current-owner and publication boundaries, plus scalar attempt
and active-operation telemetry. The additional Java sources live in `overlay/`.
The isolated module substitutes `Q30NormalScreenHarness` as its entry point. The
shipping module has no qualification entry point or player-accessible switch.

The authority exempts only the disabled catalog projection. It requires a fresh
existing coordinator, an ordinary MEDIUM four-candidate search request, asynchronous
execution, a completion callback, proof reuse enabled and no initial debug/verifier
scope. Its identity is bound to the exact simulator/request/job; closing it revokes
admission and releases retained job references. Ordinary catalog eligibility stays
false. Physical admission, policy, CircuitJS behavior, difficulty, diagnostic proof,
repair/completion, novelty, publication and cleanup retain their baseline owners.

The standard staged installer temporarily sets `developerVerifierRunning` even in
normal generation. The authority permits that internal flag only after job binding.
`troubleshootDebug` remains false, and the original asynchronous foreground clock,
continuation and watchdog run unchanged. There is no developer stepping loop,
private measurement request/cache, deadline extension or proof warming.

The harness captures the complete normal result, retires any published test owner,
restores its still-current predecessor snapshot and clears ordinary proof artifacts.
Scope loss and cancellation use normal coordinator cleanup. A successor identity
prevents restoration. Failure to clean up remains an infrastructure failure.

The overlay also registers one focused native suite in the exported verifier script.
It does not edit the working checkout's verifier script. `overlay-manifest.json`
records the source delta, dependencies and exact compiled-source hashes. Recorded
line-ending masks preserve mixed LF/CRLF bytes when replaying the patch on Windows.

## Reproduction

Python 3, Git, the recorded GWT 2.7.0 jars, JDK 8 and Microsoft Edge are required.
Use a new destination under OS temp, outside the checkout:

```powershell
python tests/qualification/q30-normal-screen/prepare.py <repository> <new-temp-root> <gwt-jar-directory>
& <new-temp-root>/app/scripts/build.ps1 -JavaHome <jdk8-directory>
python -m pip install --target <new-temp-root>/host/deps playwright==1.63.0
python <new-temp-root>/host/normal_screen_host.py <new-temp-root>/app <new-output-directory> <spec-json>
```

`prepare.py` verifies all exported baseline files and overlay inputs before reporting
success, then checks the prepared source against the measured compiled inputs.
The host records visible Edge identity, a fresh profile, selected resource hashes,
separate startup/case/teardown times, source integrity, process ownership and cleanup.
It reads DOM attributes; it does not invoke application controllers. Synthetic host
fixtures are provided under `host-fixtures/`.

The frozen `screen-plan.json` specifies the only screen jobs: seeds **7, 64, 13**,
in that order. The committed evidence already contains their single executions;
these instructions do not authorize extra screening repetitions. Each spec uses
`circuitjs.html?lang=en&tsjNormalMode=cold&tsjNormalSeed=<seed>`, state attribute
`data-tsj-q30-normal-state`, report attribute `data-tsj-q30-normal-report`,
`expectedPrefix: "SCREEN_DONE"`, `terminalPrefixes: ["SCREEN_DONE"]`, and a separate
150-second host timeout. The application's allowance remains 90,000/640/5,000.

Pre-screen modes are `unauthorized`, `cancel` and `scope-loss`. The latter two
intervene after at least one observed HYPOTHESES work unit and record the actual
stage/count. They never manually advance the generation clock or job. Each uses
its own fresh profile; they are lifecycle tests, not successful cold-screen rows.

## Receipt checks

```powershell
python tests/qualification/q30-normal-screen/q30_screen_reader.py --report <app-report> --plan tests/qualification/q30-normal-screen/screen-plan.json --host-record <host-result>
python -m unittest discover -s tests/qualification/q30-normal-screen -p test_q30_screen_reader.py -v
```

The reader pins the frozen plan bytes, request, candidate identity/order, shared
limits, foreground/cache state, work ledger, cleanup, physical/difficulty evidence
and the five repair/retest proofs with 37 solver samples each. Host transport status
is separate. Exit 0 requires application PASS and supplied host PASS; 1 records a
completed timeout/rejection or host transport problem; 2 records malformed evidence
or an infrastructure/programming failure. Synthetic tests are schema fixtures only.

Three passes establish this limited screen. Q30 remains BLOCKED / NOT ACCEPTED;
population reliability, broader scale and full acceptance are outside this package.
