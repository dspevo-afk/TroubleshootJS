# Task 49 — intent-driven value synthesis

Status: **COMPLETE — implementation and closed acceptance qualified** (2026-09-07).
Accepted base: `62c878b8f381e3418214a93a46d3e8d2d9693b3e`, branch
`codex/task43p-final-recovery`, upstream `origin/codex/task43p-final-recovery`.
Entry was clean and the remote branch contained that exact Task48 SHA.
Task50 and the separate pre-Task50 gate remain unstarted.

## Phase A reconciliation and ownership

All five required read-only Luna MAX investigations returned before a coder
existed: value architecture, duplicated authority, replay/versioning,
ratings/package truth, and closed acceptance design. Astra reconciled the
findings and owns this design, integration, evidence, validation and publication.
One Luna MAX leaf coder completed the production/test changes listed below; a fresh
Luna MAX leaf reviewer passed the integrated candidate and each corrected delta. No worker speed
selector or agent-close operation is exposed. No global configuration changes
are needed. Workers own no browser, preview, publication, or shared build process.

Reconciliation corrected two proposed assumptions before implementation. The
`default-led` model is not bounded by 1.2–1.5 V at the intended current, and the
typed sink permits 0.8 V, not an assumed 0.25 V. The model envelope below is tied
to the existing CircuitJS model. Exact length-framed named-stream derivation
also corrects an investigator's provisional seed0 choice to 330 ohms. The frozen
seed table was independently computed with the accepted Task46 Python reference.

The unrestricted repair catalog remains available. Candidate eligibility limits
generated healthy designs; it must not prevent wrong repairs or their electrical
consequences. The original Task48 330-ohm part remains 0.25 W, while Task49's
catalog-backed 330-ohm part is 0.22 W. Axial lead spans are geometry only.

## Frozen design

1. **Block:** only `load` / `resistor-led-load` in the existing composed NMOS
   controlled indicator. RG, RPD, the NMOS, LED and adapters retain their current
   function and physical topology. Seven components, fifteen pads, six connected
   nets, two external sources, and the same two resistor-open fault owners.
2. **Intent inputs:** an indicator conducting at least 5 mA on command; the
   maximum current is the typed load demand (16 mA), within the typed 20 mA sink
   capacity with at least 1.25x current headroom. Resolve supply guarantees
   4.75–5.25 V, load acceptance 4.5–5.5 V, and sink clamp 0–0.8 V from the actual
   request's known typed interfaces/relationship. Unknown or contradictory
   interface facts reject. The versioned `default-led` design envelope is
   1.6–2.0 V; it is a model assumption checked by CircuitJS, not a manufacturer
   voltage/current rating. Required resistor tolerance is 5%, power headroom 2x,
   and supported package identity is `AXIAL_RESISTOR`.
3. **Catalog authority:** reuse the existing `ResistorReplacementCatalog` and
   its immutable entries/nameplates directly. Its finite E12 nominal series
   spans 10 ohms through 10 megohms; 5% tolerance and current ratings are preserved,
   including the 330-ohm 0.22 W entry. No second E12 table or selected-value array
   belongs in production. Only valid candidates enter deterministic selection.
4. **Candidate fields:** one canonical catalog entry (ID, nominal resistance,
   tolerance, rated watts) plus supported package identity. Copy/sort input
   collections, reject duplicate IDs, forged catalog metadata, nonstandard
   values, malformed/nonfinite data, and incompatible package identities.
   `SPAN_220`, `SPAN_240`, and `SPAN_260` cannot be supplied as power/package types.
5. **Pure equations:** for nominal resistance `R` and fractional tolerance `t`,
   `Rmin = R*(1-t)`, `Rmax = R*(1+t)`,
   `Imin = (VsMin-VfMax-VdsMax)/Rmax`,
   `Imax = (VsMax-VfMin-VdsMin)/Rmin`,
   `Pguard = VsMax*VsMax/Rmin`. Require positive finite ordered inputs and
   headroom; `Imin >= targetMin`, `Imax <= typedLoadDemand`,
   `1.25*Imax <= sinkCapacity`, and `2*Pguard <= ratedWatts`.
   The rating guard conservatively assigns the entire source voltage to the
   resistor. No CircuitJS observations enter this phase. An empty valid set
   fails deterministically before a mutable assembler context exists.
