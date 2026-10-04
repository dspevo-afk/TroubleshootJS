# Q30 headed representative player flows — local checkpoint

The 20-, 30-, and 40-part representatives completed visible powered-DC checks,
normal Shop acquisition, physical removal/compatible installation, and customer
retest. All three unrepaired retests failed and all three repaired results showed
`FUNCTION VERIFIED`. **Q30 is NOT ACCEPTED and remains disabled for normal play.**
Parent acceptance is pending; U06/U07/Q60 are unstarted. No production, save or
replay format change, new build/full regression run, publication, email or evidence
deletion occurred in this followup.

The audited private production build from the
[nonvisual checkpoint](../remaining-gates-20261004/README.md) was reused. Public
source identity is `a43f8165921f08c309636d2bf1a971905fdbc9382885c0708788d79744e1fb51`;
checkpoint HEAD is `579dc1fc9eb13bfe91bf8830d408205490db4655`, source base
`d37c3b3b8581abdeaa9b88b7bfb2349cff958d31`. The private copy has the single
catalog enable toggle; public registration remains false. Fresh before/after
checks cover 1,354 private raw inputs, 357 compiled files/five permutations, six
deployment files and nine pinned jars; 1,354 public inputs were rechecked too.
Prior cold77/native/build/menu/replay/source-archive evidence was not rerun.

## Actual input and observed results

Parent expressly authorized headed SDK locator, mouse, keyboard and screenshot
operations. One persistent Edge context opened the normal production menu with
`headless=False`; a real window handle/title and one ready page were recorded.
All interactions use normal UI controls. There is no page evaluation, hidden
snapshot/game-owner call, injected state or synthetic event dispatch. Actual
left/red and right/black clicks placed probes on the 20-part JOA pads; subsequent
readings also used the normal View → Probe terminal → Place probe controls.

| Seed / visible packages | Unrepaired observations | Replacement | Repaired observations / retest |
| --- | --- | --- | --- |
| 10387 / 20 | HIGH output 2.16 μV; customer retest failed | 5 V relay acquired, original removed, compatible `Output A / KA` installation | HIGH 11.983 V; LOW 2.16 μV; rail 5 V; FUNCTION VERIFIED |
| 10226 / 30 | HIGH output 2.16 μV; rail 0 V; entry-diode path 11.229 V; retest failed | Generic silicon diode, narrow lead spacing, installed at `12 V entry / DREV` | HIGH 11.983 V; LOW 2.16 μV; rail 5 V; FUNCTION VERIFIED |
| 10014 / 40 | Both HIGH outputs 2.16 μV; rail 0 V; enable-resistor path 10.512 V; retest failed | 10,000 Ohm ±5% resistor, wide lead spacing, installed at `5 V regulation / REN` | Rail 4.992 V; all eight output readings below; FUNCTION VERIFIED |

| A/B inputs | JOA | JOB |
| --- | --- | --- |
| LOW/LOW | 2.16 μV | 2.16 μV |
| HIGH/LOW | 11.983 V | 2.159 μV |
| LOW/HIGH | 2.159 μV | 11.983 V |
| HIGH/HIGH | 11.98 V | 11.98 V |

Frozen expectations remain ≤0.05 V at LOW, 10.8–12.6 V at HIGH and 4.75–5.25 V
on the regulated rail. Visible ticket waits were 21.698/38.358/89.400 seconds.
These are observer timings, not an additional cold benchmark. Application limits
remain 90,000 ms/640 shared units/5,000 ms active/coordinator. Three guided
representatives do not establish population timing or blind fault localization;
the independent frozen D01 and cold77 proofs remain separate.

## Preserved failures and limits

- The first headed attempt used `?tsjQuickPlay=true`, which starts quick play and
  bypasses the menu. The menu wait failed; its SDK cleanup also attempted to close
  after Playwright stopped. Raw failure and executed driver are retained under
  `failed-start/`. Its recorded instances were absent and port60200 closed later.
