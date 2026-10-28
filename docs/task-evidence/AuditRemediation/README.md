# Audit remediation: production Player review

Date: 2026-10-28. Ordinary replay: `tsj-alpha/7/EASY/LED_INDICATOR/3`.
This packet belongs to the audit commit containing it. Product R12 Phase 2,
normal RB56 enablement, HARD and Q100 are not qualified by this LED flow.

## Final-source artifact identity

Both final-source builds pass five permutations from the same 1,193 compiler
file inputs (JDK 8u502 and pinned GWT 2.7.0). Only variant/module selectors differ.

| Artifact | Input digest | Output digest | Cache JavaScript bytes |
|---|---|---|---:|
| Developer v6 | `7d563277334aafce8f4ca9353629d57f1089f9aec5fcfb4e09e2051f1bf8f4b3` | `3ccfc65a9fa2a58298bdf4065de572a7c5ce8aa55c1e6ae135186ab212239f57` | 17,336,218 |
| Player final v2 | `28fac0962f5419d58e8583a02cb4229dce752e3aab335259b7b1c7124941387d` | `697484d190a03bb9cac4e62258fd15f14c33a2d07ea914170380d74cde1bb5df` | 9,391,454 |

Commands: `scripts/build.ps1 -JavaHome <JDK8> -Variant Developer` (exit 0,
122.360 s), then `-Variant Player` (exit 0, 88.476 s). Player removes
7,944,764 bytes (45.83%); it retains no old Developer cache or A10 failure sentinel.
All five old Developer v6 cache URLs return 404. A fresh ordinary running-board
check with the Developer query flags below exposes no verifier report, developer
controls, netlist or answer key, with no browser warning/error logs. Its screenshot
was saved and inspected outside git. Maintained preview stop exits 0 and positively
releases the exact process and port. No task browser tab or preview remains.

## Saved visible artifact identity (v5)

Both v5 builds used the same 1,193 compiler file inputs (JDK 8u502, pinned
GWT 2.7.0, five permutations). The variant/module selectors differ by design.
The later E02/E04/E06 numerical corrections have their own fresh build and
compiled evidence above/below. The visible LED interaction below belongs to these
explicit v5 receipts; it constructs none of those three electrical models.
Reuse audit: independently reconstructing both v5 compiler-input digests from
the current tree with the three pre-correction model files substituted matched
both receipts exactly. The other 1,190 compiler files are unchanged. These fixes
changed no UI script, CSS, LED circuit, instrument controller, routing or retest
owner. This bounds reuse of the visible LED flow; the electrical and compiled
gates use current-source evidence separately.

| Artifact | Input digest | Output digest | Cache JavaScript bytes |
|---|---|---|---:|
| Developer | `edbf1c991bbc00541a5eb691024e48701879432c81ed8b8354ab97e0e6485d6f` | `fca89b409c1c55515a1983d8dbf0be98e9f687cbafb0dc62289166ef73a14ea0` | 17,324,090 |
| Player | `2a0b726d39574e0c94a779c0e985fd9fa20215a87f0cc8128dcc84e122db9746` | `00025270df377928d8f4c58cc0bd705898dbd1da1a8022c41196453eb05958aa` | 9,379,277 |

Player removes 7,944,813 bytes (45.86%) from these five compiled permutations.
All five old Developer cache URLs returned HTTP 404 after Player promotion; none
remained in the deployed directory. The Developer-only A10 failure-canary string
was absent from every Player permutation. These checks supplement the actual
Player query-flag check below; they do not claim removal of production diagnostics.

Commands: `scripts/build.ps1 -JavaHome <JDK8> -Variant Developer` (exit 0,
121.655 s), then `-Variant Player` (exit 0, 87.957 s).
The launcher used `scripts/start-preview.ps1 -Variant Player -QuickPlay -Port 8927`
without requesting a build. Raw receipts/logs remain outside git in the task's
OS-temp evidence directory.

## Actual visible input

The Codex in-app browser opened actual Player production output with
`tsjQuickPlay=true`, `tsjDebug=true`, `tsjVerifyA10=true` and
`tsjVerifyResistance=true`. No verifier result/report appeared. Normal menus,
board view and instruments remained available; no netlist, answer key or neat
schematic was exposed.

The following used real browser input; no injected controller action was used:

1. Main menu → Open a saved replay code → enter the exact code above → Prepare
   replay → Accept ticket and start.
2. AC selected then selected again: `aria-pressed` false → true → false, matching
   the visible selected styling. Hz/frequency followed the same sequence.
3. Request reload with unfinished work. The bench remained intact. The browser
   exposed no JavaScript-dialog object, so **native prompt rendering is NOT PROVEN**.
   The JS contract separately covers retained boards, PREPARING/RETEST, completed
   RESULTS and teardown; this flow does not substitute for a Chrome prompt test.
4. Run customer retest on the untouched board: **did not pass**.
5. Power OFF → click R1 on the board → Component. Visible bands were BROWN,
   BLACK, RED, GOLD. Remove component → Parts Tray showed one loose part.
6. Shop search `1k`, `10k`, `1M`: each showed exactly one of 76 entries, respectively
   1000, 10000 and 1000000 Ohm. Search `1k` again and Add to Parts Tray. The tray
   count became two and the corrected selection/install hint appeared.
7. Close Shop → Parts Tray → select the acquired `Part 4 - 1000 Ohm +/-5%` →
   **Install as Circuit / R1** → power ON → Run customer retest.
8. **Customer retest passed**, with the reported behavior resolved. No browser
   warning/error logs were recorded. Navigate to `about:blank` from RESULTS:
   completed in 46 ms with no dialog. Close the task tab.

The maintained stop command positively terminated the exact preview process and
released its port. No browser tab or preview is retained for this packet.

## Inspected screenshots

All five images use the browser's existing viewport, without resizing or editing.

| Image | Observed state |
|---|---|
| [Initial workbench](01-player-initial.jpg) | Ordinary PCB, meter, ticket/retest controls; Developer query flags ineffective. |
| [AC selected](02-player-ac-selected.jpg) | Selected V~ button and `--- V RMS` display. |
| [Metric shop and hint](03-player-shop-1k.jpg) | One 1000 Ohm result, tray count two and accurate click/drag installation guidance. |
| [Unrepaired retest](04-player-unrepaired-retest.jpg) | Customer retest did not pass. |
| [Repaired retest](05-player-repaired-retest.jpg) | Function verified; ready to return to the customer. |

Automated final-source compiled Developer verifiers are separate evidence: resistance
controller lifecycle PASS, E02 PASS (1,321 assertions/six cases), and A10 PASS
(3,134 assertions, 24 frozen attempts/eight D01 benchmarks, cleanup PASS). A10's
worst frozen attempt was 4,098 ms; max observed operation 2,889 ms; max work 622;
cancellation max 5 ms. The 90 s / 640 / 5 s application bounds remain unchanged.
The complete native/process gates and their final outcome are recorded in the
[main audit report](../../audit-remediation.md).