6. **Admitted results:** exactly the following existing catalog entries qualify.
   Other entries are rejected by the equations, not a hardcoded two-value filter.

   | ID | Nominal / tolerance | Rating / package | Minimum / maximum current | Required power rating |
   | --- | --- | --- | --- | --- |
   | `R_CATALOG_270` | 270 ohms / 5% | 0.25 W / `AXIAL_RESISTOR` | 6.8783 / 14.2300 mA | 0.2149123 W |
   | `R_CATALOG_330` | 330 ohms / 5% | 0.22 W / `AXIAL_RESISTOR` | 5.6277 / 11.6427 mA | 0.1758373 W |

   220 ohms fails the current ceiling and power headroom; 390 ohms fails the
   current floor. Additional pure negatives separately exercise current,
   voltage/interface, package, tolerance, power, catalog and empty-set rejection.
7. **Resolved authority:** one immutable resolved load recipe retains the selected
   candidate, the intent and relevant derived margin receipt, and policy
   `controlled-led-load-e12@1`. The contribution's resistor recipe delegates
   numeric/package metadata to it; it must not store independently chosen copies.
   `BoundedAssemblyPlan` resolves it exactly once per plan, before CircuitJS
   allocation. The request carries unresolved intent declarations, not a first
   selection that the plan repeats. The load declaration has an explicit new
   provider schema (version 2); its local component/pad/net identities stay stable.
   Legacy contribution signatures remain byte compatible; new signatures bind
   the selected recipe and policy. The resolver stays outside the assembler.
8. **Replay:** Task49 is `bounded-assembler@3`, schema1,
   `controlled-indicator@1` intent/profile, geometry3, namespace1. Generator3
   maps explicitly to the policy above. Existing generator2 factories/descriptors
   retain fixed 330-ohm/0.25-W behavior and never invoke new candidate selection.
   Unknown versions and mismatched declaration/version pairs reject. Preserve the
   old ordinary URL and sidebar entry; add an explicit ordinary
   `tsjChallenge=controlled-indicator-values&seed=<canonical-long>` route for v3.
   Quick Play and leaf generation remain unchanged.
9. **Named stream:** derivation1, exact root signed-long decimal text,
   intent `controlled-indicator@1`, scope `block`, key `load`, concern `values`,
   revision1, semantic key `resistance`. Use `NamedRandomStreams.select` on
   canonical unique candidate IDs. No shared random cursor. Keep the device
   `fault`/revision1/`selected-fault` tuple and candidate IDs unchanged. A values
   revision affects only that tuple; optional block insertion cannot change the
   existing block key. Scenario, topology, placement, routing and presentation
   streams retain their existing seeds and meanings.
10. **Consumers:** the resolved contribution feeds CircuitJS resistor construction,
    `BoardPhysicalSpecifications`, `ResistorNameplate`, physical part registration,
    and intended repair metadata. Package mapping validates the recipe's exact
    supported identity against the registered package. The assembler only reads
    values and constructs objects. The selected catalog ID supplies diagnostic
    and verifier repairs; ordinary players may still choose other catalog parts.
    Physical color bands and existing workbench details consume the actual part
    specification. No candidate list, private recipe, or selected-fault answer
    is added to player UI.
11. **Behavior boundary:** v3 behavior reads its recipe's operating envelope and
    settled CircuitJS observations: source within 4.75–5.25 V, resistor/LED current
    within 5–16 mA, LED forward voltage within 1.6–2.0 V, sink voltage within
    0–0.8 V, branch-current consistency and the existing gate/OFF predicates.
    Healthy LOW/HIGH proof runs before fault application; fault diagnosis and
    repaired customer OFF/ON retest remain actual solver operations. Functional
    alternative repairs remain admissible; nominal-value identity is not the
    customer completion oracle. Legacy v2 behavior retains its 8–20 mA bounds.
