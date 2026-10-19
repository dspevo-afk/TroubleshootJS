# Global pocket descent offline reader

`assess.py` validates the frozen COPY-only global-pocket packet and its saved phase receipts. The canary runner builds synthetic logs and reports in memory; it does not open native `unit.txt`, `routes.txt`, receipts, timing outputs, or implementation source. Contract loading reads the pinned accepted15 baseline and its original source log.

Run the offline canaries with the private Python interpreter using `-X utf8 -B`:

```powershell
& $pythonExe -X utf8 -B docs/task-evidence/PCB/global-pocket-descent-trial-r1/check_assessment.py
```

The phase readers are available after root has supplied phase evidence:

```powershell
& $pythonExe -X utf8 -B docs/task-evidence/PCB/global-pocket-descent-trial-r1/assess.py unit
& $pythonExe -X utf8 -B docs/task-evidence/PCB/global-pocket-descent-trial-r1/assess.py routes
```

The ordered unit suites cover global pocket descent, medium whole-plan cost, vacancy targets, and REL-B session identity. Root confirmed 11 named global scenarios and one literal cost fixture against the final source review. The first marker therefore requires exactly 11 fixtures and `evaluator=globalPocketDescent`; the cost marker requires exactly 1 fixture and `evaluator=mediumWholePlanCost`. Both also require positive bounded assertion counts. The vacancy assertion count is positive and bounded. REL-B remains `scope=IDENTITY_AND_ADMISSION_ONLY ownerRoundTrip=false`.

The routes phase requires seven ordered suites, two fixed roots per group, versions 15 then 22, and four whitespace/case pairs with `evaluator=planMedium`. It accepts exactly 28 whitespace and 28 `PCB_GLOBAL_POCKET_CASE` rows. The fourteen version15 rows match all 42 pinned non-time fields with exact JSON types. Version22 requires density15 and preserves parts, pads, live graph, source intent, layout seed, and routing seed; source-plan and geometry digests may differ.

For every case, the reader parses six `statisticsCanonical` placement-score records in ranked order, requires the exact component field order, bounds each component to a nonnegative signed long, and independently recomputes each total using the frozen weighted formula. The global score gate requires candidate2's total not to increase and every score component for candidates 0, 1, 3, 4, and 5 to remain exact to saved15. This checks the recorded placement cost only; it makes no electrical-physics or route-quality claim.

Native admission, global score monotonicity, and strict physical fitness remain separate. Strict fitness requires no increase in area, protected empty rectangle, copper length, or vias, plus at least one area or protected-empty-rectangle decrease. A native PASS with either global-score or strict-fitness failure is retained in `assessment.json`; the routes command exits2 after writing it. Unit-validation and assessment artifacts refuse overwrite. The 36,000-second phase limit is only a reader receipt bound; the per-case 60,000 ms bound and native search caps are unchanged.

The fresh in-memory run passed 51 named canaries: the inherited strict-reader cases and the unchanged-metrics / void-only fitness boundaries, plus three cost-unit marker checks and three global-score checks. These include candidate2 total increase and other-candidate score change as separate failed gates, plus malformed, duplicated, and formula-inconsistent score-record rejection. The fixed-count-plus-one checks derive 12 and 2 from the root-confirmed counts. Negative canaries catch only `ReaderError`; unexpected exceptions propagate. The fresh receipt is [assessment-canary-results.json](assessment-canary-results.json); it records the current consumed hashes, `nativeRuntimeEvidence=NOT_RUN`, and `sourceCallEvidence=NOT_RUN`.

Frozen consumed SHA-256 values:

| Input | SHA-256 |
| --- | --- |
| `assess.py` | `0a45548e3091f6bccbf34d6ceab9fe1be1cb5c802796ae7aaa5d63ddbdca5e86` |
| `check_assessment.py` | `cefb9bc0c3e231885880701b82de35acf50eab43efc6312e45db44f004fbe723` |
| `proposal.json` | `0a5ec22a2990bd6df7ffcc32d4102b497f2974cc58b9e8c6a90f0db8cb659662` |
| `protocol.json` | `a934363b2817217dfde6b9bfdf7f2c8b8c9d953f3335803e9f1882a5648f17bd` |
| `unit-suites.json` | `d70197f6f11dd4919b16983af9f9a3f470caad856bd8c0c8346edcea32a581e2` |
| `routes-suites.json` | `ec06fedcbc76e823839b4590d2f2061e52ff817467a79f1941cca5f09227b3c8` |
| accepted15 `accepted15-baselines.json` | `12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816` |
| original accepted15 `final-native.txt` | `db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d` |

Canary receipt SHA-256: `32b13a1f0e1fac1fc39686d9634da40ab3ce7ac01e7b4e14783e054ed1152b60`.

Base commit: `a3a0b851c1e5b80212b1d604a3962a8e77437893` (root-verified origin). Native runtime and source-call evidence: **NOT RUN**. No implementation source or actual phase output was read. Player replay, solver, GWT, all-seed, and manufacturing behavior remain outside this reader packet.
