# P06: true raised factory-crossover prototype

Status: **PASS - P06 prototype complete**, September 15, 2026. Base
`9082930f197c173b1e22ca7ada8033e4143fbdd8`, branch
`codex/task43p-final-recovery`. Scope is P06 only; normal factory-link adoption
and new-topology diagnostic qualification remain deferred to P09. P07 is next.

## What is implemented

The dedicated `RAISED_FACTORY_LINK` package declares two exposed terminals,
a conservative insulated underpass and a finite electrical backing. Its fixed
prototype resistance is **50 milliohms**, modeled by an actual CircuitJS resistor,
not a zero-resistance wire. It has no package-internal copper union. Existing
physical service slots, provenance, live mutation transactions, catalog inventory,
lead lifting, removal and reinstallation are reused rather than duplicated.

The local underpass is `[92,5,36,60]` inside the complete `[12,5,196,60]` courtyard.
The elevated conductor bottom is 16 units; insulation is 2 units and the supported
copper-height envelope is 2 units. These are abstract drawing units, **not mm or
manufacturer clearance/rating data**. This is a low-voltage model prototype,
not a thermal, ampacity, dielectric-withstand or three-dimensional field solver.
The passage avoids exposed pads, connected/lifted leads and their probe regions.

Routing and independent layout validation require the **entire swept copper
stroke** within the declared passage where it intersects the same-face courtyard.
A centerline alone is insufficient. Package placement still uses the complete
courtyard. All cardinal rotations, both mount faces, mirroring and copying retain
the declaration, and its dimensions/heights participate in sealed geometry identity.
Unraised package fingerprints and existing routing bounds are unchanged.

Copper below the insulated body does not become a link terminal. Permanent copper
projection excludes the finite component and its detachable solder connections.
Removing the link opens that actual component path without cutting or shorting the
underpass. Both through-hole pad faces and loose endpoint probes retain their real
measurement endpoints. The insulated sleeve is selectable, but not a fake probe.

## Sparse policy and admission boundary

The prototype limits are at most **two links**, no more than `1 + nonLinkParts/12`,
and at least **60,000 board-area units per link**. A **32-candidate** ceiling and
**400 route-unit link penalty** are proposed for subsequent strategy comparisons.
Count/density checks are active; P06 does not implement automatic link insertion
or claim that a constant is a completed insertion/search policy.

Raised packages require a newly constructed developer-only board and the exact
supported finite backing. Construction and inventory reject disguised fuses,
ordinary resistor packages, mismatched specifications, ideal wire stamps and
unsupported resistance values. Sealed boards reject late insertions. The debug
fixture's structural contract is explicitly rejected as normal diagnostic proof.
Normal constructors reject these links until P09 owns fresh affected electrical
and diagnostic admission. Existing Quick Play families and curated seeds remain.

## Frozen linked versus all-copper comparisons

The three fixtures retain the same external endpoint positions, board outline,
clearance/quality rules and P05 search budgets. A linked design has six physical
parts; its genuine all-copper control has five and no link body or backing. A
separate ordinary-axial negative checks that relabeling a flat part cannot earn an
underpass exception; that negative is **not** the all-copper performance control.

| Fixture vertical shift | Link / no-link area | Link / no-link copper length | Link / no-link search expansions |
|---|---:|---:|---:|
| -20 | 168,000 / 168,000 | 540 / 1,000 | 2,067 / 14,992 |
| 0 | 168,000 / 168,000 | 540 / 1,040 | 1,977 / 15,000 |
| +20 | 168,000 / 168,000 | 540 / 1,000 | 1,879 / 14,239 |

**No area is saved in these matched controls.** Copper is shorter by 46.0-48.1%
and search expansions fall 86.2-86.8%, at the cost of one physical part. The linked
routes are straight with a visibly distinct insulated crossing; that is a local
clarity observation, not a user study or universal readability result. All controls
pass on the first tested outline, and repeated linked geometry is deterministic.
These are fixed difficult crossing fixtures, not a representative generator census
or a proof of globally optimal area. The proposed 400-unit penalty deliberately
charges for the extra part rather than treating crossovers as free routing magic.

## Electrical and actual input evidence

