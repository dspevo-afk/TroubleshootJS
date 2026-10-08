# E06 converter fidelity decision

Accepted 2026-10-08 for the declared solver envelope. Q60 will use an **opaque
averaged converter module**, counted as one package for its hidden conversion
stage. Actual external rectification, storage, filtering, loading and optocoupler
feedback remain causal. Physical ownership, procedural generation, diagnostics,
session restoration and normal player admission are Q60 work, still unstarted.

The candidate is based on `6730de219f25306b65da3d336b20f1cd90a82896` plus the exact
[current source pins](current-input-pins.json). Raw developer reports intentionally
retain candidate/NOT_QUALIFIED fences: this decision qualifies the stated solver
observables for guarded integration, not a playable product.

## Model and observable boundary

Both implementations use CircuitJS with the same actual 120/90 V RMS, 60 Hz source,
22 ohm source impedance, two-pole isolation, fuse, bridge and 47 uF bulk capacitor.
The detailed two-switch forward reference has real transformer/reset/rectifier
paths and bounded finite-resistance switch channels with body diodes. Its 2 kHz
switching is a private solver reference; the existing 250 Hz player observation
limit is unchanged. The averaged element has separate primary and secondary
terminals, no implicit ground, actual input-dependent energy transfer, finite
output impedance/current limit, and the same accepted-step startup/enable/PI
control. Its input accounting includes output power, declared 85% efficiency,
winding loss and quiescent consumption. Actual external primary-powered bias is
accounted once. The pilot's external enable source is separately accounted and
must be replaced by honest physical control ownership for Q60.

The shared external output network includes a 20 mH inductor, 470 uF capacitor,
48/24 ohm load, bleed path and causal Zener/opto feedback. The nominal feedback
operating point is approximately 11.2 V. The 12 V control command is not an ideal
output supply. Averaged gate, switch and winding observations are unavailable;
hidden switching parts cannot be advertised as individual repair targets.

The detailed transformer and selected storage use backward Euler; the output
inductor uses trapezoidal integration. Receipts distinguish physical dissipation,
stored energy, backward-Euler numerical damping and trapezoidal endpoint error.
No global MOSFET, diode, solver tolerance, iteration, readiness or runtime limit
was changed. Saturation, avalanche, gate charge, switching loss, EMI, thermal,
short/overload and arbitrary line/load/frequency behavior are unqualified.

## Qualification

[Frozen criteria](criteria.json) were set before the expanded matrix, using the
already retained nominal pilot measurements. They apply to explicit observables
and three healthy 50 ms windows, not arbitrary transient stability.

| Check | Result | Acceptance or scope |
| --- | --- | --- |
| Final native matrix | PASS, 24 cases, 1,320,000 accepted steps, 324,172,762 assertions | 47.128 s host; 25.925 s electrical case time |
| Coarse/fine output mean difference | At most 0.01569% | 2% bound |
| Detailed/averaged output mean difference | At most 0.65281% | 5% bound |
| Detailed/averaged stage input energy difference | At most 10.19132% | 15% bound |
| Startup crossing difference | At most 0.6425 ms | 10 ms bound at 10.8 V |
| Healthy-window ripple / mean drift | At most 2.16568% / 0.35717% | 5% / 2% bounds |
| Energy closure | Maximum absolute residual 18.849 uJ | 2% transferred stage energy + 10 uJ; every case/phase passes |
| Reference output difference | At most 1.009e-10 V | 1 mV bound |
| Solver convergence | At most 20 iterations; no elevated opto gmin samples | Existing limits; gmin at baseline 1e-12 S |
| Actual production JDK8/GWT | PASS, five permutations | build-r2, 96.728 s |
| Actual compiled positive and forced-after-solve cleanup | PASS | compiled-r2, 20.002 s; strict JSON booleans |
| Final resource audit | PASS | 47 recorded instances absent; two ports closed |
| Visible player interaction | NOT APPLICABLE | No visible player-flow change; compiled evidence is not player input |

The [native matrix](native-matrix.json) contains four coarse/fine operating
sequences (both models), four actual feedback-removal/fuse-opening cases, and
sixteen reference/insertion/offset/bond cases. Each sequence starts disabled,
enables at 50 ms, doubles load at 200 ms, reduces line at 350 ms, disables at
500 ms, re-enables at 600 ms and isolates both source poles at 700 ms. Storage
and accepted control history survive each mutation and reanalysis. Disable has
one accepted-step latency, followed by strict no-drive checks. Actual sense
resistor removal retains its prior energy record; the blown fuse retains its
finite 1 Gohm leakage model.

