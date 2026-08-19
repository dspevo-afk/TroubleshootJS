# TroubleshootJS Troubleshooting Realism & Solvability Audit

## 1. Executive Summary

### Scope and verdict

This is a read-only audit of the current normal-player TroubleshootJS path at the accepted Task 37 baseline. It covers the five families currently registered for normal Quick Play:

- `LED_INDICATOR`
- `DIODE_PROTECTED_INDICATOR`
- `PARALLEL_DUAL_INDICATOR`
- `RC_DELAY`
- `NPN_LOW_SIDE_SWITCH`

Task 38/NMOS is not part of the current normal-player surface and is treated as unfinished, consistent with `docs/ROADMAP.md` and `docs/CODEX_TASK_REPORT.md`.

**Current behavior:** the challenges are electrically grounded and partly resemble fair bench troubleshooting. CircuitJS remains the source of electrical truth; faults change the live graph; measurements use real solver-backed probe transactions; component removal, lead lifting, and replacement change the graph; healthy, faulted, and repaired states are checked against family contracts. The parallel-indicator and RC families already provide useful diagnostic reasoning rather than only a single click target.

**Inference:** the current set is fair at the level of small, authored training circuits, but it is not yet fair in the broader sense of an unfamiliar PCB with a reliably solvable diagnostic path. The system validates that a symptom exists, not that accessible player observations distinguish the selected fault from every reasonable alternative. Several families are still structurally obvious because one component is always the only realistic repair target.

**Recommendation:** before adding substantial composition or complexity, add an explicit diagnostic-solvability contract and verifier. It should prove, for every normal seed/fault route, that the player has an available measurement/action sequence that separates the selected fault from plausible alternatives and reaches a valid repair. In parallel, expose real player-controlled inputs for switching families and make fault ownership match the physical part or trace the UI presents.

### Direct answer to the required question

Current TroubleshootJS challenges behave like fair electronics troubleshooting problems only in a bounded sense: they are real solver-backed circuits with plausible local faults and useful physical mutations, but the current templates do not yet guarantee the open-ended observability, ambiguity, and operational workflow expected from real technician work. The biggest realism risk as complexity grows is **semantic solvability drift**: generators can continue to produce a board whose healthy and faulty validators pass while its accessible measurements, control inputs, component identity, and repair options no longer form a unique, understandable diagnostic path.

### Strongest realism already present

The architecture has an unusually important foundation for this goal:

1. A fault is normally represented by an actual CircuitJS switch, shunt, or component-value mutation, not a fake meter result (`GeneratedFaultEngine.java:6-144`, `GeneratedFaultEffect.java`).
2. Probe measurements are solver transactions with power-state restrictions and cleanup, rather than UI-only arithmetic (`CircuitMeasurementAdapter.java`, `InstrumentController.java`, `StandardInstrumentModeProviders.java:7-15`).
3. A physical original part keeps the generated fault; a replacement receives a distinct solver identity. This preserves the difference between removing a bad part and merely changing metadata (`PhysicalPartProvenance`, `GeneratedFaultBinding`, `GeneratedComponentConnectionBindings`).
4. The parallel family gives the player a healthy comparison branch, and the RC family gives the player a real time-domain symptom with a meaningful charge/discharge network.

### Largest current solvability risks

The most important concrete risks are:

- There is no automated proof that an accessible measurement sequence distinguishes the candidate faults.
- The normal diode family always selects the diode-open fault and exposes D1 as the only replaceable component; the RC family always faults C1; LED and parallel families always fault R1. That is not hidden metadata, but it is structural answer leakage.
- The NPN family validates ON/OFF behavior through a hidden `controlCommand` switch. The normal player cannot currently operate a real control input to reproduce the complaint or verify both states.
- Some faults are modeled with private series switches whose public target is a component. That can be defensible as an internal lead failure, but the physical identity must be explicit; otherwise the player can be asked to replace `RLOAD` for an interruption that visually reads as a separate path or trace.
- Global board power toggles all generated external inputs together. This is adequate for current small boards, but it will become misleading when multiple rails or dependent subsystems are composed.

## 2. Current Diagnostic Architecture

### What the current flow does

The normal flow is effectively:

```text
family/seed selection
        |
        v
logical board + CircuitJS elements + physical parts + PCB layout
        |
        v
healthy solver verification
        |
        v
fault effect applied to the live graph
        |
        v
faulted solver verification + compatible complaint selection
        |
        v
READY: player probes, powers, lifts, removes, replaces
        |
        v
solver-backed repair status / functional completion
```

`GeneratedChallengeController.java:14-171` owns the preparation gates, fault application, scenario selection, READY/COMPLETED states, and repair-status checks. `GeneratedBoardVerifier.java:8-76` checks structural ownership, pad bindings, net voltage sanity, and family healthy behavior where applicable. `GeneratedChallengeBehaviorAdapter.java` forwards family-specific healthy, faulted, and repaired predicates through a common contract.

### Current guarantees versus missing guarantees

| Area | Current guarantee | Not currently guaranteed |
|---|---|---|
| Electrical truth | CircuitJS owns the generated circuit behavior and active measurement result. | A generated circuit has a unique diagnostic explanation from the player’s allowed observations. |
| Fault installation | Selected compatible fault is applied and remains bound to its generated original. | Every fault has a player-visible physical identity that matches the interruption mechanism. |
| PCB mapping | Pads are bound to owned solver endpoints; PCB nets have stable IDs. | Every important diagnostic node has an intentional, useful test point or a practical probe path. |
| Healthy/faulted behavior | Family validators and scenario compatibility checks reject many invalid symptoms. | Validators enumerate and separate all plausible candidate faults or alternative repairs. |
| Mutation | Power-off lift/remove/reconnect/restore and family-specific replacement affect the graph. | Jumpers, trace cuts, trace repair, and general alternate repair paths exist. |
| Player workflow | Voltage, resistance, continuity, and diode test modes are visible and state-gated. | Current, capacitance, frequency, oscilloscope, current limiting, and real external control stimulus exist. |
| Privacy | Debug-only generation details are not the normal complaint; scenario text is generic. | Structural availability of replacement targets does not leak which part class is eligible. |
| Procedural diversity | Seeded value, fault, and limited layout choices are reproducible. | Topology composition, semantic rail contracts, auxiliary systems, and diagnostic diversity are generated independently. |

