# U04 / U05 / REL-A combined qualification

Status: implementation and compiled qualification PASS; final visible workflows
and human EASY/MEDIUM trials remain IN PROGRESS. No release acceptance, staging,
commit, push or email is claimed. [Gate ledger](gates.json).

Base: ec10aa8b849149c33ae00153873c94c6aa6c9ca6, branch
codex/task43p-final-recovery. Current execution digest:
`b5080dcf8a26650157498b86f5d4c3a6d88f15a57d4ba3c4e79c85f2ebda747d`.
The [preview receipt](preview-menu-focus.json) matches the actual HTTP/tree
identity and [1,151 frozen inputs](final-ui-inputs.json).

The actual native/build, A10, Task43P and Alpha runs use compiled Java from
execution `35b0bbb28d03de8c828895f6d3390ec11d2ff2c32748f8fa42e4210b6d47dfa1`.
The [two-file UI/test delta](menu-focus-input-delta.json) changes only the
closed-disclosure keyboard focus filter and its DOM fixture. Every consumed
Java/GWT/native/model/electrical input is unchanged. All subsequent compiled
regressions and visible workflows use the current b5080dcf preview.

Current passing technical gates:

- [Maintained native contracts](native-contracts.txt): 33 Java suites plus
  independent oracles, exit0 and cleanup PASS. Q15 has6,771 assertions,
  all eleven constructions, fourteen required selector inputs, and both known
  bad exact seeds rejecting after80 placement attempts. U04 has56 native
  assertions and U05 has23.
- [Actual JDK8/GWT production build](gwt-build.txt): five permutations,
  70.426s compile/1.325s link, exit0.
- [Alpha38](alpha-compiled.json): 1,217 assertions,110 acquisitions,
  105 stale callbacks,72 negative checks and223 mutation checks;851,241ms
  operations/2ms cleanup with the exact original owner restored.
  The [strict reader](alpha-readers.txt),20 malformed canaries and
  [forced failure](alpha-forced-negative.json) pass. RC0/2/3 maximum units
  are1,934/1,921/2,004ms with unchanged five-second limits.
- [Dedicated Q15](q15/final-runtime-summary.json): eleven seeds/four designs/
  three faults;481+80 assertions;560,040ms operations/2ms cleanup.
  Admission p50/p95/max31,078/58,285/58,285ms; max work unit2,016ms.
  [Readers and malformed canaries](q15/readers-current.txt) pass.
- [Affected compiled regressions](regressions-current.json): Task41 twenty
  routes/182 samples; Task49 397 assertions; A08 1,103; E01 21; E03 46 raw/
  113 mutation assertions/six cases; A07 58 pure/71 runtime/59 model;
  U01 1,237 assertions/six live targets with unchanged identity.
  Required strict readers and forced/malformed negatives pass.
- [A10](a10-report.json): 1,274 assertions/24 attempts,707 RC phase/abort
  checks and14 observation-cleanup checks; strict reader and17 malformed
  canaries pass. [Task43P](task43p-runtime-reader.json) has zero open blockers,
  real overlay cleanup and actual owner/callback replacement coverage.
- [Quick Play](quick-play-final.json): full sixteen-entropy/eight-family
  selection/construction, unrepaired rejection, correct completion and fresh
  session isolation. The legacy report does not expose separate operation and
  cleanup durations. This compiled contract is separate from visible entry.
- [UI contracts](../U04/ui-contracts.txt):49 fresh assertions and syntax PASS.
  [Actual keyboard checks](../U04/menu-focus-runtime-pass.json) cover collapsed/
  expanded menu and Resources wraps. Settings persistence, exact drafts,
  return focus and inert background checks also pass. Three useful screenshots
  are inspected; the original browser focus failure is preserved.

Normal RB15 entropy selects only
`0,1,2,3,17,42,101,-1,9007199254740993,-9223372036854775808,9223372036854775807`.
Quick Play, U04 New board and legacy New control board use the same Java selector.
The known bad seeds `-4518705223253195925` and `-5365808313541656343` cannot
emerge from it. Explicit replay/developer seeds remain exact and can reject.
Neither the eleven-seed cohort nor the independent28 PASS/2 FAIL sample is a
random-population estimate. The80 placements,90-second job,640-unit and
five-second unit limits are unchanged.

[Current visible-input evidence](../U04/player-runtime-b508.json) records
ordinary RC3 admission, cancellation retaining exact replay and OFF power,
repeated RB15 random draws, exact-seed failures and recovery. Remaining full
repair, entry and privacy flows are tracked in the ledger. Agent-operated
inputs are not human calibration; [the U05 trial protocol](../U05/README.md)
requires ordinary EASY and MEDIUM attempts with outcome, time, actions and
usability observations. No human results have been received.

The alpha advertises selected low-voltage5-20-package content, including the
sixteen-package procedural control board, plus identified three/four-package
practice boards. HARD/PSYCHOTIC, mains, general multilayer routing, large boards,
durable session saves, economy and scoring are unavailable.

[Development history](development-history.md) preserves earlier RC deadlines,
concurrent-verifier load, rollback, startup/build, Task43P, Quick Play and A08
fixture failures and their candidate boundaries. [Root review](root-review.md)
records resolved findings. A11-D1 remains nonblocking coverage debt; actual
Browser input does not qualify the historical Windows CDP wrapper. Raw timing
upper bounds include observation/transport delay unless explicitly separated.

The separately authorized visual pass follows stable alpha acceptance in its
own commit. P05 remains the next unstarted roadmap milestone.
