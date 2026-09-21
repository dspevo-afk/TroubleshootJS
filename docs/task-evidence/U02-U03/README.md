# U02/U03 repair evidence

This repair requalified the U02 DMM and U03 scope contracts after independent
review found an ideal-voltmeter bypass, stale AC capture, AC bandwidth ambiguity,
and scope presentation defects. A post-review follow-up also fixed retained DC
reacquisition, the finite-window false rejection of a clean 199 Hz sine, and
the irregular-sample false rejection of clean 199/200 Hz sines under the
declared 200 Hz policy. A fresh JDK8 production build compiled all five
permutations before the final browser check.

## Compiled-browser evidence

The checks used a task-owned production preview and a debug-gated temporal
fixture. The fixture first safely isolates the normal generated LED board, then
adds one actual CircuitJS source between its existing physical VIN/GND endpoints.
That source is outside the logical board model and cannot be reached by a normal
player route. It exercises the same player meter, probe, observation, and canvas
paths rather than injecting a display value. Its normal route immediately
restores the exact pre-fixture graph, power, probes, instrument state and solver
ownership. The separately debug-gated `tsjTemporalVisualHold=true` route holds
the already-validated visual state for at most 15 seconds, then uses that same
cleanup path so real compiled-browser controls can be exercised.

- [periodic-ac-rms-compiled.png](periodic-ac-rms-compiled.png) shows visible
  AC mode after a real 60 Hz CircuitJS source changes from 3 V peak to 6 V peak
  without moving either probe. The displayed reading is `4.206 V RMS`; the
  report recorded initial `2.103076641350908 V RMS`, updated
  `4.2066368715920035 V RMS`, and an 8,192-sample bounded capture.
- [periodic-scope-compiled.png](periodic-scope-compiled.png) shows the periodic
  solver-time trace and `SCOPE: 60 Hz`. The report's timestamp-derived frequency
  was `60.000000311783815 Hz`.
- [periodic-scope-controls-compiled.png](periodic-scope-controls-compiled.png)
  shows the real trace with visible scale and trigger controls. The fresh held
  browser interaction clicked the time control twice, voltage control once and
  trigger control once: `T 1 ms/div` / `V 1 V/div` / `TRIG ↑` became
  `T 20 ms/div` / `V 5 V/div` / `TRIG ↓`; the coarser capture explicitly
  reported `SCOPE: WINDOW` rather than inventing a trace.
- [dc-trace-no-frequency-compiled.png](dc-trace-no-frequency-compiled.png)
  shows a real horizontal 2.4 V DC trace with `SCOPE: NO SIGNAL`, not an absent
  trace.
- [transient-trace-no-frequency-compiled.png](transient-trace-no-frequency-compiled.png)
  shows a real one-shot trace with `SCOPE: FREQ?`; it has a valid waveform but
  insufficient periodic crossing evidence for a frequency.

The raw document-root receipts are retained as
[periodic](periodic-temporal-report.json), [DC](dc-temporal-report.json), and
[pulse](pulse-temporal-report.json). They record `PASS:u02-u03`, 8,192-sample
bounded history, temporary-source cleanup, cancelled-operation retirement,
render read-only behavior, and DMM cleanup. The corresponding visible control,
DC-canvas and pulse-canvas observations are in
[compiled-browser-interaction.json](compiled-browser-interaction.json). The
fixture also verifies replacement of the scope subscription after a probe change
and retirement on invalidation.
The normal compiled meter verifier reported `PASS:meter`, including its actual
12 V / `$10 MOhm$` source-resistance case: the real `$10 MOhm$` DMM burden produces the
loaded 6 V solution and 0.6 uA burden current rather than an unloaded 12 V node
reading. The relay browser verifier reported `PASS:e03` (46 verifier assertions,
161 mutation assertions), including selected cross-reference scope probes showing
`REF?` without a subscription.

The post-review task-owned compiled preview additionally reported `PASS:rc` for
the real `C1` RC board. Its verifier discharges the actual capacitor, powers the
board once, retains the same physical `J2.1`/`J2.2` probes, proves that no second
finite-load DC transaction occurs before another 50 ms of accepted solver time,
then proves exactly one fresh loaded reading after that deadline. The same fresh
artifact reported `PASS:u02-u03` for periodic, DC, and one-shot fixtures. The
periodic report recorded the 2.103076641350908 to 4.2066368715920035 V RMS
change, 60.00000033902902 Hz timestamp-derived frequency, and 8,192 samples.
During the bounded DC visual hold, manual inspection showed `SCOPE: NO SIGNAL`,
the `T 1 ms/div` / `V 1 V/div` / `TRIG up` controls, and the real horizontal
trace before normal fixture cleanup. The exact fresh receipts and resource
cleanup result are in [review-repair-browser-results.json](review-repair-browser-results.json).

