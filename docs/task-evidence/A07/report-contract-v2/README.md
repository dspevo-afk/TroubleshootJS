# A07 report contract V2 — integration checkpoint

**Status: ACCEPTED LOCALLY; publication pending.** Final A07, Current9, Player
privacy and both production builds pass. Root reviewed the integrated source and
owns publication. This packet records a bounded verifier prerequisite; it grants
no product-release or expanded electrical qualification.

Candidate branch: `codex/a07-report-contract-v2-20261031`

Base: `b48a140bfb90317ad44870ad25220933aa755963`

## Scope

The initial failure was an exact report-contract mismatch. The b48 A07 producer
emitted 26 passing runtime cases while the host accepted only 23. The original
host run waited 90,172 ms, accepted no route, then cleaned up in 65,926 ms. That
failure remains a failure; no old report is reclassified.

The maintained changes now cover four related boundaries:

- The A07 producer, host reader and independent report fixture require
  `TSJ-A07-SOLVER-2` and the exact 26-case set. Missing, duplicate, failed,
  unexpected and V1 cases remain rejected. Physics, type, count, latency and
  cleanup predicates are retained.
- The A03 CDP owner accepts one `beforeunload` only during the exact owned
  `Page.navigate` request, on the same socket, deadline, run, route, completed
  source URL and loopback origin. It requires both navigation and acknowledgement
  responses, exact destination readiness, and clears authorization in `finally`.
- The bound-receipt browser cleanup uses a fresh native parent census only for the
  protected-helper census and post-close late-child check. It retains the full
  CIM snapshot, independent current-ID checks, raw six-field CIM comparisons,
  retained handles, native creation ticks, late-child rejection and all resource
  proofs.
- Gate B's bound-receipt fixture follows the observation boundary and rejects
  missing or inconsistent native creation metadata. A separate task wrapper
  measures its existing selected group under the documented aggregate envelope.
  It changes no maintained route, send, solver, shutdown or cleanup limit.

No solver math, circuit model, route count, public admission, or player UI
flow changed. The Java producer change is a report-version update only.

The ninth inherited stored-energy route also needs the existing developer debug
flag. Its missing flag caused a preserved eight-route-pass/one-route-timeout run.
The final query adds only `tsjDebug=true`; it retains the same active verifier,
exact `PASS:stored-energy` reader and all nine routes. The older monolithic
verifier already normalizes this flag centrally and remains unchanged.

## Frozen plans

These four plans remain the scope anchors for this packet:

- [Report contract plan](PLAN.md) — SHA-256
  `721ec37a3c89fbf6c3a6c919b4270b295a3c8f70613889db59b57b216188295f`
- [Owned navigation plan](NAVIGATION-PLAN.md) — SHA-256
  `e4e0fe37276a2290469459246da5b9d5cc5aeee8fd1969803fd975e5abdc13b0`
- [Native parent plan](NATIVE-PARENT-PLAN.md) — SHA-256
  `a4cf71e3b9b8984c1e8d850996ef0b56c88a3aabb118711f5a4c734e12e94159`
- [Composite parent amendment](COMPOSITE-PARENT-PLAN.md) — SHA-256
  `b67bd6c43216ed4a6f4593f53aacd04af26646290ed7de62a6ea4096815a6217`

The [Gate B aggregate amendment](AGGREGATE-ENVELOPE-PLAN.md) is the fifth frozen
plan, SHA-256
`ec1c8d312ee4d6be927382d046a9ad93d7083f49502fe3193a38c0a5b52a402c`. Its
600,000 ms limit applies only to the selected natural-shutdown group wrapper.
Every maintained 15 s shutdown, 120 s cleanup, 90 s route, 5 s solver/send and
640-work limit remains in force.

The [stored-energy route amendment](STORED-ENERGY-ROUTE-PLAN.md), SHA-256
`93f39794bc59af24589da54711558fc6b1cbb41804ec8b1ef389116ab68420a2`, freezes
the final single-literal caller correction and its evidence-reuse boundary.

## Final source pins

