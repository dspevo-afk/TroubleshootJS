# Copy-effects summary and fixed scope: finite current contract

Append checks after geometry validation, before encoding. CurrentResultProjections
cleanup reads template.effectsBeforeSha256/effectsAfterSha256, compares exact
Strings and emits originalCaptureCopyEffectsUnchanged with the fixed scope
ORIGINAL_CAPTURE_COPY_ONLY. Reuse retained geometryTemplate and cleanup maps.
No actual external effects or SHA shape are established by these observations.

Read/type/bound effectsBefore then effectsAfter via existing correlationString.
Both use the existing BODY_STRING_LIMIT256. Next read the strict Boolean summary
with existing correlationBoolean, then effectsScope with correlationRead and
existing requireFixedString against the26-character fixed producer value. All
leaf validation precedes String.equals and summary equality. Matching false is
valid; scope must still be fixed. Earlier header/context/role/duration/restoration/
ownership/geometry errors retain precedence.

Missing/null/non-String or length257 effects leaf fails BODY_EFFECTS_STRING.
Missing/null/non-Boolean summary fails BODY_EFFECTS_BOOLEAN. Missing/null/non-
String/wrong scope fails BODY_EFFECTS_SCOPE; fixed expected String.equals is
bounded by26 characters and uses the existing combined type/equality owner.
Inconsistent summary fails BODY_EFFECTS_SUMMARY. Earlier new String errors
precede Boolean, then scope, then mismatch. These new failures emit no bytes and
leave the body unchanged. The normal formatter still owns ASCII32..126 afterward:
matching ASCII-invalid strings may fail there after output starts. Whole-body
zero-output preflight, complete DTO validity and SHA syntax are not qualified.
Matching empty, short and256-character printable strings are admitted.

Parameterize the existing private String reader with errorCode. Migrate its six
geometry calls to pass BODY_GEOMETRY_STRING, preserving old errors. Retire the old
private signature; no duplicate/wrapper reader or validator. Reuse Boolean and
fixedString owners. Two(get/type/length)=6, Boolean(get/type/unbox)=3, scopeget+
fixedStringcontrol=2, effects.equals=1, summarycompare=1:13 extra sink hooks/pass,
39 across three passes,4 gets. Total152hooks/46gets. Hooks are not native units;
no Meter/copy/group/byte/mutation operation is added. Block-zero admission and
all fees/limits/exclusive stable borrowing remain unchanged.

Pin baseline31250/SHA256193b8b3d56aa909a87c825055c6f4fd47502e75107318eb936c5888de4fa1d64
and reviewed fragments. Pure transform changes only the private reader/six calls
and appended checks. Inverse recovers exact input; lower writeBody suffix is
conserved. No I/O, CLI, class-loading or producer interface; existing caller owns
admitted Source I/O. Preserve original draft and all prior evidence/failures.

Migrate six current higher Sources: template adds effectsBeforeSha256 and
effectsAfterSha256 after geometry fields, both explicit synthetic64-character '6'.
Cleanup adds effectsScope then originalCaptureCopyEffectsUnchanged true after
sourceGeometryUnchanged. Directly author minimal/bodyJson/wide Java literals and
independent Python expectation. Preserve disjoint maps/padding/two-block bounds.
Other four canaries change only46 get/152 hook assertions and labels; retain
60/210,69/253,209/566,125/344 coverage and exact lower1177/6370 oracles.

Independent native cases: equal/true and distinct/false plus flipped summaries;
two leaves missing/null/Integer/Boolean/257; accepted0/1/255/256 matching strings;
summary types; missing/null/types/wrongcase/wrongscope/empty/257 scope; type/scope/
mismatch and prior-error precedence. Check exact errors/zero bytes/tree identity,
fresh admissions/152hooks/46gets and actual same-body wide R5 count equivalence.
Source12/private+public, independentliteral3/private+public, final-source JDK8
target7 and new/migrated/retained native gates required before publication.

Only synthetic Map/List/scalars, codec and actual Meter execute. No receiptfiles/
positiveprepare/binding/factory/actualorfake arm owner/graph/Session/positive
originalDTO/executeArm/R9DTO/routing/exportV2/genuine execution. Metadata agreement
is not actual copy effects, full semantics, source authority or lifetime. Full
export fees remain Source inference; actual count equivalence is separate.
Production/GWT/Player N/A, electrical/population NOT RENEWED, CI NOT RUN.
All90s/640/5s/32768/copy1MiB/chunk4096/Source4MiB/opJSON8MiB/result1MiB/depth64/
key1m/class10k256MiB/compile-suite60s/reader180s/wrapper480s/cleanup120s limits,
exact seeds/epochs and exclusions unchanged: full30 EXCLUDED, HARD/PSYCHOTIC
unavailable/unrun, oldnormal20 not reread, protectedUnread UNCERTIFIED,
playable100=0, beta HELD, genuine UNSPENT_GATED. Protected/heldoutDATA/oldactual
artifacts/generateddataclasses/wholelegacybuilders/consumers remain denied.
