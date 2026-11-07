# P8 native API and reason-row contract

Frozen before the P8 Router/rules transform, helper or wire producer. This is a
native evidence contract, not a production API. PLAN_R1.md controls scope.
Production Router92347537 and rulesb04f9906 remain byte-identical. Derive the
Router overlay from accepted P6 overlay0432eed2, preserving its entire coarse
observer API and permitsLayerStep bounds/endpoint loop verbatim.

## Router API

Inside PcbNetRouter.Router add FixedGraphReasonObserver with the single method
void onRejected(FixedGraphReason reason). FixedGraphReason is an immutable native
record of scalar/string fields below. No owner, rectangle, collection, Router or
callback reference is retained by the event. Its constructor/factory is called
only after checking the observer is non-null. Immutable descriptor ID reads are
bounded metadata work within the measured query. No geometry predicate is called
to create metadata. The observer return type is void; normal return cannot alter
the existing predicate result.

Add attachFixedGraphReasonObserverForStudy(observer, expectedQuery, live,
pristine, comparisonPassed), detachFixedGraphReasonObserverForStudy(), and
hasFixedGraphReasonObserverForStudy(). Attachment rejects null roles, any alias
between the three roles, wrong receiver, failed comparison and duplicate attach,
in the same order as P6's coarse API. Fine and coarse sinks are independent and
may coexist. Copies retain neither sink. Detach is idempotent. Fine sinks attach
only to the already-compared disposable query view. Finally blocks detach both
and drop references on success and every error. No sink on live/pristine/rules.

Fields are final and package-visible; names/types are also the exact JSON keys:

| Fields | Type / meaning |
| --- | --- |
| site, ownerKind | non-null String enums below |
| stage | int; existing P6 LEGAL_MOVE_REJECTED0 or TRAVERSE_REJECTED1 |
| startPadId, endpointPadId | String; start non-null, endpoint nullable |
| fromGridX, fromGridY, toGridX, toGridY | int; actual predicate inputs |
| ownerId, ownerNet | nullable String; actual descriptor only |
| ownerIndex | int; actual pad/component/boundary ordered index, otherwise -1 |
| reservationState, reservationValue | nullable String; cache fields only |
| domainLow, domainHigh | nullable String; boundary fields only |
| domainHorizontal, domainLowLimit, domainHighLimit, domainFirst, domainLast | int; domainHorizontal -1 unobserved, 0 false, 1 true; other domain operands0 outside boundary events |
| startEscape, endEscape, underpassEnabled | int; -1 unobserved, 0 false, 1 true |

No extra inline end-distance, direction comparison, nested escape/underpass
reason, unique trace, geometry rectangle or hypothetical result is synthesized.
The row's immutable query identity provides net/face/direction/physical points;
its scalar coordinates and endpoint IDs must correlate with the coarse witness.
underpassEnabled records the existing factoryUnderpasses flag only on a rejecting
courtyard branch. Rejection proves an allowed underpass did not rescue the move;
it does not identify the unobserved helper-internal reason. startEscape/endEscape
are the existing evaluated locals, never a repeated helper call.

## First false-return sites

| site | stage | ownerKind | observed owner fields |
| --- | --- | --- | --- |
| START_ESCAPE_DIRECTION | 0 | PAD | startPadId as ownerId; ownerIndex-1/ownerNet null |
| END_ESCAPE_DIRECTION | 0 | PAD | endpointPadId as ownerId; ownerIndex-1/ownerNet null |
| DOMAIN_UNKNOWN_NET | 1 | DOMAIN_POLICY | ownerId null/ownerIndex-1; ownerNet actual net argument |
| DOMAIN_CORRIDOR_OVERLAP | 1 | DOMAIN_BOUNDARY | ownerIndex and all boundary fields |
| DOMAIN_MEMBERSHIP_SIDE | 1 | DOMAIN_BOUNDARY | same; the original compound OR is preserved, its individual membership subcause NOT_OBSERVED |
| EMPTY_FACE_RESERVATION | 1 | CACHED_RESERVATION | reservationState SINGLE or MIXED; reservationValue exact nonempty net or empty string; ownerNet single value or null for mixed; ownerId null/index-1 |
| ADJACENT_RESERVATION | 1 | CACHED_RESERVATION | same |
| PAD_ESCAPE_INTERSECTION | 1 | PAD | pads[index].padId, padNets[index], index |
| PROFILED_PAD_TOUCH | 1 | PAD | same |
| COMPONENT_COURTYARD | 1 | COMPONENT | componentId,index,ownerNet null; existing startEscape/endEscape and underpassEnabled |

