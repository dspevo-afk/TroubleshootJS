# E01 — source limits, loads and protection

Scope: bounded low-voltage CircuitJS sources and loads, real independent
disconnect/current controls, solved readout and elementary persistent protection.
Base: `d2e9d35ed2dea2937fda79d8a41a87f882a819dc`, branch
`codex/task43p-final-recovery`. E03 shares this acceptance candidate.

The source is an MNA voltage source with a nonlinear series branch, not a display
clamp. Its envelope is 0–24 V and 1–500 mA, with 0.05 Ohm output resistance and a
nominal 250 mA limit. Compliance and reverse blocking use a finite 1 µS slope;
reverse current remains below 45 µA within the qualified terminal envelope.
Voltage is fixed at the board's nameplate; the player selects current limit and
disconnects individual outputs. Source contracts record the real nominal model.

The fuse uses accepted-step I²t damage (0.1 Ohm, 0.0002 A²s default) and remains
blown after power cycling/solver reset. It is a foundation model with dedicated
solver fixtures. The external load is 1–100k Ohm, 24 V/6 W, and fails open after
exceeding the envelope; reset does not repair it. E03 consumes the 180 Ohm load.
These models do not claim switching ripple, foldback, four-quadrant sinking,
thermal package calibration or mains behavior.

Evidence and reproduction:

- [Native results](native-results.json): maintained JDK8 command, 29 suites,
  E01 96,015 assertions, E03 161, A06 152, independent seed/value/role oracles,
  strict provider readers and 470 report-protocol assertions. Native compilation
  uses the maintained fail-closed stub for the unrelated Task35 generic comparison;
  the actual GWT build compiles the real source.
- [Production build](gwt-build.log): actual `scripts/build.ps1`, selected JDK8,
  all five GWT permutations. Final input identities are in the shared
  [candidate provenance](../E03/candidate-provenance.json).
- [Compiled source proof](compiled-source-proof.json): independent load equation,
  overload, wire short, backfeed, partial/all isolation, persistent fuse and load
  failure, accepted versus trial damage and exact player restoration.
- [Task49](task49-report.json), [A08](a08-report.json), and
  [Task41](../E03/task41-report.txt): actual compiled regression reports. Run
  `./scripts/verify-e01-e03-evidence.ps1` for the maintained strict readers,
  both forced failures and malformed-proof canaries.
- [Player workflow](player-workflow.json): real Browser controls, failed retest,
  powered-off resistance measurement, marked 1 kOhm replacement, a 1 mA limit
  that causes a real output drop/failed retest, and restored 250 mA operation.
  Inspected screenshots: [unrepaired](unrepaired-ohms.png),
  [limited](limited-output.png), [repaired](repaired-output.png).
- [Forced negative](forced-negative.json): explicit developer failure with no
  successful proof. Normal-player privacy and three-source partial isolation are
  also exercised in [E03's player workflow](../E03/player-workflow.json).

Raw scratch logs remain outside the repository. The earlier E01-only
`source-fingerprints.json` is historical; the shared final provenance supersedes it.
Development failures and browser observation limitations are retained in
[the failure record](../E03/retained-failures.json). Browser input evidence does
not qualify the historical Windows CLI/CDP wrapper. Independent agent review was
not run; root owns integrated review and acceptance. Q15 remains unstarted.
