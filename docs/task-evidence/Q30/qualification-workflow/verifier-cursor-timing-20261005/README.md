# Q30 verifier cursor timing — focused evidence packet

Candidate HEAD: `4bf9a6ac9d630f10b2408423295f0933d9bb052f`
Verifier source identity after the focused fix: `096a9bf708b549dc3e0323fc1b55af1e271a13a2846e2a91ae6b2f04cdafb944`
Changed source SHA-256: `3a5dee8c4b21e577f8196946925d8dac65b17895cb209a8d2d091796aad74ad6`

## What changed

The one-file developer-canary timing fix moves charged relay cursor capture and the first bounded solver step before ordinary isolation settlement. It retains the physical, cursor, power and restore guards, and records cursor work and settlement separately. The focused source audit pins 1,355 final-source inputs. No public physics model, threshold, cap, save/replay behavior or normal catalog enablement changed; Q30 remains disabled.

The independent static review warned against saying that a single 50 ms step clears every physical guard. The three positive compiled receipts confirm the narrower behavior: after the first cursor step the relay is still physically undischarged, and after isolation settlement the part is still unavailable. Availability becomes true after the remaining bounded cursor work completes. Each positive case reports five work units with a maximum advance of 50 ms per unit; total advances are 0.25 s for seed 10387 and 0.15 s for seeds 10226 and 10014. Host-operation times were 45.5567486 s, 63.353892 s and 143.0157722 s respectively. These are extended developer-fixture clocks covering cold canary, proof and warm work; they are not normal 90,000 ms admission qualification. The normal 90,000 ms job, 640 shared-work and 5,000 ms active-operation limits were unchanged.

## Evidence chronology

| Stage | Result | Interpretation |
|---|---|---|
| Baseline compiled 31, before the fix | **FAIL**. Ten core cases passed; 20 positive D01 cases failed before electrical proof. The declared typed seed-35 rejection passed. Strict audit recorded 723 errors. | Preserved failure; not a timeout or proof of the new fix. Exact classification is in `receipts/baseline/compiled31-failure-census.json`. |
| Baseline native 83, before the fix | Command completed with exit 0 and 83 suites; maintained audit **PASS**, zero issues. | Historical baseline only. The command receipt itself is marked pending receipt review; the separate audit is the acceptance check. |
| Baseline append 26, before the fix | Command completed with exit 0; maintained audit **PASS**, zero issues across 26 expected rows. | Historical baseline only; does not validate the changed source. |
| Baseline build/archive, before the fix | Shipping build **PASS**, five permutations. Source archive **PASS_SOURCE_ARCHIVE_ONLY**; its receipt says acceptance was not assessed. | Preserved historical gates only. |
| Focused native after the fix | **PASS**, two Java suites, 75.2973158 s operation. | Focused contracts only. Cleanup duration was not separately recorded. |
| Focused GWT build after the fix | **PASS**, five permutations, 83.4187844 s; 1,355 inputs checked. | Focused changed-source build. |
| Actual compiled D01 four-case canary after the fix | **PASS**: seeds 10387, 10226 and 10014 passed; unchanged typed seed-35 rejection passed. Maintained semantic audit **PASS**, zero errors. | Focused browser canary only, not the full 31-case cohort. Raw case reports, states and report hashes are included. |
| Focused release audit | **PASS** for 12 recorded identities; port 56366 had zero listeners. | Exact recorded focused resources only. |

The focused host cleanup receipt reports 1.2585414 s. These focused results belong to the after-source identity above. The baseline passes belong to the earlier source identity `926d99d51a94b6282481156e8d7dba045de545c52c9901ce78d838fda4609efc` and are not fresh acceptance of the fix.

## Limits and restart material

After the verifier change, full native 83, append 26, compiled 31, cold 77, menu/private and archive gates are **NOT RUN**. No full qualification claim is made. The 4 h 12 min quiet allocation and fresh cold-run grant remain pending; no cold sequence was launched. The previous headed visible proof is historical, with no fresh GUI QA after this verifier change. The packet includes frozen case specs, exact focused scripts, the exact maintained `compiled_attribute_acceptance.py` source, before/after verifier snapshots, focused source snapshots, selected controlled receipts and case reports. Absolute profile, temp, repository and runtime paths were replaced with named placeholders; `evidence-manifest.json` records each original-source and packet SHA-256 and whether bytes were copied exactly or path-sanitized.

The supplied raw mirror did not include the focused D01 host log. The runner result, case inputs, terminal states, application reports, semantic audit and release receipt are included. The raw mirror itself remains unchanged outside this packet.

For restart, use the focused scripts/specs in `scripts/` and `specs/` after restoring the task-local pointer roots and environment paths represented by placeholders. The scripts are evidence copies, not a launch request. No application/native/browser gates, app tests, benchmarks or browser processes were launched while preparing this packet; validation was limited to copy hashes, JSON parsing and privacy checks.

## Packet checks

`packet-audit.json` records JSON parse round-trip and privacy-scan results. `evidence-manifest.json` contains raw-to-sanitized hashes for every selected source artifact. The checkpoint preservation audit records eight pre-existing untracked items unchanged and no deletion.

Use [the binary evidence archive](evidence.tar.gz) for byte-exact hash checks across Git checkouts. It preserves all 52 reviewed packet files; SHA-256 `89a24307e8040d7f56c3d437dc7a89e05344ff9a9c3d1a9f4bf39447f6a39e35`. The individual text copies may undergo Git line-ending conversion. Archive roundtrip and all 49 artifact hashes were checked before packaging.
