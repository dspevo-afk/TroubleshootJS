# Quick Play physical uniqueness evidence

Candidate base: `0018f3d7456a12b02fe72299205531fe2e572267` on
`codex/task43p-final-recovery`. The enclosing commit is the publication identity.
The [current checkpoint](../../CODEX_TASK_REPORT.md) contains the full runtime
family/profile audit, architecture decisions, matrix and limitations.

## Automated population

`QuickPlayPhysicalMatrixContractTest` discovers the nine selectable pairs from
`PlayerFamilyCatalog`, generates 20 boards per pair from declared signed-long
roots, and checks current envelope/geometry, seeded placement and routing,
exact parsed replay, nonrepeating full and novelty signatures, relative part
arrangement, merged copper and outline variety. The `PHYSICAL_MATRIX` rows in
`native-unique-final.txt` show 180/180 valid structural boards, 180/180
unique novelty signatures and 0/180 rejected roots. Each family has 20/20
distinct component-placement, trace-path and merged-copper records. Relay has
19 distinct relative layouts and 20 board outlines. The focused final-source
matrix and guard contracts pass in `native-unique-final.txt`; the complete
final-source gate passes 55 Java suites and independent oracles in
`native-final.txt`.

The timed matrix output includes **both** in-memory constructions per seed,
including exact replay. It does not include full solver diagnostic admission.
There is no comparable before-task timing cohort with identical source and
instrumentation. The previous 144-case structural cohort remains useful for
the prior candidate, not a before/after speed claim.

`QuickPlayGateContractTest` covers sixteen-entry successful-board history,
deterministic fresh roots under repeated entropy, exact replay without history
mutation and a copper witness whose route segments change but drawn geometry
does not. Duplicate candidates use the existing bounded rejection path.

## Retained verifier outcomes

The initial full run `native-full.txt` stopped at an old internal-pair route
assumption. `native-full-final.txt` then exposed top-face-only escape and
fixed-route-order expectations. `native-full-accepted.txt` passed those checks
and stopped at a P05 developer witness whose source had shifted to the normal
bottom face. The focused `native-footprint-final.txt` and `native-p05-final.txt`
pass after restoring the witness's original unrouted top-face fixture and
checking actual physical connectivity instead of route order. No production
validator or routing limit was relaxed.

`native-complete.txt` is the first full pass before final copper normalization
and recent-root coverage; `native-final.txt` supersedes it on the accepted
source.

`native-p09-audit.txt` retains every P09 physical candidate under the unchanged
normal envelope: 9/11 regression passes, 2 explicit `ROUTE_BUDGET` rejections
(seed 17 and `Long.MIN_VALUE`), 9/12 held-out passes and three explicit
rejections. That corpus invokes the raw layout generator. Normal player
generation supplies the envelope to its bounded generator and then runs full
electrical and diagnostic admission. The separate 180-board player-path
structural matrix admits both signed-long extremes for RB15.

The RB15 raw candidate gate records 21/24 development passes and 43/48
held-out passes. Its three and five rejected candidates retain exact typed
board-size or route-budget outcomes in `native-tail-final.txt`. These are
candidate rates, not New Board launch success rates; admission can try up to
four deterministic candidates within the unchanged shared budget.

The current P07 developer-only layer comparison reports all 54 structural
rows, 11 prototype successes and explicit `DOMAIN_BARRIER_REJECT` outcomes
where a placement has no isolation corridor. `native-p07-audit.txt` retains
these rejects without counting them as player boards. The final six older
construction/replay/corpus suites pass in `native-last-suites.txt`.

## Production build and visible player acceptance

`scripts/build.ps1 -JavaHome .tools/jdk8-download/jdk8u502-b07 -Style OBF`
passes all five production permutations. `gwt-final.txt` records 80.572 s of
compilation and 1.476 s of linking; its local workspace path is redacted.
The complete native command was `scripts/verify-current-contracts.ps1
-JavaHome .tools/jdk8-download/jdk8u502-b07`, with exit 0 and 55 Java suites.

`tests/browser/quickplay_procedural_acceptance.py` served the built `war/`
bundle locally and used real Edge clicks/selects in one persistent page. For
each selectable family/profile it selected the menu values, clicked New board
three times, waited for a ticket, entered the ready workbench, switched to
bottom copper and back, and returned to the menu. It then entered two saved
replay codes, checked exact accepted identity and powered each board off.
`browser-result.json` and `browser-live.txt` record **27/27** successful new
launches, **2/2** exact replays, no page errors, no private verifier metadata
and no owned process survivors. Preparation seconds and cleanup seconds are
separate; cleanup took 0.716 s. No launch failed; the 180-board structural
matrix had no rejected root. The raw RB15 candidate corpus retains its
rejection rates above.

The browser captured 54 top/bottom views. `screenshots/` retains top and bottom
views of launches 0 and 2 for every pair (36 images). Representative visual
comparisons include [LED first top](screenshots/led_indicator-0-top.png) and
[third top](screenshots/led_indicator-2-top.png), [relay first bottom](screenshots/relay_output-0-bottom.png)
and [third bottom](screenshots/relay_output-2-bottom.png), and
[RB15 first top](screenshots/rb15_control-0-top.png) versus
[third top](screenshots/rb15_control-2-top.png). The composed MEDIUM pair is
[first bottom](screenshots/composed_controlled_indicator-0-bottom.png) and
[third bottom](screenshots/composed_controlled_indicator-2-bottom.png).
Inspected layouts change component arrangement, connectors and copper paths.

The browser's task-named OS-temp output was copied and hash-checked before
cleanup. Automatic command review blocked recursive removal by policy of
`%TEMP%\TroubleshootJS-QuickPlayUnique-bf9c0507e5ce42ee9de28c9f73faadfe`;
no browser or server process remains. Earlier native
failure logs remain here as failed attempts, not passing qualification.
