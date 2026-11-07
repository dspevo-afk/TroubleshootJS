# Copper-free branch native wire — R1

Status: frozen for this private diagnostic. This wire describes one new native
study. It does not qualify production routing or claim whole-board connectivity.

## Invocation and files

The Java main class is
com.lushprojects.circuitjs1.client.CopperFreeBranchStudy.

- Authored invocation: OBSERVE UUID FINAL_RUN_MANIFEST_SHA256 WIRE_CONTRACT_SHA256 EMPTY_ABSOLUTE_DATA_DIR
- Canary invocation: --canary with no additional arguments.
- Any other argument vector is rejected before native generation.
- The authored data directory must be an existing absolute empty directory without
  a symlink/reparse target.
- On successful authored completion, Java creates exactly op-1.jsonl and
  result.json in that directory. It writes one stdout line and no progress lines.
- op-1.jsonl is freshly emitted by the existing ActualGeneratorBranchOrderStudy
  authored operation-1 ArmRun using the supplied run UUID, final source manifest
  SHA-256 and this wire SHA-256. It is not copied from an earlier run.
- The operation-1 JSONL schema, record ordering, limits and fields remain exactly
  p07-actual-branch-order-study-v6; this diagnostic does not rewrite its records.
- result.json is UTF-8 without BOM, one compact JSON object followed by LF.
  All hashes are lowercase hexadecimal SHA-256. Relative artifact paths use /.
  The JSON object uses the exact keys and types below. Unknown or missing fields
  are rejected by the reader.

The standalone canary runs real open and blocked native fixtures through Search
and Publisher, then injects one AttemptObserver exception and verifies the same
exception escapes after adapter cleanup. It does not run an authored operation,
write files, or claim original-context restoration. On success it prints exactly
one compact JSON line with these keys and types:

| Key | Type | Meaning |
|---|---|---|
| schema | string | tsj-copper-free-branch-canary-v1 |
| status | string | PASS |
| positive | object | actual open-fixture result |
| blocked | object | actual blocked-fixture result |
| injectedFailure | object | injected observer exception and cleanup proof |
| originalContext | string | NOT_APPLICABLE |
| work | object | aggregate bounded canary work and cleanup |

Each positive/blocked object has exactly disposition, nodeCount, traceCount,
holeCount, searchCalls, publisherCalls, endpointsConnected, fixturePreflight,
fixturePreflightMessage, fullGeometryControl, and cleanup.
disposition is FOUND for positive and NO_PATH for blocked; nodeCount, traceCount,
holeCount, searchCalls, and publisherCalls are nonnegative integers;
endpointsConnected is the actual partial copper-graph endpoint result (true for
positive, null for blocked). Both fixtures must record fixturePreflight as
EXPECTED_REJECTION and the exact fixturePreflightMessage
PCB net is electrically disconnected: A_COIL_LOW before routing. Positive
must have a nonempty node chain and trace set with endpointsConnected true;
its fullGeometryControl is PASS only after the unchanged full-layout geometry
validator passes following publication. Blocked has no candidate/node chain,
publishes no traces, and has fullGeometryControl NOT_RUN. Each cleanup object
has exactly elapsedNanos and searchReleased, branchReleased, publisherReleased,
sessionReleased, layoutReleased, templateReleased, boardReleased, and
referencesReleased, all booleans except the nonnegative cleanup duration; values
are read from the adapter after cleanup.

injectedFailure has exactly observed, callbackCalls, searchPresent,
activeBranchPresent, publisherPresent, phase, branchOrdinal, netId, startPadId,
rootPadId, exceptionClass, exceptionMessage, fixturePreflight,
fixturePreflightMessage, fullGeometryControl, and cleanup. Values are true, 1,
true, true, false, BRANCH_SEARCH, 1, A_COIL_LOW, QA.C, DA.A,
IllegalStateException, CANARY_INJECTED_CALLBACK, EXPECTED_REJECTION, the exact
expected disconnect message above, and NOT_RUN. callbackCalls counts observer
calls only when the actual Search and BranchSearch are both present; constructor
checks before Search/BranchSearch assignment are ignored. The failure is thrown
only after those actual owner/branch identities are captured and checked. cleanup
has the same shape and requires all release booleans true. work has exactly elapsedNanos, units, groups, maxGroupNanos,
maximumUnitsPerGroup, and cleanupNanos; values are nonnegative integers and
remain within the same 90-second, 640-group, 5-second, 32768-unit, and 120-second
cleanup bounds. No completion line is written on any failed control.

## Result object

Top-level required keys:

| Key | Type | Meaning |
|---|---|---|
| schema | string | tsj-copper-free-branch-v1 |
| mode | string | OBSERVE |
| runId | string | lowercase UUID passed to the process |
| finalRunManifestSha256 | string | exact manifest digest passed to the process |
| wireContractSha256 | string | exact digest of this wire file passed to the process |
| operation1 | object | authentic fresh operation-1 receipt and request binding |
| reference | object | selected original terminal branch facts |
| work | object | combined measured operation-1 and wrapper work, excluding separately measured cleanup |
| source | object | pristine template geometry/effects and zero-copper clone proof |
| counterfactual | object | same-directed-branch native Search and Publisher result |
| cleanup | object | post-release references and native-context state |

