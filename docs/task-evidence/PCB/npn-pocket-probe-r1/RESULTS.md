# NPN second sweep: NO_GO

**Finite task-only experiment PASS; NO_GO_NO_STRICT_ROUTED_GAIN.**

Base `a73eeaeb3edd9898a29156b761d20021c3c6db18`, branch
`codex/q60-faulted-profile-counts`. Production, maintained tests/scripts, solver,
RNG, seed/epochs, physical allowances and search/route/metric limits stay exact.
No private planner is promoted.

The exact NPN public replay380469407799690561 was constructed once in each
fresh JVM using a private planner with at most two sweeps sharing the original
60000 evaluation ceiling. Four guarded owner changes add the second sweep,
no-change stop and passive aggregate/accepted-move observations; three guarded
adapter changes substitute only that owner. The existing first-sweep ordering,
five targets, fitting/access/proxy gates and route budgets remain. This native
interpretation is explicitly task-only, not the current production replay.

| Observation | First sweep | Additional sweep |
| --- | --- | --- |
| Shared evaluations | 45 to68 | 68 to90 |
| Accepted translations | RLOAD(310,110) to(280,150) | LED1(600,90) to(570,100) |
| Enclosing-envelope area | 520442 to520442 | 520442 to510822 |
| Wire-demand proxy | 13353 to13344 | 13344 to12864 |

Coordinates describe the private proposal before later route/compaction, not
the displayed selected board. The extra sweep did real work and reduced the
enclosing-envelope proxy, but the routed proposal failed one unchanged gate:

| Metric | Published incumbent | Private routed proposal |
| --- | ---: | ---: |
| Area | 628530 | 618310 |
| Copper | 4300 | 3650 |
| Vias | 0 | 0 |
| Largest protected empty rectangle | 80000 | 84000 |

Area fell1.63% and copper15.12%, but protected void grew5%. The strict comparator
correctly retains the full original selected geometry
`bd41ae74d9e78fea77e1af1d9e13cb0fd9773dd123a86ecad0e0fcc4b431ada5`.
The decision reader separately compares that actual selected result with the
incumbent, so its external area predicate is false. The candidate tuple above
and reused physical reader explain the earlier internal rejection. No rejected
proposal geometry was reconstructed; no smaller production board is delivered.

Actual validation:

- Selected native PowerShell parser and Python syntax PASS; four generated root
  helpers reproduce their exact intended bytes. Before final freeze, source
  review caught the inherited anchor oracle measuring the opposite edge. Planner
  +x escape is inward from LEFT, -x from RIGHT. Correct NPN margins are30 each,
  rather than opposite816. Literal J1/J2 positives and shifted negatives PASS;
  prior reader is untouched. This explicit observer correction is pinned in
  `anchor-oracle-review.json` and the final seal, before any native phase.
- Missing private owner: real selected adapter rejects at binding, exit1 before
  compiler/native work. Canary phase actualexit0,0.3943648s; stderr private/hash-bound.
- Cold actual JDK8/native: PASS161, actualexit0,42.2000588s.
- Repeat fresh JDK8/native: PASS161, actualexit0,42.3427842s.
- Each tool exit preceded separate driver checks; all six recorded births exit.
  Phase times include maintained terminal cleanup, not separately measured.
- Final strict reader PASS. All nonclock physical rows, identities, work and
  accepted-move observations match. Only validated packing wall clock and its
  canonical token normalize. Physical admission/access/geometry, independent
  metrics, graph/endpoint inventories and corrected outward anchor binding pass.
- Positive clock-only, negative nonclock coordinate/original cap, accepted-move
  cap/nonanchor, and J1/J2 edge translation controls PASS. Move events reconcile
  with aggregate counts/proxies/evaluation intervals. Original graph and IDs stay.
- Published source1482/machinery24, two baseline helpers,12 published references,
  eleven final helpers and exact private native inputs are audited. Prior actual
  production player/build proof is applicable only to unchanged production,
  not to this private planner. GWT, browser, electrical and full matrix NOT_RUN.

Follow-up source review finds no vacancy-target acceptance bug. Movable-part
`vacancyDomainBox` and `fits` inset the outline by10, not the connectors' local
30. The sampler's320x250 corner clips to at most312x240 in that domain, too
narrow for RB320 or RPD340. The conservative40-unit grid masks whole overlapping
cells, excludes partial border cells, and considers one largest pocket/five
origins. Phase-preserving10-unit snapping is followed by exact fits/validation.
Those limits can miss placements; this review supplies no specific additional
legal move or justification for another target-search change.

Optional leaves performed source work only; root generated/reviewed, froze and
executed the helpers, then owns raw evidence/privacy and publication. No native
attempt failed. The anchor observer correction was caught before freeze/run;
existing older failures remain. Private raw data/owner/adapter are retained,
six current recorded births exit and no browser/preview/listener was launched.
All four blocked profile-cleanup actions and seven unrelated groups remain
untouched. No main merge/email, all-seed, optimum, manufacturing, performance,
new electrical/player or size claim. Next review should use the current finite
cross-family corpus to select a useful remaining gap instead of another
unmeasured variation of this seed.
