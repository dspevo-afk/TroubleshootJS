# Actual net-priority report v4

FROZEN before v4 producer/reader implementation or native study execution.
Implements the prior [frozen experiment](../canonical-pass1-reuse-r1/PLAN.md),
SHA1e09cf1a8b29b8551ea85d23ba35df1bdd26050f2f20e30f58b257d30274f1d4.
No default, epoch, routing acceptance or playable100 admission.

## Retained closed contract

The exact [v3 wire](../branch-tree-study-v3-r1/WIRE.md),
SHAbc7379b8e1b7f17299b4787fe8e0ca119708cfc818109dadb00acba78dffc885,
defines all unchanged field sets, encoding, limits, ownership and cleanup rules.
Only the explicit replacements below apply. No compatibility lane: headers v1,
v2 and v3 reject. Current HEADER.schema=p07-actual-branch-order-study-v4.
Index/source/spec/transport envelopes remain their existing v1 schemas and are
bound to the new wire and current source. Current native base has1080 exact rows:
850 production Java,224 direct contract Java,6 static. Its exact raw manifest is
pinned by reader.BASE_SHA and the pre-run source manifest; no old base is accepted.
Exactly one writer,host,reader,wire extends that base to1084 native source rows;
actual javac input census is1075 after the same three exact scratch substitutions.
The running reader and WIRE source paths stay exact; WIRE now resolves to this file.

All90s/640groups/5s/full-group-close/32768raw-step/120s-cleanup/480s-native/
1800s-outer and1M/100K/250K/48via/4transition/3ordering bounds remain. Encoding,
file/line/PAD/geometry caps, exclusive regular files, reparse checks, exact raw
hashes, source rereads and strict integer/boolean separation are unchanged.
Every actual pair-preparation/sort/materialization unit stays in raw P07 work.
Only verified diagnostic clone units may be removed for normalized control
comparison; no treatment work or resource cost is subtracted.

## Arms and exact new row

HEADER.arm gains netOrderStrategy; its exact fields are
id,api,strategy,netOrderStrategy,observed. strategy still means branch choice.

|op|id|api|strategy|netOrderStrategy|observed|
|---|---|---|---|---|---|
|0|PLAIN_ENTRY_RECEIPT_CONTROL|PLAIN_BEGIN|CANONICAL_NEAREST|CANONICAL|false|
|1|OBSERVED_DEFAULT_NEAREST|BOUNDARY_CONVENIENCE|CANONICAL_NEAREST|CANONICAL|true|
|2|OBSERVED_EXPLICIT_NEAREST|BOUNDARY_EXPLICIT|CANONICAL_NEAREST|CANONICAL|true|
|3|SECOND_ORDER_MINIMUM_PAIR|BOUNDARY_EXPLICIT|CANONICAL_NEAREST|SECOND_ORDER_MINIMUM_PAIR|true|

NET_ORDER exact fields:
record,operation,group,pass,strategy,promotedNet,completedOrderings,yieldedOrderings,
prefixSha256,prefixBytes,netIds.

Exactly one NET_ORDER is required per observed operation that reaches a pass1
branch. It has pass=1,strategy=that arm's netOrderStrategy, integer operation1..3,
and a complete array of unique net-ID tokens containing every PAD census net
exactly once. HEADER.netCount equals the PAD census net count for this finite
report domain; a zero-pad net outside that census is unsupported, not silently
omitted. Actual tiny and authored cases retain all singleton nets.
promotedNet is null or a census net ID. completedOrderings=1 and yieldedOrderings
is0or1 at this boundary. prefixBytes is1..1MiB and prefixSha256 a lowercase SHA256.

Capture from the actual Search only after nets is fully installed and existing
promotion applied, while currentNet is null,netIndex=0,branch and publisher null,
and before any branch of that pass advances. Hash that live Search.layout; it
must equal the actual pristine request placement hash/byte count. Source review
and root native review must establish this access. No copied/reconstructed order
may substitute for Search.nets. Emit before all pass1 BRANCH rows, after PAD census.
No NET_ORDER is valid in plain operation0, other passes, or after RESULT.

