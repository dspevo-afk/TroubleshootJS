# Development attempts (retained failures)

- `native-initial.log`: FAIL/exit 2 before compilation; the runner registered
  the new contract before its source file existed. Task scratch cleanup passed.
- `native-construction.log`: FAIL/exit 2; validator called the nonexistent
  `PhysicalBoardSlot.isEmpty`. Fixed to inspect `getInstalledPart`.
- `native-construction-fixed.log`: PASS/exit 0; plan 1,397, relay service 110,
  and physical metadata 1,713 assertions, including routed seeds 0 and 37.
- `native-service-initial.log`: FAIL/exit 2; native fixture did not establish
  `CircuitElm.sim` before construction.
- `native-service-fixture-fixed.log`: FAIL/exit 2; native execution reached
  the JSNI console logger. The maintained runner now transparently redirects
  that exact single method to stderr in a task-owned scratch source copy.
  The production source, solver and models remain unchanged.
- `native-service-bridge.log`: FAIL/exit 2; relay inductors had been constructed
  with a different simulator from the service test's live simulator. The
  stamping exception reached an uninitialized native localization path.
  The fixture was corrected to construct and solve each owner under one sim.
  The experimental pre-profile voltage sampling in this diagnostic attempt
  was removed before acceptance testing; it could perturb cold history.
- `build-initial.log`: PASS/exit 0; five GWT permutations compiled in 105.177 s
  and linked in 1.721 s. Later production edits require another build.
- First compiled seed-0/DREV service attempt: browser read commands timed out,
  then the Browser tool reported that the page had crashed. No Q30 receipt
  was obtained; phase and cause are unknown, so this is not PASS. The crashed
  tab's data-URL error page also blocked Browser navigation/close commands.
- `compiled-install-failure.json` and `compiled-install-errors.json`: the
  subsequent challenge-only attempt returned FAIL at solver settlement.
  The underlying error was `Healthy Q30 failed four input conditions`.
  Cleanup reported the original owner restored and the prototype not retained.
- `native-service-same-sim.log`: FAIL/exit 2 on the real healthy profile.
  The 5 V rail was valid, but both loaded-output probes read 12 V even with
  LOW inputs. Construction had omitted each output connector's external
  harness declaration. Service completion therefore disconnected the real
  180 ohm load from its connector. This is a provider construction defect,
  not a reason to widen the healthy-function limits.

- `native-service-load-harness.log`: the first four seed-0 repairs passed;
  relay removal failed. This run used a coarse 100 us native timestep.
- `native-service-discharge.log` and `native-service-runtime-settle.log`:
  relay service still rejected after 100 ms of real unpowered simulation.
  Capacitors retained about 1.2–2.5 V. Readiness flags alone were not the cause.
- `native-service-discharge-observed.log`: extended unpowered simulation at
  the coarse 100 us timestep reached the E02 negative-output guard. This
  stress result is retained as FAIL; acceptance uses the actual production
  5 us maximum timestep and adaptive stepping, without changing the E02 model.
- `build-load-harness.log`: PASS, five GWT permutations, compile 114.130 s,
  link 1.853 s. Later scoped-service changes require another build.
- `compiled-service-deadline.json`: FAIL during diagnostic/service/retest;
  the real solver reached its unchanged wall-clock deadline at 18,533 accepted
  steps. Cleanup restored the original owner. The verifier now advances the
  unpowered graph in 5 ms simulated increments rather than one 100 ms call.
  No solver budget or voltage/current state is changed by that adjustment.
- `native-service-scoped-guard.log`: FAIL/exit 2 at the unchanged 60 s child
  process limit, after DREV_OPEN, REN_OPEN and SENSOR_A_OPEN on seed 0 passed.
  The runner is being partitioned into the full two-seed/five-fault census;
  every child keeps the original 60 s bound.
- `build-scoped-service.log`: PASS, five GWT permutations, compile 83.430 s,
  link 1.456 s. The synchronous compiled service attempt again ended in a
  browser crash with no service receipt. This is FAIL, with no proved cleanup.
  The subsequent challenge-only attempt passed installation on seed 0 in
  34,788 ms (routing 20,229 ms), and real visible DC probe controls measured
  12 V at DREV.A and 612.48 mV at DREV.K against J1.2. Power and retest clicks
  did not take effect; that player-flow issue remains under investigation.
- `native-service-partitioned.log`: FAIL/exit 2; all ten cases were attempted
  under unchanged 60 s child bounds. Eight nonrelay cases produced their
  actual Java PASS row, but the new runner incorrectly expected `fingerprint=`
  instead of `fingerprintHash=` and rejected those rows. Positive captured-row
  checks and mismatched-seed/fault negative checks qualify the parser repair.
  Both relay cases reached real safe target readings after 200 ms simulation,
  while upstream CIN remained at 0.8412 / 0.8122 V. Their first removal then
  exposed a missing native fixture challenge controller during retest
  invalidation. No failed attempt is counted as a passing gate.
- `native-service-lifecycle.log`: FAIL/exit 2, ten attempted cases; the attempted
  full native controller preparation reached absent GWT instrument widgets.
  The final native fixture runs the actual electrical profiles and service
  owners, retains a real controller for retest invalidation, and supplies only
  a native readiness adapter after the electrical profiles pass. Full UI
  lifecycle evidence belongs to the compiled browser, not this native adapter.

