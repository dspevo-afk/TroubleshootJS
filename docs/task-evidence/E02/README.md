# E02 rail-producing regulator qualification

Date: 2026-09-23

## Delivered boundary

`RailRegulationContract` declares the reusable four-terminal INPUT/OUTPUT/RETURN/
ENABLE role. `LinearRegulatorElm` and `AveragedSwitchingRegulatorElm` are
separate finite CircuitJS implementations for 12 V, 5 V and 3.3 V contracts.
RETURN is the tested local reference. `E02FiniteSourceElm`, the regulator dump
types and the physical TO-220 mapping keep source, terminal and current model
identities visible to dependency capture and board rendering.

The normal-output branch has the declared 0.1 ohm regulation resistance, not
the former permanent `Vnom/Imax` series term. A nonlinear Norton limiter is
stamped into CircuitJS at 0.200 A; its tiny flat-branch conductance keeps the
shorted graph finite. The v2 contract explicitly declares 0.90 of maximum
current (0.180 A) as its usable regulated envelope and a 5% voltage tolerance.
Construction rejects a declared envelope whose own `Iusable × Rout` droop
exceeds that tolerance. The output target remains derived from solved input,
dropout, enable and local return. Linear input current includes delivered and
quiescent current; the averaged alternative uses solved output power and its
declared efficiency. Neither advertises a switching waveform/frequency.

## Acceptance coverage

`E02RegulatorContractTest` executes all six declared contracts (linear and
averaged 12 V, 5 V and 3.3 V) through the real solver. Each matrix row checks
light load, 25/50/75/90% of the declared usable 0.180 A envelope, the exact
0.200 A onset and a 0.1 ohm hard overload. Separate 95% and 99% hard-limit
samples close the gap between the usable envelope and the limiter knee and
prove that current limiting does not begin early. These are ordinary load
resistances near nominal output, not engineered collapse points presented as
regulation. It also covers enable low/mid/high, dropout/headroom, source
loss/recovery and return offset. Power is checked independently from the
regulator's reporting helpers by using the contract equations, solved load and
finite-source KCL, Thevenin-source loss, no-energy-creation inequalities and
the averaged variant's declared efficiency. Timestep stability uses a real
1 mF output capacitor and the same 1000 ohm to 10 ohm load step over an equal
2 ms interval at fixed 100 us and 25 us timesteps. Contract and dump negatives
reject incompatible envelopes, a usable fraction at the hard knee, and
unsupported backfeed.

Fresh native readouts (each cell is actual solved output V/A; both variants
were run separately and agreed at the shown precision):

| Variant | Light | 25% usable | 50% usable | 75% usable | 90% usable | 95% max | 99% max | Limit onset | 0.1 Ω short |
|---|---|---|---|---|---|---|---|---|---|
| Linear 12 V | 11.9988 / .0120 | 11.9955 / .0450 | 11.9910 / .0900 | 11.9865 / .1350 | 11.9838 / .1620 | 11.9810 / .1900 | 11.9802 / .1980 | 11.9800 / .2000 | .0200 / .2000 |
| Linear 5 V | 4.9995 / .0050 | 4.9955 / .0450 | 4.9910 / .0900 | 4.9865 / .1350 | 4.9838 / .1620 | 4.9810 / .1900 | 4.9802 / .1980 | 4.9800 / .2000 | .0200 / .2000 |
| Linear 3.3 V | 3.2997 / .0033 | 3.2955 / .0450 | 3.2910 / .0900 | 3.2865 / .1350 | 3.2838 / .1620 | 3.2810 / .1900 | 3.2802 / .1980 | 3.2800 / .2000 | .0200 / .2000 |
| Averaged 12 V | 11.9988 / .0120 | 11.9955 / .0450 | 11.9910 / .0900 | 11.9865 / .1350 | 11.9838 / .1620 | 11.9810 / .1900 | 11.9802 / .1980 | 11.9800 / .2000 | .0200 / .2000 |
| Averaged 5 V | 4.9995 / .0050 | 4.9955 / .0450 | 4.9910 / .0900 | 4.9865 / .1350 | 4.9838 / .1620 | 4.9810 / .1900 | 4.9802 / .1980 | 4.9800 / .2000 | .0200 / .2000 |
| Averaged 3.3 V | 3.2997 / .0033 | 3.2955 / .0450 | 3.2910 / .0900 | 3.2865 / .1350 | 3.2838 / .1620 | 3.2810 / .1900 | 3.2802 / .1980 | 3.2800 / .2000 | .0200 / .2000 |

The unrounded native receipt records short currents of 0.20000001196 A at
12 V, 0.20000000496 A at 5 V and 0.20000000326 A at 3.3 V. The explicit
test ceiling is `Imax + 1e-7 A`: the 1e-9 S limiter leakage over the bounded
input voltage contributes at most 2.4e-8 A, with the remainder reserved for
solver rounding. The complete machine-readable six-case capture is
[e02-native-regulation-points.json](e02-native-regulation-points.json); the
compiled developer verifier retains solved values per point too.

The final-source native gate passed 1,429 assertions under JDK 8. After the
five-permutation production build, the compiled browser verifier passed 1,321
assertions across all six role/implementation cases and retained every solved
normal-load, pre-limit, onset and overload reading in
[e02-regulator-report.json](e02-regulator-report.json). The final maintained
current-contract gate and renderer/provider boundary also passed.

## Limits

This is not an offline converter, ripple, magnetics or arbitrary-rail model. The
finite Norton approximation is valid only inside the explicit declared envelope;
unsupported operating points fail closed.