- In the corrected session, six command attempts failed: exact Difficulty label,
  wrong DC-mode label, unsupported relay lead lift, wrong Parts Tray capitalization,
  diode stock without its spacing suffix, and selection of a not-yet-acquired
  diode. Nothing bypassed disabled controls. Only subsequent observed successful
  controls/results establish repair. All 388 commands/responses match the result
  ledger; 382 are PASS and six FAIL. Host exit is not a UI acceptance oracle.
- OHM on the isolated 20-part coil and 40-part resistor, and DIODE in both
  directions on the isolated 30-part entry path, showed `UNKNOWN`. These are
  **inconclusive readiness statuses, not OL or measurements**. Exact live cause
  remains UNKNOWN. Static review finds Q30's active-readiness owner assesses all
  rails; passive DC can work while active modes fail closed. Q30 D01 declares DC
  observations and power-off service preparation, so native D01 PASS does not
  certify these active-meter checks. Parent must adjudicate this remaining limit.
- Relay lead lift is unsupported; remove/install are supported. Early removal
  controls were disabled and later normal removal passed after discharge/control
  refresh. No forced click or injected mutation was used.
- Corrected session inputs passed before and after, but its bounded host exit is
  **1**, preserved: the final body check observed its own controller-associated
  conhost before that controller exited. The raw host is not relabeled PASS.
  Independent post-exit release at21:02:02.3301555Z found all16 exact recorded
  instances absent and port60064 closed, with no audit termination. No task
  browser, preview or listener persists. Wrapper window20:36:06.6254228Z–
  21:01:11.4289788Z; final preview/process cleanup0.640s. Browser-close duration
  was not separately recorded; it precedes the final input audit/cleanup timer.
- The invoked UI driver was the workspace path, not its preserved copy. Launch,
  post-run and copied bytes match SHA-256
  `6cd53ec3619ec1d8877af692d34e92171bea28f59adab66019c86b25549edf74`;
  this does not claim immutable-copy execution. One page was recorded at ready;
  page count was not continuously enforced.
- [The first export check](failed-export.json) rejected a wrong authored Unicode unit parser before
  writing any packet. Corrected parsing accepts the actual Greek μ and micro-sign
  forms; numeric voltage oracles and raw readings are unchanged.

## Evidence and restart

[Flow audit](flow-audit.json), [full UI ledger](ui-result.json),
[source boundary](source-boundary.json), [actual host](host-result.json),
[independent release](release-audit.json), [static review](static-review.json), and
[screenshot inspection](screenshot-inspection.json) define the separate proof
boundaries. The read-only Luna MAX leaf reviewed APIs/ownership/source semantics;
it did not operate the UI, run gates, edit files or accept Q30. Root reviewed all
actions, the numeric conditions and selected images. Parent owns final acceptance.

Five selected actual headed PNGs are unchanged:

- [20-part unrepaired HIGH/probe polarity](screenshots/005-20part-unrepaired-high.png)
- [20-part verified](screenshots/012-20part-function-verified.png)
- [30-part verified](screenshots/017-30part-function-verified.png)
- [40-part bottom copper/unrepaired](screenshots/019-40part-bottom-unrepaired.png)
- [40-part verified](screenshots/023-40part-function-verified.png)

`derivation.json` binds raw and portable bytes separately; only known personal
paths are tokenized and UTF-8 BOMs removed. `packet-manifest.json` hashes exported
files; `packet-audit.json` checks that boundary. Raw commands, responses, other
screenshots, profiles, private build and all prior failures remain local. Personal
restart paths are in the task workspace handoff rather than this repository.
The historical “interactive tools unavailable” checkpoint is superseded: headed
SDK input was an available authorized route. It remains historical evidence.

Next: parent acceptance review, including the active-meter/host limits; any
necessary followup should use the current owners and fresh applicable gates if
production changes. Do not enable public Q30, relaunch the consumed cold77 grant,
or start U06/U07/Q60 based on this packet alone.
