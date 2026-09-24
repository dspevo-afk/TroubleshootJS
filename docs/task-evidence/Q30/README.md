# Q30 multi-rail procedural qualification — blocked

**Current status:** Q30 remains unregistered and unaccepted. Its internal
Q30-P1 physical sub-gate is **PASS, ready for independent review**: the
unchanged fuller two-layer router accepts **54/72** matched placements after
generic medium-board floorplanning, versus **6/72** before; the provider-selected
bounded policy accepts **12/12** seeds, including six held-out seeds. Final-source
JDK 8/GWT and two compiled production-workbench boards pass, with 82/82 pad
targets on both faces, real via/endpoint checks, and visible Browser face,
zoom, pan and DC-probe input. The final-source maintained JDK 8 native suite
passed 66 Java suites, independent oracles, report protocol and cleanup.
D01 fault proof, complete service, player repair/retest, normal admission and performance
qualification remain unproved. See the [floorplan diagnosis](floorplanning/README.md),
[two-layer before/after receipts](two-layer-investigation/README.md), and
[compiled inspection](inspectability/README.md). Earlier compiled failures and
their repairs are retained in the inspection evidence.

The earlier evidence table below records the **pre-P1 Q30 blockage**. Its
0/12 P09 and 6/72 P07 results are historical baselines, not the improved
candidate's route yield. The separate [brownout repair](brownout-investigation/README.md)
remains independent of the physical work.

| Current Q30-P1 check | Result and limit |
| --- | --- |
| Matched actual board | 54/72 P07 fuller successes across all 12 seeds, up from 6/72. P05 and the other two P07 policies remain 0/72. Exact inputs and failure rows are preserved in the matched receipt. |
| Provider-selected policy | 12/12 seeds, including 6/6 held-out; six deterministic placements, at most three P05 and three P07 routes; 17–34 plated vias (median 29), selected area median 6,454,500, aggregate expansions median 4,069,854. No factory links, router-budget increase or normal admission. |
| Compiled workbench | PASS for seed 0 and held-out 37 on final-source JDK 8/GWT build: 33 packages, 82 pad targets on each face, real top/bottom copper and via endpoint, 1,497 assertions per board. Five screenshots and visible face/zoom/pan/probe input are recorded. This is developer-only inspection, not player diagnosis. |
| Regression gate | PASS: final-source JDK 8 maintained native suite, 66 Java suites, independent seed/value/role oracles, report protocol, and verified scratch cleanup. The two historical full-suite timeouts remain failures at their original checkpoint. See [full log](p1-native-full.log) and [receipt](p1-native-full-receipt.txt). |

## Base, scope, and design

All Q30 work started from accepted commit
`e0c368855a3891acd4673e94ce9afa732390e2bf` in a separate worktree. The
ordinary `codex/task43p-final-recovery` checkout has unrelated tracked and
untracked changes and was not edited. E02, E03, E04 and D01 accepted contracts
were used as inputs; no redesign of those milestones is claimed. E04 gained
a declaration-equivalence accessor for Q30 decision replacement and a bounded
undervoltage recovery hysteresis after the exact-board integration trace
found output/load chatter at the declared brownout threshold. The latter has
focused E04 and four-seed whole-board electrical evidence below.

`Rb30Plan` declares one 33-package graph, with exact signed-long seed replay
and separate named streams for the two driver choices and sensor arrangement.
There are BJT and NMOS low-side options for each output, and separate direct
references or a shared hysteretic reference/feedback arrangement. These choices
change packages and connectivity, not just placement or values. The manifest
has 82 pads and 24 or 25 nets. Its honest package allocation is four entry and
protection, four regulation and filtering, eight sensor and reference, ten
driver and relay, two status, and five sensor/load/output connectors. The two
180-ohm external loads, finite bench sources, helper elements, and PCB copper
are not counted as physical packages.

`Rb30Generator` constructs a single CircuitJS graph with the accepted E02
12-to-5 V regulator, two E04 decisions, two E03 relays, two real low-side
drivers, a shared finite load source, and separate control and load returns.
The two 5 V sensor inputs are external stimuli at their connector nets; they
are not the control rail. `CIN` is a physical 22-uF electrolytic on the 12 V
regulator input. An earlier 1-uF candidate exposed a real startup overvoltage
and was rejected; see `generator-nmos-1uf-failure.txt`. The current graph has
202 solver elements in the pilot. `Rb30TopologyValidator` requires a backing
element and mapped endpoint for every physical package/pad and classifies the
remaining external, support and interconnect elements. Its injected resistor
and ghost-wire negatives fail. Analyzed node numbers are not used as durable
board identity.

