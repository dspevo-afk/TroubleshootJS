# Q100-1: private functional and electrical pilot

Status: implementation and validation in progress. Model 1 was rejected by actual
CircuitJS analysis before its first accepted step. Model 2 is frozen below before
its measurement. Model 2 then failed startup convergence; model 3 is frozen below.
No Q100 qualification is claimed.

Base: `a1352b5caeb78e069606f84a569e14670f28d807`.
Branch: `codex/q100-constrained-pilot`.

The owner authorized continued roadmap work on 2026-10-29, until told to stop.
Q100's Q60, U07, P09, D01 and A11 prerequisites are accepted within their recorded
scopes. Existing RB100 architecture, resistor-ladder and physical fixtures do not
supply a live, playable RB100 family.

## First coherent scope

Implement a versioned, Developer-only RB100 functional intent and an actual
CircuitJS construction pilot. Account for approximately 100 purposeful physical
packages, globally unique terminals/nets and the supported model for every part.
Build from the RB56 functional architecture with justified additional output,
sensor, rail, monitoring, interlock and interface functions. External loads do
not count as board packages; copied independent boards and decorative parts do
not qualify. Declare bounded structural alternatives, rather than parameter
jitter alone. Reconcile construction interfaces before assigning writers.

Freeze the exact inventory, functions, canonical fault population and independent
electrical expectations before behavior measurements. Preserve the complete
declared hypothesis and observation population; do not remove difficult cases
to make a pilot pass. Report healthy behavior, compatible fault symptoms,
diagnostic/repair checks and their actual costs separately from structural proof.

This first pilot assigns no public difficulty or catalog authority. Normal
playable admission, PCB routing/access, save/replay and long-session qualification
remain later Q100 boundaries. The current 96-part retained-inventory limit cannot
support a 100-package saved board; its eventual extension requires explicit
capacity qualification, including retained repair parts and over-capacity
rejection. Do not silently raise it during the electrical pilot.

## Validation and limits

Use focused native contracts for inventory, role/identity/terminal correspondence,
structural alternatives and negative declarations. Exercise actual final-source
JDK8/GWT through `scripts/build.ps1` and the actual compiled solver path for the
new electrical pilot. Test missing/duplicate ownership or mapping and unsupported
model declarations before mutation; restore temporary sources, callbacks and
graphs on success, rejection, error and cancellation.

Keep the normal 90-second job, 640 work-unit and 5-second unit limits. Any private
reference operation must have an explicit bound frozen before execution, with
operation and cleanup measured separately. A reference or structural result is
not a normal-generation PASS. Preserve failures and report infeasible boundaries
honestly. No pilot seeds or outcomes may become a hidden population whitelist.

The procedural qualification cohort and Q100-specific scale/resource thresholds
are not frozen yet. Freeze them after the authored feasibility boundary, before
observing holdouts. Existing U07 resource limits remain evidence of that workload,
not a Q100 capacity or portability result.

HARD/PSYCHOTIC remain unavailable and protected HARD data remains unread/unrun.
No all-seed, human diagnosis/repair, modest-host, long-session, frame/latency,
capacity/endurance, public-beta or complete-Q100 claim follows from this pilot.

After this substep passes its declared checks, publish it with its limits through
the established normal commit/push/Gmail workflow, then continue the next eligible
Q100 substep under the owner's standing instruction.

## Authored reference freeze, before first behavior

The authored seed is the exact signed long `9007199254740993`, with both
`SHARED_HYSTERETIC_LINEAR` and `SEPARATE_DIRECT_SWITCHING` realizations. These are
representatives, not procedural holdouts or a whitelist. Their A/B controllers
use a shared reference with regenerative feedback versus separate direct
references. C/D reuse the corresponding real reference and use NMOS drivers to
retain the E02 5 V rail's current margin. The 3.3 V regulator is linear versus
averaged switching. Converter switching waveforms and hardware accuracy are not
claimed.

