# Next coherent contract: qualified-prior assembly envelope

The bounded read-only body_semantic_seam leaf authenticated the three baseline
Sources against SOURCE_INPUT_R1.json. It read only those admitted current Sources
and scope metadata. No tests, producer/helper/native entrypoint or GUI executed.
Root records this as Source selection only, not runtime qualification.

CurrentAssemblyMetadata.prepare110 creates a LinkedHashMap with exactly these
twelve ordered keys:

1. schema
2. inputContextSha256
3. sourceParent
4. manifestSha256
5. sourceManifestSha256
6. readerSha256
7. resultSha256
8. operation1Sha256
9. parentReceiptSha256
10. priorRunId
11. priorOriginalReceipt
12. trialDisposition

The schema is explicitly tsj-cohort-terminal-qualified-prior-v2, owned by
CurrentAssemblyMetadata.QUALIFIED_PRIOR_SCHEMA. priorShape() checks the source
prior's ten keys/order; validateReceiptTuple() checks receipt metadata before
copying. The copied values otherwise rely on the validated, unchanged caller
setup. This establishes no runtime value type, authority or receipt-content proof.

Current codec preflight guards qualifiedPrior Map type, correlates its context
to the body, and reads trialDisposition for cohort checks. It lacks the exact
wrapper schema and twelve-key shape check. Cohort, arm and cleanup projection
outer contracts are covered by N26/N27/N28; do not reopen them or expand into
all nested prior semantics, a whole DTO rewrite or one-field validators.

Candidate implementation: reuse the existing prior Map and append one complete
envelope census after all existing cohort/arm/cleanup checks. Use fixed12 and
the prepaid iterator/fixed-string pattern, checking schema entry value during
the census. Reuse the current schema constant; priorKey() is private, so a local
fixed selector or narrowly justified sharing must preserve its owner contract.
No new schema literal/ID format/type/nonempty/hex rule without Source evidence.

Price: iterator1 +12*4 +terminal1 =50 hooks; schema entry.getValue1 plus its fixed
string guard1 gives52. No new Map.get/List.get. These three files prove the delta,
not the absolute native baseline. Accepted N28 native proof supplies4162 hooks/
65 Map gets/1897 List gets, implying4214/65/1897 if unchanged. Independent Source
review and actual native checks must confirm before acceptance. Hooks are not
native units; no copy/Meter/group/byte charge or global budget change justified.

Finite synthetic evidence: ordered envelope with correct schema, bad schema
value, missing unused key, extra/non-String/reordered keys, and earlier context/
trial/cohort/arm/cleanup failures. Missing inputContextSha256 must retain
BODY_PRIOR_CONTEXT; missing trialDisposition must keep its existing earlier Map
failure. Missing other keys reach the late census. Freeze exact new error/read
order before implementation, including when schema value and later keys are bad.
Migrate current higher ordinary/Java-literal/Python fixtures directly without
claiming their unused synthetic values meet the actual setup/receipt contracts.
Preserve work padding/two-block layout and independent lower1177/6370-byte oracles.

Envelope-only, no full DTO, authority, borrowed lifetime, receipt contents,
actual cleanup/effects/geometry/SHA truth, capture mapping or whole-body
zero-output. Ordinary inner/ASCII checks may reject later after output. No further
producer/validator Source admitted without exact selection/authentication.
N29 implementation/tests/build/native/genuine NOT RUN. All existing limits,
exact seeds/epochs/exclusions unchanged: full30 EXCLUDED; HARD/PSYCHOTIC
unavailable/unrun; protected/heldout DATA, old actual artifacts/generated DATA
classes and whole legacy builders/consumers denied; oldnormal20 not reread;
protectedUnread UNCERTIFIED; playable100=0; beta HELD; genuine UNSPENT_GATED.