| File | SHA-256 |
| --- | --- |
| `src/com/lushprojects/circuitjs1/client/A07SolverDeveloperVerifier.java` | `e351be3b09d3ce76d42b5614f5d0112f3133a25d17e85aa3a90e455f17002a65` |
| `scripts/verify-a03-browser.ps1` | `c790b2a95fab462fd275ee6ede981ad9997c1d046646eabacfdb31b9e76719f6` |
| `scripts/VerifierIsolation.psm1` | `5e0ae0c8fb6beb20c2d9fff3f652074136032197eab61f450c5fdceaa4e3a1fa` |
| `scripts/verify-gate-b.ps1` | `c686d7f7846a1b195ab812173f8a24b6dd0c1ba64fbadc56aebcad60a2be51d0` |
| `tests/contracts/a04-report-contract.ps1` | `326b4ffce60bbaed99c22c2cc43f5c7e5af116ca1ca74197cda27ded445c516b` |
| `tests/contracts/a03-navigation-contract.ps1` | `b4c559a79cf1e75083c4588254d443f8b1cfee4104e56c56b1a6e6436a284bc7` |
| `tests/contracts/browser-bound-receipt-native-errors.ps1` | `8b946a078077d7184a6343ca80733bcd59a9487b779d74ceab7638068722694b` |
| `tests/contracts/browser-bound-receipt-parent.ps1` | `0b4350f04a3846cf0bfbaf0a42560af7410359f41cd51ac39135340db24fa504` |
| `tests/contracts/browser-natural-exit-reuse.ps1` | `ebb8457d47a584a60e82a84c1cd2a3d844e3d615f553b71a9944fbade3474ae6` |
| `tests/contracts/browser-cleanup-budget.ps1` | `fe0e177dcb074ddda55cd21dfe6ad56492b83a7fd7b11787a85387612212e44c` |

## Completed selected checks

The strict controlled export passes (exit0,1.476s). [evidence.json](evidence.json)
has SHA-256 `b615f899d76c12f494a30c4763a3116d78caf5eedbb1013bb1896bc15ab3d433`;
the packet also contains the complete native/build input and both output
inventories. Exporter V6 SHA-256 is
`e553bd0b5585b8115a999042281de668f4a109608999a8968cb54e65aedcf092`.
V5's first export remains REFUSED/exit2: it incorrectly expected build target
`browser`, while the pinned manifest and maintained build contract require
`Compile`. V6 changes that one literal, retaining exact style and every other
predicate. No packet was written by the refused attempt.

The artifact paths below are relative to the task-owned scratch root. Result JSON
hashes bind the captured status and timings; raw logs, personal absolute paths and
process identities are not copied into this packet.

