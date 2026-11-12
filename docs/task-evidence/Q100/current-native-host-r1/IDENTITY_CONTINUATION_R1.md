# Native Source and tool identity continuation

`observe_native_code_tools` can now retain its original observation identities
for a later trusted caller check. Previously it returned only path/size/SHA facts,
so a caller that observed again after compile setup could accept a different file
with identical bytes and lose the original identity comparison.

The strict boolean keyword `include_identity_tokens` defaults to False. Existing
callers still receive exactly `sourceFacts` and `toolFacts`. True adds fresh
`sourceTokens` and `toolTokens` maps in the same fixed six/two role order, using
the observer's existing `(bytes, sha256, projected_identity)` tuples. The trusted
caller retains its authorized specs and uses the existing `_recheck_fact_map`
later. There is no second reader, rechecker, file body or live handle. Token maps
are in-process observations, not unforgeable capabilities, serialized authority,
normal admission or an atomic check-to-launch lock. Module/spec authenticity and
exclusive endpoint lifetime remain caller obligations.

The [manifest](IDENTITY_CONTINUATION_SOURCE_MANIFEST_R1.json) and
[proof](IDENTITY_CONTINUATION_PROOF_R1.json) bind final source and actual selected
Windows Python3.13 `-I -B`, optimize0 gates. Early Smoke2 passes positive retained
identity rechecks and a same-byte Source replacement after return. Final Suite20
passes all16 unchanged existing methods and four new methods: ordered identity
maps/default compatibility, nonboolean flags rejected before reader calls, and
Source/copied-tool replacements detected against original identities. Each
replacement preserves the renamed original and asserts equal hashes plus distinct
real Windows file IDs. Selected donor tools are read only; Java is not executed.
The real junction node is target/tag revalidated, removed and proven absent.
All three outer Python exits, including the initial failed harness, are observed;
a query-only final audit finds no known new persistent actors. Ordinary CODE and
explicit NO_DATA fixtures remain controlled evidence.

Initial SmokeR1 remains FAIL/exit1: the root harness used the wrong existing test
class name before fixture setup or any tests. Corrected SmokeR2 passes. Root also
corrected draft tests to reuse existing BASE.digest, and corrected two private
preparation guards (a substring count and a reverse documentation splice). Those
refusals remain recorded; no behavioral assertion was weakened, no old result
reclassified and no unchanged test gate rerun. All original adapter bytes outside
the owned signature/doc/flag/return regions are exact; all old test source bytes
and methods remain unchanged. Bounded leaf review found no blocker and made no
execution claim; root owns actual gates, integrated review and acceptance.

A leaf's helper search exceeded the assigned slices: `rg -g '*.py'` covered
scripts and tests/qualification and returned Python CODE helper matches. Root
recorded the footprint and confined later reads to named files/slices. This does
not restore the already-uncertified protected-unread boundary or authorize DATA
access. No normal qualifier operation or generated Java DATA factory was run.

The independent current class metadata was located through the existing public
CODE proof/source-freeze/config chain; an initially guessed manifest filename
was wrong. The larger six-Source native compile/closed-class composition is NOT
RUN here. N62's declared inputs are unchanged and rechecked; its finite evidence
is retained within its recorded scope, not rerun or renewed. Native role budgets,
actual verified return, tool/library/classpath provenance and semantic/lifecycle
closure remain separate requirements. Neither N62's test4096B caps nor JSON-body
limits establish normal role policy.

Both normal Study holds remain unchanged; original->NULL/OBSERVE UNSPENT_GATED.
Full30 normal MEDIUM EXCLUDED; HARD/PSYCHOTIC unavailable/unrun; protectedUnread
UNCERTIFIED; oldnormal20 NOT REREAD; playable100=0; beta HELD. CircuitJS truth,
product seeds/epochs/order and all existing budgets remain. Production Java/GWT/
visible Player N/A; electrical/population NOT RENEWED; CI NOT RUN/self-hosted runner
prerequisite. Main/E/Desktop work, previous failures and uncertain resources stay
preserved. Next unstarted N64: freeze the trusted current CODE compile composition
and its remaining explicit policy/provenance gates.
Implementation `17a776513b674b00830e555f59ba96402224834a` (Retain native source and tool observation identities)
normally pushed to origin/codex/q100-physical-feasibility; exact remote SHA verified.
Completion Gmail `1a1ce9981c71ccd7` sent once; this receipt sends no second email.
