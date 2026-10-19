# Routed trace guard trial worker notes

Status: source implementation prepared in the accepted1467 source-only COPY for root review. Base source: ca050e0445168249481b68dbb357f9e7b5553f9f. Runtime acceptance remains pending; these notes do not claim the contract or build passed.

## Owned source paths

The COPY changes are confined to these five production files and one new contract:

- src/com/lushprojects/circuitjs1/client/Rb56Placement.java: adds TRACE_GUARD_VERSION20, mapping only that private identity to density15, ROLE_WIDTHS, and two-layer routing. Versions16–19 remain unsupported; explicit placement15 and generator general16 are unchanged.
- src/com/lushprojects/circuitjs1/client/PcbPlacementConstraints.java: admits density15.
- src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java: changes only routed trace-vertex occupancy when the selected medium physical policy and density15 are both present. The guard is (PcbTraceRules.clearance(width,width)+1)/2; all other cases retain the old full self-clearance.
- src/com/lushprojects/circuitjs1/client/SeededPcbLayoutGenerator.java: adds density15 to only the density14 postselection border-trim owner and the density13/14 medium-canvas owner.
- src/com/lushprojects/circuitjs1/client/PlayerSessionReplay.java: adds the private20 canonical replay prefix/token tsj-private-session/20/.../rb56-placement-20-trace-guard; v15 identity remains byte-exact.
- tests/contracts/PcbRoutedTraceGuardContractTest.java: adds mapping/replay, independent width arithmetic, and three actual-compactor fixture configurations (with one repeat invocation for determinism).

No planner/routing search or order, CHANNEL20, GRID10, package/access, hole/via guard, validator, catch, route-quality, capacity, or fallback behavior was changed. The existing physical-failure exceptions remain unhandled.

## Literal fixture expectations

Each fixture uses the P06 TEST_POINT geometry, cloned with explicit local escape (-1,0,30), TOP-only routing, and declared rotations 0/180. All four test footprints now use rotation180, so each world escape points +x and both endpoints of each U route use the declared escape corridor. Each pad center is x100. The P06 package is 40×20 with local pad (10,10); under 180-degree placement its local pad is (30,10), giving footprint x70..110. The explicit escape reaches x130 with a 20-unit access box x120..140. With access margin20, the planner envelope is x50..160 and y=[padY−50,padY+30]. The four y centers 200, 700, 850, and 1350 have nonoverlapping envelopes inside outline (20,20,1200,1400).

The input traces are distinct nets and source identities: width9 GUARD_WIDTH9 at y200..700 with x points [100,500,500,100], and width11 GUARD_WIDTH11 at y850..1350 with x points [100,550,550,100]. Their y ranges are disjoint. This fixture measures the effect of mixed-width trace vertices in the x-axis projection; it is not evidence of physical parallel-rail spacing. The unchanged width-geometry suite remains the physical pair-boundary oracle.

The x-cut expectations are derived directly from the current compactor's interval, CHANNEL20, and GRID10 rules:

| Case | Trace guards (width9,width11) | Literal cuts | Final rail x | Outline width |
| --- | ---: | --- | ---: | ---: |
| explicit placement15 / density14 medium | 15, 17 | x485 removes300; next gap is 18, so no cut | 200, 250 | 900 |
| private placement20 / density15 medium | 8, 9 | x492 removes310; x541 removes10 | 190, 230 | 880 |
| density15 nonmedium | 15, 17 | x485 removes300; next gap is 18, so no cut | 200, 250 | 900 |

For the split case, the envelope ends at x160; the first vertex interval begins at 492, giving excess 492−160−20=312 and a 310-unit grid cut. The next interval begins at 541 after the first interval ends at 508, giving excess 541−508−20=13 and a 10-unit cut. Under the old guards, the first interval begins at 485 and gives excess305, while the next interval begins at533 after end515, leaving excess−2. These yield the literal x points asserted by the test.