Five candidate fault targets cover sensor input, 12 V protection, regulator
enable, driver input and relay coil. They are **candidate** hypotheses only:
D01 cold solvability, observation plans and complete service/retest are not
proved. The provider-local `Rb30DecisionService` defines compatible five-pad
catalog/replacement ownership for each controller. `Rb30RelayService` now
composes the accepted E03 five-terminal mutation owner twice, with distinct
KA/KB capability, inventory, workbench and catalog identities. The new
construction contract checks both declarations; there is still no installed
Q30 normal-player mutation flow or repair/retest proof.

## Evidence and limits

| Check | Result and boundary |
| --- | --- |
| Q30 plan contract | PASS: 1,397 assertions; 17 signed-long seeds, both sensor arrangements and both driver classes, exact plan replay, shared-reference placement and declared reference rejection. This is a manifest check, not compiled replay. |
| Two-channel relay service contract | PASS: 110 assertions over five exact signed-long seeds. Both relay slots have separate mutation inventories and catalog IDs; the accepted E03 singleton ID is absent from Q30. Focused E03 161 and pre-repair E04 303 assertions also passed after service integration; post-repair E04 passes 316. This checks registration, not player mutation. |
| Construction/solver pilot | PASS within scratch-native pilot: eight seed/topology constructions, 33 packages and 202 elements each; five selected BJT/BJT fault/restoration readings and NMOS healthy seeds 1–5. See `generator-pilot-pass.txt`, `generator-variants-pass.txt`, `Rb30GeneratorPilot.java`. These do not establish D01 diagnosis or service. |
| Exact 33-package whole-board electrical pilot | PASS: 10 assertions each on seeds 0, 1, 3 and held-out 11, spanning BJT/BJT, mixed BJT/NMOS, shared hysteretic and NMOS/NMOS. The live E02 rail fell from about 4.9998 V unloaded to 4.9959 V with A loaded and 4.9919 V with both channels loaded; its input current rose from about 3 mA to 42 mA to 82 mA. Main-source isolation with sensors and load source still on left the rail at 0 V and outputs off. Load-source and sensor-A isolation had distinct causal effects. Declared control/load references passed and a cross-domain reference was rejected. See `whole-board-variants-pass.txt` and `Q30WholeBoardElectricalPilot.java`. These are solver pilots, not player probe or diagnostic proof. |
| Cold 4 V main-input case | **PASS 4/4 at the production 5 µs maximum timestep:** settled rail 2.56690–2.57110 V and outputs off. The pre-repair 100 µs pilot **FAILED 4/4** at time zero and was not retested after E04 repair: seed 0 overshot to −0.291 V, then reached a −15.66 µV E02 output trial. This is an unaccepted Newton transient through the passive off branch, neither a settled backfeed nor ordinary roundoff; no E02 tolerance changed. See `brownout-investigation/README.md` and the historical `whole-board-4v-failure.txt`. |
| Warm 12 V to 4 V and recovery | **PASS 4/4 after a focused E04 brownout recovery-hysteresis repair**, at the production 5 µs/50 ps timestep on seeds 0, 1, 3 and held-out 11. The rail fell causally to 2.56690–2.57110 V, both outputs turned off, and 4→12 V recovery restored rail 4.99186–4.99196 V and both outputs. Before repair, all four fine-timestep cases failed as the E04 outputs chattered HIGH↔BROWNOUT across 3.8 V; the earlier coarse seed-1 68/70 V reading was an unaccepted Newton trial. The E04 falling trip remains 3.8 V and rising recovery is 3.85 V on the 5 V declaration. See `brownout-investigation/README.md` and its before/after logs. |
| Independent integration pilot | PASS: 38 assertions on a separate 107-element graph using 470-ohm external loads. E02 rail was 4.9999 V unloaded, 4.9920 V with both channels on; regulator source current rose from 2.012 mA to 81.336 mA. Losing sensor A source turned its coil off while B remained active; 4 V main input browned out both. An isolated cross-domain reference was rejected. See `electrical-pilot/`. Its topology and loading are not the 33-package candidate. |
| Physical 33-package root-plan-equivalent probe | **FAIL: 0/12 routes** under unchanged `THT_SINGLE_FACE@1`, six placement candidates per seed. Eleven final outcomes were `ROUTING_SEARCH_LIMIT`, one `ROUTING_NO_PATH`; six seeds had at least one hard `NO_PATH` candidate. Six representative seeds were 0, 1, 3, 17, 42, -1; held-out seeds were 11, 23, 37, 59, 83, -23. The pilot has its own manifest matching the root plan's package/net inventory; it is not a complete generated-instance test. See `routing-final-pilot-receipt.txt`. |
| Right-side load connector experiment | **FAIL: 0/12 routes** after moving only `JLOAD` to the right edge in the root plan and equivalent physical pilot. Under the same single-face clearance and search budget, eleven final outcomes were `ROUTING_SEARCH_LIMIT`, one was `ROUTING_NO_PATH`, and two seeds had at least one hard no-path candidate. This reduced hard obstacles but did not produce a board. See `routing-jload-right-pilot-receipt.txt`; it is the current placement result. |
| Matched P07 two-layer experiment | **PARTIAL structural route:** the current 33-package right-side-JLOAD manifest was tested on 12 seeds × six identical placement candidates × four policies (288 rows). P05 single-face, P07 one-layer and P07 restricted two-layer each routed 0/72; P07 fuller two-layer routed 6/72 placements across five seeds, including both sensor arrangements. All six used real top and bottom copper, validated connectivity/clearance, and required 36–46 plated vias and 31,850–39,950 unique copper units. None meets P09 or proves normal inspectability. See `two-layer-investigation/README.md`; its developer pilot patch is **unapplied**. |
| Two-layer component-count scaling | **Structural only:** a separate connected multi-channel through-hole fixture passed fuller two-layer routing 10/10 at 33 parts and 10/10 at 80; 90 passed 9/10. The largest single success in the bounded sweep was 180 parts (2/3) at the upper test bound, not a global router maximum. Restricted two-layer passed 0/61 tested requests. At 80 every success used the 48-via cap; the 180-part outline had 15,134,000 area, far outside gameplay scale. Exact Q30 at 33 remained 6/72 under fuller. See `two-layer-investigation/scaling/README.md` and the [architectural decision](two-layer-investigation/DECISION.md). |
| Provider-local floorplan experiment | **FAIL:** seed 0 separate-direct manual channel placement produced six valid candidates and 0/6 routes, all at the unchanged 1,000,000-expansion search limit. The selected outline was 3,700 × 2,100 (area 7,770,000), well beyond the proposed medium envelope. Sixteen routing salts and eight more manual attempts also produced no route. See `q30-floorplan-v3-receipt.txt` and its sanitized exploratory patch. This floorplan was not integrated into the generator. |
| Unapplied local hysteretic sensor proposal | **PARTIAL:** an explicit schema-2 patch replaces the nonplanar shared reference with independent A/B dividers and two real feedback resistors. Direct candidates remain 33 packages/82 pads; local hysteretic candidates have 35/86, all with 25 nets. A 12-seed incidence graph oracle found all variants planar, the proposal's JDK 8 plan test passed 1,556 assertions, but the unchanged physical pilot still routed 0/12. See `local-hysteretic-proposal.patch`, `local-hysteretic-graph.txt`, `local-hysteretic-planarity-results.txt` and `local-hysteretic-routing-receipt.txt`. This patch is **not applied** to the Q30 source or electrical receipts. |
| Placement sensitivity | FAIL: no success for exact placement, removed connector anchors, collapsed regions, or wider access margins on the initial ceramic-capacitor manifest (two seeds per variant). That obsolete 1-uF manifest is not an electrically accepted design. See `routing-pilot-receipt.txt`. |
| Existing content | Earlier PASS: 62-suite maintained JDK 8 current-contract matrix, including E02, E03, E04, D01, A10, P09, Quick Play, U02, physical serviceability and U04; independent seed/value/role/report readers and cleanup. It preceded the latest Q30 and E04 edits. Two fresh full attempts before the E04 repair exceeded the procedural corpus child's 60-second verifier bound; both are BLOCKED, not PASS. Twenty selected suites passed before the E04 repair; focused E02/E04 passed afterward. See `full-suite-timeout.txt` and `brownout-investigation/native-focused.log`. EASY/MEDIUM content retains its previous qualification, with a fresh full regression pending. |
| Production compile | PASS after the E04 repair: `scripts/build.ps1`, JDK 8/GWT, five permutations, 113.096 s compile and 1.726 s link. This compiles the unregistered candidate; it does not publish Q30. See `brownout-investigation/gwt-build.log`. |
| Q30 diagnosis/service/compiled player | NOT RUN: no Q30 normal admission. The four-seed main-isolation backfeed negative is bounded; a broader backfeed corpus, flat-netlist impostor and unproved-hypothesis rejection, complete probe correspondence, removal/catalog/replacement/lead operations, and player diagnosis/repair/retest remain unproved. |
| Qualified corpus timing | NOT APPLICABLE: zero qualified boards, so no honest cold/warm generation p50 or p95, proof-count distribution, matrix distribution, navigation/probing screenshots, or compiled replay identity can be reported. Pilot routing attempts are in the receipt and are not substituted for generation timings. |

