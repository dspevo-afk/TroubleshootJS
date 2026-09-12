# A11 provider conformance and extension exercise

Status: ACCEPTED; requested qualification complete. Base: `d5b9b6f2ba1c751a43d13b960ea5c5a4e56b0ecf`
(accepted/published A10), branch `codex/task43p-final-recovery`.

Implemented one immutable type/version registration surface for current electrical,
physical, contribution, functional-role and observation providers. Reject missing
or conflicting capabilities before publication. Added `nmos-low-side-driver-alt@1`
with provider-local 680 Ohm gate resistance, 1.2 V threshold and beta 6, reusing
the current NMOS primitive, package and pin adapter. No new physics, routing,
instrument, replay or player-UI device branch is required.

| Gate | Contract and evidence |
|---|---|
| Native conformance | Real registry; all paired/join entries; declared model/terminal/package/fault/repair consistency; duplicate type/version, split capabilities, missing footprint/renderer and incorrect pin mapping negatives |
| Independent current oracles | Explicit signed seeds, three canonical role choices, independent stream/value equations and current generator epoch; no historical resolver |
| Source boundaries | Generic assembly, materialization, diagnosis, replay, routing and UI contain no added individual-provider branch/import; report actual extension knowledge by category |
| Production build | Final actual JDK8/GWT OBF build and all five permutations |
| Compiled conformance | Shared A11 declaration harness runs inside Task49 and publishes a separate declaration report. Task49 proves actual solver healthy/faulted observations, complete hypothesis proof, physical mutation and customer retest across explicit seeds covering every role variant; its forced-failure and debug-off routes check dispatch/privacy |
| Affected regression | Current Task49 and A09 strict reports, A10 staged-generation report/strict reader; native full current suite |
| Player flow | Real visible input on a seed containing the new variant: initial failed retest, measured fault, catalog repair and restored functional retest; curated screenshots |
| Closure | Frozen input/build/evidence hashes, optional focused source review, intended diff/whitespace, normal commit/push and remote SHA verification; Gmail after publication |

The three-entry role population changes seeded selection, so the current bounded
generator advances to epoch 6. Schema shape remains unchanged. Old development
outputs remain historical evidence; current physics, identity and determinism
are required. Existing A10 RC latency, queued repaint retention and blocked
scratch/crash-tab cleanup are carried forward as explicit limitations.

## Extension cost and ownership

| Category | Actual knowledge changed |
|---|---|
| Provider-local extension | `NmosDriverProfile` declares the alternate ID and 680 Ohm / 1.2 V / beta 6 parameters; `AlternateNmosDriverProvider` supplies its contribution through the reusable NMOS factory. Existing NMOS electrical, physical and observation adapters accept that immutable profile/identity. |
| Declared bootstrap | One alternate entry in `ConstructionProviderRegistry` joins electrical, physical, contribution, observation and package claims. The same registry now owns all nine current entries; former lookup switches delegate to it. |
| Generic safety | Physical materialization checks registered package equivalence. Existing primitive validation now rejects an unavailable NMOS model ID instead of silently accepting a label the actual adapter cannot implement. Neither check names the alternate provider. |
| Current interpretation | Bounded generator 6 identifies the expanded canonical role population; dependency interpretation v4 includes the registration surface. Current replay and seeded oracles consume the current epoch without a historical resolver. |
| Diagnosis and UI | Observation lookup now requires exact type and version. Generic diagnostic execution, routing, replay serialization, instruments and player UI require no individual-provider branch. |
| Qualification | Shared native/GWT declaration checks, explicit three-variant seeds, independent selection/value expectations and source-boundary falsifiers. The frozen mutation oracle adds the explicit 680-to-1000 Ohm alternative; actual Task49/A09/A10 reports and real player repair qualify its behavior. Build and run costs are recorded below. |

This is a bounded construction registry with current consumers, not a promise of
automatic import support or arbitrary new physics. The source check rejects
individual-provider references in ten generic owners; its in-memory falsifiers
test that boundary reader separately from the electrical runtime proofs.
The [actual changed-file ledger](extension-ledger.json) classifies all 29 changed
code, test and runner files; it separates the registry consolidation from the
ordinary variant's provider-local knowledge and single registration entry.

## Final results