| Packages | Actual purpose |
|---:|---|
| 51 | One AC/isolated converter, 5 V control and A/B sensor/relay architecture |
| 20 | C/D sensor inputs, conditioning, decisions and relay drivers/coils |
| 12 | E/F low-side DC outputs, drive/bias, branch shunts, storage and protection |
| 3 | 3.3 V regulator and input/output storage |
| 6 | Aggregate shunt, powered decision, actual reference divider and RC input filter |
| 4 | NPN inverter, base resistor, gate pullup and auxiliary return NMOS |
| 4 | C/D relay and E/F DC output connectors |

Each relay contact drives an external 180 ohm load from a shared actual 12 V,
500 mA limited contact supply. E/F each drive an actual external 180 ohm + 10 mH
series load from the converted rail, with a 1 ohm branch shunt. External loads
and internal converter solver elements are excluded from the package count.
E/F have 1 uF output storage. The common return shunt is 1 ohm; its input filter
is 10 kohm/100 nF (1 ms), rather than an ineffective capacitor across the shunt.
The powered monitor compares that filtered voltage with 3.3 V divided by
19 kohm/1 kohm (nominal 0.165 V). Its output inhibits C/D relay coil returns;
it reports overload without shedding E/F, avoiding a self-resetting trip loop.
It does not establish a safety-rated or fail-safe interlock.

The reference sequence is LOW, all HIGH, A/C HIGH only, all HIGH with both DC
loads changed to 100 ohm, restored 180 ohm with all HIGH, then LOW. Startup is
0.4 simulated seconds; subsequent contexts each settle for 0.04 seconds. Freeze
the steady-state oracle: converted rail 10–12.5 V, 5 V rail 4.65–5.25 V, 3.3 V
rail 3.05–3.5 V; commanded enabled outputs above 9 V, disabled below 0.5 V;
monitor LOW for nominal loads and HIGH for overload; C/D inhibited only during
overload while A/B and E/F continue their commanded behavior. These wide bounded
electrical expectations are independent of measured candidate values. No
transient trip-delay, filter attenuation or protection-clamp PASS is inferred
from these steady samples. Healthy timestep sensitivity uses 100 us and 50 us,
with rail/output differences at matched contexts at most 0.15 V.

The complete pilot fault population is the following 31 members, in this order.
No candidate is removed after observing behavior. All begin on a fresh graph,
retain all six contexts, and clear the same live fault before the healthy
six-context repair/retest sequence. This is an electrical reference matrix,
not production D01 solvability, physical repair or diagnostic uniqueness proof.

1. RENAC 1 Gohm; 2. REN 1 Gohm; 3. RSA 1 Gohm; 4. RDA 1 Gohm;
5. KB coil open; 6. RSC 1 Gohm; 7. RSD 1 Gohm; 8. RDC 1 Gohm;
9. RDD 1 Gohm; 10. KC coil open; 11. KD coil open; 12. RDE 1 Gohm;
13. RDF 1 Gohm; 14. RSH_E 1 Gohm; 15. RSH_F 1 Gohm;
16. DE short; 17. DF short; 18. CSTORE_E short; 19. CSTORE_F short;
20. C33 short; 21. CIN33 short; 22. RMON 10 ohm; 23. RMON_H 1 Gohm;
24. RMON_L 1 Gohm; 25. CMON short; 26. RBAUX 1 Gohm;
27. RPUAUX 10 ohm; 28. QAUX drain/source short; 29. CFLT_C short;
30. CFLT_D short; 31. RMON_IN 1 Gohm.

Short references use an explicit 0.1 ohm shunt with 1 Tohm cleared leakage;
resistor and coil failures use existing live value/relay effects. Independent
witnesses are lost designated rails/outputs for items 1–21 and 29–30, unexpected
C/D inhibition at nominal load for 22, failure to inhibit C during overload for
23–26, 28 and 31, and AUX_GATE above 1 V during overload for 27. Repair must
restore the complete healthy reference sequence, not just the witness.