### Normal-player family inventory

The registry currently exposes the five families above (`QuickPlayFamilyRegistry.java:9-71`). In normal Quick Play, the legacy family seed envelope is `{0, 2, 3}` and the NPN envelope is `{0, 1, 2, 3}`. That keeps the current route deterministic and bounded, but it also means the normal surface is a small set of templates rather than a broad generated board population.

| Family | Electrical topology | Normal reachable fault selections | Player-facing complaint |
|---|---|---|---|
| LED indicator | `VIN -> R1 -> LED1 -> GND` | `R1` open; `R1` incorrect value | “Indicator does not light.” |
| Diode-protected indicator | `VIN -> D1 -> R1 -> LED1 -> GND` | `D1` open | “Indicator does not light.” |
| Parallel dual indicator | Two independent `VIN -> R -> LED -> GND` branches | Branch 1 `R1` open; branch 1 `R1` incorrect value | “The two indicators do not behave the same.” |
| RC delay | `VIN -> R1 -> RC_OUT`, with `R2` and `C1` to ground plus healthy `C2` decoupling | `C1` positive-lead open; `C1` shunt short | Immediate response or no response after power-up |
| NPN low-side switch | Independent load and control paths, `RLOAD -> LED1 -> Q1 C-E`, `CONTROL -> RB -> Q1 B`, `RPD` pull-down | `Q1` C-E open; `Q1` C-E short; `RB` open; load-path open after `RLOAD` | Controlled load does not switch on, or remains active when control is low |

The family generators are explicit and hand-authored (`LedIndicatorGenerator.java`, `DiodeProtectedIndicatorGenerator.java`, `ParallelDualIndicatorGenerator.java`, `RcDelayGenerator.java`, `NpnLowSideSwitchGenerator.java`). Current procedural variation is mostly template selection plus seeded component values, fault selection, and small placement shifts. It is not yet composition of reusable functional blocks with typed interfaces.

### Measurement and mutation surface

`StandardInstrumentModeProviders.java` registers only `NONE`, DC voltage, resistance, continuity, and diode test. Resistance, continuity, and diode test are unpowered active measurements; the measurement adapter creates a temporary solver stimulus, reads it, and restores the normal graph. DC voltage uses a high-impedance meter load and is available according to board power state. The important consequence is positive: an in-circuit measurement can be misleading for the same electrical reasons it is misleading on a workbench. The important limitation is that the UI has no direct current, capacitance, frequency, or scope measurement.

`BoardModificationController.java` is the mutation owner for current lead lift, reconnect, component removal, and restore operations. These operations are gated to a ready, unpowered board and mutate the actual CircuitJS graph. Replacement is supplied by family/runtime capabilities. There is currently no general jumper, trace-cut, trace-repair, or per-rail isolation operation.

## 3. Family-by-Family Review

### 3.1 LED indicator

**Current behavior:** `LedIndicatorGenerator.java:10-238` creates a direct series indicator. Supply and resistor values are selected as matched arrays: 5 V/330 ohms, 9 V/680 ohms, or 12 V/1 kohm. The healthy validator expects approximately 5–15 mA and matching resistor/LED current. The two normal fault candidates are an open R1 and an R1 value increased by 100x. A connector-open candidate is constructed but marked incompatible for normal selection.

**Useful technician measurements:**

- Powered DC voltage at the connector establishes whether the source is present.
- Voltage across R1 and LED1 can establish whether the series loop carries current and can approximate current from the resistor drop.
- Power-off resistance/continuity across the series path can find an open, but an in-circuit LED path makes the reading less direct.
- Lifting R1 gives a clean out-of-circuit resistance test, which is a good reason to use the mutation system.

**Fairness:** this is a fair beginner circuit because the symptom, topology, and likely measurements agree. It is also the least ambiguous family: an open resistor and a 100x resistor both produce a dark LED, so the player must make a measurement rather than merely observe the symptom. The actual gap is not electrical validity; it is diagnostic breadth. The only faulted component is always R1, there is no unrelated healthy circuitry, and the board does not force the player to decide whether the symptom is supply, return, LED polarity, or resistor-related before isolation.

**Answer leakage:** R1 and LED1 are the meaningful replacement slots, but only R1 is ever faulted. A player who inspects the workbench capabilities can reduce the search space without probing. That is legitimate physical information if replacement eligibility is intended to be discoverable, but it makes the challenge less like an unfamiliar repair board.

**Assessment:** electrically sound; fair as an introductory exercise; not a strong test of diagnostic search once the template is recognized.

### 3.2 Diode-protected indicator

**Current behavior:** `DiodeProtectedIndicatorGenerator.java:6-203` adds D1 ahead of the resistor and LED. The normal generator deliberately marks the diode-short candidate as developer-only and selects the diode-open candidate in the normal path (`:75-86`). D1 is replaceable; the resistor and LED are fixed. The developer route can force a short and use the brighter-than-expected scenario, but that route is not normal-player coverage.

**Useful technician measurements:**

- Powered DC voltage across the input and diode can show where the series path stops conducting.
- Power-off diode test in both polarities should distinguish an open diode from a healthy forward drop, subject to the in-circuit path.
- Lifting D1 provides a clean diode test and is an appropriate isolation action.

**Fairness:** a diode-open fault is plausible and solver-backed. The topology supports a sensible diagnostic sequence. However, normal-player solvability is trivialized by fault population: the only normal fault is D1 open, the only replaceable part is D1, and the complaint is the same dark-indicator complaint as the direct LED family. The developer-only D1 short demonstrates that the electrical model can support a more varied fault, but it does not improve normal-player uncertainty.

**Assessment:** good electrical foundation and good diode-test teaching opportunity; currently a highly leaky normal challenge because the repair target is predetermined by the available catalog.

### 3.3 Parallel dual indicator

**Current behavior:** `ParallelDualIndicatorGenerator.java:7-259` creates two real branches from the shared input to ground. Branch 1 uses the lower resistor value and is intentionally brighter than branch 2. Normal faults affect R1 only: open or 100x incorrect value. The second branch is healthy and is checked as a reference by `ParallelDualIndicatorGeneratedBoardValidator.java`.

**Useful technician measurements:**

