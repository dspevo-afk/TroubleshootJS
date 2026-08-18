# Task 37 Correction Report — NPN Quick Play Seed/Fault Reachability

Date: 2026-08-18

Original Task 37 commit named by the request: `7194bd19a2b0eb1ca95eba9e4e457438a277bb9`.

Actual correction baseline in this checkout: `94a3141aa735f847e5f97d018eff6aadfd0425aa`
(`Task 37: fix NPN validation state and meter stability`).

Scope: Task 37 only. Task 38/NMOS was not started.

## Summary and decision

This correction closes the reviewed Quick Play coverage gap. The family-specific
normal-player boundary is preserved: legacy families continue to use `{0, 2,
3}`, while `NPN_LOW_SIDE_SWITCH` uses `{0, 1, 2, 3}`. The correction adds a
permanent verifier canary and expands the browser harness so ordinary Quick Play
can reach and validate all four NPN faults, including the seed-1 C-E-short
stuck-active scenario.

The NPN generator remains the owner of the seed-to-voltage and seed-to-fault
behavior. The Quick Play verifier reaches it through the ordinary
`QuickPlaySelector`/`QuickPlayFamilyRegistry` path; it does not force a fault,
rewrite a generated fault, or expose fault metadata to players.

Primary architect result: `FINAL PASS`, subject to the single local commit
below. No push is performed because the current Task 37 completion protocol
requires stopping after the commit.

## Architectural correction

`QuickPlayDeveloperVerifier` now permanently checks:

- arbitrary injected random values remain inside each family’s validated seed
  envelope;
- legacy family envelopes remain `{0, 2, 3}`;
- NPN’s ordinary envelope is `{0, 1, 2, 3}`;
- ordinary NPN generation maps seeds 0, 1, 2, and 3 to
  `TRANSISTOR_CE_OPEN`, `TRANSISTOR_CE_SHORT`, `BASE_RESISTOR_OPEN`, and
  `LOAD_PATH_OPEN` respectively;
- the generated `LOAD_VIN_INPUT` physical nameplates are 9 V, 12 V, 5 V, and
  9 V for those seeds; and
- the diode developer-only short remains excluded from normal Quick Play.

The family-specific registry boundary already existed in the prior Task 37
player-truth correction and was not replaced with a global seed change. This
correction verifies that boundary and extends `scripts/verify-browser.ps1` to
use seeds 0, 1, 2, and 3 by default for both the NPN electrical matrix and the
ordinary Quick Play NPN routes. A caller-supplied `-Seeds` list remains
supported.

For ordinary Quick Play seed 1, the verifier checks the live CircuitJS state:
the control is low, load current is materially present, and collector voltage
is low. It then removes and replaces Q1 through the normal physical-part path,
proves healthy ON/OFF behavior through the real solver-backed family state, and
finishes through the generic repair/completion boundary.

## Validation evidence

### Build and static checks

- JDK 8/GWT production build with
  `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`
  passed all five permutations, compilation, and linking.
- `scripts/verify-renderer-boundary.ps1`: `PASS:renderer-provider-boundary`.
- `scripts/verify-browser.ps1` PowerShell AST parse: `PASS:verify-browser-parser`.
- `git diff --check` passed before staging; the cached form is run again during
  completion.

### NPN electrical and Quick Play coverage

- The expanded forced validation matrix executed all 16 seed/fault combinations:
  seeds 0, 1, 2, and 3 crossed with all four NPN faults. Every route returned
  `PASS:npn` through the CircuitJS board and validators.
- The matrix reported the expected load supply variants: seed 0 `+9V`, seed 1
  `+12V`, seed 2 `+5V`, and seed 3 `+9V`; the control supply remained `+5V`.
- Ordinary Quick Play developer routes for NPN seeds 0, 1, 2, and 3 each
  returned `PASS:quick-play` with
  `unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated`.
  These routes used the normal selector/generator boundary and did not pass a
  forced fault.