The pre-repair focused affected-regression command passed 17 Java suites
and cleanup: E02/E03/E04, D01, A10 generation/dependency, U02,
serviceability, U04, Q30 plan/relay service, P09 policy and 32-row corpus,
Quick Play physical matrix and development/held-out corpus (24/48 rows),
and Quick Play gate contracts. This is a focused PASS; the full matrix and
independent readers remain BLOCKED by the two recorded timeouts.
An additional pre-repair focused run passed Quick Play current seed
construction, A10 routing rejection (28 assertions), and Task43 physical
endpoint ownership (320 assertions), with cleanup. Across these two focused
runs, 20 selected suites passed. After the E04 repair, focused E02 and E04
suites passed 1,429 and 316 assertions respectively, with cleanup. The full
post-repair matrix remains NOT RUN; the prior two full attempts timed out.

The right-side-connector 33-package attempts occupied 4,427,500–5,628,000
outline area units, with 15–16 maximum net degree. Each failed route spent 919,886–1,000,000
search expansions; no complete copper length or segment count exists. P09 v1
allows at most 16 packages, 40 pads, 16 nets, degree 10, area 2,250,000,
16,000 unique copper units and one declared face with no vias or links. It
rejects every Q30 pilot board even before electrical/player qualification.
The P07 structural-only 30-part reference was a different netlist and exceeded
P09 area/copper limits. The new matched Q30 experiment provides direct route
evidence for the current manifest: 6/72 fuller two-layer successes, with high
via/copper cost; restricted two-layer and both one-face algorithms routed none.
The shared-hysteretic component/net incidence graph has a nonplanarity witness in
`routing-pilot-receipt.txt`; the separate-direct graph is planar by that test,
but it also failed the unchanged router. This is a topology warning, not a
claim that every possible physical implementation is impossible.
`routing-cin22-pilot-receipt.txt` preserves the earlier 22-uF result before
the shared reference placement was corrected; its 0/12 route result is
historical. `routing-final-pilot-receipt.txt` captures the corrected shared
reference placement with the load connector left; the later right-side load
connector result is `routing-jload-right-pilot-receipt.txt`.