GROUP gains netOrderRows: exact nonnegative integer number of NET_ORDER records
in that group. Such a group is ADVANCE,measured P07,p07Slices=1,observerChecks>=1.
callbacks stays BRANCH-only,censusRows stays PAD-only. Serialized byte accounting
includes the new line; hashInputBytes includes its actual geometry preimage.
No partial preparation/vector qualifies. Retain all DIAGNOSTIC_CLONE pair, timing,
stability and raw-work proofs from v3 unchanged.

## Independent order and promotion proof

From PAD rows independently group every unique pad by net. Canonical pass1 base
order is descending pad count then net-ID order. Treatment base order uses each
net's minimum Manhattan distance over every unordered actual pad pair, with
unbounded/wide arithmetic; paired nets sort by minimum then net-ID, all singleton
nets follow lexically. Lexical ties use the existing bounded ASCII ID domain,
equivalent to Java String.compareTo. The reader derives lexical witness pairs
for reporting; no producer metric or witness is an oracle input.
Move only the independently justified promotedNet to index0 after base sorting,
then compare the complete recorded vector. Reject duplicates, omissions, unknown
nets, wrong strategy/order/promotion, partial or late rows and inconsistent counters.

For this finite lane, promotion authority is deliberately closed:

- If prior pass0 yielded, captured cumulative yieldedOrderings=1,promotedNet=null,
  all pass0 terminal searches are FOUND and their count is padCount-netCount.
  The count is a source-owned yield audit input; FOUND alone never establishes yield.
- If prior pass0 did not yield, yieldedOrderings=0 and its final terminal search
  must be NO_PATH or BRANCH_SEARCH_LIMIT. promotedNet must equal that failed net.
  Earlier pass0 searches must be FOUND. A TOTAL_SEARCH_LIMIT cannot continue here.
- An all-FOUND non-yielded prior pass has no unambiguous failure authority in this
  wire. Reject it, including a claimed null promotion; do not infer Publisher or
  final quality outcomes. The scoped authored C_COIL_LOW failure and tiny yielded
  pass are covered without adding an inferred prior-outcome field.

The producer must source-check these live counters/promotion against its earlier
terminal observations; the reader checks them independently against pass0 rows.
An unsupported prior outcome is a failed/incomplete report, not treatment success.

All branch-choice expectations use canonical nearest, including operation3.
Retain independent root/reached/tie, expansion, branch/clone and pass0 parity
checks. Pass1 observed BRANCH net IDs must form an order-preserving subsequence
of NET_ORDER.netIds; singleton and unvisited tails may have no branch row. Later
selection proves publication only under the existing v3 rules; tail FOUND does not.

## Comparison and admission

Observed default and explicit canonical controls match their NET_ORDER records
apart from operation/group. Plain/default receipt,identity,winner,physical and
normalized logical-work parity remain mandatory. Explicit canonical versus
treatment must share census,request context,prior-pass counters/promotion and
pristine prefix hash/byte count. Compare their complete vectors and report the
first differing index and net IDs only under that same-prefix condition. This
is an ordering-vector comparison at the pre-branch boundary, not proof of the
live copper prefix at a later first differing branch. Do not retain the v3
farthest-pad divergence proof or demand pass2 equality after a changed pass1.

If pass1 is not reached, the comparison is NOT_OBSERVED and root cannot call the
treatment measured. An equal vector is NO_DIVERGENCE, not a route success. Keep
fixture-only versus native-report-only statuses and separate root process/source/
context acceptance. Physical/access/functional/solvability and actual finalizer
proofs remain required for playable100. No seed sweep, normal scope expansion,
HARD/PSYCHOTIC access, budget change or default adoption is granted.
