# E04 sensor-conditioning and control-decision qualification

Date: 2026-09-23

## Delivered boundary

E04 composes a selected bounded 5 V E02 rail, finite raw input, external sensor,
reference divider and loaded control output. Deterministic seed 0 selects the
direct threshold/linear-rail implementation; seed 1 selects the hysteretic
regenerative/averaged-rail implementation. LOW, MID and HIGH are ordinary
player operations. The `DecisionElement` retains only its live control state and
serializes stable declaration data, so dependency identity never treats a latch
state as an answer.

The admitted diagnostic population is deliberately bounded to three physical,
serviceable resistor-open hypotheses: RBIAS, RREF and RFB. Brownout and
reference-loss are tested solver states, not silently admitted missing-supply
fault candidates. U1 is a physically mapped E02 regulator and can be serviced
through the catalog, but it is not an E04 fault locus.

The decision element is now the visible five-terminal U2, with sensor,
reference, rail, output and return pads on the actual CircuitJS element. The
reference divider's low resistor is visible `RREF_LOW` in both variants; the
external regenerative resistor is visible `RFB_HYST` only in the hysteretic
variant. Each has live resistor backing, two mapped pads, detachable leads,
physical definition, shop/service ownership and routed copper. Direct boards
have nine components and hysteretic boards ten, rather than the old seven.

The complete model census is explicit: selected U1 regulator and its sources,
U2 decision, RBIAS/RREF/RFB seams, RREF_LOW and optional RFB_HYST are mapped;
the rail and sensor input loads are documented U2 internal behavior; the raw
input is J1 external infrastructure, the sensor source J2 external
infrastructure, and the customer output load J3 external infrastructure. The
return is declared infrastructure, while model wires are explicit electrical
interconnect. Production construction validates each mapped owner against a
real board component, all U2/support pad and solver bindings, and the entire
post-service graph against model rows, component leads/backings and declared
fault helpers. An unclaimed added resistor fails closed.

## Native/compiled coverage

Focused contracts verify the threshold/loading matrices, direct/hysteretic
variants, raw-versus-conditioned sensor nets, physical seam mapping, all three
fault symptoms and repair/retest, U1 catalog mutation and stable dump/load
identity: **PASS — E04 303 assertions; SensorControl family 268 assertions.** The
family contract independently pins the complete diagnostic population at 36
samples per hypothesis and 108 across RBIAS/RREF/RFB; it prevents the browser
benchmark from silently losing a declared public probe or passive check. Fresh
native [final ownership census](e04-final-ownership.txt) lines for seeds 0 and
1 enumerate every published solver element's exact class and physical,
external, interconnect, pad, or declared helper owner; the post-service
negative adds an unexplained resistor and verifies rejection. The ten-pair
physical matrix passes 20/20 generated boards per current player family/profile
pair. The serviceability contract passed 964 assertions and the U04 session
contract passed 3,423.

The family contract executes partitioned real unpowered service paths: U1 on
the direct seed, U2 on both seeds, the three common passives on the direct seed,
and the two variant-specific support passives on the hysteretic seed. It
validates each empty physical slot against the active graph, catalog-installs a
distinct compatible part, and then requires U1's four posts, U2's five posts,
and every exercised resistor's current primary/auxiliary bindings to follow the
replacement solver owner while the physical board endpoints remain fixed. The
compiled cases below supply the full nine-case direct and ten-case hysteretic
per-variant service sweeps. Together these checks prevent a valid replacement
from being rejected merely because it differs from the construction-time model
object, without weakening the generic active-graph, canonical-inventory or
immutable-endpoint checks.

After the final JDK 8 production build, compiled Alpha case 25 (direct, seed 0)
passed 260 assertions, 149 mutation checks and all nine physical service cases;
case 26 (hysteretic, seed 1) passed 285 assertions, 149 mutation checks and all
ten physical service cases. Their exact receipts are
[alpha-case-25-report.json](alpha-case-25-report.json) and
[alpha-case-26-report.json](alpha-case-26-report.json).

## Normal-player receipt

The rebuilt final-source production preview route was:

`?tsjChallenge=sensor-control&seed=0&difficulty=EASY&run=e04-player-final-dynamic`

Visible controls generated and accepted the ticket, selected LOW, MID and HIGH,
and ran the unrepaired customer retest, which failed. Board View then selected
RBIAS; the player powered down, removed it, filtered the Shop to
`SENSOR_SOURCE_RESISTOR`, acquired the compatible 10 kOhm ±5% part, selected the
loose tray part, installed it in RBIAS, restored power and reran the customer
retest. The final visible status was: `Customer retest passed. The reported
behavior is resolved.` The current [initial board](e04-player-initial.svg) and
[repaired customer retest](e04-player-final.svg) screenshots visibly include
U2 and the added support hardware. The fresh final-build
[normal-player receipt](e04-normal-player-flow.json) records all 21 visible
inputs, unchanged replay identity, normal-route privacy, Codex In-app Browser
execution, successful post-install ownership validation and zero page errors.

The ordinary New Board menu also exposed nine EASY families including Sensor
Control plus the composed MEDIUM family. A fresh ordinary-menu selection launched
accepted replay `tsj-alpha/3/EASY/SENSOR_CONTROL/7302857472280055372`, reached
its Customer ticket and exposed U1, U2, RBIAS and RREF_LOW on the workbench;
the [visible-launch receipt](e04-quickplay-visible-launch.json) records privacy
and zero page errors. The clean procedural browser matrix separately passed
three cases for each of all ten current family/profile pairs, exact replay,
power isolation, privacy and owned-process cleanup.

## Limits

No broad op-amp library, noisy analog campaign, sensor animation, or admitted
power-loss fault vocabulary is claimed. A future supply fault must enter with a
causal, physical, legally separable observation/repair contract.
