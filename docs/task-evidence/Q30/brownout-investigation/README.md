# Q30 exact-board E02/E04 brownout investigation

Candidate: isolated `codex/q30-multirail-qualification` at accepted base
`e0c368855a3891acd4673e94ce9afa732390e2bf`, with the uncommitted
33-package Q30 construction pilot. Seeds 0, 1, 3 and held-out 11 were run on
that candidate. This packet owns scratch instrumentation and the focused E04
repair. Q30 remains unregistered and physically unrouted.

## Reproduction and cause

The exact 33-package CircuitJS graph has 202 elements, a 22 µF regulator
input capacitor, two E04 decisions, two real relay and driver branches, and
180 Ω externally referenced loads. The production reset solver timestep is
5 µs maximum / 50 ps minimum. `run_debug.py` copies source to a unique OS-temp
directory, replaces only native JSNI console logging, and instruments Newton
trials in those scratch copies. It never alters the production solver budget,
matrix, elements, or topology. It compiles with JDK 8 and the repo GWT jars.

| Case | Before E04 repair | After E04 repair |
|---|---|---|
| Cold main 4 V, production timestep | PASS 4/4; settled rail 2.56690–2.57110 V, outputs off | PASS 4/4; same operating region |
| Cold main 4 V, pilot 100 µs timestep | FAIL 4/4 at time zero; seed 0's initial Newton step swung rail to −0.291 V, then −15.66 µV before E02 rejected the negative trial | Not retested; production timestep is the acceptance setting |
| Warm main 12→4 V, production timestep | FAIL 4/4 near E04's 3.8 V brownout threshold | PASS 4/4; rail 2.56690–2.57110 V, outputs off |
| Main 4→12 V recovery | Could not reach after failed transition | PASS 4/4; rail 4.99186–4.99196 V, both outputs on |
| Main disconnected with sensors and load source present | Existing normal pilot isolation PASS | Fresh full pilot PASS 4/4; rail 0 V, outputs off, regulator input current effectively zero |

The −15.66 µV coarse cold reading is neither a settled backfeed nor ordinary
floating-point roundoff: it follows a −0.291 V Newton overshoot while E02
temporarily switches from its active target to its passive off branch. E02's
negative-output check catches that unaccepted trial. The same cold input
converges at the production timestep, so no E02 tolerance was changed.

At seed 1's failed warm transition, time was 0.031854373703 s and the
timestep had fallen to 76 ps. E02 remained in its ordinary 0.1 Ω regulation
branch: target ≈3.80749 V, output conductance 10 S, input current about
65–80 mA. Both E04 decisions alternated HIGH and BROWNOUT as the rail moved
between 3.79956 and 3.80104 V. Their ideal output sources alternated about
3.65 V and 0 V, changing relay/driver load enough to move the rail back
across 3.8 V. E02, E04 and NMOS convergence setters appeared, but E02 was
neither current limited nor the origin of the discontinuity. The solver
could not find a fixed point and eventually reached 5,000 iterations. See
`warm-seed1-original.log` for the unmodified trace and `warm-seed1.log` for
the final-source trace.

The separate 107-element pilot is a different experiment: it starts with
control power off, raises regulator input in 1 V steps, uses 470 Ω contact
loads, and takes its 4 V observation while both sensor commands and relays
are off. It does not cross the E04 brownout threshold with both Q30 coils
loaded and does not contain the exact board's 22 µF input network. Its PASS
therefore does not predict the exact-board transition.

## Repair and checks

`DecisionElement` now trips at the declared 3.8 V falling threshold and
recovers at 3.85 V on a 5 V rail. The recovery band is 1% of nominal, capped
at the gap to nominal for other valid declarations. This models a bounded
undervoltage comparator hysteresis: relief of the decision's own driven load
cannot immediately re-enable it. Reset clears the transient state. There is
no solver tolerance, iteration, timestep, current-limit, or output clipping
change. The serialized decision declaration is unchanged; its runtime latch
is still excluded from dumps.

Focused `E04SensorControlContractTest` cases exercise falling trip, the
3.8–3.85 V held-off and held-on states, rising recovery, two repeated
cycles, reset and reference loss using solved rail nodes. The maintained
native E02/E04 run PASS: E02 1,429 assertions; E04 316 assertions, with
cleanup. The exact-board final-source pilot PASS on all four seeds for warm
12→4→12 V and for the full 11-assertion baseline including cold 4 V and
main-source isolation. The same warm and baseline runs PASS with E02, E04
and the solver loop uninstrumented. Logs record the readings for each seed.
The E02 suite retains its reverse-power rejection checks.
The final JDK 8/GWT production build PASS, all five permutations, with
113.096 s compile and 1.726 s link; see `gwt-build.log`.

Reproducible commands (run from the isolated worktree):

```powershell
python docs/task-evidence/Q30/brownout-investigation/run_debug.py --java-home '<JDK8_HOME>' --gwt-home '<GWT_HOME>' --scenario cold --seeds 0,1,3,11 --trace-seed 0
python docs/task-evidence/Q30/brownout-investigation/run_debug.py --java-home '<JDK8_HOME>' --gwt-home '<GWT_HOME>' --scenario cold --coarse-cold --seeds 0,1,3,11 --trace-seed 0
python docs/task-evidence/Q30/brownout-investigation/run_debug.py --java-home '<JDK8_HOME>' --gwt-home '<GWT_HOME>' --scenario warm --seeds 0,1,3,11 --trace-seed 1
python docs/task-evidence/Q30/brownout-investigation/run_debug.py --java-home '<JDK8_HOME>' --gwt-home '<GWT_HOME>' --scenario warm --no-instrument --seeds 0,1,3,11
python docs/task-evidence/Q30/brownout-investigation/run_debug.py --java-home '<JDK8_HOME>' --gwt-home '<GWT_HOME>' --scenario baseline --seeds 0,1,3,11
python docs/task-evidence/Q30/brownout-investigation/run_debug.py --java-home '<JDK8_HOME>' --gwt-home '<GWT_HOME>' --scenario baseline --no-instrument --seeds 0,1,3,11
.\scripts\verify-current-contracts.ps1 -JavaHome '<JDK8_HOME>' -Suite @('E02RegulatorContractTest','E04SensorControlContractTest')
.\scripts\build.ps1 -JavaHome '<JDK8_HOME>' -Style OBF -Target Compile
```

`--brownout-hysteresis` applied the 50 mV change only to scratch E04 source
before the production edit; its `*-hysteresis-seed*.log` receipts are kept
separately. The final commands omit that option and compile the production
E04 source with scratch logging only. The original failing `warm-seed*-original.log`
files and successful final-source `warm-seed*.log` files are distinct.
`--no-instrument` leaves E02, E04 and the solver loop unchanged; it keeps
only the required native JSNI logging shim and the driver for warm recovery.

The E04 repair resolves this electrical integration blocker only. No routed
Q30 physical board, production mutation, D01 diagnosis, player flow, or
normal admission is claimed by these solver pilots.