- Compare the two LED-node voltages or the voltage drops across the two resistors.
- Infer branch current from each resistor’s voltage drop; branch 2 supplies a built-in baseline without a fake reference reading.
- Power-off resistance across the live board is intentionally affected by parallel paths, making the result a realistic “do not trust this reading yet” situation.
- Lift R1 or remove the original part to isolate it. The separate parallel-resistance fixture shows that the project understands why isolation matters, although that fixture is not itself the generated board.

**Fairness:** this is the strongest current static family. The complaint describes a comparative symptom, the healthy branch is a meaningful red herring/reference, and the player has a reason to compare rather than immediately replace. An open R1 and a high R1 are distinguishable by branch behavior and isolation. The family still has a fixed topology and always faults the same branch/component, so its replay value and answer uncertainty are limited.

**Assessment:** fair and educational at the current scale; a strong model for future “healthy comparison path” design.

### 3.4 RC delay

**Current behavior:** `RcDelayGenerator.java` builds an RC output with R1 charging, R2 bleeding, C1 as the timing capacitor, and C2 as healthy input decoupling. The seed mapping is deliberately bounded: the normal RC envelope includes an open-C1 case and a short-C1 case. The open effect is a positive-lead open; the short effect adds a low-ohm shunt while keeping the board-facing positive lead distinct. C1 is the only replaceable component. `RcDelayTemporalBehavior.java` uses the live solver to power off, wait for residual charge to clear, power on, sample at defined times, and classify the observed behavior.

**Useful technician measurements:**

- Power-off DC voltage after discharge checks whether the capacitor is retaining charge.
- Powered DC voltage at RC_OUT at multiple times after power-up reveals the charging curve.
- Resistance/diode measurements after the stored-energy readiness gate can find an open or near-short network, but in-circuit R2 and C2 paths must be considered.
- Removing or lifting C1 is the correct isolation step for a capacitor-specific test.

**Fairness:** this is a meaningful step beyond static continuity. The complaint describes a real temporal symptom, and the healthy C2/R2 network has a defensible circuit purpose. The model does not use a fake “capacitor reading”; the timing result is produced by CircuitJS. The player-facing weakness is operational: there is no actual controller/load input, no scope, and no capacitance mode, so the player is asked to infer a timing fault from a simplified output node and a hidden temporal verifier. The complaint already narrows the behavior to “too fast” or “never,” while C1 is the only replaceable suspect.

**Assessment:** electrically promising and reasonably fair as an RC exercise; not yet a realistic controller repair because the user cannot exercise a real downstream behavior and the timing contract is not visible as a measurement plan.

### 3.5 NPN low-side switch

**Current behavior:** `NpnLowSideSwitchGenerator.java:9-405` creates independent load and control supplies, a load resistor/LED, a base resistor, a base pull-down, and an NPN transistor. It exposes board pads for the load supply, control input, base, collector, emitter, and relevant component leads. Four normal fault candidates are registered at `:132-143`: Q1 collector path open, Q1 C-E short, RB open, and a load path open after RLOAD. RLOAD, RB, and Q1 are replaceable; RPD and LED1 are fixed.

The family state contains a private `controlCommand` switch (`NpnLowSideSwitchFamilyState.java:5-20`). Scenario presentation and validators toggle it to test “commanded on” and “commanded off.” The visible player does not currently have a normal input control that drives the `CONTROL_INPUT` connector through those states.

**Useful technician measurements:**

- Confirm both supply rails and ground.
- With a known ON command, inspect RB/base voltage, base-emitter voltage, collector voltage, and load-side voltage.
- With a known OFF command, inspect whether collector voltage rises and load current disappears.
- Use resistor voltage drops to infer base/load current; direct current mode is not required for the basic topology.
- Power-off resistance/diode tests can separate RB, Q1 junctions, and the load path after removing parallel influence.

**Fault signatures:**

- C-E short: the load remains active when control is low; the collector is near the emitter/ground.
- RB open: the load does not activate and base current is absent.
- Q1 C-E open: the load does not activate while base drive remains present.
- Load-path open: the load does not activate while base drive remains present. This is intentionally close to the C-E-open symptom and should require isolation or a collector/load-node measurement.

**Fairness:** the four faults are plausible and more interesting than the earlier families. The base-current distinction gives RB-open a reasonable path, and the C-E short has a clear stuck-active signature. The difficult pair is C-E open versus load-path open: both can present “control ON, load OFF, base drive present.” A technician can distinguish them by checking the collector/load path, diode-testing or ohming the isolated device/path, and following the board copper. The current validators prove the behavior but do not prove that the visible workflow makes this separation easy.

The load-path fault is implemented by a private series switch after the RLOAD element while its logical target is `RLOAD`. That can represent an internal resistor lead or attachment failure, but the physical model should say so explicitly. If the player sees the interruption as a separate trace or path, target identity becomes misleading.

**Assessment:** the best current family for multi-stage diagnosis, but its hidden stimulus and global power control are substantial realism limitations. It should not be used as the basis for larger composition until the player can command and verify the actual input behavior.

## 4. Fault-by-Fault Review

There are 11 normal-player fault selections when counted by family route: two LED, one diode, two parallel, two RC, and four NPN. They collapse to nine unique fault types because resistor faults appear in two families. The table also records candidates that are intentionally excluded from the normal path.

