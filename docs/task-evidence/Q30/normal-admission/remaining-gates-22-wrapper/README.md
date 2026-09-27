# Q30 remaining-gate wrapper preparation

Candidate: `16a922b2b757dfcd1cbc3a0230ab2b97ac2fbb50`, branch `codex/q30-multirail-qualification`. These wrappers are prepared for the root to execute serially. No browser, verifier, build, or test gate was launched during preparation.

Find this directory through `.tools/q30-remaining-gates-22-path.txt` in the repository. From the repository root, set `$repo = (Get-Location).Path` and run:

```powershell
$temp = (Get-Content -LiteralPath '.tools/q30-remaining-gates-22-path.txt' -Raw).Trim()
& (Join-Path $temp 'browser-modern-22.ps1') -RepoRoot $repo
& (Join-Path $temp 'native-final-05.ps1') -RepoRoot $repo
```

`browser-modern-22.ps1` runs the maintained Python driver with `-B` into a unique OS-temp directory. Before launch it checks all 392 `web-final-21` hashes, all 1,133 `build-final-21` hashes, and the driver hash; it records before/after per-file hashes and aggregate SHA-256 values. It returns PASS only for exit 0, 33 passing family/profile/ordinal cases with unique replay codes, all three passing isolated saved replays, no page errors, `serverStopped=true`, no owned survivors, and unchanged audited inputs. Its output directory pointer is `.tools/q30-modern-22-path.txt`; its wrapper run-directory pointer is `.tools/q30-modern-22-wrapper-path.txt`.

`controlled-p09-22.ps1` is prepared for use after the root confirms population22 PASS. It freezes the 392 web paths plus the admission driver and declared controlled-negative spec (394 total), audits the unchanged 1,133 build paths, and invokes `verify_admission.py` with `-B`. It requires the six expected case IDs/outcomes, passing driver result, no browser/HTTP errors, `serverThreadStopped=true`, and no owned survivors. Immediately before launch it writes `.tools/q30-p09-controlled-22-path.txt` for the driver output directory; `.tools/q30-controlled-22-wrapper-path.txt` points to the wrapper logs. This controlled result does not establish full P09 by itself.

`native-final-05.ps1` runs `scripts/verify-current-contracts.ps1` without `-Suite`, using the specified JDK8 and receipt path `docs/task-evidence/Q30/normal-admission/native-final-05-receipt.txt`. It snapshots all 1,248 paths from `native-final-04-inputs.json` with current hashes and requires the only differences from that prior manifest to be the U04 catalog mutation verifier and the Q30 normal execution policy test. It requires the actual runner exit code 0, exactly one full PASS marker, exactly one cleanup marker, no runner/cleanup failure markers, and a nonempty receipt before starting either strict reader. It then runs `check_native_service.py --runner-scope full --seeds 7,13,4,14,43,3,10,64` and the full `check_corpus.py` against that runner log. The three reader inputs (both readers and the normal-admission README) are hashed immediately before the readers and checked after them. Native wrapper logs/results stay under its unique OS-temp run directory; its pointer is `.tools/q30-native-22-wrapper-path.txt`.

Each wrapper streams merged process output to a UTF-8, no-BOM log with autoflush and captures `$LASTEXITCODE` immediately after the foreground command. Failure logs and partial evidence are retained. The required native receipt and sidecars are written at the specified repository path. Root owns any later evidence copying/sanitizing and strict combination; do not infer full P09 until native05, population22, and controlled22 have all passed the root strict reader.

Preparation checks: PowerShell parser PASS for all four scripts. Hash helper canaries PASS (unchanged map yielded 0 differences; changed/added map yielded 2; removal yielded 1). Foreground command canaries PASS: Python exit 0 and exit 7 were captured exactly, including Unicode stdout and stderr in durable logs. Literal CRLF PASS and cleanup-marker matching canary PASS.

## Script SHA-256

| File | SHA-256 |
|---|---|
| `Q30GateWrapper.Common.ps1` | `300F498C07717B5C0590F01E44710E58782766FEB153088973F381A7D9069B42` |
| `browser-modern-22.ps1` | `47295E13A9FC83201842154C0A3023489700DB0F4D354557A236E6282320FB8C` |
| `native-final-05.ps1` | `0B9979C93A00C7597FEC1F0ED67E449825795ECC6BC9158275F2CC0EF453CD10` |
| `controlled-p09-22.ps1` | `4C3CAD7B31FF5F9AB2DF93DC6A2C654634050CB0EF126390C2722C281D89C725` |

Canary logs and the compact canary result are `helper-canary-positive.log`, `helper-canary-negative.log`, and `helper-canary-summary.json` in this temp directory.