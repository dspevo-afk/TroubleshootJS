# Cohort projection preflight contract

This is a native codec component contract, not full DTO or host acceptance.
The authenticated current bridge borrows setup.data.prior, derives the cohort,
projects it, and later copies that same trialDisposition into qualifiedPrior.
Its caller owns validated, unchanged setup and captured IDs exclusively.

Source basis: CurrentResultProjections.cohort17-34 (10055B/c0398d317419931b7b3b749d3f900ef1a7b27d9c630473412a7bf47595fccd28);
Study.Cohort/deriveCohort551-596 (236974B/4e0563854a2282b10d5804da9e9843814ba8363b23400eea63a2459ca14de680);
CurrentAssemblyMetadata.cohortBeforeBody31-48 and prepare110-175
(12486B/49f9930a4e1d8aed297236dd4ee742233c73cffcded9f495fd5815f58d11687b);
CurrentV2InputSetup.unique334-349/trial368-379/walk/read
(22823B/d0e42cf2fd93657e2a086e4fff4e8b0111937b7d5c22899fac1107c1bf3b12e2).
Only scoped current Source was read; these owners were not executed.

After all existing header/correlation/cleanup-schema checks, read cohort again
through the existing prepaid Map reader/guard. Require exactly the two keys
netIds then requestedNetIds. Borrow List values of lengths2 and60, respectively.
Read qualifiedPrior.trialDisposition with the same Map guard. Require status RUN,
reason TRIAL_EXECUTED, orderChanged true, feedbackValidated true; borrow its
cohortNetIds and requestedNetIds Lists at lengths2/60; read capturedFailedNetId
and controlFailedNetId. Each cohort pair ID is a nonempty String of length<=256,
equal by content to its prior counterpart and captured/control field in order.
The pair must be distinct. Each requested ID uses that same String domain and
equals its prior counterpart; first two equal the cohort pair; no duplicates.
Every earlier contract keeps precedence. See fragment for exact read/error order.

Stable failures: BODY_COHORT_ORDER, BODY_COHORT_LIST, BODY_COHORT_DISPOSITION,
BODY_COHORT_ID, BODY_COHORT_PAIR, BODY_COHORT_DUPLICATE,
BODY_COHORT_REQUESTED, BODY_COHORT_PREFIX; nested Map guard retains
BODY_CORRELATION_MAP. Rejections in this preflight occur before encoded bytes.
Sink denial occurs before the corresponding controlled operation. Errors from
borrowed containers propagate. Caller stability remains required across passes.

The non-null/nonempty request domain comes from the validated-setup bridge
precondition; deriveCohort alone can permit one null if supplied arbitrary prior.
These synthetic IDs do not qualify CurrentV2InputSetup, whose current trial has
specific IDs. No ID spelling/hex rule, remaining58-to-originalCapture order rule,
full DTO qualification or broader actual-data acceptance is imposed or claimed.
ASCII enforcement remains in the unchanged ordinary formatter; other invalid
values may still fail after output starts. This is no whole-body zero-byte proof.

No new Map/List/copy/Meter/group/byte allocation or global budget. Lists are
borrowed. Success adds3898 sink hooks and12 Map.get calls (totals4052/59), plus
1897 List.get calls per pass. Uniqueness uses at most1770 bounded comparisons.
Hooks are not native units. Actual same-body R5 count is tested separately;
full export/write/compare/publication remains NOT RUN and complete fees are
Source inference. No original arm owner/graph/Session/factory/receipt/prepare/
positive DTO/exportV2/genuine execution. Existing limits/exact seeds unchanged.
playable100=0; beta HELD; genuine UNSPENT_GATED; full30 EXCLUDED;
HARD/PSYCHOTIC unavailable/unrun; protected-unread boundary UNCERTIFIED.
