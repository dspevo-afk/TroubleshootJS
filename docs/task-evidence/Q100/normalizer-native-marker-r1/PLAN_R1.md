# Pinned native marker reconciliation

Second actual fixed20 qualifier after new normal-only journal0acf08e1 failed
NORMALIZER_NATIVE_MARKER_KEYS, normalization exit2; both failed output roots,
configs/manifests/journals and receipts preserved. No context/Java DATA,
nativeproducer/session0; genuine original+exactly NULL/OBSERVE UNSPENT/GATED.
Console correction35e36eee is published/accepted; do not undo it.

Root authenticated only already-authorized N_PRIOR_CALL_STDOUT_TXT328bytes,
SHA42dacdb5c346b4595a2d4d07436723f4be8206ff778d31a233906318d484d0a5.
It is one CRLF JSON object. Exactkeys: schema,status,resultSha256,resultBytes,
elapsedNanos,finalExportNanos,totalGroups,totalUnits,maxGroupNanos,
maximumUnitsPerGroup. The current normalizer incorrectly expects only first4.
No marker fields may be ignored. No support for obsolete4-key marker.

Independent original SOURCE authority (read SOURCE only, never import/execute old
module/CLI): N/read_failure_cohort.py SHA84c8dbf2805ab5607f1e79f1d1fa67cc1a2e4ec06d3a22fdd44771ce48d03b52,
function _validate_marker lines833..857. It requires exact10keys; schema/status,
result SHA/len; elapsed1..90s/export1..5s; exact groupsBeforeExport+1;
finalExportUnits=5*ceil(resultBytes/4096)+3; exact unitsBeforeExport+final;
maxGroupNanos=max(work max before, finalExportNanos);
maximumUnitsPerGroup=max(work maximum before,finalExportUnits);
finalunits<=32768; elapsed>=work cutoff+finalExportNanos.
Retain all these checks and original640/32768/90s/5s/copy limits.

SOURCE-only worker owns ONLY AB/normalizer-native-marker-r1/normalize_prior.py
and NEW test_normalizer_native_marker.py. Preserve all other copied sources/tests.
Add a narrow pure _validate_native_marker(marker,result_raw,doc) helper mirroring
the full original marker contract. Replace the obsolete inline4-key check.
Parse marker with existing console adapter but perform complete marker semantic
validation AFTER _validate_prior_result has validated work; retain all prior
oracles/ordering/byte authentication/codec/kernel provenance. No broad changes,
no version fallback or invented marker values. Historical API is not compatibility.

Tests construct independent synthetic result bytes, work and literal expected
marker fields; cover positive, 4-key/extra/missing keys, changedhash/size/schema/
status, bool/float numeric masquerades, exact final export arithmetic and chunk
boundaries, time/group/unit caps and coupling. Keep all69 previous tests unchanged.
Root will compare against SOURCE-extracted pure reference and run final gates.

Leaves MUST NOT execute/tests/spawn/stage/commit/push/mail or read DATA/journal,
old D descriptor/result/generatedinput, heldout/protected bodies. Root owns all
runtime and actual authorized DATA diagnosis, proof/pin migration, new authority
and later deliberate qualifier. Never reuse a failed root or retry automatically.
Full30EXCLUDED/HARD-PSYCHOTIC unavailable-unrun/historical unreadUNCERTIFIED/
fullD-H1unqualified/playable100=0/betaHELD. Caps/seeds/epochs/CircuitJS truth intact.
