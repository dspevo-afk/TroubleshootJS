# Attribute-read failure canary

This bundle demonstrates one narrow host-runner behavior: `compiled_attribute_acceptance.py` now fails a case when Playwright `Locator.get_attribute` records an error, even if a later read reaches the expected terminal state and valid report. It does not test CircuitJS, GWT, Q30 behavior, or simulator-controller flows.

## Source and change

- Repository candidate: `codex/q30-multirail-qualification` at `02450fb07ea63dc6fa0966b0a3b79f04a12f7c9f`.
- Source: `tests/browser/compiled_attribute_acceptance.py`, SHA-256 `1bb267655c97e35f2869925ae91d92a56c708256305e4bfb10106a172d2748a4`.
- Exact HEAD-relative one-line diff: [`runner/compiled_attribute_acceptance.patch`](runner/compiled_attribute_acceptance.patch).
- Pre-fix runner SHA-256 from the preserved old run: `4d0e724e563ff709397dffbddc200d7f52a1eb7ddff8cc68301fffcbe0deddf5`.

The change adds `read_errors` to the existing `browser_error` predicate. It does not change read timeouts, case deadlines, terminal-state logic, cleanup, or fixture behavior.

## Selected-Edge canaries

Each run used the runner's actual Playwright `msedge` persistent context and isolated profile. The wrapper/audit guard checks the Edge process identity, exact canary attributes, state/report recovery, lack of unrelated page/HTTP/console/listener/navigation errors, stable inputs, server shutdown, and zero owned browser survivors.

| Run | Runner/case | Attribute read | State and report | Input audit / cleanup |
| --- | --- | --- | --- | --- |
| Preserved pre-fix negative (`old-negative-62c8c89b2e10490889d63fad771c7923`) | PASS / PASS | One 10-second `TimeoutError` | Later PASS state and valid matching report | PASS / PASS |
| Post-fix positive (`new-positive-e19d1cbb3281453e8157bfdcf169bb24`) | PASS / PASS | No read errors | PASS state and valid matching report | PASS / PASS |
| Post-fix negative (`new-negative-c4189192b78a4569af97a03dfb73a832`) | FAIL / FAIL (runner exit 1) | One 10-second `TimeoutError` | Later PASS state and valid matching report; case did not time out | PASS / PASS |

The negative fixture removes the real `<html>` element shortly after DOM ready, holds it absent for 15 seconds, and reinserts it with `data-tsj-host-canary-state="PASS"` and a valid JSON report. That provokes the 10-second attribute-read timeout and then verifies recovery. The fixture is intentionally synthetic and only proves fail-closed handling of this host API error.

## Reproduction

From PowerShell, with the repository checkout and Python/Playwright/Edge prerequisites available, create an isolated OS-temp copy for each reproduction. Replace the two placeholders with the local checkout and this evidence directory:

```powershell
$repo = (Resolve-Path '<repository checkout>').Path
$source = (Resolve-Path '<this evidence directory>').Path
$runner = Join-Path $repo 'tests\browser\compiled_attribute_acceptance.py'
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('tsj-attribute-read-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempRoot | Out-Null
Copy-Item -LiteralPath (Join-Path $source 'run_canary.ps1') -Destination $tempRoot
Copy-Item -LiteralPath (Join-Path $source 'audit_canary_result.py') -Destination $tempRoot
Copy-Item -LiteralPath (Join-Path $source 'fixtures') -Destination $tempRoot -Recurse
Copy-Item -LiteralPath (Join-Path $source 'specs') -Destination $tempRoot -Recurse
New-Item -ItemType Directory -Path (Join-Path $tempRoot 'results') | Out-Null
New-Item -ItemType Directory -Path (Join-Path $tempRoot 'logs') | Out-Null
& (Join-Path $tempRoot 'run_canary.ps1') -Mode new-positive -RunnerPath $runner
& (Join-Path $tempRoot 'run_canary.ps1') -Mode new-negative -RunnerPath $runner
```

The reproduction wrapper writes only beneath its unique OS-temp copy. The exact specs, fixture HTML, wrapper, result auditor, sanitized compressed run receipts, and source patch are included. Gzip receipts use stable names under `raw/`; browser profile databases were omitted. `evidence-index.json` records original and stored artifact hashes. Paths rooted under a user profile were replaced with placeholders before storage.

## Separate retrospective audit and limits

[`retro/host-error-retroaudit.json.gz`](retro/host-error-retroaudit.json.gz) is a separate read-only audit of 25 prior host receipts. It reports all five host-error arrays empty for those archived runs, with matching case/result receipts, input audit, and cleanup. It is not part of the three canaries above and did not rerun them. The retained D01 host receipts are seed-specific: seed 10387 with 20 packages is a clean host PASS, while seed 10014 with 40 packages is a host FAIL. This canary does not change either result and does not establish normal-player admission or full-matrix qualification.

No production behavior, GWT build, native service gate, full browser matrix, or acceptance budget is claimed here.