Each case enforces 90 s wall time, 640 work units and 5 s per unit; one unit is
construction/analysis or at most 64 accepted solver steps. These private
per-case limits do not qualify normal generation of the full population in one
90 s job. Native reference-matrix operation has a separate 1,200 s host bound;
compiled case capture has 90 s per case and a 6,000 s matrix bound. Host cleanup
is separately bounded at 120 s. No production limit is raised. Preserve failed
attempts and freeze a justified new reference revision before any changed
oracle or design is measured.

## Electrical model revision 2, frozen before measurement

The exact original [model 1 plan](PLAN-model-v1.md) remains preserved, SHA256
`d0b93ccdd9fb373102aeb3d49dc247aacb576880443d6149e6e9aaf5145f27d5`.
The first actual analysis (native healthy attempt 5, all four authored cases)
failed at simulated time zero with CircuitJS's capacitor-loop rejection: the
existing C5 and new CIN33 were ideal capacitors in parallel. That is FAIL, not
healthy electrical evidence; no solver step was accepted. Earlier build and
declaration/mapping failures also remain preserved.

Model 2 gives the single physical CIN33 package an explicit 1 ohm series ESR,
represented by an actual owned resistor and the existing capacitor model. Its
two public terminals are ESR-positive and capacitor-negative; its internal
resistor/capacitor junction is not a new board net or package. The typed capacitor
specification and canonical model identity carry the same ESR. The CIN33 short
reference shunts the actual dielectric and retains its declared series ESR.
This uses the existing bounded capacitor-package contract and preserves
CircuitJS's structural checks.

`MODEL_VERSION=2` identifies this interpretation in the canonical plan and every
reference report. `PLAN_VERSION=1` and its named stream derivation stay unchanged,
so the same exact seed and structural choices are retained. Both variants, all
six contexts, all 31 fault members, witnesses, tolerances and resource limits
above are unchanged. This is a model correction with its failure retained, not
a new cohort or a successful qualification result.

## Electrical model revision 3, frozen before measurement

The [model 2 plan](PLAN-model-v2.md) is retained, SHA256
`628a459f5466151e53db8a8e1ca0454c13e91762b0ff9370f2c2aff34bb3ad89`.
Native attempts 6 and 7 accepted startup steps, then all four healthy authored
cases failed CircuitJS convergence at 0.00935–0.0094 simulated seconds. The
post-step cleanup canary passed. No healthy context was reached. Attempt 7 adds
explicitly unaccepted-trial/last-accepted component diagnostics; these are not
measurement receipts. Static review rules out a hard 4.1 V regulator UVLO toggle:
E02's actual target is continuous through dropout and enable ramps. The exact
numerical coupling cause remains unproved.

Model 3 evaluates an explicit parallel-rail design: U33 input and enable, and
CIN33's positive terminal, use the actual converted RAIL12 instead of RAIL5.
This removes the new rail's cascaded dependency on the 5 V startup loop while
retaining all circuitry and its functions. Both E02 3.3 V contracts support this
input. CIN33 retains its actual owned 1 ohm ESR. Its short still must lose the
5 V witness through the common converted supply; this expectation is not relaxed.
MON_SENSE is explicitly a high-current return net. A negative canary is labeled
cancelled only after its own injected accepted-step abort actually executes.

`MODEL_VERSION=3` identifies this interpretation. The topology stream derivation,
exact authored seed, both structural variants, package count, all 31 hypotheses,
all six contexts, witnesses, timestep comparison and resource limits remain
unchanged. A passing result would establish feasibility of this declared design,
not prove the cause of the earlier numerical failure or qualify normal Q100.

## Regulator numerical-boundary correction, before validation

