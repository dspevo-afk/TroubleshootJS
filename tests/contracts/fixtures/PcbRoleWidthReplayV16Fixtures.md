# PCB role-width replay v16 fixtures

`PcbRoleWidthReplayV16Fixtures.zip` freezes the two original layout-v16 route
records used by `PcbRoleWidthReplayDiagnosticTest`. The current router must not
regenerate these routes: the test measures a width-only copy of the saved paths
and keeps their old candidate identities visible.

## Provenance and contents

The source snapshot is commit
`5d4de10b56c10fbf1012a2ac39324382074fc6a1`, where layout epoch 16 selected the
two P07 routes. Seed `-1022` selected attempt 0 (151 traces, 48 vias), and seed
`42` selected attempt 1 (144 traces, 48 vias). Their original canonical hashes
remain `1216897c60853a35008c3d2fd62965d6489ad997c2e3a62cb578b081dedd5be9` and
`df2494c31141ac8c6ccb5065454ba70e2190d29d9690a195f518c1c268c5fb01`.

The data was extracted in an isolated archive of that commit with a temporary
test-only serializer called immediately after the historical selector produced
each route. The serializer saved the canonical fingerprint and the geometry
rows used to rebuild it; it was not added to production generation. To recreate
the source snapshot and rerun extraction, start with:

```powershell
git -C <repo> archive --format=tar --output=<scratch>\layout-v16.tar 5d4de10b56c10fbf1012a2ac39324382074fc6a1
tar -xf <scratch>\layout-v16.tar -C <scratch>\layout-v16
$env:TSJ_ROLE_WIDTH_FIXTURE_DUMP = '<scratch>\fixture-dumps'
# In the isolated snapshot, add the temporary serializer and call
# PcbRoleWidthFixtureDumper.dump(seed, layout) after each selected layout.
& '<scratch>\layout-v16\scripts\verify-current-contracts.ps1' `
  -JavaHome '<JDK8>' -Suite @('PcbRoleWidthReplayDiagnosticTest')
```

The inner records are GZIP streams in a ZIP archive. Each payload is big-endian:
magic `0x50525731`, signed seed, layout version and board dimensions, outline and
tray rectangles, the old canonical string, then length-prefixed regions,
components, pads, labels, holes and traces. Strings are UTF-8 with a 32-bit
length; nullable strings have a boolean presence byte. A trace stores source/net
IDs, optional endpoint pad IDs, layer/exposure, width, point count and ordered
integer coordinate pairs. The current parser checks the exact two-entry ZIP,
seed and magic, bounded lengths/counts, no trailing payload bytes, route and pad
identity, Manhattan segments, and reconstruction of the full old canonical
string. A route-coordinate corruption canary must change that canonical string.

Exact archive and member digests:

| Item | Bytes | SHA-256 |
| --- | ---: | --- |
| ZIP archive | 98,578 | `e526fad4b7ae20a22773863562abd85dbb9b48ec94c78914a9b9d0da23ac74d5` |
| `seed-neg1022.gz` member | 51,300 | `8d508d752426fe51d08c68808a4be0d98ec60b5d7bc8803a258db9dcf00b6647` |
| `seed-neg1022.gz` decompressed payload | 350,074 | `18b0a997e52da667ab0c4497c23a6ec0ee8cc4e85d73733c00ac7e0f422c2b93` |
| `seed-42.gz` member | 47,872 | `7cd5873bb5c0e39b92a6710ada1a34db86ff4874e28bbdb423a6ff595b5f0ecf` |
| `seed-42.gz` decompressed payload | 322,340 | `d82440d3445eb358505c17416e968db6af7a89a34c2d4f2bd3e76046b14d267f` |

ZIP metadata can change if the archive is repacked; the member and payload
digests identify the fixture contents.

## Validation and limits

The fixture test was run against the clean `cf8556a941b43b709a0fae95fa0e9231defa41fb`
baseline with JDK 8u502. The focused suites
`PcbRoleWidthReplayDiagnosticTest` and `PcbRoleWidthTurnBoundContractTest` exited
0 in 24.107 seconds. It preserved both historical hashes, completed the
width-only copy, checked route paths and electrical endpoint nets, and passed
the corruption and trailing-data canaries. That baseline materialized the old
records as layout epoch 19; root's integrated current-epoch run is still the
required validation for the epoch-20 change.

The test is diagnostic, not route acceptance. For seed `-1022`, the baseline
run rejects a component-courtyard crossing and unrelated copper overlap
(`B_COIL_LOW / RAIL5`); for seed `42`, it rejects a courtyard crossing and
unrelated copper overlap (`A_COIL_LOW / LOAD_RETURN`). Two-layer rules and trace
clearance pass in those runs. First-step search replay is `NOT_RUN`, and route
acceptance is `NOT_PROVEN`. These diagnostics are retained rather than treated
as successful physical proofs.

From the repository root, rerun the current focused suites with the configured
JDK 8 path:

```powershell
& .\scripts\verify-current-contracts.ps1 -JavaHome '<JDK8>' `
  -Suite @('PcbRoleWidthReplayDiagnosticTest','PcbRoleWidthTurnBoundContractTest')
$runnerExit = $LASTEXITCODE
exit $runnerExit
```

This focused check does not replace the full native contract matrix or prove
that either frozen historical route passes current geometry/connectivity
acceptance.
