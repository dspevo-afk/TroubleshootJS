# Initial outlet metric wire R1

Status: implementation contract for the private native diagnostic. It makes no
routing-treatment or production-behavior claim. The governing task scope is
`PLAN_R1.md` (SHA-256 `D78724B8B26F93B0DC935AA863A50F8FD1EFA20B3FAEB3401DCD7BEA27EAD0D2`).

## Invocation and files

The Java entry point is `com.lushprojects.circuitjs1.client.InitialOutletStudy`.
The standalone canary invocation is exactly `--canary`. Authored mode is exactly
five arguments:

```
OBSERVE UUID FINAL_RUN_MANIFEST_SHA256 WIRE_CONTRACT_SHA256 EMPTY_ABSOLUTE_DATA_DIR
```

Reject malformed arguments, a non-lowercase UUID or digest, an output directory
that is not an existing empty absolute real directory, and a symlink/reparse
directory before constructing the actual generator study or writing any file.
Authored mode writes exactly `op-1.jsonl` (from a fresh actual operation 1) and
`result.json` in the supplied directory. It prints exactly one compact JSON
marker line after both files close and verify. Canary mode prints one compact
JSON canary line and creates no files. Invalid input exits nonzero without a
completion marker. Preflight rejection writes exactly
`REJECTED_INITIAL_OUTLET_INPUT` to stderr and exits 1. No stack trace is an
expected-negative protocol.

All JSON is UTF-8 without BOM, compact, one line per record, with a final LF on
each file. SHA-256 is lowercase hexadecimal over exact bytes. Result, JSONL,
line, native clock, group, unit, copy, and cleanup bounds remain those frozen in
the plan and the qualified P07/P6 inputs.

## Authored result

`result.json` is one object with these required top-level members:

| Member | Type | Meaning |
|---|---|---|
| `schema` | string | `tsj-initial-outlet-v1` |
| `mode` | string | `OBSERVE` |
| `runId` | string | Fresh invocation UUID passed to actual operation 1 |
| `finalRunManifestSha256` | string | Exact input manifest digest passed to actual operation 1 |
| `wireContractSha256` | string | Exact digest of this wire file passed to actual operation 1 |
| `operation1` | object | Actual old-study ArmResult and receipt metadata, as specified below |
| `reference` | object | Authenticated selected branch and prefix facts |
| `source` | object | Source fingerprints and the sorted, exact placed-pad table |
| `measurement` | object | All-net pair identities, query masks, counts, and bounded distribution |
| `work` | object | Pre-export measured work, units, group count, and maxima |
| `cleanup` | object | Actual original and query-owner cleanup evidence |

`operation1` contains `operation=1`, `mode=AUTHORED`, `runId`,
`sourceManifestSha256`, `wireSha256`, `receiptPath="op-1.jsonl"`,
`receiptBytes`, `receiptSha256`, `elapsedToCloseNanos`, `maxFullGroupNanos`,
`cleanupNanos`, `ownersReleased`, `contextRestored`, `groupCount`,
`p07OwnerSteps`, `groupP07StepsSum`, `diagnosticUnits`, `callbackUnits`,
`maximumUnitsPerGroup`, `requestIndex`, `selectedAttempt`, `receiptAttempt`,
and signed-long decimal strings `rootSeed`, `placementSeed`, `routingSeed`.
These values are read from the genuine existing native study and its exact
fresh operation-1 JSONL; `groupP07StepsSum`, `diagnosticUnits`, and
`maximumUnitsPerGroup` are derived from its actual unique GROUP rows because
the older ArmResult omits those emitted fields. The sum of GROUP `p07Steps`
must equal the actual ArmResult `p07OwnerSteps`. The required
identity is root `9007199254740993`, placement `-6587958367974952701`, routing
`8445301674744508279`, request `0`, selected and receipt attempt `4`.

`reference` contains `receiptOrdinal=307`, the actual `groupOrdinal`,
`lineageOrdinal=77`, `pass=2`, `netId="A_COIL_LOW"`, `startPadId="QA.C"`,
`rootPadId="DA.A"`, `terminalReason="NO_PATH"`,
`prefixGeometrySha256="6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb"`,
actual `prefixGeometryBytes`, `prefixHoleCount=48`, `prefixViaCount=48`,
`reachedPadIds=["DA.A"]`, `reachedPthPadIds=[]`, and their actual counts.
Receipt ordinal is global zero-based; lineage ordinal is one-based within the
selected pass. These are distinct fields and are not interchangeable.