12. **Diagnostic request preservation:** `Task41DeveloperVerifier.Route` currently
    reconstructs controlled proof candidates from family and seed, implicitly
    selecting generator2. Carry the original immutable versioned request through
    candidate generation and restoration so v3 diagnostics use the same recipe.
    Prove this with the 270-ohm seed and a fail-closed request/recipe assertion.
    This necessary Task49 dependency does not change candidate eligibility or
    implement the separately deferred eligibility audit finding.
13. **Allowed implementation:** new bounded pure synthesis/recipe and Task49
    verifier/test files; surgical changes to `ComposedBlockContribution`,
    `ControlledIndicatorBlockContributions`, `BoundedAssemblyRequest`,
    `BoundedAssemblyPlan`, `BoundedGeneratedBoardAssembler`,
    `ControlledIndicatorDeviceBehavior`, the diagnostic request/repair seam in
    `Task41DeveloperVerifier`, `CirSim` route/publication hooks, and the explicit
    pure assembly harness. Existing pure catalog classes may be included as
    compile inputs without changing the catalog. Astra owns project docs and
    curated evidence. Any newly necessary production file must be reconciled
    with Astra before editing.
14. **Non-scope:** Task50/support blocks; arbitrary synthesis/part search; other
    block or leaf conversions; router, bend counts or connected-placement target
    corrections; diagnostic eligibility alignment; new packages/ratings/SMD;
    broad assembler/solver/runtime refactoring; global configuration, historical
    Task43 certification, and unrelated UI or publication changes.

## Frozen seed and acceptance matrix

The seed table uses the accepted independent Task46 Python arithmetic and
canonical candidate order. All seeds retain their exact signed decimal strings.

| Seed | Selected entry | Unchanged fault decision |
| --- | --- | --- |
| `0` | `R_CATALOG_330` | `load-rload-open` |
| `1` | `R_CATALOG_330` | `driver-rg-open` |
| `2` | `R_CATALOG_270` | `load-rload-open` |
| `3` | `R_CATALOG_270` | `load-rload-open` |
| `-9223372036854775808` | `R_CATALOG_270` | `driver-rg-open` |
| `9223372036854775807` | `R_CATALOG_270` | `driver-rg-open` |
| `9007199254740993` | `R_CATALOG_330` | `load-rload-open` |
| `-9007199254740993` | `R_CATALOG_270` | `load-rload-open` |

