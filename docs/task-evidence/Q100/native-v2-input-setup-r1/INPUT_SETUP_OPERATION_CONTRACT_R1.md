# Current input/setup operation contract R1

Root Source design decision; no implementation or runtime acceptance. The
immutable R1 draft and policy map remain separate proposals. This contract
resolves their design choices. Actual Java Source and its debit inventory must
be audited against this catalogue before any compile/factory/native execution.
No DATA, original, Session or routing invocation is authorized by this file.

The component API is CurrentV2InputSetup.read(Study.SetupMeter,long started,
String expectedContextSha,Factory)->the existing Study.SetupResult. Factory
has schemaTag():String, contextSha256():String, dataEntries():int and
dataJson():Map<String,Object>. The caller exclusively owns the returned tree
through validation and use. A future normal facade is independently source/
class/context bound. Neither this injection seam nor a returned hash supplies
normal host authority. Normal main/assembly remain disabled.

## Fixed sequence and debits

1. Null basic arguments fail before data access. Charge18 before the exact18
   native field/list inspections enumerated in INPUT_SETUP_PREPAYMENT_DRAFT_R1.
   Require empty work/copy/owner/measure state after that debit, actual started
   equality and the default allowance. No positivity or raw ordering check on
   nanoTime origins. A zero-work open group may be indistinguishable from a
   newly opened one; normal source constructs the Meter just before this call.
2. H=72 on valid headers: expected SHA length control1 and64 lowercase-hex
   character inspections; four precharged factory API calls; one bounded
   fixed schema String.equals; one bounded expected-SHA equality; one exact
   entry-count predicate. Equality to fixed/validated strings also establishes
   returned-string domain. Require exactly271 entries (which implies1..4096).
   Incompatible headers fail before dataJson. Argument Strings are typed; null
   rejection is part of the bounded length/equality control.
3. Before dataJson, charge290 factory operations and reserve copy271. The
   inventory16+255 and factory16+255+13+6 are distinct. copy271 contributes
   another271 units and one chunk; never charge those units twice. Call
   dataJson exactly once. This finite source-bound factory has bounded work;
   an arbitrary injected implementation is not trusted normal code.
4. Before validator control construction/population, charge3 explicit control
   allocations (one state object, one IdentityHashMap, one reusable HashSet)
   and reserve copy202 for16 container registrations plus186 uniqueness-set
   insertions over six lists. This is reference admission, not a tree clone.
   The state retains the Meter and its two controls as bounded identity state;
   no extra logical DTO or graph copy. Reused-set clear/probe/insertion work is
   separately unit-priced. Before every tracker write, consume a checked local
   reference-admission slot; fail before exceeding prepaid202. Invalid trees
   may leave unused credits; their Meter/result are discarded.
5. Validate ordinary domain, no aliases/cycles, key-inclusive visits<=1m,
   depth32, entries<=4096 and observed count271; then all strict ordered schema,
   field types/ranges/fixed values/uniqueness/bindings from the pinned policy
   map. Two passes are allowed and all repeated source observations are priced.
   Do not infer live physical/source/PAD truth from static projection metadata.
6. While metadata is open, charge1 SetupResult allocation, reserve copy5 for
   its existing fields, charge4 deferred native counter inspections, and
   charge1 explicit bounded finish lifecycle operation. Then finish once.
   After close perform ONLY those four prepaid typed reads (units/copiedEntries
   Long, groups/copyChunks Integer) and allocate/store the prepaid five-field
   result. No later charge, copy, compatibility check or tree mutation.

The tail costs11 actual units,5 entries,1 chunk. Actual R7 Source uses
meter.copy for26 constructor scalars and34 final replacements; these are not
units-only stores. Here copy5 contributes5 units, so charge10 PLUS copy5 would
double count them. Reserve/finish may roll groups; return actual closed groups.

Valid copies=271+202+5=478 and chunks=3. Fixed successful work excluding the
validator operations below is18+72+290+271+3+202+11=867 units. No global cap
changes. Source code must not introduce additional control allocations or
copies without first updating this inventory and preserving the prior version.

## V: exact logical operation catalogue

V is the actual sum of the following precharged source-level operations, not
a magic constant or CPU/VM instruction census. The implementation worksheet
must enumerate helper calls and independently derive the positive debit for
each frozen synthetic fixture before execution. Source audit must reject an
unpriced inspection, arithmetic operation, allocation or predicate.

- Checked visit, inventory, depth or tracker-admission increment/addition:1
  before each operation (bounds and overflow rejection belong to that checked
  operation). Keep visits including keys distinct from container/edge inventory.
- Generic node type classification:1; depth-limit predicate:1. Each generic
  node also has its separate checked visit increment. Integers are Integer
  only; Booleans/null are separate; no Long/float/array/unknown object.
- String length access:1; each character inspection plus its bounded printable/
  hex/UUID-category test:1. A generic text admission can check its length bound
  in the same length operation. Field-specific nonempty/length/fixed predicates
  are separately priced unless the implementation uses a bounded fixed equality
  that already proves them. No free repeated scans.
- Map/list size, iterator acquisition, hasNext, next, entry.getKey,
  entry.getValue, map.get, list.get or list-size inspection:1 each. Iterator
  control is an API operation; primitive internal VM allocations are unmeasured.
- Key-type guard, ordered-key equality, exact-size constraint, scalar type
  guard, unboxing/value read, field range/fixed predicate:1 each as applicable.
  One declared bounded field constraint may combine its fixed min/max checks;
  do not hide additional source reads, traversals or arithmetic inside it.
- IdentityMap.put/HashSet.add/contains/clear:1 each, distinct from checked
  reference-slot admission and from inspecting a returned duplicate predicate1.
  Set insertion storage is already prepaid. Use one set reused across six lists.
- Every subset/index/value-join comparison:1 for the bounded predicate, plus
  separately charged source reads and list/map operations it consumes. Compare
  the two trial objects explicitly through their fixed fields/lists; do not
  delegate an unpriced recursive Map.equals traversal.
- Every additional explicit checked arithmetic or validation control not covered
  by an operation above must be named and priced before execution. Application
  constants/switch selectors and primitive VM instructions are not data reads.

Use switches for schema-key metadata, avoiding unpriced application-owned key
array construction. Helpers may cache a source observation in a local within
their operation; every later actual getter/field/character access is charged.
No tree clones, duplicate setup ledger, fabricated OriginalArm/ArmResult or
unmeasured generation/solver behavior. Independent native and reference counts
must match the final Source; neither may be relaxed to bless a mismatch.

## Scope and proof limits

Source-only admission will settle the concrete helper inventory and V formula;
only then can root choose fresh JDK8 and actual-Meter synthetic gates. Header
comparisons are bounded String APIs and do not claim to count every character
executed inside String.equals/hash tables. Primitive VM internals are unmeasured.
The hash refers to full context while the returned tree excludes physical data;
host/class/full-context authority and post-original physical proof remain later.

All existing limits, exact policy seeds, epochs, independent oracles and CircuitJS
truth stay. HARD/PSYCHOTIC unavailable/unrun, full30 excluded, oldnormal20 READ
historically/no reread here, protected unread boundary UNCERTIFIED, playable100=0,
beta held and genuine one-original then exact NULL/OBSERVE UNSPENT_GATED.