The `envelope-candidate.patch` and `Q30EnvelopeContractTest.java` are an
**unapplied design proposal** for versioned family policy selection. In the
separate exploratory worktree they passed 23 Q30-envelope assertions, P09 58,
Quick Play 26,978, D01 168 and A10 dependency 15. The proposed v2 remains
unqualified and unregistered. Raising caps without a routable, inspectable
and serviceable corpus would not satisfy Q30.
The local hysteretic proposal is likewise unapplied because its planar graph
did not yield a routed board, and its own electrical brownout behavior has not
been separately proved. Both exploratory patches pass `git apply --check` against this
isolated worktree but are not production changes.

## Extension cost and next dependency

Reused generic owners: `TroubleshootBoard`, named seed streams, the E02
regulator/source contracts, E03 relay/driver providers, E04 decision element,
physical package/pad/binding abstractions, P03 placement demand and P05 router.
New provider-local work: `Rb30Plan`, `Rb30PowerDomains`, `Rb30Generator`,
`Rb30TopologyValidator`, `Rb30DecisionService` and `Rb30RelayService`. The
active generic routing, probing, physical-runtime, workbench, and normal-
admission layers have no Q30-specific branch. The accepted E04 source now has
the narrow declaration-equivalence accessor and a bounded undervoltage
recovery hysteresis with reset, each covered by focused tests;
the candidate envelope patch would require generic admission call-site edits
and remains unapplied.