The exact [model 3 pre-correction plan](PLAN-model-v3-before-regulator-fix.md) SHA256
`759a2225c532792569552a88b74bdcdea0068a33e5c479d124bd0e171b238453` and native
attempt 8 remain preserved. Its 100 us cases reached four complete contexts before
a 37.5 V unaccepted input trial was rejected during recovery; its 50 us cases
failed startup near the regulator's 2 V enable knee. No complete case passed.

Source review identifies a missing bounded enable-knee secant in E02's Newton
linearization: the enable derivative drops to zero above 2 V, while only the
input/dropout and output-current knees currently have adjacent-Newton chords.
The failed node vector supports this hypothesis, but does not prove the complete
per-iteration cause. Add the bounded adjacent enable secant consistently to
target/output/input/quiescent-current derivatives, retaining the same physical
current equations and physical residual checks. Do not tune the board or seeds.

Separately, E02 currently rejects finite unsupported terminal voltages during
`doStep`, before CircuitJS checks global convergence. Add an existing-element
validation hook after global convergence and before advancing time, accepted
callbacks, physical stress or observation receipts. E02 must still reject every
unsupported accepted terminal/current/backfeed state using its unchanged limits;
nonfinite trials still fail immediately. A finite speculative Newton overshoot
is not itself an accepted electrical state. This is not permission to suppress
errors, admit voltages over 24 V, change the solver or weaken any budget.

Keep the entire model-3 circuit, exact seed, streams, two realizations, six
contexts, 31 faults, witnesses and thresholds frozen. Changed regulator/solver
source files are additional consumed inputs in the candidate binding. Qualify
the numerical correction with actual CircuitJS 12 V input, 30 ohm load, 1 uF
output and 50 us enable crossings from 1.998 V to 2.002 V and back, plus cold RC
enable ramp and persistent unsupported/nonfinite negatives. Independent steady
targets follow E02's unchanged declared I–V law; persistent 25 V input must fail
before the first accepted step. The complete existing E02 contracts and affected
solver/runtime boundaries must also pass, followed by the unchanged Q100 gates.

## Converter input-branch convergence correction, before validation

The [pre-correction plan](PLAN-before-converter-input-residual.md) remains
preserved. The actual E02 knee gate passes all 12 cases /1830 assertions,
including source rejection before accepted time/sample publication and the
exact 64-step cold ramps; the existing E02 suite passes /1540 assertions.
Native healthy attempt 9 completes all six contexts for both 100 us cases.
Both 50 us cases still fail at 0.00935 s, so the paired healthy gate is FAIL.

The native-only shadow trace observes existing doStep() calls without changing
flags, vectors, equations or stamps. Its timing is excluded from qualification.
It identifies UAC's averaged converter as the first convergence-false setter
in the last ten iterations of each failing case. The [controlled excerpt](startup-convergence-model3.txt),
[source pins](startup-convergence-source-pins.json) and
[summary](startup-convergence-summary.json) retain the evidence. These are
unaccepted Newton states, not instrument measurements.

All 20 inner rows reject iteration-to-iteration input-current movement at 1 nA.
Thirteen rows have no other failing converter guard; seven additionally exceed
the unchanged 1 uV output-drop change criterion. No target-change, bounded-trial
or drive-residual guard fails in those rows. The previous input Norton stamp's
physical constitutive residual is at most 4.7171e-14 A, below the same 1 nA
criterion. Thus a current-change proxy blocks otherwise eligible iterations
even when the matrix's actual input-current equation is satisfied. This does
not prove later models have no independent convergence condition.

Correct that existing converter owner to compare physical input current at the
current raw solved nodes with the previous input Norton stamp's prediction.
Retain the same 1 nA scaled criterion, every other guard, current/voltage limits,
controller, I–V/power equations and accepted predictor semantics. Previous-stamp
history must be adjacent in the same time/timestep and invalidated on stamp,
iteration start and reset. Missing/nonfinite history may not mint convergence.
The entire model-3 circuit, exact seed, variants, all six contexts, 31 faults,
witnesses and budgets remain frozen. This is a constitutive-residual correction,
not permission to relax voltage/current tolerances.