operation1 has exactly these required members:

| Key | Type |
|---|---|
| operation | integer, exactly 1 |
| mode | string, exactly AUTHORED |
| runId | string, equal to top-level runId |
| sourceManifestSha256 | string, equal to top-level finalRunManifestSha256 |
| wireSha256 | string, equal to top-level wireContractSha256 |
| receiptPath | string, exactly op-1.jsonl |
| receiptBytes | integer, exact byte length of that file |
| receiptSha256 | string, exact SHA-256 of that file |
| elapsedToCloseNanos | positive integer, original ArmRun elapsed time through close |
| maxFullGroupNanos | positive integer no greater than 5,000,000,000 |
| groupCount | integer | original operation-1 actual complete group count |
| cleanupNanos | nonnegative integer no greater than 120,000,000,000 |
| p07OwnerSteps | nonnegative integer | actual operation-1 request P07 owner steps |
| callbackUnits | nonnegative integer | work performed by the selected terminal-prefix callback, charged to its actual GROUP |
| ownersReleased | boolean, actual ArmRun result |
| contextRestored | boolean, actual ArmRun result |
| requestIndex | integer, exactly 0 |
| selectedAttempt | integer, exactly 4 |
| rootSeed | decimal string, exactly 9007199254740993 |
| placementSeed | decimal string, exactly -6587958367974952701 |
| routingSeed | decimal string, exactly 8445301674744508279 |

reference has exactly these required members:

| Key | Type | Meaning |
|---|---|---|
| receiptOrdinal | integer, exactly 307 | selected BRANCH row's global JSONL ordinal |
| groupOrdinal | integer | operation-1 group owning that BRANCH row |
| lineageOrdinal | integer, exactly 77 | actual Search.BranchSearch branch ordinal |
| pass | integer, exactly 2 |
| netId | string, exactly A_COIL_LOW |
| startPadId | string, exactly QA.C |
| rootPadId | string, exactly DA.A |
| terminalReason | string, exactly NO_PATH |
| prefixGeometrySha256 | string, exactly 6b49cfb2d42a83007824b15c8c7a7c89cdadf627fed9b6d9d966692af0323ddb |
| prefixGeometryBytes | positive integer | exact UTF-8 preimage length |
| prefixHoleCount | integer, exactly 48 |
| prefixViaCount | integer | count from the actual branch snapshot |
| reachedPadIds | array of strings | complete actual Search reached-pad vector, sorted only for serialization |
| reachedPthPadIds | array of strings | actual PTH reached set from the selected branch lineage; expected empty |
| reachedPadCount | integer | exact source vector count |
| reachedPthPadCount | integer | exact source lineage count |

The reader independently selects the matching BRANCH row from the fresh
op-1.jsonl, validates its header/request/receipt and compares all fields above.
The diagnostic itself must verify the target callback's selected identity and
reached-pad vector before copying the pristine owner template. It must fail if
there is no unique matching branch or if the complete reached set is not exactly
the allowed root-only set.

source has exactly these required members:

| Key | Type | Meaning |
|---|---|---|
| templateGeometryBeforeSha256 | string | hash of pristine owner template fingerprint before copying |
| templateGeometryAfterSha256 | string | hash of the same fingerprint after copying |
| templateEffectsBeforeSha256 | string | hash of both original Search router fixed-graph effect values before copying |
| templateEffectsAfterSha256 | string | same values after copying |
| detachedGeometrySha256 | string | hash of retained copper-free template fingerprint before replay |
| detachedGeometryAfterSha256 | string | hash of that same retained template after replay |
| padLands | array of pad objects | all actual placed pad identities and physical geometry, sorted by padId |
| templateTraceCount | integer, exactly 0 |
| templateHoleCount | integer, exactly 0 |
| sourceUnchanged | boolean, true only after equality checks |

There are exactly 246 padLands items. Each item has exactly padId, netId, x, y, bounds, attachment, and layer; bounds is [x,y,width,height], attachment is exactly PLATED_THROUGH_HOLE, and layer is exactly TOP+BOTTOM. The reader binds these rows to fresh operation-1 PAD rows and verifies the detached-template export is identical; these operands are private.

counterfactual has exactly these required members:

