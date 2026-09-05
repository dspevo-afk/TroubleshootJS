# Task 43P C1/C2 acceptance evidence — 2026-09-05

This packet contains the complete acceptance evidence for the C1 production
repair, C2 verification repair and authorized efficiency changes. Final packet
review passed for the authorized publication and Owner Review handoff. Owner
approval has not occurred; Task 44 remains unstarted.

Tested branch: `codex/task43p-final-recovery`; starting HEAD
`8bf442416a2fa2c0c9d654d1efa14e754c2b7ee7` plus the intended diff; source/verifier
digest `9e4d9457411db8c24fbb74b3d6655d38a14b3a9a2c0ff930068b7033ef79b663`.

| Artifact | What it proves |
| --- | --- |
| [Acceptance index](acceptance-index.json) | Exact commands, build/source hashes, original exits, run identities, cleanup, reviews and failed-attempt dispositions. |
| [A–I runtime](runtime-observations.json) | Legitimate exit `0`, no open observations, four real measurement cases closed, seven cleanup canaries and complete supported snapshot checks. |
| [Physical correspondence](physical-correspondence.json) | Six families × seeds 0/2/3, eighteen triad PASS packets and nine semantic negatives each. All six invocations retain their original identities after the final BOM fixture delta; legacy aggregates remain `2`. |
| [Compiled falsification](source-falsification.json) | Eleven exact compiled catches: two anchor repair cases, eight earlier physical cases and one current-Java C2 case. All are reused after the final BOM fixture delta with original proof/build identities and qualified cleanup. |
| [Efficiency validation](efficiency-validation.json) | Eleven mutation anchors and four preflight canaries, original compiled-byte reconstruction, dependency audit, actual route timing and full Gate B checks. Preflight is not compiled proof. |

The five screenshots below are real built-in Browser captures of the production
preview at `tsjChallenge=led&seed=3`, using visible mouse and keyboard controls.

| Screenshot | What it proves |
| --- | --- |
| [Unrepaired retest](visible-unrepaired-retest.png) | The original customer complaint fails the public retest. |
| [Resistance and probes](visible-resistance.png) | Board power is off; left click places red and right click black on R1. Selecting OHM again exits the mode. |
| [Original removed](visible-removed.png) | Public Remove changes the board and puts the original resistor in the loose-parts tray. |
| [Wrong repair](visible-wrong-repair.png) | The installed 2200-ohm replacement fails the public retest. |
| [Correct repair completed](visible-completed.png) | The 1000-ohm replacement passes customer retest and completes the repair, with terminal controls disabled. |

The first eight reused source cases retain their original compiled hashes;
current preflight mutation text differs only by CRLF/LF. C2's current preflight
matches its original before/after mutation bytes. Relevant producer, detector,
manifest, build/toolchain and isolation dependencies remain applicable. The
current 500 ms proof budget, deadlines, ownership checks and actual exits are
unchanged. Phase and cleanup diagnostics never grant acceptance.

The final helper delta corrects a preflight fixture that previously claimed BOM
coverage without BOM bytes. Fresh preflight and full Gate B returned `0`; the
positive fixture now verifies `EF BB BF` and exact byte restoration. All eleven
mutation definitions and all production/web bytes are unchanged. The final
dependency audit preserves earlier browser/compiled proofs as reused evidence
under their original digests; it does not relabel them as final-digest fresh runs.

Snapshots cover the supported fresh-candidate/detached-original boundary.
Generic epochs, deep same-owner rollback, nullable-target/synthetic-settlement
schema limitations and broad privacy remain bounded follow-ups. Privacy evidence
is limited to the exercised ordinary LED3 DOM/accessibility states. Failed runs
remain unaccepted, including those with separate exact manual or natural-exit
absence evidence. Sanitized projections retain raw-artifact hashes and identities;
private raw diagnostics and retained failed profiles/claims remain outside Git.
