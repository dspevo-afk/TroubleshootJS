# Current V2 input setup implementation worksheet R1

Status: SOURCE-ONLY candidate and independently source-derived debit inventory.
No Java execution, compile, factory call, codec import, generated DATA, native
Meter invocation, original capture, Session or routing run was performed by this
worker. Root owns source acceptance and every later gate. The implementation is
`CurrentV2InputSetup.java`, a package-visible final helper in the pinned study's
package. This worksheet does not enable normal main/assembly.

## Source authority and implementation boundary

The root operation contract is
`INPUT_SETUP_OPERATION_CONTRACT_R1.md`, SHA-256
`89c2b14507708985a32a37869ca357b8b072f57fa0ef48a320a28fb14567b2a3`.
The complete nested field policy is
`NATIVE_PROJECTION_VALIDATION_MAP_R1.md`, SHA-256
`cd5773481a2592aa79355ee82733c0d4500df3b223d5c578e348ce64f74ef642`.
The contract audit and prepayment draft were read as source design evidence.
Only the named R2 source inputs were consulted; in particular, relevant pinned
study/owner members and the literal `_synthetic_context` SOURCE in
`source/test_context_codec.py` were read. That function was not imported or run.
No generated projection/context/prior DATA was read or produced.

The four-method checked-exception Factory seam returns the original Map. No
legacy factory is called. No result DTO, tree clone, secondary ledger or graph
is introduced. The result is the existing five-field Study.SetupResult.

This frozen emitter profile admits **exact LinkedHashMap and ArrayList classes**,
matching its generated constructions and Python's exact dict/list policy. It
does not promise acceptance of arbitrary Map/List implementations or subclasses.
The one generic classification operation covers the exact-class inspection;
unknown classes fail without invoking their collection methods. Root confirmed
this interpretation before execution. Integer/String/Boolean are exact final
classes, null is a distinct generic node, and unknown numeric types fail.

All input-dependent data inspection is precharged. Generic traversal retains
key-inclusive visits separately from container/edge inventory. Strict ordered
map size/key checks occur before the final observed-versus-declared inventory
predicate, so ordinary nested missing/extra/reordered keys report SCHEMA_SHAPE.
Earlier generic-domain, alias and resource-bound failures retain their own codes.
The schema-key switches construct no application-owned key arrays.

## Fixed successful debit, outside V

| Operation | Units | Copy entries | Copy chunks |
| --- | ---: | ---: | ---: |
| Fresh native field/list inspections | 18 | 0 | 0 |
| H: expected SHA length and 64 character checks, four calls, three predicates | 72 | 0 | 0 |
| Factory constructions/mutations/boxings | 290 | 0 | 0 |
| Projection reservation `copy(271)` | 271 | 271 | 1 |
| State, IdentityHashMap, reused HashSet constructions | 3 | 0 | 0 |
| Tracker-reference reservation `copy(202)` | 202 | 202 | 1 |
| Result allocation, copy5, four deferred reads, finish | 11 | 5 | 1 |
| Total outside V | **867** | **478** | **3** |

H has expected-SHA length/control1 + lowercase-hex characters64 + API calls4 +
schema equality1 + expected-context equality1 + exact271 predicate1. The API unit
for dataJson is charged immediately before that call, after factory/copy
prepayment. On entry to dataJson the actual expected snapshot is units651,
copiedEntries271, copyChunks1. This is a source expectation, not an observation.

The 18 native inspections are started, units, groups, copiedEntries, copyChunks,
priorGroups, copyAllowance, open, groupUnits, maxGroupNanos, maxGroupUnits,
inFlightOwnerGroups, three original-owner flags, groupMeasures reference, its
size, and sessionConstructorEntries. The cached started equality has its own
START rejection. Remaining cached freshness facts reject METER_STATE. Basic
null arguments, including the expected SHA, reject ARGUMENTS before these reads.
No positivity or raw timestamp ordering is imposed.

The only allocated validator controls are one State containing the Meter and its
two trackers, one IdentityHashMap and one reused HashSet. Before every tracker
write `slot()` charges a checked admission increment and rejects before the
202-reference quota is exceeded. Valid storage admissions are 16 identity puts
and 186 uniqueness adds; the six set clears do not replenish the total quota.
Tracker writes/probes and returned duplicate/presence predicates have separate
unit charges. Collection/iterator implementation internals are outside the
source-operation census specified by the contract.

