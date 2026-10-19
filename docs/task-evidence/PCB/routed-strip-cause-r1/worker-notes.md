# Routed strip cause observer worker handoff

## Scope and state

Implemented the bounded native-only, read-only observer in the isolated accepted
native1467 copy. Main production source remains unchanged; pre-existing report
and packet state is preserved. The only copy code files touched are
`src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java`,
`tests/contracts/PcbPostSelectionEdgeTrimContractTest.java`, and the new
`tests/contracts/PcbRoutedStripCauseContractTest.java` and
`tests/contracts/PcbRoutedStripCauseContractTestGroup2.java`.

The observer is disabled by default and package-private. A scoped install records
up to six existing `compactRouted` calls, twelve axes, and 256 copied cuts per
axis. It makes no router, planner, scorer, validator, raster, generation-work
scope, or callback calls. It records only the cuts, candidate signature, chosen
outcome, and existing caught rejection. Only the already-existing placement
rejection and route-quality rejection remain caught by compaction; another
exception continues to propagate; a begun axis remains `PENDING` and its call
is `INCOMPLETE`. Observer-cap or observer-signature failures set truncation and make
the binding diagnostic inconclusive.

Static diff review found the original occupied-interval construction, cut order
and arithmetic, footprint/copper/hole movement, candidate materialization,
statistics copy, and validation order unchanged. The old private
`routeAndObserve` body in the edge-trim fixture is unchanged; its new guarded
adapter calls it once for explicit version15/density14 and returns its existing
JSON and selected layout. The legacy metric row is emitted with only its prefix
changed from `PCB_EDGE_TRIM_CASE ` to `PCB_ROUTED_STRIP_CASE `.

The common contract checks fixed root42 and root-1022 inputs against sealed
current15/density14 source-plan, live-graph, source-intent, signed-long layout
seed, signed-long routing seed, component-count, and pad-count literals before
the single routed generation. It checks source graph/plan preservation and
observer cleanup in a `finally` path. A separate no-generation lifecycle fixture
covers nested-install rejection, stale-owner clear rejection while a successor
is installed, and finally-based cleanup after a thrown sentinel. Independent
literal signature fixtures cover uniform-translation invariance and changed
component origin, pad position, full trace vertex sequence, via, and non-plated
hole. The standard axial package declares only `DEG_0`, so the fixture does not
exercise a rotation change; the signature includes rotation, but that specific
change remains outside fixture coverage.

## Event schema

Runtime examples are not available yet. These are the compact event shapes
produced by the observer; strings are JSON-escaped and seeds are canonical
signed-long decimal strings.

```json
{"schema":"PCB_ROUTED_STRIP_EVENT@1","kind":"CALL","seed":"42","callOrdinal":1,"status":"COMPLETE","inputSignature":"<sha>","outputSignature":"<sha>","axisCount":2,"truncated":false}
{"schema":"PCB_ROUTED_STRIP_EVENT@1","kind":"AXIS","seed":"42","callOrdinal":1,"axisOrdinal":0,"axis":"X","status":"APPLIED","occupiedIntervalCount":12,"cutCount":1,"cutsTruncated":false,"cuts":[[640,30]],"inputSignature":"<sha>","proposedSignature":"<sha>","outputSignature":"<sha>","caughtType":null,"caughtMessage":null}
{"schema":"PCB_ROUTED_STRIP_BINDING@1","seed":"42","selectedSignature":"<sha>","matchingCallOrdinals":[1],"incompleteCallOrdinals":[],"truncated":false,"status":"MATCHED","caseStatus":"INCONCLUSIVE"}
```

Each call precedes its recorded axes. Call ordinals are contiguous from 1 and
axis ordinals are X=0/Y=1. Axis statuses are `NO_CUT`, `APPLIED`,
`PLACEMENT_REJECTED`, `QUALITY_REJECTED`, or `PENDING`. For no-cut, output
equals input and proposed is null. Applied records proposed as output. Rejected
axes retain input as output and retain the attempted candidate as proposed.
Placement rejection records the fully qualified exception type and exact
`Rejected.reason`; route-quality rejection records the fully qualified type and
`getMessage()`. A propagated original exception leaves the active axis pending
and call incomplete with null call output. Cut tuples are `[start, amount]` in
the original scan order. All complete call outputs matching the final selected
layout signature are retained; no uniqueness is assumed by the observer. The
binding's `caseStatus` deliberately remains `INCONCLUSIVE`: the event producer
binds records to a layout but does not independently adjudicate a unique cause.
The root reader can derive a causal conclusion from complete, uniquely matching
branch records after its schema checks.

The physical signature uses length-prefixed tokens with null represented
explicitly. It normalizes component, pad, trace, and hole positions to the
lexicographically first component ID's origin. It includes component ID, pose,
dimensions, mounting side, and physical geometry realization fingerprint;
sorted pad identity, normalized position, pad/probe bounds, escape metadata,
attachment, side, and exposure; traces in original stored order with source ID,
net, layer, exposure, width, endpoints, and every normalized vertex; and sorted
holes with every stored metadata field and normalized bounds. It excludes board
outline, canvas/tray, and silkscreen. The independent fixture's canonical
expectation is a literal token sequence in the contract test, not generated by
the signature helper.

## Review, hashes, and validation limits

SHA256 for the final isolated-copy code files:

| File | SHA256 |
| --- | --- |
| `src/com/lushprojects/circuitjs1/client/PcbPlacementCompactor.java` | `FCB1E2817B028BC6ED46D371493D3430A465BBCB276F4B85061A1CE30B842DB3` |
| `tests/contracts/PcbPostSelectionEdgeTrimContractTest.java` | `215DFED0C329FD261C93981AB3F2D25B56413885F51AC6CA7AF4F1579B5FD488` |
| `tests/contracts/PcbRoutedStripCauseContractTest.java` | `721E1411F275EEE62F4E12E629C5D6A43F4C5E0EA813C9A07B783E4D63FBB744` |
| `tests/contracts/PcbRoutedStripCauseContractTestGroup2.java` | `DE04AFC00A688EB07003DBCADE5346AE6A51E1253EA3C24AD999EE38FE6248B4` |

The main checkout's pre-existing compactor and edge-trim fixture hashes remain
`A7F7DFCA5C85C17380BE868EDA6175DEE551172D7695BF6A2CF18012E844F732` and
`2DE7932B48D479A8DF1FFBB39B929EE88E438214224F8A16CCC8D5180B957F36`, matching
their hashes at worker start. The assigned native suites, build, and runtime
were **NOT RUN** by this worker; root owns freeze, final readers, and acceptance.
There are no runtime event values or causal findings in this handoff. No
production policy, generation, registry, staging, commit, push, or notification
work was performed.

Expected suite markers are `PCB routed strip cause contracts ` and
`PCB routed strip cause Group2 `, with event prefixes `PCB_ROUTED_STRIP_CASE `,
`PCB_ROUTED_STRIP_EVENT `, and `PCB_ROUTED_STRIP_BINDING `.