- Native: 24 suites PASS, including 427 provider assertions/19 negatives, six
  explicit plans and 78 physical parts. Current independent seed/value/role and
  diagnostic oracles pass. The fresh report contract passes 470 assertions and
  the actual provider reader passes 125. Native scratch cleanup and exit 0 are
  recorded in [the native receipt](native-summary.json).
- Build: final actual JDK8/GWT OBF build PASS, five permutations; compile 82.971 s,
  link 1.574 s, exit 0. The only Java delta after native execution was the compiled
  alternative-repair fixture; unchanged native paths qualify by the explicit
  dependency audit. Fresh reader checks qualify the later role-regex/test delta.
  All 1,061 final source/test/script inputs and five outputs remained unchanged
  through closure. [Provenance](candidate-provenance.json), [build results](build-summary.json).
- Compiled declarations: PASS427/19 negatives. Actual Task49 separately passes
  397 assertions, four solved signed-seed cases, every serviceable fault/repair
  owner, wrong/correct/alternative repairs and original-owner restoration. The
  alternate's 680-to-1000 Ohm alternative passes the real customer retest.
  [Runtime report](task49-report.json), [compact qualification](qualification-summary.json).
- Actual compiled values/descriptors for nine signed seeds and role vectors for
  six signed seeds pass the maintained independent Python oracle. The
  [value receipt](compiled-values.txt) and [role receipt](compiled-roles.txt) are
  field-preserving transcriptions of Task49 JSON, not generated expectations.
- Task41 and A09 strict readers PASS; A09 executes 97 assertions/11 negatives,
  three driver variants, eight composed hypotheses and 1,056 samples. Its
  independent reader passes 144 assertions. [Diagnostic report](diagnostic-report.json).
- Current A10 compiled regression PASS1,273 assertions and all 24 frozen attempts;
  first p50/p95/worst 529/3,025/3,025 ms, immediate repeat 519/3,094/3,094 ms.
  RC admission/repeat takes 48,198/48,030 ms, maximum active unit 4,201 ms and
  maximum cancellation-handler cleanup 4 ms. Existing budgets are unchanged.
  [Generation report](generation-report.json), [strict summary](performance-summary.json).
- Forced Task49 failure returns its exact canary with both reports absent;
  debug-off also exposes no verifier/provider report. These are distinct from
  positive solver qualification.
- Real player seed4: unrepaired customer retest fails; unpowered channel-B RG
  measures OL; catalog replacement measures 680 Ohm; independent HIGH/LOW inputs
  work and the powered customer retest passes. All five screenshots were inspected:
  [unrepaired](01-unrepaired.png), [OL](02-open-resistance.png),
  [680 Ohm replacement](03-replacement-measured.png),
  [B LOW with A operating](04-channel-b-low.png), [repaired](05-repaired.png).
  The final normal page has no error logs or private verifier reports.
  [Exact interactions](player-flow.json).
- Optional read-only source review PASS, no blocker. A11-D1 is nonblocking
  TEST/TOOL coverage debt: physical NMOS parameters are derived from validated
  electrical declarations rather than independently compared by the backing
  validator. The current private immutable path has no reachable bypass.
  [Review scope and disposition](independent-review.json).

## Retained failures and limits

The initial native NPN replay canary used a seed now selecting alternate NMOS;
its unchanged model-mutation assertion now uses the explicit NPN seed2 fixture.
The first compiled Task49 run exposed a missing 680 Ohm entry in its frozen
alternative-repair oracle. The actual strict reader then exposed its old two-role
regex. Each was corrected and freshly qualified; prior failures are retained.
No solver assertion was removed or changed to accept a failed repair.

One Task49 navigation produced a transient target-closed read and a
MutationObserver error of unestablished origin. Rebinding the still-open tab
yielded its complete passing report; fresh isolated Task41/A09, A10 and normal
player pages had no errors. [Browser diagnostics](browser-diagnostics.json).
No browser-wrapper certification or native desktop-input qualification is claimed.

This work does not qualify larger boards or modest-hardware performance. A10's
queued repaint-retention follow-up and slow RC admission remain. Owned A11 tabs
and preview are closed; raw task evidence is retained externally. Earlier routing
scratch remains because automatic approval review rejected guarded deletion as
blocked by policy. The former A10 crash tab is no longer listed; no root cleanup
of it is claimed. [Resource closure](resource-closure.json).
