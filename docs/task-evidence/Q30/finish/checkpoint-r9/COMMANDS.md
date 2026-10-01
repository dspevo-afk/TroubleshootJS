# Reproduction and evidence lookup

The evidence-packager command below is safe to rerun after gates finish. Choose a new output directory for each capture; existing outputs are never replaced.

```powershell
python <TASK_TEMP>/evidence-package/package_evidence.py `
  --repo-root <REPO_ROOT> `
  --output <TASK_TEMP>/evidence-package/after-gates-<unique-name>
```

Final r9 full-corpus capture:

```powershell
python <TASK_TEMP>/evidence-package/package_evidence.py `
  --repo-root <REPO_ROOT> `
  --output <TASK_TEMP>/evidence-package/final-r9-77-<unique-name> `
  --require-cold-revision r9
```

The current example includes all available revisions. Omit `--through-revision` to collect later scale plans, prepared roots, cold families, canaries, and the explicitly named optional final-gate receipt paths.

For the final r9 corpus, use `--require-cold-revision r9`. The guard reads only `scale-plan-r9-bound.json` and the exact `cold-r9-final77` directory, requires 77 unique planned host/reader pairs, and verifies each strict reader's plan digest and root seed. It never substitutes a focused r9 diagnostic or the obsolete unbound setup plan.

Example strict-reader and artifact lookup (expand gzip artifacts before passing them to the reader):

```powershell
python <REPO_ROOT>/tests/qualification/q30-scale-acceptance/q30_scale_reader.py `
  --report <PACKAGE>/files/cold-r9-final77/<run>/cases/<case>.report.json.gz `
  --plan <PACKAGE>/files/scale-plan-r9-bound.json.gz `
  --host-record <PACKAGE>/files/cold-r9-final77/<run>/result.json `
  --output <NEW_READER_RESULT.json>
```

Gzip files expand to the exact application JSON when their role is `raw_app_json`. Use `manifest.json` to find each artifact, verify its original/sanitized/package hashes, and map redacted locations. Host result JSON may contain path-only substitutions; case outcomes, resource statuses, report hashes, timings, errors, cleanup, input-audit fields and browser identity values are retained.
