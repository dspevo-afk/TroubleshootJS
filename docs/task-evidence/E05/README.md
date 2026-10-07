# E05 bounded AC, storage and isolation qualification

**Accepted locally, 2026-10-07.** Commit and normal push are recorded separately.
This scope covers fixed simulator fixtures and an eleven-package developer bench.
E06 and Q60 remain unstarted.

JAC, F1, T1, D1-D4, C1, RBLEED, RLOAD and JOUT have actual CircuitJS backing and
current probe endpoints. Input is 120 V RMS/60 Hz, peak `120 * sqrt(2)`, with
22 ohms series impedance, simulated I-squared-t protection and two real isolation
poles. The linear transformer is 4 H, ratio 0.1, coupling 0.999; P1/P2/S1/S2 map
to posts 0/2/1/3. Its fixed body is unserviceable. JAC's ordinary inspector shows
`Marking: 120 VAC RMS / 60 Hz input`. OFF preserves stored energy. The existing
live temporal hook advances actual solver time in bounded 5 ms increments.

PRI_RETURN, SEC_B and DC_MINUS remain separate references. Numerical ground grants
no earth bond. Immutable terminal domains and insulating-body geometry bind
physical identity. Only the declared body spans its barrier; both-face copper,
lands, connected/lifted leads, vias, plated holes and NPTH drills have no exception.
Default identities, production whitelist and normal 20-40-part admission retain
their behavior. The bench has no normal customer challenge admission.

[Native contracts](native-contracts.json) cover **14 + 1 + 1 suites**: fourteen
unaffected suites reused from native-final, E05Power refreshed in native-r12
(27.668 s; 76 power assertions, twelve reference rows/36,108 assertions, eighteen
electrical rows/323,919 assertions), and installed construction refreshed in
native-r15 (23.718 s; 284 assertions, seven routed nets, 55,436 expansions).
[Input differences and reuse boundaries](source-binding.json) distinguish actual
refreshed sources from unaffected contracts. The only post-r15 CirSim change is
the explicit visual-hold sidebar width; its exact inverse matches the earlier
source bytes and final GWT/visible gates cover the change.

[Production GWT](gate-hosts.json) passed all five permutations in 93.037 s.
[Compiled positive and forced-failure cases](compiled-workload.json) passed in
32.402 s, including 194 installed checks, 48 rendered endpoints, exact owner
restoration, candidate disposal and temporary-meter cleanup. Actual four-rail
readiness is DISCHARGED/READY before and after the final DC measurement. The RC
ratio is 0.5570562 against 0.5570466, following 50 ms of real magnetic settling.

[Visible evidence](visible-workload.json) is an explicit scoped composite.
R3 actions 1-93 completed ordinary source inspection, primary 119.967 V RMS,
secondary 11.981 V RMS, reference refusals, top/bottom DC (bottom 15.34 V), OFF
residual/DISCHARGE, then 331.725 ohms after decay, and exact restoration/PASS:e05.
The whole r3 remains **FAIL**: its borrowed replay helper insisted on seed0 after
the UI correctly produced seed3. R4 passed only the outstanding debug-off seed3
menu/ticket/accept/privacy flow in 17.569 s. Both runs retain actual actions,
cleanup and unchanged input bindings; the foundation was not repeated.
Five [reviewed screenshots](review.json) show the foundation and normal player UI.

[Off-winding diagnosis](off-winding-diagnostic.json) preserves eight cases, forty
windows and 640 samples. Trapezoidal integration retained late primary voltages
of roughly +/-8-69 V with winding energy below 4e-34 J. Existing backward Euler
removed that mode in the isolated rectifier; reference-only transformers retain
trapezoidal integration and energy oracles. Algorithmic dissipation was
0.008992/0.004497 J over 0.1 s at 50/25 us, without a physical-heat or efficiency
claim. Published Shockley-current KCL has maximum residual 4.1670856 mA and maximum
per-sample residual/analytic-bound ratio 0.9996713236. No one-microamp accuracy is
claimed; the original failure remains preserved.

The [attempt ledger](attempt-ledger.json) retains all 33 native/build/browser
attempts, including routing, RC timing, notification, residual-voltage, selector
and replay-helper failures. [Resource audit](resource-audit.json) confirms all
169 recorded instances absent and ten ports closed. Eight original untracked
files retain their bytes. Q30's unknown 91.616-second failure, six missing
inventories and late-release audit remain recorded. No unchanged Q30 corpus was
rerun, and no generation, convergence or readiness limit was raised.

Reproduction uses the maintained `scripts/verify-current-contracts.ps1` with
E05PowerContractTest, E05IsolationGeometryContractTest, E05NameplateContractTest
and E05InstalledFixtureContractTest, then `scripts/build.ps1 -JavaHome <JDK8>
-Target Compile -Style OBF`. The compiled route is
`circuitjs.html?tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyE05=true&running=true`;
add `tsjE05Fail=true` for forced cleanup or `tsjE05VisualHold=true` for ordinary
visible interaction. Exact command, driver and input hashes are retained in this
packet; the visible action record identifies the real controls and observations.
