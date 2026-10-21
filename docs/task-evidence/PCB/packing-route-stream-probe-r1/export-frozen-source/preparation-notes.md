# Fixed optional P05 routing stream preparation

Preparation completed with Python exit 0 after the frozen baseline audit.

Base: `be7098819b3bdba15012a7f8d6869645bca0ca58`

Request: `tsj-alpha/6/EASY/RELAY_OUTPUT/-397178238694963708`

Private root: `unique task-owned OS-temp directory (private q60 pointer)`

The exact accepted control private five were audited from the prior relay
packet. The prepared manifest exposes these five variant paths:

- `common/SparseNormalBoardDiagnostic.java` (bounded relay seed assertions)
- `common/verify-two-pad-span.ps1` (copied exact relay adapter)
- `variant/SeededPcbLayoutGenerator.java` (fixed optional P05 stream)
- `variant/PcbPlacementPlanner.java` (copied exact planner)
- `variant/PcbPlacementCompactor.java` (copied exact compactor)

The generator patch adds one package-access helper, changes only the optional
P05 route argument, and reports planned seeds at the four passive stages and
the four outer typed catches. The P07 setup, `Session.routingSeed`, inner
route-plan observer, planners, and compactor are preserved. The exporter adds
six guarded helper-versus-literal assertions before its existing suffix.

Independent source seed: `3078526323410797175`
Independent derived seed: `-7609816130686485171`
Derivation vectors: `derivation-vectors.json`

Pure preparation checks: baseline audit PASS; seed reference PASS; generator
and exporter reverse checks PASS; planner/compactor and exporter observer
suffix checks PASS. Java compilation, native canaries, routing, browser, data
reader, and GO decision are NOT RUN in this leaf.