| Fault type and reachability | Electrical implementation | Reasonable player evidence | Realism/solvability assessment |
|---|---|---|---|
| Resistor open — normal LED/parallel | Private open switch in series with the original resistor path (`GeneratedFaultEngine.java:10-14`, `:115-144`). | No branch current; open or abnormal isolated resistance; companion branch remains healthy in parallel family. | Physically plausible and well suited to power-off isolation. Repeatedly targets R1, so family-level answer leakage remains. |
| Resistor incorrect value — normal LED/parallel | Actual `ResistorElm` value changes to a generated effective value, currently 100x healthy in these families. | Nonzero but very small branch current; abnormal resistor voltage/current relationship; out-of-circuit resistance identifies value. | Strong solver-backed fault. A 100x value is diagnostically clear but not necessarily a common field failure unless the scenario is framed as a wrong-installed part. |
| Diode open — normal diode family | Private switch opens the diode path; public terminals represent the physical diode sides. | Diode test is open in both directions after isolation; no series current; full supply may appear across D1. | Plausible and teachable. Normal family population makes it effectively predetermined because D1 is the only replaceable target. |
| Diode short — developer-only | Parallel bypass switch creates a real short around D1. | Near-zero diode drop; increased branch current/brightness; diode test may show a short. | Electrical model is useful, but it is not normal-player coverage. Keeping it developer-only is valid for a bounded milestone, but normal reachability should be expanded later. |
| Capacitor positive-lead open — normal RC | Switch opens the board-facing positive lead of C1 while preserving the original component identity. | RC_OUT charges/discharges too quickly; C1 side and board side can differ when measured/isolate; no normal capacitance mode. | Good physical modeling for a lead failure. The temporal symptom is real; diagnostic path is under-specified and C1 is always the repair target. |
| Capacitor short — normal RC | Low-ohm shunt, currently 0.1 ohm, with a distinct positive-lead switch. | RC_OUT held low, excessive input current inferred from R1 drop, power-off near-short after discharge. | Plausible, solver-backed, and meaningfully different from open. It needs current limiting or stress consequences before being used in larger boards. |
| Transistor C-E open — normal NPN | Collector-side private switch opens the original Q1 collector path. | Base drive present; no load activation; collector/load-node measurements and isolated transistor tests separate it from load-path open. | Plausible, but its “open” is a path interruption rather than a modeled semiconductor open state. The player needs a visible identity and a real control input. |
| Transistor C-E short — normal NPN | 0.1-ohm C-E shunt is added around Q1. | Load remains active with control low; collector near emitter; high load current. | Strong diagnostic signature and a plausible catastrophic failure. Without a current-limited supply, the circuit does not yet teach cascading damage. |
| Base resistor open — normal NPN | Private switch opens RB’s series path. | No base drive; load off under ON command; RB isolation reads open. | Plausible and distinguishable if the player can issue ON. |
| Load-path open — normal NPN | Private switch opens immediately after RLOAD; logical fault target is RLOAD. | Base drive present but load off; isolate RLOAD/load path; compare collector and LED/load-node voltages. | Useful ambiguity partner for Q1 C-E open. Physical target semantics must be made explicit; otherwise replacement target and visible defect can disagree. |
| Connector open path — constructed but incompatible in current LED/diode/parallel routes | Private series switch represents an open connector path; candidate is passed with `compatible=false`. | No input voltage/current at the board side. | A realistic future fault, but not currently part of normal selection. It should not be counted as current normal coverage. |

### Fault modeling conclusion

The electrical effect vocabulary is currently better than the fault population. Open, short, value-change, and lead-open effects are real enough for the current proof families. The next risk is not adding more enum values; it is ensuring that each effect has a stable physical owner, an accessible diagnostic observation, and a valid repair route before it is admitted to normal generation.

## 5. Solvability Risks

### 5.1 Symptom validation is not diagnostic validation

The current pipeline rejects a healthy board that is not healthy, rejects a fault that does not produce its expected symptom, and rejects a scenario that is not compatible with the live faulty state. This is necessary. It is not sufficient.

A challenge can pass all current gates while:

- two candidate faults produce the same readings on every accessible node;
- the only separating measurement requires a control input the player cannot operate;
- the separating measurement requires a component isolation action that the target does not expose;
- the displayed replaceable-part set reveals the answer before the player measures;
- a fault is electrically real but has no physically coherent board location;
- a wrong replacement produces a plausible temporary symptom but no meaningful return-to-customer test.

No current verifier enumerates those conditions.

### 5.2 Fixed targets create structural answer leakage

Current reachable faults are biased toward a single component in three families:

- diode family: D1 only;
- RC family: C1 only;
- LED and parallel families: R1 only.

The normal UI does not print the hidden fault ID, and the workbench nameplate does not intentionally print fault state. Nevertheless, a player can inspect which slots offer replacement or isolation operations. That is a real physical observation, but because the generated boards have so few replaceable candidates it often functions as an answer key.

The correction is not to hide capabilities. The correction is to generate multiple physically valid fault owners and give each a real diagnostic and repair path, or to make the board’s intended serviceability model explicit and accept that the family is a guided exercise.

### 5.3 NPN ambiguity is real but the input is not

The Q1 C-E-open and load-path-open cases are a good pair because a technician should not be able to identify both from one static symptom. However, the current player cannot change the control command through the board. The behavior contract changes the hidden switch internally, which makes automated validation pass but removes an important normal troubleshooting action: “apply the control signal and observe the load.”

This is a solvability risk because the hidden action can make a challenge look more observable in code than it is at the PCB.

### 5.4 Temporal validation is stronger than temporal interaction

RC temporal validation is solver-backed and more rigorous than a static fake reading. But the player sees no waveform, no frequency/time scale, and no explicit input/output operation. The challenge is therefore solvable as a simplified voltage-observation exercise, not yet as a realistic controller timing diagnosis.

### 5.5 Global power is too coarse for future composition

`BoardPowerController` delegates one powered/unpowered state to all `GeneratedExternalPowerBindings`. The current families can tolerate that. A board with a 12-V load rail, a 5-V logic rail, a separate sensor supply, or a controlled enable rail cannot be diagnosed accurately if every rail always changes together. Coarse power control will create false conclusions such as “the control signal is missing” when the player has intentionally removed the load supply, or will prevent the player from isolating a rail short.

### 5.6 Missing actions will become missing evidence

The architecture anticipates jumpers and trace cuts, but current normal interaction supports only a subset of component mutations. When a future generated fault is a trace open, connector contact problem, rail short, or missing ground, it must not be admitted before the corresponding physical repair/isolation action exists. Otherwise the system will know the fault but the player will not have a fair route to completion.

### 5.7 Procedural variation is coupled and narrow

Current seed behavior is reproducible, which is a strength. But most seeds select among fixed arrays or fixed fault lists, and in RC the seed also couples the value profile to the fault type. In NPN, supply selection, fault selection, and layout shifts are derived from the same small seed envelope. This limits diversity and makes future failures harder to classify: a symptom may change because three seed dimensions changed at once.

Use independent deterministic substreams for topology, values, fault, layout, scenario, and auxiliary circuits before the generator becomes larger.

## 6. Diagnostic Distinguishability