The tail is charged while open: allocation1, copy5 (which adds five units),
deferred reads4, finish1. Then finish occurs once. Afterwards there are exactly
four native reads, with Long units/copiedEntries and Integer groups/copyChunks,
followed by the prepaid result construction. There are no post-close checks,
copies or charges. Actual closed groups are returned; no group count is assumed.

## V catalogue for the concrete helper

Each `u(1)` is immediately before its operation. A checked update includes its
bound/overflow rejection. Generic type classification is one bounded operation;
depth predicates are separately charged. Constants, switch selection, cached
local values, loop-index control and primitive VM instructions are not additional
source observations. A rejected-operation exception is the failure branch of
the already charged guard; no success-path exception/control allocation exists.

For a successful strict projection, define L as the sum of the lengths of its
290 generic strings (69 map keys and 221 string values). `ascii` caches the
single charged length; each character is read once under its own charge. Strict
hex/UUID validation and list nonempty checks deliberately repeat observations
and are separately priced below.

### Generic pass: walk = 2699 + L

| Actual helper operation | Multiplicity | Units |
| --- | ---: | ---: |
| State.visit checked increment | 325 | 325 |
| Depth-limit predicate | 325 | 325 |
| kind node classification | 325 | 325 |
| down checked depth addition | 324 | 324 |
| ascii length/bound admission | 290 | 290 |
| ascii character/category inspection | L | L |
| Identity slot + put + duplicate predicate | 16 x 3 | 48 |
| State.edge checked inventory increment | 16 containers + 255 edges | 271 |
| Map/List size and upper-bound predicate | 16 x 2 | 32 |
| Map.entrySet + map iterator, list iterator | 10 x 2 + 6 | 26 |
| Iterator.hasNext including final false | maps79 + lists192 | 271 |
| Iterator.next | maps69 + lists186 | 255 |
| Map.Entry.getKey | 69 | 69 |
| Map-key String guard | 69 | 69 |
| Map.Entry.getValue | 69 | 69 |
| Subtotal | | **2699 + L** |

The 324 child descents are 69 keys + 69 map values + 186 list elements.
Keys undergo visit, depth, kind and ASCII admission like other strings, but do
not count as additional inventory edges. Containers16 + edges255 = inventory271.
The structural counts are visits325, strings290, Integers13, Booleans6.

### Strict pass and final inventory predicate: 3044

| Actual helper operation | Derivation | Units |
| --- | --- | ---: |
| shape over 10 maps / 69 keys | per map type,size,size predicate,entrySet,iterator,final false =6; per key hasNext,next,getKey,key guard,key equality =5 | 405 |
| All get(Map,key) observations | 69 schema fields + receipt SHA re-read1 + sameTrial16 + capture-trial cohort re-read1 | 87 |
| fixed | 21 x (String guard1 + bounded equality1) | 42 |
| hex | 13 x (String guard1 + length1 + length predicate1) + 40 + 12 x 64 characters | 847 |
| uuid | guard1 + length1 + length predicate1 + 36 category inspections | 39 |
| integer / positiveInt | 13 x (type guard1 + unbox1 + field constraint1) | 39 |
| bool | 6 x (type guard1 + unbox1 + declared field constraint1) | 18 |
| unique over six lists | 6 x (type,size,size predicate,clear) + 186 x (list.get,String guard,length,nonempty,slot,add,duplicate predicate) | 1326 |
| cohort fixed ordering | 3 lists x 2 x (list.get1 + fixed equality1) | 12 |
| sixty cohort subset | 3 lists x 2 x (contains1 + presence predicate1) | 12 |
| sameTrial excluding its Map.get observations already above | four scalar equalities4 + sameList(2)=9 + sameList(60)=183 + four Boolean unboxes4 + two Boolean comparisons2 | 202 |
| Capture/trial cohort list join | sameList(2): size2 + size predicate1 + 2 x (get2 + equality1) | 9 |
| Receipt operation-SHA join | bounded scalar equality1 | 1 |
| Captured index43 join | list.get1 + equality1, using cached failed value | 2 |
| Promoted membership | contains1 + presence predicate1, using cached promoted value | 2 |
| Final observed inventory comparison | after strict schemas | 1 |
| Subtotal | | **3044** |