| Gate / production path | Verifier, independent oracle, fixtures and dependencies | Result |
| --- | --- | --- |
| Pure synthesis and authority | Actual-source JDK8/source7 isolated compile; frozen candidate/seed literals plus independent Python reference; immutability, permutation, canonical IDs, all eighteen requested pure requirements and malformed/interface/current/rating/package/zero-candidate negatives | PASS — 137 assertions; 8 independent seed receipts |
| Affected Task44–48 pure contracts and exact old receipts | `verify-block-contracts.ps1`, `verify-challenge-contracts.ps1`, extended `verify-assembly-contracts.ps1`; original Task46 parity and Task47/48 receipt bytes compared to accepted evidence; existing independent reference programs | PASS — 247/202/410/269/224 assertions; 12 low-side checks; Task47/48 bytes exact |
| v3 replay, actual graph and recipe/spec/catalog agreement | New compiled Task49 verifier, eight frozen signed seeds, direct and parsed fresh runtimes, disjoint owners, exact recipe/fault/layout snapshots; no formula-only runtime PASS | PASS — 8 seeds, inside 1,053 runtime assertions |
| Healthy operating range and physical correspondence | Both admitted values; actual solver at nominal and supply/tolerance corners, restoring original values exactly; LOW/HIGH current/voltage/rating checks; existing fifteen-terminal controlled physical oracle with wrong-mapping negatives | PASS — both values, 4 corners each, 15 terminals per seed and 9 physical negative fixtures |
| Diagnostic and repair integration | Seeds1/2, both explicit existing fault-owner overrides per value; actual normal admission retains v3 request/recipe; legal distinguishing probes, unrepaired/wrong/correct/alternative repair and OFF/ON retest; wrong options remain installable | PASS — both proof owners for every seed; 4 complete value/owner repair pairs on seeds0/1/2/MIN |
| Mutation and owner succession | New-value replacement/isolation/restoration and stale callbacks across 270/330 owners; Task48 compiled/mutation suites and selected composition entry lifecycle/mutation gate | PASS — 6 measurement cases; Task48 mutation; 5 construction/5 installation failures; 330→270 stale-owner proof |
| Shared replay/runtime regressions | Existing Task47 compiled suite, Task48 compiled suite, Task41 fourteen legacy routes, and LED3/RC2/NPN0/NMOS0 legacy replay; selected legacy challenge/replacement; no new historical CDP qualification claim | PASS — Task48 755 assertions; Task41 14 routes/128 samples; four-family replay strings exact |
| Fail-closed verification | Final-build explicit Task49 AssertionError must terminate FAIL without PASS report; one pure synthesis source mutation disabling the power-margin rejection must make the actual pure gate fail; unique-anchor/apply/exact-byte-restore preflight before mutation, no weakened oracle | PASS — expected AssertionError FAIL/no report; source canary exit2 at the intended power assertion; exact restoration |
| Final production build | `scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF`; JDK8 1.8.0_502, repository GWT, all five permutations, before/after source manifest and compiled hashes | PASS — root build4; GWT2.7.0, five OBF permutations, unchanged before/after source |
| Normal player truth/privacy | Real visible in-app Browser, debug off, v3 seeds1/2; selected markings, complaint, LOW/HIGH, left-red/right-black probes, mode exit, isolate/repair/retest; unrepaired failure; ignored debug flags and DOM/tooltips privacy; two to five inspected screenshots. Old v2 entry remains intact | PASS — 2 complete player loops, 11 privacy states, 4 inspected screenshots, actual legacy entry click |
| Independent review and final reconciliation | Fresh read-only Luna MAX integrated review, targeted independent delta review after repairs; source/compiled/evidence identity, intended diff, `git diff --check`, staged diff/check, resources | PASS — source/delta/evidence review; 977 source and 6 compiled hashes; staged/unstaged checks |
| Publication | Explicit intended files only, normal commit/push to configured upstream, exact remote SHA verification, then established Gmail completion notification | The accepted implementation is the commit containing this packet; exact SHA, remote and Gmail outcomes are recorded in the final handoff |

## Qualified candidate and reproducible evidence

Final source fingerprint (977 inputs):
`ee88af871814a7bda9d88e422367a7a90945621acc984a441bb9bc22a257e4f7`.
The complete [source manifest](source-manifest.json) binds the accepted base plus
the uncommitted implementation. [Compiled hashes](compiled-manifest.json) bind
all five final permutations. The actual Browser loaded
`BAEB8F97F1A9EEDAA85F5C4E3F2E55C9.cache.js`, which matches that manifest.
The accepted implementation is the commit containing this packet; the final
handoff records exact commit, remote verification and notification outcomes.

Run these commands from the repository with JDK8 selected. `<python>` is the
selected Python3 interpreter; `<temp>` is a unique task-owned directory outside
the repository. Final execution used Python3.12.14 and Temurin1.8.0_502-b07.

```text
powershell.exe -NoProfile -File scripts/verify-block-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07
powershell.exe -NoProfile -File scripts/verify-challenge-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -PythonExe <python> -ParityOutputPath <temp>/task46.tsv
powershell.exe -NoProfile -File scripts/verify-assembly-contracts.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -PythonExe <python> -ReceiptOutputPath <temp>/task47.tsv -ControlledReceiptOutputPath <temp>/task48.tsv -SynthesizedReceiptOutputPath <temp>/task49.tsv
powershell.exe -NoProfile -File scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Target Compile -Style OBF
```