The following are plausible player paths based on the current exposed pads, measurement modes, and mutation primitives. They are diagnostic recommendations/inferences, not current automated guarantees.

| Family | Initial symptom | A fair separating path | What current code formally proves | Remaining risk |
|---|---|---|---|---|
| LED | Dark indicator | Verify VIN; measure R1/LED voltage drops; power off and lift R1; resistance-test the loose part; replace and verify current. | Healthy current range, faulted dark/low-current state, repaired current range. | No proof that the path is exposed in a particular rendered board state; R1 target repetition. |
| Diode | Dark indicator | Verify VIN; diode-test D1 in both directions; lift D1 if in-circuit path is ambiguous; replace and verify forward drop/current. | Healthy diode drop/current and diode-open low-current state. | Normal fault and replaceable target are both predetermined; developer short is not normal. |
| Parallel | Unequal indicators | Compare branch voltage drops; use branch 2 as a reference; power off and lift R1; measure isolated R1; replace and compare both branches. | Shared rail, KCL, branch current ranges, asymmetry, and repaired parity. | No generic plan verifier; alternate branch components are not fault candidates. |
| RC | Too fast or never responds | Discharge; measure residual voltage; power-cycle; sample RC_OUT over time; isolate C1; compare an out-of-circuit capacitor/part identity; replace and repeat. | Real solver timing profile and repair timing profile. | No scope/capacitance/input control; temporal profile is hidden and C1 is the only target. |
| NPN | Load does not switch or is stuck active | Operate control low/high; verify both rails; measure base and collector; infer current from RB/RLOAD drops; isolate RB/Q1/load path; repeat both states. | Hidden command can produce healthy/faulted ON/OFF currents and collector voltages. | The player cannot currently operate the hidden command; C-E open vs load-path open needs a real visible sequence. |

### Required future contract

Every generated challenge should eventually carry a diagnostic contract containing at least:

1. The allowed player actions for the challenge: power transitions, external inputs, probeable endpoints, isolation operations, replacement operations, and any repair primitives.
2. A finite set of plausible candidate faults, including healthy and alternative-fault baselines.
3. One or more measurement/action plans that are safe and accessible from the initial state.
4. Solver-derived result ranges or classifications for each plan under each candidate.
5. A proof that the selected fault is separated from the alternatives by at least one plan, or an explicit classification that the challenge is intentionally ambiguous and has a permitted diagnostic branch.
6. A repair reachability proof: the selected fault can be corrected by an allowed mutation and the customer behavior can then be re-tested.

The contract should describe evidence, not reveal the answer to the player.

## 7. Triviality / Answer Leakage

### What is appropriately hidden

Current normal presentation generally does not expose the family topology, selected fault, fault effect, expected symptom class, hidden solver values, or developer verification route. The complaint text is generic. `?tsjDebug=true` and explicit developer routes are separated from normal Quick Play. This is a good privacy boundary.

### What leaks structurally

- The workbench capability list reveals which parts can be lifted, removed, or replaced. In a board with one replaceable component, this is effectively a fault hint.
- The diode normal family has one replaceable D1 and one reachable fault: D1 open.
- The RC family has one replaceable C1 and two fault modes that both name the same physical target.
- LED and parallel normal faults always name R1, even though LED/branch observations can make a player reason more broadly.
- The current fixed layouts and small component counts make topology recognition quick. This is acceptable for beginner fixtures but not for a supposedly unfamiliar PCB at higher difficulty.
- The NPN replacement catalog includes a “low beta” option with beta `.1`. A beta below unity is not a credible ordinary NPN replacement parameter; it communicates “wrong answer” more than a realistic substitution risk.

### Fairness judgment

This is not hidden answer metadata in the prohibited sense. It is **model topology and serviceability leakage**. The distinction matters: the right response is to improve board and fault diversity, not to conceal real component capabilities from the player. A technician should be allowed to see that a component is socketed, replaceable, or difficult to remove; the board should simply contain enough plausible serviceable candidates that this fact is not the answer by itself.

## 8. Real-Technician Workflow Comparison

| Technician stage | Current support | Audit judgment |
|---|---|---|
| Receive incomplete complaint | Generic family scenarios exist. | Good start, but complaints are currently too tightly coupled to a small family behavior. |
| Establish operating conditions | DC voltage and one global board power control. | Adequate for simple boards; insufficient for multi-rail boards. |
| Identify blocks from unfamiliar PCB | PCB traces, pads, designators, and physical parts are present. | Good architecture; current boards are too small/fixed to test real unfamiliar-board inference. |
| Choose measurements | Four useful meter modes, power-state gates, real in-circuit paths. | Strong foundation; no explicit diagnostic plan or current/scope coverage. |
| Apply an input or stimulus | RC uses power cycling; NPN uses a hidden command. | Major gap. A real control/input mechanism must be player-operated. |
| Compare with expected/reference behavior | Parallel branch and some node relationships supply references. | Good in parallel; weak in direct LED/diode and hidden in NPN/RC. |
| Isolate a suspect | Lift/remove/reconnect while unpowered. | Strong primitive; not all candidate path faults have a matching physical action. |
| Repair trace/connector or jumper | Not currently available. | Future trace/connector faults must wait for these primitives. |
| Install replacement | Real replacement part identity and solver graph changes. | Strong; catalog breadth and physical plausibility need improvement. |
| Control current/avoid secondary damage | Resistor stress/damage exists, but no bench current limit. | Not yet a complete workbench model. |
| Re-power and verify customer function | Solver-backed repair status; Quick Play has Finish Job. | Strong functional criterion, but NPN control is hidden and completion can be terminal/latching. |

The current system resembles a technician’s bench in the measurement and isolation mechanics, but resembles a guided lab in the stimulus and board-complexity mechanics.

## 9. Red Herrings and Healthy Circuitry

### Existing healthy circuitry that helps

- The second branch in `PARALLEL_DUAL_INDICATOR` is a real healthy comparator, not a decorative extra.
- RC includes R2 as a bleeder and C2 as input decoupling. These parts have electrical purposes and can affect readings without being the selected fault.
- NPN includes RPD, a legitimate base pull-down that can attract an incorrect diagnosis if the player ignores the control path.
- The direct LED and diode families expose component leads and simple series relationships, which helps players learn the instrument behavior before facing parallel paths.

### Current insufficiency