- The ordinary seed-1 route selected the C-E-short behavior and exercised the
  existing stuck-active complaint and live low-control/high-load-current
  compatibility proof. The corrected replacement completed through generic
  `Finish Job`.

### Visible in-app Browser acceptance

Using the visible local in-app Browser and real player interaction, a natural
normal-player Quick Play session reached the NPN seed-1 C-E-short presentation.
The visible board showed the +12 V load nameplate, +5 V control nameplate, the
active controlled load, and the complaint:
`The controlled load stays active when control is low.`

The visible workflow then powered the board off, selected and removed Q1,
installed the generic NPN replacement from the parts tray, powered the board
back on, and used the normal `Finish Job` action. The application transitioned
to a fresh challenge session after successful completion. The visible browser
does not expose a direct player control-toggle widget; the low-control/live
stuck-active electrical condition and restored ON/OFF proof are therefore
asserted by the solver-backed verifier at the generic completion boundary, not
by hidden player UI state.

Architecture and PCB layout routes were rechecked in the visible application
with `PASS:architecture` and `PASS:layout`. The existing Task 37 regression
evidence also passed for RC/stored-energy, LED, diode, and parallel families;
those areas were not changed by this correction. A later diode-route read hit
the known in-app CDP deadline and is recorded as a harness interruption rather
than a false product pass.

The standalone PowerShell Edge harness was not reported as passing: its Edge
process query was blocked by the documented WMI/CIM `Access denied` condition
before route execution. The visible in-app Browser was used for the required
player-facing and route validation instead.

## Files changed

- `AGENTS.md` — added the requested Parallel Subagent Policy; the user-owned
  `.codex/config.toml` max-concurrent-subagent change was left unstaged.
- `src/com/lushprojects/circuitjs1/client/QuickPlayDeveloperVerifier.java` —
  family-envelope, ordinary NPN fault/voltage reachability, seed-1 live
  scenario, and generic repair/Finish Job canaries.
- `scripts/verify-browser.ps1` — default NPN matrix and ordinary Quick Play
  route coverage expanded to seeds 0, 1, 2, and 3.
- `docs/ARCHITECTURE.md` — documents the family-specific envelope and ordinary
  NPN reachability proof boundary.
- `docs/ROADMAP.md` — records the accepted 16-case Task 37 correction and keeps
  Task 38 as the next eligible, unstarted milestone.
- `docs/CODEX_TASK_REPORT.md` — replaced with this correction report.

No screenshot or evidence artifact is added to the commit.

## Review protocol

- Bounded coder Fermat implemented the verifier and browser-harness correction,
  reported the build and route evidence, and did not push.
- Fresh read-only reviewer Hilbert inspected the actual diff and execution
  paths, specifically checking ordinary (not forced) NPN reachability, the
  family-specific seed boundary, the seed-1 live scenario, generic completion,
  and regression scope. Result: `PASS`.
- The reviewer was explicitly told that the max-subagent setting was changed
  by the user and was not a subagent edit; that `.codex/config.toml` change is
  intentionally left alone and unstaged.
- Primary architect review: one normal review round, followed by independent
  final inspection of the implementation, documentation, validation evidence,
  and intended file list. Result: `FINAL PASS`.
- Escalation architect: not required.

## Known limitations

- The standalone Edge/PowerShell harness remains unavailable under the current
  WMI/CIM permissions; this is recorded as unavailable, not passing.
- The in-app Browser occasionally reports a CDP frame-tree/deadline timeout
  while a route is still settling. Stabilized reads passed for the required
  Quick Play seed routes; the isolated later diode read is retained as a
  harness limitation.
- Existing compact TO-92 renderer physical-fidelity debt remains outside this
  bounded Quick Play correction.

## Final boundary

Only Task 37 correction work is included. Task 38/NMOS, PNP, PMOS, and other
future-family work was not started.

Commit message:

`Task 37: fix NPN quick play fault coverage`