| Check | Result | Bound evidence |
| --- | --- | --- |
| A04/A07 report contract | PASS, 521 assertions, 8.394 s; final c790 source inputs stable. | `host-v2-report-contract5/result.json`, 504 B, SHA-256 `3d757693ef5e043e967517489476850719eadb042b3e16d52f7831ec97ee4857` |
| Pure owned-navigation contract | PASS, 61 assertions, 1.448 s; final c790 source inputs stable. | `host-v2-navigation-pure7/result.json`, 708 B, SHA-256 `af214b89d07628a544a8893a84983396e90a65161b90d50c6e0a15abcfc317a5` |
| Native error contract | PASS, 12/12 source-derived synthetic fail-closed cases, 1.346 s. This is not an OS access-denial or PID-reuse test. | `host-v2-native-errors1/result.json`, 745 B, SHA-256 `6f700f22511f2b15af736f8d2db2e0ff68799f60f36e0d86b83a32ce9322e189` |
| Native A07 execution contract | PASS, 306,531 assertions, 36.260 s; 1,061 inputs / 85,552,900 bytes, exact input pin `d61de93b59c179e667e7be6275c97200545e57642610e31d373f8e72a211c526`; native exit 0, termination proven, cleanup PASS. This result does not supply a separate cleanup duration; stdout explicitly says the full matrix and independent oracles were NOT RUN. | `host-v2-native-a07-3/result.json`, 1,495 B, SHA-256 `ec22ef3e417953b332ce1083b81ec5d776656b9f727f03fa9dabc36e3a552c5f` |
| Natural-exit reuse contract | PASS, 19 assertions, 16.120 s; real owned job-tree, staggered natural exits, identity checks and absence proofs. | `host-v2-natural-exit2/result.json`, 683 B, SHA-256 `7a40cba386dc8f39dccf99751229f877917b14e30e53a6beea51bbcde87eb54f` |
| Cleanup-budget contract | PASS, 68 checks over 512 profile files; operation 105.526 s, shutdown 11.067 s, cleanup 68.334 s of 120 s. | `host-v2-cleanup-budget2/result.json`, 687 B, SHA-256 `aacddf93cc316ea28f00fcef19b3106bfa577cd0acec1eee4abecd48848914b4` |
| Real navigation canaries | PASS: one trusted-mouse positive and three expected rejections; wrapper total 332.617 s. Every lane proves shutdown and cleanup; fixture cleanup PASS in 45 ms. The report honestly records zero generic assertions. | `host-v2-navigation-real5/result.json`, 1,923 B, SHA-256 `6fd79fe2c24abb1a408f5d174acdb2be06d10da19d87ebf1cb7616ba3409ea9f`; report SHA-256 `90399af756e027ebc6bcd77a7770265b90c8618ab50156c98b987822f2c7a6da` |
| Parent-census contract, direct lane | PASS, 42 assertions; exact module pin, empty baseline and live owned-child rejection. | `maintained-parent-canary-debug2/direct-cli-lane3/maintained-parent-canary2/attempt-8283c57d5b8e45c5a1f981de5d4ce0ae/result.json`, 18,713 B, SHA-256 `e25991e715e581256ae1736bf18a301f7db99a6f6f0d0d5c9926b03eebae00db` |
| Parent-census contract, bounded-module lane | PASS, 42 assertions; one-child baseline, complete unfiltered process census, owned-child exit and complete empty-tree/no-late-child proof. Both lanes include 10 synthetic CIM faults with no partial output and restored test state. The wrong-module pin is rejected before owner/attempt/child allocation. | `maintained-parent-canary-debug2/bounded-module-lane4/maintained-parent-canary2/attempt-774b16f9d5934318bb7b77e92e840be2/result.json`, 19,543 B, SHA-256 `8836a54a41dc8f54112dfa221154aa819b43e5bcfec2016db8d7246f812c17bd`; wrong-pin result SHA-256 `f71719288986134f720c8cf07289c1a6731034155657660329b72b8d999f9abc` |
| Gate B native-process probe | PASS as a selected probe, helper 5.737 s, exit 0 and termination proven. This is not the full Gate B group. | `host-gateb-probe-gate1/host-v2-native-process2-nativeprocess/result.json`, 3,721 B, SHA-256 `ae3ed49fe85ecf7798e90cb761ff131d6e436006e292ca16f1e1b7c5dc40487c` |
| Gate B natural-shutdown group | PASS, helper operation 201.360 s and wrapper 203.354 s, exit 0 and termination proven; all nine exact markers, stable inputs, empty stderr, no failure or cleanup-not-proven marker. It ran under the task-only 600 s aggregate wrapper. | `host-gateb-probe-gate2/host-v2-natural-shutdown3-naturalshutdown/result.json`, 6,632 B, SHA-256 `193b527d1869be3fb767c5dd4e01b5c9d929bfbecd4aed818890a7ea073f2681` |
| Final-source Player build3 | PASS, 134.098 s; actual JDK8/GWT switch from Developer, 1,199 compiler inputs and 357 outputs, complete output inventory and five caches / 9,513,277 bytes. | `host-v2-player-build3/result.json`, SHA-256 `bdd71084a55d87d25b644a3dc7bf093f657b589c5bde201354f80b7564edc448`; `output-inventory.json` SHA-256 `1ad2caf8f0ec64cb59b61da18565ad5e14d0160a022cf8dfdbb733024b09f404` |
| Final-source Developer build2 | PASS, 183.563 s; actual JDK8/GWT switch from Player, 1,199 compiler inputs and 357 output files. The complete installed output inventory exactly matches Developer1. | `host-v2-developer-build2/result.json`, SHA-256 `db7aae7243ff453f485008030efce372133d496cde46fe47303932e11761c75c`; `output-inventory.json` SHA-256 `e4dae12c4fec0fef445b2b1fadea5c629cd598fb32cb8d444124682e5f8c3a12` |
| Final Player privacy4 | PASS on c790: one compiled Player route/seven JS probes, ticket input and two DOM-negative controls; 106.422 s, cleanup62.845 s, shutdown12.779 s, stable inputs/artifact, exit0/termination. | `host-v2-player-privacy4/result.json`, SHA-256 `08bc447c65ad246ab12bff8fd420075972d3e310d68ffadb72f53ca33953f69b`; report SHA-256 `e2d5c02b606d5e88ec7b5a45531611bf6d1358c030613529dd43ecc8b7cea6b4` |
| Final full A07 command4 | PASS all9 routes, 310.573 s, exit0/termination, stable final source/artifact. A06 and A03 each handle/acknowledge one owned beforeunload; stored-energy verifies. Cleanup79.380 s, shutdown9.455 s. | `host-v2-compiled-a07-4/result.json`, SHA-256 `431a2c9f31bf9c05adec8e805faa392b0fdf793cee2dac3d0db9c54b6626a87b`; report SHA-256 `8b9b43d7e8c0313b6284bee8e83416a9f3a551616989dbf29c2b97990f838f34` |
| Final Current9 command2 | PASS all9 routes, 347.073 s, exit0/termination, stable final source/artifact and cleanup PASS; shutdown9.415 s. No aggregate cleanup interval is supplied. | `host-v2-compiled-current2/result.json`, SHA-256 `b92be1fce2399f18a4255e7b0bb73c20cf35d8bbaa17d623e935add56ca0fb63`; report SHA-256 `d395b99c43e7cca01baddb0dd792ecfcf060c2e466548ce6ae5a97f5df5726fb` |