There are no unrelated auxiliary circuits in the current normal families: no power indicator on a separate path, spare connector, sensor input, secondary rail, buzzer, protection network, or healthy status subsystem that remains functional while the complaint occurs. That is consistent with the bounded early milestones, but it means the player is usually solving “which part in this small circuit?” rather than “which subsystem explains the customer symptom?”

Healthy red herrings should be added only with contracts that prove:

- they do not accidentally create a second valid explanation for the complaint;
- their readings remain electrically consistent across all selected faults;
- they have a purpose a technician could infer from the PCB;
- they do not merely add visual clutter or hidden answer hints.

The roadmap’s auxiliary-circuit and module-composition work should therefore follow a diagnostic-solvability verifier, not precede it.

## 10. Measurement Coverage

| Instrument | Current behavior | Useful current families | Coverage gap |
|---|---|---|---|
| DC voltage | Solver-backed voltage with a high-impedance meter load; powered and unpowered board states are represented. | All five; especially rails, resistor drops, diode drop, collector/base nodes, RC timing samples. | No direct current or waveform context; a single static reading can be under-informative. |
| Resistance | Unpowered active stimulus, approximately 1 V through 1 kohm, with graph restoration. | Open/high resistors, parallel-path diagnosis, isolated component checks. | In-circuit readings need player interpretation; no automatic “this is a parallel reading” explanation, appropriately. |
| Continuity | Resistance-based threshold, currently 50 ohms. | Hard opens and shorts. | Threshold is a UI classification, not a universal electronics truth; future low-ohm loads and semiconductors can make it misleading. |
| Diode test | 3 V/1 kohm stimulus with compliance/current classification. | Diode family and isolated transistor junction checks. | No capacitance or dynamic semiconductor test; in-circuit paths can confuse readings. |
| Current | Not exposed as a normal instrument mode. | Inferred through resistor drops and solver validators. | Important for load faults, shorts, stress, and current-limited troubleshooting. |
| Capacitance | Not exposed. | RC family currently relies on voltage/time and isolation. | A capacitor tester is not required for solvability, but future RC difficulty must not assume one. |
| Frequency/scope | Not exposed. | No current family requires it. | Needed for oscillator, PWM, digital, intermittent, and advanced timing families. |
| Bench supply/current limit | Board power is global and nominal; no adjustable current-limited supply. | Current small families tolerate the simplification. | Essential before rail shorts and secondary failure are normal faults. |

The present meter set is sufficient for the five small families if the challenge is deliberately designed around it. It is not sufficient as a general diagnostic foundation for the later roadmap without explicit family capability requirements.

## 11. Wrong-Repair Realism

### Current strengths

- Replacements are distinct physical/solver identities. Reinstalling the original faulted part does not erase its fault; installing a catalog part does not inherit the private fault infrastructure.
- Wrong resistor values are actual values, not a “wrong” label. They alter current and voltage naturally.
- Wrong LED/diode orientation can produce actual polarity behavior rather than being silently corrected.
- Capacitor alternatives change the real RC response. A 1 uF or 220 uF replacement is not treated as an equivalent 33 uF part.
- `ResistorStressDamageSystem` observes solved resistor power and can open a stressed resistor after accumulated overload. It pauses appropriately around unpowered active measurements and does not replace electrical behavior with a fake damage message.
- Repair completion checks function, not merely whether the player clicked the originally selected component. This leaves room for legitimate alternate repairs later.

### Limits

- There is no general jumper or trace repair, so alternate repairs are mostly theoretical in current generated families.
- Secondary stress/damage is concentrated in resistor behavior; the current set does not yet provide a complete current-limited bench or broad component ratings/failure modes.
- A 0.1-ohm short can create large currents in the solver without a corresponding player-controlled supply limit or clear thermal consequence.
- The NPN low-beta replacement value `.1` is a poor model of a plausible catalog substitution. A more realistic wrong replacement might be a device with low but credible gain, wrong pinout, insufficient Vce rating, or a device family mismatch—provided the physical model can represent it honestly.
- The current challenge lifecycle treats `COMPLETED` as terminal for interaction purposes. The architecture documents that later board changes can still affect electrical behavior but do not retract the first verified repair. That is defensible as a job-record decision, but it is not the same as a customer-return monitor and should be explicit in the product model.

### Recommendation

Do not prevent wrong repairs merely because they are wrong. Instead, define which wrong repairs are safe, which cause immediate functional failure, and which accumulate defensible stress. Completion should be based on a repeatable customer-function test and should be distinct from a latched score/job record if the simulator later supports post-repair user mistakes.

## 12. Completion / Customer Return Semantics

### Current behavior

The challenge controller will not leave preparation until healthy behavior, faulted behavior, and a compatible scenario have been validated. At READY, the selected fault must remain applied. A functional repair is accepted only when the family contract reports `CORRECTLY_RESTORED`; with the Quick Play finish action, `finishJob()` transitions to `COMPLETED` only after that status is true (`GeneratedChallengeController.java:96-149`). Some non-Quick-Play paths can auto-complete after a correct restored status.

This is a good invariant: finding or replacing the originally faulted part is not enough by itself. The board must behave correctly.

### Missing customer-return layer

Current scenarios mostly stop at a single complaint condition and a repair predicate. A realistic return test should include:

- the customer’s stated operation, not merely a static meter check;
- all relevant external inputs in their normal states;
- a repeat or duration test for intermittent/timing families;
- confirmation that no unrelated healthy function was lost;
- a clear definition of whether a later user-caused failure reopens the job or only affects the simulation after completion.

The NPN family is the clearest current issue: the family contract can test both ON and OFF, but the player cannot run those commands. The completion contract is therefore more capable than the normal UI.

## 13. Automated Solvability Validation

### What is already automated

The current codebase has a strong base of structural and electrical checks:

- generated component and auxiliary elements must be owned and active;
- every PCB pad must have an owned solver endpoint;
- every board net must have finite, consistent endpoint voltages;
- healthy validators check family operating ranges;
- fault validators check the selected fault’s expected symptom;
- scenario catalogs select only solver-compatible complaints;
- fault ownership validation prevents private fault infrastructure from claiming unrelated board identity;
- layout validation checks board geometry, pads, traces, clearances, and footprint relationships;
- mutation and replacement paths preserve physical identity and trigger verification.

