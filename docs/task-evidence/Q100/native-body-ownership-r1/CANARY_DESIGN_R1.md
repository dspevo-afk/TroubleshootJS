# Body ownership canary: frozen source design R1

Scope: source authoring only. All native cases below are NOT RUN by this worker.
The root owns source review, closure admission, selected JDK8/target7 build,
SMOKE execution and then the unchanged 60-second SUITE limit. No fixtures,
fixture directory, result file, input import, generation or genuine capture is
needed. Only BodyOwnershipCanary.java and this design are worker-owned.

Inputs read: OWNERSHIP_CONTRACT_R1.md; the nine-parent SOURCE_INPUT_R1.json;
the pinned CURRENT_STUDY.java (524318f4...), SHARED_OWNER.java (99993fc4...),
CurrentV2CanonicalCodec.java (76fb7bbc...) and explicitly admitted current
AuthoredFlatWorkCanary.java (38def52b...). No legacy builder, whole consumer,
DATA, old classes, Original/ArmResult instance, Session, routing or producer was
read or invoked. Current main/assemble stay closed. Parent loads type metadata
through its existing adapter; the canary calls no genuine-arm entrypoint.

## Fixed names and protocol, before execution

Main class: com.lushprojects.circuitjs1.client.BodyOwnershipCanary.
Exactly one argument, SMOKE or SUITE. One compact JSON stdout line:
mode (string), status (PASS/FAIL), names (ordered string array), cases (ordered
record array). A bad argument produces FAIL with empty arrays and exit 1.
Any failed assertion, exception or unexpected cleanup failure produces FAIL
and exit 1. No assertion total is predicted or reported. PASS records have
empty error and cleanupError. Every case record has exactly these scalar keys:

    name,status,units,copies,chunks,groups,observedItems,operationNanos,
    cleanupNanos,error,cleanupError,cleanupDisposition

Counts/timings are nonnegative JSON integers. cleanupDisposition is CLOSED
normally (including the null-meter case, where nothing exists to close), or
DISCARDED_EXPECTED_REJECTION for group-expiry. The latter retains failed open
Meter state only until the case record is released, invokes no finish, owns no
OS resource and is explicitly not evidence of successful finishGroup cleanup.
The measured operation ends before harness cleanup; cleanup time is separate.
No failures/timeouts/exit2 are converted to PASS.

SMOKE has exactly nine cases, in order:

1. null-meter
2. null-handcount
3. empty-map-handcount
4. empty-list-handcount
5. one-map-handcount
6. three-list-handcount
7. mixed-handcount
8. sibling-alias
9. copy-capacity-zero

SUITE has exactly 21 additional cases, with no repeated smoke cases:

1. debit-order-map
2. scalar-domain-sharing
3. disjoint-equal-containers
4. nested-alias
5. self-list-cycle
6. self-map-cycle
7. ancestor-cycle
8. unsupported-values
9. unsupported-keys
10. depth64
11. depth65
12. map-key-depth65
13. key-inclusive-visits-1000000
14. key-inclusive-visits-1000001
15. copy-capacity-one-of-two
16. group-roll
17. group-limit
18. operation-expiry
19. group-expiry
20. r7-alias-before-close
21. r7-disjoint-handoff

Root must authenticate SMOKE before SUITE. The source enumerates 30 unique
cases; this is a case manifest, not a claim of 30 passing executions.

## Independent expectations

The positive oracle is the frozen census 5+6M+5L+10Em+5El actual units and M+L
copied entries/chunks, independently supplied as tiny literal expectations:
null 5/0; empty map 11/1; empty list 10/1; a:null map 21/1; three-null list
25/1; a:[null,null] 36/2. Equal distinct empty children cost 30/3 and nine
supported/shared scalar children cost 55/1. Scalar sharing is allowed while
map/list identities repeat neither across siblings nor within nested branches.

DebitMap demands actual Meter units 9/10/11/12/13/17/21 immediately when
entrySet/iterator/hasNext/next/getKey/getValue/final-hasNext are observed.
Unsupported scalar attempts cost five each; each unsupported map key costs
16 including its inclusive key visit. All BODY_OWNERSHIP_* and copy-capacity
rejections are required to be IOException with exact messages.

