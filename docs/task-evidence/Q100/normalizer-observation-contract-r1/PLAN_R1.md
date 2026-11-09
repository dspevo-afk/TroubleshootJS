# Fixed normal observation adapter correction

Third actual fixed20 qualifier on published normA612/caller093d failed
NORMALIZER_JSON_NONFINITE at observation decode after validated prior-result,
independentP07/PAD/work and complete marker. Journalff991c96 predated read;
operation674.0759/wrapper911.2133ms, exit2, no context/Java/newnative. All three
failed roots/configs/manifests/journals/receipts preserved; no automatic rerun.

Root inspected ONLY already-authorized D_PRIOR_OBSERVATION_CROSSCHECK3916bytes,
SHAd2dd656567031515aa520528f2cb8343c9252558b3108bcc4eb840c887e9edcb.
There is no NaN/Infinity. Two finite float fields:
processElapsedMs=112691.18650000001, wrapperTotalElapsedMs=112943.8217,
matching authenticated qualification parent.elapsedMs/result.totalElapsedMs.
_json_load allow_float=False maps ANY float to JSON_NONFINITE. Do not weaken
global parsing or nonfinite rejection. Permit finite timings only at this adapter
and bind both to already-authenticated qualification host facts, with float types.

Root also found actual approvedPriorArtifactRows has EXACT3keys/path,bytes,sha256
in each of9 rows, in the current _artifact_rows order. Existing normalizer wrongly
requires4keys includingrole. Keep _artifact_rows and canonical context4-field API
UNCHANGED. At observation boundary independently derive expected3-field rows by
projecting known pinned _artifact_rows output; do not add fields to historical
DATA, invent rows, follow paths, ignore unknown fields or bless changed goldens.

Actual exact17topkeys: approvedPriorArtifactRows,capturedVectorCount,
isolatedCleanupDurationMs,newAuthoredSessions,newNativeComparisonStatus,
padLandCount,processElapsedMs,processRootAndLogsAbsentOnRecheck,
projectedCohortNetIds,projectionIsFrozenDescriptor,projectionSource,
qualificationReceiptHashes,qualificationSummary,reuseBoundary,schema,status,
wrapperTotalElapsedMs. Schema tsj-r2-prior-input-qualification-observation-v1;
statusPASS_PRIOR_INPUT_ONLY; newAuthoredSessions int0; projectionIsFrozenDescriptor
false; newNativeComparisonStatus NOT_IMPLEMENTED_NOT_RUN_NOT_ACCEPTED;
processRootAndLogsAbsentOnRecheck true; isolatedCleanupDurationMs null.
projectionSource and reuseBoundary are bounded nonempty strings of descriptive
DATA, never operational instructions. Fixed role SHA authenticates their exact
bytes. Existing qualification summary19 fields, receipt SHA map, cohort ids,
captured60/pads246 and artifact count/order/path/bytes/SHA joins must remain.
Require integer counts and exact summary primitive types (bool cannot equal int).
Only processElapsedMs/wrapperTotalElapsedMs may be floats; reject NaN, Infinity,
float overflow, wrong timing types/values and floats in summaries/counts/rows.

SOURCE-only worker owns ONLY AB/normalizer-observation-r1/normalize_prior.py
and NEW test_normalizer_observation.py. All79 existing tests untouched; another
worker may own a separate new _build_context test. Not alone, preserve all edits.
Add narrow pure _validate_observation_crosscheck(raw, role_bytes, pins,
host_calls, prior_result, qualification_summary) helper containing the existing
observation reconciliation with corrected3-field adapter and typed finite timing
joins/exact schema. Call helper at current observation boundary. Keep n_call_sha_map
in normalize_prior for its later final binding. No unrelated AST/global API change.
Return observation for direct tests. Retain all raw/P07/marker/work/cleanup and
final traversal/context/authority checks. Do not execute a prior module or builder.

Synthetic tests independently construct9role rows/hashes,7qualification hashes,
flat19-key summary, host finite timing facts and60/246 priorcounts/cohort. Cover
full positive,17key closure, nonfinite/overflow/float masquerades, timingjoins,
old4-field/extra/missing/reordered/changed3-field artifact rows, summary types,
counts and denial/cleanup flags. No actual role DATA/journal/private docs read.
No tests/execution/spawn/stage/commit/push/mail; root owns allruntime/proofs/pins.

Root will run all final-source gates and actual isolated adapter canary only
after SOURCE release; fresh output/config/manifest/journal before any later
complete qualifier. Native genuine original+exactly NULL/OBSERVE UNSPENT/GATED.
Old D descriptor/result/generatedinput DATA, oldbuilders/CLIs, manifestfollowing,
protected/heldout bodies denied. Full30EXCLUDED/HARD-PSYCHOTIC unavailable-unrun/
historical protected unreadUNCERTIFIED/fullD-H1unqualified/playable100=0/betaHELD.
Allcaps/seeds/epochs/oracles/CircuitJS truth unchanged; GWT/PlayerN/A.