Require fresh uninstrumented paired healthy runs, existing E06/power contracts,
affected solver/runtime checks, the complete 31-by-two reference and final-source
GWT/compiled gates. Earlier failures, source freezes and the diagnostic traces
remain retained. No Q100 or normal-generation acceptance follows from a trace.

## Clipped-tangent basis correction, before validation

The converter residual correction clears its redundant input-change veto on
eligible iterations, but uninstrumented native attempt 10 still fails both
50 us cases. Both 100 us cases again complete all six contexts. A repeated
shadow trace exposes the regulator conditions previously masked by the first
converter veto. The [controlled excerpt](clipped-tangent-cycle-model3.txt) and
[source pins](clipped-tangent-source-pins.json) retain unaccepted state evidence;
the [previous plan](PLAN-before-clipped-tangent-fix.md) is preserved.

U33 repeats a three-iteration cycle: reverse drops near -0.3358 and -0.3216 V,
then a raw forward drop of 0.0484 V clipped to the 0.0200 V current-limit knee.
Its physical current residual remains wrong, so rejection is required. The raw
secant between the reverse and forward requests is about 0.54 S, but the stamp
anchors that slope at the clipped knee's 0.2 A point. It consequently predicts
about 0.215 A at the raw forward request, rather than the physical 0.2 A law.
This inconsistent secant/evaluation basis feeds the observed cycle.

For a bounded output-drop trial, retain the exact local derivative at its chosen
clipped evaluation point. Apply both output-current and input-load raw secants
only when the current trial is unbounded. Input/enable target secants use their
actual unclipped terminals and remain unchanged. Keep every physical-current
residual, bounded-trial and other convergence guard, clipping, domain limit and
tolerance. The model-3 board, seed, streams, hypotheses, witnesses and budgets
remain frozen. Require fresh uninstrumented paired healthy and affected E02/E06
contract gates before any acceptance; a source diagnosis is not a runtime PASS.

## Accepted decision memory correction, before validation

The complete model-3 fault matrix failed 12 of 62 cases: eight output shunts
failed convergence near 0.4022 simulated seconds and four other transitions
reached a solver unit deadline. All 62 cases cleaned up; all failures remain
retained outside Git. Selected-fault traces at 100 us and 50 us remain diagnostic
only and do not replace the population gate.

The E02/E04 branch equations inspected in those traces match their declared
current laws. Large unaccepted rail changes cross E04's 3.8 V brownout and
3.85 V recovery boundaries. E04 currently treats the previous Newton trial's
logic as hysteresis memory. A rejected or failed trial must not become accepted
physical history. Correct this existing owner first, without adding latency or
changing instantaneous port equations, thresholds, sources, storage or budgets.

Every trial must evaluate its mode from current solved nodes and the last
globally accepted hysteresis memory. Preserve prior trial fields only for
convergence comparison. Cold/reset UNSETTLED memory requires the recovery
threshold. Add a pure model-state commit hook after executor boundary acceptance
and before observation callbacks; stepFinished alone is earlier than acceptance.
Failed trials, callbacks and retries must not commit memory. Public logical
state reports the accepted mode; current and power accessors still describe the
actual stamped trial branch, matching the mode after acceptance.

This is not a sample-held or delayed decision model. No physical propagation
approximation, tolerance change, capacitor sizing, seed rescue or fault removal
is authorized by this correction. A source diagnosis does not establish that it
resolves the shorted-branch cycle. Require actual memory/acceptance negatives,
existing E04/E02/E06/runtime contracts, both Q100 healthy timesteps, the entire
fault/repair population and fresh final-source compiled gates. Preserve the
pre-correction plan and failures. If a different temporal model is needed,
declare its bounds and interpretation separately before measuring it.
## Adaptive-resolution diagnostic, before measurement

