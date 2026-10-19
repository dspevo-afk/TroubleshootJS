# Successful route pool offline reader

The reader reports only the existing `FINALIZED_ROUTE_CANDIDATE` observations
for roots5 and2 at versions15 and22. It reads no Java output during preparation.
The current native unit and paired routes phases remain NOT RUN.

`assess.py unit` validates the one-suite log, child receipt, timing record,
terminal cleanup markers, protocol and prior-source pins, then writes a new
`unit-validation.json`. `assess.py routes` requires that exact current unit
validation, checks the two ordered root suites, and writes a new `assessment.json`
only when the selected rows and complete successful pools pass. Both commands
refuse to overwrite an existing output artifact.

The reader imports strict parsing, typed comparison, case-shape, whitespace and
placement-score helpers from the pinned previous `assess.py` only after checking
its SHA-256. It also verifies the frozen previous native inputs, root review and
routes log before selecting the four saved selected rows. Each current selected
row must match its corresponding published15 or published22 row in all42
non-time fields; elapsed time remains separately bounded at60,000ms. This uses
those rows as identity witnesses and does not rerun the previous fitness or
14-root assessment.

Every pool event has the protocol's closed field order and types. The reader
checks its seed/version/label, route index, finalized phase, compact/dense flags,
bounded integer metrics, outline area and major side, immutable fingerprint
bounds, graph snapshot stability, and placement cheap score against the
corresponding frozen placement score. The ordered strategy/attempt pairs must
equal only the `SUCCESS` outcomes in the selected row's pinned canonical route
statistics. A selected pool event must match the final selected case's copper
length and via count. Candidate area and route quality stay descriptive because
the observation is before selected-only border trim.

The source graph fields are interpreted as the graph snapshot at the first
successful callback and after the observer helper. Both must match each other
and the selected row's `liveBoardGraphSha256`; the selected-row witness also
supplies whole-run physical/source identity. `preferCompact=true` and
`preferDensePacking=false` follow the confirmed15/22 generator branches.

The assessment says observation PASS only. It records `fitnessDisposition` as
`NOT_EVALUATED; published22 remains rejected`, makes no policy or acceptance
change, and keeps the protocol's two-root limits. Candidate metrics are copied
to the assessment only from actual pool events after those checks.

The in-memory synthetic reader canaries passed55 cases:4 positive phase,
paired-count and fresh-prerequisite cases, with51 malformed, drifted, stale or inconsistent inputs
rejected. They build selected-case fixtures from the pinned previous packet and
use synthetic pool event payloads only to exercise parsing; those payloads are
not route measurements. No actual native output, Java/GWT build, player run,
solver check, or fitness decision was consumed.

Command run:

```text
python -B check_assessment.py
PASS synthetic offline assessment canaries: 55 cases; receipt not written
```

Pinned inputs and owned reader hashes at final source review:

| Input | SHA-256 |
|---|---|
| `protocol.json` | `536fa0f92ff22f3c0c43505ee6b01a1bdf0c798161ec6051e9b2f858de4691f8` |
| `unit-suites.json` | `06166a405f390342ec130cf14d352885ff4aa180ef357fdbde952550be80489f` |
| `routes-suites.json` | `fe298e4e97a4c947569fba6a0aabc78c5aae9da500b5275d2727b6b3f09dd3fa` |
| `../global-pocket-descent-trial-r1/assess.py` | `0a45548e3091f6bccbf34d6ceab9fe1be1cb5c802796ae7aaa5d63ddbdca5e86` |
| `../global-pocket-descent-trial-r1/routes.txt` | `dcca3fd2f61eae74b770857c56ac87082a03e491fac83a147c46f9f9cfe773d7` |
| `../global-pocket-descent-trial-r1/native-inputs.json` | `f3fd5759e6796e204cbf147ca969fa674cd490e351d6be2bfe1ecf9a463553f4` |
| `../global-pocket-descent-trial-r1/root-review.json` | `c373edd3ae507af14a9a488518987cf3d2f9b840a23e497843f33dff7a862bb9` |
| `assess.py` | `0581d1ea602e82190fd278c9287465cb6399ed57eee899c023e131afe2e14c3a` |
| `check_assessment.py` | `5379204a6ca9a87120ec49b76fa8c336953c263d39ecc4bbda14381fb7f1ab4e` |
| `assessment-canary-results.json` | `cc0b267a7945a03f74fbcf765d312a663ce898c3eff0b7290db31787969e1530` |

