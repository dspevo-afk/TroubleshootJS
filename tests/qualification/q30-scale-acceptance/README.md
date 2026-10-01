# Q30 scale acceptance wrapper

This package adapts the frozen `q30-normal-screen` harness to the 77 signed-long
roots in `acceptance-plan.json`, plan epoch 4, the existing 90,000 ms / 640 work /
5,000 ms limits, and one- or two-channel boards. It uses the existing isolated
host runner and does not change shipped source. `prepare.py` copies the exact
working source into a unique OS-temp export, applies the generic isolated guard
and this package's overlay, and records per-file source and input identities.
The local HTTP server sends `Cache-Control: no-store` on every response so each
compiled-resource navigation can be captured with its response body.

Before any cold job, compile the prepared app and run its visible
`tsjNormalMode=manifest-export` screen. That screen calls the production Java
`Rb30Plan.resolve` and ordinary `PlayerLaunchRequest.random(...).generation()`
path for every frozen root, then emits all 308 candidate manifests, their Java
plan identities, and the root search canonical. Preserve the raw app report and
the host `result.json`: the latter binds the report byte hash and capture times.
The subsequent materialization time is not evidence that declarations preceded
cold evaluation; use the export host record's finished time against the first
cold-run start time.

Materialize the evaluation plan once, after preserving those pre-run artifacts:

```powershell
python tests/qualification/q30-scale-acceptance/freeze_plan.py `
  <repo> <prepared-root>/prepare-receipt.json `
  <manifest-export.report.json> <prepared-root>/app <scale-plan.json> `
  --manifest-export-host-record <manifest-export-host-result.json>
```

For each cold run, pass its app report, the same `scale-plan.json`, and its host
result to the strict reader:

```powershell
python tests/qualification/q30-scale-acceptance/q30_scale_reader.py `
  --report <app-report.json> --plan <scale-plan.json> `
  --host-record <host-result.json> --output <reader-result.json>
```

The reader rejects mismatched source, manifest, wrapper, prepared-input or runner
identities; changed budgets/order; incomplete candidates or attempt accounting;
warm/private caches; failed cleanup; and incomplete proof. It requires all five
hypotheses and 17 solver samples per hypothesis for a one-channel published
candidate or 37 for a two-channel one. A clean timeout remains a completed
non-pass. Focused negatives run with:

```powershell
python -m unittest discover -s tests/qualification/q30-scale-acceptance `
  -p test_q30_scale_reader.py -v
```

Each reader result records `readerSha256` for the exact reader source bytes,
`scalePlanSha256`, and `freezePlanSha256` for the co-located plan freezer. Keep
that result with each app/host pair and verify its reader hash against the
submitted `q30_scale_reader.py`. These postprocessing helpers are separate from
the prepared GWT app identity; do not rewrite an existing preparation receipt or
overlay identity to imply they were part of its build.
