# Q100 quality-scalar wire contract, revision 8

Frozen before producer implementation. Scope: test-only actual-generator diagnostic for one exact `SEPARATE_DIRECT_SWITCHING` variant. Schema protocol version is 1; source revision is 8. Every line below is newline-delimited JSON after its exact marker. No production epoch or source revision changes.

## Required line order

1. `RB100_QUALITY_SCALAR_CONTROLS <json>` with exact top-level keys `protocolVersion,sourceRevision,runId,status,fixture,checks,assertions,layoutQualification`. `checks` has exactly five rows in order: `direct_zero_rejected`, `length_over_3x_rejected`, `bends_over_16_rejected`, `exact_3x_16_accepted`, `via_direction_reset`. Each row has keys `name,status,planarLength,directDistance,bends,pathNodeCount,transitionCount,publisherStage`. The exact fixture value is `SYNTHETIC_PUBLISHER_METRICS_ONLY`.
2. Existing `RB100_CAPACITY_BOUND_PHYSICAL_SUBSETS <json>` receipt for NULL, then `RB100_QUALITY_SCALAR_OPERATION <json>` with exact wrapper keys `protocolVersion,sourceRevision,runId,operationId,variant,mode,result`. `result` is the existing `PlacementDiagnosticCase.toJson()` object without reserialization or field rewriting.
3. Existing physical-subset receipt for PROBE, then the corresponding `RB100_QUALITY_SCALAR_OPERATION` wrapper.
4. Exactly one `RB100_QUALITY_SCALAR_EVENT <json>`, emitted only after the actual target branch rejection is confirmed on the same retained P07 session and the generator operation ends EXHAUSTED with no layout.
5. Exactly one `RB100_QUALITY_SCALAR_PAIR <json>` with exact keys `protocolVersion,sourceRevision,runId,variant,nullOperationId,probeOperationId,rootSeed,placementSeed,routingSeed,layoutVersion,dependencyEpoch,planVersion,modelVersion,physicalIntentVersion,status,routeEligiblePlacementAttemptsEqual,firstP07RequestAttemptEqual,routeOutcomesEqual,primaryRoutingOrdersEqual,primaryRoutingExpansionsEqual,policyCanonicalEqual,boardIdentityEqual,solverIdentityEqual,terminalStatusEqual,layoutOutputEqual,layoutAbsent,routeMillisStatus,layoutQualification`.
6. Exact final marker: `PASS: RB100 quality scalar contracts <json>` with exact summary keys `protocolVersion,sourceRevision,status,operations,nullOperations,probeOperations,pairs,confirmedScalarEvents,variant,runId,rootSeed,placementSeed,routingSeed,layoutVersion,dependencyEpoch,planVersion,modelVersion,physicalIntentVersion,assertions,suiteMs,suiteLimitMs,manualDriverMillis,manualSliceSteps,routeMillisStatus,feasibility,layoutQualification,scope`.

The operations are `QUALITY_NULL`/`NULL` then `QUALITY_PROBE`/`PROBE`, both for `SEPARATE_DIRECT_SWITCHING`. The single event binds the actual context: root seed string `9007199254740993`, placement seed string `-6587958367974952701`, routing seed string `8445301674744508279`, layout 26, dependency epoch `tsj-generation-dependencies-v28`, plan 1, model 4, physical intent 9, route request 0, eligible index 0, placement attempt 4, ordering pass 1, net `D_SENSE`.

## Event keys

All keys are required. Decimal seeds remain JSON strings to preserve exact signed-long transport. The event has exact keys `protocolVersion,sourceRevision,runId,operationId,variant,mode,rootSeed,placementSeed,routingSeed,layoutVersion,dependencyEpoch,planVersion,modelVersion,physicalIntentVersion,routeRequestIndex,selectedPlacementIndex,selectedPlacementAttempt,eligiblePlacementAttempts,placementFingerprintLength,placementFingerprintSha256,pass,netId,startPadId,rootPadId,endPadId,startX,startY,endX,endY,endLayer,primaryLayer,firstLayer,gridWidth,gridHeight,gridOriginX,gridOriginY,gridPitch,pathNodeCount,transitionCount,planarLength,directDistance,bends,rejectDirectZero,rejectLengthOverThreeDirect,rejectBendsOver16,qualityRejected,qualityRejectionOwner,qualityRejectionMessage,generatorLastFailureSource,generatorLastFailureType,generatorLastFailureMessage,routeMillisStatus,manualDriverMillis,manualSliceSteps,acceptanceFlags,feasibility,layoutQualification,graphCallbacks`.