All fields not explicitly applicable are null, -1 for ownerIndex and tri-states,
or0 for domain numeric operands. No null-cache rejection exists: a null cache
passes that guard and emits no fine event. Empty string is mixed, never a net ID.
The first actually encountered owner is not unique ownership or trace attribution.

Keep every original conditional expression and its evaluation order. For the
empty-face return, store the original reserved==null || reserved.equals(net)
result once; emit only if false; return that same result. Other Router false
returns may call a false-return reporting helper at the original return point.
Do not rewrite/re-evaluate compound predicates or call private predicates later.

## Per-call rules API

Inside PcbTwoLayerRules add native ReasonObserver.onRejected(String site,
int boundaryIndex,String low,String high,boolean horizontal,int lowLimit,
int highLimit,int first,int last). Add permits(String net,Rectangle copper,
ReasonObserver observer). Existing permits forwards null to this one body.
Likewise the existing private permitsDomains entry forwards null to its one
observer-capable body. permitsDomain keeps its behavior through that null entry.
No callback field/global/thread state exists on rules. Ordered foreach boundaries
remain ordered; a local boundaryIndex starts0 and increments once after each
nonrejecting boundary. Unknown net uses index-1/null IDs/false/zero operands.
At each of the original two false returns emit the corresponding actual site
then return false; preserve both original conditions verbatim. Router supplies
a per-call adapter only when its fine sink is non-null, captures actual query
inputs, and emits the same single event before canTraverse returns false. No
duplicate generic domain event. Callback exceptions propagate unchanged.

## Native reason sidecar envelope

Version tsj-q100-geometric-reasons-v1, UTF-8 without BOM, exact LF JSONL. Every
line is one JSON object with unique keys, no NaN, <=65536 bytes including LF.
Full reason sidecar plus the unchanged parent artifacts remains <=8388608 bytes.
Header exact keys: kind="header", schema, runId, inputSha256, sourceSha256,
mode, parentPrefixSha256, parentGraphSha256, selectedQueryOrdinals.
selectedQueryOrdinals is the immutable14-index list rederived from the qualified
P6 full22 rows, not a newly selected frontier. mode is NULL, OBSERVE,
CALLBACK_THROW or REASON_THROW; CANARY has a separate literal report.

Query row exact keys: kind="query", ordinal, parentOrdinal, identity, allowed,
fineEvents, coarseEvents, cleanup, work. identity is an exact semantic copy of
the corresponding original P6 query identity. fineEvents contains only the exact
record fields above. coarseEvents uses the P6 endpoint fields/stage with the
original event order. Normal OBSERVE selected queries require exactly one fine
and one coarse rejecting event with equal endpoint/coords/stage. NULL requires
empty fineEvents but the same result/counters/state; a separate diagnostic coarse
witness may be attached to establish parity. There are14 P8 actual full query
invocations in a completed run, reported separately from original P5/P6 queries.

The exact nested identity, cleanup, work and footer envelopes MUST be frozen in
a transport addendum before helper/JSON producer implementation; this contract
authorizes only the independent native transform/API and literal canary sources.
No partial row or missing footer qualifies as a completed observation.

REASON_THROW uses a new exact sentinel FIRST_GEOMETRIC_REASON_CALLBACK, throws
on the first P8 fine event after recording it, and has fine1/coarse0 on that
query. The old P6 CALLBACK_THROW/FIRST_ENDPOINT_CALLBACK contract stays intact.
Cleanup/effects/owner/context proofs precede any expected-exception match.

## Validation boundary

Exact source hashes and whitelisted byte edits are checked before transform.
Preserved segments, original P6 coarse seam, bounds/loop, original guard text,
copy-null sinks and absence of rules sink fields are checked after transform.
Literal geometry/owner/lifecycle tests require final-source selected JDK8 native
execution; static checks alone are NOT RUN for runtime behavior. Canonical event
counts are not expected goldens. Independent parent cut/graph/map/geometry and
full22 semantic parity stay required. No production/GWT/Player renewal is claimed.
