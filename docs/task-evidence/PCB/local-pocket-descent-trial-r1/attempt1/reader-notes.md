# Local pocket descent offline reader

`assess.py` validates the frozen COPY-only local pocket packet and its saved phase receipts. The canary runner builds synthetic logs in memory; it does not open `unit.txt`, `routes.txt`, phase receipts, or timing outputs. It reads only the pinned accepted15 baseline and its original source log for contract validation.

Run the checks with the private Python interpreter using `-X utf8 -B`:

```powershell
& $pythonExe -X utf8 -B docs/task-evidence/PCB/local-pocket-descent-trial-r1/check_assessment.py
& $pythonExe -X utf8 -B docs/task-evidence/PCB/local-pocket-descent-trial-r1/assess.py unit
& $pythonExe -X utf8 -B docs/task-evidence/PCB/local-pocket-descent-trial-r1/assess.py routes
```

The unit phase requires the three ordered current suites. Its first marker requires positive, bounded assertion and fixture counts plus `evaluator=localPocketDescent`; the frozen `UNIT_FIXTURE_COUNT` is 10. The vacancy-target suite accepts a positive bounded assertion count. The REL-B identity suite keeps `scope=IDENTITY_AND_ADMISSION_ONLY ownerRoundTrip=false`.

The routes phase requires seven ordered suites, each reporting two fixed roots, versions 15 then 21, and four whitespace/case pairs (`evaluator=planMedium`). It accepts exactly 28 whitespace and 28 `PCB_POCKET_DESCENT_CASE` rows. The fourteen version15 rows must match all 42 pinned non-time fields with exact JSON types. Version21 requires density15 and preserves pads, parts, live graph, source intent, layout seed, and routing seed; its source-plan and geometry digests may differ. The four strict metrics may not increase, and at least one area or protected empty rectangle metric must decrease.

Native phase admission and strict fitness are separate. A native PASS with strict fitness FAIL is retained in `assessment.json`; the routes command exits2 after writing that assessment. Unit validation and assessment files refuse overwrite. The 36,000-second phase limit is only a reader receipt bound; the maintained per-case 60,000 ms limit and native search caps are unchanged.

A fresh in-memory run passed 45 canaries: the inherited 43 strict reader cases and two fitness boundaries. Identical metrics produce native admission PASS and strict fitness FAIL; a protected empty rectangle only improvement produces native admission PASS and strict fitness PASS. The other canaries cover pinned proposal/protocol/manifests/baselines, JSON typing and ordering, signed-long seeds, phase receipts, cleanup markers, timing bounds, unit freshness, and report identity. Rejection canaries catch only `ReaderError`; unexpected failures propagate. The malformed-fixture canary rejects zero, the frozen count plus one (11), and values above the reader maximum. The fresh results receipt is [assessment-canary-results.json](assessment-canary-results.json); it records 45 PASS canaries, the consumed-input hashes below, and `nativeRuntimeEvidence=NOT_RUN`.

Current consumed SHA-256 values:

| Input | SHA-256 |
| --- | --- |
| `assess.py` | `43202e54eb49fb60f10d7e5e783fe4abb26b8c692bad323cd7db04083b8d693c` |
| `check_assessment.py` | `52819b852e058a6c09c348d87715b4e050fe40d25f50ef3f8a0eda1ff2c608f1` |
| `proposal.json` | `70897df24fd634ec90286f5843fdf94a7a93fa1011c108488ecadf304518462e` |
| `protocol.json` | `ea0e15e0929a3d0f4ca724bd96dcbaf33e76c1587a893c118f845f3ac8d252d6` |
| `unit-suites.json` | `75d850fe580f1240d0aa977138c2a539c8b6c99430ffaa1432bfa2b4f9984c47` |
| `routes-suites.json` | `230e64e7027a7bda092c37c398ec4e481e832b2fbce6764af5460a4f2b7dbb8d` |
| `accepted15-baselines.json` | `12cebe33eeda3477d2e4d4a8531ff85c39025db9ea5ff341aacedf3007966816` |
| original accepted15 `final-native.txt` | `db538fe4cca9aff18e33a974a6afa53ecb2fc244c7827c65201cc5408b65330d` |

Canary receipt SHA-256: `1a0ce00f6e8aa323d41607bca0e310225e60f63782a630df3086d4e64c039a21`.

Native runtime evidence: **NOT RUN**. No native unit, route, timing, or phase-output files were read. Player, solver, GWT, all-seed, and manufacturing behavior are outside this reader packet.


