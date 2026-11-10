# Bounded current Source review

Root reviewed the exact transform, fragments, pinned baseline/producer, six
fixture migrations and new canary. A separate read-only reviewer checked the
five component contract/fragment/transform/test files and both baseline Sources.
It found no substantive issue and ran no tests/programs. Static review and actual
runtime gates are separate evidence.

CurrentResultProjections.cleanup reads template effectsBeforeSha256 and
effectsAfterSha256, computes exact String.equals and emits the summary plus the
fixed ORIGINAL_CAPTURE_COPY_ONLY tag. The extension validates this relationship
after geometry, before encoding, reusing geometryTemplate and cleanup. It does
not establish actual external copy effects, hash format or complete DTO validity.

The existing private String reader now accepts an error code; its old signature
is removed and all six geometry calls preserve BODY_GEOMETRY_STRING. The two new
calls use BODY_EFFECTS_STRING and the existing BODY_STRING_LIMIT256. Existing
Boolean and fixedString owners handle summary and scope. Scope's fixed expected
26-character value bounds equality without a new String-domain validator.

New validation order: before/after Strings, Boolean summary, scope get/fixed
guard, effects equality, summary comparison. Old errors retain precedence. New
String errors precede Boolean, then scope, then mismatch. Matching false is valid.
Two bounded String reads6 +Boolean3 +scopeget/fixed2 +two comparisons2 =13 hooks
and4 gets/pass,39 over three passes; total152hooks/46gets. Hooks are not native
units. No Meter/copy/group/byte/mutation operation is added. Inverse recovery
conserves all other bytes, including the complete lower transport suffix.

The existing printable-ASCII formatter remains afterward. Matching empty/short/
256-character printable strings may pass; no64-hex requirement is imposed.
Matching ASCII-invalid values may fail later after output begins; zero-output
claims apply only to these new preflight failures, not whole-body validation.

Root inspected the fixture maps/Python expectations and actual four-canary diffs.
Existing fields are added directly to Java minimal/bodyJson/wide literals and
Python expectations, without blessing serializer output. Canary changes are only
46 get/152 hook assertions and labels;60/210,69/253,209/566,125/344 coverage,
two-block padding and independent lower1177/6370 byte oracles are unchanged.
Root independently counted new54 cases/161 assertions before execution:4/10
equality/flips,10/30 malformed leaves,4/8 bounds,4/12 Boolean,8/24 scope,3/9
new precedence,16/48 earlier errors,1/1 hooks,3/9 admission,1/10 actual R5 count.

Only synthetic Map/List/scalars, codec and actual Meter execute. No receiptfiles,
positive prepare/binding, factory, actual/fake arm owner, graph, Session, positive
originalDTO, executeArm/R9DTO, routing, exportV2 or genuine envelope. Metadata
agreement does not prove actual effects, full semantics, authority or exclusive
borrowed lifetime. Protected/held-out DATA and old actual artifacts remain
excluded; the preexisting unread boundary remains UNCERTIFIED.