Corrected accepted-memory ownership passes the existing E04 suite (365
assertions), but the selected DE short still fails both variants at 100 us.
The preceding fixed-step diagnostic at 50 us also failed. No steady-state
physics or capacitor value has been changed. A delayed/sample-held controller
would be a different temporal contract and is not adopted by this study.

Test the existing CircuitJS adaptive solver in a separate, unqualified program:
maximum dt 100 us (then 50 us for comparison), configured minimum 1 ns, existing
CircuitJS reduction/retry/growth rules, and the original maximum 64 accepted
steps per work unit. Keep the original 90 s case /640 work /5 s unit caps and
all stricter executor limits. Preserve all six fault and six repaired contexts,
all 31 hypotheses, witnesses and thresholds. Charge actual analyses/batches.
Advance each context for at least its declared simulation duration, with final
overshoot bounded by the selected maximum dt; do not assume count*dt while dt
adapts. Report actual context times and distinguish the maximum-step departure
from a fixed-step correspondence error.

The maintained runner creates a scratch-only reference-program adapter with
protocol q100-adaptive-resolution-study-v0; actual models, graph and solver are
unchanged. Selected-fault diagnostic mode never emits the qualified suite PASS
marker. Its output cannot qualify the v1 reader or full fault population.
This initial study establishes feasibility only. If useful, freeze a revised
reference protocol with independent adaptive/timestep and transient checks,
then rerun the entire population and final-source compiled gates before acceptance.
No eightfold batch-size increase or work-budget reinterpretation is adopted.
## Hysteresis attempt rollback correction, before validation

The initial accepted-memory implementation passes all three actual retry/failure
cases (71 assertions), existing E04 (365) and A07 execution (306531), with command
cleanup PASS. Earlier new-test failures came from assuming a Brownout output
node must be near zero. Actual regenerative feedback can passively backfeed that
node; the corrected witness retains actual rail, zero rail-fed drive current,
finite output and accepted-state/receipt/sample checks. The failures remain retained.

The adaptive DE study still fails both variants near 0.402337 s, reaching the
configured minimum timestep. No capacitor, current law or budget was changed.
The first correction was too restrictive: reinitializing from accepted memory
on every Newton trial removed original within-attempt hysteresis. An ON solution
below 3.8 V may request OFF, whose rail rises into 3.8--3.85 V. That OFF branch
must retain its attempt-local Brownout state until recovery, while failure of the
whole attempt must not commit that state to a later retry.

Restore accepted high/control state once in startIteration(), at every real solve
or adaptive retry. Keep the original trial-to-trial hysteretic branch selection
inside that attempt. Commit persistent memory only through solverStepAccepted(),
after boundary acceptance and before observations. Retain cold recovery, public
accepted-state getters, instantaneous electrical laws, all convergence checks,
64-step work units and existing resource limits. This introduces no delayed or
sample-held controller. Attempt-local branch-path dependence remains the existing
model approximation; no unique physical fixed point is inferred from a trace.

A read-only ownership review confirms the actual retry/startIteration boundary;
it is not runtime proof. Require fresh three-case retry/callback/event canaries,
existing E04/A07 contracts, then the separately labeled adaptive diagnostic.
No previous PASS is reused for the changed implementation.
## Initial-guess transition guard, before validation

The attempt-reset revision passes actual rejected-wide-step/retry checks, but a
late callback recovery can still begin with voltages from the failed attempt.
Those subiteration-zero values have not been solved for the new source/matrix.
They must not trigger a hysteresis event before the first current-attempt solve.