`sameTrial` compares the two four-string scalar prefixes, the two lists through
explicit element loops, and the two Boolean fields through explicit unboxing.
It does not invoke Map.equals or any unpriced recursive collection equality.
All sameList operands have already passed their strict list/scalar checks.
The captured list is validated last among the six uniqueness lists, so the
single reused set still contains its elements for promoted membership.

The exact source formula is therefore:

```
V = (2699 + L) + 3044 = 5743 + L
successful units = 867 + V = 6610 + L
copiedEntries = 478; copyChunks = 3; groups = actual closed Meter groups
```

No success expectation is derived from the subject Meter or by first running the
helper. These counts must remain independently checked before root executes it.

## Frozen synthetic source fixture debit

The known positive fixture is the literal `_synthetic_context` in the ninth R2
source file. Two independently allocated copies of that same synthetic fixture
have the same expected debit. No current or future actual capture is inferred.

Generic map-key character lengths, independently summed from the ordered schema:

| Map | Key character sum |
| --- | ---: |
| Root | 49 |
| prior | 151 |
| priorOriginalReceipt | 64 |
| prior trialDisposition | 105 |
| capture | 125 |
| capture trialDisposition | 105 |
| ownerSettings | 130 |
| seedValues | 32 |
| sourceIdentity | 132 |
| padLandIdentity | 20 |
| Total | **913** |

Generic string-value lengths:

| Class | Independent source derivation | Characters |
| --- | --- | ---: |
| Hex fields | sourceParent40 + six prior digests384 + receipt64 + five identity digests320 | 808 |
| UUID | pinned 8-4-4-4-12 form | 36 |
| Fixed non-list fields | receiptPath10 + two trial prefixes74 + capture36 + owner42 + seeds55 + PAD85 | 302 |
| Three cohort lists | 3 x (10+10) | 60 |
| Three 60-ID lists | each 60 x seven-character NET IDs, with D_SENSE length7 and two coil IDs length10: 3 x 426 | 1278 |
| Total string values | 808+36+302+60+1278 | **2484** |

Thus L = 913 + 2484 = **3397**, V = 5743 + 3397 = **9140**, and the
independently expected successful debit is **10007 units**, with478 copied
entries and3 chunks. Both same-source positive canary invocations use this debit.
For any additional source-frozen positive fixture, independently recompute L and
apply6610+L before execution; this worksheet does not bless an unspecified tree.
In particular, the separate future actual run's owner value419905 must never
replace the historic source-policy918222 in this fixture or validator.

## Errors, source review and preserved limits

The agreed component prefix is `V2_SETUP_`, with codes ARGUMENTS, START,
METER_STATE, SCHEMA, CONTEXT_SHA, ENTRIES, JSON_DOMAIN, ALIAS, SCHEMA_SHAPE,
POLICY and BOUNDS. The canary also names METER as a harness classification;
the helper leaves actual native budget failures intact rather than masking
their existing cause. Invalid non-null expected hashes fail CONTEXT_SHA before
factory calls. Boolean numeric fields fail POLICY; Long/float/unknown nodes fail
JSON_DOMAIN. Custom/subclass collection methods are not invoked.

Source review in this worker: completed against the root catalogue, pinned
nested field map, literal fixture and current canary source. Runtime validation:
**NOT RUN** (explicit source-only scope). Compile/native/production build/player
input gates: **NOT RUN**, owned by root where applicable. No repository paths
were edited, and no staging, commit, push or notification occurred. Only the
assigned Java helper and this worksheet were authored; other workers' files
were preserved. No worker-owned process/resource was launched.

All caps remain90s/640groups/5s/32768units/copy1MiB/chunk4096. HARD/PSYCHOTIC
remains unavailable/unrun, full30 excluded, oldnormal20 READ historically with
no reread here, protected-unread UNCERTIFIED, playable100=0, beta held, and
genuine one-original followed by exact NULL/OBSERVE remains UNSPENT_GATED.
