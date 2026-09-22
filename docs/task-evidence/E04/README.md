# E04 sensor-conditioning and control-decision qualification

Date: 2026-09-22

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

## Native/compiled coverage

Focused contracts verify the threshold/loading matrices, direct/hysteretic
variants, raw-versus-conditioned sensor nets, physical seam mapping, all three
fault symptoms and repair/retest, U1 catalog mutation and stable dump/load
identity: **PASS — E04 90 assertions; SensorControl family 44 assertions.** The
family contract independently pins the complete diagnostic population at 36
samples per hypothesis and 108 across RBIAS/RREF/RFB; it prevents the browser
benchmark from silently losing a declared public probe or passive check.

A fresh JDK8/GWT production build passed (all configured permutations; 76.699 s
compile; linker success). Its compiled Alpha sensor-control cases passed for
both topology variants: seed 0 direct/linear in the [first receipt](alpha-case-25-report.json)
and seed 1 hysteretic/averaged in the [second receipt](alpha-case-26-report.json):

- `assertions: 199`, `acquisitions: 8`, `mutationChecks: 103`;
- all seven physical components, including U1, passed remove/replace/reinstall
  compensation; and
- `repairPassed: true` with the original owner restored; and
- the final direct rerun completed in `3043 ms` with `activeCase: 26`, while
  the hysteretic rerun completed in `2620 ms` with `activeCase: 27`.

## Normal-player receipt

Fresh production preview route:

`?tsjChallenge=sensor-control&seed=0&difficulty=EASY&run=e04-player-final`

Using only visible normal-player controls, the flow selected LOW, MID and HIGH,
ran the customer retest (which failed), selected RBIAS on the board, powered the
board down, removed RBIAS, added the compatible 10 kOhm SENSOR_SOURCE_RESISTOR
to the tray through the visible shop, selected the physical loose part, installed
it in RBIAS, powered back on and ran the customer retest. The final visible status
was: `Customer retest passed. The reported behavior is resolved.`

Two fresh Browser screenshots were captured and inspected: [initial board
preparation](e04-player-initial.svg) and [the repaired customer retest](e04-player-final.svg).
The SVG evidence wrappers contain the Browser's captured JPEG pixels so the
screens remain repository-native text artifacts. The player interaction and the
visible status receipt both passed.

## Limits

No broad op-amp library, noisy analog campaign, sensor animation, or admitted
power-loss fault vocabulary is claimed. A future supply fault must enter with a
causal, physical, legally separable observation/repair contract.