The isolated Gate B orchestration AST check also passed all 12 modes in 31.244 s;
two modes intentionally wait 15.020 s and satisfy their unchanged deadline
rejections. The frozen plan binds launcher result SHA-256 `c01f46bbf546504c6c81883d2325aebb5a16310e3f3e397664600ecbd8ab6a24` and trace SHA-256 `25ecb02d7ef614ec56a7e12a376ed9d8766a5ea323582b8d33e4a99a4c16243a`. This narrow
orchestration check is distinct from the completed natural-shutdown group above.

The real navigation report records these individual operation, shutdown and cleanup
phases (milliseconds):

| Lane | Outcome | Operation | Shutdown | Cleanup |
| --- | --- | ---: | ---: | ---: |
| `positive` | PASS | 27,818 | 11,058 | 45,149 |
| `alert` | PASS_EXPECTED_REJECTION | 26,939 | 10,685 | 58,920 |
| `source-mismatch` | PASS_EXPECTED_REJECTION | 27,057 | 11,040 | 58,825 |
| `wrong-destination` | PASS_EXPECTED_REJECTION | 32,160 | 10,031 | 54,661 |

The focused navigation harness used its own 12 s destination-wait setting. It does
not replace the maintained 90 s route deadline. The A07 route, 5 s send, 15 s
shutdown and 120 s cleanup limits remain unchanged.

RealNavigation5 retains its original raw host8f input pin. The explicit source
audit `stored-energy-route-fix1/reuse-audit2.json`, SHA-256
`f016303e9a8417ec3f8b71f3888a88965b8d7b77916d3c0261c29398d7fea512`, proves
the sole byte-level query substitution, all18 AST-imported navigation function
bodies unchanged, and the same test/module/preview inputs. Its function-contract
evidence is reused on that narrow boundary; no old raw source hash is relabeled.
Player privacy is renewed instead because its executed route-factory body changed.

The native parent implementation is deliberately narrow: a complete fresh empty
native walk is reported as an empty native observation and does not claim a CIM
query occurred. Nonempty results are joined to a direct CIM set; CIM strings and
path remain authoritative, all six existing CIM field comparisons remain exact,
and native creation ticks must match the retained process start. The original
9-tick CIM precision predicate is unchanged. `QueryFullProcessImageNameW` casing
is not normalized and does not replace the CIM path. No helper-stop authority was
added.

## Preserved failures and intermediate diagnostics

- The original V1 report mismatch and failed host timeout remain preserved. The
  retained V1 report diagnostic and caller inventory are not qualification.
- Before the navigation change, all three A07 routes passed but the inherited A06
  `Page.navigate` timed out behind a beforeunload dialog after the real ticket
  click; A06 had not loaded. A separate Current9 pass is not reused as final
  evidence.
- Full A07 attempt2 on host8f passes eight routes, then the stored-energy report
  is absent at90.270s because its query omitted the required debug flag. The
  complete331.212s run remains FAIL with cleanup PASS79.036s. The final c790
  caller correction passes all9 routes in a separate fresh attempt; no case or
  electrical assertion is removed.
- The first RealNavigation1 attempt raced a source change from expected `c643`
  to `50b`; its event-array conversion was also lost. The exact `c643` input is
  unavailable and was not reconstructed. That run remains source-unstable FAIL,
  not a gate result.