- `native-service-current.log`: PASS/exit 0, all ten cases, 2,416 assertions
  and verified task-scratch cleanup. The native readiness limitation above
  still applies.
- `build-current.log`: PASS, five GWT permutations, compile 99.861 s and
  link 1.579 s. This build includes the staged service callbacks, exact-owner
  cleanup guards, 0.1 ms extra live UI advance and wider developer sidebar.
  It is superseded by the subsequent relay verifier capability correction.
- The staged seed-0 DREV compiled attempt passed real observation and service
  in 55,250 ms (installation qualification 44,094 ms, routing 28,766 ms).
  Native qualification ran concurrently, so this is not a cold/warm benchmark.
- `compiled-relay-lift-failure.json`: FAIL, seed 0 / RELAY_B_COIL_OPEN,
  phase `lift-lead`, 26,595 ms. The verifier incorrectly required individual
  lead service from the existing whole-part relay capability. Cleanup restored
  the original owner. The corrected verifier explicitly checks that KB has
  no lift/reconnect action and continues through remove, original reinstall,
  wrong 12 V replacement and correct 5 V replacement. Other candidates still
  require lift/reconnect. No relay capability or service policy was widened.
- The next staged seed-0 REN_OPEN attempt crashed Browser with no final
  service receipt. Subsequent reads were blocked by Browser's crash-page URL
  policy. This is FAIL, phase and elapsed time unavailable, cleanup unproved.
  No attempt is inferred successful from an absent receipt or read timeout.

- `build-final.log`: PASS, five permutations, compile 85.367 s / link 1.381 s.
  The seed-0 visible repair flow and all five compiled service cases passed.
  Their receipts remain in `compiled-fixed-step-partial-receipts.json` and
  `player-fixed-step-build-receipt.json`. This build is superseded below.
- `compiled-final-failure-37-RELAY_B_COIL_OPEN.json` and its `-retry` receipt:
  two fresh held-out relay attempts FAIL at `discharge-chunk-1`, after 13,246
  and 12,804 ms respectively. Each hit the unchanged 500 ms single accepted-step
  attempt limit and restored its prior owner. No further identical attempt was
  made. Source audit found a real configuration mismatch: the native fixture
  explicitly enables adaptive CircuitJS stepping, while the developer entry
  inherited the initial LED board's false adaptive flag. Fixed stepping tries
  up to 5,000 nonlinear iterations instead of reducing the timestep after 100.
  The developer entry now explicitly selects the same 5 us maximum / 50 ps
  minimum adaptive configuration after snapshot capture, reports actual settings,
  and retains snapshot restoration. The native comment claiming these were all
  defaults was corrected. The qualification settings enter the Q30 temporal
  dependency. Solver budgets, models and physical policies are unchanged.

- `build-adaptive.log`: PASS, five permutations, compile 80.096 s / link 1.336 s.
  The affected three-suite native rerun passed 4,239 assertions with cleanup.
  Compiled seed-37 KB, DREV and REN passed in 59,347 / 38,205 / 33,999 ms;
  `compiled-adaptive-partial-receipts.json` preserves those superseded results.
- `compiled-adaptive-failure-37-SENSOR_A_OPEN.json` and its `-retry` receipt:
  two compiled attempts FAIL during `solver-settle`, before service. Both
  restore the predecessor owner. The first generic report says the challenge
  is not ready; full console capture on the second reveals the actual cause:
  `Q30 fault has no measured symptom`. The healthy profile ended HIGH and the
  held-out board's real 22k regenerative feedback could keep A latched HIGH
  after RSA opened. The native fixture had skipped the actual fault-preparation
  callback. The corrected profile now proves all four healthy conditions,
  drives both inputs LOW before fault application, then HIGH for fault
  verification. The native fixture mirrors both real preparation callbacks and
  independently checks the LOW outputs and subsequent symptom. No direct model
  state reset or electrical threshold change is used. The old runtime-only
  developer settlement is replaced by the shared ready-and-settled helper.
  Phase elapsed time was not published by those pre-service failure receipts.
- `native-preparation-unknown-suite.log`: FAIL/exit 2 before suite execution;
  a misspelled suite name was rejected and scratch cleanup passed. The command
  was corrected to the maintained `Q30RelayServiceContractTest` name.

- Final candidate: `native-preparation-final.log` PASS/exit 0 (4,249 assertions,
  complete ten-case service census, cleanup); `build-preparation-final.log`
  PASS/exit 0 (five permutations, 85.395 s compile / 1.405 s link). The complete
  final compiled census is 10/10 PASS, with no case omitted; per-phase elapsed
  and read-only transport timeouts are recorded in `compiled-results.md`.
  The independent reader passes 370 measurements and twenty distinct pairs;
  all seven negative canaries reject. The final visible player flow passes on
  the same build, with four inspected screenshots. No source inputs changed
  after these final native/build/compiled/player gates.

All native failures above recorded task-scratch cleanup. No thresholds, fault
population, routing budgets, physical policy or admission guard were relaxed.
