# A08: Multi-provider mutation and failure-isolated physical lifecycle

Status: **COMPLETE - QUALIFIED**. Base: `c5a0d5e31ce9848b0e06b7c04c25866034d3d090`.
Branch: `codex/task43p-final-recovery`. A09 remains unstarted.
The containing completion commit is identified by the tested source and reader
hashes in [candidate-provenance.json](candidate-provenance.json).

## Delivered boundary

Resistor and diode providers now use `PhysicalMutationIntent`, a restricted
`PhysicalMutationScope`, and validation/compensation receipts. Preparation checks
exact board, slot, installed provider, inventory storage and part ownership.
Commit validates the resulting electrical/physical state. Abort compensates only
owned state; failed compensation isolates that board instead of allowing more
actions. An obsolete operation cannot restore over a successor graph or owner.
The obsolete resistor-only scope is removed, not retained behind a wrapper.

Real remove, lift, reconnect, restore, catalog acquisition and reinstall paths
share this lifecycle. Wrong compatible values and diode orientation remain
possible, with real CircuitJS consequences. Original faults and secondary damage
remain separate. Probe/endpoint, model, attachment and inventory identities are
validated through failure and restoration; no fabricated meter readings are used.

Fresh installation rejects mutable layout, operational, behavior, scenario,
operation/retest, construction-receipt and family/temporal captures from an old
board. New wrappers do not make old controls or endpoints fresh. Stateless
observations and immutable geometry/diagnostic metadata may remain shareable.
Supported factories qualify current captures; arbitrary hostile Java providers,
universal deep rollback, capacitor migration and A09 are not claimed.

## Final validation

| Gate | Result and evidence |
|---|---|
| Maintained native command | PASS, exit 0: 17 Java suites, independent seed/value/role references, 466 strict report-contract assertions. JDK 8u502 and CPython 3.13.14. |
| Production GWT build | PASS, exit 0: maintained `scripts/build.ps1`, GWT 2.7.0 OBF, five permutations. |
| Final compiled routes | PASS: 14/14 through maintained route definitions, report extraction and strict readers on the actual production preview, using fresh Chromium contexts. |
| Actual A08 runtime | PASS: 1,108 assertions; diode open seeds 0/3 and short seed 0; 90 injected partial writes compensated; five fresh-install failure stages. |
| Resistor integration | Actual composition mutation verifier invoked by A08, including resistor compensation, original/secondary damage and failure isolation. Not merely a report fixture. |
| Fresh ownership | Real-install negative/positive probes for mutable holders, copied callback graphs, scenario wrappers, controlled/NPN/NMOS family controls and RC temporal endpoints. |
| Visible player | PASS: 68 recorded native mouse/keyboard action records on uniquely owned visible windows; diode and resistor diagnosis, wrong replacement, correction and customer retest. |
| Independent review | Final read-only gpt-reserve MAX delta review PASS for supported current production paths. Root reconciled integrated changes and final validation. |
| Candidate identity | Final source/reader hashes match the native and GWT candidate; documentation-only closure changes no qualified execution input. |

The 14 routes are A08 positive/forced-negative/debug-off; diode open/short;
A07 positive/forced-negative/debug-off; A06 positive/forced-negative/debug-off;
A03, Task49 and stored energy. Every ordinary route has no page errors. A06's
forced failure has its deliberate failure marker and one obfuscated `n7` error;
[a06-forced-error-adjudication.json](a06-forced-error-adjudication.json) records the
source interpretation and per-permutation AssertionError mapping limitation.

Earlier reviews found real fresh-owner defects. They were repaired and the full
native, GWT and compiled gates rerun. [review-history.md](review-history.md) keeps
those findings as **resolved history**, not final acceptance. [review.md](review.md)
is the final independent result; its static review does not claim root's tests.

## Visible player evidence

Native Win32 input selected tools, placed red/black probes, removed components,
chose catalog entries, installed replacements, changed board power and retested.
DOM reads supplied visible-control coordinates and observations only. No injected
clicks or private controller calls substitute for this player evidence.

- [Faulty diode measurement](player-diode-faulty-measurement.png): original diode is OL with board power off.
- [Wrong diode repair](player-diode-wrong-repair.png): reversed compatible diode remains installed and fails retest.
- [Correct diode repair](player-diode-repaired-retest.png): normal orientation passed retest after a 496.051 mV forward measurement.
- [Wrong resistor repair](player-resistor-wrong-repair.png): 100 kOhm replacement remains installed and fails retest.
- [Correct resistor repair](player-resistor-repaired-retest.png): 1 kOhm measured replacement restores the indicator and passes retest.

Normal completed-challenge interaction locking is preserved. The controlled
indicator was also opened through its normal button and probed; that observation
is not represented as a completed controlled-indicator repair.

## Reproducibility and limits

Raw native output/receipt and all compiled reports are retained as sanitized gzip
files. Readable build/native summaries, strict-reader outcomes, the full A08
failure-stage report, player result/action records and final hashes accompany them.
Personal filesystem paths and the ephemeral preview origin are normalized.

This certifies compiled application behavior and the maintained report readers,
**not the CLI wrapper's process/listener ownership or CDP transport preflight**.
Fresh graphs are checked for supported current factory/capture contracts, not
untrusted executable plugins. Bootstrap without an existing owner is not an
arbitrary-provider sandbox. No universal state rollback or future content claim
is inferred from these bounded resistor/diode tests. A09 remains unstarted.