`source` contains `componentCount=100`, `netCount=60`,
`templateGeometryBeforeSha256`,
`templateGeometryAfterSha256`, `templateEffectsBeforeSha256`,
`templateEffectsAfterSha256`, `detachedGeometrySha256`,
`detachedGeometryAfterSha256`, `templateTraceCount=0`, `templateHoleCount=0`,
`queryRouterEffectsBeforeSha256`, `queryRouterEffectsAfterSha256`,
`sourceUnchanged=true`, and `padLands`. `padLands` is the actual source layout's
246 rows, sorted by `padId`; each row has `padId`, `netId`, integer `x`, integer
`y`, integer `bounds=[x,y,width,height]`, `attachment="PLATED_THROUGH_HOLE"`,
and `layer="TOP+BOTTOM"`. The adapter verifies the table before and after
detaching the actual layout. This private table supports independent query
reconstruction; later public summaries must sanitize pad/component/net IDs.

`measurement` contains:

* `policy="FULLER_TWO_LAYER"`, `pass=2`, `widthProfile="ROLE_WIDTHS"`,
  `grid=10`, `primaryFace` (actual placement routing layer), and
  `oppositeFace` (the other actual copper layer).
* `faceOrder=["TOP","BOTTOM"]`, `directionOrder=["N","E","S","W"]`,
  with direction bits `N=1`, `E=2`, `S=4`, `W=8`, also recorded in
  `directionBits`.
* `queriesPerPair=16`, `queryCount` (actual calls), `singletonNetCount`,
  `pairedNetCount`, `rows`, and `distribution`.
* `sourceGeometryBeforeSha256` and `sourceGeometryAfterSha256` are hashes of
  the detached query source before and after all queries and equal the
  corresponding source fingerprints. `coldReplayMatches` is `null` in authored
  mode because authored measurement does not replay; the separate canary
  performs and reports its replay comparison.

There is exactly one `rows` entry per actual board net in strictly ascending
`netId` order. Each row has the exact lexically sorted `padIds`, actual board
`role`, and integer `traceWidth` from
`PcbTraceWidthProfile.ROLE_WIDTHS.widthFor(role)` (HIGH_CURRENT 13,
SUPPLY/RETURN 11, CONTROL/SIGNAL 9).

* For a singleton: `paired=false`, `rootPadId=null`, `startPadId=null`,
  `forward=null`, `reverse=null`, `value=null`, and zero probes.
* For a multi-pad net: `paired=true`; `rootPadId` is the lexical first pad;
  `startPadId` is returned by the unchanged pass-2 ROLE_WIDTHS
  `chooseBranchPad` using the exact remaining pad list, root-only reached list,
  and `CANONICAL_NEAREST`. `forward` measures start toward root and `reverse`
  measures root toward start. Each has `topMask`, `bottomMask`, `topCount`,
  `bottomCount`, and `totalCount`; counts are mask popcounts, and
  `totalCount=topCount+bottomCount` in `[0,8]`. Each orientation performs four
  distinct-endpoint cardinal one-grid queries on each face. `value` is the
  minimum of forward and reverse `totalCount`, in `[0,8]`.

Every query is one direct call to the unchanged face router's
`permitsLayerStep(ax,ay,bx,by,net,start,end)`, on a dedicated fresh normal P07
Search constructed for queries only. The adapter does not advance that Search,
construct a branch, invoke a publisher, change the original Search, or modify
source geometry. Empty first-branch reached-PTH targets are equivalent to the
actual null/empty reached-PTH list. All masks are recorded from actual returns.

`distribution` contains `pairedValueHistogram` (integer-string keys 0 through 8
and nonnegative counts), `distinctValueCount`, and `varies`. It is descriptive
only; it does not rank nets or choose a route strategy. The histogram sum must
equal `pairedNetCount`.

