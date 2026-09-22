# E02 rail-producing regulator qualification

Date: 2026-09-22

## Delivered boundary

`RailRegulationContract` declares the reusable four-terminal INPUT/OUTPUT/RETURN/
ENABLE role. `LinearRegulatorElm` and `AveragedSwitchingRegulatorElm` are
separate finite CircuitJS implementations for 12 V, 5 V and 3.3 V contracts.
RETURN is the tested local reference. `E02FiniteSourceElm`, the regulator dump
types and the physical TO-220 mapping keep source, terminal and current model
identities visible to dependency capture and board rendering.

The averaged alternative is deliberately an averaged power-balance model. It
does not advertise or draw a switching waveform/frequency.

## Acceptance coverage

`E02RegulatorContractTest` executes all six declared contracts (linear and
averaged 12 V, 5 V and 3.3 V) through the real solver. Each matrix row covers
nominal settling, finite load/current limit, disable/enable, dropout/headroom,
input loss/recovery, input/output power balance, RETURN-reference shift and
timestep sensitivity. It also rejects hidden independent output, unsupported
backfeed and invented switching-waveform behavior.

Focused native result: **PASS — 238 assertions**.

Final JDK8/GWT production build: **PASS** (all configured permutations; 76.699 s
compile; linker success). The fresh compiled debug route
`?tsjDebug=1&tsjChallenge=led&seed=0&tsjVerifyE02=1&running=true&run=e02-final-final`
returned `PASS:e02`; its [browser receipt](e02-regulator-report.json) records
169 assertions across the six real element variants in 23 ms, including
measured enable, headroom/dropout, finite-load and distinct overload/disabled/
input-loss/restored-state readings. RETURN-offset and timestep comparison stay
in the independent native six-variant matrix; the compiled route complements,
rather than replaces, that solver contract.

## Limits

This is not an offline converter, ripple, magnetics or arbitrary-rail model. The
finite Norton approximation is valid only inside the explicit declared envelope;
unsupported operating points fail closed.