[Pure/build receipts](pure-and-build.json) record exit codes, counts and elapsed
times. Task46's 22-vector independent oracle and the Task47/48/49 eight-seed
references pass. Task47/48 JVM receipts are byte-identical to accepted Task48
evidence. Task46 parity text is identical after CRLF/LF normalization; it is
not claimed byte-identical. The four exact receipt files are retained here.

Use an owned production preview and open `circuitjs.html` with the exact queries
in [runtime.json](runtime.json) and [browser-regressions.json](browser-regressions.json).
The latter also records hashes proving exact old descriptor/parity/snapshot/replay
strings for LED3, RC2, NPN0 and NMOS0. The [Task41 receipt](task41-evidence.txt)
preserves its declared-versus-executed metrics without upgrading those historic
declarations into new proof. Composition entry adds 113 assertions and its
mutation receipt adds 180 assertions/18 partial aborts.

The [source negative](power-guard-negative.json) disables the one power-headroom
expression after unique-anchor/apply/exact-restoration preflight. The actual
pure harness exits2 at `power-headroom equation rejects every catalog value`.
That rejected mutant is the expected negative result, not a passing process.
Its synthesis/test/harness inputs are byte-identical to final source; the exact
dependency audit records reuse. All final positive pure gates were refreshed.
The final-build forced query terminates
`FAIL:task49:task49-explicit-assertion-canary` with no Task49 report.

[Independent review](review.json) covers the integrated implementation and all
three corrected deltas. [Diagnostic history](diagnostic-history.json) separates
the three earlier runtime failures and the coder's unqualified diagnostic build
from the final build4 PASS. The corrections were two developer enablement guards
and settlement before verifier cleanup restored power. No product invariant or
negative assertion was weakened.

## Visible evidence and retained limits

[Player acceptance](player-acceptance.json) records real visible controls and
probe inputs, selected values, legal isolated readings, physical replacement,
LOW/HIGH operation, customer retest and privacy. These four original Browser
JPEGs were inspected; their hashes and dimensions are in that receipt.

| Screenshot | What it proves |
| --- | --- |
| [330-ohm-unrepaired.jpg](330-ohm-unrepaired.jpg) | Orange/orange/brown markings and 330-ohm/5% detail; symptom-only ticket and failed unrepaired customer retest |
| [330-ohm-repaired.jpg](330-ohm-repaired.jpg) | Retained removed RG, illuminated LED with HIGH after actual catalog repair, and customer acceptance |
| [270-ohm-unrepaired.jpg](270-ohm-unrepaired.jpg) | Red/violet/brown markings and 270-ohm/5% detail; failed unrepaired customer retest |
| [270-ohm-repaired.jpg](270-ohm-repaired.jpg) | Retained removed RLOAD, illuminated LED with HIGH after actual 270-ohm catalog repair, and customer acceptance |

This is a single load-block policy with two admitted E12 values and one truthful
axial package. The LED voltage envelope describes the existing simulation model,
not manufacturing certification. The UI shows nominal value/tolerance; physical
rating/package correspondence is proved by the compiled oracle. It does not add
support blocks, arbitrary part search, package proliferation or routing changes.

All four owned previews/tabs were closed; strict process/listener/lease cleanup
and wrapper exit0 are recorded in [cleanup-and-assets.json](cleanup-and-assets.json).
The root evidence directory remains outside Git for raw provenance. Three coder
compile directories also remain outside Git: automatic approval review rejected
their verified scoped cleanup with only `blocked by policy`. Deletion is not
claimed. All Luna assignments returned; the interface exposes no worker-close
operation. Some early DOM reads timed out during synchronous solver work; final
results were read from those same completed navigations. No timeout is PASS,
and these Browser receipts do not claim historical standalone CDP certification.

## Next unstarted boundary

Before Task50 implementation, a **separate bounded pre-Task50 gate** must align
diagnostic proof enumeration with the canonical admitted-candidate eligibility
predicate; run a small physical-capacity/layout smoke using the actual proposed
Task50 support-block shape; and separately adjudicate the known bend-count and
placement-target findings if relevant to that smoke. Task49 records this gate
only and does not implement it or begin Task50.