`work` is cut off immediately before final result serialization. It contains
`cutoff="PRE_RESULT_SERIALIZATION"`, `elapsedNanos`, `units`, `groups`,
`maxGroupNanos`, `maximumUnitsPerGroup`, `originalP07OwnerSteps`,
`originalDiagnosticUnits`, `originalCallbackUnits`, `metricUnits`,
`preExportUnits`, `metricGroups`, `preExportGroups`, and `queryCount`. `units` is exactly `operation1.p07OwnerSteps + operation1.diagnosticUnits +
operation1.callbackUnits + metricUnits + preExportUnits`; the actual GROUP-row
`groupP07StepsSum` must equal `operation1.p07OwnerSteps`. `groups` is exactly
`operation1.groupCount + metricGroups + preExportGroups`. The separate export
group is excluded from `work.groups` and reported by the marker as its one export
group. `metricGroups` is every metered native group other than the one
pre-export group. It includes capture-bridge setup, post-operation
identity/receipt revalidation, the bounded audit of fresh operation-1 GROUP
rows, both query-only template-copy checks and copies, fresh query-Search/router
setup, one measured group per actual source net, final source/effects
fingerprint verification, and authored board identity checks. Initial detached
source fingerprinting and `getNetIds()` enumeration/sort occur inside query
setup; final detached source/effects fingerprinting occurs inside its final
verification group.
Each whole `PcbBoardLayout.copyForRouting()` is prebounded and metered by
actual pads, components, silkscreen labels, regions, and holes; the adapter
requires each of the two query-only copies to fit one 4096-entry chunk.
`preExportGroups=1` covers result-object construction, pad-row detachment, and
the bounded result-tree walk. `preExportUnits` counts one unit per JSON object member, array element, and
scalar value visited by the tree walk; empty containers and the root container
carry no separate unit. `elapsedNanos` is process-start to the pre-serialization cutoff minus the
three separately measured cleanup durations. `maxGroupNanos` and
`maximumUnitsPerGroup` are observed maxima over the actual original GROUP rows
and all metered native pre-export groups; the separate export group is reported
in the marker. The
original actual GROUP rows and the adapter's metered groups are independently
reconcilable. Final serialization, file write/flush/readback/hash, marker
preflight, and their elapsed time/units form one measured export group reported
in the stdout marker; the marker does not claim post-cutoff time as `work`.
The export unit ledger is `ceil(resultBytes/4096)` serialized-byte units plus
one file-write/flush unit, one readback/hash unit, and one marker-preflight
unit. Its timer begins before final output-directory validation and includes
result serialization, write/flush, readback/hash, file-set validation, and
marker-object construction. The marker's elapsed value is sampled immediately
before its own line serialization; the native source guards the complete
marker-line serialization and print boundary against the same five-second and
90-second limits. `nativeElapsedNanos` is sampled at that same pre-marker-line
cutoff; the parent process receipt remains the whole-process elapsed oracle.
Total native wall time includes both work and export and is bounded by 90s.

`cleanup` contains actual `originalOwnersReleased`, `contextRestored`,
`querySearchReleased`, `querySessionReleased`, `queryLayoutReleased`,
`queryRoutersReleased`, `sourceTemplateReleased`, `sourceBoardReleased`,
`allReferencesReleased`, `originalCleanupNanos`, `queryCleanupNanos`,
`sourceReleaseNanos`, and `combinedCleanupNanos`. The original, query, and
source-release durations are measured separately; the combined value is their
actual sum and must remain within 120s. Flags are read
after cleanup and are not set to true before references are dropped.

The completion marker object is exactly schema
`tsj-initial-outlet-marker-v1`, status `COMPLETED_DIAGNOSTIC`,
`resultSha256`, `resultBytes`, `exportElapsedNanos`, `exportUnits`,
`exportGroupCount=1`, `exportMaxGroupNanos`, `exportMaximumUnitsPerGroup`, and
`nativeElapsedNanos`. The result digest and size refer to the closed exact
`result.json`; stdout contains one line and no log text.

## Canary result

`--canary` makes no data files and prints exactly one JSON object with schema
`tsj-initial-outlet-canary-v1`, status `PASS`, `open`, `blocked`,
`coldReplay`, `injectedFailure`, and `work`.

`open` is an actual `CopperFreeBranchFixtures.open()` query with the exact
QA.C-to-DA.A pair. It proves bottom-face forward South and reverse West are
allowed; all other bits are reported as observations. `blocked` is an actual
`CopperFreeBranchFixtures.blocked()` query; its forward top and bottom masks
must both be zero. `coldReplay` repeats the full query set on the same
disposable query-only routers and requires byte-for-byte equal masks and
unchanged source geometry; router counters/cache state may change. Both runs
report `netId`, sorted `padIds`, `role`, `traceWidth`, root/start identities,
forward/reverse masks and counts, actual query calls, primary/opposite faces,
source-geometry equality, and cleanup. The replay record has `matches`,
`callsPerScan`, `totalCalls`, `sourceGeometryUnchanged`, and cleanup.

`injectedFailure` throws one known `IllegalStateException` immediately after
the first real query while both face routers are installed. It reports exact
exception class/message, one completed query, installed-router evidence, and
the actual post-finally release booleans. The caught Throwable must be the same
object as the injected failure and have no suppressed exception. Its cleanup
has elapsed nanos and actual search/session/layout/router/all-reference release
booleans. Canary `work` contains `cutoff="PRE_MARKER_SERIALIZATION"`, elapsed
work, units, groups, max group time, maximum units per group, and cleanup nanos.
It includes fixture setup, both query scans, injected-failure cleanup, and
result-object preparation; the final stdout line is separately guarded by the
native source and whole-process elapsed oracle. Canary fixtures do not stand in
for authored operation-1 source or a production route.