For a finite first trial, restore the accepted mode and stamp its unchanged live
port law without classifying a new logic transition. Keep immediate nonfinite
handling. From subiteration one, evaluate the original attempt-local hysteresis
against nodes solved by this attempt. CircuitJS already forbids acceptance at
subiteration zero. Cold UNSETTLED therefore remains off for the initial guess
and classifies the actual solved rail, including the 3.85 V recovery rule, before
any accepted state. This adds no timestep delay or changed electrical equation.

Retain the strong late-callback fresh-3.83 V HIGH oracle, event-failure negative,
100 rejected-trial/retry cases, existing E04 and A07 contracts. Rerun them before
the adaptive study; previous PASS does not cover this changed implementation.
## Model 4: separate load shorts from control power, before implementation

Model 3 now passes all four fresh fixed-step healthy cases after the E04
ownership fixes, but its full 62-case fault gate remains FAIL. The selected
adaptive DE-short study also fails; no failed gate has been accepted. Preserve
model 3 and its exact source/evidence before this hardware revision.

Model 4 keeps exactly 100 packages, inventory order, plan version 1, named
streams, seed 9007199254740993, both structural variants, all 31 faults and their
existing witnesses/oracles. Bump MODEL_VERSION to 4. Use the existing bounded
external JLOAD supply (12 V, 500 mA) for all relay contact and E/F DC loads.
The isolated converted RAIL12 continues to power the 5 V control rail, 3.3 V
monitor and relay coils. A load short then current-limits the load bus without
removing control power. No solver law, capacitor value or current limit changes.

Declare the low-voltage common physically: remap only JLOAD, JOA and JOB's
LOAD_RETURN terminal to CTRL_RETURN in the imported Q100 declarations, and
JOC/JOD's return pads likewise. Do not change Rb56Plan or its exact 20-package
power adapter. E/F diode cathodes, CSTORE supply terminals and JOE/JOF positive
pins move from RAIL12 to LOAD12. Their switched returns, shunts and MON_SENSE
remain unchanged. No LOAD_RETURN island or additional ground may remain. These
are explicit pad/net/copper declarations, never a hidden adapter shortcut.

Canonical identity must declare the shared LOAD12 positive, non-isolated common
CTRL_RETURN and E/F low-side output topology. The RB56-core comparison allows
exactly the three named return-pin overrides and preserves every other base
value, package, terminal, net and power declaration. Normal RB56 remains unchanged.

At ideal nominal voltages the six 180 ohm loads request about 400 mA; actual
series drops reduce that value. Two 100 ohm DC loads plus four relay loads
request up to 506.7 mA before the interlock acts, so current limiting is a real
possible transient, not a guaranteed measured event. After C/D inhibit the ideal
request is about 373.3 mA. The actual 24 V/6 W external-load model bounds remain
unchanged; no winding/thermal or converter switching-waveform claim is added.

Keep the original fixed-step reference protocol v1: 100 us/50 us healthy pair,
100 us full 31-by-two fault/repair reference, six existing contexts/14 signals,
0.15 V healthy pair threshold, 90 s/640 work/5 s outer limits and stricter executor
limits, 64 accepted steps per unit. Adaptive studies remain unqualified and are
not silently adopted. New reports and strict readers must require model 4;
model-3 artifacts remain preserved and must not be relabeled.

Add independent actual graph checks for source posts/power-input pads, A-D
contact/load returns, E/F header-RL chain, diode/capacitor pins, absent old return
island, sole primary ground and unchanged package/harness census. Add a separate
real candidate power-domain canary: a task-only 10 ohm load across LOAD12 and
CTRL_RETURN must enter the existing 500 mA compliance region (roughly 5 V on
that bus) while the actual 5 V/3.3 V control rails remain in their healthy bounds.
Clear that same temporary load, prove load-bus recovery and dispose the entire
canary graph. This is a harness canary, not an added board package or a change
to the canonical fault population. Do not demand a fabricated transient peak.
Require new declaration/healthy/full-fault gates and final-source builds/compiled
privacy/lifecycle/regression evidence before accepting or publishing this pilot.