The irregular-cutoff follow-up rebuilt that same fixture from the final source
and ran it through a separate task-owned preview. Its periodic receipt again
reported the real 3 V-peak to 6 V-peak change (`2.103076641350908` to
`4.2066368715920035 V RMS`) with `60.00000033902902 Hz` and 8,192 accepted
samples. Fresh DC and pulse receipts both passed; visible held states showed a
drawn DC `SCOPE: NO SIGNAL` trace and a drawn pulse `SCOPE: FREQ?` trace.
Manual inspection also exercised the actual scope controls: `T 1 ms/div` /
`V 1 V/div` / rising trigger became `T 20 ms/div` / `V 5 V/div` / falling
trigger, and the coarser capture correctly displayed `SCOPE: WINDOW` rather
than fabricating a trace. The browser fixture is an integration proof of the
final compiled meter/scope path; the exact irregular 199/200/201/500 Hz
boundary matrix is the native analysis contract below. See the reproducible
[follow-up receipt](irregular-cutoff-followup.json).

## Contract/build evidence

The final focused command was:

```powershell
scripts/verify-current-contracts.ps1 -JavaHome .\.tools\jdk8-download\jdk8u502-b07 -Suite @('U02MeasurementContractTest','U03ObservationContractTest','A07ExecutionContractTest','VisualWorkbenchContractTest')
```

It passed `U02MeasurementContractTest` (585), `U03ObservationContractTest` (17),
`A07ExecutionContractTest` (24,868), and `VisualWorkbenchContractTest` (150).
The U02 cases include supported irregular 50/60 Hz RMS, AC-coupled DC offset,
32 phase offsets of a 199 Hz sine through the actual 8,192-sample/5 us ring,
and both reviewer cadences across 32 phases: a repeating
500/250/1000/500/1000/250 us sequence and a 5 us cadence with one 2.5 us step
every 101 samples. In each cadence 199/200 Hz remain qualified and 201/500 Hz
are explicit nonnumeric bandwidth refusals. The same matrix is repeated after a
large finite solver-time origin offset, proving that endpoint representation
precision is bounded without an arbitrary absolute-time tolerance. The cases
also cover insufficient temporal coverage, overrange, and the finite 10 Mohm
burden, plus the shared reacquisition deadline and retirement of
reference/unavailable outcomes.
The U03 cases include a DC horizontal trace, one-shot `FREQ?` trace, periodic
timestamp frequency, no unsafe-gap interpolation, `REF?`, subscription
retirement, and bounded history.

`node --check war/tsj-workbench-ui.js`, `bench_meter_contract.mjs` (380 checks),
and `u04_ui_contract.mjs` (113 checks) passed. The full maintained
`scripts/verify-current-contracts.ps1` gate also passed: 57 Java suites with
independent seed/value/role oracles and report protocol. Finally,
`scripts/build.ps1 -JavaHome .\.tools\jdk8-download\jdk8u502-b07 -Style OBF -Target Compile`
passed the fresh five-permutation production GWT build.

## Qualified limits

- Every U02 DC/AC DMM result has a temporary `$10 MOhm$` differential burden only
  after reference policy admits the pair. The burden is removed before the
  canonical graph is exposed again; it does not enter exports, undo history,
  BoardPad/BoardNet identity, or logical board content.
- Every retained non-reference, available U02 DC/AC probe pair is eligible for
  at most one subsequent finite-load transaction only after another 50 ms of
  accepted solver time. AC RMS also uses that bounded 50 ms acquisition and
  needs at least 40 ms/16 accepted samples. Neither behavior is driven by
  rendering or wall-clock UI cadence.
- The 200 Hz policy is a conservative repeated-content qualification, not a
  modeled analog low-pass filter. It uses same-direction full observed cycles,
  local monotone-PCHIP crossing disagreement, endpoint timestamp precision, and
  the upper median of conservative periods. `BW RMS` therefore needs a strict
  majority of repeated observed cycles to establish faster content; an isolated
  short interpolation estimate is not enough. Missing refinable full-cycle
  evidence reports a nonnumeric state. The policy cannot claim to reveal
  content absent from accepted samples or to characterize a one-off transient
  as recurring bandwidth content.
- Scope paths only draw accepted samples. Unsafe gaps break the trace, and a
  valid DC or transient trace may remain visible without a valid frequency.
  The fixture is developer-only and proves temporal behavior; it is not a new
  player board or a claim about arbitrary dynamic challenge content.