The Task 37 report also records forced NPN seed/fault verification, which is valuable reachability evidence. It is fault coverage evidence, not diagnostic-distinguishability evidence.

### Missing automated checks

Add a separate, family-agnostic solvability verifier rather than placing more assumptions in `GeneratedBoardVerifier`:

1. **Candidate enumeration.** Generate the healthy board and each compatible candidate fault independently, with the same physical layout and normal player action set.
2. **Observation enumeration.** Build the set of legal probe pairs, power states, component isolation actions, and player-controlled input states available from the rendered board.
3. **Signature sampling.** For each candidate and each safe observation sequence, record solver-derived values/classifications with tolerances, not exact floating-point equality.
4. **Separation proof.** Require at least one allowed sequence that separates the selected fault from every plausible alternative, or mark the challenge as intentionally multi-hypothesis and require a valid branch/repair policy.
5. **Repair reachability.** Verify that a player can install, reconnect, isolate, or otherwise correct the selected fault through currently exposed operations.
6. **Re-test proof.** Verify that the required customer operation can be issued through player-facing controls and that the repaired behavior is stable across the stated test profile.
7. **No structural single-target failure.** Report when all reachable faults in a family have the same repair slot unless the family is explicitly tagged as a guided beginner exercise.
8. **Seed matrix.** Run the check across hundreds or thousands of deterministic seeds once composition is enabled, retaining rejection reasons for debug mode.

### Human review still required

No numeric verifier can fully judge whether a trace layout is visually understandable, whether a complaint sounds like a real customer, whether a healthy red herring is purposeful, or whether a technician would reasonably choose the proposed next measurement. Automated signature coverage should narrow the review surface, not replace human/player review.

## 14. Difficulty Model

### Current difficulty

The current family order suggests a reasonable learning progression:

- LED: direct series, beginner.
- Diode: direct series with polarity/diode-test reasoning, beginner/intermediate.
- Parallel dual: in-circuit parallel ambiguity and healthy comparison, intermediate.
- RC: stored energy and time behavior, intermediate.
- NPN: multiple rails, control path, load path, and ambiguous open faults, intermediate/advanced in concept.

In practice, NPN is simplified by the hidden command seam and the one fixed topology. Diode and RC are simpler than their component count suggests because their reachable fault target is fixed.

### Recommended difficulty dimensions

Scale difficulty through observable electrical reasoning, not information denial:

1. Number of functional blocks and genuine interfaces.
2. Number of plausible fault owners with distinct physical locations.
3. Parallel paths and measurement contamination.
4. Need for isolation or controlled input transitions.
5. Number of rails and their dependencies.
6. Temporal/intermittent behavior with suitable instruments.
7. Healthy auxiliary circuitry and reference signals.
8. Wrong-repair consequences and accumulated stress.
9. Routing complexity and visual trace-following distance.
10. Complaint ambiguity that remains resolvable through normal measurements.

Do not increase difficulty by hiding probeable pads, suppressing ordinary component information, randomly disabling necessary controls, or presenting a symptom that no available action can investigate.

## 15. Prioritized Recommendations

### P0 — before substantial multi-subsystem composition

1. **Add a diagnostic-solvability contract and verifier.** Make observability, distinguishability, and repair reachability explicit generation acceptance criteria. Start with the existing five families and reject any seed/fault route without a separating plan.
2. **Make every normal fault physically targetable and diagnostically separable.** Expand single-target families only when additional candidates have real solver effects, physical ownership, accessible measurements, and a repair path. Do not solve answer leakage by hiding the workbench capabilities.
3. **Expose player-controlled functional inputs.** Replace the NPN hidden command-only workflow with a real board input/control action that the player can set and re-test. Give RC a concrete downstream observation/input contract before relying on it for higher difficulty.
4. **Make fault identity match the physical interruption.** For series switches inside a component, model them as an explicit internal lead/terminal failure. For trace or connector faults, wait until trace/connector repair primitives exist and expose the correct path identity.

### P1 — before broad auxiliary and multi-rail generation

5. **Introduce semantic module/interface contracts.** Extend the logical board model beyond `BoardNet` string IDs with enough metadata to validate nominal voltage domain, source/sink role, signal/control role, ground relationship, and allowed interconnection. Keep electrical behavior in CircuitJS, but use the contract to reject nonsensical compositions.
6. **Partition deterministic randomness.** Use independent seeded substreams for topology, values, fault, layout, scenario, auxiliary circuitry, and routing. Preserve the seed as a reproducible challenge identity without coupling unrelated changes.
7. **Add purposeful healthy circuitry.** Build one bounded auxiliary/red-herring task around the parallel branch and RC decoupling lessons. Validate that the auxiliary subsystem remains healthy and does not create an alternate explanation.
8. **Add per-rail power and input control.** A global power button is not enough for multi-rail boards; support deliberate rail isolation and realistic input application before adding rail shorts or enable chains.

### P2 — later capability expansion

9. Add current measurement and current-limited bench-supply behavior before making high-current shorts or stress cascades normal.
10. Add scope/frequency/capacitance modes only with families whose solvability contracts require them.
11. Add jumper wires, trace cuts, trace repair, and component-specific secondary damage with physical ownership and stress evidence.
12. Expand routing/placement only after the router can report both electrical connectivity and diagnostic readability constraints.

## 16. Recommended Bounded Future Tasks

These are recommendations for future milestones, not work performed by this audit.

### Task A — Existing-family diagnostic solvability proof

Scope only the five current normal families and their reachable normal seed envelopes. Add a verifier/test harness that enumerates all 11 normal family fault routes, legal probe endpoints, current power/mutation actions, and solver-derived signatures. Produce rejection reasons for ambiguous or unrepairable routes. Do not add a new family or new instrument.

**Acceptance criteria:** every admitted route has a documented measurement/action plan; ambiguous pairs such as NPN C-E open versus load-path open either have a verified separating sequence or are rejected; no report relies on hidden controller state.

### Task B — Player-facing NPN operation and repair test

Expose a normal player control that drives `CONTROL_INPUT` through real board state. Remove the need for the scenario/repair validator to be the only actor that changes `controlCommand`, while retaining a private deterministic test hook for automated verification. Clarify whether the load-path open is an internal RLOAD lead fault or a distinct trace/path fault.

