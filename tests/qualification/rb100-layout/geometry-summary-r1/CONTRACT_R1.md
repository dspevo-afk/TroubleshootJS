# Source-geometry summary agreement: finite current contract

Append checks to validateCorrelations after ownership, before encoding. Current
ResultProjections.cleanup defines the summary as five exact string comparisons
reduced by four eager conjunctions. Reuse capture/nullCleanup/observeCleanup/
cleanup locals; read capture.template and use existing correlationMap guard.
Then read six strings in order: template.geometryBeforeSha256/geometryAfterSha256,
nullCleanup.sourceGeometryBeforeSha256/sourceGeometryAfterSha256, and the same
two observeCleanup fields. Finally read cleanup.sourceGeometryUnchanged as a
strict Boolean using the existing parameterized reader.

The current originalDto fragment transports source values and the projection
casts Strings; reviewed current-only Sources do not prove a fixed SHA format
for all six output fields. This contract deliberately uses the existing ordinary
transport domain: each String length at most BODY_STRING_LIMIT256. Empty, short
and256-character ordinary ASCII strings are admitted; no new64-lowerhex rule.
Comparisons are bounded by256 characters. Unchanged canonical formatting still
owns ASCII32..126 validation afterward. This partial preflight does not promise
zero bytes for a later unrelated formatter-domain failure or qualify SHA shape.

Missing/non-Map template fails existing BODY_CORRELATION_MAP. Missing/null/non-
String or length257 fails BODY_GEOMETRY_STRING. Missing/null/non-Boolean summary
fails BODY_GEOMETRY_BOOLEAN. All six String checks and the Boolean read complete
before comparisons; a later bad leaf preempts a summary mismatch. Compare each
of the five other strings to capture-before, reduce all five with eager AND,
then require summary equality or fail BODY_GEOMETRY_SUMMARY. Matching false is
valid. Earlier header/context/roles/duration/restoration/ownership errors retain
precedence. These specific new failures emit no bytes or mutate the body.

New private correlationString reads, type checks and checks the existing length
limit, each with preceding sink admission; it owns no duplicate bound/state.
Reuse existing Map/Boolean owners. Addition: templateget+Mapguard2, six(get/type/
length)18, Boolean(get/type/unbox)3, fiveequals5, fourAND4, summarycompare1 =33hooks
per successful pass,99 across three passes. Total139hooks/42Mapgets. Hook calls
are not native units. No Meter/copy/group/byte/mutation call is added; existing
block-zero admission, fees, copies, groups and stable exclusive borrowing remain.

Pin current28660-byte codec SHA2560faa081daf3c6b478424c088759488fa2d6ae784574b5e3362993a8ef4b732c7.
Pure transform adds only checks and one private reader at exact anchors. Inverse
recovery conserves all other bytes, including lower writeBody suffix. No I/O,
CLI, class-loading or producer interface; existing caller owns admitted Source I/O.
Preserve previous contract draft and its unadopted411-hook SHA-format proposal.

Migrate five owned current fixtures: capture adds disjoint template with two
synthetic64-character '4' hashes after ownersReleased; each arm cleanup adds two
sourceGeometry fields after alias flags; cleanup adds summary true after alias
summaries. Explicit Java minimal/bodyJson/wide literals and independent Python
expectations migrate directly. Preserve padding and two-block bounds. Current
body gets34->42; restoration and ownership gets34->42/hooks106->139, labels only.
Retain60/210,69/253,209/566 cases/assertions and lower1177/6370 exact oracles.

Independent native oracle:32 masks over five comparisons using64-character '4'
and '5' strings, onlymask31 true; matching and flipped summary each. Test all six
leaves missing/null/Integer/Boolean/length257; matching empty/1/255/256 strings;
template and summary failures; type/comparison and prior-error precedence; zero
output/tree identity preservation;139hooks/42freshgets, admission denial and actual
same-body wide R5 count equivalence. Source12/private+public, literal3/private+
public, final-source JDK8target7 and new/migrated/retained native gates required.

Only synthetic Map/List/scalars, codec and actual Meter execute. No receipt
files/positiveprepare/binding/factory/actual orfake arm owner/graph/Session/
positiveoriginalDTO/executeArm/R9DTO/routing/exportV2/genuine envelope. Metadata
agreement is not actual geometry preservation, complete DTO validity, authority
or lifetime. Full export fees remain Source inference, full export NOT RUN.
All budgets/exact seeds/epochs/exclusions unchanged:90s/640/5s/32768/copy1MiB/
chunk4096/Source4MiB/opJSON8MiB/result1MiB/depth64/key1m/class10k256MiB/
compile-suite60s/reader180s/wrapper480s/cleanup120s; full30 EXCLUDED,
HARD/PSYCHOTIC unavailable/unrun, oldnormal20 historical read/not reread,
protectedUnread UNCERTIFIED, playable100=0, beta HELD, genuine UNSPENT_GATED.
Production/GWT/Player N/A, electrical/population NOT RENEWED, CI NOT RUN.