| Key | Type | Meaning |
|---|---|---|
| disposition | string | one of FOUND, NO_PATH, QUALITY_REJECTED, CAP_REJECTED |
| reason | string or null | exact native terminal/rejection reason |
| policy | string, exactly FULLER_TWO_LAYER |
| pass | integer, exactly 2 |
| netId | string, exactly A_COIL_LOW |
| startPadId | string, exactly QA.C |
| rootPadId | string, exactly DA.A |
| initialTraceCount | integer, exactly 0 |
| initialHoleCount | integer, exactly 0 |
| maximumVias | integer, exactly 48 |
| maximumTransitions | integer, exactly 4 |
| maximumRequestExpansions | integer, exactly 1000000 |
| maximumBranchExpansions | integer, exactly 100000 |
| work | object | measured bounded operation totals |
| nodeChain | array of node objects, or null only when Search found no candidate |
| publishedTraces | array of actual trace objects, empty only if Publisher published none |
| publishedHoles | array of actual hole objects, empty only if Publisher published none |
| validators | object | distinct outcomes for original partial-layout validators |

When Search found a candidate, nodeChain preserves its actual predecessor chain even if Publisher later rejects it; null means Search found no candidate. Published trace/hole arrays preserve the actual partial layout state at disposition, including zero entries when rejection preceded publication. Each nodeChain node has exactly x, y, layer, direction, transitions, and cost,
all integers except layer (string TOP or BOTTOM). Each publishedTraces item has
exactly sourceId, netId, startPadId, endPadId, layer, width, x, and y; endpoint
fields may be null as in the actual Publisher output and coordinate arrays are
integer arrays. Each publishedHoles item has exactly id, netId, kind, x, y,
drillRadius, and landRadius; netId may be null only for NPTH.

counterfactual.work has exactly elapsedNanos, units, searchCalls, publisherCalls, expansions, groups, and maxGroupNanos, all nonnegative integers. These are phase-only counters through counterfactual metadata; top-level work aggregates operation-1 and wrapper phases exactly once.

Top-level work has exactly elapsedNanos, units, groups, maxGroupNanos, maximumUnitsPerGroup, resultMetadataUnits, and resultMetadataGroups. All are nonnegative integers. elapsedNanos measures combined work through completion of native metadata and before final result serialization; it is at most 90,000,000,000. The exact unit formula is operation1.p07OwnerSteps + operation1.callbackUnits + counterfactual.work.units + resultMetadataUnits. resultMetadataUnits is exactly 2 and resultMetadataGroups is exactly 1 for the final result-object metadata group. groups is exactly operation1.groupCount + counterfactual.work.groups + resultMetadataGroups and is at most 640. maxGroupNanos is the maximum complete actual or wrapper group duration before final serialization and is at most 5,000,000,000. maximumUnitsPerGroup is the observed maximum across each actual operation-1 GROUP (p07Steps when non-null plus diagnosticUnits, plus callbackUnits on reference.groupOrdinal) and each wrapper group; it is positive and at most 32,768. The callback is included in its actual operation-1 GROUP duration and is not added as a separate group. Final result export is separately guarded as a complete 5-second segment; full process elapsed through final byte/hash reread and marker preflight is also guarded within 90 seconds. Cleanup duration is reported separately and cannot offset work.
validators has exactly routingGeometry, twoLayerRules, routeQuality, copperGraph,
endpointsConnected, and allNetContinuity. Validator values are PASS, FAIL,
NOT_RUN, or NOT_APPLICABLE; endpointsConnected is a boolean or null. The copper
graph is built by the original PcbConductorBuilder.capture(board, partialLayout)
and only the selected start and root endpoint connectivity is claimed. For a
completed FOUND result, routingGeometry is NOT_APPLICABLE because the unchanged
full-layout validator also requires every net to be connected; twoLayerRules,
routeQuality, and copperGraph must each PASS and endpointsConnected must be true.
Any failure in those applicable checks is a hard failure with the original cause
and no result or completion marker. For every non-FOUND disposition, all four
route validators are NOT_RUN and endpointsConnected is null, even when a Search
candidate/nodechain or partial publication exists. allNetContinuity is always
NOT_RUN; no whole-board continuity claim is made. The standalone open canary
also runs the unchanged full-layout geometry validator only after its target net
is fully published; the blocked fixture preflight records the exact expected
global net-disconnect rejection and never treats it as a pass.

cleanup has exactly elapsedNanos, searchReleased, branchReleased, publisherReleased, sessionReleased, layoutReleased, templateReleased, boardReleased, referencesReleased, and contextRestored. elapsedNanos is a nonnegative integer measuring new counterfactual owner/data cleanup, separately from operation1.cleanupNanos; the host adds its final process cleanup to enforce the cumulative 120,000,000,000 ns ceiling. All remaining fields are booleans read from actual references/state after cleanup; referencesReleased is true only when every listed owner/data reference is null. Original operation-1 contextRestored must also be true.
## Stdout completion marker

After op-1.jsonl and result.json have both been closed and re-read for their
actual byte lengths/hashes, successful OBSERVE prints exactly one JSON line:

{"schema":"tsj-copper-free-branch-marker-v1","status":"COMPLETED_DIAGNOSTIC","resultSha256":"<lowercase sha256>","resultBytes":<integer>}

No completion marker is written after any failed proof or cleanup. The child
process must exit nonzero on all failures. The reader verifies the exact marker
shape and binds it to the actual result.json bytes.