**Acceptance criteria:** a normal player can reproduce both ON and OFF conditions, distinguish all four current NPN faults with the available meter/mutation actions, and complete the repair by running the same functional profile used by the contract.

### Task C — Serviceability diversity in the existing simple families

Add one additional physically owned and solver-backed candidate fault to a family at a time. Start with LED or diode, not a new topology. Ensure the candidate is physically plausible, has a visible diagnostic path, has a repair/isolation capability, and does not make the complaint ambiguous without a fair next action.

**Acceptance criteria:** target-slot structure no longer identifies the fault before measurement; all candidates pass healthy/faulted/repaired and solvability verification across the normal seed matrix.

### Task D — Semantic module interfaces before composition

Define a minimal typed interface for rails, control inputs, loads, references, and grounds. Use it to validate one composed board with one healthy auxiliary block. Keep layout and CircuitJS adapters separate from the semantic contract.

**Acceptance criteria:** invalid voltage-domain or incompatible interface connections are rejected before PCB rendering; the composed challenge has an accessible diagnostic plan and a meaningful healthy red herring.

### Task E — Technician-facing customer return profile

Separate “correct repair detected” from “job closed.” Define a player-facing repeatable customer operation for each family, including both NPN states and RC timing. Keep completion functional and solver-backed; do not reduce it to clicking the original target.

**Acceptance criteria:** a repaired board passes the same visible operation a technician would perform, unrelated healthy functions remain valid, and the product behavior after a later user-caused fault is explicitly defined.

## 17. File/Class Watchlist

| File/class | Audit relevance |
|---|---|
| `QuickPlayFamilyRegistry.java`, `QuickPlaySelector.java` | Defines the normal-player family and seed boundary; update only when reachability evidence is available. |
| `LedIndicatorGenerator.java` | Direct-series baseline; R1-target bias and connector candidate compatibility. |
| `DiodeProtectedIndicatorGenerator.java` | Developer-only short versus normal open; single-target serviceability. |
| `ParallelDualIndicatorGenerator.java` | Best current healthy-comparison/red-herring pattern; branch fault and in-circuit measurement behavior. |
| `RcDelayGenerator.java`, `RcDelayTemporalBehavior.java` | Temporal profile, stored-energy readiness, C1-only fault ownership, hidden timing assumptions. |
| `NpnLowSideSwitchGenerator.java`, `NpnLowSideSwitchFamilyState.java` | Hidden control command, load-path ownership, two-rail semantics, fault ambiguity. |
| `GeneratedFaultEngine.java`, `GeneratedFaultEffect.java` | Effect vocabulary, private switch/shunt ownership, compatible-candidate filtering. |
| `GeneratedChallengeController.java` | Preparation, scenario, repair, READY/COMPLETED lifecycle, terminal completion semantics. |
| `GeneratedBoardVerifier.java` | Structural/electrical guarantees; natural home for a separate solvability verifier boundary, not necessarily all of its implementation. |
| `GeneratedScenario.java`, `GeneratedScenarioLibrary.java` | Complaint wording, solver compatibility, hidden scenario presentation state. |
| `StandardInstrumentModeProviders.java`, `InstrumentController.java`, measurement adapter classes | Available diagnostic evidence and power-state restrictions. |
| `BoardModificationController.java`, physical runtime/capability classes | Actual legal isolation, removal, restore, and replacement actions. |
| `BoardPowerController.java`, `GeneratedExternalPowerBindings.java` | Current global rail behavior and future per-rail expansion. |
| `ResistorStressDamageSystem.java` | Real stress/damage boundary; future component damage and current-limit work. |
| `BoardNet.java`, `ExternalBoardPowerInput.java`, `GeneratedBoardInstance.java` | Logical board ownership and the missing semantic interface/domain layer. |
| `SeededPcbLayoutGenerator.java`, `RcDelayPcbLayoutFactory.java`, `NpnLowSideSwitchPcbLayoutFactory.java` | Seed/layout diversity, routing/placement validation, future diagnostic readability checks. |
| `docs/ROADMAP.md`, `docs/CODEX_TASK_REPORT.md` | Milestone order and accepted baseline; do not silently reorder based on this audit. |

## 18. Risks and Open Questions

1. **Visible layout evidence:** source inspection shows stable pad bindings and layout validation, but a future player-facing audit should verify that all intended separating pads are visually probeable and not obscured by footprints/traces in the rendered browser.
2. **In-circuit thresholds:** continuity’s 50-ohm threshold and diode-test compliance behavior are useful UI classifications, but future low-ohm loads, protection networks, and semiconductor paths need family-specific tolerance review.
3. **NPN fault naming:** confirm whether `LOAD_PATH_OPEN` is intentionally an internal RLOAD lead/attachment failure. If so, expose that physical identity consistently in the part model; if not, bind it to a separate trace/path component once trace repair exists.
4. **NPN replacement realism:** replace beta `.1` with a credible wrong-part model only when the physical/catalog system can explain the distinction without adding fake semantics.
5. **RC observation contract:** define what the player operates and observes after power-up. A hidden temporal profile is useful for validation but should not be the only reason a scenario is considered solvable.
6. **Per-rail power:** decide whether a board’s external inputs are one bench supply connection or independently controlled supplies. The answer affects fault compatibility, measurement preparation, and future stress modeling.
7. **Completion after damage:** decide whether `COMPLETED` is a terminal job record, a live functional state, or both. A terminal record should not be mistaken for an assertion that the board can never be damaged afterward.
8. **Fault probabilities:** current deterministic selection is excellent for tests, but future random fault weighting should reflect plausible field failures without making common faults the only fair ones.
9. **Multiple faults:** the current contract assumes one selected generated fault. Multi-fault challenges should wait until single-fault distinguishability and repair reachability are formally checked.
10. **Human readability:** solver separability does not guarantee that a competent technician would choose the measurement sequence. Retain normal-player browser validation for rendered boards and complaints as complexity increases.

### Final audit position

TroubleshootJS has the correct electrical foundation and several genuinely fair diagnostic exercises. The project should now invest in proving and presenting solvability before it invests in making boards merely larger or more random. Complexity should add plausible alternative explanations together with accessible ways to eliminate them; otherwise the simulator will remain electrically correct while becoming diagnostically unfair.
