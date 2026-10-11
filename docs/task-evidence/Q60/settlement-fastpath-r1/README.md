# Q60: already-settled entry avoids a redundant update

2026-10-11, over published `16527de548874582bf412fb90c09fe2b038e7ab3` on
`codex/q60-faulted-profile-counts`. **Q60 remains IN PROGRESS / UNACCEPTED.**

`GeneratedRuntimeDeveloperSettlement.settle` now returns after its work-scope
check when the exact board/controller is READY, the authoritative runtime is
settled, and instrument NONE has no queued acquisition. This avoids an extra UI
frame and incidental live solver advance at a satisfied boundary. The original
fallback, pending-work behavior and paused/running restoration remain intact.
Both independent 13-unit REPAIR programs, 9-unit FAULTED, numerical/model cadence, seeds,
oracles and the original 90 s / 640 work / 5 s limits are unchanged.
The omitted idle frame removes incidental advancement; identical intermediate
solver trajectories are not claimed.

| Final-source gate | Actual result |
| --- | --- |
| Native152 | PASS D01 168 and A10 190 assertions; 24.810 s; 1,353 inputs unchanged |
| Actual JDK8/GWT build32 | PASS five permutations/linking; 86.928 s; 1,222 inputs unchanged |
| Current Edge155 compiled146 | PASS pure 306,503; runtime 103/26; models 59/21; scale 16; Task41 failure/recovery |
| Original60/-1022 compiled147, profiling off | FAIL deadline in HYPOTHESES: 90,015 ms / 519 work; native 96.927 s |
| Original40/-1017 compiled148, profiling off | PASS full production D01: 5 hypotheses, 55 samples, 516 work, 27,245 ms; native 33.960 s |

Three added real compiled cases use a fresh disjoint LED and the existing Task41
snapshot. Repeated idle calls in paused/running states preserve accepted
observation, time, model dump and frame sentinel. Actual pending analysis runs;
active queued DC acquires a finite reading, a second drain is idle, and the exact
protected owner/graph/instrument state is restored. All original cases/oracles
remain. The negative frame sentinel avoids rollover ambiguity; page/console
error arrays were empty. It is not a dedicated update-invocation counter.

The original60 terminal/report was observed without a host timeout. Its phase
is RESTORED; HYPOTHESES records 61,735 ms / 418 work, max active unit 1,618 ms.
All application, browser, listener, observer and native ownership closure passed.
Original40's complete private proof does not certify normal player repair/retest;
`ordinaryCustomerRetestPassed` is false in both private reports. Frozen146/147/148
contain 1,829/1,837/1,847 inputs. Root rehashed all final frozen and actual bindings;
all 1,170 source and 392 web files remained unchanged. Eight original untracked
files remain byte-identical.

Native151/build31 PASS before the fixture-only rollover witness correction are
preserved; final-source gates are native152/build32. Original40 preparation
failed before launch because the earlier actual report also recorded its runtime
metadata reader. A separate preparation explicitly froze those identical bytes;
old frozen controls and the failed preparation are preserved. No case was rerolled.

No end-to-end speedup or complete changed-source six-case acceptance is claimed.
The retained143 profile still guides cost diagnosis. A follow-up geometry-review
candidate was rejected: fresh hypotheses already share sealed layout values;
its whole geometry-validation category is only 769.458 ms inclusive/overlapping,
and deleting the first check changes malformed-geometry rejection ordering.
No source/test/rerun followed that finding.

**Next:** continue Q60 proof-cost work at a materially larger measured boundary,
preserving independent electrical owners and both repair proofs. Normal
RB56/HARD/save-load remain held; exact60 visible/session NOT RUN; actual modest
hardware BLOCKED/unavailable; REL-B/Q100 unstarted. All prior failures, original
Q30 91.616 s unknown failure, six missing inventories and late BLOCKED audit remain.
No unchanged Q30 cold/warm run, unrelated process action, Core/Android change,
C4130/server access, main merge or email.

[Actual sanitized receipts and artifact digests](settlement-fastpath-evidence-r1.json).
[Prior original-six/profile/routing results](../current-six-profile-r1/README.md).