The depth64 chain has 64 lists and a null leaf at depth64: 645 units/64 copies.
Its retained identity path must be unchanged. A depth65 leaf rejects. A map at
depth64 with a key at depth65 rejects, independently covering key depth.

The virtual AbstractList is caller-owned and supplies an actual iterator. Its
size/get are forbidden; each observed hasNext and next increments a test-only
observation count. No guard counter is changed or reflected to bypass the cap.
A root one-entry map, its key, its list child and 999997 null items give exactly
1000000 visits, 5000011 units and two identity copies/chunks. The iterator must
observe 999997 next and 999998 hasNext operations. The over case actually returns
one additional null item, then rejects its visit admission before kind dispatch:
999998 next, 999998 hasNext, 5000013 units, two copies/chunks. This is actual
million-visit proof if it executes successfully, not private-counter simulation.

Capacity uses the real setCopyAllowance method: zero rejects before any list
observation at five units/zero copies; allowance one permits the outer container
and rejects its child before observing that child. Group roll first charges
32766 using the real adapter, then null adds five across the group boundary.
The group-limit case actually closes 640 one-unit groups before the denied next
guard allocation. Operation expiry constructs the actual Meter with an already
expired monotonic origin; no live field is overwritten. Group expiry spends
5.1 actual seconds inside its controlled iterator operation and requires the
next precharge to reject at nine units, zero closed groups, open Meter state.
The failed Meter is explicitly discarded, rather than invoking an expired finish.
No copy-chunk overflow counter is invented; huge integer-chunk exhaustion is
not separately claimed. Normal copy/chunk pricing is checked through real calls.

## R7 integration expectations

The canary targets the emitted Study with exactly one ownership guard insertion
after its work map and plan exist and before placeholder-body accounting/finish.
It uses Parent/SetupMeter and beginOriginalOwner/admitOwnerGroup/adoptOriginalOwner
only for the existing closed-snapshot synthetic lifecycle prerequisite. Detached
counter demand is zero except one admitted original-owner group; there is no
fabricated OriginalArm, ArmResult, electrical graph, Session or producer invocation.

For an alias body {work:null,a:shared,b:shared}, the existing seam installs its
34-slot work map, then ownership rejects. Before harness cleanup it must still
be open, have zero closed groups and an empty actual groupMeasures list. All
work slots remain zero; the original a/b identity/content remains unchanged.

For the generic disjoint body {work:null}, existing R7 work costs independently
624 + 6 per literal WORK_KEYS ASCII character + 184 fixed units and 95 copies
in three chunks. The new ownership traversal sees two maps and 35 entries:
367 extra units and two copies/chunks. Therefore the seam closes with 97 copies,
five chunks and one measured wrapper group. Its 34 placeholder slots retain
literal order and become detached numeric values only. All 34 finalized values
are checked against the independent synthetic scalar vector; units and metadata
residual both equal the expected new census, groupsBeforeExport=2,
wrapperGroupsBeforeExport=metadataGroups=1. Snapshot ready/binding checks and
finalization must leave actual Meter units/copies/chunks and work identity unchanged.
This does not execute count/write/read-compare/export/publication or certify the
thirteen-key constructor, metadata ticket, host transport or a playable body.

## Status and limits

Authored/reviewed source only; native SMOKE/SUITE, compile and runtime are NOT RUN
by this worker. No OS processes, listeners, profiles or persistent resource were
launched. No staging, commit, push, email or repository writes were performed.
Existing unrelated repository work is preserved. Root owns integration acceptance.

Normal main/assemble closed; genuine original,NULL,OBSERVE UNSPENT_GATED;
HARD/PSYCHOTIC unavailable-unrun; protected DATA denied; full30 excluded;
protected unread UNCERTIFIED; playable100=0; beta HELD. No electrical/population,
GWT or visible-player qualification is renewed. Existing 90s/640groups/5s/
32768units/copy1048576/chunk4096 bounds and depth64/visit1000000 remain unchanged.