The exact-board production-timestep E02/E04 brownout transition now converges
on four tested seeds. Q30 still needs a versioned medium-board physical policy
backed by routability and inspectability. The current P09 limits do not admit
the candidate; P07 fuller two-layer reaches only 6/72 current placements and
requires 36–46 vias. A better floorplan, routing policy, or both need
qualification before normal admission. Only after that should the family be
registered and D01 hypotheses, physical service,
compiled replay, normal-player repair and cold/warm performance be qualified.
No family-specific router exception or silent layer-policy expansion is part of
this worktree.

## Reproduction

Use a clean worktree at the base SHA with the Q30 source and test files from
this worktree, plus JDK 8 and the repository's GWT dependency bundle.

```powershell
.\scripts\verify-current-contracts.ps1 -JavaHome '<JDK8_HOME>' -Suite @('E03RelayContractTest','E04SensorControlContractTest','Q30PlanContractTest','Q30RelayServiceContractTest')
.\scripts\verify-current-contracts.ps1 -JavaHome '<JDK8_HOME>'
.\scripts\build.ps1 -JavaHome '<JDK8_HOME>' -Style OBF -Target Compile
.\docs\task-evidence\Q30\run-generator-pilot.ps1 -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>'
.\docs\task-evidence\Q30\run-generator-pilot.ps1 -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>' -Pilot WholeBoard -WholeBoardMode 'skip-brownout seed=11'
.\docs\task-evidence\Q30\run-generator-pilot.ps1 -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>' -Pilot WholeBoard -WholeBoardMode 'warm-brownout seed=1'
.\docs\task-evidence\Q30\run-generator-pilot.ps1 -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>' -Pilot WholeBoard -WholeBoardMode 'trace-warm-brownout seed=1'
.\docs\task-evidence\Q30\run-generator-pilot.ps1 -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>' -Pilot WholeBoard -WholeBoardMode 'fine-warm-brownout seed=1'
.\docs\task-evidence\Q30\run-generator-pilot.ps1 -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>' -Pilot WholeBoard
.\docs\task-evidence\Q30\run-physical-pilot.ps1 -JavaHome '<JDK8_HOME>' -GwtHome '<GWT_HOME>'
git apply --check .\docs\task-evidence\Q30\local-hysteretic-proposal.patch
```

For the optional planarity oracle on the unapplied proposal, install
`networkx==3.7` into a unique task-owned temporary Python target, set
`Q30_NETWORKX_PATH` to that target, then run
`python docs/task-evidence/Q30/local-hysteretic-planarity.py`. The retained
graph rows and 12 result rows are in the adjacent evidence files. This
checks graph planarity only, not physical route feasibility.

`Q30PhysicalPilot.java` is the developer-only source for the root-equivalent
33-package routing probe. The reproduction script selects its `--root-plan`
mode, which uses a root-plan-equivalent package/net manifest. Its other 32-part
mode is an independent historical feasibility sketch and does not represent
Q30. The exact per-seed routing inputs, outcomes, package counts and work are
recorded in `routing-jload-right-pilot-receipt.txt`. The generator and electrical
pilot sources are also retained here. Native pilot execution used a
scratch-only JVM logging replacement for `CirSim.console`, whose JSNI logging
call otherwise throws outside GWT; no production source was modified for that
bridge. The stock-native failure is retained in
`electrical-pilot/stock-native-jsni-blocker.txt`. The current-contract suite
and production build above do not need that pilot bridge.
Both reproduction scripts allocate distinct OS-temp directories and print their
locations; they do not edit production sources. They were rerun successfully
after the final 22-uF package and placement correction. The independent
electrical pilot source and readings are retained separately because it has a
different graph and 470-ohm loads.
The original whole-board default command uses a coarse 100 µs cold-start step
and retains its failure as a stress observation. The original warm pilot logs
predate the E04 repair. Reproduce the final-source production-timestep cold,
warm, recovery and isolation checks with the commands in
`brownout-investigation/README.md`. Reproduce the matched 12-seed × six-candidate
P05/P07 comparison with `two-layer-investigation/run-comparison.ps1` as
specified in its README; that developer-only patch remains unapplied.