`endPadId` is always JSON `null`: the actual found goal is a routing-grid node and is not resolved as a pad by this diagnostic. `endX/endY` are the actual goal node mapped through `Search.x/y`; `rootPadId` is only the original tree root and is never used as an alias for the goal. The event has no end-pad identity resolver.

`planarLength` and `bends` are read from the actual Publisher. `pathNodeCount` is the actual Publisher path vector size, `transitionCount` is read from the actual end Node and conserved against the actual path layer changes, `firstLayer` is read from the first actual path Node, and the event coordinates come from actual start placement and the actual Search grid inverse. `directDistance` is the exact Manhattan predicate input from those actual coordinates. `qualityRejectionOwner` identifies the same P07 session's existing `reason` field; `qualityRejectionMessage` must identify the same target net and `BRANCH_QUALITY_LIMIT`. The generator's typed `lastFailure` source (`SeededPcbLayoutGenerator.Session#lastFailure`), class, and final message are recorded independently; they describe the generator's final rejection and do not claim that it is the target branch cause. `acceptanceFlags` has exact keys `accepted,routed,playable`, all `false`; `feasibility` is `UNKNOWN`; `layoutQualification` is `false`; `graphCallbacks` is `0`.

The five control rows call the real Publisher metric/readiness method on local synthetic node chains. They read the produced metric fields from Publisher state; fixture inputs are source-level test controls, not board or electrical evidence. `directDistance` for each control is sourced from the actual Search grid inverse and its start/end cells. Control and event records contain no path vector, graph, layout geometry, measurements, or fault details.

The pair booleans above must all be `true`, `status` is `PASS`, `layoutAbsent` and `layoutQualification` are `true` and `false` respectively. The summary has `operations:2`, `nullOperations:1`, `probeOperations:1`, `pairs:1`, `confirmedScalarEvents:1`, source revision 8, and `routeMillisStatus:INCOMPLETE_MANUAL_SINGLE_STEP_CADENCE_NOT_REPRESENTATIVE`; `feasibility:UNKNOWN`, `layoutQualification:false`, and `scope:OBSERVATIONAL_ACTUAL_GENERATOR_QUALITY_SCALAR_ONLY`.

The reader must reject duplicate, missing, reordered, unexpected, stale-source, wrong-context, nonconserved, predicate-mismatched, non-false acceptance, or layout-bearing records.

## Frozen literal Publisher controls

The independent reader pins these source-level literal grid-chain controls (GRID=10); the producer compares each actual Publisher result to this table before accepting the control receipt:

| Check | Literal chain | Planar length | Direct | Bends | Nodes | Transitions | Stage | Expected |
|---|---|---:|---:|---:|---:|---:|---:|---|
| `direct_zero_rejected` | singleton at anchor | 0 | 0 | 0 | 1 | 0 | 1 | quality reject |
| `length_over_3x_rejected` | N,N,N,E,E,S,S,S | 80 | 20 | 2 | 9 | 0 | 1 | quality reject |
| `bends_over_16_rejected` | (E,N) repeated 9 times | 180 | 180 | 17 | 19 | 0 | 1 | quality reject |
| `exact_3x_16_accepted` | E6,N1,W6,N1,E1,N1,W1,N1,E1,N1,W1,N1,E1,N1,W1,N1,E1 | 270 | 90 | 16 | 28 | 0 | 2 | accepted through quality guard only |
| `via_direction_reset` | E1,V1,N1,E1 | 30 | 30 | 1 | 5 | 1 | 2 | accepted through quality guard only |

A control path starts from a real placed pad with ten grid cells of margin on the active Search grid and stays within that grid. The synthetic end is a grid node, not a board-pad claim. No control advances into publication stages after stage 2.