The independent arithmetic table is clearance/guard: width9 → 15/8, width11 → 17/9, width13 → 19/10, width63 → 69/35. For every pair in {9,11,13,63}, split guards plus CHANNEL20 leave 21 units above the current physical pair clearance. Widths8,10,65 are required to reject through the existing width owner.

## Static checks and hashes

- Compared each modified production file with the base worktree. The diff is limited to the version mapping/allowlists, density15 admission, one compactor guard expression, two Seeded density-owner branches, and private20 replay handling described above.
- Static density-owner search found no changed planner/routing branch; only the two intended Seeded branches reference density15.
- Manually matched the test's constructor and geometry APIs against the source and the existing P06 compactor fixture. The test calls the package/access, routed-geometry, and route-quality validators before and after compaction and fingerprints source inputs for immutability.
- Source diff whitespace scan completed without whitespace diagnostics; a separate trailing-whitespace scan found none in the six owned files.
- The output marker is exactly PASS: PCB routed trace guard contracts for the root-registered suite.

SHA-256 of the six owned COPY files at handoff:

- Rb56Placement.java: B0CC281469BA54F111D7B4233C86046FF890243D12340019D9B4CB04E3CA01F5
- PcbPlacementConstraints.java: 5534541B3D218BDE55A76CC092D478850A6A8C01DB07C9BB5645F993D77FCC62
- PcbPlacementCompactor.java: D87375493505FE9AF424E8A9C13BE8B1E021DDECDF5238504C34550E74C6D450
- SeededPcbLayoutGenerator.java: 0DFD1890ECEDF90350237152CAEE42CABFA7ABAE412386250FA74B9E09098CF6
- PlayerSessionReplay.java: F9E1EFBEAB16721C7C26B7D9D31DF4E5A263883566281AEEA1FCC2D8E956F9F3
- PcbRoutedTraceGuardContractTest.java (pre-fix fixture hash): F7280274E28622AFF4B8F47CE67EE1C8210D612304687E775A9FC5F9E5804338

## Not run at original worker handoff

- Contract execution, including the three fixture configurations and deterministic repeat: NOT RUN (root owns execution).
- Unchanged PCB trace-width geometry suite: NOT RUN (root owns execution).
- JDK8/GWT build, native suite, JS/runtime, seeded corpus, reader, and visible player flow: NOT RUN.
- No stage, commit, push, or notification performed by this worker.

## Root native attempt1 and fixture correction

Root retained attempt1 with 49 exact artifacts, 25 gates, and 15 deltas in its attempt1 packet. The maintained compile completed PASS, but the actual native attempt was FAIL after 45.9804363 seconds (metadata exit2, JVM1). The new contract reached mapping and arithmetic; validateInput failed before any compactRouted invocation with: PCB trace width disagrees with net profile: NET11 expected=9 actual=11. The other two suites were NOT RUN. Root verified both driver processes had exited and did not terminate them.

The fixture assigned NET11 its default SIGNAL role while declaring an 11-unit trace. The test-only correction now declares NET9 as BoardNet.RoutingRole.SIGNAL and NET11 as BoardNet.RoutingRole.SUPPLY, then constructs its constraints with requiresTwoLayerRouting=false and PcbTraceWidthProfile.ROLE_WIDTHS. The existing profile maps SIGNAL to width9 and SUPPLY to width11. No production rule, fixture geometry, cut expectation, or assertion changed. This corrects the fixture's declared copper profile to match the role-width contract.

Updated SHA-256 for tests/contracts/PcbRoutedTraceGuardContractTest.java: 4AFBB2B7B7E1B2CCFB306C26142A878CD0FF4337424E3C327D8D1B25D5F60C2B. The earlier F7280274E28622AFF4B8F47CE67EE1C8210D612304687E775A9FC5F9E5804338 hash above is pre-fix. All five production COPY file hashes remain the same as listed above.

Post-fix contract, width-geometry suite, and build: NOT RUN by this worker; root owns the fresh validation.