- The first maintained parent wrapper assumed its host had no children. The
  bounded WinPS host actually owned a conhost, so `host-v2-parent-contract1/result.json`
  remains FAIL at2 checks, before the test child started, SHA-256
  `a7ec87dff51d954849ec783bca4ec8fbaac0c34a7cf0c1fbd4898181d12d0947`.
  V2 compares the full baseline and owned-child sets instead of filtering records.
  Its first bounded baseline loop then collided with PowerShell's read-only
  `$PID`; the failing six-check attempt is separate and preserved. Renaming that
  local fixes the final two lanes. A separate internal variable originally
  shadowed the case-insensitive explicit module-pin parameter; the final wrong-pin
  case now rejects before owner marker, attempt or test-child allocation.
- The first Gate B natural-shutdown wrapper attempt failed a fixture
  property/closure check and remains FAIL (helper exit 1, 153.797 s operation),
  `host-gateb-probe-gate1/host-v2-natural-shutdown1-naturalshutdown/result.json`,
  SHA-256 `cb0861a045c5366c21935c0eb468bab89cee07b81559337d388ebd90bd1664cf`.
  The second hit the old 180 s aggregate timeout at 180.425 s and has no child
  exit or termination receipt; it remains FAIL/UNPROVEN,
  `host-gateb-probe-gate1/host-v2-natural-shutdown2-naturalshutdown/result.json`,
  SHA-256 `2323665d75a896c2ea8b3c4880abfe11a5e1ceb9219cf695788e21077a242300`.
  A later empty root census does not prove that failed run's cleanup. The final
  third attempt passes with the task-level 600 s envelope; the earlier failures
  remain failures.
- The native image-path casing mismatch is retained as a rejected alternative:
  `K32GetModuleFileNameExW` returned the same spelling as
  `QueryFullProcessImageNameW`. The final composite keeps CIM strings as
  authority. The private 30-pair alternating provider comparison measured native
  medians of 12.3–14.0 ms versus CIM 92.6–96.1 ms; projected CIM showed no useful
  timing benefit. These selected-host checks do not establish universal host
  speedup or prove PID reuse/access-denial behavior.
- An early native-helper review found that a failed first Toolhelp call could be
  treated as an empty set and that `CloseHandle` failures were ignored. Those
  pre-fix source bytes were not retained. The final 12-case synthetic contract
  covers first/partial enumeration and process/snapshot close errors, including
  preserving a primary error when close also fails.
- The first integrated native-census candidate passed its Player route but
  exceeded the unchanged 15 s shutdown bound at 16.335 s. The selected second
  candidate diagnostic shut down in 13.403 s with 63.968 s cleanup; it remains
  private diagnostic evidence, not final maintained-source acceptance.
- Early console/report-reader and pin-binding failures remain in scratch as
  failed attempts, not silently recast as passes. The task history and raw
  failure evidence remain in the task-owned scratch set and the root
  [task-report history](../../../CODEX_TASK_REPORT.md).

## Acceptance and limits

The final independent review was source-only; it did not rerun gates. It found no
material integration blocker after tracing the full Gate B success path. A
preexisting exceptional-path follow-up remains: its final fallback wait ignores
the Boolean result and removes the release signal. Accepted PASS already requires
the earlier retained-start wait and natural-absence proof; this follow-up concerns
an already failing canary and grants no cleanup proof for that failure.

| Gate | Status |
| --- | --- |
| A07 renewal after exact restoration of the legacy script | PASS attempt4; earlier attempt3 retains its own prior tree provenance |
| Current9 regression on final host source | PASS9/347.073s, exit0/termination/cleanup; no aggregate cleanup interval is supplied by its report |
| Final Player3 switch/build and privacy4 on c790 | PASS; both renewals use the final source tree |
| Full matrix, 30-root / 2-by-31 renewal, and Q100 routing/electrical admission | NOT RUN |

No E06, normal-release, public admission, playable-100-route, or electrical
qualification follows. There is no visible Player-flow change in this task, so no
repair screenshots or manual player-flow qualification are claimed. HARD and
PSYCHOTIC remain unavailable and unrun; the historical protected-unread boundary
is uncertified. The CI zero-runner prerequisite is not run. The packet does not
claim a completed full matrix or reuse a separate earlier Current9 pass.

No raw report, uncontrolled log, personal absolute path, or process identity is
copied into this README. Root owns evidence curation and publication. The controlled
JSON packet binds selected raw evidence hashes; raw logs and process/profile
identities remain outside the repository.