Reference cases use primary, secondary, automatic and no explicit ground,
reversed insertion order, actual +10 V offsets, and one or two actual 1 Mohm
bonds. One bond has no physical return (nominal current below 1 nA); two bonds
produce the expected differential closed-loop current. Existing grounds are
removed before selecting a reference. Topology checks follow actual posts and
connection declarations. Every completed case restores and disposes its private
graph, elements, executor and singleton ownership.

The published terminal-current KCL diagnostic reaches **10.558 mA** in the
fine-step detailed sequence. Inherited nonlinear junction/body-diode raw-current
reporting differs from the limited tangent and accepted solve tolerances. This
is not a precision-current guarantee; signed energy closure is independently
reported. Coarse/fine stage input energy changes by up to 2.9441%, also retained
as a measurement rather than an invented acceptance band. The two models are
not equivalent in pulsed versus continuous internal current.

Source isolation does not mean discharged: at 1.2 s the primary bulk still holds
78.89-79.17 V and about 0.147 J. Q60 must provide a real discharge/readiness path
without waiving the existing guard. Feedback removal changes the final output
to approximately 11.94/12.00 V; fuse opening drains the output to approximately
0.67/0.46 V while bulk storage remains. These are bounded causal fault responses,
not a complete Q60 diagnostic population.

## Execution, failures and review

Maintained native command: `scripts/verify-current-contracts.ps1 -JavaHome <JDK8>
-Suite E06ConverterPilotContractTest -ReceiptOutputPath <OWNED_RECEIPT>`.
Production build: `scripts/build.ps1 -JavaHome <JDK8> -Target Compile -Style OBF`.
[Host receipts](gate-hosts.json) retain operation and cleanup separately;
[source binding](source-binding.json) records zero changed consumed inputs for
native-r9 (1,314 files), build-r2 and compiled-r2 (1,200 files each).

The private compiled verifier requires `tsjDebug` and `tsjVerifyE06`, runs both
actual fresh 200 ms graphs in deferred bounded batches, and forces failure only
after accepted averaged solving. Actual production factory and ChipElm paths
are exercised. [Positive](compiled-positive.json) and
[forced cleanup](compiled-forced-cleanup.json) receipts use the unchanged strict
contract and exact player-owner restoration. [Workload](compiled-workload.json)
and [specification](compiled-spec.json) preserve browser/source bindings. Compiled
batch-endpoint measurements do not replace the native energy/reference matrix.

All 13 attempts remain in the [ledger](attempt-ledger.json). Native r1 failed
UI-less chip construction. r2/r3 failed at 57.63 ms; r4 failed at 62 ms even with
backward Euler. The retained diagnostics distinguish numerical opto conductance
from light and identify the full MOSFET high-side limiter conflict. The bounded
channel r5 solved but oscillated and was rejected for fidelity. Shared PI r6 and
paired r7 passed nominal mechanics only. Compiled r1 failed the strict report
because bitwise booleans became numeric 1; logical conjunction fixed the report.
Native r8 exceeded the unchanged 1 MiB output cap and remains FAIL. r9 aggregates
only recognized informational convergence messages and emits compact electrical
windows; warnings, errors, assertions and budgets remain unchanged. Prior pilot
measurements are [retained](prior-pilot-measurements.json).

The [resource audit](resource-audit.json) records 62 evidence files. The failed
r8 compiler scratch is intentionally preserved, with a complete sanitized
[inventory](r8-preserved-scratch-inventory.json): 2,649 files / 12,449,484 bytes.
No recorded process survives. All eight original untracked files retain their
saved hashes. Raw logs and pre-refinement source snapshots remain task-local;
public receipts replace personal absolute paths with placeholders.

Root reviewed integrated models, fixtures, tests, private hook and source
binding. Independent read-only reviews covered averaged power/control, private
lifecycle and the matrix energy/reference oracles. There was no blocker to this
bounded decision. Full unrelated native matrices, Q30 cold/warm corpora and
player qualification were not rerun. Q30's unknown 91.616 s failure, six missing
historical scratch inventories and late blocked historical release audit remain
unchanged. The [decision](decision.json) hands Q60 one honest module plus causal
external parts; normal family admission remains disabled for this new content.
