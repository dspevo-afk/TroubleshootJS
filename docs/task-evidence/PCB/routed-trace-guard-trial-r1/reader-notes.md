# Routed trace guard reader

`assess.py` is a strict offline reader for the bounded COPY-only native trial. It pins the proposal, protocol, suite manifests, accepted15 baseline, and the original accepted15 native log. It does not inspect or start the trial runtime.

Run it after the matching phase artifacts have been saved in this packet:

```powershell
& $pythonExe -Xutf8 -B docs/task-evidence/PCB/routed-trace-guard-trial-r1/assess.py unit
& $pythonExe -Xutf8 -B docs/task-evidence/PCB/routed-trace-guard-trial-r1/assess.py routes
```

The unit phase requires exactly three ordered suite receipts: the routed trace guard contract (`assertions=N fixtures=3 evaluator=compactRouted`), the existing trace width geometry contract, and the existing REL-B session identity contract (`scope=IDENTITY_AND_ADMISSION_ONLY ownerRoundTrip=false`). It writes `unit-validation.json` only if no such file exists.

The routes phase recomputes the current unit validation from the current unit log, receipt, timing, and frozen manifest; `unit-validation.json` must match it exactly. It then requires all fourteen roots in protocol order, each with version15 followed by version20, and each whitespace report immediately followed by its 43-field case report. All fourteen version15 cases must match all42 saved non-time fields with exact JSON types. Version20 requires density15 and preserves parts, pads, live graph, source intent, layout seed, and routing seed. Its source-plan digest may differ. Each route-suite block must contain two roots, two version pairs, and four case rows; log and child receipt markers/reports must match exactly.

Native admission and strict fitness remain separate results. A valid native phase can therefore produce an assessment with `nativePhase.status=PASS`, `admission.status=PASS`, and `strictFitness.status=FAIL`; all fourteen metric deltas and each regression are retained in `assessment.json`. The routes command exits2 on that strict-fitness failure after writing the assessment. Existing phase artifacts are never overwritten.

The reader rejects noncanonical JSON integers and seeds, duplicate keys, non-finite numbers, boolean-as-integer values, unrecognized PCB reports, missing or reordered rows, mismatched receipts, failed or skipped output, missing terminal/cleanup markers, timing schema changes, and per-case times above60000ms. Phase timing uses the exact eight-field schema and requires a finite elapsed value from0 through36000 seconds as a reader-side receipt bound, not a maintained runtime allowance. The maintained JVM/search/physical limits remain fixed.

## Synthetic canary result

`assessment-canary-results.json` records 43 PASS canaries across strict JSON, pinned inputs, phase receipts, ordered report identity, exact15 replay,20 graph identity, all four strict metric regressions, timing, marker, cleanup, and fresh-unit-prerequisite checks. The four metric regressions each retain native admission PASS while strict fitness fails. Native runtime evidence is `NOT_RUN`; the canaries use synthetic fixtures and fixed accepted15 evidence only.

The synthetic receipt binds these exact consumed bytes:

| Input | SHA-256 |
| --- | --- |
| `assess.py` | `1827c6486665b6d6617d5dade01b1c256a07d5ee02bbe1385ecd80433d18fd7f` |
| `check_assessment.py` | `f9c33316226c411c4bd4841b50e30d38f4c74e2c8973cc93568c93f3004a9ddc` |
| `assessment-canary-results.json` | `e0c45a4bf6f96fbc9f31fe8a0a32dd43ada09624a507ea06824ca6cae42bc475` |
| `proposal.json` | `4ec7c2be29cd54a8037ec7b829f53b1785a83767a7a94a2bb258067cf3382576` |
| `protocol.json` | `6473758c9248a9958cb73229b0a0cfa8c825d8c66712f9341db4b92d7644389e` |
| `unit-suites.json` | `38abfae20c6e2e179a541bcd701bf12f91dd26f2ca55b8c6f8cb8bc7ef7e6097` |
| `routes-suites.json` | `034cb9fb8ba6b91df3d4ffab257412b7d745923c3e31f5434285d23ab0490a31` |
| `accepted15-baselines.json` | `12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816` |
| original accepted15 `final-native.txt` | `db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d` |

The packet-root `assessment-canary-results.json` also contains this table's bindings for automated freeze verification. These finite native checks do not establish explicit20 player replay, solver, manufacturing, all-seed behavior, or production promotion.