The real compiled bench uses independent 5 V link/load and 7 V underpass loops.
The 100-ohm load draws 49.9750125 mA with the 50-milliohm link installed, **zero**
with it removed, and 49.9750125 mA after reinstallation. The 1,000-ohm underpass
load remains at **7 mA** throughout. These are actual CircuitJS results checked
against separately calculated expectations. A projected short would invalidate
the copper audit or these distinct operating points.

The compiled verifier also checks powered-mutation rejection, both probe faces,
loose backing/provenance identity, lifted/reconnected leads, independently backed
catalog replacement, final copper correspondence and sealed geometry identity.
Its 47 assertions restore the previous live owner exactly. An intentionally forced
failure runs the same restoration and must emit FAIL, not a green success marker.
The optional retained developer bench is labeled separately from restoration runs.

Real browser mouse/accessible-control input opens the component menu on the actual
body, removes the component, selects a loose link, reinstalls it, and places both
meter probes. The actual meter reads **50 mOhm**. Screenshots show the installed
crossing, real actions, cleared body with intact underpass, and the repaired part.
This legacy debug bench is not a normal customer challenge or a new polished UI;
its existing narrow sidebar can clip long controls. Normal-player regression is
qualified separately, without turning that debug presentation into a release claim.

## Validation and limits

`native-results.json`, `browser-results.json` and `source-build-identity.json`
record the final qualified source variants, commands and outcomes. The maintained
43-suite native matrix plus independent seed/value/role/provider/diagnostic/report
oracles passed. The final inventory fail-closed guard then changed only
`PhysicalServicePart.java` and its P06 negative test; the three affected P06,
serviceability and provider-conformance suites were rerun. The final P06 suite has
**1,705 assertions**. The source audit identifies exactly those two deltas; affected suites were requalified and all other matrix input files are byte-identical.

Final-source qualification is complete:

- **PASS:** clean-publication and preserved-visual integration JDK8/GWT builds,
  all five permutations each, with exact Java-source and generated-byte hashes.
- **PASS:** clean compiled P06, 47 real-runtime assertions; forced-negative cleanup
  emits the expected FAIL marker and proves exact prior-owner restoration.
- **PASS:** actual mouse/action/probe input on the clean prototype bench; the
  real OHM display reads 50 mOhm after removal and reinstallation.
- **PASS:** final clean compiled full layout/geometry verifier, 26.30 seconds.
- **PASS:** final clean Q15 regression, all 11 cases, 613 assertions plus 80 support
  assertions, cancellation and exact owner restoration; 379.73 seconds overall.
- **PASS:** integrated P06 runtime and five native suites, including the unchanged
  11-seed dense packing test, P01, P02 and physical serviceability.
- **PASS:** integrated normal-player menu, exact seed 3, ticket acceptance,
  top/bottom inspection, middle-drag pan and wheel zoom. Preparation took 25.86
  seconds. No application exceptions; owned browsers and listeners closed.

The linked/all-copper comparison changes electrical topology by introducing a
finite physical component; it is not a claim that an old diagnostic proof can be
reused for the linked board. That normal admission boundary remains closed.
These elapsed values are single-host observations, not controlled speed benchmarks.

Final-source publication and preserved-visual integration builds/checks are recorded
separately. No independent agent review was used; root review covers declaration
immutability, whole-stroke/face boundaries, finite backing, service ownership,
normal-admission exclusion and the mixed worktree. New operating systems, broad
browser populations, modest-machine performance and unrelated release matrices
are not certified by this prototype.

Early native fixture checks caught an incorrect bottom-face orientation and a
shifted reference label overlapping copper. The fixtures were corrected without
weakening physical assertions. The initial mixed-checkout merge encountered only
checkout line-ending differences in an otherwise baseline-identical renderer;
an incomplete first integration compile failed before the completed merge was
qualified. Failed attempts remain in task scratch and are not counted as PASS.

The original visual/FPS/packing/tray/shop edits and untracked evidence are preserved.
Only the P06 content of shared files is staged from the separately qualified clean
candidate. Reproduction commands are in `REPRODUCE.md`; raw attempts remain in the
owned Temp directory `TroubleshootJS-P06-20260915-g3kl9e46`. No unrelated processes,
profiles, files, test limits or working changes are removed or weakened.
