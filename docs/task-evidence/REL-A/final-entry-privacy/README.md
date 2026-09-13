# Final entry/privacy runtime candidate

Execution `5ea881de6b5c6132b19ff55fd581b2e52be131090f0cec6dc041224e15032623`.
Source/test/script inputs are frozen in ../entry-privacy-inputs.json; the owned
production preview matches ../preview-entry-privacy.json. Reports are captured
through built-in Browser from developer-published DOM attributes. Application
controller calls are not injected. Actual player-input evidence is separate.

Status: final technical gates PASS; human calibration and acceptance pending.
Alpha38 PASS1217 assertions,110 real acquisitions,
105 stale callbacks,72 negatives and223 mutation checks;884074ms operation,
2ms cleanup and original owner restored. The strict reader,20 malformed
canaries and forced failure with owner restoration PASS.

The identified Task43P runtime passes the maintained strict reader with
zero open blockers across all eight lane groups. The first invocation omitted
run/route IDs and its reader FAIL remains in task43p-runtime-missing-provenance.json
and task43p-runtime-first-reader-failure.json. The fresh malformed-reader
contract probe also passes. This does not qualify the historical Windows
CDP wrapper.

Q15 all11 PASS481+80 assertions,557220ms operation/1ms cleanup; strict reader,
16 malformed canaries and forced owner restoration pass. A10 PASS1274/24
attempts,707 RC phase/abort and14 cleanup checks; strict reader/17 malformed
canaries and exact forced failure pass. Quick Play's full selection/completion
corpus and fresh Task41/Task49/A08/E01/E03/A07/U01 regressions pass, including
the maintained electrical/Q15 combined readers and A07 strict negatives.

Actual visible inputs pass normal RC3 preparation/cancellation, RB15 random
selection and exact replay/error recovery, complete RB15/MEDIUM repair/retest,
Settings/Resources/menu focus/privacy, and two ordinary Quick Play entries.
See [RB15 repair](rb15-player-input.json), [Quick Play entries](quick-play-player-input.json),
[U04 inputs](../../U04/player-input-entry-final.json), and
[MEDIUM repair](../../U05/player-input-entry-final.json). Screenshots were
inspected. These actions do not substitute for human EASY/MEDIUM trials.

The [final input audit](final-input-audit.json) verifies all1151 consumed inputs,
all1435 execution files, current preview HTTP/tree identity and the owned
preview process. Prior candidates and failed orchestration/input attempts retain
their original evidence. No source inputs changed during final qualification.
