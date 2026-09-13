# E03 — relay and switched-output family

Scope: isolated coil/contact behavior, actual RL flyback, BJT and NMOS drivers,
meaningful coil/contact/drive faults, five-terminal physical replacement and
HIGH/LOW functional retest. Base/branch and final native/GWT gates are shared
with [E01](../E01/README.md).

The nine-part board has separate 5 V coil/command inputs and an isolated 12 V,
180 Ohm external load. Seeds 0/1 select coil-open, 2/3 contact-open and 4/5
drive-resistor-open; even seeds use BJT and odd seeds NMOS. Every normal candidate
executes healthy behavior, its fault symptom and the real diagnostic admission
program before publication. Quick Play includes the new family.

`ServiceRelayElm` uses CircuitJS's inherited 0.2 H RL winding and approximate
current-dependent contact position. The catalog has 125 Ohm/5 V and 720 Ohm/12 V
coils. Contact resistance is 0.2 Ohm on/1 GOhm off, with both branch leakage
currents included in KCL. The NO-open fault leaves coil current intact; coil-open
retains applied voltage with negligible current. There is no arcing, bounce,
welding, calibrated mechanical delay, EMC or mains claim. The generic five-pin
package is explicitly mapped A1/A2/COM/NC/NO → solver posts 3/4/0/1/2.

The driver-specific collector/drain path and reverse-biased flyback diode carry
the actual release current. The family keeps its fixed support parts outside
player removal/lifting. The 12 V replacement fits but cannot pick up on 5 V.
Faulted originals remain faulted in the tray. Five attachment failure injections
prove compensated catalog acquisition, graph, endpoint, inventory and mount state.

The isolated load return has no shared GroundElm with control. Typed reference
declarations reject cross-domain and undeclared-earth DC readings. Temporal DC
within a declared domain uses accepted live voltage as a high-impedance
observation. Active meters use real temporary sources; 25 ms of solver time
settles coil current before sampling and discharges meter-induced energy after
removal. The residual-current guard is 1 µA. Source rollback preserves partial
isolation and selected current limits without decrementing observation revisions.

Evidence:

- [Compiled relay proof](compiled-relay-proof.json): two independent raw solver
  fixtures plus all six installed mutation/repair cases, reference rejection,
  wrong catalog parts, five attachment rollback positions and exact owner restore.
  The verifier yields between installed cases; assertions and solver work budgets
  are unchanged by that scheduling.
- [Task41](task41-report.txt): complete actual 17-route diagnostic regression,
  read by the maintained strict parser. The earlier pre-review report is retained
  separately and does not replace final-source qualification.
- [Player workflow](player-workflow.json): BJT seed0 coil reads OL and wrong 12 V
  replacement fails retest; NMOS seed3 coil reads 124.999 Ohm despite failed output.
  Correct replacement restores 11.983 V on HIGH and 2.16 µV on LOW, then passes
  customer retest. Disconnecting only the coil supply leaves aggregate power ON
  and blocks OHM until every source is off.
- Five inspected images: [open coil](unrepaired-coil.png),
  [contact fault/healthy coil](contact-fault-healthy-coil.png),
  [HIGH](repaired-high.png), [LOW](repaired-low.png),
  [customer retest](customer-retest.png). These use real visible Browser input.
- The final normal NMOS playthrough and four images were repeated after the
  final build, including cross-domain DC rejection and partial isolation. The
  retained earlier BJT sequence/image has an explicit consumed-input audit in
  the player workflow; all six compiled repair cases were rerun on final source.
- [Forced failure](forced-negative.json), [candidate provenance](candidate-provenance.json),
  [retained failures](retained-failures.json), [resource cleanup](resource-cleanup.json).
  Run `./scripts/verify-e01-e03-evidence.ps1` for electrical thresholds, complete
  driver coverage, forced rejection and the maintained Task41/Task49/A08 readers.
  [Final reader result](evidence-reader-results.json) records the command, counts,
  negative canaries and elapsed time separately from resource cleanup.

The layout is a bounded single-layer channel template using current package and
canonical copper owners. It adds no general autorouter, larger board admission,
cut/jumper flow, oscilloscope, scope-earth connection or saved-game milestone.
After customer completion, the established terminal state locks instruments,
power and physical mutations; HIGH/LOW semantic inputs remain available.
Root integrated review is separate from the tests; no agent review was used.
Existing A11-D1 NMOS parameter-comparison debt and Windows wrapper limits remain.
Next unstarted milestone: Q15.
