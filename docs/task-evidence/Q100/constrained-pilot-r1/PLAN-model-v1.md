# Q100-1: private functional and electrical pilot

Status: planning and implementation in progress. No RB100 behavior has been run
for this pilot and no Q100 qualification is claimed.

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
